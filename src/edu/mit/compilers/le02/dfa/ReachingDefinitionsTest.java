package edu.mit.compilers.le02.dfa;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;
import edu.mit.compilers.le02.GlobalLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.dfa.ReachingDefinitions.BlockItem;


public class ReachingDefinitionsTest extends TestCase {
  

  private VariableLocation makeLoc(String name) {
    return new GlobalLocation(name);
  }
  
  private OpStatement makeDef(String name, int value) {
    GlobalLocation loc = new GlobalLocation(name);
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

  private void checkReachingDefs(BlockItem bi, 
                                 String name, int num, int value) {
    Set<BasicStatement> reachingDefs;
    
    reachingDefs = bi.getReachingDefinitions(makeLoc(name));
    assertEquals(num, reachingDefs.size());
    for (BasicStatement s : reachingDefs) {
      OpStatement def = (OpStatement) s;
      assertEquals(value, ((ConstantArgument) def.getArg1()).getInt());  
    }
  }
}
