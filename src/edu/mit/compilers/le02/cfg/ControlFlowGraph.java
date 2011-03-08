package edu.mit.compilers.le02.cfg;

import java.util.Map;

import edu.mit.compilers.le02.symboltable.FieldDescriptor;

public final class ControlFlowGraph {
  private Map<String, BasicBlockNode> basicBlocks;
  private Map<String, FieldDescriptor> globals;
  private Map<String, String> stringData;
  
  public ControlFlowGraph(Map<String, BasicBlockNode> basicBlocks,
                          Map<String, FieldDescriptor> globals,
                          Map<String, String> stringData) {
    this.basicBlocks = basicBlocks;
    this.globals = globals;
    this.stringData = stringData;
  }
  
  public BasicBlockNode getBasicBlock(String id) {
    return basicBlocks.get(id);
  }
  
  public FieldDescriptor getGlobal(String id) {
    return globals.get(id);
  }
  
  public String getStringData(String id) {
    return stringData.get(id);
  }

}
