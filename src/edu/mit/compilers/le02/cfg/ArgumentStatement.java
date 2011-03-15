package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ASTNode;

public final class ArgumentStatement extends BasicStatement {
  private Argument arg;

  public ArgumentStatement(ASTNode node, Argument arg) {
    super(node, null);
    this.type = BasicStatementType.ARGUMENT;
    this.arg = arg;
  }

  public Argument getArgument() {
    return arg;
  }

  @Override
  public String toString() {
    return "ArgumentStatement(" + arg + ")";
  }
}
