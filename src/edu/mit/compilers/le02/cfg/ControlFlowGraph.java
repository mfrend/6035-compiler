package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mit.compilers.le02.ast.StringNode;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;

public class ControlFlowGraph {
  private Map<String, CFGNode> methods;
  private Map<String, FieldDescriptor> globals;
  private Map<String, StringNode> stringData;

  public ControlFlowGraph() {
    this.methods = new HashMap<String, CFGNode>();
    this.globals = new HashMap<String, FieldDescriptor>();
    this.stringData = new HashMap<String, StringNode>();
  }

  public CFGNode getMethod(String id) {
    return methods.get(id);
  }

  public List<String> getMethods() {
    return new ArrayList<String>(methods.keySet());
  }

  public void putMethod(String id, CFGNode node) {
    methods.put(id, node);
  }

  public FieldDescriptor getGlobal(String id) {
    return globals.get(id);
  }

  public List<String> getGlobals() {
    return new ArrayList<String>(globals.keySet());
  }

  public void putGlobal(String id, FieldDescriptor desc) {
    globals.put(id, desc);
  }

  public StringNode getStringData(String id) {
    return stringData.get(id);
  }

  public List<String> getAllStringData() {
    return new ArrayList<String>(stringData.keySet());
  }

  public void putStringData(String id, StringNode data) {
    stringData.put(id, data);
  }
}
