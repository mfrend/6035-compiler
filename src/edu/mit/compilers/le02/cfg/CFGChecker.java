package edu.mit.compilers.le02.cfg;

public class CFGChecker {
  
  
  
  /**
   * Assert invariants of basic block control flow graphs
   * 
   * 1. All nodes are connected to only one method head
   * 2. All basic block conditionals are boolean values
   */
  public static void checkBasicBlockCFG(ControlFlowGraph cfg) {
    
  }


  public static void checkNode(BasicBlockNode node) {
    // XXX: Checking code here
    
    checkNode(node.getNext());
    if (node.isBranch()) {
      checkNode(node.getBranchTarget());
    }
  }
}
