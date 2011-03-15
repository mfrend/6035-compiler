package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BasicBlockNode implements CFGNode {
  private static Set<CFGNode> visited = new HashSet<CFGNode>();
  private Set<BasicBlockNode> predecessors;
  private String method;
  private String id;
  private List<BasicStatement> statements;
  private BasicBlockNode next;
  private BasicBlockNode branchTarget;

  public BasicBlockNode(String id, String method) {
    this.id = id;
    this.method = method;
    this.next = null;
    this.branchTarget = null;
    
    this.predecessors = new HashSet<BasicBlockNode>();
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
  
  public void removeFromCFG() {
    assert (statements.isEmpty());
    assert (branchTarget == null);
    for (BasicBlockNode n : predecessors) {
      if (this == n.next) {
        n.next = this.next;
      } else if (this == n.branchTarget) {
        n.branchTarget = this.next;
      } else {
        assert(false);
      }
    }
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
    if (branchTarget != null) {
      branchTarget.predecessors.remove(this);
    }
    
    branchTarget = node;
    branchTarget.predecessors.add(this);
  }

  public String getTrueBranch() {
    return branchTarget.id;
  }

  public void setNext(BasicBlockNode node) {
    if (next != null) {
      next.predecessors.remove(this);
    }
    
    next = node;
    next.predecessors.add(this);
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
  
  @Override
  public void prepDotString() {
    BasicBlockNode.resetVisited();
  }
  
  @Override
  public String getDotString() {
    if (visited.contains(this)) {
      return "";
    }
    
    if (next == null) {
      return id + " " + getDotStringLabel() + "\n";
    }
    
    visited.add(this);
    
    String me = id; 
    String nextStr = next.id;
    
    String s = me + " " + getDotStringLabel() + "\n"
               + me + " -> " + nextStr;
    
    if (!isBranch()) {
      s += "\n";
      return s + next.getDotString();
    }
    else {
      s += " [label=\"false\"]\n";
      String branchStr = branchTarget.id;
      s += me + " -> " + branchStr + " [label=\"true\"]\n";
      return s + next.getDotString() + branchTarget.getDotString();
    }
  }
  
  public static void resetVisited() {
    visited.clear();
  }
  
  private String getDotStringLabel() {
    String s = "[shape=none, margin=0, label=<<TABLE BORDER=\"0\" "
               + "CELLBORDER=\"1\" CELLSPACING=\"0\" CELLPADDING=\"4\">";
    s += "<TR><TD><B>" + id + "</B></TD></TR>";
    for (BasicStatement st : statements) {
      s += "<TR><TD>" + st + "</TD></TR>";
    }
    s += "</TABLE>>]";
    return s;
  }
  
  public String getMethod() {
    return method;
  }

}
