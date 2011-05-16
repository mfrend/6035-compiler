package edu.mit.compilers.le02.ast;

import java.util.Comparator;

/**
 * Allows all ExpressionNodes to be compared
 * @author Maria Frendberg (mfrend)
 *
 */

public class ExpressionNodeComparator implements Comparator<ExpressionNode> {

	@Override
	public int compare(ExpressionNode arg0, ExpressionNode arg1) {
		if (arg0.getClass().equals(arg1.getClass())) {
			return arg0.compare(arg1);
		} else {
			return classCompare(arg0,arg1);
		}
	}
	
	public static int classCompare(ExpressionNode arg0, ExpressionNode arg1){
		return arg0.getClass().toString().compareTo(
				arg1.getClass().toString());
	}

}
