package edu.mit.compilers.le02.dfa;

/**
 * 
 * @author Maria Frendberg (mfrend@mit.edu)
 *
 */
public abstract class Lattice<T extends Enum<T>> {
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
	
}
