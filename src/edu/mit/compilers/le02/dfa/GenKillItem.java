package edu.mit.compilers.le02.dfa;

import java.util.BitSet;


public abstract class GenKillItem extends WorklistItem<BitSet> {
  
  abstract protected BitSet gen();
  abstract protected BitSet kill();
  
  public BitSet transferFunction(BitSet in) {
    BitSet ret = (BitSet) in.clone();
    ret.andNot(kill());
    ret.or(gen());
    return ret;
  }

}
