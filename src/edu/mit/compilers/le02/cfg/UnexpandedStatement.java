package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ExpressionNode;

public final class UnexpandedStatement extends BasicStatement {

  public UnexpandedStatement(ExpressionNode expr) {
    super(expr);
  }
}
