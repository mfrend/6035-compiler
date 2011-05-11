package edu.mit.compilers.le02.cfg;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.Main.Optimization;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.dfa.DeadCodeElimination;
import edu.mit.compilers.le02.dfa.Liveness;
import edu.mit.compilers.le02.opt.BasicBlockVisitor;
import edu.mit.compilers.le02.opt.CpVisitor;
import edu.mit.compilers.le02.opt.CseVisitor;
import edu.mit.compilers.le02.opt.GlobalCseVisitor;
import edu.mit.compilers.le02.opt.RegisterVisitor;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public class BasicBlockGraph {
  private static int id;
  private static Map<SimpleCFGNode, BasicBlockNode> visited =
    new HashMap<SimpleCFGNode, BasicBlockNode>();

  public static String nextID() {
    id++;
    return ".block" + Integer.toString(id);
  }

  public static ControlFlowGraph makeBasicBlockGraph(ControlFlowGraph cfg,
      EnumSet<Optimization> opts) {
    ControlFlowGraph newCFG = new ControlFlowGraph();

    id = -1;
    for (String methodName : cfg.getMethods()) {
      CFGNode node = cfg.getMethod(methodName);
      assert (node instanceof SimpleCFGNode);
      SimpleCFGNode enter = (SimpleCFGNode) node;

      visited.clear();
      BasicBlockNode methodEnter = makeBasicBlocks(methodName, enter);

      Iterator<BasicBlockNode> iter = visited.values().iterator();
      while (iter.hasNext()) {
        BasicBlockNode n = iter.next();
        if (n.getStatements().isEmpty()) {
          n.removeFromCFG();
          iter.remove();
        }
      }

      // Run local CP
      if (opts.contains(Optimization.COPY_PROPAGATION)) {
        CpVisitor cp = new CpVisitor();
        cp.visit(methodEnter);
      }

      // Run local and global CSE
      if (opts.contains(Optimization.COMMON_SUBEXPR)) {
        BasicBlockVisitor cse = new CseVisitor();
        cse.visit(methodEnter);
        GlobalCseVisitor.performGlobalCse(methodEnter);
      }

      // Run local CP
      if (opts.contains(Optimization.COPY_PROPAGATION)) {
        CpVisitor cp = new CpVisitor();
        cp.visit(methodEnter);
      }

      // Run global dead code elimination.
      if (opts.contains(Optimization.DEAD_CODE)) {
        Liveness live = new Liveness(methodEnter);
        new DeadCodeElimination(methodEnter, live.getBlockItems());
      }

      ASTNode enterNode = methodEnter.getStatements().get(0).getNode();
      SymbolTable st = enterNode.getSymbolTable();
      MethodDescriptor md = st.getMethod(methodEnter.getMethod());

      // Remove any BasicBlockNodes that are empty after optimizations
      for (BasicBlockNode n : visited.values()) {
        if (n.getStatements().isEmpty()) {
          n.removeFromCFG();
        }
      }

      RegisterVisitor rv = null;
      // Run register allocation.
      if (opts.contains(Optimization.REGISTER_ALLOCATION)) {
        rv = RegisterVisitor.runRegisterAllocation(methodEnter, md);
      }

      // All of these optimizations change the number of local variables.
      // That's okay - we don't call getLargestLocalOffset until after
      // optimization is finished.

      // Places an enter statement with the desired offset
      int localOffset = -getLargestLocalOffset();

      // Adjust local offset count.
      if (opts.contains(Optimization.REGISTER_ALLOCATION)) {
        localOffset -= rv.getArgTempOffset();
      }

      // TODO: Find a suitable source location to put in here
      OpStatement enterStmt = new OpStatement(enterNode, AsmOp.ENTER,
                                Argument.makeArgument(localOffset),
                                null, null);
      methodEnter.prependStatement(enterStmt);


      newCFG.putMethod(methodName, methodEnter);
    }

    for (String name : cfg.getGlobals()) {
      newCFG.putGlobal(name, cfg.getGlobal(name));
    }

    for (String name : cfg.getAllStringData()) {
      newCFG.putStringData(name, cfg.getStringData(name));
    }

    return newCFG;
  }

  public static int getLargestLocalOffset() {

    int min = 0;
    int curr;
    for (BasicBlockNode n : visited.values()) {
      curr = n.largestLocalOffset();
      if (curr < min) {
        min = curr;
      }
    }

    return min;
  }

  private static void addStatement(BasicBlockNode node, BasicStatement st) {
    switch (st.getType()) {
     case JUMP:
     case ARGUMENT:
      break;
     case OP:
     case CALL:
     case NOP:
     case HALT:
      node.addStatement(st);
      break;
     default:
      ErrorReporting.reportErrorCompat(
        new Exception("Unexpected statement type " + st.getType()));
      break;
    }
  }
  public static BasicBlockNode makeBasicBlocks(String id,
                                               SimpleCFGNode start) {
    return makeBasicBlock(id, start, null);
  }

  public static BasicBlockNode makeBasicBlock(String id, SimpleCFGNode start,
                                              BasicBlockNode parent) {
    BasicBlockNode currBB;
    if (parent == null) {
      currBB = new BasicBlockNode(id, id);
    }
    else {
      currBB = new BasicBlockNode(id, parent.getMethod());
    }

    SimpleCFGNode currNode = start;

    if (visited.containsKey(start)) {
      return visited.get(start);
    }


    while (currNode != null
           && !currNode.isBranch() && !currNode.hasMultipleEntrances()) {
      SimpleCFGNode nextNode = currNode.getNext();
      addStatement(currBB, currNode.getStatement());
      currNode = nextNode;
    }

    visited.put(start, currBB);

    if (currNode != null) {
      // There are still more statements in this method
      addStatement(currBB, currNode.getStatement());

      if (currNode.isBranch()) {
        BasicBlockNode branchTarget =
          makeBasicBlock(nextID(), currNode.getBranchTarget(), currBB);
        currBB.setBranchTarget(branchTarget);
      }

      BasicBlockNode next =
        makeBasicBlock(nextID(), currNode.getNext(), currBB);
      currBB.setNext(next);
    }
    return currBB;
  }

}
