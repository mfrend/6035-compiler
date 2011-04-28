package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class CallStatement extends BasicStatement {
  private List<Argument> args;
  private String name;
  private boolean callout;

  public CallStatement(ExpressionNode expr, String name, List<Argument> args,
                       TypedDescriptor result, boolean callout) {
    super(expr, result);
    this.name = name;
    this.args = args;
    this.type = BasicStatementType.CALL;
    this.callout = callout;
  }

  public String getMethodName() {
    return name;
  }

  public List<Argument> getArgs() {
    return new ArrayList<Argument>(args);
  }

  public boolean isCallout() {
    return callout;
  }

  public void setArgs(List<Argument> newArgs) {
    args = newArgs;
  }

  @Override
  public String toString() {
    String s = "CallStatement" + uid + "(" + name;
    for (Argument a : args) {
      s += ", " + a;
    }
    s += ")";

    if (result != null) {
      s += ": " + result;
    }
    
    s += " LIVE: ";
    for (Register r : registerLiveness.getLiveRegisters()) {
      s += r + " ";
    }
    return s;
  }

}
