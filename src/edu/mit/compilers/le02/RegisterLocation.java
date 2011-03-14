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
    RFLAGS,
    RIP
  }

  public RegisterLocation(Register reg) {
    this.reg = reg;
  }

  public Register getRegister(){
    return this.reg;
  }

}
