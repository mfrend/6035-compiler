package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.opt.CseVariable;


public final class ConstantArgument extends Argument implements CseVariable {
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

  @Override
  public DecafType getFlattenedType() {
    if (isInt) {
      return DecafType.INT;
    }
    else {
      return DecafType.BOOLEAN;
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
    } else {
      return this.b == other.b;
    }
  }

  @Override
  public int hashCode() {
    if (this.isInt) {
      return this.i;
    } else {
      return (this.b) ? 1 : 0;
    }
  }

  @Override
  public String toString() {
    if (this.isInt) {
      return "$" + i;
    }
    else if (b) {
      return "$1";
    } else {
      return "$0";
    }
  }


}
