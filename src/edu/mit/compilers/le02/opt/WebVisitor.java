package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.cfg.Argument.ArgType;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.dfa.ReachingDefinitions;
import edu.mit.compilers.le02.dfa.ReachingDefinitions.BlockItem;

public class WebVisitor extends BasicBlockVisitor {
  private Map<BasicStatement, Web> defUses;
  private Map<BasicStatement, List<Web>> useToDefs;
  private List<Web> finalWebs;
  private ReachingDefinitions rd;

  public WebVisitor(ReachingDefinitions rd) {
    // TODO
  }
  
  public static void getWebs(BasicBlockNode methodHead) {
    ReachingDefinitions rd = new ReachingDefinitions(methodHead);
    WebVisitor visitor = new WebVisitor(rd);
  }
  
  /**
   * Generates the def-use chains (in Web form) for a given basic block.
   * @param node The given basic block
   */
  private void generateDefUses(BasicBlockNode node) {
    Collection<BasicStatement> defs;
    BlockItem bi = rd.getDefinitions(node);
    for (BasicStatement stmt : node.getStatements()) {
      
      // Only ops are in def-use chains
      if (stmt.getType() != BasicStatementType.OP) {
        continue;
      }

      OpStatement op = (OpStatement)stmt;
      VariableLocation loc;
      if (op.getArg1().getType() == ArgType.VARIABLE) {
        loc = op.getArg1().getDesc().getLocation();
        defs = bi.getReachingDefinitions(loc);
        addDefUse(op, loc, defs);
      }
      
      if (op.getArg2().getType() == ArgType.VARIABLE) {
        loc = op.getArg2().getDesc().getLocation();
        defs = bi.getReachingDefinitions(loc);
        addDefUse(op, loc, defs);
      }
    }
  }
  
  /**
   * Adds a specific use of a specific variable to the def-use chains of the
   * given definitions.  Assumes that the definitions all reach the given use
   * and that they use the same variable as given.
   * @param use
   * @param loc
   * @param defs
   */
  private void addDefUse(BasicStatement use,
                         VariableLocation loc, 
                         Collection<BasicStatement> defs) {
    for (BasicStatement def : defs) {
      Web uses = defUses.get(def);
      if (uses == null) {
        uses = new Web(loc, def);
      }
      uses.addStmt(use);
      defUses.put(def, uses);
      
      List<Web> webs = useToDefs.get(use);
      if (webs == null) {
        webs = new ArrayList<Web>();
      }
      webs.add(uses);
      useToDefs.put(use, webs);
    }
  }
  
  /**
   * Combines the def-use chains created by generateDefUses such that they
   * satisfy the condition for webs, that all definitions that reach same use 
   * must be in same web.
   */
  private void combineWebs() {
    HashMap<VariableLocation, Web> finalWebMap = 
        new HashMap<VariableLocation, Web>();
    
    for (Entry<BasicStatement, List<Web>> entry : useToDefs.entrySet()) {
      List<Web> webs = entry.getValue();
      
      Web finalWeb;
      for (Web web : webs) {
        finalWeb = finalWebMap.get(web.loc());
        
        if (finalWeb == null) {
          finalWeb = web;
        }
        else {
          assert finalWeb.loc().equals(web.loc());
          finalWeb.union(web);
        }
        finalWebMap.put(finalWeb.loc(), finalWeb);
      }
      
      finalWebs.addAll(finalWebMap.values());
      finalWebMap.clear();
    }
  }

  @Override
  protected void processNode(BasicBlockNode node) {
    // TODO Auto-generated method stub
    
  }
}
