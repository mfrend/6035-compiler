package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import edu.mit.compilers.le02.RegisterLocation;
import edu.mit.compilers.le02.StackLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.cfg.ArgReassignStatement;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.Argument.ArgType;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.dfa.GenKillItem;
import edu.mit.compilers.le02.dfa.Lattice;
import edu.mit.compilers.le02.dfa.ReachingDefinitions;
import edu.mit.compilers.le02.dfa.WorklistAlgorithm;
import edu.mit.compilers.le02.dfa.WorklistItem;
import edu.mit.compilers.le02.dfa.ReachingDefinitions.BlockItem;
import edu.mit.compilers.le02.dfa.ReachingDefinitions.FakeDefStatement;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
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
  private List<Register> registerOrder;
  private List<Web> finalWebs;
  private Map<Web, Integer> webIndices;
  private Map<TypedDescriptor, Register> allocatedGlobals;
  private InterferenceGraph ig;
  private ReachingDefinitions rd;
  private Pass pass;
  private BasicStatement startOfMethod;
  private MethodDescriptor methodDescriptor;
  private ArgReassignStatement argReassign = null;
  
  public static final int NUM_REGISTERS = 10;
  
  // This boolean indicates whether or not to consider globals for allocation
  // TODO: Add code to spill globals at appropriate times in order to allocate 
  //       them temporarily within a block.
  public static final boolean ALLOCATE_GLOBALS = false;
  

  public static enum Pass {
    GENERATE_DU,
    GENERATE_WEB_LIVENESS,
    GENERATE_IG,
    INSERT_REGISTERS
  }


  public static void runRegisterAllocation(BasicBlockNode methodHead, 
                                           MethodDescriptor md) {
    ReachingDefinitions rd = new ReachingDefinitions(methodHead);
    RegisterVisitor visitor = new RegisterVisitor(rd);
    visitor.methodDescriptor = md;
    visitor.startOfMethod = methodHead.getStatements().get(0);
    
    // == STAGE 1 ==
    // Generate def-use (DU) chains, which pair the definition of a variable
    // with all of its reachable uses.
    visitor.pass = Pass.GENERATE_DU;
    visitor.visit(methodHead);  // generateDefUse(node)
    
    // == STAGE 2 ==
    // Generate webs from DU chains, by combining chains which share
    // the same uses.
    visitor.combineWebs();
    
    if (CLI.debug) {
      System.out.println("== WEBS ==");
      for (Web w : visitor.finalWebs) {
        System.out.println(w.longDesc());      
      }
    }
    
    // == STAGE 3 ==
    // In order to generate the interference graph in stage 4, we need
    // to use our existing variable liveness information to determine which
    // webs are live in which blocks.
    visitor.pass = Pass.GENERATE_WEB_LIVENESS;
    visitor.visit(methodHead); // generateWebLivenessInfo(node)
    
    /* Commented to avoid huge dumps of debug data, but still occasionally
     * useful.
    if (CLI.debug) {
      System.out.println("== WEB LIVENESS ==");
      for (BasicBlockNode n : visitor.blockLiveness.keySet()) {
        System.out.println("----- " + n.getId() + " -----");
        for (BasicStatement st : n.getStatements()) {
          System.out.println(st);
        }
        WebLiveness wl = visitor.blockLiveness.get(n);
        System.out.println("*** GEN ***");
        for (Web w : wl.theGen) {
          System.out.println("  " + w);
        }
        System.out.println("*** KILL ***");
        for (Web w : wl.theKill) {
          System.out.println("  " + w);
        }
      }
    }
    */

    // This worklist algorithm fills out the global information for all
    // of the block liveness values.
    WorklistAlgorithm.runBackwards(visitor.blockLiveness.values(), visitor);
    
    // == STAGE 4 ==
    // Using the per-block web liveness information generated in stage 3, 
    // run a per-statement liveness analysis on each block to determine which
    // webs interfere with each other, thus generating an interference graph
    visitor.initInterferenceGraph();
    visitor.pass = Pass.GENERATE_IG;
    visitor.visit(methodHead); // generateInterferenceGraph(node)

    
    // == STAGE 5 ==
    // Now that we have an interference graph, we color the interference graph
    // and do a spill cost analysis on each of the webs to determing which
    visitor.allocateRegisters();

    if (CLI.debug) {
      System.out.println("== AFTER COLORING ==");
      for (Web w : visitor.finalWebs) {
        System.out.println(w.longDesc());      
      }
    }
    
    // == STAGE 6 ==
    visitor.pass = Pass.INSERT_REGISTERS;
    visitor.visit(methodHead); // insertRegisters(node)
  }
  
  public RegisterVisitor(ReachingDefinitions rd) {
    this.rd = rd;
    
    this.defUses = new HashMap<BasicStatement, Web>();
    this.useToDefs = new HashMap<BasicStatement, List<Web>>();
    this.blockLiveness = new HashMap<BasicBlockNode, WebLiveness>();
    this.liveWebsAtStatement = new HashMap<BasicStatement, Collection<Web>>();
    this.dyingWebsAtStatement = new HashMap<BasicStatement, Collection<Web>>();
    this.finalWebs = new ArrayList<Web>();
    this.webIndices = new HashMap<Web, Integer>();

    this.registerMap = new HashMap<Integer, Register>();
    this.registerOrder = new ArrayList<Register>();
    this.pass = Pass.GENERATE_DU;
    

    this.registerOrder.add(Register.RBX);
    this.registerOrder.add(Register.R13);
    this.registerOrder.add(Register.R14);
    this.registerOrder.add(Register.R15);
    this.registerOrder.add(Register.R9);
    this.registerOrder.add(Register.R8);
    this.registerOrder.add(Register.RDX);
    this.registerOrder.add(Register.RCX);
    this.registerOrder.add(Register.RSI);
    this.registerOrder.add(Register.RDI);

    for (int i = 0; i < NUM_REGISTERS; i++) {
      this.registerMap.put(i, registerOrder.get(i));
    }
    // These registers should be unallocated, as they are used as temps
    this.registerMap.put(-1, Register.R10);
    this.registerMap.put(-2, Register.R11);
    this.registerMap.put(-3, Register.R12);
    this.registerMap.put(-4, Register.RAX);
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
    // The reaching definitions give definitions from other blocks that reach
    // the start of this one.
    ReachingDefinitions.BlockItem bi = rd.getDefinitions(node);
    
    // The localDefs map keeps track of changes of the definition which reaches 
    // each variable _within_ the block.
    HashMap<TypedDescriptor, BasicStatement> localDefs =
        new HashMap<TypedDescriptor, BasicStatement>();
    
    // We are trying to fill out all of our def-use chains.  We do this
    // by scanning the basic block for uses, and placing them in the web
    // of each one of their reaching definitions in the defUses map.  We 
    // also keep track of which defs correspond to each use in the 
    // useToDefs map.
    TypedDescriptor desc;
    for (BasicStatement stmt : node.getStatements()) {
      
      // Handle calls
      if (stmt.getType() == BasicStatementType.CALL) {
        CallStatement call = (CallStatement) stmt;

        // Iterate over call argument to look for uses.
        for (Argument arg : call.getArgs()) {
          handleArg(bi, arg, call, localDefs);
        }

        // If this is a definition, add it to the local definitions.
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
      } else if (stmt instanceof FakeDefStatement) {
        // Fake def statements are placeholders at the beginning of the method
        // to be "definitions" for arguments passed into the method.
        FakeDefStatement fds = (FakeDefStatement) stmt;
        localDefs.put(fds.getParam(), fds);
        continue;
      } else if (stmt.getType() != BasicStatementType.OP) {
        continue;
      }

      // By here we've handled anything that's not an op.
      OpStatement op = (OpStatement)stmt;
      
      // Check both uses to see if they are variables
      if (op.getArg1() != null) {
        handleArg(bi, op.getArg1(), op, localDefs);
      }
      
      if (op.getArg2() != null) {
        // If the op is a move, the second argument is a def and not a use.
        if (op.getOp() != AsmOp.MOVE) {
          handleArg(bi, op.getArg2(), op, localDefs);
        } else if (op.getArg2().getType() == ArgType.ARRAY_VARIABLE) {
          // If the second arg is an array variable, the index variable will
          // still be used.
          ArrayVariableArgument ava = (ArrayVariableArgument) op.getArg2();
          handleArg(bi, ava.getIndex(), op, localDefs);
        }
      }
      
      // Update localDefs if this statement is a def.
      if (op.getTarget() != null) {
        localDefs.put(op.getTarget().getDesc(), op);
      }
    }
  }
  
  
  /** This function determins if the argument is a variable, and if so adds it
   * to the def-use webs.  It also handles the index if the argument is an
   * array variable.
   */
  private void handleArg(BlockItem bi, Argument arg, BasicStatement stmt,
                         HashMap<TypedDescriptor, BasicStatement> localDefs) {
    
    TypedDescriptor desc;
    Collection<BasicStatement> defs;
    
    // If the argument is a variable, add it as a use
    if (arg.getType() == ArgType.VARIABLE || 
        arg.getType() == ArgType.ARRAY_VARIABLE) {
      desc = arg.getDesc();
      
      // If we are allocating globals, or if the location isn't a global,
      // then add it as a use, otherwise ignore it.
      if (ALLOCATE_GLOBALS ||
          desc.getLocation().getLocationType() != LocationType.GLOBAL) {
        
        defs = bi.getReachingDefinitions(desc.getLocation());
        addDefUse(stmt, desc, defs, localDefs);
      }

    }
    
    // If the argument is an array variable, check the index as well.
    if (arg.getType() == ArgType.ARRAY_VARIABLE) {
      ArrayVariableArgument ava = (ArrayVariableArgument) arg;
      
      // We do this recursively in case the index is also an array variable.
      handleArg(bi, ava.getIndex(), stmt, localDefs);
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
      // Get the use web for this use's def, and add the use to it.
      Web uses = defUses.get(d);
      if (uses == null) {
        uses = new Web(loc, d);
      }
      uses.addStmt(use);
      defUses.put(d, uses);

      // Also, add this def's use web to this use's list of use webs.
      List<Web> webs = useToDefs.get(use);
      if (webs == null) {
        webs = new ArrayList<Web>();
      }
      webs.add(uses);
      useToDefs.put(use, webs);
      return;
    }    
    
    // If we don't have a local definition, then _every_ reaching definition
    // for this basic block which defines loc should have this as a possible
    // use.
    for (BasicStatement def : defs) {
      // Get the use web for this use's def, and add the use to it.
      Web uses = defUses.get(def);
      if (uses == null) {
        uses = new Web(loc, def);
      }
      uses.addStmt(use);
      defUses.put(def, uses);

      // Also, add this def's use web to this use's list of use webs.
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

    // For each use, we want to combine all webs which contain that use AND
    // target the same variable.
    for (Entry<BasicStatement, List<Web>> entry : useToDefs.entrySet()) {
      List<Web> webs = entry.getValue();
      
      // Iterate through all the webs that contain this use.
      Web finalWeb;
      for (Web web : webs) {
        // For each web, get the merged web corresponding to this variable.
        finalWeb = finalWebMap.get(web.desc());
        
        if (finalWeb == null) {
          // If the merged web doesn't exist, then it is just our current web.
          finalWeb = web;
        }
        else {
          // Otherwise, merge our current web into the final web.
          assert finalWeb.desc().equals(web.desc());
          finalWeb.union(web);
        }
        
        // And replace the final web with the new merged version.
        finalWebMap.put(finalWeb.desc(), finalWeb.find());
      }
      
      // Store all the merged webs we got in this round (eliminating duplicates)
      // and clear out the web map for the next round.
      finalWebSet.addAll(finalWebMap.values());
      finalWebMap.clear();
    }
    
    // Add all the merged webs to a list, and sort them, to make the coloring
    // algorithm behave deterministically (to make debugging easier).  See the
    // Web class for the sorting criteria.
    finalWebs.addAll(finalWebSet);
    
    Collections.sort(finalWebs);
    for (int i = 0; i < finalWebs.size(); i++) {
      webIndices.put(finalWebs.get(i), i);
    }
    
  }
  
  /**
   * Generates a WebLiveness object for each node, containing information about
   * webs become live and dead locally, within the basic block.  This 
   * information is then passed to a worklist algorithm to generate global web 
   * liveness information for the entire method.
   * 
   * Note: The reason we have to generate liveness info again, is that our first
   * pass generates info for variables, which is coarser-grained information
   * than information about webs.  Otherwise, the algorithm is the same.
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

    WebLiveness webLiveness = new WebLiveness(this, node, 
                                              genMap.values(), killSet);
    if (webLiveness.successors().isEmpty()) {
        webLiveness.setOut(bottom());
    }
    blockLiveness.put(node, webLiveness);
  }
  
  /**
   * Allocates the interference graph object and adds all webs as unconnected 
   * nodes.
   */
  private void initInterferenceGraph() {
    ig = new InterferenceGraph();
    for (Web w : finalWebs) {
      ig.addNode(w.find());
    }
  }

  /**
   * Generates the interference graph by doing a per-statement liveness
   * calculation for the given block, and noting which webs interfere.
   * @param node
   */
  private void generateInterferenceGraph(BasicBlockNode node) {
    WebLiveness wl = blockLiveness.get(node);
    List<Web> liveOnExit = wl.liveOnExit();
    HashMap<TypedDescriptor, Web> currentlyLive = 
      new HashMap<TypedDescriptor, Web>();
    
    assert wl != null;
    
    if (CLI.debug) {
      System.out.println("Processing == " + node.getId() + " ==");
    }

    // Link all ending nodes in the interference graph.
    int size = liveOnExit.size();
    for (int i = 0; i < size; i++) {
      Web w1 = liveOnExit.get(i);
      currentlyLive.put(w1.desc(), w1.find());
      for (int j = i+1; j < size; j++) {
        Web w2 = liveOnExit.get(j);
        ig.linkNodes(w1, w2);
        if (CLI.debug) {
          //System.out.println("Linking " + w1 + " and " + w2);
        }
      }
    }
    

    // Traverse backwards through the statement list to compute liveness
    List<BasicStatement> stmts = node.getStatements();
    Collections.reverse(stmts);
    
    if (CLI.debug) {
      for (Web w : currentlyLive.values()) {
        System.out.println("Starting live: " + w);
      }
    }

    ArrayList<Web> dying = new ArrayList<Web>();
    for (BasicStatement stmt : stmts) {
      if (CLI.debug) {
        System.out.println("Processing statement " + stmt);
      }
      
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
        if (CLI.debug) {
          System.out.println("Becoming dead " + web);
        }
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
            if (CLI.debug) {
              System.out.println("Becoming live " + w);
            }
            Web newWeb = w.find();
            currentlyLive.put(newWeb.desc(), newWeb);
            dying.add(newWeb);

            for (Web w2 : currentlyLive.values()) {
              if (CLI.debug) {
                //System.out.println("Linking " + w + " and " + w2);
              }
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
      for (int i = 0; i < numColors; i++) {
        Register reg = registerMap.get(i);
        methodDescriptor.markRegisterUsed(reg);
      }
      return;
    }
    else {
      
      // TODO: Take loop nesting into account in spill cost calculation
      // Tree maps used for determinism
      TreeMap<Integer, List<Web>> colorMap = new TreeMap<Integer, List<Web>>();
      for (Web w : finalWebs) {
        List<Web> list = colorMap.get(w.find().getColor());
        if (list == null) {
          list = new ArrayList<Web>();
        }
        list.add(w.find());
        colorMap.put(w.find().getColor(), list);
      }
      
      if (CLI.debug) {
        for (Integer i : colorMap.keySet()) {
          System.out.println("Webs colored with " + ":");
          for (Web w : colorMap.get(i)) {
            System.out.println("  " + w + " color: " + w.getColor());
          }
          System.out.println();
        }
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
      

      if (CLI.debug) {
        System.out.println("Web lists: ");
        for (List<Web> lw : webLists) {
          System.out.println("  Color " + lw.get(0).getColor());
        }
      }
      
      Register reg;
      for (int i = 0; i < NUM_REGISTERS && i < webLists.size(); i++) {
        reg = registerOrder.get(i);
        List<Web> list = webLists.get(i);
        
        // Assign a register to this color
        registerMap.put(list.get(0).getColor(), reg);
        
        if (CLI.debug) {
          System.out.println("Assigning " + reg + " to color " + list.get(0).getColor());
        }
        
        // Mark this register as used in the function
        methodDescriptor.markRegisterUsed(reg);
        
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
    int argTmpOffset = 0;
    
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
      else if (stmt instanceof FakeDefStatement) {
        Web web = defUses.get(stmt);
        if (web != null) {
          reg = registerMap.get(web.find().getColor());
          TypedDescriptor desc = web.find().desc();
          if (reg == null) {

            // Move the variable OUT of its register into a temporary
            int tmpOffset = startOfMethod.getNode().getSymbolTable().getNonconflictingOffset();
            
            // Update the argument temp offset, so if we do this for more
            // than one variable, they don't overlap
            tmpOffset += argTmpOffset;
            argTmpOffset -= 8;
            
            FakeDefStatement fds = (FakeDefStatement) stmt;
            Register oldReg = fds.getParam().getIndexRegister();
            if (oldReg != null) {
              VariableLocation loc = new StackLocation(tmpOffset);
              fds.getParam().setLocation(loc);

              BasicStatement bs = new OpStatement(startOfMethod.getNode(),
                  AsmOp.MOVE,
                  Argument.makeArgument(
                      new AnonymousDescriptor(
                          new RegisterLocation(oldReg),
                          null)),
                          Argument.makeArgument(fds.getParam()),
                          null);
              newStmts.add(bs);
            } 
          } else {
            if (desc.getLocation().getLocationType() == LocationType.REGISTER) {
              if (argReassign == null) {
                argReassign = new ArgReassignStatement(startOfMethod.getNode());
                newStmts.add(argReassign);  
              }
              argReassign.putRegPair(desc.getLocation().getRegister(), reg);
            }
            else {
              BasicStatement bs;
              bs = new OpStatement(startOfMethod.getNode(),
                  AsmOp.MOVE,
                  Argument.makeArgument(desc),
                  Argument.makeArgument(
                      new AnonymousDescriptor(
                          new RegisterLocation(reg),
                          web.find().desc())),
                          null);
              newStmts.add(bs);  
            }
            
          }
        }
        newStmts.add(stmt);
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
      
      // Using the web for the definition here, we want to update the register
      // for the target.
      Web web = defUses.get(stmt);
      if (web != null) {
        reg = registerMap.get(web.find().getColor());
        if (reg != null) {
          result = new AnonymousDescriptor(new RegisterLocation(reg),
                                           web.find().desc());
        }
      }
      
      List<Web> webs = useToDefs.get(stmt);
      if (webs != null) {
        arg1 = convertArg(webs, op.getArg1());
        arg2 = convertArg(webs, op.getArg2());
      }
      
      OpStatement newOp;
      if (op.getOp() == AsmOp.MOVE) {
        // Convert the descriptor to an argument.
        Argument resultArg = Argument.makeArgument(result);
        
        // If the argument is an array variable, we need to process more
        if (op.getTarget().getType() == ArgType.ARRAY_VARIABLE) {
          ArrayVariableArgument origTarget = 
              (ArrayVariableArgument) op.getTarget();
          Argument index = origTarget.getIndex();
          
          // The index uses are uses, so they need to be converted with the
          // use webs for this statement.
          if (webs != null) {
            index = convertArg(webs, index);
          }
          resultArg = Argument.makeArgument(result, index);
        }
        newOp = new OpStatement(op.getNode(), op.getOp(), 
                                     arg1, resultArg, null);
        
      } else {
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
    if (CLI.debug) {
      System.out.println("Live at " + oldStatement + ": " + liveWebs);
    }
    if (liveWebs != null) {
      for (Web w : liveWebs) {
        Register r = registerMap.get(w.find().getColor());
        if (r != null) {
          newStatement.setRegisterLiveness(r, true);
        }
      }
    }
    if (dyingWebs != null) {
      for (Web w : dyingWebs) {
        Register r = registerMap.get(w.find().getColor());
        if (r != null) {
          newStatement.setRegisterDying(r, true);
        }
      }
    }
  }
  
  // Given the list of colored webs, modify the argument to change all
  // variable references to refer to their new register.
  private Argument convertArg(List<Web> webs, Argument arg) {
    if (arg == null || !arg.isVariable()) {
      return arg;
    }
    
    Register reg;
    TypedDescriptor newDesc = arg.getDesc();
    for (Web w : webs) {
      if (w.desc().equals(arg.getDesc())) {
        reg = registerMap.get(w.find().getColor());
        if (reg != null) {
          newDesc = new AnonymousDescriptor(new RegisterLocation(reg),
                                            w.desc());
          break;
        }
      }  
    }
    
    // If the argument is an array variable, recursively convert the index.
    if (arg.getType() == ArgType.ARRAY_VARIABLE) {
      Argument newIndex = convertArg(webs,
                                     ((ArrayVariableArgument) arg).getIndex());
      return Argument.makeArgument(newDesc, newIndex);
    } else {
      return Argument.makeArgument(newDesc);
    }
  }
  
  
  private static class WebLiveness extends GenKillItem {
    public Collection<Web> theGen;
    public Collection<Web> theKill;
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
      
      // XXX: Debug only
      theGen = new ArrayList<Web>(gen);
      theKill =  new ArrayList<Web>(kill);
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
