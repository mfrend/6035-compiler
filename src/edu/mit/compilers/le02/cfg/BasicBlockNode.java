package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

public final class BasicBlockNode implements CFGNode {
  private String id;
  private List<BasicStatement> statements;
  private BasicStatement conditional;
  private BasicBlockNode next;
  private BasicBlockNode branchTarget;

  public BasicBlockNode(String id) {
    this.id = id;
    this.next = null;
    this.branchTarget = null;
    
    this.statements = new ArrayList<BasicStatement>();
  }

  public String getId() {
    return id;
  }

  public void setStatements(List<BasicStatement> statements) {
    this.statements = statements;
  }

  public void prependStatement(BasicStatement statement) {
    this.statements.add(0, statement);
  }
  
  public void addStatement(BasicStatement statement) {
    this.statements.add(statement);
  }

  public List<BasicStatement> getStatements() {
    ArrayList<BasicStatement> list = new ArrayList<BasicStatement>(statements);
    return list;
  }
  
  public BasicStatement getLastStatement() {
    if (statements.isEmpty()) {
      return null;
    }
    
    return statements.get(statements.size() - 1);
  }

  /**
   * @return Most negative local offset in this basic block.
   */
  public int largestLocalOffset() {
    int min = 0;
    int curr;
    for (BasicStatement s : statements) { 
      curr = s.getNode().getSymbolTable().getLargestLocalOffset();
      if (curr < min) {
        min = curr;
      }
    }
    
    return min;
  }
  
  public BasicStatement getConditional() {
    if (!isBranch()) {
      return null;
    }
    
    return getLastStatement();
  }

  public void setBranchTarget(BasicBlockNode node) {
    this.branchTarget = node;
  }

  public String getTrueBranch() {
    return branchTarget.id;
  }

  public void setNext(BasicBlockNode node) {
    this.next = node;
  }

  public String getFalseBranch() {
    return next.id;
  }

  @Override
  public BasicBlockNode getBranchTarget() {
    return branchTarget;
  }

  @Override
  public BasicBlockNode getNext() {
    return next;
  }

  @Override
  public boolean isBranch() {
    return this.branchTarget != null;
  }

}
