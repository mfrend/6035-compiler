package edu.mit.compilers.le02;


public class StackLocation extends VariableLocation {
  private int offset;

  public StackLocation(int offset) {
    this.type = LocationType.STACK;
    this.offset = offset;
  }

  /**
   * @return The offset of the variable from the frame pointer (%rbp), in bytes
   */
  public int getOffset() {
    return this.offset;
  }

  /**
   * Set the offset of the varible from the frame pointer in bytes.
   */
  public void setOffset(int offset) {
    this.offset = offset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StackLocation)) return false;
    
    StackLocation other = (StackLocation) o;
    return this.offset == other.offset;
  }
  
  @Override
  public String toString() {
    return offset + "(%rbp)";
  }
}
