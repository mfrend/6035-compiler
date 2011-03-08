package edu.mit.compilers.le02.cfg;

import edu.mit.compilers.le02.symboltable.Descriptor;

public abstract class Argument {
  public enum ArgType {
    VARIABLE,
    CONST_INT,
    CONST_BOOL
  }
  
  abstract public ArgType getType();
  
  public static Argument makeArgument(Descriptor d) {
    return new VariableArgument(d);
  }
  
  public static Argument makeArgument(int i) {
    return new ConstantArgument(i);
  }
  
  public static Argument makeArgument(boolean b) {
    return new ConstantArgument(b);
  }

}
