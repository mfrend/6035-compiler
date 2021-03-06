package edu.mit.compilers.le02.ast;

import java.util.List;

import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.Util;

public final class ForNode extends StatementNode {
  private AssignNode init;
  private ExpressionNode end;
  private BlockNode body;

  public ForNode(SourceLocation sl,
                 AssignNode init, ExpressionNode end, BlockNode body) {
    super(sl);
    this.init = init;
    this.end = end;
    this.body = body;
  }

  @Override
  public List<ASTNode> getChildren() {
    return Util.makeList(init, end, body);
  }

  @Override
  public boolean replaceChild(ASTNode prev, ASTNode next) {
    if ((init == prev) && (next instanceof AssignNode)) {
      init = (AssignNode)next;
      init.setParent(this);
      return true;
    } else if ((end == prev) && (next instanceof ExpressionNode)) {
      end = (ExpressionNode)next;
      end.setParent(this);
      return true;
    } else if ((body == prev) && (next instanceof BlockNode)) {
      body = (BlockNode)next;
      body.setParent(this);
      return true;
    }
    return false;
  }

  public AssignNode getInit() {
    return init;
  }

  public void setInit(AssignNode init) {
    this.init = init;
  }

  public ExpressionNode getEnd() {
    return end;
  }

  public void setEnd(ExpressionNode end) {
    this.end = end;
  }

  public BlockNode getBody() {
    return body;
  }

  public void setBody(BlockNode body) {
    this.body = body;
  }

  @Override
  public <T> T accept(ASTNodeVisitor<T> v) {
    return v.visit(this);
  }
}
