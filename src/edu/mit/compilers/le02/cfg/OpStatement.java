package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.ASTNode;

public final class OpStatement extends BasicStatement {
  private AsmOp op;
  private Argument arg1, arg2;
  
  public enum AsmOp {
    MOVE,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MODULO,
    UNARY_MINUS,
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    GREATER_THAN,
    GREATER_OR_EQUAL,
    NOT,
    RETURN,
    ENTER
  }

  public OpStatement(ASTNode node, AsmOp op, Argument arg1, Argument arg2,
                     VariableLocation result) {
    super(node, result);
    this.op = op;
    this.arg1 = arg1;
    this.arg2 = arg2;
    this.type = BasicStatementType.OP;
  }
  
  public AsmOp getOp() {
    return op;
  }

  public Argument getArg1() {
    return arg1;
  }

  public Argument getArg2() {
    return arg2;
  }
}
