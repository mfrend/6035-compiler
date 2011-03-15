package edu.mit.compilers.le02.cfg;

import java.util.HashSet;
import java.util.Set;

import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;

public final class SimpleCFGNode implements CFGNode {
  private static Set<CFGNode> visited = new HashSet<CFGNode>();
  private Set<CFGNode> predecessors;
  private SimpleCFGNode next;
  private SimpleCFGNode branchTarget;
  private BasicStatement statement;
  private Argument result;
  
  public SimpleCFGNode(BasicStatement statement) {
    this.statement = statement;
    this.predecessors = new HashSet<CFGNode>();
    
    if (this.statement.type == BasicStatementType.ARGUMENT) {
      this.result = ((ArgumentStatement) statement).getArgument();
    }
    else {
      this.result = Argument.makeArgument(statement.getResult());
    }
  }

  @Override
  public SimpleCFGNode getBranchTarget() {
    return branchTarget;
  }
  
  public void setBranchTarget(SimpleCFGNode node) {
    if (branchTarget != null) {
      branchTarget.predecessors.remove(this);
    }
    branchTarget = node;
    if (branchTarget != null) {
      branchTarget.predecessors.add(this);
    }
  }

  @Override
  public BasicStatement getConditional() {
    if (!isBranch()) {
      return null;
    }
    
    return statement;
  }

  @Override
  public SimpleCFGNode getNext() {
    return next;
  }
  
  public void setNext(SimpleCFGNode node) {
    if (next != null) {
      if (statement.type == BasicStatementType.JUMP) {
        return;
      }
      next.predecessors.remove(this);
    }
    next = node;
    if (next != null) {
      next.predecessors.add(this);
    }
  }

  @Override
  public boolean isBranch() {
    return branchTarget != null;
  }

  public BasicStatement getStatement() {
    return statement;
  }
  
  public Argument getResult() {
    return result;
  }
  
  public boolean hasMultipleEntrances() {
    return predecessors.size() > 1;
  }
  

  public static void resetVisited() { 
    visited.clear();
  }
  
  @Override
  public void prepDotString() { 
    visited.clear();
  }

  @Override
  public String getDotString() {
    if (visited.contains(this)) {
      return "";
    }
    
    if (next == null) {
      return this.hashCode() + "[label=\"" + statement.toString() + "\"]\n";
    }
    
    visited.add(this);
    
    String me = "" + this.hashCode();
    String nextStr = "" + next.hashCode();
    
    String s = me + " [label=\"" + statement.toString() + "\"]\n"
               + me + " -> " + nextStr;
    
    if (!isBranch()) {
      s += "\n";
      return s + next.getDotString();
    }
    else {
      s += " [label=\"false\"]\n";
      String branchStr = "" + branchTarget.hashCode();
      s += me + " -> " + branchStr + " [label=\"true\"]\n";
      return s + next.getDotString() + branchTarget.getDotString();
    }
  }

}
