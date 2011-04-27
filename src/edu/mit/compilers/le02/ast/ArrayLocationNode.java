package edu.mit.compilers.le02.ast;

import java.util.List;

import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.Util;

public final class ArrayLocationNode extends LocationNode {
  private ExpressionNode index;

  public ArrayLocationNode(SourceLocation sl, String name,
                           ExpressionNode index) {
    super(sl, name);
    this.index = index;
  }

  @Override
  public List<ASTNode> getChildren() {
    return Util.makeList((ASTNode)index);
  }

  @Override
  public boolean replaceChild(ASTNode prev, ASTNode next) {
    if ((index == prev) && (next instanceof ExpressionNode)) {
      index = (ExpressionNode)next;
      index.setParent(this);
      return true;
    }
    return false;
  }

  public void setIndex(ExpressionNode index) {
    this.index = index;
  }

  public ExpressionNode getIndex() {
    return index;
  }

  @Override
  public String toString() {
    return super.toString() + " " + getType() + " " + name + "[" + index + "]";
  }

  @Override
  public <T> T accept(ASTNodeVisitor<T> v) { return v.visit(this); }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ArrayLocationNode)) {
      return false;
    }
    ArrayLocationNode other = (ArrayLocationNode)o;
    return (name.equals(other.getName()) &&
            getType().equals(other.getType()) &&
            index.equals(other.getIndex()));
  }
}
