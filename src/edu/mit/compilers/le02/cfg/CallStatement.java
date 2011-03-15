package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.ExpressionNode;

public class CallStatement extends BasicStatement {
  private List<Argument> args;
  private String name;

  public CallStatement(ExpressionNode expr, String name, List<Argument> args, 
                       VariableLocation result) {
    super(expr, result);
    this.name = name;
    this.args = args;
    this.type = BasicStatementType.CALL;
  }

  public String getMethodName() {
    return name;
  }

  public List<Argument> getArgs() {
    return new ArrayList<Argument>(args);
  }


  @Override
  public String toString() {
    String s = "CallStatement(" + name;
    for (Argument a : args) {
      s += ", " + a;
    }
    s += ")";
    return s;
  }

}
