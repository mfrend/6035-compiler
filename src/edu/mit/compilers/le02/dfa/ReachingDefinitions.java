package edu.mit.compilers.le02.dfa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
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
  private List<BasicStatement> definitions;
  
	
	public static class BlockItem extends GenKillItem {
	  private BasicBlockNode node;
    private List<BasicStatement> blockDefinitions;
    private Map<VariableLocation, Set<BasicStatement>> reachingDefinitions;
    private Set<BlockItem> predecessorSet;
    private Set<BlockItem> successorSet;
	  
	  public BlockItem(BasicBlockNode node, List<BasicStatement> blockDefs) {
	    this.node = node;
	    this.blockDefinitions = blockDefs;
	  }

    public Set<BasicStatement> getReachingDefinitions(VariableLocation loc) {
      return reachingDefinitions.get(loc);
    }
	  
	  public List<BasicStatement> getInDefinitions() {
	    BitSet in = this.getIn();
	    return null;
	  }
	  
    public List<BasicStatement> getOutDefinitions() {
      BitSet in = this.getIn();
      return null;
    }

    @Override
    protected BitSet gen() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    protected BitSet kill() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Collection<WorklistItem<BitSet>> predecessors() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Collection<WorklistItem<BitSet>> successors() {
      // TODO Auto-generated method stub
      return null;
    }

    public void setPredecessors(Set<BlockItem> predecessorSet) {
      this.predecessorSet = predecessorSet;
    }

    public void setSuccessors(Set<BlockItem> successorSet) {
      this.successorSet = successorSet;
    }
	  
	}
	
	
	public ReachingDefinitions(BasicBlockNode methodRoot) {
	  this.definitions = new ArrayList<BasicStatement>();
	  this.definitionIndices = new HashMap<BasicStatement, Integer>();
    this.blockDefinitions = new HashMap<BasicBlockNode, BlockItem>(); 
	  this.visit(methodRoot);
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
	      blockDefs.add(s);
	      definitions.add(s);
	      definitionIndices.put(s, definitions.size() - 1);
	    }
	  }
	  
	  return new BlockItem(node, blockDefs);
	  
	}
	
	private boolean isDefinition(BasicStatement s) {
	  return false;
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
