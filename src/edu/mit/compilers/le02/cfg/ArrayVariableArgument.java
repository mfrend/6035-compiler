package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.VariableLocation;

public class ArrayVariableArgument extends VariableArgument {
  private Argument index;

  public ArrayVariableArgument(VariableLocation loc, Argument index) {
    super(loc);
  }

  @Override
  public ArgType getType() {
    return ArgType.ARRAY_VARIABLE;
  }

  public Argument getIndex() {
    return index;
  }

}
