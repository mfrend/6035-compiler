package edu.mit.compilers.le02;


public class VariableLocation {
	//private String register;
	//private int offset;
	private int type;
	private StackLocation stackLocation;
	private RegisterLocation registerLocation;
	
	public static int UNDEFINED = 0;
	public static int STACK_LOCATION = 1;
	public static int REGISTER_LOCATION = 2;
		  
	public VariableLocation(){
		this.type = UNDEFINED;
	}
	
	public void setRegisterLocation(String global){
		this.type = REGISTER_LOCATION;
		this.registerLocation = new RegisterLocation(global);
	}
	
	public void setStackLocation(boolean globalLocation, int offset){
		this.type = STACK_LOCATION;
		this.stackLocation = new StackLocation(globalLocation,offset);
	}
	
	public int getLocationType(){
		return this.type;
	}
	
	public RegisterLocation getRegisterLocation(){
		if(this.type==REGISTER_LOCATION){
			return this.registerLocation;
		} else {
			return null;
		}
	}
	
	public StackLocation getStackLocation(){
		if(this.type==STACK_LOCATION){
			return this.stackLocation;
		} else {
			return null;
		}
	}
}
