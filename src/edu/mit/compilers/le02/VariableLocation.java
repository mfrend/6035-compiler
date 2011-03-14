package edu.mit.compilers.le02;

import edu.mit.compilers.le02.RegisterLocation.Register;


public class VariableLocation {
  protected LocationType type;

  public enum LocationType {
    UNDEFINED,
    STACK,
    REGISTER,
    GLOBAL;
  }

  public VariableLocation() {
    this.type = LocationType.UNDEFINED;
  }

  public LocationType getLocationType() {
    return this.type;
  }
  
  /**
   * Convenience method to cast this VariableLocation to a StackLocation
   * and get the stored offset.
   * @return Offset of location on the stack
   */
  public int getOffset() {
    assert this.type == LocationType.STACK;
    return ((StackLocation) this).getOffset();
  }
  
  /**
   * Convenience method to cast this VariableLocation to a RegisterLocation
   * and get the stored register.
   * @return Register in which variable resides
   */
  public Register getRegister() {
    assert this.type == LocationType.REGISTER;
    return ((RegisterLocation) this).getRegister();
  }

  /**
   * Convenience method to cast this VariableLocation to a GlobalLocation
   * and get the symbol at which the variable is stored.
   * @return Symbol at which the variable is stored.
   */
  public String getSymbol() {
    assert this.type == LocationType.GLOBAL;
    return ((GlobalLocation) this).getSymbol();
  }
}
