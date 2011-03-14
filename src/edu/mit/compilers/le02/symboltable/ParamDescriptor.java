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

public class ParamDescriptor extends TypedDescriptor {
  
  private static Register[] arguments = {RDI, RSI, RDX, RCX, R8, R9};

  public ParamDescriptor(SymbolTable parent, String id, DecafType type) {
    super(parent, id, type);
    
    int num = parent.getNumParams();
    if (num < 6) {
      this.location = new RegisterLocation(arguments[num]);
    }
    else {
      this.location = new StackLocation((num - 6) * 8 + 16);
    }
  }
  
}
