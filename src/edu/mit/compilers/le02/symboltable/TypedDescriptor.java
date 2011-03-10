package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;

public abstract class TypedDescriptor extends Descriptor {
  private DecafType type;
  private Location location;

  public TypedDescriptor(SymbolTable parent, String id, DecafType type) {
    super(parent, id);
    this.type = type;
    this.location = new Location();
  }

  public DecafType getType() {
    return type;
  }
  
  public Location getLocation(){
	  return this.location;
  }

  @Override
  public String toString() {
    return this.getType().toString();
  }

}
