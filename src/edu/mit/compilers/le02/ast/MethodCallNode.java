package edu.mit.compilers.le02.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;


public final class MethodCallNode extends CallNode {
  private String name;
  private List<ExpressionNode> args;
  private MethodDescriptor desc;

  public MethodCallNode(SourceLocation sl, String name) {
    super(sl);
    this.name = name;
  }

  public MethodCallNode(SourceLocation sl,
                        String name, List<ExpressionNode> args) {
    super(sl);
    this.name = name;
    this.args = args;
  }

  @Override
  public List<ASTNode> getChildren() {
    List<ASTNode> children = new ArrayList<ASTNode>();
    children.addAll(args);
    return children;
  }

  @Override
  public boolean replaceChild(ASTNode prev, ASTNode next) {
    ListIterator<ExpressionNode> iter = args.listIterator();
    while (iter.hasNext()) {
      if ((iter.next() == prev) && (next instanceof ExpressionNode)) {
        next.setParent(this);
        iter.set((ExpressionNode)next);
        return true;
      }
    }
    return false;
  }

  public List<ExpressionNode> getArgs() {
    return args;
  }

  public void setArgs(List<ExpressionNode> args) {
    this.args = args;
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof MethodCallNode)) {
      return false;
    }
    MethodCallNode other = (MethodCallNode)o;
    return (name.equals(other.getName()) &&
            args.equals(other.getArgs()));
  }

  public MethodDescriptor getDesc() {
    return desc;
  }

  public void setDesc(MethodDescriptor desc) {
    this.desc = desc;
  }

  @Override
  public String toString() {
    return super.toString() + " " + name;
  }

  @Override
  public <T> T accept(ASTNodeVisitor<T> v) {
    return v.visit(this);
  }

  @Override
  public DecafType getType() {
    return (desc == null) ? null : desc.getType();
  }

@Override
public int compare(ExpressionNode arg) {
 if (arg instanceof MethodCallNode){
  return this.getName().compareTo(((MethodCallNode) arg).getName());
 } else {
  return ExpressionNodeComparator.classCompare(this, arg);
 }
}
}
