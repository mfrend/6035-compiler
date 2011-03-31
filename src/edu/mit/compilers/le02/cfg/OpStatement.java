package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

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
    ENTER,
  }

  public OpStatement(ASTNode node, AsmOp op, Argument arg1, Argument arg2,
                     TypedDescriptor result) {
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

  public void setArg1(Argument a) {
    arg1 = a;
  }

  public Argument getArg2() {
    return arg2;
  }

  public void setArg2(Argument a) {
    arg2 = a;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OpStatement)) return false;

    OpStatement other = (OpStatement) o;
    return expressionEquals(other) &&
      ((result != null) ? result.equals(other.result) : other.result == null);
  }

  public boolean expressionEquals(OpStatement other) {
    return op.equals(other.op) &&
      ((arg1 != null) ? arg1.equals(other.arg1) : (other.arg1 == null)) &&
      ((arg2 != null) ? arg2.equals(other.arg2) : (other.arg2 == null));
  }

  @Override
  public int hashCode() {
    return ((op != null) ? 1 + op.hashCode() : 0) +
      ((arg1 != null) ? 1 + arg1.hashCode() : 0) +
      ((arg2 != null) ? 1 + arg2.hashCode() : 0) +
      ((result != null) ? 1 + result.hashCode() : 0);
  }

  @Override
  public String toString() {
    return "OpStatement(" + op +
      ((arg1 != null) ? ", " + arg1 : "") +
      ((arg2 != null && op != AsmOp.MOVE) ? ", " + arg2 : "") +
      ")" +
      ((result != null) ? ": " + result : "") +
      ((op == AsmOp.MOVE) ? ": " + arg2 : "");
  }

}
