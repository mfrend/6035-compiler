package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ArrayLocationNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.ForNode;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class ArrayBoundsChecks extends ASTNodeVisitor<Boolean> {
  private static ArrayBoundsChecks instance;
  private static List<ArrayLocationNode> arrays;
  private static Map<TypedDescriptor, ExpressionNode> lowerBounds;
  private static Map<TypedDescriptor, ExpressionNode> upperBounds;

  private static ArrayBoundsChecks getInstance() {
    if (instance == null) {
      instance = new ArrayBoundsChecks();
    }
    return instance;
  }

  /**
   * Returns the list of expressions which appear as array indices in
   * a subtree of the AST
   */
  public static void findArrayAccesses(ASTNode root) {
    arrays = new ArrayList<ArrayLocationNode>();
    lowerBounds = new HashMap<TypedDescriptor, ExpressionNode>();
    upperBounds = new HashMap<TypedDescriptor, ExpressionNode>();

    root.accept(getInstance());
  }

  public static List<ArrayLocationNode> getAccesses() {
    return arrays;
  }

  public static Map<TypedDescriptor, ExpressionNode> getLowerBounds() {
    return lowerBounds;
  }

  public static Map<TypedDescriptor, ExpressionNode> getUpperBounds() {
    return upperBounds;
  }

  @Override
  public Boolean visit(ForNode node) {
    TypedDescriptor loopVar = node.getInit().getLoc().getDesc();
    lowerBounds.put(loopVar, node.getInit().getValue());
    upperBounds.put(loopVar, node.getEnd());
    defaultBehavior(node);
    return true;
  }

  @Override
  public Boolean visit(ArrayLocationNode node) {
    arrays.add(node);
    defaultBehavior(node);
    return true;
  }
}
