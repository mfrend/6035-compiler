package edu.mit.compilers.le02.dfa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.opt.BasicBlockVisitor;

/**
 *
 * @author David Koh (dkoh@mit.edu)
 *
 */
public class ReachingDefinitions extends BasicBlockVisitor
                                 implements Lattice<BitSet, BasicBlockNode> {
  private Map<BasicBlockNode, BlockItem> blockDefinitions;
  private Map<BasicStatement, Integer> definitionIndices;
  private Map<VariableLocation, BitSet> varDefinitions;
  private BitSet globalDefinitions;
  private List<BasicStatement> definitions;


  public class BlockItem extends GenKillItem {
    private ReachingDefinitions parent;
    private BasicBlockNode node;
    private List<BasicStatement> blockDefinitions;
    private BitSet genSet;
    private BitSet killSet;

    public BlockItem(ReachingDefinitions parent,
        BasicBlockNode node, List<BasicStatement> blockDefs) {
      this.parent = parent;
      this.node = node;
      this.blockDefinitions = blockDefs;

      this.genSet = new BitSet();
      this.killSet = new BitSet();
    }

    public void init() {
      int index;
      for (BasicStatement s : blockDefinitions) {
        if (s.getType() == BasicStatementType.CALL) {
          this.genSet.andNot(parent.globalDefinitions);
          this.killSet.or(parent.globalDefinitions);
          continue;
        }

        OpStatement def = (OpStatement) s;
        index = parent.definitionIndices.get(s);
        this.genSet.set(index);

        this.killSet.or(parent.varDefinitions.get(
            parent.getDefinitionTarget(def)));
      }
    }

    public Collection<BasicStatement>
    getReachingDefinitions(VariableLocation loc) {
      BitSet ret = (BitSet) this.getIn().clone();
      ret.and(parent.varDefinitions.get(loc));
      return getBitsetDefinitions(ret);
    }

    private List<BasicStatement> getBitsetDefinitions(BitSet bs) {
      if (parent.definitions.isEmpty()) {
        return Collections.emptyList();
      }

      List<BasicStatement> defs = new ArrayList<BasicStatement>();

      int len = parent.definitions.size();
      for (int i = 0 ; i < len; i++) {
        BasicStatement s = parent.definitions.get(i);

        if (bs.get(i)) {
          defs.add(s);
        }
      }

      return defs;
    }

    public List<BasicStatement> getInDefinitions() {
      return getBitsetDefinitions(this.getIn());
    }

    public List<BasicStatement> getOutDefinitions() {
      return getBitsetDefinitions(this.getOut());
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
        WorklistItem<BitSet> item = parent.blockDefinitions.get(pred);
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
        item = parent.blockDefinitions.get(node.getNext());
        ret.add(item);
      }


      if (node.getBranchTarget() != null) {
        item = parent.blockDefinitions.get(node.getBranchTarget());
        ret.add(item);
      }

      return ret;
    }
  }


  public ReachingDefinitions(BasicBlockNode methodRoot) {
    this.definitions = new ArrayList<BasicStatement>();
    this.definitionIndices = new HashMap<BasicStatement, Integer>();
    this.blockDefinitions = new HashMap<BasicBlockNode, BlockItem>();
    this.varDefinitions = new HashMap<VariableLocation, BitSet>();
    this.globalDefinitions = new BitSet();
    this.visit(methodRoot);

    for (BlockItem bi : blockDefinitions.values()) {
      bi.init();
    }

    BlockItem start = blockDefinitions.get(methodRoot);
    BitSet init = bottom();

    // Run a fixed point algorithm on the definitions to calculate the
    // reaching definitions.
    WorklistAlgorithm.runForward(blockDefinitions.values(), this, start, init);
  }

  @Override
  protected void processNode(BasicBlockNode node) {
    this.blockDefinitions.put(node, calcDefinitions(node));
  }

  public BlockItem getDefinitions(BasicBlockNode node) {
    return blockDefinitions.get(node);
  }

  private BlockItem calcDefinitions(BasicBlockNode node) {
    List<BasicStatement> blockDefs = new ArrayList<BasicStatement>();

    for (BasicStatement s : node.getStatements()) {
      if (isDefinition(s)) {
        OpStatement def = (OpStatement) s;
        VariableLocation target = getDefinitionTarget(def);

        blockDefs.add(s);
        definitions.add(s);
        int index = definitions.size() - 1;
        definitionIndices.put(s, index);

        BitSet bs = varDefinitions.get(target);
        if (bs == null) {
          bs = new BitSet();
          varDefinitions.put(target, bs);
        }
        bs.set(index);

        if (target.getLocationType() == LocationType.GLOBAL) {
          globalDefinitions.set(index);
        }

      }
      else if (s.getType() == BasicStatementType.CALL) {
        blockDefs.add(s);
      }
    }

    return new BlockItem(this, node, blockDefs);

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
        return ((VariableArgument) def.getArg2()).getDesc().getLocation();
      case ADD:
      case SUBTRACT:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
      case UNARY_MINUS:
      case NOT:
        return def.getResult().getLocation();
      default:
        ErrorReporting.reportErrorCompat(new Exception("Tried to get target " +
        "of a non definition!"));
        return null;
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
    return new BitSet(definitions.size());
  }

  @Override
  public BitSet top() {
    BitSet s = new BitSet(definitions.size());
    s.flip(0, definitions.size());
    return s;
  }

  @Override
  public BitSet leastUpperBound(BitSet v1, BitSet v2) {
    BitSet ret = (BitSet) v1.clone();
    ret.or(v2);
    return ret;
  }

}
