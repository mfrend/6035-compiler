package edu.mit.compilers.le02.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public final class MethodDeclNode extends DeclNode {
  private List<VarDeclNode> params;
  private BlockNode body;
  private MethodDescriptor descriptor;

  public MethodDeclNode(SourceLocation sl, DecafType type,
                        String id, List<VarDeclNode> params, BlockNode body) {
    super(sl, type, id);
    this.params = params;
    this.body = body;
  }

  @Override
  public List<ASTNode> getChildren() {
    List<ASTNode> children = new ArrayList<ASTNode>(params);
    children.add(body);
    return children;
  }

  @Override
  public boolean replaceChild(ASTNode prev, ASTNode next) {
    ListIterator<VarDeclNode> iter = params.listIterator();
    while (iter.hasNext()) {
      if ((iter.next() == prev) && (next instanceof VarDeclNode)) {
        next.setParent(this);
        iter.set((VarDeclNode)next);
        return true;
      }
    }
    if ((body == prev) && (next instanceof BlockNode)) {
      body = (BlockNode)next;
      body.setParent(this);
      return true;
    }
    return false;
  }

  public List<VarDeclNode> getParams() {
    return params;
  }

  public BlockNode getBody() {
    return body;
  }

  @Override
  public String toString() {
    return super.toString() + " " + type + " " + name;
  }

  @Override
  public <T> T accept(ASTNodeVisitor<T> v) {
    return v.visit(this);
  }

  @Override
  public SymbolTable getSymbolTable() {
    return body.getSymbolTable();
  }

  public void setDescriptor(MethodDescriptor desc) {
    descriptor = desc;
  }

  public MethodDescriptor getDescriptor() {
    return descriptor;
  }
}
