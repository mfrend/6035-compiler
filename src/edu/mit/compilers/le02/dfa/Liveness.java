package edu.mit.compilers.le02.dfa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.opt.BasicBlockVisitor;

/**
 *
 * @author Shaunak Kishore
 *
 */
public class Liveness extends BasicBlockVisitor
implements Lattice<BitSet, BasicBlockNode> {
  private Map<BasicBlockNode, BlockItem> blockItems;
  private Map<BasicStatement, Integer> definitionIndices;
  private Map<BasicStatement, List<Integer>> useIndices;

  private List<VariableLocation> variables;
  private Map<VariableLocation, Integer> variableIndices;

  public class BlockItem extends GenKillItem {
    private Liveness parent;
    private BasicBlockNode node;
    private List<BasicStatement> blockItems;
    private BitSet genSet;
    private BitSet killSet;

    public BlockItem(Liveness parent,
        BasicBlockNode node, List<BasicStatement> blockDefs) {
      this.parent = parent;
      this.node = node;
      this.blockItems = blockDefs;

      this.genSet = new BitSet();
      this.killSet = new BitSet();
      this.init();
    }

    private void init() {
      int index;

      for (BasicStatement s : blockItems) {
        index = parent.definitionIndices.get(s);
        if (!this.killSet.get(index)) {
          this.genSet.set(index);
        }

        for (Integer i : parent.useIndices.get(s)) {
          this.killSet.set(i);
        }
      }
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
        WorklistItem<BitSet> item = parent.blockItems.get(pred);
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
        item = parent.blockItems.get(node.getNext());
        ret.add(item);
      }

      if (node.getBranchTarget() != null) {
        item = parent.blockItems.get(node.getBranchTarget());
        ret.add(item);
      }

      return ret;
    }
  }

  public Liveness(BasicBlockNode methodEnd) {
    this.blockItems = new HashMap<BasicBlockNode, BlockItem>();
    this.definitionIndices = new HashMap<BasicStatement, Integer>();
    this.useIndices = new HashMap<BasicStatement, List<Integer>>();

    this.variables = new ArrayList<VariableLocation>();
    this.variableIndices = new HashMap<VariableLocation, Integer>();

    // TODO Probably not in this visitor, but write a way to find the
    // 'last' basic block in a method. If there is no unique last block,
    // create one
    this.visit(methodEnd);
    BlockItem start = blockItems.get(methodEnd);
    // TODO Make the initial bitset the set of globals - these are
    // the variables which are live outside the method
    BitSet init = bottom();

    // Run a fixed point algorithm on the basic blocks to calculate the
    // list of live variables for each block
    WorklistAlgorithm.runBackwards(blockItems.values(), this, start, init);

    // TODO Either in this visitor or in another, run through definitions
    // and remove all definitions of dead variables
  }

  @Override
  protected void processNode(BasicBlockNode node) {
    this.blockItems.put(node, calcDefinitions(node));
  }

  public BlockItem getDefinitions(BasicBlockNode node) {
    return blockItems.get(node);
  }

  private BlockItem calcDefinitions(BasicBlockNode node) {
    List<BasicStatement> blockDefs = new ArrayList<BasicStatement>();

    for (BasicStatement s : node.getStatements()) {
      if (isDefinition(s)) {
        OpStatement def = (OpStatement) s;
        blockDefs.add(s);

        VariableLocation target = getDefinitionTarget(def);
        definitionIndices.put(s, getVarIndex(target));

        List<Integer> uses = new ArrayList<Integer>();
        for (VariableLocation use : getDefinitionUses(def)) {
          uses.add(getVarIndex(use));
        }
        useIndices.put(s, uses);
      }
    }

    return new BlockItem(this, node, blockDefs);
  }

  private int getVarIndex(VariableLocation loc) {
    Integer index = variableIndices.get(loc);
    if (index == null) {
      index = new Integer(variableIndices.size());
      variableIndices.put(loc, index);
    }

    return index;
  }

  private boolean isDefinition(BasicStatement s) {
    if (!(s instanceof OpStatement)) {
      return false;
    }

    OpStatement ops = (OpStatement) s;
    switch (ops.getOp()) {
      case MOVE:
      case ADD:
      case SUBTRACT:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
      case UNARY_MINUS:
      case NOT:
        return true;
      default:
        return false;
    }
  }

  private VariableLocation getDefinitionTarget(OpStatement def) {
    switch (def.getOp()) {
      case MOVE:
        return ((VariableArgument) def.getArg2()).getLoc();
      case ADD:
      case SUBTRACT:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
      case UNARY_MINUS:
      case NOT:
        return def.getResult();
      default:
        ErrorReporting.reportErrorCompat(new Exception("Tried to get target " +
        "of a non definition!"));
        return null;
    }
  }

  private List<VariableLocation> getDefinitionUses(OpStatement def) {
    List<VariableLocation> ret = new ArrayList<VariableLocation>();

    switch (def.getOp()) {
      case MOVE:
      case UNARY_MINUS:
      case NOT:
        if (def.getArg1() instanceof VariableArgument) {
          ret.add(((VariableArgument) def.getArg1()).getLoc());
        }
        break;
      case ADD:
      case SUBTRACT:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
        if (def.getArg1() instanceof VariableArgument) {
          ret.add(((VariableArgument) def.getArg1()).getLoc());
        }
        if (def.getArg2() instanceof VariableArgument) {
          ret.add(((VariableArgument) def.getArg2()).getLoc());
        }
        break;
      default:
        ErrorReporting.reportErrorCompat(new Exception("Tried to get " +
        "variables used in a non definition!"));
        return null;
    }

    return ret;
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
    return new BitSet(variables.size());
  }

  @Override
  public BitSet top() {
    BitSet s = new BitSet(variables.size());
    s.flip(0, variables.size());
    return s;
  }

  @Override
  public BitSet leastUpperBound(BitSet v1, BitSet v2) {
    BitSet ret = (BitSet) v1.clone();
    ret.or(v2);
    return ret;
  }

}
