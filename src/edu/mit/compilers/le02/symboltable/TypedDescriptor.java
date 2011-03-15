package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.VariableLocation;

public abstract class TypedDescriptor extends Descriptor {
  private DecafType type;
  protected VariableLocation location;

  public TypedDescriptor(SymbolTable parent, String id, DecafType type) {
    super(parent, id);
    this.type = type;
    this.location = null;
  }

  public DecafType getType() {
    return type;
  }
  
  public VariableLocation getLocation() {
	  return this.location;
  }

  @Override
  public String toString() {
    return this.getType().toString();
  }

}
