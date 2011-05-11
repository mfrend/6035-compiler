package edu.mit.compilers.le02.opt;

import java.util.HashSet;
import java.util.Set;

import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

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
public class Web implements Comparable<Web> {
  private int color;
  private Web rep;
  private int _rank;
  private Register preferredReg = null;
  private TypedDescriptor desc;
  private HashSet<BasicStatement> stmts;
  private int id;
  private static int nextID = 0;
  
  
  public Web(TypedDescriptor loc, BasicStatement def) {
    this.id = nextID++;
    this.rep = this;
    this._rank = 0;
    this.desc = loc;
    this.stmts = new HashSet<BasicStatement>();
    
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
    
    assert this.desc == other.desc;
    Web thisRoot = this.find();
    Web otherRoot = other.find();
    
    
    if (this.rank() >= other.rank()) {
      otherRoot.rep = thisRoot;
      thisRoot.stmts.addAll(otherRoot.stmts);
      
      if (thisRoot.preferredReg == null) {
        thisRoot.preferredReg = otherRoot.preferredReg;
      }
      
      if (this.rank() == other.rank()) {
        thisRoot._rank++;
      }
    }
    else {
      // other.rank() > this.rank()
      thisRoot.rep = otherRoot;
      otherRoot.stmts.addAll(thisRoot.stmts);
      
      if (otherRoot.preferredReg == null) {
        otherRoot.preferredReg = thisRoot.preferredReg;
      }
    }
  }
  
  public int rank() {
    return this.find()._rank;
  }
  
  public TypedDescriptor desc() {
    return this.desc;
  }

  public Register getPreferredRegister() {
    return this.preferredReg ;
  }
  
  public void setPreferredRegister(Register reg) {
    this.preferredReg = reg;
  }
  
  public void addStmt(BasicStatement stmt) {
    stmts.add(stmt);
  }

  public Set<BasicStatement> getStmts() {
    return this.find().stmts;
  }

  public int getColor() {
    return color;
  }

  public void setColor(int color) {
    this.color = color;
  }
  
  // TODO: Include loop nesting in spill cost
  public int getSpillCost() {
    return this.stmts.size();
  }
  
  @Override
  public String toString() {
    return "Web" + id + " {" + desc + "}";
  }
  
  public String longDesc() {
    String str =  "WEB" + id + "\n"
                + "  is rep: " + (this.find() == this.rep) + "\n"
                + "  rank: " + _rank + "\n"
                + "  desc: " + desc + "\n"
                + "  color: " + color + "\n"
                + "  statments: \n";
                
    
    for (BasicStatement s : stmts) {
      str += "  " + s + "\n";
    }

    return str;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    else if (!(o instanceof Web)) return false;
    
    Web other = (Web) o;
    return this.desc.equals(other.desc) &&
           this.stmts.equals(other.stmts);
  }
  
  @Override
  public int hashCode() {
    return desc.hashCode() * stmts.hashCode();
  }

  @Override
  public int compareTo(Web w) {
    int ret = this.desc.getId().compareTo(w.desc.getId());
    
    if (ret != 0) {
      return ret;
    }
    
    ret = this.stmts.size() - w.stmts.size();
    
    if (ret != 0) { 
      return ret;
    }
    
    return this.hashCode() - w.hashCode();
  }

}
