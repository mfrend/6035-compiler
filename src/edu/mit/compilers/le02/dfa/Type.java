package edu.mit.compilers.le02.dfa;

/**
 * 
 * @author Maria Frendberg(mfrend@mit.edu)
 *
 * @param <T>
 */
public interface Type<T> {
	
	/**
	 * Transfer Function for generic type T
	 * @param value The value to be applied
	 * @return instance of T
	 */
	public T transferFunction(T value);

}
