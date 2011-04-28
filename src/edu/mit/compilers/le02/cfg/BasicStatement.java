package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public abstract class BasicStatement {
  private ASTNode node;
  protected TypedDescriptor result;
  protected BasicStatementType type;
  protected RegisterLiveness registerLiveness;

  public enum BasicStatementType {
    ARGUMENT,
    OP,
    CALL,
    NOP,
    JUMP
  }

  public BasicStatement(ASTNode node, TypedDescriptor result) {
    this.node = node;
    this.result = result;
    this.registerLiveness = new RegisterLiveness();
  }

  public ASTNode getNode() {
    return node;
  }

  public TypedDescriptor getResult() {
    return result;
  }

  public void setResult(TypedDescriptor desc) {
    result = desc;
  }

  public BasicStatementType getType() {
    return type;
  }
  
  public RegisterLiveness getRegisterLiveness() {
    return registerLiveness;
  }
  
  public List<Register> getLiveRegisters() {
    return new ArrayList<Register>(registerLiveness.getLiveRegisters());
  }
  
  public List<Register> getNonDyingRegisters() {
    return new ArrayList<Register>(registerLiveness.getNonDyingRegisters());
  }
  
  public List<Register> getNonDyingCallerSavedRegisters() {
    return new ArrayList<Register>(
        registerLiveness.getNonDyingCallerSavedRegisters());
  }
  
  public void setRegisterLiveness(Register r, boolean live) {
    registerLiveness.setRegisterLiveness(r, live);
  }
  
  public void setRegisterDying(Register r, boolean live) {
    registerLiveness.setRegisterDying(r, live);
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }
}
