package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.StackLocation;
import edu.mit.compilers.le02.VariableLocation;

public class LocalDescriptor extends TypedDescriptor
    implements Comparable<LocalDescriptor> {
  public LocalDescriptor(SymbolTable parent, String id,
                         DecafType type, int offset) {
    super(parent, id, type);
    this.location = new StackLocation(offset);
  }

  @Override
  public int compareTo(LocalDescriptor o) {
    return this.getLocation().getOffset() - o.getLocation().getOffset();
  }
}
