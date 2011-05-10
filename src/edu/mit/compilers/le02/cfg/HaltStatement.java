package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ASTNode;

public final class HaltStatement extends BasicStatement {
  public HaltStatement(ASTNode node) {
    super(node, null);
    this.type = BasicStatementType.HALT;
  }

  @Override
  public String toString() {
    return "HaltStatement";
  }
}
