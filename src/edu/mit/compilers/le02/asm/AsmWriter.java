package edu.mit.compilers.le02.asm;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map.Entry;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.tools.CLI;

public class AsmWriter {
  private ControlFlowGraph cfg;

  public AsmWriter(ControlFlowGraph graph) {
    cfg = graph;
  }

  public void write(PrintStream writer) {
    writer.println("# Assembly output generated from " +
      CLI.getInputFilename());
    writer.println(".global main");

    Iterator<Entry<String, BasicBlockNode>> blockIter = cfg.getBasicBlocks();
    while (blockIter.hasNext()) {
      Entry<String, BasicBlockNode> entry = blockIter.next();
      String blockName = entry.getKey();
      BasicBlockNode node = entry.getValue();
      writer.println("# BlockNode: " + blockName);
      writer.println(blockName + ":");
      for (BasicStatement stmt : node.getStatements()) {
        if (stmt instanceof CallStatement) {
          
        } else if (stmt instanceof OpStatement) {
          OpStatement op = (OpStatement)stmt;
          writer.println("  " + "" + "# " + "original source");
        } else {
          // We have an UnexpandedStatement that made it to ASM generation.
          ErrorReporting.reportError(new AsmException(
            stmt.getNode().getSourceLoc(),
            "UnexpandedStatement found at codegen time."));
        }
      }
    }
  }
}
