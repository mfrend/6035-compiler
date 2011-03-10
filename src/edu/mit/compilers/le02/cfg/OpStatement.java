package edu.mit.compilers.le02.cfg;

import java.util.Collections;
import java.util.List;

import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.VariableLocation;

public final class OpStatement extends BasicStatement {
  private Op op;
  private Argument arg1, arg2;
  private VariableLocation result;
  
  public enum Op {
    MOVE("mov"),
    ADD("add"),
    SUBTRACT("sub"),
    MULTIPLY("mul"),
    DIVIDE("div"),
    MODULO("mod"),
    UNARY_MINUS(""), // help, this shouldn't be here. need to already have transformed to (0 - temp)
    NOT(""),
    EQUAL("cmp"),
    NOT_EQUAL("cmp"),
    LESS_THAN("eq"),
    LESS_OR_EQUAL("le"),
    GREATER_THAN("gt"),
    GREATER_OR_EQUAL("ge"),
    RETURN("return");

    private String opcode;

    private Op(String code) {
      opcode = code;
    }
    @Override
    public String toString() {
      return opcode;
    }
  }

  public OpStatement(ASTNode node, Op op, Argument arg1, Argument arg2,
                     VariableLocation result) {
    super(node);
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

  public VariableLocation getResult() {
    return result;
  }

  @Override
  public List<BasicStatement> flatten() {
    return Collections.singletonList((BasicStatement) this);
  }

  
}
