package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ArrayLocationNode;
import edu.mit.compilers.le02.ast.AssignNode;
import edu.mit.compilers.le02.ast.ClassNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.ForNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.LocationNode;
import edu.mit.compilers.le02.ast.MathOpNode;
import edu.mit.compilers.le02.ast.MethodCallNode;
import edu.mit.compilers.le02.ast.VariableNode;
import edu.mit.compilers.le02.ast.MathOpNode.MathOp;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class LoopMonotonicCode extends ASTNodeVisitor<Boolean> {
  private static LoopMonotonicCode instance;
  private static List<ForNode> fors;
  private static Set<TypedDescriptor> loopVars;
  private static Set<ExpressionNode> monotonicExprs;

  private static Map<ForNode, ForNode> pullup;
  private static Set<ForNode> flatFors;
  private static ForNode highestFlatFor;

  private class UntouchedLoopVariable extends ASTNodeVisitor<Boolean> {
    private TypedDescriptor loopVar;
    private boolean isField;
    private boolean untouched;

    private class UnpackExpression extends ASTNodeVisitor<Boolean> {
      private Set<TypedDescriptor> vars;

      // Returns a list of variables in an expression
      // Returns null if and only if the expression includes any
      // array accesses or method calls
      public Set<TypedDescriptor> listVars(ExpressionNode root) {
        vars = new HashSet<TypedDescriptor>();
        root.accept(this);
        return vars;
      }

      @Override
      public Boolean visit(MethodCallNode node) {
        vars = null;
        return true;
      }

      @Override
      public Boolean visit(VariableNode node) {
        if (node.getLoc() instanceof ArrayLocationNode) {
          vars = null;
        } else if (vars != null) {
          vars.add(node.getLoc().getDesc());
        }
        return true;
      }
    }

    // Checks if an expression is loop-invariant
    public boolean check(ForNode root, ExpressionNode expr) {
      Set<TypedDescriptor> vars = new UnpackExpression().listVars(expr);
      if (vars == null) {
        return false;
      }

      for (TypedDescriptor var : vars) {
        if (!check(root, var, true)) {
          return false;
        }
      }

      return true;
    }

    // Checks if a variable is loop-invariant
    public boolean check(ForNode root, TypedDescriptor var,
        boolean includeLoopBounds) {
      loopVar = var;
      isField = (var instanceof FieldDescriptor);
      untouched = true;

      if (includeLoopBounds) {
        root.accept(this);
      } else {
        root.getBody().accept(this);
      }
      return untouched;
    }

    @Override
    public Boolean visit(AssignNode node) {
      if (node.getLoc().getDesc() == loopVar) {
        untouched = false;
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
    fors = new ArrayList<ForNode>();
    loopVars = new HashSet<TypedDescriptor>();
    monotonicExprs = new HashSet<ExpressionNode>();

    pullup = new HashMap<ForNode, ForNode>();
    flatFors = new HashSet<ForNode>();
    highestFlatFor = null;

    assert(root instanceof ClassNode);
    root.accept(getInstance());

    /*
    for (ForNode node : pullup.keySet()) {
      System.out.println("Loop " + node.getInit().getLoc().getDesc() +
          " can be pulled up to " +
          pullup.get(node).getInit().getLoc().getDesc());
    }

    System.out.println("");
    for (ForNode node : flatFors) {
      System.out.println("Loop " + node.getInit().getLoc().getDesc() +
          " is flat");
    }
    */
  }

  public static Set<ExpressionNode> getMonotonicExprs() {
    return monotonicExprs;
  }

  public static Set<ForNode> getFlatFors() {
    return flatFors;
  }

  @Override
  public Boolean visit(ForNode node) {
    // If no higher nodes are flat, than this node is the current
    // highest-known flat node
    if (highestFlatFor == null) {
      highestFlatFor = node;
    }
    pullupForNode(node);

    fors.add(node);
    TypedDescriptor loopVar = node.getInit().getLoc().getDesc();
    if (new UntouchedLoopVariable().check(node, loopVar, false)) {
      loopVars.add(loopVar);
    }

    defaultBehavior(node);
    loopVars.remove(loopVar);
    fors.remove(node);

    // The current node is flat if and only if the current highest
    // flat node is at least as high as it
    if (highestFlatFor != null) {
      flatFors.add(node);
      // If this node is the highest flat node, then after popping
      // it, no higher nodes are flat
      if (highestFlatFor == node) {
        highestFlatFor = null;
      }
    }
    return true;
  }

  // A ForNode A can be pulled up to a ForNode B above it if the bounds
  // of node A are invariant while B is executing
  // This method pulls up every ForNode as far as possible
  // A node is flat if every ForNode in its subtree can be pulled up to
  // its level, or higher
  private void pullupForNode(ForNode node) {
    // If this ForNode is an outer loop, it cannot be pulled up
    if (fors.isEmpty()) {
      pullup.put(node, node);
      return;
    }

    ExpressionNode lowerBound = node.getInit().getValue();
    ExpressionNode upperBound = node.getEnd();
    UntouchedLoopVariable visitor = new UntouchedLoopVariable();

    int size = fors.size();
    ForNode cur = node;
    ForNode next;

    for (int i = 0; i < size; i++) {
      // We do not pull up ForNodes above loops which are not
      // completely flat
      if (cur == highestFlatFor) {
        break;
      }

      // If the lower and upper bounds of this loop are invariant
      // in the next higher loop, pull it up
      next = fors.get(size - 1 - i);
      if (visitor.check(next, lowerBound) &&
          visitor.check(next, upperBound)) {
        cur = next;
      } else {
        break;
      }
    }

    // The level to which this node can be pulled up is an upper
    // bound on the current flattest node
    highestFlatFor = cur;
    pullup.put(node, cur);
  }

  @Override
  public Boolean visit(MathOpNode node) {
    if (fors.isEmpty()) {
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

    if (monotonic) {
      monotonicExprs.add(node);
      /*
      System.out.println("Found monotonic math op:");
      node.accept(new AstPrettyPrinter());
      System.out.println("");
      */
    }
    return monotonic;
  }

  @Override
  public Boolean visit(VariableNode node) {
    LocationNode loc = node.getLoc();
    if (loc instanceof ArrayLocationNode) {
      defaultBehavior(node);
      return false;
    } else {
      if (loopVars.contains(loc.getDesc())) {
        monotonicExprs.add(node);
        return true;
      }
      return false;
    }
  }

  @Override
  public Boolean visit(IntNode node) {
    monotonicExprs.add(node);
    return true;
  }
}
