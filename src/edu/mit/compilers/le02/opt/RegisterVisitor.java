package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import edu.mit.compilers.le02.dfa.Liveness;
import edu.mit.compilers.le02.dfa.ReachingDefinitions;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class RegisterVisitor extends BasicBlockVisitor {
  private Map<BasicStatement, Web> defUses;
  private Map<BasicStatement, List<Web>> useToDefs;
  private Map<BasicBlockNode, WebLiveness> blockLiveness;
  private Map<Integer, Register> registerMap;
  private List<Web> finalWebs;
  private InterferenceGraph ig;
  private ReachingDefinitions rd;
  private Liveness liveness;
  private Pass pass;
  

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
    
    // Generate DU chains
    visitor.pass = Pass.GENERATE_DU;
    visitor.visit(methodHead);
    
    
    // Generate webs from DU chains
    visitor.combineWebs();
    
    for (Web w : visitor.finalWebs) {
      System.out.println(w);      
    }
    
    
    // Annotate blocks with web liveness info
    visitor.pass = Pass.GENERATE_WEB_LIVENESS;
    visitor.visit(methodHead);
    
    // Generate interference graph from annotated blocks
    visitor.initInterferenceGraph();
    visitor.pass = Pass.GENERATE_IG;
    visitor.visit(methodHead);
    
    visitor.allocateRegisters();
    System.out.println("== AFTER COLORING ==");
    for (Web w : visitor.finalWebs) {
      System.out.println(w);      
    }
    visitor.pass = Pass.INSERT_REGISTERS;
    visitor.visit(methodHead);
  }
  
  public RegisterVisitor(ReachingDefinitions rd, Liveness liveness) {
    this.rd = rd;
    this.liveness = liveness;
    
    this.defUses = new HashMap<BasicStatement, Web>();
    this.useToDefs = new HashMap<BasicStatement, List<Web>>();
    this.blockLiveness = new HashMap<BasicBlockNode, WebLiveness>();
    this.finalWebs = new ArrayList<Web>();
    this.registerMap = new HashMap<Integer, Register>();
    this.pass = Pass.GENERATE_DU;
    
    this.registerMap.put(0, Register.RAX);
    this.registerMap.put(1, Register.RBX);
    this.registerMap.put(2, Register.RCX);
    this.registerMap.put(3, Register.RDX);
    this.registerMap.put(4, Register.RSI);
    this.registerMap.put(5, Register.RDI);
    this.registerMap.put(6, Register.R8);
    this.registerMap.put(7, Register.R9);
    this.registerMap.put(8, Register.R12);
    this.registerMap.put(9, Register.R13);
    this.registerMap.put(10, Register.R14);
    this.registerMap.put(11, Register.R15);
    this.registerMap.put(12, Register.R10);
    this.registerMap.put(13, Register.R11);
  }
  

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
            result = new AnonymousDescriptor(new RegisterLocation(reg));
          }
        }
        
        CallStatement call = (CallStatement) stmt;
        List<Web> webs = useToDefs.get(stmt);
        List<Argument> args = call.getArgs();
        System.out.println("Checking call " + call);
        if (webs != null) {
          for (int i = 0; i < args.size(); i++) {
            Argument arg = args.get(i);
            arg = convertArg(webs, arg);
            args.set(i, arg);
          }
        }
       
        newStmts.add(new CallStatement(((ExpressionNode) call.getNode()), 
                                       call.getMethodName(), 
                                       args, 
                                       result, 
                                       call.isCallout()));
        continue;
      }
      else if (stmt.getType() != BasicStatementType.OP) {
        newStmts.add(stmt);
        continue;
      }
      
      OpStatement op = (OpStatement) stmt;
      Argument arg1 = op.getArg1();
      Argument arg2 = op.getArg2();
      result = op.getResult();
      
      Web web = defUses.get(stmt);
      if (web != null) {
        reg = registerMap.get(web.find().getColor());
        if (reg != null) {
          result = new AnonymousDescriptor(new RegisterLocation(reg));
        }
      }
      
      List<Web> webs = useToDefs.get(stmt);
      System.out.println("Checking op " + op);
      if (webs != null) {
        arg1 = convertArg(webs, op.getArg1());
        arg2 = convertArg(webs, op.getArg2());
      }
      
      if (op.getOp() == AsmOp.MOVE) {
        newStmts.add(new OpStatement(op.getNode(), op.getOp(), 
                                     arg1, Argument.makeArgument(result),
                                     null));
      }
      else {
        newStmts.add(new OpStatement(op.getNode(), op.getOp(), 
                                     arg1, arg2, result));
      }
    }
    node.setStatements(newStmts);
  }
  
  private Argument convertArg(List<Web> webs, Argument arg) {
    Register reg;
    for (Web w : webs) {
      System.out.println("Desc: " + w.desc());
      if (w.desc().equals(arg.getDesc())) {
        reg = registerMap.get(w.find().getColor());
        System.out.println("Found arg " + arg + " reg: " + reg);
        if (reg != null) {
          return Argument.makeArgument(new AnonymousDescriptor(
                                           new RegisterLocation(reg)));
        }
      }
    }
    return arg;
  }
  
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
    if (numColors <= 14) {
      // Assign registers to everything, since we're ok (also the register
      // map is setup in the initialization).
      return;
    }
    else {
      // We have effectively 12 registers (since we need r10 and r11 for spill
      // values), so spill everything else
      Collections.sort(finalWebs, new Comparator<Web>() {
        // Want the highest spill costs to be first in the list
        @Override
        public int compare(Web w1, Web w2) {
          return -(w1.getSpillCost() - w2.getSpillCost());
        }
      });
      
      Register reg;
      for (int i = 0; i < 12; i++) {
        reg = registerMap.get(i);
        registerMap.put(finalWebs.get(i).getColor(), reg);
      }
    }
  }
  
  /**
   * Generates the def-use chains (in Web form) for a given basic block.
   * @param node The given basic block
   */
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
            defs = bi.getReachingDefinitions(desc.getLocation());
            addDefUse(call, desc, defs, localDefs);
          }
        }

        if (call.getResult() != null) {
          localDefs.put(call.getResult(), call);
        }
        
        for (TypedDescriptor d : localDefs.keySet()) {
          if (d.getLocation().getLocationType() == LocationType.GLOBAL) {
            localDefs.remove(d);
          }
        }
        continue;
      }
      
      // Only ops are in def-use chains
      else if (stmt.getType() != BasicStatementType.OP) {
        continue;
      }

      OpStatement op = (OpStatement)stmt;
      if (op.getArg1().getType() == ArgType.VARIABLE) {
        desc = op.getArg1().getDesc();
        defs = bi.getReachingDefinitions(desc.getLocation());
        addDefUse(op, desc, defs, localDefs);
      }
      
      if (op.getArg2().getType() == ArgType.VARIABLE) {
        desc = op.getArg2().getDesc();
        defs = bi.getReachingDefinitions(desc.getLocation());
        addDefUse(op, desc, defs, localDefs);
      }
      
      if (op.getTarget() != null) {
        localDefs.put(op.getTarget().getDesc(), op);
      }
    }
  }
  
  private void initInterferenceGraph() {
    ig = new InterferenceGraph();
    for (Web w : finalWebs) {
      ig.addNode(w);
    }
  }

  private void generateInterferenceGraph(BasicBlockNode node) {
    WebLiveness wl = blockLiveness.get(node);
    HashMap<TypedDescriptor, Web> currentlyActive = 
      new HashMap<TypedDescriptor, Web>();
    
    assert wl != null;

    // Link all starting nodes at beginning
    int size = wl.liveOnEnter.size();
    for (int i = 0; i < size; i++) {
      Web w1 = wl.liveOnEnter.get(i);
      currentlyActive.put(w1.desc(), w1);
      for (int j = i+1; j < size; j++) {
        Web w2 = wl.liveOnEnter.get(j);
        ig.linkNodes(w1, w2);
      }
    }
    
    for (BasicStatement stmt : node.getStatements()) {
      // Only ops are in def-use chains
      if (stmt.getType() != BasicStatementType.OP
          && stmt.getType() != BasicStatementType.CALL) {
        continue;
      }
      
      Web web = defUses.get(stmt);
      if (web == null) {
        continue;
      }
      
      Web oldWeb = currentlyActive.get(web.desc());
      if (web.find() != oldWeb) {
        Web newWeb = web.find();
        currentlyActive.put(newWeb.desc(), newWeb);
        
        for (Web w : currentlyActive.values()) {
          ig.linkNodes(newWeb, w);
        }
      }
    }
  }
  
  private void generateBlockLivenessInfo(BasicBlockNode node) {
    HashMap<TypedDescriptor, Web> currentlyActive = 
        new HashMap<TypedDescriptor, Web>();
    
    WebLiveness wl = blockLiveness.get(node);
    if (wl == null) { 
      wl = new WebLiveness();
    }
    
    for (BasicStatement stmt : node.getStatements()) {
      // Only ops are in def-use chains
      if (stmt.getType() != BasicStatementType.OP 
          && stmt.getType() != BasicStatementType.CALL) {
        continue;
      }
      
      Web web = defUses.get(stmt);
      if (web == null) {
        continue;
      }
      
      currentlyActive.put(web.desc(), web.find());
    }
    
    Liveness.BlockItem bi = liveness.getBlockItem(node);
    for (TypedDescriptor desc : currentlyActive.keySet()) {
      if (bi.isLiveOnExit(desc)) {
        wl.liveOnEnter.add(currentlyActive.get(desc));
      }
    }
    blockLiveness.put(node, wl);

    if (node.getNext() != null) {
      wl = blockLiveness.get(node.getNext());
      if (wl == null) { 
        wl = new WebLiveness();
      }
      
      bi = liveness.getBlockItem(node.getNext());
      for (TypedDescriptor desc : currentlyActive.keySet()) {
        if (bi.isLiveOnExit(desc)) {
          wl.liveOnExit.add(currentlyActive.get(desc));
        }
      }
      blockLiveness.put(node.getNext(), wl);
    }
    
    if (node.getBranchTarget() != null) {
      wl = blockLiveness.get(node.getBranchTarget());
      if (wl == null) { 
        wl = new WebLiveness();
      }
      
      bi = liveness.getBlockItem(node.getBranchTarget());
      for (TypedDescriptor desc : currentlyActive.keySet()) {
        if (bi.isLiveOnExit(desc)) {
          wl.liveOnExit.add(currentlyActive.get(desc));
        }
      }
      blockLiveness.put(node.getBranchTarget(), wl);
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
      
      finalWebs.addAll(finalWebMap.values());
      finalWebMap.clear();
    }
  }

  @Override
  protected void processNode(BasicBlockNode node) {
    switch (pass) {
      case GENERATE_DU:
        generateDefUses(node);
        break;
      case GENERATE_WEB_LIVENESS:
        generateBlockLivenessInfo(node);
        break;
      case GENERATE_IG:
        generateInterferenceGraph(node);
        break;
      case INSERT_REGISTERS:
        insertRegisters(node);
        break;
    }
  }
  
  private static class WebLiveness {
    public List<Web> liveOnEnter = new ArrayList<Web>();
    public List<Web> liveOnExit = new ArrayList<Web>();
  }
}
