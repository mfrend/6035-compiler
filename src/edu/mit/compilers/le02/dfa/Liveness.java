package edu.mit.compilers.le02.dfa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.opt.BasicBlockVisitor;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

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
  private Map<VariableLocation, Integer> variableIndices;
  private List<VariableLocation> globals;

  public class BlockItem extends GenKillItem {
    private Liveness parent;
    private BasicBlockNode node;
    private List<BasicStatement> statements;
    private BitSet genSet;
    private BitSet killSet;

    public BlockItem(Liveness parent, 
        BasicBlockNode node, List<BasicStatement> statements) {
      this.parent = parent;
      this.node = node;
      this.statements = statements;

      this.genSet = new BitSet();
      this.killSet = new BitSet();
      this.init();
    }

    private void init() {
      Integer index;

      for (BasicStatement s : statements) {
        index = parent.definitionIndices.get(s);
        if ((index != null) && (!this.killSet.get(index))) {
          this.genSet.set(index);
        }

        for (Integer i : parent.useIndices.get(s)) {
          this.killSet.set(i);
        }
      }
    }

    public Set<BasicStatement> getEliminationSet() {
      BitSet liveness = (BitSet) this.getOut().clone();
      Set<BasicStatement> ret = new HashSet<BasicStatement>();
      BasicStatement s;
      int def;

      for (int i = statements.size() - 1; i >= 0; i--) {
        s = statements.get(i);
        def = parent.definitionIndices.get(s);

        if ((parent.isEliminable(s)) && (liveness.get(def))) {
          // This variable is currently live, so this definition cannot
          // be eliminated. Set and clear liveness bits for used and defd vars
          liveness.clear(def);
          for (Integer use : parent.useIndices.get(s)) {
            liveness.set(use);
          }
        } else {
          // This variable is not live so this definition can be eliminated
          ret.add(s);
        }
      }

      return ret;
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

  public Liveness(BasicBlockNode methodStart, BasicBlockNode methodEnd) {
    this.blockItems = new HashMap<BasicBlockNode, BlockItem>();
    this.definitionIndices = new HashMap<BasicStatement, Integer>();
    this.useIndices = new HashMap<BasicStatement, List<Integer>>();
    this.variableIndices = new HashMap<VariableLocation, Integer>();
    this.globals = new ArrayList<VariableLocation>();

    BitSet globalSet = new BitSet();
    if (methodEnd.getLastStatement() != null) {
      SymbolTable st = methodEnd.getLastStatement().getNode().getSymbolTable();
      for (FieldDescriptor desc : st.getFields()) {
        globals.add(desc.getLocation());
        globalSet.set(getVarIndex(desc.getLocation()));
      }
    }

    // TODO Probably not in this visitor, but write a way to find the
    // 'last' basic block in a method. If there is no unique last block,
    // create one
    this.visit(methodStart);
    BlockItem end = blockItems.get(methodEnd);

    // Run a fixed point algorithm on the basic blocks to calculate the
    // list of live variables for each block
    WorklistAlgorithm.runBackwards(blockItems.values(), this, end, globalSet);
  }

  @Override
  protected void processNode(BasicBlockNode node) {
    this.blockItems.put(node, calcDefinitions(node));
  }

  public BlockItem getDefinitions(BasicBlockNode node) {
    return blockItems.get(node);
  }

  private BlockItem calcDefinitions(BasicBlockNode node) {
    List<BasicStatement> statements = new ArrayList<BasicStatement>();

    for (BasicStatement s : node.getStatements()) {
      if ((isDefinitionOp(s)) || (s instanceof CallStatement)) {
        statements.add(s);

        VariableLocation target = getDefinitionTarget(s);
        definitionIndices.put(s, getVarIndex(target));

        List<Integer> uses = new ArrayList<Integer>();
        for (VariableLocation use : getDefinitionUses(s)) {
          uses.add(getVarIndex(use));
        }
        useIndices.put(s, uses);
      }
    }

    return new BlockItem(this, node, statements);
  }

  private int getVarIndex(VariableLocation loc) {
    Integer index = variableIndices.get(loc);
    if (index == null) {
      index = new Integer(variableIndices.size());
      variableIndices.put(loc, index);
    }

    return index;
  }

  private boolean isDefinitionOp(BasicStatement s) {
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
      case RETURN:
        return true;
      default:
        return false;
    }
  }

  private VariableLocation getDefinitionTarget(BasicStatement s) {
    if (s instanceof OpStatement) {
      return getDefinitionTarget((OpStatement) s);
    } else if (s instanceof CallStatement) {
      return getDefinitionTarget((CallStatement) s);
    }
    ErrorReporting.reportErrorCompat(new Exception("Tried to get target " +
      "of a non-definition and a non-call"));
    return null;
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
      case RETURN:
        if ((def.getArg1() != null) &&
            (def.getArg1() instanceof VariableArgument)) {
          return ((VariableArgument) def.getArg1()).getDesc().getLocation();
        }
        return null;
      default:
        ErrorReporting.reportErrorCompat(new Exception("Tried to get target " +
        "of a non-definition"));
        return null;
    }
  }

  private VariableLocation getDefinitionTarget(CallStatement call) {
    return call.getResult().getLocation();
  }

  private List<VariableLocation> getDefinitionUses(BasicStatement s) {
    if (s instanceof OpStatement) {
      return getDefinitionUses((OpStatement) s);
    } else if (s instanceof CallStatement) {
      return getDefinitionUses((CallStatement) s);
    }
    ErrorReporting.reportErrorCompat(new Exception("Tried to get uses " +
      "of a non-definition and a non-call"));
    return null;
  }

  private List<VariableLocation> getDefinitionUses(OpStatement def) {
    List<VariableLocation> ret = new ArrayList<VariableLocation>();

    switch (def.getOp()) {
      case MOVE:
      case UNARY_MINUS:
      case NOT:
        if (def.getArg1() instanceof VariableArgument) {
          ret.add(((VariableArgument) def.getArg1()).getDesc().getLocation());
        }
        break;
      case ADD:
      case SUBTRACT:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
        if (def.getArg1() instanceof VariableArgument) {
          ret.add(((VariableArgument) def.getArg1()).getDesc().getLocation());
        }
        if (def.getArg2() instanceof VariableArgument) {
          ret.add(((VariableArgument) def.getArg2()).getDesc().getLocation());
        }
        break;
      default:
        ErrorReporting.reportErrorCompat(new Exception("Tried to get " +
        "variables used in a non definition!"));
        return null;
    }

    return ret;
  }

  private List<VariableLocation> getDefinitionUses(CallStatement call) {
    List<VariableLocation> ret = new ArrayList<VariableLocation>(globals);

    for (Argument arg : call.getArgs()) {
      if ((arg instanceof VariableArgument) && 
          !(arg.getDesc() instanceof FieldDescriptor)) {
        ret.add(((VariableArgument) arg).getDesc().getLocation());
      }
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
    return new BitSet(variableIndices.size());
  }

  @Override
  public BitSet top() {
    BitSet s = new BitSet(variableIndices.size());
    s.flip(0, variableIndices.size());
    return s;
  }

  @Override
  public BitSet leastUpperBound(BitSet v1, BitSet v2) {
    BitSet ret = (BitSet) v1.clone();
    ret.or(v2);
    return ret;
  }

  public boolean isEliminable(BasicStatement s) {
    if (isDefinitionOp(s)) {
      return ((OpStatement) s).getOp() != AsmOp.RETURN;
    }
    return false;
  }

  public Map<BasicBlockNode, BlockItem> getBlockItems() {
    return blockItems;
  }
}
