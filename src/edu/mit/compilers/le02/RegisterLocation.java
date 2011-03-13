package edu.mit.compilers.le02;

import edu.mit.compilers.le02.VariableLocation;

public class RegisterLocation extends VariableLocation{
	private String global;
	
	public RegisterLocation(String global){
		this.global = global;
	}
	
	public String getGlobal(){
		return this.global;
	}
	
}
