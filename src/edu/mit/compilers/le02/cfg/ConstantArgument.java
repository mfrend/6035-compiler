package edu.mit.compilers.le02.cfg;


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
    this.isInt = false;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ConstantArgument)) return false;
    
    ConstantArgument other = (ConstantArgument) o;
    if (this.isInt != other.isInt) {
      return false;
    }
    
    if (this.isInt) {
      return this.i == other.i;
    }
    else {
      return this.b == other.b;
    }
  }

  
}
