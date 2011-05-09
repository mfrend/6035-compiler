package edu.mit.compilers.le02.dfa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.opt.BasicBlockVisitor;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

/**
 *
 * @author Shaunak Kishore
 *
 */
public class Liveness extends BasicBlockVisitor
implements Lattice<BitSet, BasicBlockNode> {
  private Map<BasicBlockNode, BlockItem> blockItems;
  private Map<BasicStatement, Boolean> eliminable;
  private Map<BasicStatement, Integer> definitionIndices;
  private Map<BasicStatement, List<Integer>> useIndices;
  private Map<TypedDescriptor, Integer> variableIndices;

  private List<TypedDescriptor> globals;
  private BitSet globalSet;

  public class BlockItem extends GenKillItem {
    private Liveness parent;
    private BasicBlockNode node;
    private List<BasicStatement> statements;
    private BitSet genSet;
    private BitSet killSet;
    private boolean returns;

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
      returns = false;

      for (BasicStatement s : statements) {
        for (Integer i : parent.useIndices.get(s)) {
          if (!this.killSet.get(i)) {
            this.genSet.set(i);
          }
        }

        index = parent.definitionIndices.get(s);
        if (index != null) {
          this.killSet.set(index);
        }

        if ((s instanceof OpStatement) &&
            (((OpStatement) s).getOp() == AsmOp.RETURN)) {
          returns = true;
          this.setOut(parent.getGlobalSet());
          break;
        }
      }
    }
    
    public boolean isLiveOnExit(TypedDescriptor desc) {
      BitSet liveness = (BitSet) this.getOut();
      Integer index = parent.variableIndices.get(desc);
      if (index == null) {
        return false;
      }
      return liveness.get(index);
    }
    
    public boolean isLiveOnEntrance(TypedDescriptor desc) {
      BitSet liveness = (BitSet) this.getIn();

      Integer index = parent.variableIndices.get(desc);
      if (index == null) {
        return false;
      }
      return liveness.get(index);
    }

    public Set<BasicStatement> getEliminationSet() {
      BitSet liveness = (BitSet) this.getOut().clone();
      Set<BasicStatement> ret = new HashSet<BasicStatement>();
      BasicStatement s;
      Integer def;

      for (int i = statements.size() - 1; i >= 0; i--) {
        s = statements.get(i);
        def = parent.definitionIndices.get(s);

        if (parent.isEliminable(s) && (def != null) && !liveness.get(def)) {
          // This variable is not live so this definition can be eliminated
          ret.add(s);
        } else {
          // This variable is currently live, so this definition cannot
          // be eliminated. Set and clear liveness bits for used and defd vars
          if (def != null) {
            liveness.clear(def);
          }
          for (Integer use : parent.useIndices.get(s)) {
            liveness.set(use);
          }
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
        if (item != null) {
          ret.add(item);
        }
      }
      return ret;
    }

    @Override
    public Collection<WorklistItem<BitSet>> successors() {
      ArrayList<WorklistItem<BitSet>> ret =
        new ArrayList<WorklistItem<BitSet>>();

      if (returns) {
        return ret;
      }

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

  public Liveness(BasicBlockNode methodStart) {
    this.blockItems = new HashMap<BasicBlockNode, BlockItem>();
    this.eliminable = new HashMap<BasicStatement, Boolean>();
    this.definitionIndices = new HashMap<BasicStatement, Integer>();
    this.useIndices = new HashMap<BasicStatement, List<Integer>>();
    this.variableIndices = new HashMap<TypedDescriptor, Integer>();

    this.globals = new ArrayList<TypedDescriptor>();
    this.globalSet = new BitSet();
    if (methodStart.getLastStatement() != null) {
      SymbolTable st =
        methodStart.getLastStatement().getNode().getSymbolTable();
      for (FieldDescriptor desc : st.getFields()) {
        globals.add(desc);
        Integer index = getVarIndex(desc);
        if (index != null) {
          globalSet.set(index);
        }
      }
    }

    this.visit(methodStart);

    // Run a fixed point algorithm on the basic blocks to calculate the
    // list of live variables for each block
    WorklistAlgorithm.runBackwards(blockItems.values(), this);
  }

  @Override
  protected void processNode(BasicBlockNode node) {
    this.blockItems.put(node, calcDefinitions(node));
  }

  private BlockItem calcDefinitions(BasicBlockNode node) {
    List<BasicStatement> statements = new ArrayList<BasicStatement>();

    for (BasicStatement s : node.getStatements()) {
      if ((isDefinitionOp(s)) || (s instanceof CallStatement)) {
        statements.add(s);

        if (isDefinitionOp(s) &&
            !(node.isBranch() && (s == node.getLastStatement()))) {
          eliminable.put(s, ((OpStatement) s).getOp() != AsmOp.RETURN);
        } else {
          eliminable.put(s, false);
        }

        TypedDescriptor target = getDefinitionTarget(s);
        definitionIndices.put(s, getVarIndex(target));

        List<Integer> uses = new ArrayList<Integer>();
        for (VariableArgument use : getDefinitionUses(s)) {
          Integer index = getVarIndex(use.getDesc());
          if (index != null) {
            uses.add(index);
          }
          addArrayIndex(uses, use);
        }

        // Handle two special cases: array indices are used in MOVE ops,
        // and all globals are used in CALL ops
        if ((s instanceof OpStatement) &&
            (((OpStatement) s).getOp() == AsmOp.MOVE)) {
          addArrayIndex(uses, (VariableArgument) ((OpStatement) s).getArg2());
        } else if ((s instanceof CallStatement) &&
            !((CallStatement) s).isCallout()) {
          for (TypedDescriptor desc : globals) {
            Integer index = getVarIndex(desc);
            if ((index != null) && !uses.contains(index)) {
              uses.add(index);
            }
          }
        }

        useIndices.put(s, uses);
      }
    }

    BlockItem ret = new BlockItem(this, node, statements);
    if (ret.successors().isEmpty()) {
      ret.setOut(globalSet);
    }
    return ret;
  }

  private void addArrayIndex(List<Integer> uses, VariableArgument arg) {
    if ((arg != null) && (arg instanceof ArrayVariableArgument)) {
      Integer index =
          getVarIndex(((ArrayVariableArgument) arg).getIndex().getDesc());
      if (index != null) {
        uses.add(index);
      }
    }
  }

  private Integer getVarIndex(TypedDescriptor loc) {
    if ((loc == null) || (loc.getType() == null) || loc.getType().isArray()) {
      return null;
    }

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

    return (((OpStatement)s).getOp() != AsmOp.ENTER);
  }

  private TypedDescriptor getDefinitionTarget(BasicStatement s) {
    if (s instanceof OpStatement) {
      return getDefinitionTarget((OpStatement) s);
    } else if (s instanceof CallStatement) {
      return getDefinitionTarget((CallStatement) s);
    }
    ErrorReporting.reportErrorCompat(new Exception("Tried to get target " +
      "of a non-definition and a non-call"));
    return null;
  }

  private TypedDescriptor getDefinitionTarget(OpStatement def) {
    switch (def.getOp()) {
      case MOVE:
        return (def.getArg2()).getDesc();
      case ENTER:
        ErrorReporting.reportErrorCompat(new Exception("Tried to get target " +
        "of a non-definition"));
        return null;
      case RETURN:
        return null;
      default:
        return def.getResult();
    }
  }

  private TypedDescriptor getDefinitionTarget(CallStatement call) {
    return call.getResult();
  }

  private List<VariableArgument> getDefinitionUses(BasicStatement s) {
    if (s instanceof OpStatement) {
      return getDefinitionUses((OpStatement) s);
    } else if (s instanceof CallStatement) {
      return getDefinitionUses((CallStatement) s);
    }
    ErrorReporting.reportErrorCompat(new Exception("Tried to get uses " +
      "of a non-definition and a non-call"));
    return null;
  }

  private List<VariableArgument> getDefinitionUses(OpStatement def) {
    List<VariableArgument> ret = new ArrayList<VariableArgument>();

    switch (def.getOp()) {
      case MOVE:
      case UNARY_MINUS:
      case NOT:
        if (def.getArg1() instanceof VariableArgument) {
          ret.add((VariableArgument) def.getArg1());
        }
        break;
      case ADD:
      case SUBTRACT:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
      case EQUAL:
      case NOT_EQUAL:
      case LESS_THAN:
      case LESS_OR_EQUAL:
      case GREATER_THAN:
      case GREATER_OR_EQUAL:
        if (def.getArg1() instanceof VariableArgument) {
          ret.add((VariableArgument) def.getArg1());
        }
        if (def.getArg2() instanceof VariableArgument) {
          ret.add((VariableArgument) def.getArg2());
        }
        break;
      case RETURN:
        if ((def.getArg1() != null) &&
            (def.getArg1() instanceof VariableArgument)) {
          ret.add((VariableArgument) def.getArg1());
        }
        break;
      default:
        ErrorReporting.reportErrorCompat(new Exception("Tried to get " +
        "variables used in a non definition!"));
        return null;
    }

    return ret;
  }

  private List<VariableArgument> getDefinitionUses(CallStatement call) {
    List<VariableArgument> ret = new ArrayList<VariableArgument>();

    for (Argument arg : call.getArgs()) {
      if (arg instanceof VariableArgument) {
        ret.add((VariableArgument) arg);
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
    return eliminable.get(s);
  }

  public BitSet getGlobalSet() {
    return globalSet;
  }

  public Map<BasicBlockNode, BlockItem> getBlockItems() {
    return blockItems;
  }
  

  public BlockItem getBlockItem(BasicBlockNode node) {
    return blockItems.get(node);
  }
}
