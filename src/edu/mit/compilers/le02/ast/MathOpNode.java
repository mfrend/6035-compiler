package edu.mit.compilers.le02.ast;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;


public final class MathOpNode extends BinaryOpNode {
  private MathOp op;

  public MathOpNode(SourceLocation sl,
                    ExpressionNode left, ExpressionNode right, MathOp op) {
    super(sl, left, right);
    this.op = op;
  }

  public MathOp getOp() {
    return op;
  }

  @Override
  public String toString() {
    return "" + op;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof MathOpNode)) {
      return false;
    }
    MathOpNode other = (MathOpNode)o;
    boolean result = left.equals(other.left) &&
      right.equals(other.right) &&
      op.equals(other.getOp());
    if (result) {
      return true;
    } else {
      ExtendedMathOpNode node1 = new ExtendedMathOpNode(this, null);
      ExtendedMathOpNode node2 = new ExtendedMathOpNode(other, null);
      return node1.equals(node2);
    }
  }

  public enum MathOp {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    MODULO("%");
    private String disp;
    private MathOp(String display) {
      disp = display;
    }
    @Override
    public String toString() {
      return disp;
    }
  }

  @Override
  public <T> T accept(ASTNodeVisitor<T> v) {
    return v.visit(this);
  }

  @Override
  public DecafType getType() {
    return DecafType.INT;
  }
}
