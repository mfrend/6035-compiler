package edu.mit.compilers.le02.cfg;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public class CFGVisualizer {
  
  public static void writeToDotFile(String filename, ControlFlowGraph cfg) {
    try {
      FileWriter outfile = new FileWriter(filename);
      outfile.write(makeDotFile(cfg));
      outfile.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  public static String makeDotFile(ControlFlowGraph cfg) {
    String dotFile = "digraph G {\n";
    for (String method : cfg.getMethods()) {
      CFGNode node = cfg.getMethod(method);
      dotFile += method + " -> " + node.hashCode() + "\n";
      dotFile += node.getDotString();
    }
    dotFile += "}\n";
    return dotFile;
    
  }
  
  public static String makeDotFile(CFGNode node, String graphName) {
    String dotFile = "digraph " + graphName + " {\n";
    dotFile += node.getDotString();
    dotFile += "}\n";
    return dotFile;
  }

  
}
