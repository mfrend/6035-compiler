package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.mit.compilers.le02.cfg.BasicBlockNode;

public abstract class BasicBlockVisitor {

  public void visit(BasicBlockNode methodNode) {
    List<BasicBlockNode> nodesToProcess = new ArrayList<BasicBlockNode>();
    Set<BasicBlockNode> processed = new HashSet<BasicBlockNode>();
    nodesToProcess.add(methodNode);
  
    while (!nodesToProcess.isEmpty()) {
      // Pop top element of queue to process.
      BasicBlockNode node = nodesToProcess.remove(0);
      // If we've seen this node already, we don't need to process it again.
      if (processed.contains(node)) {
        continue;
      }
      // Mark this node processed.
      processed.add(node);
  
      // If this node has successors, queue them for processing.
      BasicBlockNode branch = node.getBranchTarget();
      if (node.isBranch()) {
        nodesToProcess.add(branch);
      }
      BasicBlockNode next = node.getNext();
      if (next != null) {
        nodesToProcess.add(next);
      }
      processNode(node);
    }
  }

  abstract protected void processNode(BasicBlockNode node);
}
