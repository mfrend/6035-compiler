package edu.mit.compilers.le02.ast;

import java.util.List;

import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public abstract class ASTNode {
  protected SourceLocation sourceLoc;
  protected ASTNode parent;

  public ASTNode(SourceLocation sl) {
    this.sourceLoc = sl;
    this.parent = null;
  }

  public void setParent(ASTNode parent) {
    this.parent = parent;
  }

  public ASTNode getParent() {
    return parent;
  }

  public SourceLocation getSourceLoc() {
    return sourceLoc;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName();
  }

  public SymbolTable getSymbolTable() {
    return parent.getSymbolTable();
  }
  abstract public List<ASTNode> getChildren();
  abstract public boolean replaceChild(ASTNode prev, ASTNode next);
  abstract public <T> T accept(ASTNodeVisitor<T> v);
}
