package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ASTNode;

public final class JumpStatement extends BasicStatement {
  public JumpStatement(ASTNode node) {
    super(node, null);
    this.type = BasicStatementType.JUMP;
  }
  
  @Override
  public String toString() {
    return "JumpStatement";
  }
}
