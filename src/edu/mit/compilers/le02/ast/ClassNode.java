package edu.mit.compilers.le02.ast;

import java.util.ArrayList;
import java.util.List;

public final class ClassNode extends ASTNode {
  private String name;
  protected List<FieldDeclNode> fields;
  protected List<MethodDeclNode> methods;

  public ClassNode(SourceLocation sl) {
    super(sl);
  }

  public ClassNode(SourceLocation sl, String name, List<FieldDeclNode> fields, List<MethodDeclNode> methods) {
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

  @Override
  public void visit(ASTNodeVisitor v) { v.accept(this); }
}