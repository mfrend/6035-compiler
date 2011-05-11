package edu.mit.compilers.le02.cfg;

import java.util.Map;
import java.util.TreeMap;

import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.ast.ASTNode;

public final class ArgReassignStatement extends BasicStatement {
  private Map<Register, Register> regMap =
    new TreeMap<Register, Register>();

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
