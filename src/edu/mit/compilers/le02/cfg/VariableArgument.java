package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.symboltable.Location;

public class VariableArgument extends Argument {
  private Location loc;

  public VariableArgument(Location loc) {
    this.loc = loc;
  }

  @Override
  public ArgType getType() {
    return ArgType.VARIABLE;
  }

  public Location getLoc() {
    return loc;
  }

}
