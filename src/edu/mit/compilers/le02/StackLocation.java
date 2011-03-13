package edu.mit.compilers.le02;

import edu.mit.compilers.le02.VariableLocation;

public class StackLocation extends VariableLocation{
	private boolean globalLocation;
	private int offset;
	
	public StackLocation(boolean globalLocation, int offset){
		this.globalLocation = globalLocation;
		this.offset = offset;
	}
	
	public boolean isGlobal(){
		return this.globalLocation;
	}
	
	public int getOffset(){
		if(this.globalLocation){
			return 0;  //Should check if global before requesting offset
		} else {
			return this.offset;
		}
	}
	
	public int getGlobalLocation(){
		if(this.globalLocation){
			return this.offset;
		} else {
			return /*rbp +*/ this.offset;
		}
	}
	
	

}
