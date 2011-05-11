package edu.mit.compilers.le02.cfg;

import java.util.HashMap;
import java.util.Map;

import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.ast.ASTNode;

public final class ArgReassignStatement extends BasicStatement {
  private HashMap<Register, Register> regMap =
    new HashMap<Register, Register>();

  public ArgReassignStatement(ASTNode node) {
    super(node, null);
    this.type = BasicStatementType.NOP;
  }

  public void putRegPair(Register from, Register to) {
    regMap.put(from, to);
  }

  public Map<Register, Register> getRegMap() {
    return regMap;
  }

  @Override
  public String toString() {
    return "ArgReassignStatement";
  }
}
