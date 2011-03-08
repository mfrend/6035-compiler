package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.symboltable.Descriptor;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;

public final class CallStatement extends BasicStatement {
  private MethodDescriptor method;
  private List<Argument> args;
  private Descriptor result;
  
  public CallStatement(ExpressionNode expr, MethodDescriptor method,
                       List<Argument> args, Descriptor result) {
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
  
  public Descriptor getResult() {
    return result;
  }
  
}
