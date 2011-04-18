package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public abstract class BasicStatement {
  private ASTNode node;
  protected TypedDescriptor result;
  protected BasicStatementType type;

  public enum BasicStatementType {
    ARGUMENT,
    OP,
    CALL,
    NOP,
    JUMP
  }

  public BasicStatement(ASTNode node, TypedDescriptor result) {
    this.node = node;
    this.result = result;
  }

  public ASTNode getNode() {
    return node;
  }

  public TypedDescriptor getResult() {
    return result;
  }

  public void setResult(TypedDescriptor desc) {
    result = desc;
  }

  public BasicStatementType getType() {
    return type;
  }

}
