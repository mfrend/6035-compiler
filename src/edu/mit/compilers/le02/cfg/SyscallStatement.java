package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.SystemCallNode;

public class SyscallStatement extends BasicStatement {

  private List<Argument> args;
  private VariableLocation result;

  public SyscallStatement(SystemCallNode node,
                          List<Argument> args,
                          VariableLocation result) {
    super(node);
    this.args = args;
    this.result = result;
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
