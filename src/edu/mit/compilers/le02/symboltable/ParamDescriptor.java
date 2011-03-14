package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.StackLocation;

public class ParamDescriptor extends TypedDescriptor {

  public ParamDescriptor(SymbolTable parent, String id, DecafType type) {
    super(parent, id, type);
  }

  /**
   * Specifies the 1-indexed position of this parameter in the list of
   * function arguments.
   */
  public void setParamIndex(int index) {
    if (index <= 6) {
      this.location = new StackLocation(-(index * 8));
    }
    else {
      this.location = new StackLocation((index - 6) * 8 + 8);
    }
  }
}
