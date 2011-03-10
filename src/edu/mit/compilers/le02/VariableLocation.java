package edu.mit.compilers.le02;


public class VariableLocation {
	private String register;
	private int offset;
	private LocationType type;
	
	 public enum LocationType {
		    UNDEFINED(-1), REGISTER(0), STACK(1);

		    private int numericCode;

		    private LocationType(int code) {
		      numericCode = code;
		    }

		    public int numericCode() {
		      return numericCode;
		    }
		  };
		  
	public VariableLocation(){
		this.type = LocationType.UNDEFINED;
	}
	
	public void setRegisterLocation(String register){
		this.type = LocationType.REGISTER;
		this.register = register;
		this.offset = 0;
	}
	
	public void setStackLocation(int offset){
		this.type = LocationType.STACK;
		this.register = null;
		this.offset = offset;
	}
	
	public LocationType getLocationType(){
		return this.type;
	}
	
	public String getRegister(){
		return this.register;
	}
	
	public int getOffset(){
		return this.offset;
	}
}
