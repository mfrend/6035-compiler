package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.CompilerException;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ArrayLocationNode;
import edu.mit.compilers.le02.ast.AssignNode;
import edu.mit.compilers.le02.ast.BoolOpNode;
import edu.mit.compilers.le02.ast.BooleanNode;
import edu.mit.compilers.le02.ast.CallStatementNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.MathOpNode;
import edu.mit.compilers.le02.ast.MethodCallNode;
import edu.mit.compilers.le02.ast.MinusNode;
import edu.mit.compilers.le02.ast.NotNode;
import edu.mit.compilers.le02.ast.ReturnNode;
import edu.mit.compilers.le02.ast.ScalarLocationNode;
import edu.mit.compilers.le02.ast.VariableNode;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

/**
 * This singleton class takes a control flow graph and flattens all the
 * UnexpandedStatements into expanded BasicStatements.  It is ok with having 
 * existing expanded BasicStatements in the control flow graph.
 * 
 * If the final conditional BasicStatement is unexpanded, it will be replaced
 * by the last statement of its the expanded expression, and the rest of the
 * BasicStatements which evaluate the expression will be appended to the
 * statement list of the basic block.   
 * 
 * The class will add some temporary locations into the symbol tables in order
 * to handle complex expressions with more than one operator.
 * 
 * @author dkoh
 *
 */
public final class ExpressionFlattener extends ASTNodeVisitor<Argument> {
  private static ExpressionFlattener instance = null;
  private List<BasicStatement> statements;
  
  public static ExpressionFlattener getInstance() {
    if (instance == null) {
      instance = new ExpressionFlattener();
    }
    return instance;
  }
  
  /*
   * Static Helper Methods
   */
  /*
  public static List<BasicStatement> flatten(UnexpandedStatement us) {
    return getInstance().flattenStatement(us);
  }
  */
  
  public static void flattenCFG(ControlFlowGraph cfg) {
    // TODO: iterate all basic blocks adn call flattenStatements(bb)
    
  }
  
  /**
   * Flattens all the unexpanded statements in the given basic block.
   * @param bb The basic block to flatten.
   */
  public static void flattenStatements(BasicBlockNode bb) {
    ArrayList<BasicStatement> newStatementList = new ArrayList<BasicStatement>();
    List<BasicStatement> statementList = bb.getStatements();
    for (BasicStatement bs : statementList) {
      //newStatementList.addAll(bs.flatten());
    }
    
    BasicStatement cond = bb.getConditional();
    if (cond != null) {
      //List<BasicStatement> condStatements = cond.flatten();
      
      // Make the result of the flattened conditional 
      // the new conditional statement
      //BasicStatement last = condStatements.remove(condStatements.size() - 1);
      //bb.setConditional(last);
      
      // Add the rest of the flattened conditional 
      // to the end of the basic block
      //newStatementList.addAll(condStatements);
    }
    
    bb.setStatements(newStatementList);
  }

  /*
  public List<BasicStatement> flattenStatement(UnexpandedStatement us) {
    statements = new ArrayList<BasicStatement>();
    us.getNode().accept(this);
    return statements;
  }
  */
  

  /*
   * Utility Methods
   */
  
  private AsmOp getAsmOp(MathOpNode node) {
    switch(node.getOp()) {
      case ADD:
        return AsmOp.ADD;
      case SUBTRACT:
        return AsmOp.SUBTRACT;
      case MULTIPLY:
        return AsmOp.MULTIPLY;
      case DIVIDE:
        return AsmOp.DIVIDE;
      case MODULO:
        return AsmOp.MODULO;
      default:
        ErrorReporting.reportError(new CompilerException(node.getSourceLoc(), 
          "MathOp " + node.getOp() + " cannot be converted into an AsmOp."));
        return null;
    }
  }
  
  private AsmOp getAsmOp(BoolOpNode node) {
    switch(node.getOp()) {
      case LE:
        return AsmOp.LESS_OR_EQUAL;
      case LT:
        return AsmOp.LESS_THAN;
      case GE:
        return AsmOp.GREATER_OR_EQUAL;
      case GT:
        return AsmOp.GREATER_THAN;
      case EQ:
        return AsmOp.EQUAL;
      case NEQ:
        return AsmOp.NOT_EQUAL;
      default:
         ErrorReporting.reportError(new CompilerException(node.getSourceLoc(), 
           "BoolOp " + node.getOp() + " cannot be converted into an AsmOp."));

         return null;
    }
  }
  
  private VariableLocation makeTemp(ASTNode node, DecafType type) {
    SymbolTable st = node.getSymbolTable();
    
    int nextIndex = st.getLargestLocalOffset() - 8;
    
    LocalDescriptor ld = new LocalDescriptor(st, Math.abs(nextIndex) + "lcltmp", 
        type);
    st.put(ld.getId(), ld, node.getSourceLoc());
    return ld.getLocation();
  }
  
  /*
   * Statement Visiting Methods
   */
  public Argument visit(AssignNode node) {
    VariableLocation destLoc = node.getLoc().getDesc().getLocation();
    Argument dest = Argument.makeArgument(destLoc);
    Argument src = node.getValue().accept(this);
    
    statements.add(new OpStatement(node, AsmOp.RETURN, src, dest, null));
    return null;
  }
  
  public Argument visit(CallStatementNode node) {
    node.getCall().accept(this);
    return null;
  }

  public Argument visit(ReturnNode node) {
    Argument arg1 = null;
    if (node.hasValue()) {
      arg1 = node.getRetValue().accept(this);
    }
    
    statements.add(new OpStatement(node, AsmOp.RETURN, arg1, null, null));
    return null;
  }
  
  /*
   * Expression Visiting Methods
   */  
  public Argument visit(BoolOpNode node) {
    Argument arg1 = node.getLeft().accept(this);
    Argument arg2 = node.getRight().accept(this);
    VariableLocation loc = makeTemp(node, DecafType.BOOLEAN);
    
    OpStatement s = new OpStatement(node, getAsmOp(node), 
                                    arg1, arg2, loc);
    statements.add(s);
    return Argument.makeArgument(loc);
  }
  
  public Argument visit(MathOpNode node) {
    Argument arg1 = node.getLeft().accept(this);
    Argument arg2 = node.getRight().accept(this);
    VariableLocation loc = makeTemp(node, DecafType.INT);
    
    OpStatement s = new OpStatement(node, getAsmOp(node), 
                                    arg1, arg2, loc);
    statements.add(s);
    return Argument.makeArgument(loc);
  }
  
  public Argument visit(NotNode node) {
    VariableLocation loc = makeTemp(node, DecafType.BOOLEAN);
    
    OpStatement s = new OpStatement(node, AsmOp.NOT, 
                                    node.getExpr().accept(this), null, loc);
    statements.add(s);
    return Argument.makeArgument(loc);
  }
  
  public Argument visit(MinusNode node) {
    VariableLocation loc = makeTemp(node, DecafType.INT);
    
    OpStatement s = new OpStatement(node, AsmOp.UNARY_MINUS, 
                                    node.getExpr().accept(this), null, loc);
    statements.add(s);
    return Argument.makeArgument(loc);
  }
  
  public Argument visit(MethodCallNode node) {
    VariableLocation loc = makeTemp(node, node.getType());
    
    List<Argument> args = new ArrayList<Argument>();
    for (ExpressionNode n : node.getArgs()) {
      args.add(n.accept(this));
    }
    
    CallStatement s = new CallStatement(node, node.getDesc(), args, loc);
    statements.add(s);
    
    return Argument.makeArgument(loc);
  }
  
  public Argument visit(VariableNode node) {
    return node.getLoc().accept(this);
  }
  
  /*
   * Location and Constant Visiting Methods 
   */
  public Argument visit(ScalarLocationNode node) {
    return Argument.makeArgument(node.getDesc().getLocation());
  }
  
  public Argument visit(ArrayLocationNode node) {
    Argument a = node.getIndex().accept(this);
    return Argument.makeArgument(node.getDesc().getLocation(), a);
  }
  
  public Argument visit(BooleanNode node) {
    return Argument.makeArgument(node.getValue());
  }
  
  public Argument visit(IntNode node) {
    return Argument.makeArgument(node.getValue());
  }

}
