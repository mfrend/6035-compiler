package edu.mit.compilers.le02.cfg;


import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.MathOpNode;
import edu.mit.compilers.le02.ast.MathOpNode.MathOp;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.stgenerator.ASTParentVisitor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public class CFGGeneratorTest extends TestCase {
  private SymbolTable symbolTable;
  private ASTNode root;
  
  public void setUp() {
    symbolTable = new SymbolTable(null);
    root = new MockASTRoot(null, symbolTable);
  }
  
  /**
   * Convert a linear fragment of nodes into a list of statements
   */
  private List<BasicStatement> getStatements(CFGFragment frag) {
    List<BasicStatement> list = new ArrayList<BasicStatement>();
    SimpleCFGNode node = frag.getEnter();
    while (node != frag.getExit().getNext()) {
      switch (node.getStatement().getType()) {
        case OP:
        case CALL:
          list.add(node.getStatement());
          break;
        default:
          break;
      }

      node = node.getNext();
    } 
    
    return list;
  }

  public void testExpressionFlattening() {
    ExpressionNode node = new MathOpNode(null,
                            new MathOpNode(null,
                              new IntNode(null, 1),
                              new IntNode(null, 2),
                              MathOp.ADD
                            ),
                            new IntNode(null, 4),
                            MathOp.MULTIPLY
                          );
    setParents(node);
    
    CFGFragment frag = node.accept(CFGGenerator.getInstance());
    
    List<BasicStatement> list = getStatements(frag);
    assertEquals(2, list.size());
    
    BasicStatement bs = list.get(0);
    assertTrue(bs instanceof OpStatement);
    OpStatement opSt = (OpStatement) bs;
    
    checkOpStatement(opSt, AsmOp.ADD, 
                     Argument.makeArgument(1), Argument.makeArgument(2));
    
    VariableLocation loc = opSt.getResult();
    bs = list.get(1);
    assertTrue(bs instanceof OpStatement);
    opSt = (OpStatement) bs;
    
    checkOpStatement(opSt, AsmOp.MULTIPLY,
                     Argument.makeArgument(loc), Argument.makeArgument(4));
    
    assertEquals(2, symbolTable.size());
    
  }
  private void setParents(ASTNode node) {
    node.accept(new ASTParentVisitor());
    node.setParent(root);
  }
  private void checkOpStatement(OpStatement opSt, 
                                AsmOp op, Argument arg1, Argument arg2) {
    
    assertEquals(op, opSt.getOp());
    assertEquals(arg1, opSt.getArg1());
    assertEquals(arg2, opSt.getArg2());
  }

}
