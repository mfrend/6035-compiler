package edu.mit.compilers.le02.ast;

import java.util.Comparator;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;


public abstract class ExpressionNode extends ASTNode{

  public ExpressionNode(SourceLocation sl) {
    super(sl);
  }

  abstract public DecafType getType();

  public int compare(ExpressionNode arg){
   return 0;
  }

}
