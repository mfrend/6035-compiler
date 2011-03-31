package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class VariableArgument extends Argument {
  protected TypedDescriptor loc;

  public VariableArgument(TypedDescriptor loc) {
    this.loc = loc;
  }

  @Override
  public ArgType getType() {
    return ArgType.VARIABLE;
  }

  @Override
  public TypedDescriptor getLoc() {
    return loc;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VariableArgument)) return false;

    VariableArgument other = (VariableArgument) o;
    return this.loc.equals(other.loc);
  }
  
  @Override
  public int hashCode() {
    return this.loc.hashCode();
  }

  @Override
  public String toString() {
    return this.loc.toString();
  }

  @Override
  public boolean isVariable() {
    return true;
  }

}
