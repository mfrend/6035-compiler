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
    ENTER,
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OpStatement)) return false;

    OpStatement other = (OpStatement) o;
    return op.equals(other.op)
           && arg1.equals(other.arg1)
           && arg2.equals(other.arg2)
           && result.equals(other.result);
  }
  
  @Override
  public int hashCode() {
    int hc = 0;
    if (op != null) { 
      hc += 1 + op.hashCode();
    }
    if (arg1 != null) { 
      hc += 1 + arg1.hashCode();
    }
    if (arg2 != null) { 
      hc += 1 + arg2.hashCode();
    }
    if (result != null) { 
      hc += 1 + result.hashCode();
    }
    
    return hc;
  }

  @Override
  public String toString() {
    String s = "OpStatement(" + op;
    if (arg1 != null) {
      s += ", " + arg1;
    }
    if (arg2 != null && op != AsmOp.MOVE) {
      s += ", " + arg2;
    }
    s += ")";
    if (result != null) {
      s += ": " + result;
    }
    if (op == AsmOp.MOVE) {
      s += ": " + arg2;
    }
    return s;
  }

}
