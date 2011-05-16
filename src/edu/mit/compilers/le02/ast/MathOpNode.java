package edu.mit.compilers.le02.ast;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;

public final class MathOpNode extends BinaryOpNode {
	private MathOp op;

	public MathOpNode(SourceLocation sl, ExpressionNode left,
			ExpressionNode right, MathOp op) {
		super(sl, left, right);
		this.op = op;

		canonicalize();
	}

	public MathOpNode(SourceLocation sl, ExpressionNode left,
			ExpressionNode right, MathOp op, boolean cannonicalize) {
		super(sl, left, right);
		this.op = op;

		if (cannonicalize) { //This option allows us to avoid inifinte loops
			canonicalize();
		}
	}
	
	public void canonicalize() {
		ExtendedMathOpNode node = new ExtendedMathOpNode(this, null);
		MathOpNode temp = node.simplify();
		setLeft(temp.getLeft());
	    setRight(temp.getRight());
		op = temp.getOp();
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
		MathOpNode other = (MathOpNode) o;
		boolean result = left.equals(other.left) && right.equals(other.right)
				&& op.equals(other.getOp());
		return result;
	}

	public enum MathOp {
		ADD("+", 0), SUBTRACT("-", 1), MULTIPLY("*", 2), DIVIDE("/", 3), MODULO(
				"%", 4);
		private String disp;
		private int val;

		private MathOp(String display, int val) {
			disp = display;
		}

		public int getVal() {
			return val;
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

	@Override
	public int compare(ExpressionNode arg) {
		if (arg instanceof MathOpNode) {
			MathOpNode temp = (MathOpNode) arg;
			if (this.getOp() == temp.getOp()) {
				int leftVal = this.getLeft().compare(temp.getLeft());
				if (leftVal == 0) {
					return this.getRight().compare(temp.getRight());
				} else {
					return leftVal;
				}
			} else if (this.getOp().getVal() > temp.getOp().getVal()) {
				return 1;
			} else {
				return -1;
			}
		} else {
			return ExpressionNodeComparator.classCompare(this, arg);
		}
	}

}
