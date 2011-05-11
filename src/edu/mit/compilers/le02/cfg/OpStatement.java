package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public final class OpStatement extends BasicStatement {
  private AsmOp op;
  private Argument arg1, arg2;

  public enum AsmOp {
    MOVE(true, true, false),
    ADD(true, true, false),
    SUBTRACT(true, true, true),
    MULTIPLY(false, true, false),
    DIVIDE(false, false, false),
    MODULO(false, false, false),
    UNARY_MINUS(false, true, false),
    EQUAL(true, false, true),
    NOT_EQUAL(true, false, true),
    LESS_THAN(true, false, true),
    LESS_OR_EQUAL(true, false, true),
    GREATER_THAN(true, false, true),
    GREATER_OR_EQUAL(true, false, true),
    NOT(false, true, false),
    RETURN(false, false, false),
    ENTER(false, false, false),
    PUSH(false, false, false),
    POP(false, true, false),
    ARRAY(false, true, false),
    ;
    public boolean acceptsImmediateArg() {
      return immedOk;
    }
    public boolean mutatesArgs() {
      return mutatesArgs;
    }
    public boolean inverted() {
      return invert;
    }
    private AsmOp(boolean immedOk, boolean mutatesArgs, boolean invert) {
      this.immedOk = immedOk;
      this.mutatesArgs = mutatesArgs;
      this.invert = invert;
    }
    private boolean immedOk;
    private boolean mutatesArgs;
    private boolean invert;
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
