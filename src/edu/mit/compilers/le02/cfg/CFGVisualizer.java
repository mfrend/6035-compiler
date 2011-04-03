package edu.mit.compilers.le02.cfg;

import java.io.FileWriter;
import java.io.IOException;

import edu.mit.compilers.tools.CLI;

public class CFGVisualizer {

  public static void writeCfgToFile(String filename,
      ControlFlowGraph cfg,
      boolean isLow) {
    try {
      FileWriter outfile = new FileWriter(filename);
      outfile.write(makeCfgFile(cfg, isLow));
      outfile.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static String makeCfgFile(ControlFlowGraph cfg, boolean isLow) {
    String dotFile = "# Control flow output generated from " +
        CLI.getInputFilename() + "\n\n";
    for (String method : cfg.getMethods()) {
      CFGNode node = cfg.getMethod(method);
      if (isLow) {
        dotFile += "\n" + method + ": " + node.hashCode() + "\n";
      }
      node.prepDotString();
      dotFile += node.getCfgListing();
    }
    dotFile += "\n";
    return dotFile;

  }

  public static void writeToDotFile(String filename,
                                    ControlFlowGraph cfg,
                                    boolean isLow) {
    try {
      FileWriter outfile = new FileWriter(filename);
      outfile.write(makeDotFile(cfg, isLow));
      outfile.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static String makeDotFile(ControlFlowGraph cfg, boolean isLow) {
    String dotFile = "digraph G {\n";
    for (String method : cfg.getMethods()) {
      CFGNode node = cfg.getMethod(method);
      if (isLow) {
        dotFile += method + " -> " + node.hashCode() + "\n";
      }
      node.prepDotString();
      dotFile += node.getDotString();
    }
    dotFile += "}\n";
    return dotFile;

  }


}
