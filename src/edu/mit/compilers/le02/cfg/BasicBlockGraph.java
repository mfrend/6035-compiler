package edu.mit.compilers.le02.cfg;

import java.util.HashMap;
import java.util.Map;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;

public class BasicBlockGraph {
  private static int id;
  private static Map<SimpleCFGNode, BasicBlockNode> visited =
    new HashMap<SimpleCFGNode, BasicBlockNode>();

  public static String nextID() {
    id++;
    return ".block" + Integer.toString(id);
  }

  public static ControlFlowGraph makeBasicBlockGraph(ControlFlowGraph cfg) {
    ControlFlowGraph newCFG = new ControlFlowGraph();

    id = -1;
    for (String methodName : cfg.getMethods()) {
      CFGNode node = cfg.getMethod(methodName);
      assert (node instanceof SimpleCFGNode);
      SimpleCFGNode enter = (SimpleCFGNode) node;

      visited.clear();
      BasicBlockNode methodEnter = makeBasicBlocks(methodName, enter);

      for (BasicBlockNode n : visited.values()) {
        if (n.getStatements().isEmpty()) {
          n.removeFromCFG();
        }
      }

      // Places an enter statement with the desired offset
      int localOffset = -getLargestLocalOffset();
      // TODO: Find a suitable source location to put in here
      OpStatement enterStmt = new OpStatement(null, AsmOp.ENTER,
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
