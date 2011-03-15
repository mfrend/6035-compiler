package edu.mit.compilers.le02;




public class RegisterLocation extends VariableLocation{
  private Register reg;

  public enum Register {
    RAX,
    RBX,
    RCX,
    RDX,
    RDI,
    RSI,
    RBP,
    RSP,
    R8,
    R9,
    R10,
    R11,
    R12,
    R13,
    R14,
    R15,
    RIP;
    
    @Override
    public String toString() {
      return "%" + this.name().toLowerCase();
    }
  }

  public RegisterLocation(Register reg) {
    this.type = LocationType.REGISTER;
    this.reg = reg;
  }

  public Register getRegister(){
    return this.reg;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RegisterLocation)) return false;
    
    RegisterLocation other = (RegisterLocation) o;
    return this.reg == other.reg;
  }
  
  @Override
  public String toString() {
    return this.reg.toString();
  }


}
