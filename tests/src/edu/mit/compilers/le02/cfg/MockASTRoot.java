package edu.mit.compilers.le02.cfg;

import java.util.List;

import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public final class MockASTRoot extends ASTNode {
  private SymbolTable st;

  public MockASTRoot(SourceLocation sl, SymbolTable st) {
    super(sl);
    this.st = st;
  }

  @Override
  public SymbolTable getSymbolTable() {
    return st;
  }

  @Override
  public <T> T accept(ASTNodeVisitor<T> v) {
    return null;
  }

  @Override
  public List<ASTNode> getChildren() {
    return null;
  }

  @Override
  public boolean replaceChild(ASTNode prev, ASTNode next) {
    return false;
  }
}
