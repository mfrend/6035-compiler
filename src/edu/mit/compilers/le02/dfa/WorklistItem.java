package edu.mit.compilers.le02.dfa;

import java.util.Collection;

/**
 * 
 * @author David Koh <dkoh@mit.edu
 *
 * @param <T> The type of information returned by this worklist item's algorithm
 */
public abstract class WorklistItem<T> {
  private T in;
  private T out;
	
	abstract public T transferFunction(T in);
	
	abstract public Collection<WorklistItem<T>> predecessors();
	
	abstract public Collection<WorklistItem<T>> successors();

  public T getIn() {
    return in;
  }

  public void setIn(T in) {
    this.in = in;
  }

  public T getOut() {
    return out;
  }

  public void setOut(T out) {
    this.out = out;
  }
	

}
