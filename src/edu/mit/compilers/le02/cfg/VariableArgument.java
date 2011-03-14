package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.VariableLocation;

public class VariableArgument extends Argument {
  protected VariableLocation loc;

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
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VariableArgument)) return false;
    
    VariableArgument other = (VariableArgument) o;
    return this.loc.equals(other.loc);
  }

}
