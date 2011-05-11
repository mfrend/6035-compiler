package edu.mit.compilers.le02.symboltable;

import static edu.mit.compilers.le02.RegisterLocation.Register.R8;
import static edu.mit.compilers.le02.RegisterLocation.Register.R9;
import static edu.mit.compilers.le02.RegisterLocation.Register.RCX;
import static edu.mit.compilers.le02.RegisterLocation.Register.RDI;
import static edu.mit.compilers.le02.RegisterLocation.Register.RDX;
import static edu.mit.compilers.le02.RegisterLocation.Register.RSI;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.RegisterLocation;
import edu.mit.compilers.le02.StackLocation;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.VariableLocation;

public class ParamDescriptor extends TypedDescriptor {
  private int index = -1;

  public static final Register[] arguments = {RDI, RSI, RDX, RCX, R8, R9};

  public ParamDescriptor(SymbolTable parent, String id, DecafType type) {
    super(parent, id, type);
  }

  public void setIndex(int i) {
    this.index = i;
    if (i < 6) {
      this.location = new RegisterLocation(arguments[i]);
    } else {
      this.location = new StackLocation((i - 6) * 8 + 16);
    }
  }

  public void setLocation(VariableLocation loc) {
    this.location = loc;
  }

  public Register getIndexRegister() {
    if (index >= 6 || index == -1) {
      return null;
    }
    else {
      return arguments[index];
    }
  }

}
