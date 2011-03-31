package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.opt.CseVariable;

public abstract class TypedDescriptor
    extends Descriptor implements CseVariable {
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

  @Override
  public boolean equals(Object o) {

    if (!(o instanceof TypedDescriptor)) {
      return false;
    }
    TypedDescriptor desc = (TypedDescriptor)o;
    return desc != null && getLocation().equals(desc.getLocation());
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }

  public boolean isLocalTemporary() {
    return getId().matches("^[0-9]+" + LOCAL_TEMP_SUFFIX + "$");
  }

  public static String LOCAL_TEMP_SUFFIX = "lcltmp";
}
