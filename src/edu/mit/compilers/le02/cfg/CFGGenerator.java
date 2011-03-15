package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.CompilerException;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.GlobalLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ArrayLocationNode;
import edu.mit.compilers.le02.ast.AssignNode;
import edu.mit.compilers.le02.ast.BlockNode;
import edu.mit.compilers.le02.ast.BoolOpNode;
import edu.mit.compilers.le02.ast.BooleanNode;
import edu.mit.compilers.le02.ast.BreakNode;
import edu.mit.compilers.le02.ast.CallStatementNode;
import edu.mit.compilers.le02.ast.ClassNode;
import edu.mit.compilers.le02.ast.ContinueNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.FieldDeclNode;
import edu.mit.compilers.le02.ast.ForNode;
import edu.mit.compilers.le02.ast.IfNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.MathOpNode;
import edu.mit.compilers.le02.ast.MethodCallNode;
import edu.mit.compilers.le02.ast.MethodDeclNode;
import edu.mit.compilers.le02.ast.MinusNode;
import edu.mit.compilers.le02.ast.NotNode;
import edu.mit.compilers.le02.ast.ReturnNode;
import edu.mit.compilers.le02.ast.ScalarLocationNode;
import edu.mit.compilers.le02.ast.StatementNode;
import edu.mit.compilers.le02.ast.StringNode;
import edu.mit.compilers.le02.ast.SyscallArgNode;
import edu.mit.compilers.le02.ast.SystemCallNode;
import edu.mit.compilers.le02.ast.VariableNode;
import edu.mit.compilers.le02.ast.BoolOpNode.BoolOp;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public final class CFGGenerator extends ASTNodeVisitor<CFGFragment> {
  private static CFGGenerator instance = null;
  private ControlFlowGraph cfg;
  private SimpleCFGNode increment, loopExit;

  public static CFGGenerator getInstance() {
    if (instance == null) {
      instance = new CFGGenerator();
    }
    return instance;
  }

  private VariableLocation makeTemp(ASTNode node, DecafType type) {
    SymbolTable st = node.getSymbolTable();
    int nextIndex = st.getLargestLocalOffset() - 8;

    LocalDescriptor ld = new LocalDescriptor(st, Math.abs(nextIndex) + "lcltmp", type);
    st.put(ld.getId(), ld, node.getSourceLoc());
    return ld.getLocation();
  }

  public static ControlFlowGraph generateCFG(ASTNode root) {
    assert(root instanceof ClassNode);
    root.accept(getInstance());
    return getInstance().cfg;
  }

  private SimpleCFGNode shortCircuit(ExpressionNode node,
      SimpleCFGNode t, SimpleCFGNode f) {

    if (node instanceof BoolOpNode) {
      return shortCircuitHelper((BoolOpNode) node, t, f);
    }

    if (node instanceof NotNode) {
      return shortCircuitHelper((NotNode) node, t, f);
    }

    CFGFragment frag = node.accept(this);
    SimpleCFGNode exit = frag.getExit();
    exit.setBranchTarget(t);
    exit.setNext(f);

    return frag.getEnter();
  }

  private SimpleCFGNode shortCircuitHelper(BoolOpNode node,
      SimpleCFGNode t, SimpleCFGNode f) {
    if ((node.getOp() != BoolOp.AND) && (node.getOp() != BoolOp.OR)) {
      CFGFragment frag = node.accept(this);
      SimpleCFGNode exit = frag.getExit();
      exit.setBranchTarget(t);
      exit.setNext(f);

      return frag.getEnter();
    }

    boolean isAnd = (node.getOp() == BoolOp.AND); 

    // Short circuit right side
    SimpleCFGNode b2 = shortCircuit(node.getRight(), t, f);
    SimpleCFGNode b1;

    if (isAnd) {
      b1 = shortCircuit(node.getLeft(), b2, f);
    }
    else {
      b1 = shortCircuit(node.getLeft(), t, b2);
    }

    return b1;
  }

  private SimpleCFGNode shortCircuitHelper(NotNode node,
      SimpleCFGNode t, SimpleCFGNode f) {
    return shortCircuit(node.getExpr(), f, t);
  }

  @Override
  public CFGFragment visit(ClassNode node) {
    cfg = new ControlFlowGraph();

    defaultBehavior(node);
    return null;
  }

  @Override
  public CFGFragment visit(FieldDeclNode node) {
    String id = node.getName();
    cfg.putGlobal(id, node.getSymbolTable().getField(id));
    defaultBehavior(node);
    return null;
  }

  @Override
  public CFGFragment visit(MethodDeclNode node) {
    cfg.putMethod(node.getName(), node.getBody().accept(this).getEnter());
    return null;
  }


  /*
   * Statement visit methods
   */
  @Override
  public CFGFragment visit(BlockNode node) {
    CFGFragment fragment = null;
    for (StatementNode s : node.getStatements()) {
      CFGFragment curr = s.accept(this);

      if (fragment == null) {
        fragment = curr;
      } else {
        fragment = fragment.link(curr); 
      }

      if ((s instanceof BreakNode) || (s instanceof ContinueNode)) {
        break;
      }
    }

    return fragment;
  }

  @Override
  public CFGFragment visit(AssignNode node) {
    VariableLocation destLoc = node.getLoc().getDesc().getLocation();
    CFGFragment frag = node.getValue().accept(this);
    Argument src = frag.getExit().getResult();
    Argument dest = Argument.makeArgument(destLoc);

    BasicStatement st = new OpStatement(node, AsmOp.MOVE, src, dest, null);
    SimpleCFGNode cfgNode = new SimpleCFGNode(st);
    return frag.append(cfgNode);
  }

  @Override
  public CFGFragment visit(ReturnNode node) {
    if (node.hasValue()) {
      CFGFragment frag = node.getRetValue().accept(this); 
      Argument returnValue = frag.getExit().getResult();
      BasicStatement st = new OpStatement(node, AsmOp.RETURN, 
          returnValue, null, null);
      return frag.append(new SimpleCFGNode(st));
    }

    BasicStatement st = new OpStatement(node, AsmOp.RETURN, 
        null, null, null);
    SimpleCFGNode cfgNode = new SimpleCFGNode(st);

    return new CFGFragment(cfgNode, cfgNode);
  }

  @Override
  public CFGFragment visit(ForNode node) {
    // Save increment and exit nodes of any outer for loop
    SimpleCFGNode oldIncrement = increment;
    SimpleCFGNode oldExit = loopExit;

    // Create dummy exit node 
    SimpleCFGNode exit = new SimpleCFGNode(new DummyStatement());
    loopExit = exit;

    // Evaluate the exit condition
    CFGFragment exitFrag = node.getEnd().accept(this);
    Argument exitVal = exitFrag.getExit().getResult();

    // Create a node where the iterator is incremented
    VariableLocation loc = node.getInit().getLoc().getDesc().getLocation();
    Argument loopVar = Argument.makeArgument(loc);
    BasicStatement st = new OpStatement(node, AsmOp.ADD,
        loopVar, new ConstantArgument(1), loc);
    increment = new SimpleCFGNode(st);

    // Compute fragments of the for loop's control flow graph
    CFGFragment initFrag = node.getInit().accept(this);
    CFGFragment bodyFrag = node.getBody().accept(this);

    // Create a branch node where the condition is evaluated and connect it up
    BasicStatement conditionStatement = new OpStatement(node, AsmOp.LESS_THAN,
        loopVar, exitVal, null);
    SimpleCFGNode branch = new SimpleCFGNode(conditionStatement);
    branch.setBranchTarget(bodyFrag.getEnter());
    branch.setNext(loopExit);

    // Connect fragments together
    exitFrag.getExit().setNext(initFrag.getEnter());
    initFrag.getExit().setNext(branch);
    bodyFrag.getExit().setNext(increment);
    increment.setNext(branch);

    // Restore increment and exit nodes of any outer for loop
    increment = oldIncrement;
    loopExit = oldExit;

    // Enter at the condition, exit via the dummy exit node
    return new CFGFragment(exitFrag.getEnter(), exit);
  }

  @Override
  public CFGFragment visit(BreakNode node) {
    return new CFGFragment(loopExit, loopExit);
  }

  @Override
  public CFGFragment visit(ContinueNode node) {
    return new CFGFragment(increment, increment);
  }

  @Override
  public CFGFragment visit(IfNode node) {        
    // Create dummy exit node 
    SimpleCFGNode exit = new SimpleCFGNode(new DummyStatement());

    // Calculate sub-expressions
    CFGFragment trueFrag = node.getThenBlock().accept(this);
    CFGFragment falseFrag = null;
    if (node.hasElse()) {
      falseFrag = node.getElseBlock().accept(this);

      // Set false fragment to point to dummy exit
      falseFrag.getExit().setNext(exit);
    }
    else {
      // Make dummy false fragment
      falseFrag = new CFGFragment(exit, exit);  
    }

    // Point true fragment at dummy exit
    trueFrag.getExit().setNext(exit);

    // Short circuit the condition
    SimpleCFGNode enter = shortCircuit(node.getCondition(),
        trueFrag.getEnter(), 
        falseFrag.getEnter());

    // Enter at the condition, exit via the dummy exit node
    return new CFGFragment(enter, exit);
  }

  public CFGFragment visit(CallStatementNode node) {
    return node.getCall().accept(this);
  }

  /*
   * Expression visit methods
   */
  public CFGFragment visit(BoolOpNode node) {

    if ((node.getOp() == BoolOp.AND) || (node.getOp() == BoolOp.OR)) {
      ArgumentStatement trueStmt = new ArgumentStatement(null, Argument.makeArgument(true));
      ArgumentStatement falseStmt = new ArgumentStatement(null, Argument.makeArgument(false));
      SimpleCFGNode trueNode = new SimpleCFGNode(trueStmt);
      SimpleCFGNode falseNode = new SimpleCFGNode(falseStmt);

      SimpleCFGNode exit = new SimpleCFGNode(new DummyStatement());
      trueNode.setNext(exit);
      falseNode.setNext(exit);

      SimpleCFGNode enter = shortCircuit(node, trueNode, falseNode);
      return new CFGFragment(enter, exit);
    }

    CFGFragment leftFrag = node.getLeft().accept(this);
    CFGFragment rightFrag = node.getRight().accept(this);

    VariableLocation loc = makeTemp(node, DecafType.BOOLEAN);
    Argument arg1 = leftFrag.getExit().getResult();
    Argument arg2 = rightFrag.getExit().getResult();
    BasicStatement st = new OpStatement(node, getAsmOp(node), 
        arg1, arg2, loc);

    // Order statements as follows: 
    // <left fragment> <right fragment> <bool op stmt>
    return leftFrag.link(rightFrag).append(new SimpleCFGNode(st));
  }

  public CFGFragment visit(MathOpNode node) {
    CFGFragment frag1 = node.getLeft().accept(this);
    CFGFragment frag2 = node.getRight().accept(this);
    VariableLocation loc = makeTemp(node, DecafType.INT);
    Argument arg1 = frag1.getExit().getResult();
    Argument arg2 = frag2.getExit().getResult();

    OpStatement s = new OpStatement(node, getAsmOp(node), 
        arg1, arg2, loc);

    return frag1.link(frag2).append(new SimpleCFGNode(s));
  }

  public CFGFragment visit(NotNode node) {
    CFGFragment frag = node.getExpr().accept(this);
    VariableLocation loc = makeTemp(node, DecafType.BOOLEAN);

    OpStatement s = new OpStatement(node, AsmOp.NOT, 
        frag.getExit().getResult(), null, loc);

    return frag.append(new SimpleCFGNode(s));
  }


  public CFGFragment visit(MinusNode node) {
    CFGFragment frag = node.getExpr().accept(this);
    VariableLocation loc = makeTemp(node, DecafType.BOOLEAN);

    OpStatement s = new OpStatement(node, AsmOp.UNARY_MINUS, 
        frag.getExit().getResult(), null, loc);

    return frag.append(new SimpleCFGNode(s));
  }

  public CFGFragment visit(MethodCallNode node) {
    VariableLocation loc = makeTemp(node, node.getType());

    CFGFragment frag = null;
    List<Argument> args = new ArrayList<Argument>();
    for (ExpressionNode n : node.getArgs()) {
      if (frag == null) {
        frag = n.accept(this);
      }
      else {
        frag = frag.link(n.accept(this));
      }
      args.add(frag.getExit().getResult());
    }

    CallStatement s = new CallStatement(node, 
                                        node.getDesc().getId(), args, loc);

    return frag.append(new SimpleCFGNode(s));
  }
  

  public CFGFragment visit(SystemCallNode node) {
    VariableLocation loc = makeTemp(node, node.getType());

    CFGFragment frag = null;
    List<Argument> args = new ArrayList<Argument>();
    for (SyscallArgNode n : node.getArgs()) {
      if (frag == null) {
        frag = n.accept(this);
      }
      else {
        frag = frag.link(n.accept(this));
      }
      args.add(frag.getExit().getResult());
    }

    CallStatement s = new CallStatement(node, node.getFuncName().getValue(), 
                                        args, loc);

    SimpleCFGNode cn = new SimpleCFGNode(s);
    if (frag == null) {
      return new CFGFragment(cn, cn);
    }
    return frag.append(cn);
  }


  
  
  /*
   * Location and Constant visit methods 
   */
  public CFGFragment visit(VariableNode node) {
    return node.getLoc().accept(this);
  }

  public CFGFragment visit(ScalarLocationNode node) {
    Argument arg = Argument.makeArgument(node.getDesc().getLocation());
    ArgumentStatement as = new ArgumentStatement(node, arg);
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }

  public CFGFragment visit(ArrayLocationNode node) {
    Argument a = node.getIndex().accept(this).getExit().getResult();
    ArgumentStatement as = new ArgumentStatement(node, a);
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }

  public CFGFragment visit(BooleanNode node) {
    Argument arg = Argument.makeArgument(node.getValue());
    ArgumentStatement as = new ArgumentStatement(node, arg);
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }

  public CFGFragment visit(IntNode node) {
    Argument arg = Argument.makeArgument(node.getValue());
    ArgumentStatement as = new ArgumentStatement(node, arg);
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }
  
  public CFGFragment visit(StringNode node) {
    String name = "str" + node.getValue().hashCode();
    cfg.putStringData(name, node);
    ArgumentStatement as = new ArgumentStatement(node,
                             Argument.makeArgument(new GlobalLocation(name)));
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }

  public CFGFragment visit(SyscallArgNode node) {
    return node.getChildren().get(0).accept(this);
  }
  


  /*
   * Utility methods
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
}

