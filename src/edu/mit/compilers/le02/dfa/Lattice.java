package edu.mit.compilers.le02.dfa;

import java.util.Collection;
import java.util.HashMap;

/**
 * 
 * @author Maria Frendberg (mfrend@mit.edu)
 *
 */
public abstract class Lattice<T> {
	

	/**
	 * Returns the abstraction of any value
	 * @param value Any object
	 * @return A Type, defined in the subclass
	 */
	abstract T abstractionFunction(Object value);
	
	/**
	 * Returns the output of a transfer function on a set of input types
	 * @param values Input to a node, an array of Types
	 * @return A Type, defined in the subclass
	 */
	abstract T transferFunction(T[] values);

	/**
	 * @return The bottom value of the lattice.
	 */
	abstract T bottom();
	
	/**
	 * @return The top value of the lattice.
	 */
	abstract T top();
	
	/**
	 * Returns the least upper bound of an array of values
	 */
	T leastUpperBound(Collection<T> values) {
		T sup = this.bottom();
		for (T item : values) {
			sup = leastUpperBound(item, sup);
		}
		
		return sup;
	}
	

	/**
	 * Returns the least upper bound for 
	 */
	abstract T leastUpperBound(T v1, T v2);
	
}
