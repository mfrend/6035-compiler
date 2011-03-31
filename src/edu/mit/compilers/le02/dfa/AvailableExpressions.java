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
import edu.mit.compilers.le02.dfa.ReachingDefinitions.BlockItem;
import edu.mit.compilers.le02.opt.BasicBlockVisitor;

public class AvailableExpressions extends BasicBlockVisitor
                                  implements Lattice<BitSet, BasicBlockNode> {

  private List<OpStatement> expressions;
  private Set<OpStatement> expressionSet;
  private Map<OpStatement, Integer> exprIndices;
  private Map<BasicBlockNode, BlockItem> blockExpressions;
  private Map<VariableLocation, BitSet> exprsFromVar; 
  private BitSet callKill;

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

        OpStatement expr = (OpStatement) s;
        index = parent.exprIndices.get(s);
        this.genSet.set(index);
        
        BitSet killed = parent.exprsFromVar.get(
                            parent.getExpressionTarget(expr));
        
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
      return getBitsetExpressions(this.getIn()).contains(expr);
    }

    private List<BasicStatement> getBitsetExpressions(BitSet bs) {
      if (parent.expressions.isEmpty()) {
        return Collections.emptyList();
      }

      List<BasicStatement> exprs = new ArrayList<BasicStatement>();

      int len = parent.expressions.size();
      for (int i = 0 ; i < len; i++) {
        BasicStatement s = parent.expressions.get(i);

        if (bs.get(i)) {
          exprs.add(s);
        }
      }

      return exprs;
    }

    public List<BasicStatement> getInExpressions() {
      return getBitsetExpressions(this.getIn());
    }

    public List<BasicStatement> getOutExpressions() {
      return getBitsetExpressions(this.getOut());
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
    this.expressions = new ArrayList<OpStatement>();
    this.expressionSet = new HashSet<OpStatement>();
    this.exprIndices = new HashMap<OpStatement, Integer>();
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
        OpStatement expr = (OpStatement) s;
        
        if (expressionSet.contains(expr)) {
          blockExprs.add(expr);
          continue;
        }
                
        blockExprs.add(expr);
        expressions.add(expr);
        expressionSet.add(expr);
        int index = expressions.size() - 1;
        exprIndices.put(expr, index);
        
        VariableArgument vArg; 
        if (expr.getArg1().isVariable()) {
          vArg = (VariableArgument) expr.getArg1();
          if (vArg.getLoc().getLocation().getLocationType() == LocationType.GLOBAL) {
            callKill.set(index);
          }

          BitSet bs = exprsFromVar.get(vArg.getLoc().getLocation());
          
          if (bs == null) {
            bs = new BitSet();
          }
          
          bs.set(index);
          exprsFromVar.put(vArg.getLoc().getLocation(), bs);
        }
        
        if (expr.getArg2().isVariable()) {
          vArg = (VariableArgument) expr.getArg2();
          if (vArg.getLoc().getLocation().getLocationType() == LocationType.GLOBAL) {
            callKill.set(index);
          }
          
          BitSet bs = exprsFromVar.get(vArg.getLoc().getLocation());
          
          if (bs == null) {
            bs = new BitSet();
          }
          
          bs.set(index);
          exprsFromVar.put(vArg.getLoc().getLocation(), bs);
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
