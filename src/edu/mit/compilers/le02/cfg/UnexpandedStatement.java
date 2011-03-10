package edu.mit.compilers.le02.cfg;

import java.util.List;

import edu.mit.compilers.le02.ast.ASTNode;

public final class UnexpandedStatement extends BasicStatement {

  public UnexpandedStatement(ASTNode node) {
    super(node);
  }

  @Override
  public List<BasicStatement> flatten() {
    return ExpressionFlattener.flatten(this);
  }
}
