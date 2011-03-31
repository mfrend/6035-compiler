package edu.mit.compilers.le02.dfa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.opt.BasicBlockVisitor;

public class AvailableExpressions extends BasicBlockVisitor
                                  implements Lattice<BitSet, BasicBlockNode> {

  private List<Expression> expressions;
  private Set<Expression> expressionSet;
  private Map<Expression, Integer> exprIndices;
  private Map<BasicBlockNode, BlockItem> blockExpressions;
  private Map<VariableLocation, BitSet> exprsFromVar; 
  private BitSet callKill;
  
  public static class Expression {
    private OpStatement expr;
    
    public Expression(OpStatement expr) {
      this.expr = expr;
    }
    
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Expression)) return false;
      
      Expression e = (Expression) o;
      return expr.expressionEquals(e.expr);
    }
    
    public OpStatement getStatement() {
      return expr;
    }
    
    @Override
    public int hashCode() {
      return expr.getOp().hashCode() + 
             + ((expr.getArg1() == null) ? expr.getArg1().hashCode() + 1 : 0)
             + ((expr.getArg2() == null) ? expr.getArg2().hashCode() + 1 : 0);
    }
  }

  public class BlockItem extends GenKillItem {
    private AvailableExpressions parent;
    private BasicBlockNode node;
    private List<BasicStatement> blockExprs;
    private BitSet genSet;
    private BitSet killSet;
    
    public BlockItem(AvailableExpressions parent,
                     BasicBlockNode node, List<BasicStatement> blockExprs) {
      this.parent = parent;
      this.node = node;
      this.blockExprs = blockExprs;

      this.genSet = new BitSet();
      this.killSet = new BitSet();
    }

    private void init() {
      int index;
      for (BasicStatement s : blockExprs) {
        if (s.getType() == BasicStatementType.CALL) {
          this.genSet.andNot(parent.callKill);
          this.killSet.or(parent.callKill);
          continue;
        }

        
        Expression expr = new Expression((OpStatement) s);
        index = parent.exprIndices.get(expr);
        this.genSet.set(index);
        
        BitSet killed = parent.exprsFromVar.get(
                            parent.getExpressionTarget(expr.getStatement()));
        
        if (killed != null) {
          this.killSet.or(killed);
        }
      }
    }
    

    /**
     * Evaluates to true if the given expression is available at the start of
     * this basic block
     * @param expr - The expression to check
     * @return true if expr is available, false otherwise.
     */
    public boolean expressionIsAvailable(OpStatement expr) {
      return getBitsetExpressions(this.getIn()).contains(new Expression(expr));
    }

    private List<Expression> getBitsetExpressions(BitSet bs) {
      if (parent.expressions.isEmpty()) {
        return Collections.emptyList();
      }

      List<Expression> exprs = new ArrayList<Expression>();

      int len = parent.expressions.size();
      for (int i = 0 ; i < len; i++) {
        Expression expr = parent.expressions.get(i);

        if (bs.get(i)) {
          exprs.add(expr);
        }
      }

      return exprs;
    }
    
    private List<BasicStatement> getBitsetStatements(BitSet bs) {
      if (parent.expressions.isEmpty()) {
        return Collections.emptyList();
      }

      List<BasicStatement> exprs = new ArrayList<BasicStatement>();

      int len = parent.expressions.size();
      for (int i = 0 ; i < len; i++) {
        Expression expr = parent.expressions.get(i);

        if (bs.get(i)) {
          exprs.add(expr.getStatement());
        }
      }

      return exprs;
    }

    public List<BasicStatement> getInExpressions() {
      return getBitsetStatements(this.getIn());
    }

    public List<BasicStatement> getOutExpressions() {
      return getBitsetStatements(this.getOut());
    }

    @Override
    protected BitSet gen() {
      return genSet;
    }

    @Override
    protected BitSet kill() {
      return killSet;
    }

    @Override
    public Collection<WorklistItem<BitSet>> predecessors() {
      ArrayList<WorklistItem<BitSet>> ret =
        new ArrayList<WorklistItem<BitSet>>();
      for (BasicBlockNode pred : this.node.getPredecessors()) {
        WorklistItem<BitSet> item = parent.blockExpressions.get(pred);
        ret.add(item);
      }
      return ret;
    }

    @Override
    public Collection<WorklistItem<BitSet>> successors() {
      ArrayList<WorklistItem<BitSet>> ret =
        new ArrayList<WorklistItem<BitSet>>();

      WorklistItem<BitSet> item;
      if (node.getNext() != null) {
        item = parent.blockExpressions.get(node.getNext());
        ret.add(item);
      }


      if (node.getBranchTarget() != null) {
        item = parent.blockExpressions.get(node.getBranchTarget());
        ret.add(item);
      }

      return ret;
    }
  }

  public AvailableExpressions(BasicBlockNode methodRoot) {
    this.expressions = new ArrayList<Expression>();
    this.expressionSet = new HashSet<Expression>();
    this.exprIndices = new HashMap<Expression, Integer>();
    this.blockExpressions = new HashMap<BasicBlockNode, BlockItem>();
    this.exprsFromVar = new HashMap<VariableLocation, BitSet>();
    this.callKill = new BitSet();
    this.visit(methodRoot);

    for (BlockItem bi : blockExpressions.values()) {
      bi.init();
    }
    
    BlockItem start = blockExpressions.get(methodRoot);
    BitSet init = new BitSet(expressions.size());

    // Run a fixed point algorithm on the definitions to calculate the
    // reaching definitions.
    WorklistAlgorithm.runForward(blockExpressions.values(), this, start, init);
  }
  
  
  public BlockItem getExpressions(BasicBlockNode node) {
    return blockExpressions.get(node);
  }
  
  @Override
  protected void processNode(BasicBlockNode node) {
    this.blockExpressions.put(node, calcExpressions(node));
  }
  
  private BlockItem calcExpressions(BasicBlockNode node) {
    List<BasicStatement> blockExprs = new ArrayList<BasicStatement>();

    for (BasicStatement s : node.getStatements()) {
      if (isExpression(s)) {
        OpStatement opSt = (OpStatement) s;
        Expression expr = new Expression(opSt);

        blockExprs.add(opSt);
        
        if (expressionSet.contains(expr)) {
          continue;
        }
                
        expressions.add(expr);
        expressionSet.add(expr);
        int index = expressions.size() - 1;
        exprIndices.put(expr, index);
        
        VariableArgument vArg; 
        if (opSt.getArg1().isVariable()) {
          vArg = (VariableArgument) opSt.getArg1();
          if (vArg.getDesc().getLocation().getLocationType() == LocationType.GLOBAL) {
            callKill.set(index);
          }

          BitSet bs = exprsFromVar.get(vArg.getDesc().getLocation());
          
          if (bs == null) {
            bs = new BitSet();
          }
          
          bs.set(index);
          exprsFromVar.put(vArg.getDesc().getLocation(), bs);
        }
        
        if (opSt.getArg2().isVariable()) {
          vArg = (VariableArgument) opSt.getArg2();
          if (vArg.getDesc().getLocation().getLocationType() == LocationType.GLOBAL) {
            callKill.set(index);
          }
          
          BitSet bs = exprsFromVar.get(vArg.getDesc().getLocation());
          
          if (bs == null) {
            bs = new BitSet();
          }
          
          bs.set(index);
          exprsFromVar.put(vArg.getDesc().getLocation(), bs);
        }

      }
      else if (s.getType() == BasicStatementType.CALL) {
        blockExprs.add(s);
      }
    }

    return new BlockItem(this, node, blockExprs);

  }

  private boolean isExpression(BasicStatement s) {
    if (!(s instanceof OpStatement)) {
      return false;
    }

    OpStatement ops = (OpStatement) s;
    switch (ops.getOp()) {
      case MOVE:
      case RETURN:
      case ENTER:
        return false;
      default:
        return true;
    }
  }
  
  private VariableLocation getExpressionTarget(OpStatement expr) {
    switch (expr.getOp()) {
      case MOVE:
      case RETURN:
      case ENTER:
        ErrorReporting.reportErrorCompat(new Exception("Tried to get target " +
        "of a non-expression!"));
        return null;
      default:
        return expr.getResult().getLocation();
        
    }
  }

  
  @Override
  public BitSet abstractionFunction(BasicBlockNode value) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public BitSet transferFunction(BitSet[] values) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public BitSet bottom() {
    BitSet s = new BitSet(expressions.size());
    s.flip(0, expressions.size());
    return s;
  }

  @Override
  public BitSet top() {
    return new BitSet(expressions.size());
  }

  @Override
  public BitSet leastUpperBound(BitSet v1, BitSet v2) {
    BitSet ret = (BitSet) v1.clone();
    ret.and(v2);
    return ret;
  }
  
  

}
