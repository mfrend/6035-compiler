package edu.mit.compilers.le02.cfg;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

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

  public void putBasicBlock(String id, BasicBlockNode node) {
    basicBlocks.put(id, node);
  }

  public Iterator<Entry<String, BasicBlockNode>> getBasicBlocks() {
    return basicBlocks.entrySet().iterator();
  }

  public FieldDescriptor getGlobal(String id) {
    return globals.get(id);
  }

  public void putGlobal(String id, FieldDescriptor desc) {
    globals.put(id, desc);
  }

  public Iterator<Entry<String, FieldDescriptor>> getGlobals() {
    return globals.entrySet().iterator();
  }

  public String getStringData(String id) {
    return stringData.get(id);
  }

  public void putStringData(String id, String data) {
    stringData.put(id, data);
  }

  public Iterator<Entry<String, String>> getStrings() {
    return stringData.entrySet().iterator();
  }
}
