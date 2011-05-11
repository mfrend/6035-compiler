package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class AnonymousDescriptor extends TypedDescriptor {
  // Replaced descriptor, for debugging in register allocation
  private TypedDescriptor desc = null;

  public AnonymousDescriptor(VariableLocation loc) {
    super(null, null, null);
    location = loc;
  }

  public AnonymousDescriptor(VariableLocation loc, TypedDescriptor desc) {
    super(null, null, null);
    location = loc;
    this.desc = desc;
  }

  @Override
  public String toString() {

    // Note replaced descriptor, for debugging in register allocation
    if (desc != null) {
      return "" + desc.getType() +
        "{" + desc.getId() + " / " + location.toString() + "}";
    }
    return location.toString();
  }

  @Override
  public boolean equals(Object o) {

    if (!(o instanceof AnonymousDescriptor)) {
      return false;
    }
    AnonymousDescriptor desc = (AnonymousDescriptor)o;
    return desc.getLocation().equals(location);
  }

  @Override
  public int hashCode() {
    return location.hashCode();
  }
}
