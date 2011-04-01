package edu.mit.compilers.le02.dfa;

import java.util.List;

import junit.framework.TestCase;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.GlobalLocation;
import edu.mit.compilers.le02.StackLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.NOPStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.dfa.AvailableExpressions.BlockItem;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;


public class AvailableExpressionsTest extends TestCase {

  private VariableLocation makeLoc(String name) {
    return new GlobalLocation(name);
  }

  private VariableLocation makeLocalLoc(int offset) {
    return new StackLocation(offset);
  }

  private OpStatement makeExpr(AsmOp op, String arg1, String arg2, String res) {
    GlobalLocation loc = new GlobalLocation(arg1);
    GlobalLocation loc2 = new GlobalLocation(arg2);
    return new OpStatement(null, op,
                           Argument.makeArgument(new AnonymousDescriptor(loc)),
                           Argument.makeArgument(new AnonymousDescriptor(loc2)),
                           new FieldDescriptor(null, res, DecafType.INT));
  }
  

  private OpStatement makeExpr(AsmOp op, int arg1, int arg2, String res) {
    StackLocation loc = new StackLocation(arg1);
    StackLocation loc2 = new StackLocation(arg2);
    return new OpStatement(null, op,
                           Argument.makeArgument(new AnonymousDescriptor(loc)),
                           Argument.makeArgument(new AnonymousDescriptor(loc2)),
                           new FieldDescriptor(null, res, DecafType.INT));
  }



  private OpStatement makeDef(String name, int value) {
    GlobalLocation loc = new GlobalLocation(name);
    return new OpStatement(null, AsmOp.MOVE,
                           Argument.makeArgument(value),
                           Argument.makeArgument(new AnonymousDescriptor(loc)),
                           null);
  }
  


  private OpStatement makeDef(int name, int value) {
    StackLocation loc = new StackLocation(name);
    return new OpStatement(null, AsmOp.MOVE,
                           Argument.makeArgument(value),
                           Argument.makeArgument(new AnonymousDescriptor(loc)),
                           null);
  }

  /**
   * Test that the reaching definitions thing works for a single
   * block.
   */
  public void testSmoke() {

    BasicBlockNode node = new BasicBlockNode("main", "main");
    OpStatement expr = makeExpr(AsmOp.ADD, "var", "var2", "var3");

    node.addStatement(expr);

    AvailableExpressions exprs = new AvailableExpressions(node);
    BlockItem bi = exprs.getExpressions(node);
    assertNotNull(bi);
    assertTrue(bi.getInExpressions().isEmpty());

    List<BasicStatement> nodeDefs = bi.getOutExpressions();
    assertEquals(1, nodeDefs.size());
    assertSame(expr, nodeDefs.get(0));
  }

  public void testNullResultStatement() {
    BasicBlockNode node = new BasicBlockNode("main", "main");

    node.addStatement(new CallStatement(null, "main", null, null));

    AvailableExpressions exprs = new AvailableExpressions(node);
  }

  public void testUnaryStatements() {
    BasicBlockNode node = new BasicBlockNode("main", "main");

    GlobalLocation loc = new GlobalLocation("var");
    node.addStatement(new OpStatement(null, AsmOp.NOT,
                          Argument.makeArgument(new AnonymousDescriptor(loc)),
                          null,
                          new FieldDescriptor(null, "res", DecafType.BOOLEAN)));

    node.addStatement(new OpStatement(null, AsmOp.UNARY_MINUS,
                          Argument.makeArgument(new AnonymousDescriptor(loc)),
                          null,
                          new FieldDescriptor(null, "res", DecafType.INT)));

    AvailableExpressions exprs = new AvailableExpressions(node);
  }


  /**
   * Test that expressions are available only if available on two branches
   */
  public void testBranch() {
    BasicBlockNode top = new BasicBlockNode("main", "main");
    BasicBlockNode left = new BasicBlockNode("block1", "main");
    BasicBlockNode right = new BasicBlockNode("block2", "main");
    BasicBlockNode end = new BasicBlockNode("block3", "main");

    top.setNext(left);
    top.setBranchTarget(right);
    left.setNext(end);
    right.setNext(end);

    top.addStatement(makeExpr(AsmOp.ADD, "var1", "var2", "res"));
    top.addStatement(makeExpr(AsmOp.SUBTRACT, "var3", "var4", "res2"));

    left.addStatement(makeExpr(AsmOp.MULTIPLY, "x", "y", "var1"));
    left.addStatement(makeExpr(AsmOp.MODULO, "var1", "z", "res3"));

    right.addStatement(makeExpr(AsmOp.DIVIDE, "x", "z", "res3"));
    right.addStatement(makeExpr(AsmOp.MODULO, "var1", "z", "res4"));

    AvailableExpressions exprs = new AvailableExpressions(top);

    BlockItem bi;
    List<BasicStatement> nodeDefs;

    // Check top block
    bi = exprs.getExpressions(top);
    assertNotNull(bi);
    nodeDefs = bi.getOutExpressions();
    assertEquals(2, nodeDefs.size());

    bi = exprs.getExpressions(left);
    assertNotNull(bi);
    nodeDefs = bi.getOutExpressions();
    assertEquals(3, nodeDefs.size());
    assertTrue(isAvailable(bi, AsmOp.ADD, "var1", "var2"));
    assertTrue(isAvailable(bi, AsmOp.SUBTRACT, "var3", "var4"));

    bi = exprs.getExpressions(right);
    assertNotNull(bi);
    nodeDefs = bi.getOutExpressions();
    assertEquals(4, nodeDefs.size());
    assertTrue(isAvailable(bi, AsmOp.ADD, "var1", "var2"));
    assertTrue(isAvailable(bi, AsmOp.SUBTRACT, "var3", "var4"));

    bi = exprs.getExpressions(end);
    assertNotNull(bi);
    assertFalse(isAvailable(bi, AsmOp.ADD, "var1", "var2"));
    assertTrue(isAvailable(bi, AsmOp.SUBTRACT, "var3", "var4"));
    assertFalse(isAvailable(bi, AsmOp.MULTIPLY, "x", "y"));
    assertFalse(isAvailable(bi, AsmOp.DIVIDE, "y", "z"));
    assertTrue(isAvailable(bi, AsmOp.MODULO, "var1", "z"));
  }
  
  /**
   * Test that expressions are clobbered by moves.
   * See cse-08
   */
  public void testBranch2() {
    BasicBlockNode top = new BasicBlockNode("main", "main");
    BasicBlockNode left = new BasicBlockNode("block1test", "main");
    BasicBlockNode end = new BasicBlockNode("block2test", "main");

    top.setNext(left);
    top.setBranchTarget(end);
    left.setNext(end);

    top.addStatement(makeExpr(AsmOp.ADD, -8, -16, "res1"));

    left.addStatement(makeExpr(AsmOp.ADD, -8, -16, "res2"));
    left.addStatement(makeDef(-8, 0));
    
    end.addStatement(makeExpr(AsmOp.ADD, -8, -16, "res3"));

    AvailableExpressions exprs = new AvailableExpressions(top);

    BlockItem bi;
    List<BasicStatement> nodeDefs;

    // Check top block
    bi = exprs.getExpressions(top);
    assertNotNull(bi);
    nodeDefs = bi.getOutExpressions();
    assertEquals(1, nodeDefs.size());

    bi = exprs.getExpressions(left);
    assertNotNull(bi);
    nodeDefs = bi.getOutExpressions();
    assertEquals(0, nodeDefs.size());
    assertTrue(isAvailable(bi, AsmOp.ADD, -8, -16));

    bi = exprs.getExpressions(end);
    assertNotNull(bi);
    assertFalse(isAvailable(bi, AsmOp.ADD, -8, -16));
  }


  private boolean isAvailable(BlockItem bi,
                              AsmOp op, String arg1, String arg2) {

    OpStatement expr = makeExpr(op, arg1, arg2, "dummy");

    return bi.expressionIsAvailable(expr);
  }

  private boolean isAvailable(BlockItem bi,
                              AsmOp op, int arg1, int arg2) {

    OpStatement expr = makeExpr(op, arg1, arg2, "dummy");

    return bi.expressionIsAvailable(expr);
  }


}
