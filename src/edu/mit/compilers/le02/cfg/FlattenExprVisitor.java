package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.BoolOpNode;
import edu.mit.compilers.le02.ast.BooleanNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.MathOpNode;
import edu.mit.compilers.le02.ast.MethodCallNode;
import edu.mit.compilers.le02.symboltable.Descriptor;

public final class FlattenExprVisitor extends ASTNodeVisitor<Descriptor> {
  private List<BasicStatement> statements;
  
  
  public List<BasicStatement> makeBasicStatementList(ExpressionNode node) {
    statements = new ArrayList<BasicStatement>();
    
  }
  
  private OpStatement.Op convertOp(MathOpNode.MathOp op) {
    return null;
  }
  
  public Descriptor visit(MathOpNode node) {
    Argument arg1 = Argument.makeArgument(node.getLeft().accept(this));
    Argument arg2 = Argument.makeArgument(node.getRight().accept(this));
    OpStatement s = new OpStatement(node, convertOp(node.getOp()), arg1, arg2, null);
  }
  public Descriptor visit(MethodCallNode node) {defaultBehavior(node); return null;}
  public Descriptor visit(BoolOpNode node) {defaultBehavior(node); return null;}
  public Descriptor visit(BooleanNode node) {defaultBehavior(node); return null;}
  public Descriptor visit(IntNode node) {defaultBehavior(node); return null;}

}
