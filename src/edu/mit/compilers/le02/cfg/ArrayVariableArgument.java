package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.cfg.Argument.ArgType;
import edu.mit.compilers.le02.symboltable.Location;

public class ArrayVariableArgument extends VariableArgument {
  private Argument index;

  public ArrayVariableArgument(Location loc, Argument index) {
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
