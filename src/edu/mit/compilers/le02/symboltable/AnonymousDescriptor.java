package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class AnonymousDescriptor extends TypedDescriptor {

  public AnonymousDescriptor(VariableLocation loc) {
    super(null, null, null);
    location = loc;
  }

  @Override
  public String toString() {
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
