package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class ArrayVariableArgument extends VariableArgument {
  private Argument index;

  public ArrayVariableArgument(TypedDescriptor loc, Argument index) {
    super(loc);
    this.index = index;
  }

  @Override
  public ArgType getType() {
    return ArgType.ARRAY_VARIABLE;
  }

  public Argument getIndex() {
    return index;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ArrayVariableArgument)) return false;

    ArrayVariableArgument other = (ArrayVariableArgument) o;
    return this.loc.equals(other.loc)
           && this.index.equals(other.index);
  }

  @Override
  public String toString() {
    return loc.toString() + "[" + index + "]";
  }
}
