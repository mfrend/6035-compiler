package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;

public class BasicBlockGraph {
  private static int id;
  
  public static String nextID() {
    return Integer.toString(id++);
  }
  
  public static ControlFlowGraph makeBasicBlockGraph(ControlFlowGraph cfg) {
    ControlFlowGraph newCFG = new ControlFlowGraph();

    id = 0;
    for (String methodName : cfg.getMethods()) {
      CFGNode node = cfg.getMethod(methodName);
      assert (node instanceof SimpleCFGNode);
      SimpleCFGNode enter = (SimpleCFGNode) node;
      
      BasicBlockNode methodEnter = makeBasicBlock(methodName, enter);
      
      
      // Places an enter statement with the desired offset
      int localOffset = -getLargestLocalOffset(methodEnter);
      // TODO: Find a suitable source location to put in here
      OpStatement enterStmt = new OpStatement(null, AsmOp.ENTER,
                                Argument.makeArgument(localOffset),
                                null, null);
      methodEnter.prependStatement(enterStmt);
      
      cfg.putMethod(methodName, methodEnter);
      
    }
   
    return newCFG;
  }
  
  public static int getLargestLocalOffset(BasicBlockNode node) {
    if (node == null) {
      return 0;
    }
    
    int min = node.largestLocalOffset();
    int curr;
    curr = getLargestLocalOffset(node.getNext());
    if (curr < min) {
      min = curr;
    }
    
    if (node.isBranch()) {
      curr = getLargestLocalOffset(node.getBranchTarget());
      if (curr < min) {
        min = curr;
      }
    }
    
    return min;
  }
  
  
  public static BasicBlockNode makeBasicBlock(String id,
                                              SimpleCFGNode start) {
    BasicBlockNode currBB = new BasicBlockNode(id);
    SimpleCFGNode currNode = start;
    
    while (currNode != null
           && !currNode.isBranch() && !currNode.hasMultipleEntrances()) {
      
      switch (currNode.getStatement().getType()) {
        case DUMMY:
        case ARGUMENT:
          break;
        case OP:
        case CALL:
          currBB.addStatement(currNode.getStatement());
        default:
          ErrorReporting.reportErrorCompat(
              new Exception("Unexpected statement type " 
                  + currNode.getStatement().getType()));
          break;
      }
      currNode = currNode.getNext();
    }
    
    if (currNode == null) {
      BasicStatement st = currBB.getLastStatement();
      
      // Insert return if it is not there.
      if (st.getType() != BasicStatementType.OP
          || ((OpStatement) st).getOp() != AsmOp.RETURN) {
        
        currBB.addStatement(new OpStatement(st.getNode(), AsmOp.RETURN, 
                                            null, null, null));        
      }
      return currBB;
    }
    else if (currNode.isBranch()) {
      BasicBlockNode branchTarget = makeBasicBlock(nextID(), 
                                                   currNode.getBranchTarget());
      currBB.setBranchTarget(branchTarget);
    }

    BasicBlockNode next = makeBasicBlock(nextID(), currNode.getNext());
    currBB.setNext(next);
    
    return currBB;
  }

}
