package edu.mit.compilers.le02.asm;

/**
 * A string object which can be saved into an AsmFile
 * 
 * @author mfrend@mit.edu (Maria Frendberg)
 * 
 */

public class AsmString implements AsmObject {
	private String line;

	public AsmString(String line) {
		this.line = line;
	}

	public String toString() {
		return this.line;
	}

}
