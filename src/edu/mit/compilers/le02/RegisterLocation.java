package edu.mit.compilers.le02;

import edu.mit.compilers.le02.asm.AsmArg;


public class RegisterLocation extends VariableLocation{
  private Register reg;

  public enum Register implements AsmArg {
    RAX,
    EAX,
    RBX,
    EBX,
    RCX,
    ECX,
    RDX,
    EDX,
    RDI,
    EDI,
    RSI,
    ESI,
    RBP,
    RSP,
    R8,
    R8D,
    R9,
    R9D,
    R10,
    R10D,
    R11,
    R11D,
    R12,
    R12D,
    R13,
    R13D,
    R14,
    R14D,
    R15,
    R15D,
    RIP;

    @Override
    public String toString() {
      return "%" + this.name().toLowerCase();
    }
    public Register thirtyTwo() {
      switch (this) {
       case RAX:
       case EAX:
        return EAX;
       case RBX:
       case EBX:
        return EBX;
       case RCX:
       case ECX:
        return ECX;
       case RDX:
       case EDX:
        return EDX;
       case RDI:
       case EDI:
        return EDI;
       case RSI:
       case ESI:
        return ESI;
       case RBP:
        return RBP;
       case RSP:
        return RSP;
       case R8:
       case R8D:
        return R8D;
       case R9:
       case R9D:
        return R9D;
       case R10:
       case R10D:
        return R10D;
       case R11:
       case R11D:
        return R11D;
       case R12:
       case R12D:
        return R12D;
       case R13:
       case R13D:
        return R13D;
       case R14:
       case R14D:
        return R14D;
       case R15:
       case R15D:
        return R15D;
       case RIP:
        return RIP;
       default:
        return null;
      }
    }
    public Register sixtyFour() {
      switch (this) {
       case RAX:
       case EAX:
        return RAX;
       case RBX:
       case EBX:
        return RBX;
       case RCX:
       case ECX:
        return RCX;
       case RDX:
       case EDX:
        return RDX;
       case RDI:
       case EDI:
        return RDI;
       case RSI:
       case ESI:
        return RSI;
       case RBP:
        return RBP;
       case RSP:
        return RSP;
       case R8:
       case R8D:
        return R8;
       case R9:
       case R9D:
        return R9;
       case R10:
       case R10D:
        return R10;
       case R11:
       case R11D:
        return R11;
       case R12:
       case R12D:
        return R12;
       case R13:
       case R13D:
        return R13;
       case R14:
       case R14D:
        return R14;
       case R15:
       case R15D:
        return R15;
       case RIP:
        return RIP;
       default:
        return null;
      }
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
  public int hashCode() {
    return this.reg.ordinal();
  }

  @Override
  public String toString() {
    return this.reg.toString();
  }


}
