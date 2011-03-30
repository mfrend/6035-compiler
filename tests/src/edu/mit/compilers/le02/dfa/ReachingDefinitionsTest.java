package edu.mit.compilers.le02.dfa;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;
import edu.mit.compilers.le02.GlobalLocation;
import edu.mit.compilers.le02.StackLocation;
import edu.mit.compilers.le02.Util;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.dfa.ReachingDefinitions.BlockItem;


public class ReachingDefinitionsTest extends TestCase {

  private VariableLocation makeLoc(String name) {
    return new GlobalLocation(name);
  }

  private VariableLocation makeLocalLoc(int offset) {
    return new StackLocation(offset);
  }

  private OpStatement makeDef(String name, int value) {
    GlobalLocation loc = new GlobalLocation(name);
    return new OpStatement(null, AsmOp.MOVE,
                           Argument.makeArgument(value),
                           Argument.makeArgument(loc), null);
  }


  private OpStatement makeLocalDef(int offset, int value) {
    StackLocation loc = new StackLocation(offset);
    return new OpStatement(null, AsmOp.MOVE,
                           Argument.makeArgument(value),
                           Argument.makeArgument(loc), null);
  }

  /**
   * Test that the reaching definitions thing works for a single
   * block.
   */
  public void testSmoke() {

    BasicBlockNode node = new BasicBlockNode("main", "main");
    OpStatement def = makeDef("var", 10);

    node.addStatement(def);

    ControlFlowGraph cfg = new ControlFlowGraph();
    cfg.putMethod("main", node);

    ReachingDefinitions defs = new ReachingDefinitions(node);
    BlockItem bi = defs.getDefinitions(node);
    assertNotNull(bi);
    assertTrue(bi.getInDefinitions().isEmpty());

    List<BasicStatement> nodeDefs = bi.getOutDefinitions();
    assertEquals(1, nodeDefs.size());
    assertSame(def, nodeDefs.get(0));
  }

  /**
   * Test that definitions carry from block to block, and are
   * properly overwritten (for a cfg of blocks arranged serially)
   */
  public void testOverride() {
    BasicBlockNode top = new BasicBlockNode("main", "main");
    BasicBlockNode middle = new BasicBlockNode("block1", "main");
    BasicBlockNode bot = new BasicBlockNode("block2", "main");
    BasicBlockNode end = new BasicBlockNode("block3", "main");

    top.setNext(middle);
    middle.setNext(bot);
    bot.setNext(end);

    top.addStatement(makeDef("var", 10));
    top.addStatement(makeDef("var2", 20));
    middle.addStatement(makeDef("var", 100));
    bot.addStatement(makeDef("var", 1000));

    ReachingDefinitions defs = new ReachingDefinitions(top);

    BlockItem bi;
    List<BasicStatement> nodeDefs;

    // Check top block
    bi = defs.getDefinitions(top);
    assertNotNull(bi);
    nodeDefs = bi.getOutDefinitions();
    assertEquals(2, nodeDefs.size());

    bi = defs.getDefinitions(middle);
    assertNotNull(bi);
    nodeDefs = bi.getOutDefinitions();
    assertEquals(2, nodeDefs.size());
    checkReachingDefs(bi, "var", 1, 10);
    checkReachingDefs(bi, "var2", 1, 20);

    bi = defs.getDefinitions(bot);
    assertNotNull(bi);
    nodeDefs = bi.getOutDefinitions();
    assertEquals(2, nodeDefs.size());
    checkReachingDefs(bi, "var", 1, 100);
    checkReachingDefs(bi, "var2", 1, 20);

    bi = defs.getDefinitions(end);
    assertNotNull(bi);
    nodeDefs = bi.getOutDefinitions();
    assertEquals(2, nodeDefs.size());
    checkReachingDefs(bi, "var", 1, 1000);
    checkReachingDefs(bi, "var2", 1, 20);
  }


  /**
   * Test that definitions carry from block to block, and are
   * properly overwritten (for a cfg of blocks arranged serially)
   */
  public void testBranches() {
    BasicBlockNode top = new BasicBlockNode("main", "main");
    BasicBlockNode left = new BasicBlockNode("block1", "main");
    BasicBlockNode left2 = new BasicBlockNode("block2", "main");
    BasicBlockNode right = new BasicBlockNode("block3", "main");
    BasicBlockNode loop = new BasicBlockNode("block4", "main");
    BasicBlockNode end = new BasicBlockNode("block5", "main");

    top.setNext(left);
    top.setBranchTarget(right);
    left.setNext(left2);
    left2.setNext(end);
    right.setNext(loop);
    loop.setNext(end);
    loop.setBranchTarget(loop);

    top.addStatement(makeDef("var", 10));
    top.addStatement(makeDef("var2", 20));
    top.addStatement(makeDef("var3", 30));
    left.addStatement(makeDef("var", 100));
    right.addStatement(makeDef("var2", 200));
    loop.addStatement(makeDef("var2", 2000));
    loop.addStatement(makeDef("var3", 3000));

    ReachingDefinitions defs = new ReachingDefinitions(top);

    BlockItem bi;
    List<BasicStatement> nodeDefs;

    // Check top block
    bi = defs.getDefinitions(top);
    assertNotNull(bi);
    nodeDefs = bi.getOutDefinitions();
    assertEquals(3, nodeDefs.size());

    bi = defs.getDefinitions(left);
    assertNotNull(bi);
    checkReachingDefs(bi, "var", 1, 10);
    checkReachingDefs(bi, "var2", 1, 20);
    checkReachingDefs(bi, "var3", 1, 30);

    bi = defs.getDefinitions(left2);
    assertNotNull(bi);
    checkReachingDefs(bi, "var", 1, 100);
    checkReachingDefs(bi, "var2", 1, 20);
    checkReachingDefs(bi, "var3", 1, 30);

    bi = defs.getDefinitions(right);
    assertNotNull(bi);
    checkReachingDefs(bi, "var", 1, 10);
    checkReachingDefs(bi, "var2", 1, 20);
    checkReachingDefs(bi, "var3", 1, 30);

    bi = defs.getDefinitions(loop);
    assertNotNull(bi);
    checkReachingDefs(bi, "var", 1, 10);
    checkReachingDefs(bi, "var2", Util.makeSet(200, 2000));
    checkReachingDefs(bi, "var3", Util.makeSet(30, 3000));

    bi = defs.getDefinitions(end);
    assertNotNull(bi);
    checkReachingDefs(bi, "var", Util.makeSet(10, 100));
    checkReachingDefs(bi, "var2", Util.makeSet(20, 2000));
    checkReachingDefs(bi, "var3", Util.makeSet(30, 3000));
  }

  /**
   * Test that definitions carry from block to block, and are
   * properly overwritten (for a cfg of blocks arranged serially)
   */
  public void testCallClearsGlobals() {
    BasicBlockNode top = new BasicBlockNode("main", "main");
    BasicBlockNode middle = new BasicBlockNode("block1", "main");
    BasicBlockNode end = new BasicBlockNode("block2", "main");

    top.setNext(middle);
    middle.setNext(end);

    top.addStatement(makeDef("var", 10));
    top.addStatement(makeLocalDef(-16, 20));
    top.addStatement(makeDef("var3", 30));

    List<Argument> args = Collections.emptyList();
    CallStatement cs = new CallStatement(null, "dummy", args, null);

    middle.addStatement(makeDef("dummyvar", 11));
    middle.addStatement(cs);
    middle.addStatement(makeDef("var4", 40));

    ReachingDefinitions defs = new ReachingDefinitions(top);

    BlockItem bi;
    List<BasicStatement> nodeDefs;

    // Check top block
    bi = defs.getDefinitions(top);
    assertNotNull(bi);
    nodeDefs = bi.getOutDefinitions();
    assertEquals(3, nodeDefs.size());

    // Check middle
    bi = defs.getDefinitions(middle);
    assertNotNull(bi);
    checkReachingDefs(bi, "var", 1, 10);
    checkReachingDefs(bi, -16, 1, 20);
    checkReachingDefs(bi, "var3", 1, 30);

    // Check that call statement cleared vars
    bi = defs.getDefinitions(end);
    assertNotNull(bi);
    checkReachingDefs(bi, "var", 0, 10);
    checkReachingDefs(bi, "var3", 0, 30);
    checkReachingDefs(bi, "dummyvar", 0, 11);
    checkReachingDefs(bi, -16, 1, 20);
    checkReachingDefs(bi, "var4", 1, 40);
  }

  private void checkReachingDefs(BlockItem bi,
      String name, int num, int value) {
    Collection<BasicStatement> reachingDefs;

    reachingDefs = bi.getReachingDefinitions(makeLoc(name));
    assertEquals(num, reachingDefs.size());
    for (BasicStatement s : reachingDefs) {
      OpStatement def = (OpStatement) s;
      assertEquals(value, ((ConstantArgument) def.getArg1()).getInt());
    }
  }

  private void checkReachingDefs(BlockItem bi,
      int offset, int num, int value) {
    Collection<BasicStatement> reachingDefs;

    reachingDefs = bi.getReachingDefinitions(makeLocalLoc(offset));
    assertEquals(num, reachingDefs.size());
    for (BasicStatement s : reachingDefs) {
      OpStatement def = (OpStatement) s;
      assertEquals(value, ((ConstantArgument) def.getArg1()).getInt());
    }
  }

  private void checkReachingDefs(BlockItem bi,
      String name, Set<Integer> defs) {
    Collection<BasicStatement> reachingDefs;

    reachingDefs = bi.getReachingDefinitions(makeLoc(name));
    assertEquals(defs.size(), reachingDefs.size());

    Set<Integer> actualDefs = new HashSet<Integer>();
    for (BasicStatement s : reachingDefs) {
      OpStatement def = (OpStatement) s;
      actualDefs.add(((ConstantArgument) def.getArg1()).getInt());
    }
    assertEquals(defs, actualDefs);
  }

}
