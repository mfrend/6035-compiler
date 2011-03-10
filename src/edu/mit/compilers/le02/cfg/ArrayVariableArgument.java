package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.symboltable.Descriptor;

public class ArrayVariableArgument extends VariableArgument {
  private Argument index;

  public ArrayVariableArgument(Descriptor desc, Argument index) {
    super(desc);
  }

  @Override
  public ArgType getType() {
    return ArgType.ARRAY_VARIABLE;
  }

  public Argument getIndex() {
    return index;
  }

}
