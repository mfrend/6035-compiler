package edu.mit.compilers.le02.semanticchecks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ClassNode;
import edu.mit.compilers.le02.ast.MethodCallNode;
import edu.mit.compilers.le02.ast.MethodDeclNode;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public class CheckMethodCalls extends ASTNodeVisitor<Boolean> {
  /** Holds the CheckMethodCalls singleton. */
  private static CheckMethodCalls instance;
  private SymbolTable methodTable;
  private Set<String> processedMethods;

  /**
   * Retrieves the CheckMethodCalls singleton, creating if necessary.
   */
  public static CheckMethodCalls getInstance() {
    if (instance == null) {
      instance = new CheckMethodCalls();
    }
    return instance;
  }

  /**
   * Checks that every method call passes the correct number and type of
   * arguments.
   */
  public static void check(ASTNode root) {
    assert(root instanceof ClassNode);
    root.accept(getInstance());
  }

  @Override
  public Boolean visit(ClassNode node) {
    methodTable = node.getDesc().getSymbolTable();
    processedMethods = new HashSet<String>();

    List<MethodDeclNode> methods = node.getMethods();
    for (MethodDeclNode mdn : methods) {
      mdn.accept(this);
    }
    return true;
  }

  @Override
  public Boolean visit(MethodDeclNode node) {
    processedMethods.add(node.getName());

    defaultBehavior(node);
    return true;
  }

  @Override
  public Boolean visit(MethodCallNode node) {
    MethodDescriptor methodDesc = methodTable.getMethod(node.getName());
    if (methodDesc == null) {
      defaultBehavior(node);
      return true;
    }

    if (!processedMethods.contains(methodDesc.getId())) {
      ErrorReporting.reportError(new SemanticException(node.getSourceLoc(),
          "Method " + node.getName() + " called before its declaration."));
      return false;
    }

    if (methodDesc.getParams().size() != node.getArgs().size()) {
      ErrorReporting.reportError(new SemanticException(node.getSourceLoc(),
        "Method " + node.getName() + " expects " +
        methodDesc.getParams().size() +
        " arguments, got " + node.getArgs().size()));
    } else {
      for (int i = 0; i < methodDesc.getParams().size(); i++) {
        DecafType expected = DecafType.simplify(
          methodDesc.getParams().get(i).getType());
        DecafType got = DecafType.simplify(node.getArgs().get(i).getType());

        if ((expected != null) && (got != null) && (expected != got)) {
          ErrorReporting.reportError(
            new SemanticException(node.getArgs().get(i).getSourceLoc(),
              "Argument " + i + " to method " + node.getName() +
              " should be of type " + expected + ", but was " + got));
        }
      }
    }

    defaultBehavior(node);
    return true;
  }
}
