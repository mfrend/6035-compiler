package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.ASTNode;

public abstract class BasicStatement {
  private ASTNode node;
  protected VariableLocation result;
  protected BasicStatementType type;
  
  public enum BasicStatementType {
    DUMMY,
    ARGUMENT,
    OP,
    CALL,
    JUMP
  }
  
  public BasicStatement(ASTNode node, VariableLocation result) {
    this.node = node;
    this.result = result;
  }

  public ASTNode getNode() {
    return node;
  }
  
  public VariableLocation getResult() {
    return result;
  }
  
  public BasicStatementType getType() {
    return type;
  }
  
}
