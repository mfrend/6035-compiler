package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public final class OpStatement extends BasicStatement {
  private AsmOp op;
  private Argument arg1, arg2;

  public enum AsmOp {
    MOVE(true, true),
    ADD(true, true),
    SUBTRACT(true, true),
    MULTIPLY(false, true),
    DIVIDE(false, false),
    MODULO(false, false),
    UNARY_MINUS(false, true),
    EQUAL(true, false),
    NOT_EQUAL(true, false),
    LESS_THAN(true, false),
    LESS_OR_EQUAL(true, false),
    GREATER_THAN(true, false),
    GREATER_OR_EQUAL(true, false),
    NOT(false, true),
    RETURN(false, false),
    ENTER(false, false),
    PUSH(false, false),
    POP(false, true),
    ARRAY(false, true),
    ;
    public boolean acceptsImmediateArg() {
      return immedOk;
    }
    public boolean mutatesArgs() {
      return mutatesArgs;
    }
    private AsmOp(boolean immedOk, boolean mutatesArgs) {
      this.immedOk = immedOk;
      this.mutatesArgs = mutatesArgs;
    }
    private boolean immedOk;
    private boolean mutatesArgs;
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

  public Argument getArg2() {
    return arg2;
  }

  public Argument getTarget() {
    if (op == AsmOp.MOVE) {
      return arg2;
    } else {
      return Argument.makeArgument(result);
    }
  }

  public boolean expressionEquals(OpStatement other) {
    return op.equals(other.op) &&
      ((arg1 != null) ? arg1.equals(other.arg1) : (other.arg1 == null)) &&
      ((arg2 != null) ? arg2.equals(other.arg2) : (other.arg2 == null));
  }

  @Override
  public String toString() {
    String s = "OpStatement" + uid + "(" + op +
      ((arg1 != null) ? ", " + arg1 : "") +
      ((arg2 != null && op != AsmOp.MOVE) ? ", " + arg2 : "") +
      ")" +
      ((result != null) ? ": " + result : "") +
      ((op == AsmOp.MOVE) ? ": " + arg2 : "");
    
    s += " LIVE: ";
    for (Register r : registerLiveness.getLiveRegisters()) {
      s += r + " ";
    }
    return s;
  }

}
