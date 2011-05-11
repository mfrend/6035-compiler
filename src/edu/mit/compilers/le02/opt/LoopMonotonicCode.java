package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.ast.AssignNode;
import edu.mit.compilers.le02.ast.AstPrettyPrinter;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.BoolOpNode;
import edu.mit.compilers.le02.ast.BoolOpNode.BoolOp;
import edu.mit.compilers.le02.ast.ClassNode;
import edu.mit.compilers.le02.ast.IfNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.ForNode;
import edu.mit.compilers.le02.ast.LocationNode;
import edu.mit.compilers.le02.ast.MathOpNode;
import edu.mit.compilers.le02.ast.MathOpNode.MathOp;
import edu.mit.compilers.le02.ast.MethodCallNode;
import edu.mit.compilers.le02.ast.MinusNode;
import edu.mit.compilers.le02.ast.NotNode;
import edu.mit.compilers.le02.ast.ScalarLocationNode;
import edu.mit.compilers.le02.ast.VariableNode;
import edu.mit.compilers.le02.semanticchecks.SemanticException;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class LoopMonotonicCode extends ASTNodeVisitor<Boolean> {
  private static final boolean verbose = true;
  private static LoopMonotonicCode instance;
  private static List<ForNode> fors;
  private static Set<TypedDescriptor> loopVars;

  private class UntouchedLoopVariable extends ASTNodeVisitor<Boolean> {
    private TypedDescriptor loopVar;
    private boolean isField;
    private boolean untouched;

    // Checks if a loop var is never altered during loop execution
    public boolean check(ASTNode root, TypedDescriptor var) {
      loopVar = var;
      isField = (var instanceof FieldDescriptor);
      untouched = true;

      assert(root instanceof ForNode);
      ((ForNode)root).getBody().accept(this);
      return untouched;
    }

    @Override
    public Boolean visit(AssignNode node) {
      if (node.getLoc().getDesc() == loopVar) {
        untouched = false;
        System.out.println(node.getLoc().getDesc());
      }
      return true;
    }

    @Override
    public Boolean visit(MethodCallNode node) {
      if (isField) {
        untouched = false;
      }
      return true;
    }
  }

  public static LoopMonotonicCode getInstance() {
    if (instance == null) {
      instance = new LoopMonotonicCode();
    }
    return instance;
  }

  /**
   * Calculates upper and lower bounds for every integer ExpressionNode
   * in the AST, using conservative logic
   */
  public static void findMonotonicCode(ASTNode root) {
    loopVars = new HashSet<TypedDescriptor>();

    assert(root instanceof ClassNode);
    root.accept(getInstance());
  }

  @Override
  public Boolean visit(ForNode node) {
    TypedDescriptor loopVar = node.getInit().getLoc().getDesc();
    if (new UntouchedLoopVariable().check(node, loopVar)) {
      loopVars.add(loopVar);
    }

    defaultBehavior(node);
    loopVars.remove(loopVar);
    return true;
  }

  @Override
  public Boolean visit(MathOpNode node) {
    if (loopVars.size() == 0) {
      return false;
    }

    MathOp op = node.getOp();
    boolean left = (node.getLeft().accept(this) == true);
    boolean right = (node.getRight().accept(this) == true);
    boolean monotonic = false;

    if (op == MathOp.ADD) {
      monotonic = left && right;
    } else if (op == MathOp.SUBTRACT) {
      monotonic = left && (node.getRight() instanceof IntNode);
    } else if (op == MathOp.MULTIPLY) {
      monotonic = left && (node.getRight() instanceof IntNode) &&
          (((IntNode)node.getRight()).getValue() >= 0);
      if (!monotonic) {
        monotonic = right && (node.getLeft() instanceof IntNode) &&
            (((IntNode)node.getLeft()).getValue() >= 0);
      }
    } else if (op == MathOp.DIVIDE) {
      monotonic = left && (node.getRight() instanceof IntNode) &&
          (((IntNode)node.getRight()).getValue() > 0);
    }

    if (monotonic && verbose) {
      System.out.println("Found monotonic math op:");
      node.accept(new AstPrettyPrinter());
      System.out.println("");
    }
    return monotonic;
  }

  @Override
  public Boolean visit(VariableNode node) {
    LocationNode loc = node.getLoc();
    if (loc instanceof ScalarLocationNode) {
      return loopVars.contains(loc.getDesc());
    } else {
      defaultBehavior(node);
      return false;
    }
  }

  @Override
  public Boolean visit(IntNode node) {
    return true;
  }
}
