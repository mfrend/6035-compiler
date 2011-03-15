package edu.mit.compilers.le02.cfg;

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
      
      makeBasicBlock(cfg, methodName, enter);
    }
   
    return newCFG;
  }
  
  
  public static BasicBlockNode makeBasicBlock(ControlFlowGraph cfg,
                                              String id,
                                              SimpleCFGNode start) {
    BasicBlockNode currBB = new BasicBlockNode(id);
    SimpleCFGNode currNode = start;
    
    
    while (currNode != null
           && !currNode.isBranch() && !currNode.hasMultipleEntrances()) {
      
      currBB.addStatement(currNode.getStatement());
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
      BasicBlockNode branchTarget = makeBasicBlock(cfg, nextID(), 
                                                   currNode.getBranchTarget());
      currBB.setBranchTarget(branchTarget);
    }

    BasicBlockNode next = makeBasicBlock(cfg, nextID(), currNode.getNext());
    currBB.setNext(next);
    
    return currBB;
  }

}
