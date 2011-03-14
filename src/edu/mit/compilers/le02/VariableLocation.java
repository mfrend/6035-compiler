package edu.mit.compilers.le02;

import edu.mit.compilers.le02.RegisterLocation.Register;


public class VariableLocation {
  private int type;

  public static int UNDEFINED = 0;
  public static int STACK_LOCATION = 1;
  public static int REGISTER_LOCATION = 2;
  public static int GLOBAL_LOCATION = 1;

  public VariableLocation() {
    this.type = UNDEFINED;
  }

  public int getLocationType() {
    return this.type;
  }
  
  /**
   * Convenience method to cast this VariableLocation to a StackLocation
   * and get the stored offset.
   * @return Offset of location on the stack
   */
  public int getOffset() {
    assert this.type == STACK_LOCATION;
    return ((StackLocation) this).getOffset();
  }
  
  /**
   * Convenience method to cast this VariableLocation to a RegisterLocation
   * and get the stored register.
   * @return Register in which variable resides
   */
  public Register getRegister() {
    assert this.type == REGISTER_LOCATION;
    return ((RegisterLocation) this).getRegister();
  }

  /**
   * Convenience method to cast this VariableLocation to a GlobalLocation
   * and get the symbol at which the variable is stored.
   * @return Symbol at which the variable is stored.
   */
  public String getSymbol() {
    assert this.type == GLOBAL_LOCATION;
    return ((GlobalLocation) this).getSymbol();
  }
}
