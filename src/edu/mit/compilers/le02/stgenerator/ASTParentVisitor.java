package edu.mit.compilers.le02.stgenerator;

import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;

/**
 * The ASTParentVisitor ensures that all nodes have the correct parent links,
 * after the AST is created.
 * 
 * This is done in a visitor rather than at AST creation time to avoid
 * further complicating the AST generation code.
 * @author dkoh
 *
 */
public final class ASTParentVisitor extends ASTNodeVisitor<ASTNode> {
  private ASTNode parent = null;

  @Override
  protected void defaultBehavior(ASTNode node) {
    ASTNode oldParent = parent;
    node.setParent(parent);
    parent = node;
    for (ASTNode child : node.getChildren()) {
      child.accept(this);
    }
    parent = oldParent;
  }

}
