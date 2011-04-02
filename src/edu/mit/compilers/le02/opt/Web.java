package edu.mit.compilers.le02.opt;

import java.util.LinkedList;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.cfg.BasicStatement;

/**
 * 
 * Webs are a representation of all the statements for which a given variable
 * must share the same register.  They are implemented as a tree which is part
 * of a disjoint-set forest of all webs, created using the union-find algorithm
 * 
 * Webs must satisfy two conditions, which are:
 * 1. A definition and all reachable uses must be in same web
 * 2. All definitions that reach same use must be in same web
 * (taken from 6.035 lecture notes)
 * 
 * Webs are originally created in the WebVisitor class, where each one
 * corresponds to a single definition of a variable, with all of its reached
 * definitions.  In the combination stage, any two definitions which both reach
 * the same use will be combined (via union), thus satisfying the two
 * conditions for what is contained in a web.
 * 
 * @author David Koh (dkoh@mit.edu)
 */
public class Web {
  private Web rep;
  private int _rank;
  private VariableLocation loc;
  private LinkedList<BasicStatement> stmts;
  
  
  public Web(VariableLocation loc, BasicStatement def) {
    this.rep = this;
    this._rank = 0;
    this.loc = loc;
    this.stmts = new LinkedList<BasicStatement>();
    
    this.stmts.add(def);
  }
  
  public Web find() {
    if (this.rep == this) {
      return this;
    }
    else {
      this.rep = this.rep.find();
      return this.rep;
    }
  }
  
  public void union(Web other) {
    if (this == other) {
      return;
    }
    
    assert this.loc == other.loc;
    Web thisRoot = this.find();
    Web otherRoot = other.find();
    if (this.rank() > other.rank()) {
      otherRoot.rep = thisRoot;
    }
    else if (other.rank() > this.rank()) {
      thisRoot.rep = otherRoot;
    }
    else {
      // other.rank() == this.rank()
      thisRoot.rep = otherRoot;
      thisRoot._rank++;
    }
  }
  
  public int rank() {
    return this.find()._rank;
  }
  
  public VariableLocation loc() {
    return this.loc;
  }
  
  public void addStmt(BasicStatement stmt) {
    stmts.add(stmt);
  }

}
