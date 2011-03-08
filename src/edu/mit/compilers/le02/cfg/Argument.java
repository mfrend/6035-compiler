package edu.mit.compilers.le02.cfg;

public abstract class Argument {
  public enum ArgType {
    VARIABLE,
    CONST_INT,
    CONST_BOOL
  }
  
  abstract public ArgType getType();

}
