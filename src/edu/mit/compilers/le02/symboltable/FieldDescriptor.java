package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.GlobalLocation;

public class FieldDescriptor extends TypedDescriptor {

  private int length;

  public FieldDescriptor(SymbolTable parent, String id, DecafType type) {
    this(parent, id, type, 0);
  }

  public FieldDescriptor(SymbolTable parent, String id, DecafType type,
      int length) {
    super(parent, id, type);
    this.location = new GlobalLocation(id);
    this.length = length;
  }

  public int getLength() {
    return length;
  }
}
