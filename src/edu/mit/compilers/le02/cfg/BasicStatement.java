package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ExpressionNode;

public abstract class BasicStatement {
  private ExpressionNode expr;
  
  public BasicStatement(ExpressionNode expr) {
    this.expr = expr;
  }

  public ExpressionNode getExpr() {
    return expr;
  }

}
