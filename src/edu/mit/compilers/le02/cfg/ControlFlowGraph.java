package edu.mit.compilers.le02.cfg;

import java.util.HashMap;
import java.util.Map;

import edu.mit.compilers.le02.symboltable.FieldDescriptor;

public class ControlFlowGraph {
  private Map<String, CFGNode> methods;
  private Map<String, FieldDescriptor> globals;
  private Map<String, String> stringData;

  public ControlFlowGraph() {
    this.methods = new HashMap<String, CFGNode>();
    this.globals = new HashMap<String, FieldDescriptor>();
    this.stringData = new HashMap<String, String>();
  }

  public CFGNode getMethod(String id) {
    return methods.get(id);
  }
  
  public void putMethod(String id, CFGNode node) {
    methods.put(id, node);
  }

  public FieldDescriptor getGlobal(String id) {
    return globals.get(id);
  }

  public void putGlobal(String id, FieldDescriptor desc) {
    globals.put(id, desc);
  }
  
  public String getStringData(String id) {
    return stringData.get(id);
  }

  public void putStringData(String id, String data) {
    stringData.put(id, data);
  }

}
