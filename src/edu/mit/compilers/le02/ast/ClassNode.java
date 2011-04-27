package edu.mit.compilers.le02.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.symboltable.ClassDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;


public final class ClassNode extends ASTNode {
  private String name;
  protected List<FieldDeclNode> fields;
  protected List<MethodDeclNode> methods;
  protected ClassDescriptor desc;

  public ClassNode(SourceLocation sl,
                   String name, List<FieldDeclNode> fields,
                   List<MethodDeclNode> methods) {
    super(sl);
    this.name = name;
    this.fields = fields;
    this.methods = methods;
  }

  public String getName() {
    return name;
  }

  @Override
  public List<ASTNode> getChildren() {
    List<ASTNode> children = new ArrayList<ASTNode>();
    children.addAll(fields);
    children.addAll(methods);
    return children;
  }

  @Override
  public boolean replaceChild(ASTNode prev, ASTNode next) {
    for (ListIterator<FieldDeclNode> iter = fields.listIterator();
        iter.hasNext();) {
      if ((iter.next() == prev) && (next instanceof FieldDeclNode)) {
        next.setParent(this);
        iter.set((FieldDeclNode)next);
        return true;
      }
    }

    for (ListIterator<MethodDeclNode> iter = methods.listIterator();
        iter.hasNext();) {
      if ((iter.next() == prev) && (next instanceof MethodDeclNode)) {
        next.setParent(this);
        iter.set((MethodDeclNode)next);
        return true;
      }
    }
    return false;
  }

  public List<FieldDeclNode> getFields() {
    return fields;
  }

  public void setFields(List<FieldDeclNode> fields) {
    this.fields = fields;
  }

  public List<MethodDeclNode> getMethods() {
    return methods;
  }

  public void setMethods(List<MethodDeclNode> methods) {
    this.methods = methods;
  }

  public ClassDescriptor getDesc() {
    return desc;
  }

  public void setDesc(ClassDescriptor desc) {
    this.desc = desc;
  }

  @Override
  public <T> T accept(ASTNodeVisitor<T> v) {
    return v.visit(this);
  }

  @Override
  public SymbolTable getSymbolTable() {
    return desc.getSymbolTable();
  }
}
