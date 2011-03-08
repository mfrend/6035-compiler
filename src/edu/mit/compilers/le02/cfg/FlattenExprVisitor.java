package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.BooleanNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.MathOpNode;
import edu.mit.compilers.le02.ast.MethodCallNode;
import edu.mit.compilers.le02.ast.MinusNode;
import edu.mit.compilers.le02.ast.MathOpNode.MathOp;
import edu.mit.compilers.le02.cfg.OpStatement.Op;
import edu.mit.compilers.le02.symboltable.Descriptor;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public final class FlattenExprVisitor extends ASTNodeVisitor<Argument> {
  private List<BasicStatement> statements;
  
  
  public List<BasicStatement> makeBasicStatementList(ExpressionNode node) {
    statements = new ArrayList<BasicStatement>();
    node.accept(this);
    return statements;
  }
  
  private Op convertOp(MathOpNode.MathOp op) {
    switch(op) {
      case ADD:
        return Op.ADD;
      case SUBTRACT:
        return Op.SUBTRACT;
      case MULTIPLY:
        return Op.MULTIPLY;
      case DIVIDE:
        return Op.DIVIDE;
      case MODULO:
        return Op.MODULO;
    }
    assert false;
    return null;
  }
  
  private Descriptor makeTemp(ASTNode node) {
    SymbolTable st = node.getSymbolTable();
    int nextIndex = st.getHighestLocalIndex() + 1;
    LocalDescriptor ld = new LocalDescriptor(st, nextIndex + "tmp", 
        DecafType.INT);
    st.put(ld.getId(), ld, node.getSourceLoc());
    return ld;
  }
  
  public Argument visit(MathOpNode node) {
    Argument arg1 = node.getLeft().accept(this);
    Argument arg2 = node.getRight().accept(this);
    Descriptor d = makeTemp(node);
    
    OpStatement s = new OpStatement(node, convertOp(node.getOp()), 
                                    arg1, arg2, d);
    statements.add(s);
    return Argument.makeArgument(d);
  }
  
  public Argument visit(MinusNode node) {
    Descriptor d = makeTemp(node);
    
    OpStatement s = new OpStatement(node, Op.UNARY_MINUS, 
                                    node.getExpr().accept(this), null, d);
    statements.add(s);
    return Argument.makeArgument(d);
  }
  
  public Argument visit(MethodCallNode node) {
    Descriptor d = makeTemp(node);
    
    List<Argument> args = new ArrayList<Argument>();
    for (ExpressionNode n : node.getArgs()) {
      args.add(n.accept(this));
    }
    
    CallStatement s = new CallStatement(node, node.getDesc(), args, d);
    statements.add(s);
    
    return Argument.makeArgument(d);
  }
  
  public Argument visit(BooleanNode node) {
    return Argument.makeArgument(node.getValue());
  }
  
  public Argument visit(IntNode node) {
    return Argument.makeArgument(node.getValue());
  }

}
