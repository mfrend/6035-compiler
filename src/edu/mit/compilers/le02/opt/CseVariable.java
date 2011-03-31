package edu.mit.compilers.le02.opt;

import edu.mit.compilers.le02.DecafType;

/**
 * Represents a CSE variable (either a constant ConstantArgument or a specific
 * TypedDescriptor corresponding to a variable).
 */
public interface CseVariable {
  public DecafType getFlattenedType();
}
