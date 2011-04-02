package edu.mit.compilers.le02.opt;

import edu.mit.compilers.le02.DecafType;

/**
 * Signals that processing of a statement in a Visitor should proceed
 * to the next statement.
 */
public class SkipProcessing implements CseVariable {

  private SkipProcessing() {

  }

  private static SkipProcessing instance = new SkipProcessing();

  public static SkipProcessing getInstance() {
    return instance;
  }

  @Override
  public DecafType getFlattenedType() {
    return null;
  }
}
