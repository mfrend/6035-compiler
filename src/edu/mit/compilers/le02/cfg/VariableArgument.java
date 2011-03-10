package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.VariableLocation;

public class VariableArgument extends Argument {
  private VariableLocation loc;

  public VariableArgument(VariableLocation loc) {
    this.loc = loc;
  }

  @Override
  public ArgType getType() {
    return ArgType.VARIABLE;
  }

  public VariableLocation getLoc() {
    return loc;
  }

}
