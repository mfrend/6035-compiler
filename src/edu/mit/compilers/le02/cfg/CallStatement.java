package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;

public final class CallStatement extends BasicStatement {
  private MethodDescriptor method;
  private List<Argument> args;
  private VariableLocation result;

  public CallStatement(ExpressionNode expr, MethodDescriptor method,
                       List<Argument> args, VariableLocation result) {
    super(expr);
    this.method = method;
    this.args = args;
    this.result = result;
  }

  public MethodDescriptor getMethod() {
    return method;
  }

  public List<Argument> getArgs() {
    return new ArrayList<Argument>(args);
  }

  public VariableLocation getResult() {
    return result;
  }
  
  @Override
  public List<BasicStatement> flatten() {
    return Collections.singletonList((BasicStatement) this);
  }

}
