package edu.mit.compilers.le02.dfa;

import java.util.Collection;
import java.util.HashMap;

/**
 * The Lattice class represents a partially ordered set of items of type T,
 * abstracted from a concrete set of items of type U.
 * 
 * @author Maria Frendberg (mfrend@mit.edu)
 * @author David Koh (dkoh@mit.edu)
 */
public interface Lattice<T, U> {
	

	/**
	 * Returns the abstraction of values of type U
	 * @param value Any object
	 * @return A Type, defined in the subclass
	 */
	abstract public T abstractionFunction(U value);
	
	/**
	 * Returns the output of a transfer function on a set of input types
	 * @param values Input to a node, an array of Types
	 * @return A Type, defined in the subclass
	 */
	abstract public T transferFunction(T[] values);

	/**
	 * @return The bottom value of the lattice.
	 */
	abstract public T bottom();
	
	/**
	 * @return The top value of the lattice.
	 */
	abstract public T top();
	
	/**
	 * Returns the least upper bound for 
	 */
	abstract public T leastUpperBound(T v1, T v2);
	
}
