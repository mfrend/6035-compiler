package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.symboltable.Descriptor;

public final class OpStatement extends BasicStatement {
  private Op op;
  private Argument arg1, arg2;
  private Descriptor result;
  
  public enum Op {
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
    RETURN
  }

  public OpStatement(ExpressionNode expr, Op op, Argument arg1, Argument arg2,
                     Descriptor result) {
    super(expr);
    this.op = op;
    this.arg1 = arg1;
    this.arg2 = arg2;
    this.result = result;
  }

  public Op getOp() {
    return op;
  }

  public Argument getArg1() {
    return arg1;
  }

  public Argument getArg2() {
    return arg2;
  }

  public Descriptor getResult() {
    return result;
  }

  
}
