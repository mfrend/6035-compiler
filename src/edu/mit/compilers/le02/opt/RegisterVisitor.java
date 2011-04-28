package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import edu.mit.compilers.le02.RegisterLocation;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.Argument.ArgType;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.dfa.GenKillItem;
import edu.mit.compilers.le02.dfa.Lattice;
import edu.mit.compilers.le02.dfa.Liveness;
import edu.mit.compilers.le02.dfa.ReachingDefinitions;
import edu.mit.compilers.le02.dfa.WorklistAlgorithm;
import edu.mit.compilers.le02.dfa.WorklistItem;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;
import edu.mit.compilers.tools.CLI;

public class RegisterVisitor extends BasicBlockVisitor
                             implements Lattice<BitSet, BasicBlockNode> {
  private Map<BasicStatement, Web> defUses;
  private Map<BasicStatement, List<Web>> useToDefs;
  private Map<BasicBlockNode, WebLiveness> blockLiveness;
  private Map<BasicStatement, Collection<Web>> liveWebsAtStatement;
  private Map<BasicStatement, Collection<Web>> dyingWebsAtStatement;
  private Map<Integer, Register> registerMap;
  private List<Web> finalWebs;
  private Map<Web, Integer> webIndices;
  private Map<TypedDescriptor, Register> allocatedGlobals;
  private InterferenceGraph ig;
  private ReachingDefinitions rd;
  private Liveness liveness;
  private Pass pass;
  
  public static final int NUM_REGISTERS = 11;
  public static final boolean ALLOCATE_GLOBALS = false;
  

  public static enum Pass {
    GENERATE_DU,
    GENERATE_WEB_LIVENESS,
    GENERATE_IG,
    INSERT_REGISTERS
  }


  public static void runRegisterAllocation(BasicBlockNode methodHead) {
    ReachingDefinitions rd = new ReachingDefinitions(methodHead);
    Liveness liveness = new Liveness(methodHead);
    RegisterVisitor visitor = new RegisterVisitor(rd, liveness);

    
    // == STAGE 1 ==
    // Generate def-use (DU) chains, which pair the definition of a variable
    // with all of its reachable uses.
    visitor.pass = Pass.GENERATE_DU;
    visitor.visit(methodHead);
    
    // == STAGE 2 ==
    // Generate webs from DU chains, by combining chains which share
    // the same uses.
    visitor.combineWebs();
    
    if (CLI.debug) {
      for (Web w : visitor.finalWebs) {
        System.out.println(w);      
      }
    }
    
    // == STAGE 3 ==
    // In order to generate the interference graph in stage 4, we need
    // to use our existing variable liveness information to determine which
    // webs are live in which blocks.
    visitor.pass = Pass.GENERATE_WEB_LIVENESS;
    visitor.visit(methodHead);
    WorklistAlgorithm.runBackwards(visitor.blockLiveness.values(), visitor);
    
    // == STAGE 4 ==
    // Using the per-block web liveness information generated in stage 3, 
    // run a per-statement liveness analysis on each block to determine which
    // webs interfere with each other, thus generating an interference graph
    visitor.initInterferenceGraph();
    visitor.pass = Pass.GENERATE_IG;
    visitor.visit(methodHead);
    
    // == STAGE 5 ==
    // Now that we have an interference graph, we color the interference graph
    // and do a spill cost analysis on each of the webs to determing which
    visitor.allocateRegisters();
    
    if (CLI.debug) {
      System.out.println("== AFTER COLORING ==");
      for (Web w : visitor.finalWebs) {
        System.out.println(w);      
      }
    }
    
    // == STAGE 6 ==
    visitor.pass = Pass.INSERT_REGISTERS;
    visitor.visit(methodHead);
  }
  
  public RegisterVisitor(ReachingDefinitions rd, Liveness liveness) {
    this.rd = rd;
    this.liveness = liveness;
    
    this.defUses = new HashMap<BasicStatement, Web>();
    this.useToDefs = new HashMap<BasicStatement, List<Web>>();
    this.blockLiveness = new HashMap<BasicBlockNode, WebLiveness>();
    this.liveWebsAtStatement = new HashMap<BasicStatement, Collection<Web>>();
    this.dyingWebsAtStatement = new HashMap<BasicStatement, Collection<Web>>();
    this.finalWebs = new ArrayList<Web>();
    this.webIndices = new HashMap<Web, Integer>();

    this.registerMap = new HashMap<Integer, Register>();
    this.pass = Pass.GENERATE_DU;
    

    this.registerMap.put(0, Register.RBX);
    this.registerMap.put(1, Register.RCX);
    this.registerMap.put(2, Register.RDX);
    this.registerMap.put(3, Register.RSI);
    this.registerMap.put(4, Register.RDI);
    this.registerMap.put(5, Register.R8);
    this.registerMap.put(6, Register.R9);
    this.registerMap.put(7, Register.R12);
    this.registerMap.put(8, Register.R13);
    this.registerMap.put(9, Register.R14);
    this.registerMap.put(10, Register.R15);
    // These registers should be unallocated, as they are used as temps
    this.registerMap.put(-2, Register.R10);
    this.registerMap.put(-3, Register.R11);
    this.registerMap.put(-1, Register.RAX);
  }
    
  
  @Override
  protected void processNode(BasicBlockNode node) {
    switch (pass) {
      case GENERATE_DU:
        generateDefUses(node);
        break;
      case GENERATE_WEB_LIVENESS:
        generateWebLivenessInfo(node);
        break;
      case GENERATE_IG:
        generateInterferenceGraph(node);
        break;
      case INSERT_REGISTERS:
        insertRegisters(node);
        break;
    }
  }
  
  /**
   * Generates the def-use chains (in Web form) for a given basic block.
   * @param node The given basic block
   */
  @SuppressWarnings("unused")
  private void generateDefUses(BasicBlockNode node) {
    Collection<BasicStatement> defs;
    ReachingDefinitions.BlockItem bi = rd.getDefinitions(node);
    HashMap<TypedDescriptor, BasicStatement> localDefs =
        new HashMap<TypedDescriptor, BasicStatement>();
    
    TypedDescriptor desc;
    for (BasicStatement stmt : node.getStatements()) {
      
      if (stmt.getType() == BasicStatementType.CALL) {
        CallStatement call = (CallStatement) stmt;

        for (Argument arg : call.getArgs()) {
          if (arg.getType() == ArgType.VARIABLE) {
            desc = arg.getDesc();
            
            if (ALLOCATE_GLOBALS &&
                desc.getLocation().getLocationType() == LocationType.GLOBAL) {
              continue;
            }
            defs = bi.getReachingDefinitions(desc.getLocation());
            addDefUse(call, desc, defs, localDefs);
          }
        }

        if (call.getResult() != null) {
          localDefs.put(call.getResult(), call);
        }
        
        // The call blows up all locals, so defs before the call can't reach
        // uses after the call.
        List<TypedDescriptor> toRemove = new ArrayList<TypedDescriptor>(); 
        for (TypedDescriptor d : localDefs.keySet()) {
          if (d.getLocation().getLocationType() == LocationType.GLOBAL) {
            toRemove.add(d);
          }
        }
        
        for (TypedDescriptor d : toRemove) {
          localDefs.remove(d);  
        }
        
        continue;
      } else if (stmt.getType() != BasicStatementType.OP) {
        continue;
      }

      OpStatement op = (OpStatement)stmt;
      if (op.getArg1() != null && op.getArg1().getType() == ArgType.VARIABLE) {
        desc = op.getArg1().getDesc();
        if (ALLOCATE_GLOBALS &&
            desc.getLocation().getLocationType() == LocationType.GLOBAL) {
          continue;
        }
        defs = bi.getReachingDefinitions(desc.getLocation());
        addDefUse(op, desc, defs, localDefs);
      }
      
      if (op.getArg2() != null && op.getArg2().getType() == ArgType.VARIABLE) {
        desc = op.getArg2().getDesc();
        if (ALLOCATE_GLOBALS &&
            desc.getLocation().getLocationType() == LocationType.GLOBAL) {
          continue;
        }
        defs = bi.getReachingDefinitions(desc.getLocation());
        addDefUse(op, desc, defs, localDefs);
      }
      
      if (op.getTarget() != null) {
        localDefs.put(op.getTarget().getDesc(), op);
      }
    }
  }
 
  /**
   * Adds a specific use of a specific variable to the def-use chains of the
   * given definitions.  Assumes that the definitions all reach the given use
   * and that they use the same variable as given.
   * @param use
   * @param loc
   * @param defs
   */
  private void addDefUse(BasicStatement use,
                         TypedDescriptor loc, 
                         Collection<BasicStatement> defs,
                         HashMap<TypedDescriptor, BasicStatement> localDefs) {
    
    // If we have a local definition, that overrides the global definitions
    BasicStatement d = localDefs.get(loc);
    if (d != null) {
      Web uses = defUses.get(d);
      if (uses == null) {
        uses = new Web(loc, d);
      }
      uses.addStmt(use);
      defUses.put(d, uses);
      
      List<Web> webs = useToDefs.get(use);
      if (webs == null) {
        webs = new ArrayList<Web>();
      }
      webs.add(uses);
      useToDefs.put(use, webs);
      return;
    }    
    
    for (BasicStatement def : defs) {
      Web uses = defUses.get(def);
      if (uses == null) {
        uses = new Web(loc, def);
      }
      uses.addStmt(use);
      defUses.put(def, uses);
      
      List<Web> webs = useToDefs.get(use);
      if (webs == null) {
        webs = new ArrayList<Web>();
      }
      webs.add(uses);
      useToDefs.put(use, webs);
    }
  }
 
  
  /**
   * Combines the def-use chains created by generateDefUses such that they
   * satisfy the condition for webs, that all definitions that reach same use 
   * must be in same web.
   */
  private void combineWebs() {
    HashMap<TypedDescriptor, Web> finalWebMap = 
        new HashMap<TypedDescriptor, Web>();
    
    HashSet<Web> finalWebSet = new HashSet<Web>();
    
    for (Entry<BasicStatement, List<Web>> entry : useToDefs.entrySet()) {
      List<Web> webs = entry.getValue();
      
      Web finalWeb;
      for (Web web : webs) {
        finalWeb = finalWebMap.get(web.desc());
        
        if (finalWeb == null) {
          finalWeb = web;
        }
        else {
          assert finalWeb.desc().equals(web.desc());
          finalWeb.union(web);
        }
        finalWebMap.put(finalWeb.desc(), finalWeb.find());
      }
      
      finalWebSet.addAll(finalWebMap.values());
      finalWebMap.clear();
    }
    
    finalWebs.addAll(finalWebSet);
    
    // This is purely to make the coloring deterministic, to make debugging
    // easier.
    Collections.sort(finalWebs);
    for (int i = 0; i < finalWebs.size(); i++) {
      webIndices.put(finalWebs.get(i), i);
    }
    
  }
  
  /**
   * Generates info about which webs are live at the beginning and end of a
   * given node, using the variable liveness information from the Liveness
   * object.
   * @param node
   */
  private void generateWebLivenessInfo(BasicBlockNode node) {
    
    // The kill set will contain all webs that become dead going backwards from
    // exit to entry, based only on this basic block.
    HashSet<Web> killSet = new HashSet<Web>();

    // The killed vars contains all variables that have been killed by defs
    // from any web.
    HashSet<TypedDescriptor> killedVars = new HashSet<TypedDescriptor>();
    
    // The gen map will contain all webs that become live going backwards from
    // exit to entry, based only on this basic block.
    HashMap<TypedDescriptor, Web> genMap = 
        new HashMap<TypedDescriptor, Web>();
    
    for (BasicStatement stmt : node.getStatements()) {
      // If we hit a use before we hit any defs, then this web is live on
      // entry.
      List<Web> webs = useToDefs.get(stmt);
      if (webs != null) {
        for (Web w : webs) {
          
          // If we have hit a def for this variable already, then this web
          // will be killed before entry (going backwards)
          if (killedVars.contains(w.desc())) {
            continue;
          }
          
          Web oldWeb = genMap.get(w.desc());
          if (w.find() != oldWeb) {
            
            // We shouldn't hit two uses from different webs without a
            // def in between
            assert oldWeb == null;
            
            Web newWeb = w.find();
            genMap.put(newWeb.desc(), newWeb);
          }
        }
      }
      
      Web web = defUses.get(stmt);
      if (web != null) {
        killedVars.add(web.desc()); 
        killSet.add(web.find());
      }
    }

    blockLiveness.put(node, new WebLiveness(this, node, 
                                            genMap.values(), killSet));
  }
  
  /**
   * Allocates the interference graph object and adds all webs as unconnected 
   * nodes.
   */
  private void initInterferenceGraph() {
    ig = new InterferenceGraph();
    for (Web w : finalWebs) {
      ig.addNode(w);
    }
  }

  /**
   * Generates the interference graph by doing a per-statement liveness
   * calculation for the given block, and noting which webs interfere.
   * TODO: Store this information so we can use it later on in register 
   *       targeting and assembly generation 
   * @param node
   */
  private void generateInterferenceGraph(BasicBlockNode node) {
    WebLiveness wl = blockLiveness.get(node);
    List<Web> liveOnExit = wl.liveOnExit();
    HashMap<TypedDescriptor, Web> currentlyLive = 
      new HashMap<TypedDescriptor, Web>();
    
    assert wl != null;

    // Link all ending nodes
    int size = liveOnExit.size();
    for (int i = 0; i < size; i++) {
      Web w1 = liveOnExit.get(i);
      currentlyLive.put(w1.desc(), w1);
      for (int j = i+1; j < size; j++) {
        Web w2 = liveOnExit.get(j);
        ig.linkNodes(w1, w2);
      }
    }
    
    List<BasicStatement> stmts = node.getStatements();
    ListIterator<BasicStatement> li = stmts.listIterator(stmts.size());
    
    if (stmts.isEmpty()) {
      return;
    }

    ArrayList<Web> dying = new ArrayList<Web>();
    // Traverse backwards through the statement list to compute liveness
    for (BasicStatement stmt = li.previous(); li.hasPrevious(); 
         stmt = li.previous()) {
      
      // We only care about Op and Call statements.
      if (stmt.getType() != BasicStatementType.OP
          && stmt.getType() != BasicStatementType.CALL) {
        continue;
      }
      
      // If we hit a definition, its web is no longer live, so we can remove it.
      // Note: Thinking forwards instead of backwards, this means the 
      //       variable becomes live at the beginning of the next statement.
      Web web = defUses.get(stmt);
      if (web != null) {
        currentlyLive.remove(web.desc());
      }
      
      // If we hit a use, this web may become live at this statement.
      // Note: Thinking forwards instead of backwards, this means the variable 
      //       begins being dead at the beginning of the next statement
      List<Web> webs = useToDefs.get(stmt);
      if (webs != null) {
        dying.clear();
        for (Web w : webs) {
          Web oldWeb = currentlyLive.get(w.desc());
          if (w.find() != oldWeb) {
            Web newWeb = w.find();
            currentlyLive.put(newWeb.desc(), newWeb);
            dying.add(newWeb);

            for (Web w2 : currentlyLive.values()) {
              ig.linkNodes(newWeb, w2);
            }
          }
        }

        if (!dying.isEmpty()) {
          dyingWebsAtStatement.put(stmt, new ArrayList<Web>(dying));
        }
      }
      
      // Record liveness info
      liveWebsAtStatement.put(stmt, new ArrayList<Web>(currentlyLive.values()));
      
      //System.out.println("dying: " + dying + " live: " + currentlyLive.values());
    }
  }
  
 
  
  /**
   * Color the interference graph and then compute the spill cost of each of
   * the colors to determine which of the colors should get registers.
   */
  private void allocateRegisters() {
    // TODO: Intelligent coloring for cases where we have a lot of calculations
    //       in a row, so:
    //       ADD a + b -> c
    //       MUL c * d -> f
    //       should become
    //       %r8 -> a, %r9 -> b, %r8 -> c, %r10 -> d or something like that
    //       add %r8, %r9
    //       mul %r8, %r10
    int numColors = ig.colorGraph();

    // TODO: Register targeting, so we don't have to copy for things like
    //       arguments and return values, or idiv arguments
    if (numColors <= NUM_REGISTERS) {
      // Assign registers to everything, since we're ok (also the register
      // map is setup in the initialization).
      return;
    }
    else {
      
      // TODO: Take loop nesting into account in spill cost calculation
      // Tree maps used for determinism
      TreeMap<Integer, List<Web>> colorMap = new TreeMap<Integer, List<Web>>();
      for (Web w : finalWebs) {
        List<Web> list = colorMap.get(w.getColor());
        if (list == null) {
          list = new ArrayList<Web>();
        }
        list.add(w);
        colorMap.put(w.getColor(), list);
      }
      
      ArrayList<List<Web>> webLists = 
          new ArrayList<List<Web>>(colorMap.values());
      Collections.sort(webLists, new Comparator<List<Web>>() {
        // Want the highest spill costs to be first in the list
        @Override
        public int compare(List<Web> w1, List<Web> w2) {
          int s1 = 0;
          int s2 = 0;
          
          for (Web x : w1) {
            s1 += x.getSpillCost();
          }
          for (Web y : w2) {
            s2 += y.getSpillCost();
          }
          
          // DETERMINIZE!
          if (s1 == s2) {
            return w1.hashCode() - w2.hashCode();
          }
          return -(s1 - s2);
        }
      });
      
      Register reg;
      for (int i = 0; i < NUM_REGISTERS || i < webLists.size(); i++) {
        reg = registerMap.get(i);
        List<Web> list = webLists.get(i);
        
        // Assign a register to this color
        registerMap.put(list.get(0).getColor(), reg);
        
        // Mark any globals that have been assigned to a register as such,
        // so we can easily spill and restore them later.
        for (Web w : list) {
          if (w.desc().getLocation().getLocationType() == LocationType.GLOBAL) {
            allocatedGlobals.put(w.desc(), reg);
          }
        }
      }
    }
  }

  /**
   * Given the register allocation done in the previous steps, modify the
   * basic blocks to actually follow this register allocation.
   * @param node
   */
  private void insertRegisters(BasicBlockNode node) {
    ArrayList<BasicStatement> newStmts = new ArrayList<BasicStatement>();
    
    // TODO: spill everything before calls so that we don't screw up stuff
    //       and restore everything after calls
    for (BasicStatement stmt : node.getStatements()) {

      Register reg;
      TypedDescriptor result = stmt.getResult();
      
      if (stmt.getType() == BasicStatementType.CALL) {

        Web web = defUses.get(stmt);
        if (web != null) {
          reg = registerMap.get(web.find().getColor());
          if (reg != null) {
            result = new AnonymousDescriptor(new RegisterLocation(reg), 
                                             web.find().desc());
          }
        }
        
        CallStatement call = (CallStatement) stmt;
        List<Web> webs = useToDefs.get(stmt);
        List<Argument> args = call.getArgs();
        if (CLI.debug) {
          System.out.println("Checking call " + call);
        }
        if (webs != null) {
          for (int i = 0; i < args.size(); i++) {
            Argument arg = args.get(i);
            arg = convertArg(webs, arg);
            args.set(i, arg);
          }
        }
       
        CallStatement newCall = new CallStatement(
            ((ExpressionNode) call.getNode()), 
            call.getMethodName(), 
            args, 
            result, 
            call.isCallout());
        
        newStmts.add(newCall);
        setRegisterLivenessInfo(call, newCall);
        continue;
      }
      else if (stmt.getType() != BasicStatementType.OP) {
        newStmts.add(stmt);
        continue;
      }
      
      OpStatement op = (OpStatement) stmt;
      Argument arg1 = op.getArg1();
      Argument arg2 = op.getArg2();
      result = op.getTarget().getDesc();
      
      Web web = defUses.get(stmt);
      if (web != null) {
        reg = registerMap.get(web.find().getColor());
        if (reg != null) {
          result = new AnonymousDescriptor(new RegisterLocation(reg),
                                           web.find().desc());
        }
      }
      
      List<Web> webs = useToDefs.get(stmt);
      if (CLI.debug) {
        System.out.println("Checking op " + op);
      }
      if (webs != null) {
        arg1 = convertArg(webs, op.getArg1());
        arg2 = convertArg(webs, op.getArg2());
      }
      
      OpStatement newOp;
      if (op.getOp() == AsmOp.MOVE) {
        newOp = new OpStatement(op.getNode(), op.getOp(), 
                                     arg1, Argument.makeArgument(result),
                                     null);

        
      }
      else {
        newOp = new OpStatement(op.getNode(), op.getOp(), 
                                     arg1, arg2, result);
      }
      newStmts.add(newOp);
      setRegisterLivenessInfo(op, newOp);
    }
    node.setStatements(newStmts);
  }
  
  private void setRegisterLivenessInfo(BasicStatement oldStatement, 
                                       BasicStatement newStatement) {

    Collection<Web> dyingWebs = dyingWebsAtStatement.get(oldStatement);
    Collection<Web> liveWebs = liveWebsAtStatement.get(oldStatement);
    if (liveWebs != null) {
      for (Web w : liveWebs) {
        Register r = registerMap.get(w.find().getColor());
        if (r != null) {
          if (CLI.debug) {
            System.out.println("Setting " + r + " live at " + newStatement);
          }
          newStatement.setRegisterLiveness(r, true);
        }
      }
    }
    if (dyingWebs != null) {
      for (Web w : dyingWebs) {
        Register r = registerMap.get(w.find().getColor());
        if (r != null) {
          if (CLI.debug) {
            System.out.println("Setting " + r + " dying at " + newStatement);
          }
          newStatement.setRegisterDying(r, true);
        }
      }
    }
  }
  
  private Argument convertArg(List<Web> webs, Argument arg) {
    if (arg == null) {
      return null;
    }
    
    Register reg;
    for (Web w : webs) {
      if (CLI.debug) {
        System.out.println("Desc: " + w.desc());
      }
      if (w.desc().equals(arg.getDesc())) {
        reg = registerMap.get(w.find().getColor());
        if (CLI.debug) {
          System.out.println("Found arg " + arg + " reg: " + reg);
        }
        if (reg != null) {
          return Argument.makeArgument(new AnonymousDescriptor(
                                           new RegisterLocation(reg),
                                           w.desc()));
        }
      }
    }
    return arg;
  }

  
  
  private static class WebLiveness extends GenKillItem {
    private BitSet genSet = new BitSet();
    private BitSet killSet = new BitSet();
    private BasicBlockNode node;
    private RegisterVisitor parent;
    
    public WebLiveness(RegisterVisitor parent,
                       BasicBlockNode node,
                       Collection<Web> gen,
                       Collection<Web> kill) {
      this.parent = parent;
      this.node = node;
      setGen(gen);
      setKill(kill);
    }
    
    public void setGen(Collection<Web> gen) {
      for (Web w : gen) {
        genSet.set(parent.webIndices.get(w));
      }
    }
    
    public void setKill(Collection<Web> kill) {
      for (Web w : kill) {
        killSet.set(parent.webIndices.get(w));
      }
    }
    
    @Override
    protected BitSet gen() {
      return genSet;
    }
    @Override
    protected BitSet kill() {
      return killSet;
    }
    
    public List<Web> liveOnExit() {
      BitSet liveness = (BitSet) this.getOut();
      ArrayList<Web> ret = new ArrayList<Web>();
      
      for (int i = 0; i < parent.finalWebs.size(); i++) {
        if (liveness.get(i)) {
          ret.add(parent.finalWebs.get(i));
        }
      }
      return ret;
    }
    
    public List<Web> liveOnEntrance() {
      BitSet liveness = (BitSet) this.getIn();
      ArrayList<Web> ret = new ArrayList<Web>();
      
      for (int i = 0; i < parent.finalWebs.size(); i++) {
        if (liveness.get(i)) {
          ret.add(parent.finalWebs.get(i));
        }
      }
      return ret;
    }
    
    @Override
    public Collection<WorklistItem<BitSet>> predecessors() {
      ArrayList<WorklistItem<BitSet>> ret =
        new ArrayList<WorklistItem<BitSet>>();


      for (BasicBlockNode pred : this.node.getPredecessors()) {
        WorklistItem<BitSet> item = parent.blockLiveness.get(pred);
        if (item != null) {
          ret.add(item);
        }
      }
      return ret;
    }
    
    @Override
    public Collection<WorklistItem<BitSet>> successors() {
      ArrayList<WorklistItem<BitSet>> ret =
        new ArrayList<WorklistItem<BitSet>>();

      WorklistItem<BitSet> item;
      if (node.getNext() != null) {
        item = parent.blockLiveness.get(node.getNext());
        ret.add(item);
      }

      if (node.getBranchTarget() != null) {
        item = parent.blockLiveness.get(node.getBranchTarget());
        ret.add(item);
      }

      return ret;
    }
  }


  @Override
  public BitSet abstractionFunction(BasicBlockNode value) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public BitSet transferFunction(BitSet[] values) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public BitSet bottom() {
    return new BitSet(finalWebs.size());
  }

  @Override
  public BitSet top() {
    BitSet s = new BitSet(finalWebs.size());
    s.flip(0, finalWebs.size());
    return s;
  }

  @Override
  public BitSet leastUpperBound(BitSet v1, BitSet v2) {
    BitSet ret = (BitSet) v1.clone();
    ret.or(v2);
    return ret;
  }
}
