package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.StackLocation;

public class LocalDescriptor extends TypedDescriptor {
  public LocalDescriptor(SymbolTable parent, String id, DecafType type, int offset) {
    super(parent, id, type);
    this.location = new StackLocation(offset);
  }
}
