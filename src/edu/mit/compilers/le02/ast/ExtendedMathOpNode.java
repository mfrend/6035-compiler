package edu.mit.compilers.le02.ast;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.ast.MathOpNode.MathOp;

/**
 * Creates a canonicalized version of a MathOpNode to allow ((2*3)*5) to equal
 * (2*(3*5))
 *
 * @author Maria Frendberg(mfrend)
 *
 */
public class ExtendedMathOpNode {
  private MathOp parentOp;  // Will only be MathOp.ADD or MathOp.MULTIPLY.
  private List<ExpressionNode> left;
  private List<ExpressionNode> right;
  private MathOpNode node;

  public ExtendedMathOpNode(ExpressionNode n, MathOp op) {
    this.parentOp = op;
    left = new ArrayList<ExpressionNode>();
    right = new ArrayList<ExpressionNode>();
    if (n instanceof MathOpNode) {
      this.node = (MathOpNode) n;
      this.expand();
    } else {
      this.left.add(n);
    }
  }

  public void expand() {
    if (this.parentOp == null) { // Op is not defined by parent
      if (node.getOp() == MathOp.ADD) {
        this.parentOp = MathOp.ADD;
        ExtendedMathOpNode n1 = new ExtendedMathOpNode(
            this.node.left, MathOp.ADD);
        ExtendedMathOpNode n2 = new ExtendedMathOpNode(
            this.node.right, MathOp.ADD);

        this.left.addAll(n1.left);
        this.left.addAll(n2.left);

        this.right.addAll(n1.right);
        this.right.addAll(n2.right);

      } else if (node.getOp() == MathOp.SUBTRACT) {
        this.parentOp = MathOp.ADD;
        ExtendedMathOpNode n1 = new ExtendedMathOpNode(
            this.node.left, MathOp.ADD);
        ExtendedMathOpNode n2 = new ExtendedMathOpNode(
            this.node.right, MathOp.ADD);

        this.left.addAll(n1.left);
        this.left.addAll(n2.right);

        this.right.addAll(n1.right);
        this.right.addAll(n2.left);

      } else if (node.getOp() == MathOp.MULTIPLY) {
        this.parentOp = MathOp.MULTIPLY;
        ExtendedMathOpNode n1 = new ExtendedMathOpNode(
            this.node.left, MathOp.MULTIPLY);
        ExtendedMathOpNode n2 = new ExtendedMathOpNode(
            this.node.right, MathOp.MULTIPLY);

        this.left.addAll(n1.left);
        this.left.addAll(n2.left);

        this.right.addAll(n1.right);
        this.right.addAll(n2.right);

      } else if (node.getOp() == MathOp.DIVIDE) {
        this.parentOp = MathOp.MULTIPLY;
        ExtendedMathOpNode n1 = new ExtendedMathOpNode(
            this.node.left, MathOp.MULTIPLY);
        ExtendedMathOpNode n2 = new ExtendedMathOpNode(
            this.node.right, MathOp.MULTIPLY);

        this.left.addAll(n1.left);
        this.left.addAll(n2.right);

        this.right.addAll(n1.right);
        this.right.addAll(n2.left);
      } else {
        this.left.add(node);
      }

    } else { // Op is defined by parent
      if (node.getOp() == MathOp.ADD && this.parentOp == MathOp.ADD) {
        ExtendedMathOpNode n1 = new ExtendedMathOpNode(
            this.node.left, MathOp.ADD);
        ExtendedMathOpNode n2 = new ExtendedMathOpNode(
            this.node.right, MathOp.ADD);

        this.left.addAll(n1.left);
        this.left.addAll(n2.left);

        this.right.addAll(n1.right);
        this.right.addAll(n2.right);
      } else if (node.getOp() == MathOp.SUBTRACT &&
                 this.parentOp == MathOp.ADD) {
        ExtendedMathOpNode n1 = new ExtendedMathOpNode(
            this.node.left, MathOp.ADD);
        ExtendedMathOpNode n2 = new ExtendedMathOpNode(
            this.node.right, MathOp.ADD);

        this.left.addAll(n1.left);
        this.left.addAll(n2.right);

        this.right.addAll(n1.right);
        this.right.addAll(n2.left);
      } else if (node.getOp() == MathOp.MULTIPLY &&
                 this.parentOp == MathOp.MULTIPLY) {

        ExtendedMathOpNode n1 = new ExtendedMathOpNode(
            this.node.left, MathOp.MULTIPLY);
        ExtendedMathOpNode n2 = new ExtendedMathOpNode(
            this.node.right, MathOp.MULTIPLY);

        this.left.addAll(n1.left);
        this.left.addAll(n2.left);

        this.right.addAll(n1.right);
        this.right.addAll(n2.right);

      } else if (node.getOp() == MathOp.DIVIDE &&
                 this.parentOp == MathOp.MULTIPLY) {

        ExtendedMathOpNode n1 = new ExtendedMathOpNode(
            this.node.left, MathOp.MULTIPLY);
        ExtendedMathOpNode n2 = new ExtendedMathOpNode(
            this.node.right, MathOp.MULTIPLY);

        this.left.addAll(n1.left);
        this.left.addAll(n2.right);

        this.right.addAll(n1.right);
        this.right.addAll(n2.left);

      } else {
        // Element is not a MathOpNode or its OP is not the same as its parents
        this.left.add(node);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ExtendedMathOpNode) {
      ExtendedMathOpNode other = (ExtendedMathOpNode) o;
      if (this.parentOp == other.parentOp) {
        // Check lhs
        List<ExpressionNode> l = other.left;
        for (ExpressionNode t : this.left) {
          if (!(l.contains(t))) {
            return false;
          } else {
            l.remove(t);
          }
        }
        if (l.size() != 0) {
          return false;
        }
        // Check rhs
        List<ExpressionNode> r = other.right;
        for (ExpressionNode t : this.right) {
          if (!(r.contains(t))) {
            return false;
          } else {
            r.remove(t);
          }
        }
        if (r.size() != 0) {
          return false;
        }
        return true;
      }
    }
    return false;
  }
}
