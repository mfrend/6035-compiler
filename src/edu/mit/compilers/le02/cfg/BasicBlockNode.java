package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

public final class BasicBlockNode {
  private String id;
  private List<BasicStatement> statements;
  private BasicStatement conditional;
  private String trueBranch;
  private String falseBranch;
  
  public BasicBlockNode(String id, BasicStatement cond, String trueBranch,
                        String falseBranch) {
    this.id = id;
    this.conditional = cond;
    this.trueBranch = trueBranch;
    this.falseBranch = falseBranch;
    
    this.statements = new ArrayList<BasicStatement>();
  }

  public String getId() {
    return id;
  }

  public void setStatements(List<BasicStatement> statements) {
    this.statements = statements;
  }

  public List<BasicStatement> getStatements() {
    ArrayList<BasicStatement> list = new ArrayList<BasicStatement>(statements);
    return list;
  }
  
  public void setConditional(BasicStatement conditional) {
    this.conditional = conditional;
  }

  public BasicStatement getConditional() {
    return conditional;
  }

  public String getTrueBranch() {
    return trueBranch;
  }

  public String getFalseBranch() {
    return falseBranch;
  }

}
