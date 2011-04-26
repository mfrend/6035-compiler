package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.ast.BlockNode;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.VariableLocation;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.List;
import java.util.SortedSet;

public class MethodDescriptor extends TypedDescriptor {
  private BlockNode code;
  private SymbolTable symbolTable;
  private List<String> params;
  private SortedSet<Register> usedCalleeRegisters = new TreeSet<Register>();
  private SortedSet<Register> usedCallerRegisters = new TreeSet<Register>();
  private SourceLocation sl;

  public MethodDescriptor(SymbolTable parent, String id, DecafType type,
                          SymbolTable symbolTable, List<String> params,
                          BlockNode node, SourceLocation sl) {
    super(parent, id, type);

    this.code = node;
    this.symbolTable = symbolTable;
    this.params = params;
    this.sl = sl;
  }

  @Override
  public String toString() {
    return "[" + this.symbolTable.toString() +
      "],[" + this.code.getSymbolTable().toString() + "]";

  }

  public BlockNode getCode() {
    return code;
  }

  public List<ParamDescriptor> getParams() {
    List<ParamDescriptor> ret = new ArrayList<ParamDescriptor>();
    for (String param : params) {
      ret.add(getSymbolTable().getParam(param));
    }

    return ret;
  }

  public SymbolTable getSymbolTable() {
    return symbolTable;
  }

  public SourceLocation getSourceLocation() {
    return sl;
  }

  public static Register[] calleeSaved = {
    Register.R12,
    Register.R13,
    Register.R14,
    Register.R15,
    Register.RBX,
  };

  public void markRegisterUsed(Register reg) {
    for (Register callee : calleeSaved) {
      if (callee == reg) {
        usedCalleeRegisters.add(reg);
        return;
      }
    }
    usedCallerRegisters.add(reg);
  }

  public List<Register> getUsedCalleeRegisters() {
    return new ArrayList<Register>(usedCalleeRegisters);
  }
  public List<Register> getUsedCallerRegisters() {
    return new ArrayList<Register>(usedCallerRegisters);
  }
}
