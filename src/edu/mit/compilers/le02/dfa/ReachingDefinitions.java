package edu.mit.compilers.le02.dfa;

import java.util.BitSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;


public class ReachingDefinitions extends Lattice<BitSet> {
	private Map<BasicBlockNode, Set<BasicStatement>> blockDefinitions;
	private Map<BasicStatement, Integer> definitionIndices;
	private int numDefs;
	
	public class Item extends GenKillItem {
	  private BasicBlockNode node;
	  
	  public Item(BasicBlockNode node) {
	    this.node = node;
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
	  
	}
	
	public ReachingDefinitions(ControlFlowGraph cfg) {
	}
	
	private Set<BasicStatement> getDefinitions(BasicBlockNode node) {
	  // TODO
	  return null;
	}

  @Override
  BitSet abstractionFunction(Object value) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  BitSet transferFunction(BitSet[] values) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  BitSet bottom() {
    return new BitSet(numDefs);
  }

  @Override
  BitSet top() {
    BitSet s = new BitSet(numDefs);
    s.flip(0, numDefs);
    return s;
  }

  @Override
  BitSet leastUpperBound(BitSet v1, BitSet v2) {
    BitSet ret = (BitSet) v1.clone();
    ret.or(v2);
    return ret;
  }
}
