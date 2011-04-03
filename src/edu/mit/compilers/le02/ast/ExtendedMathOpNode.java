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
    parentOp = op;
    left = new ArrayList<ExpressionNode>();
    right = new ArrayList<ExpressionNode>();
    if (n instanceof MathOpNode) {
      node = (MathOpNode)n;
      expand();
    } else {
      left.add(n);
    }
  }

  public void expand() {
    MathOp op = node.getOp();
    MathOp canonical = null;
    switch (op) {
     case ADD:
     case SUBTRACT:
      canonical = MathOp.ADD;
      break;
     case MULTIPLY:
     case DIVIDE:
      canonical = MathOp.MULTIPLY;
     default:
      // Element is something irreducible like modulo.
      left.add(node);
      return;
    }

    ExtendedMathOpNode n1 = null;
    ExtendedMathOpNode n2 = null;

    if (parentOp == null) { // Op is not defined by parent
      parentOp = canonical;
    } else if (parentOp != canonical) {
      // Element's OP is not the same as its parents.
      left.add(node);
      return;
    }

    n1 = new ExtendedMathOpNode(node.left, canonical);
    n2 = new ExtendedMathOpNode(node.right, canonical);

    left.addAll(n1.left);
    left.addAll((op == canonical) ? n2.left : n2.right);

    right.addAll(n1.right);
    right.addAll((op == canonical) ? n2.right : n2.left);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ExtendedMathOpNode)) {
      return false;
    }
    ExtendedMathOpNode other = (ExtendedMathOpNode) o;
    if (this.parentOp != other.parentOp) {
      return false;
    }
    // Check lhs
    return unorderedEquals(new ArrayList<ExpressionNode>(other.left),
                           new ArrayList<ExpressionNode>(this.left)) &&
           unorderedEquals(new ArrayList<ExpressionNode>(other.right),
                           new ArrayList<ExpressionNode>(this.right));
  }

  private boolean unorderedEquals(List<ExpressionNode> theirs,
                                  List<ExpressionNode> mine) {
    for (ExpressionNode t : mine) {
      if (!(theirs.contains(t))) {
        return false;
      } else {
        theirs.remove(t);
      }
    }
    if (theirs.size() != 0) {
      return false;
    }
    return true;
  }
}
