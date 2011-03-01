package edu.mit.compilers.le02.semanticchecks;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ClassNode;
import edu.mit.compilers.le02.ast.MethodDeclNode;
import edu.mit.compilers.le02.ast.ReturnNode;
import edu.mit.compilers.le02.semanticchecks.SemanticException;

public class CheckReturnValues extends ASTNodeVisitor<Boolean> {
  /** Holds the CheckReturnValues singleton. */
  private static CheckReturnValues instance;
  private static String curMethod;
  private static DecafType curType;

  /**
   * Retrieves the CheckReturnValues singleton, creating if necessary.
   */
  public static CheckReturnValues getInstance() {
    if (instance == null) {
      instance = new CheckReturnValues();
    }
    return instance;
  }

  /**
   * Checks that no void method returns a value and that every other
   * method returns a value of the correct type.
   */
  public static void check(ASTNode root) {
    assert(root instanceof ClassNode);
    root.accept(getInstance());
  }

  @Override
  public Boolean visit(MethodDeclNode node) {
    curMethod = node.getName();
    curType = node.getType();

    defaultBehavior(node);
    return true;
  }

  @Override
  public Boolean visit(ReturnNode node) {
    if (curType == null) {
      defaultBehavior(node);
      return true;
    }

    if (!node.hasValue()) {
      if (curType != DecafType.VOID) {
      ErrorReporting.reportError(
        new SemanticException(node.getSourceLoc(),
          "Method " + curMethod + " must return " + curType));
      }
    } else {
      DecafType got = DecafType.simplify(node.getRetValue().getType());
      if ((got != null) && (got != curType)) {
        ErrorReporting.reportError(
          new SemanticException(node.getSourceLoc(),
            "Method " + curMethod + " should return " + curType +
            ", but it returned " + got + " instead"));
      }
    }

    defaultBehavior(node);
    return true;
  }
}
