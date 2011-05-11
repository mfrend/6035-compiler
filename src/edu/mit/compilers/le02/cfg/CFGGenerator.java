package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mit.compilers.le02.CompilerException;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.GlobalLocation;
import edu.mit.compilers.le02.Main.Optimization;
import edu.mit.compilers.le02.RegisterLocation;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.SourceLocation;
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
import edu.mit.compilers.le02.opt.ArrayBoundsChecks;
import edu.mit.compilers.le02.opt.LoopMonotonicCode;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;
import edu.mit.compilers.le02.symboltable.SymbolTable.SymbolType;
import edu.mit.compilers.le02.symboltable.Descriptor;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public final class CFGGenerator extends ASTNodeVisitor<CFGFragment> {
  private static boolean arrayBoundsChecksOpt;
  private static CFGGenerator instance = null;
  private static String curMethod;
  private static boolean inFlatFor;
  private static boolean skipBoundsChecks;
  private ControlFlowGraph cfg;
  private SimpleCFGNode increment, loopExit;


  public static CFGGenerator getInstance() {
    if (instance == null) {
      instance = new CFGGenerator();
    }
    return instance;
  }

  /**
   * Make a temporary variable at node's scope with an offset that does not
   * conflict with any of node's, node's ancestors', or node's descendents'
   * locals.
   */
  public static LocalDescriptor makeTemp(ASTNode node, DecafType type) {
    SymbolTable st = node.getSymbolTable();
    int offset = st.getNonconflictingOffset();

    LocalDescriptor ld =
      new LocalDescriptor(st,
        Math.abs(offset) + TypedDescriptor.LOCAL_TEMP_SUFFIX, type, offset);
    st.put(ld.getId(), ld, node.getSourceLoc());
    return ld;
  }

  public static ControlFlowGraph generateCFG(ASTNode root,
      EnumSet<Optimization> opts) {
    arrayBoundsChecksOpt = 
        opts.contains(Optimization.LOOP_ARRAY_BOUNDS_CHECKS);
    if (arrayBoundsChecksOpt) {
      LoopMonotonicCode.findMonotonicCode(root);
    }
    inFlatFor = false;
    skipBoundsChecks = false;

    assert(root instanceof ClassNode);
    root.accept(getInstance());
    return getInstance().cfg;
  }

  /**
   * Create a node which branches on the value of a boolean
   * expression. The branchNodeHelper method for BoolOpNodes
   * handles short circuiting.
   */
  private SimpleCFGNode branchNode(ExpressionNode node,
      SimpleCFGNode t, SimpleCFGNode f) {

    if (node instanceof BoolOpNode) {
      return branchNodeHelper((BoolOpNode) node, t, f);
    }

    if (node instanceof NotNode) {
      return branchNodeHelper((NotNode) node, t, f);
    }

    CFGFragment frag;
    if (node instanceof VariableNode) {
      frag = node.accept(this);
      Argument src = frag.getExit().getResult();
      // Move the value of this variable into a register so it
      // can be used for je
      BasicStatement st = new OpStatement(node, AsmOp.MOVE, src,
        Argument.makeArgument(new AnonymousDescriptor(
          new RegisterLocation(Register.R11))), null);
      SimpleCFGNode cfgNode = new SimpleCFGNode(st);
      frag = frag.append(cfgNode);
    } else {
      frag = node.accept(this);
    }

    SimpleCFGNode exit = frag.getExit();
    exit.setBranchTarget(t);
    exit.setNext(f);

    return frag.getEnter();
  }

  private SimpleCFGNode branchNodeHelper(BoolOpNode node,
      SimpleCFGNode t, SimpleCFGNode f) {
    if ((node.getOp() != BoolOp.AND) && (node.getOp() != BoolOp.OR)) {
      // Equality and comparision operators do not need to be short circuited
      CFGFragment frag = node.accept(this);
      SimpleCFGNode exit = frag.getExit();
      exit.setBranchTarget(t);
      exit.setNext(f);

      return frag.getEnter();
    }

    boolean isAnd = (node.getOp() == BoolOp.AND);

    // Short circuit the right side if possible
    SimpleCFGNode b2 = branchNode(node.getRight(), t, f);
    SimpleCFGNode b1;

    if (isAnd) {
      b1 = branchNode(node.getLeft(), b2, f);
    } else {
      b1 = branchNode(node.getLeft(), t, b2);
    }

    return b1;
  }

  private SimpleCFGNode branchNodeHelper(NotNode node,
      SimpleCFGNode t, SimpleCFGNode f) {
    return branchNode(node.getExpr(), f, t);
  }

  @Override
  public CFGFragment visit(ClassNode node) {
    cfg = new ControlFlowGraph();

    defaultBehavior(node);
    return null;
  }

  @Override
  public CFGFragment visit(MethodDeclNode node) {
    curMethod = node.getName();
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
    }
    if (node.getStatements().size() == 0) {
      SimpleCFGNode nop = new SimpleCFGNode(new NOPStatement(node));
      fragment = new CFGFragment(nop, nop);
    }

    return fragment;
  }

  @Override
  public CFGFragment visit(AssignNode node) {
    // Assumption: location to assign, value to assign is evaluated, then
    // assignment is performed.
    CFGFragment destFrag = node.getLoc().accept(this);
    Argument destArg = destFrag.getExit().getResult();
    assert (destArg instanceof VariableArgument);

    CFGFragment valueFrag = node.getValue().accept(this);
    BasicStatement st = new OpStatement(node, AsmOp.MOVE,
        valueFrag.getExit().getResult(), destArg, null);
    SimpleCFGNode cfgNode = new SimpleCFGNode(st);

    if ((node.getValue() instanceof VariableNode &&
          ((VariableNode)node.getValue()).getType().isArray()) ||
        destArg instanceof ArrayVariableArgument) {
      return destFrag.link(valueFrag).append(cfgNode);
    }

    if (node.getValue() instanceof VariableNode ||
        node.getValue() instanceof IntNode ||
        node.getValue() instanceof BooleanNode) {
      return new CFGFragment(cfgNode,cfgNode);
    }

    valueFrag.getExit().getStatement().setResult(destArg.getDesc());
    valueFrag.getExit().setResult(destArg);

    return destFrag.link(valueFrag);
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
    CFGFragment loopFrag = forNodeHelper(node);

    if (arrayBoundsChecksOpt && !inFlatFor &&
        (LoopMonotonicCode.getFlatFors().contains(node))) {
      inFlatFor = true;

      skipBoundsChecks = true;
      CFGFragment skipFrag = forNodeHelper(node);
      skipBoundsChecks = false;
      return precheckArrayBounds(node, loopFrag, skipFrag);
    }

    return loopFrag;
  }

  private CFGFragment precheckArrayBounds(
      ForNode node, CFGFragment loopFrag, CFGFragment skipFrag) {
    CFGFragment frag = null;

    ArrayBoundsChecks.findArrayAccesses(node);
    for (ArrayLocationNode array : ArrayBoundsChecks.getAccesses()) {
      ExpressionNode index = array.getIndex();

      if (LoopMonotonicCode.getMonotonicExprs().contains(index)) {

        // Get the size of the array and make a fragment which prints oob errors
        int size = ((FieldDescriptor)(array.getDesc())).getLength();

        // Create a branch node where the array lower-bound is evaluated
        CFGFragment lowerCheck = replaceVars(node, index.accept(this),
              ArrayBoundsChecks.getLowerBounds(), 0);
        BasicStatement lowerBoundCheck = new OpStatement(node,
            AsmOp.LESS_THAN, lowerCheck.getExit().getResult(),
            new ConstantArgument(0), null);
        SimpleCFGNode lowerBound = new SimpleCFGNode(lowerBoundCheck);
        lowerBound.setBranchTarget(loopFrag.getEnter());
        lowerCheck = lowerCheck.append(lowerBound);

        // Create a branch node where the array upper-bound is evaluated
        CFGFragment upperCheck = replaceVars(node, index.accept(this),
              ArrayBoundsChecks.getUpperBounds(), 1);
        BasicStatement upperBoundCheck = new OpStatement(node,
            AsmOp.GREATER_OR_EQUAL, upperCheck.getExit().getResult(),
            new ConstantArgument(size), null);
        SimpleCFGNode upperBound = new SimpleCFGNode(upperBoundCheck);
        upperBound.setBranchTarget(loopFrag.getEnter());
        upperCheck = upperCheck.append(upperBound);

        if (frag == null) {
          frag = lowerCheck.link(upperCheck);
        } else {
          frag = frag.link(lowerCheck).link(upperCheck);
        }
      }
    }

    if (frag == null) {
      return skipFrag;
    }

    SimpleCFGNode exit = new SimpleCFGNode(new NOPStatement(node));
    loopFrag.append(exit);
    skipFrag.append(exit);
    frag = frag.link(skipFrag);
    return new CFGFragment(frag.getEnter(), exit);
  }

  private CFGFragment replaceVars(ForNode node, CFGFragment frag,
        Map<TypedDescriptor, ExpressionNode> replacements, int offset) {
    Map<Descriptor, Argument> subs = new HashMap<Descriptor, Argument>();
    Descriptor desc;

    SimpleCFGNode enter = new SimpleCFGNode(new NOPStatement(node));
    frag = new CFGFragment(enter, enter).link(frag);

    SimpleCFGNode cur = enter;
    SimpleCFGNode next = cur.getNext();

    while (next != null) {
      if (next.getStatement() instanceof OpStatement) {
        OpStatement op = (OpStatement)next.getStatement();

        desc = op.getArg1().getDesc();
        if (replacements.keySet().contains(desc)) {
          if (!subs.keySet().contains(desc)) {
            cur = substitute(node, cur, next, replacements.get(desc).accept(this), offset);
            subs.put(desc, cur.getResult());
          }
          op.setArg1(subs.get(desc));
        }

        desc = op.getArg2().getDesc();
        if (replacements.keySet().contains(desc)) {
          if (!subs.keySet().contains(desc)) {
            cur = substitute(node, cur, next, replacements.get(desc).accept(this), offset);
            subs.put(desc, cur.getResult());
          }
          op.setArg2(subs.get(desc)); 
        }
      } else if (next.getStatement() instanceof ArgumentStatement) {
        ArgumentStatement arg = (ArgumentStatement)next.getStatement();
        desc = arg.getArgument().getDesc();
        if (replacements.keySet().contains(desc)) {
          if (!subs.keySet().contains(desc)) {
            cur = substitute(node, cur, next, replacements.get(desc).accept(this), offset);
            subs.put(desc, cur.getResult());
          }
          arg.setArgument(subs.get(desc));
          next.setResult(subs.get(desc));
        }
      }

      cur = next;
      next = cur.getNext();
    }
    
    return frag;
  }

  private SimpleCFGNode substitute(ASTNode node, SimpleCFGNode cur,
      SimpleCFGNode next, CFGFragment frag, int offset) {
    cur.setNext(frag.getEnter());
    cur = frag.getExit();

    if (offset != 0) {
      TypedDescriptor loc = makeTemp(node, DecafType.INT);
      SimpleCFGNode subtractOffset = new SimpleCFGNode(new OpStatement(node,
          AsmOp.SUBTRACT, cur.getResult(), new ConstantArgument(offset), loc));
      cur.setNext(subtractOffset);
      cur = subtractOffset;
    }

    cur.setNext(next);
    return cur;
  }

  private CFGFragment forNodeHelper(ForNode node) {
    // Save increment and exit nodes of any outer for loop
    SimpleCFGNode oldIncrement = increment;
    SimpleCFGNode oldExit = loopExit;

    // Create dummy exit node
    SimpleCFGNode exit = new SimpleCFGNode(new NOPStatement(node));
    loopExit = exit;

    // Evaluate the exit condition
    TypedDescriptor exitLoc = makeTemp(node.getBody(), DecafType.BOOLEAN);
    CFGFragment exitFrag = node.getEnd().accept(this);
    Argument exitVal = exitFrag.getExit().getResult();
    BasicStatement exitStatement = new OpStatement(node, AsmOp.MOVE,
        exitVal, Argument.makeArgument(exitLoc), null);
    exitFrag = exitFrag.append(new SimpleCFGNode(exitStatement));

    // Create a node where the iterator is incremented
    TypedDescriptor loc = node.getInit().getLoc().getDesc();
    Argument loopVar = Argument.makeArgument(loc);
    BasicStatement st = new OpStatement(node, AsmOp.ADD,
        loopVar, new ConstantArgument(1), loc);
    increment = new SimpleCFGNode(st);

    // Compute fragments of the for loop's control flow graph
    CFGFragment initFrag = node.getInit().accept(this);
    CFGFragment bodyFrag = node.getBody().accept(this);

    // Create a branch node where the condition is evaluated and connect it up
    BasicStatement conditionStatement = new OpStatement(node, AsmOp.LESS_THAN,
        loopVar, Argument.makeArgument(exitLoc), null);
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
    SimpleCFGNode breakNode = new SimpleCFGNode(new JumpStatement(node));
    breakNode.setNext(loopExit);
    return new CFGFragment(breakNode, breakNode);
  }

  @Override
  public CFGFragment visit(ContinueNode node) {
    SimpleCFGNode continueNode = new SimpleCFGNode(new JumpStatement(node));
    continueNode.setNext(increment);
    return new CFGFragment(continueNode, continueNode);
  }

  @Override
  public CFGFragment visit(IfNode node) {
    // Create dummy exit node
    SimpleCFGNode exit = new SimpleCFGNode(new NOPStatement(node));

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
    SimpleCFGNode enter = branchNode(node.getCondition(),
        trueFrag.getEnter(),
        falseFrag.getEnter());

    // Enter at the condition, exit via the dummy exit node
    return new CFGFragment(enter, exit);
  }

  @Override
  public CFGFragment visit(CallStatementNode node) {
    return node.getCall().accept(this);
  }

  /*
   * Expression visit methods create an asm OpStatement for each unary
   * or binary operation in the expression
   */
  @Override
  public CFGFragment visit(BoolOpNode node) {
    TypedDescriptor loc = makeTemp(node, DecafType.BOOLEAN);

    if ((node.getOp() == BoolOp.AND) || (node.getOp() == BoolOp.OR)) {
      // Create two nodes which will either move true or false into loc
      BasicStatement trueStmt = new OpStatement(node, AsmOp.MOVE,
          Argument.makeArgument(true), Argument.makeArgument(loc), null);
      BasicStatement falseStmt = new OpStatement(node, AsmOp.MOVE,
          Argument.makeArgument(false), Argument.makeArgument(loc), null);
      SimpleCFGNode trueNode = new SimpleCFGNode(trueStmt);
      SimpleCFGNode falseNode = new SimpleCFGNode(falseStmt);

      // Connect these nodes to a common exit
      SimpleCFGNode exit = new SimpleCFGNode(new NOPStatement(node));
      trueNode.setNext(exit);
      falseNode.setNext(exit);

      // Create a node which branches on the expression - note that
      // branchNode handles short circuiting
      SimpleCFGNode enter = branchNode(node, trueNode, falseNode);
      return new CFGFragment(enter, exit);
    }

    CFGFragment leftFrag = node.getLeft().accept(this);
    CFGFragment rightFrag = node.getRight().accept(this);

    Argument arg1 = leftFrag.getExit().getResult();
    Argument arg2 = rightFrag.getExit().getResult();
    BasicStatement st = new OpStatement(node, getAsmOp(node),
        arg1, arg2, loc);

    // Expressions are evaluated in the following order:
    // <left fragment> <right fragment> <bool op stmt>
    return leftFrag.link(rightFrag).append(new SimpleCFGNode(st));
  }

  @Override
  public CFGFragment visit(MathOpNode node) {
    CFGFragment frag1 = node.getLeft().accept(this);
    CFGFragment frag2 = node.getRight().accept(this);
    TypedDescriptor loc = makeTemp(node, DecafType.INT);
    Argument arg1 = frag1.getExit().getResult();
    Argument arg2 = frag2.getExit().getResult();

    OpStatement s = new OpStatement(node, getAsmOp(node),
        arg1, arg2, loc);

    return frag1.link(frag2).append(new SimpleCFGNode(s));
  }

  @Override
  public CFGFragment visit(NotNode node) {
    CFGFragment frag = node.getExpr().accept(this);
    TypedDescriptor loc = makeTemp(node, DecafType.BOOLEAN);

    OpStatement s = new OpStatement(node, AsmOp.NOT,
        frag.getExit().getResult(), null, loc);

    return frag.append(new SimpleCFGNode(s));
  }


  @Override
  public CFGFragment visit(MinusNode node) {
    CFGFragment frag = node.getExpr().accept(this);
    TypedDescriptor loc = makeTemp(node, DecafType.BOOLEAN);

    OpStatement s = new OpStatement(node, AsmOp.UNARY_MINUS,
        frag.getExit().getResult(), null, loc);

    return frag.append(new SimpleCFGNode(s));
  }

  /**
   * Method calls and system calls are represented by a call node which
   * can contain a list of arguments of arbitrary length.
   */
  @Override
  public CFGFragment visit(MethodCallNode node) {
    TypedDescriptor loc = makeTemp(node, node.getType());

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
                                        node.getDesc().getId(),
                                        args, loc, false);

    if (frag != null) {
      return frag.append(new SimpleCFGNode(s));
    } else {
      SimpleCFGNode n = new SimpleCFGNode(s);
      return new CFGFragment(n, n);
    }
  }


  @Override
  public CFGFragment visit(SystemCallNode node) {
    TypedDescriptor loc = makeTemp(node, node.getType());

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
                                        args, loc, true);

    SimpleCFGNode cn = new SimpleCFGNode(s);
    if (frag == null) {
      return new CFGFragment(cn, cn);
    }
    return frag.append(cn);
  }




  /*
   * Location and Constant visit methods create an ArgumentStatement with
   * the location or value of this expression
   */
  @Override
  public CFGFragment visit(VariableNode node) {
    return node.getLoc().accept(this);
  }

  @Override
  public CFGFragment visit(ScalarLocationNode node) {
    Argument arg = Argument.makeArgument(node.getDesc());
    ArgumentStatement as = new ArgumentStatement(node, arg);
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }

  @Override
  public CFGFragment visit(ArrayLocationNode node) {
    CFGFragment indexFrag = node.getIndex().accept(this);
    Argument index = indexFrag.getExit().getResult();
    if (index instanceof ArrayVariableArgument) {
      // This needs to be flattened rather than passed through.
      ArrayVariableArgument ava = (ArrayVariableArgument)index;
      LocalDescriptor indexTemp =
        makeTemp(node, DecafType.simplify(ava.getDesc().getType()));
      index = Argument.makeArgument(indexTemp);
      indexFrag = indexFrag.append(new SimpleCFGNode(
        new OpStatement(node, AsmOp.MOVE, ava, index, null)));
    }

    // Create the final argument statement fragment
    Argument array = Argument.makeArgument(node.getDesc(), index);
    ArgumentStatement as = new ArgumentStatement(node, array);
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);

    if (skipBoundsChecks &&
        (LoopMonotonicCode.getMonotonicExprs().contains(node.getIndex()))) {
      return indexFrag.append(cfgNode);
    }

    // Get the size of the array and make a fragment which prints oob errors
    Descriptor arrayDesc =
      node.getSymbolTable().get(node.getName(), SymbolType.VARIABLE);
    int size = ((FieldDescriptor)arrayDesc).getLength();
    CFGFragment violation = boundsViolation(node);

    // Create a branch node where the array lower-bound is evaluated
    BasicStatement lowerBoundCheck = new OpStatement(node,
        AsmOp.LESS_THAN, index, new ConstantArgument(0), null);
    SimpleCFGNode lowerBound = new SimpleCFGNode(lowerBoundCheck);
    lowerBound.setBranchTarget(violation.getEnter());

    // Create a branch node where the array upper-bound is evaluated
    BasicStatement upperBoundCheck = new OpStatement(node,
        AsmOp.GREATER_OR_EQUAL, index, new ConstantArgument(size), null);
    SimpleCFGNode upperBound = new SimpleCFGNode(upperBoundCheck);
    upperBound.setBranchTarget(violation.getEnter());

    // Link all the fragments together and return the result
    return indexFrag.append(lowerBound).append(upperBound).append(cfgNode);
  }

  private CFGFragment boundsViolation(ASTNode node) {
    SourceLocation sl = node.getSourceLoc();
    StringNode errorMessage = new StringNode(sl,
        "*** RUNTIME ERROR ***: Array out of Bounds access in method \"%s\"\n");
    StringNode methodName = new StringNode(sl, new String(curMethod));

    ArrayList<Argument> args = new ArrayList<Argument>();
    args.add(errorMessage.accept(this).getExit().getResult());
    args.add(methodName.accept(this).getExit().getResult());

    SimpleCFGNode print = new SimpleCFGNode(
        new CallStatement(node, "printf", args, null, true));
    SimpleCFGNode exit = new SimpleCFGNode(new HaltStatement(node));
    print.setNext(exit);
    return new CFGFragment(print, exit);
  }

  @Override
  public CFGFragment visit(BooleanNode node) {
    Argument arg = Argument.makeArgument(node.getValue());
    ArgumentStatement as = new ArgumentStatement(node, arg);
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }

  @Override
  public CFGFragment visit(IntNode node) {
    Argument arg = Argument.makeArgument(node.getValue());
    ArgumentStatement as = new ArgumentStatement(node, arg);
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }

  @Override
  public CFGFragment visit(StringNode node) {
    String name = ".str" + Math.abs(node.getValue().hashCode());
    cfg.putStringData(name, node);
    ArgumentStatement as = new ArgumentStatement(node,
      Argument.makeArgument(new AnonymousDescriptor(
        new GlobalLocation(name))));
    SimpleCFGNode cfgNode = new SimpleCFGNode(as);
    return new CFGFragment(cfgNode, cfgNode);
  }

  @Override
  public CFGFragment visit(SyscallArgNode node) {
    return node.getChildren().get(0).accept(this);
  }



  /*
   * Utility methods for converting Decaf ops to asm ops
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

