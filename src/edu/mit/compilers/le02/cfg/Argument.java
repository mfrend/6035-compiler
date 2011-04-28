package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.opt.CseVariable;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public abstract class Argument {
  public enum ArgType {
    ARRAY_VARIABLE,
    VARIABLE,
    CONST_INT,
    CONST_BOOL
  }

  abstract public ArgType getType();
  abstract public boolean isVariable();

  public TypedDescriptor getDesc() {
    return null;
  }

  public static Argument makeArgument(CseVariable var) {
    if (var instanceof TypedDescriptor) {
      return makeArgument((TypedDescriptor)var);
    }
    return (Argument)var;
  }

  public static Argument makeArgument(TypedDescriptor loc) {
    return new VariableArgument(loc);
  }

  public static Argument makeArgument(TypedDescriptor loc,
                                      Argument index) {
    return new ArrayVariableArgument(loc, index);
  }

  public static Argument makeArgument(int i) {
    return new ConstantArgument(i);
  }

  public static Argument makeArgument(boolean b) {
    return new ConstantArgument(b);
  }
  
  public boolean isRegister() {
    if (this.getType() != ArgType.VARIABLE) {
      return false;
    }
    
    return (getDesc().getLocation().getLocationType() == LocationType.REGISTER);
  }

}
