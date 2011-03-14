package edu.mit.compilers.le02;

import edu.mit.compilers.le02.VariableLocation;

public class StackLocation extends VariableLocation {
  private int offset;

  public StackLocation(int offset) {
    this.offset = offset;
  }

  public int getOffset() {
    return this.offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }
}
