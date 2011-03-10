package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.symboltable.Descriptor;

public class VariableArgument extends Argument {
  protected Descriptor desc;

  public VariableArgument(Descriptor desc) {
    this.desc = desc;
  }

  @Override
  public ArgType getType() {
    return ArgType.VARIABLE;
  }

  public Descriptor getDesc() {
    return desc;
  }

}
