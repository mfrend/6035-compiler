package edu.mit.compilers.le02.cfg;

public class CFGChecker {



  /**
   * Assert invariants of basic block control flow graphs
   *
   * 1. All nodes are connected to only one method head
   * 2. All basic block conditionals are boolean values
   * 3. All basic blocks that don't jump anywhere end in return
   * 4. Only OpStatements and CallStatements
   * 5. All method heads have an ENTER OpStatement as their first statement
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
