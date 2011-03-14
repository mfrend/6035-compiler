package edu.mit.compilers.le02.symboltable;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.ast.BlockNode;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.List;
import java.util.SortedSet;

public class MethodDescriptor extends TypedDescriptor {
  private BlockNode code;
  private SymbolTable symbolTable;
  private List<String> params;
  private SortedSet<Register> usedRegisters = new TreeSet<Register>();
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

  public void markRegisterUsed(Register reg) {
    usedRegisters.put(reg);
  }

  public SortedSet<Register> getUsedRegisters() {
    return usedRegisters;
  }
}
