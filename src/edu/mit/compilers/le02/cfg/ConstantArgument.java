package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.symboltable.Descriptor;

public final class ConstantArgument extends Argument {
  private int i;
  private boolean b;
  private boolean isInt;
  
  public ConstantArgument(int i) {
    this.i = i;
    this.isInt = true;
  }

  public ConstantArgument(boolean b) {
    this.b = b;
    this.isInt = true;
  }

  @Override
  public ArgType getType() {
    if (isInt) {
      return ArgType.CONST_INT;
    }
    else {
      return ArgType.CONST_BOOL;
    }
  }

  public int getInt() {
    return i;
  }

  public boolean getBool() {
    return b;
  }

}
