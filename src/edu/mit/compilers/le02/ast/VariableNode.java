package edu.mit.compilers.le02.ast;

import java.util.List;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.Util;


public final class VariableNode extends ExpressionNode {
  private LocationNode loc;

  public VariableNode(SourceLocation sl, LocationNode loc) {
    super(sl);
    this.loc = loc;
  }

  @Override
  public List<ASTNode> getChildren() {
    return Util.makeList((ASTNode)loc);
  }

  @Override
  public boolean replaceChild(ASTNode prev, ASTNode next) {
    if ((loc == prev) && (next instanceof LocationNode)) {
      loc = (LocationNode)next;
      loc.setParent(this);
      return true;
    }
    return false;
  }

  public void setLoc(LocationNode loc) {
    this.loc = loc;
  }

  public LocationNode getLoc() {
    return loc;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof VariableNode)) {
      return false;
    }
    VariableNode other = (VariableNode)o;
    return loc.equals(other.getLoc());
  }

  @Override
  public <T> T accept(ASTNodeVisitor<T> v) {
    return v.visit(this);
  }

  @Override
  public DecafType getType() {
    return loc.getType();
  }

@Override
public int compare(ExpressionNode arg) {
 if(arg instanceof VariableNode){
  return this.getLoc().getName().compareTo(
		  ((VariableNode) arg).getLoc().getName());
 }
 return 0;
}
}
