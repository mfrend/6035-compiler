package edu.mit.compilers.le02.dfa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.opt.BasicBlockVisitor;
import edu.mit.compilers.le02.dfa.Liveness.BlockItem;

/**
 *
 * @author Shaunak Kishore
 *
 */
public class DeadCodeElimination extends BasicBlockVisitor {
  private Map<BasicBlockNode, BlockItem> blockItems;

  public DeadCodeElimination(BasicBlockNode methodStart,
                             Map<BasicBlockNode, BlockItem> blockItems) {

    this.blockItems = blockItems; 
    this.visit(methodStart);
  }

  @Override
  protected void processNode(BasicBlockNode node) {
    List<BasicStatement> oldStatements = node.getStatements();
    List<BasicStatement> statements = new ArrayList<BasicStatement>();
    BlockItem item = blockItems.get(node);
    if (item == null) {
      ErrorReporting.reportErrorCompat(new Exception("No liveness data for " +
          "a node in the cfg"));
      return;
    }
    Set<BasicStatement> eliminationSet = item.getEliminationSet();

    for (BasicStatement s : oldStatements) {
      if (!eliminationSet.contains(s)) {
        statements.add(s);
      }
    }
    node.setStatements(statements);
  }
}
