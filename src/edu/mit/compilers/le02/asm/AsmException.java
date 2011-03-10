package edu.mit.compilers.le02.asm;

import edu.mit.compilers.le02.semanticchecks.SemanticException;
import edu.mit.compilers.le02.SourceLocation;


/**
 * Represents an exception discovered in the AsmWriter.
 */
public class AsmException extends SemanticException {
  public AsmException(SourceLocation loc) {
    super(loc);
  }
  public AsmException(SourceLocation loc, String text) {
    super(loc, text);
  }
  public AsmException(int line, int col, String msg) {
    super(line, col, msg);
  }
  public AsmException(int line, int col) {
    super(line, col);
  }
}
