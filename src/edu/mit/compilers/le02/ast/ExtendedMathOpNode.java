package edu.mit.compilers.le02.ast;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.ast.MathOpNode.MathOp;

/**
 * Creates a canonicalized version of a MathOpNode to allow ((2*3)*5) to equal
 * (2*(3*5))
 * 
 * @author Maria Frendberg(mfrend)
 * 
 */
public class ExtendedMathOpNode extends ExpressionNode {
	MathOp ParentOp;  //Will only be MathOp.ADD or MathOp.MULTIPLY
	ArrayList<ExpressionNode> left;
	ArrayList<ExpressionNode> right;
	MathOpNode node;

	public ExtendedMathOpNode(SourceLocation sl, ExpressionNode n, MathOp op) {
		super(sl);
		this.ParentOp = op;
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
		if (this.ParentOp == null) { // Op is not defined by parent
			if (node.getOp() == MathOp.ADD) {
				this.ParentOp = MathOp.ADD;
				ExtendedMathOpNode n1 = new ExtendedMathOpNode(null,
						this.node.left, MathOp.ADD);
				ExtendedMathOpNode n2 = new ExtendedMathOpNode(null,
						this.node.right, MathOp.ADD);

				this.left.addAll(n1.left);
				this.left.addAll(n2.left);

				this.right.addAll(n1.right);
				this.right.addAll(n2.right);

			} else if (node.getOp() == MathOp.SUBTRACT) {
				this.ParentOp = MathOp.ADD;
				ExtendedMathOpNode n1 = new ExtendedMathOpNode(null,
						this.node.left, MathOp.ADD);
				ExtendedMathOpNode n2 = new ExtendedMathOpNode(null,
						this.node.right, MathOp.ADD);

				this.left.addAll(n1.left);
				this.left.addAll(n2.right);

				this.right.addAll(n1.right);
				this.right.addAll(n2.left);

			} else if (node.getOp() == MathOp.MULTIPLY) {
				this.ParentOp = MathOp.MULTIPLY;
				ExtendedMathOpNode n1 = new ExtendedMathOpNode(null,
						this.node.left, MathOp.MULTIPLY);
				ExtendedMathOpNode n2 = new ExtendedMathOpNode(null,
						this.node.right, MathOp.MULTIPLY);

				this.left.addAll(n1.left);
				this.left.addAll(n2.left);

				this.right.addAll(n1.right);
				this.right.addAll(n2.right);

			} else if (node.getOp() == MathOp.DIVIDE) {
				this.ParentOp = MathOp.MULTIPLY;
				ExtendedMathOpNode n1 = new ExtendedMathOpNode(null,
						this.node.left, MathOp.MULTIPLY);
				ExtendedMathOpNode n2 = new ExtendedMathOpNode(null,
						this.node.right, MathOp.MULTIPLY);

				this.left.addAll(n1.left);
				this.left.addAll(n2.right);

				this.right.addAll(n1.right);
				this.right.addAll(n2.left);
			} else {
				this.left.add(node);
			}

		} else { //Op is defined by parent
			if (node.getOp() == MathOp.ADD && this.ParentOp == MathOp.ADD) {
				ExtendedMathOpNode n1 = new ExtendedMathOpNode(null,
						this.node.left, MathOp.ADD);
				ExtendedMathOpNode n2 = new ExtendedMathOpNode(null,
						this.node.right, MathOp.ADD);

				this.left.addAll(n1.left);
				this.left.addAll(n2.left);

				this.right.addAll(n1.right);
				this.right.addAll(n2.right);
			} else if (node.getOp() == MathOp.SUBTRACT
					&& this.ParentOp == MathOp.ADD) {
				ExtendedMathOpNode n1 = new ExtendedMathOpNode(null,
						this.node.left, MathOp.ADD);
				ExtendedMathOpNode n2 = new ExtendedMathOpNode(null,
						this.node.right, MathOp.ADD);

				this.left.addAll(n1.left);
				this.left.addAll(n2.right);

				this.right.addAll(n1.right);
				this.right.addAll(n2.left);
			} else if (node.getOp() == MathOp.MULTIPLY
					&& this.ParentOp == MathOp.MULTIPLY) {

				ExtendedMathOpNode n1 = new ExtendedMathOpNode(null,
						this.node.left, MathOp.MULTIPLY);
				ExtendedMathOpNode n2 = new ExtendedMathOpNode(null,
						this.node.right, MathOp.MULTIPLY);

				this.left.addAll(n1.left);
				this.left.addAll(n2.left);

				this.right.addAll(n1.right);
				this.right.addAll(n2.right);

			} else if (node.getOp() == MathOp.DIVIDE
					&& this.ParentOp == MathOp.MULTIPLY) {

				ExtendedMathOpNode n1 = new ExtendedMathOpNode(null,
						this.node.left, MathOp.MULTIPLY);
				ExtendedMathOpNode n2 = new ExtendedMathOpNode(null,
						this.node.right, MathOp.MULTIPLY);

				this.left.addAll(n1.left);
				this.left.addAll(n2.right);

				this.right.addAll(n1.right);
				this.right.addAll(n2.left);

			} else { //Element is not a MathOpNode or it's OP is not the same as it's parents
				this.left.add(node);
			}
		}
	}

	@Override
	public DecafType getType() {
		return DecafType.INT;
	}

	@Override
	public <T> T accept(ASTNodeVisitor<T> v) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<ASTNode> getChildren() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ExtendedMathOpNode) {
			ExtendedMathOpNode other = (ExtendedMathOpNode) o;
			if (this.ParentOp == other.ParentOp) {
				//Check lhs
				ArrayList<ExpressionNode> l = other.left;
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
				//Check rhs
				ArrayList<ExpressionNode> r = other.right;
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
