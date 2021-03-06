package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ASTNode;

public class NOPStatement extends BasicStatement {
  public NOPStatement(ASTNode node) {
    super(node, null);
    this.type = BasicStatementType.NOP;
  }

  @Override
  public String toString() {
    return "NOPStatement";
  }
}
