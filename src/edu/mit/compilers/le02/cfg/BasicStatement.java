package edu.mit.compilers.le02.cfg;

import java.util.List;

import edu.mit.compilers.le02.ast.ASTNode;

public abstract class BasicStatement {
  private ASTNode node;
  
  public BasicStatement(ASTNode node) {
    this.node = node;
  }

  public ASTNode getNode() {
    return node;
  }
  
  abstract public List<BasicStatement> flatten();

}
