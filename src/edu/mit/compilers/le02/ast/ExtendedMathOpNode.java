package edu.mit.compilers.le02.ast;

import java.util.ArrayList;
import java.util.Collections;
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
	private MathOp parentOp; // Will only be MathOp.SUBTRACT or MathOp.DIVIDE.
	private List<ExpressionNode> left;
	private List<ExpressionNode> right;
	private MathOpNode node;

	public ExtendedMathOpNode(ExpressionNode n, MathOp op) {
		parentOp = op;
		left = new ArrayList<ExpressionNode>();
		right = new ArrayList<ExpressionNode>();
		if (n instanceof MathOpNode) {
			node = (MathOpNode) n;
			expand();
		} else if (n != null) {
			left.add(n);
		}
		//System.out.println(this.left.toString()+this.parentOp.toString()+this.right.toString());
	}

	public void expand() {
		MathOp op = node.getOp();
		MathOp canonical = null;
		switch (op) {
		case ADD:
		case SUBTRACT:
			canonical = MathOp.SUBTRACT;
			break;
		case MULTIPLY:
		case DIVIDE:
			canonical = MathOp.DIVIDE;
			break;
		default:
			// Element is modulo.
			parentOp = op;
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
		left.addAll((op == canonical) ? n2.right : n2.left);

		right.addAll(n1.right);
		right.addAll((op == canonical) ? n2.left : n2.right);
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
				new ArrayList<ExpressionNode>(this.left))
				&& unorderedEquals(new ArrayList<ExpressionNode>(other.right),
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

	public MathOpNode simplify() {
		Collections.sort(left, new ExpressionNodeComparator());
		Collections.sort(right, new ExpressionNodeComparator());
		//System.out.println(left.toString()+parentOp.toString()+right.toString());
		MathOpNode result = node;
		if (parentOp != null && parentOp != MathOp.MODULO) {
			if (left.size() > 0) {
				if (right.size() == 0) { // This ensures a valid simplification to MathOpNode
					if(left.size() > 1){
					 result = (MathOpNode) buildTree(left,parentOp);
					}
				} else {
					result = new MathOpNode(null,buildTree(left,parentOp),buildTree(right,parentOp),parentOp,false);
				}
			}
		}
		return result;
	}

	private ExpressionNode buildTree(List<ExpressionNode> input,
			MathOp canonical) {
		ExpressionNode result = null;
		if (input.size() == 1) {
			result = input.get(0);
		} else {
			int breakPoint = input.size() / 2;
			switch (canonical) {
			case SUBTRACT:
				result = new MathOpNode(null, buildTree(input.subList(0,
						breakPoint), canonical), buildTree(input.subList(
						breakPoint, input.size()), canonical), MathOp.ADD,
						false);
				break;
			case DIVIDE:
				result = new MathOpNode(null, buildTree(input.subList(0,
						breakPoint), canonical), buildTree(input.subList(
						breakPoint, input.size()), canonical), MathOp.MULTIPLY,
						false);
				break;
			}
		}
		return result;
	}
}
