package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;

public class LocalDescriptor extends TypedDescriptor {
  private int index;

  public LocalDescriptor(SymbolTable parent, String id, DecafType type) {
    super(parent, id, type);
  }
}
