package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

public class InterferenceGraph {
  // Trying to keep things deterministic
  private SortedMap<Web, IGNode> nodes = new TreeMap<Web, IGNode>();
  
  public boolean isEmpty() {
    return nodes.isEmpty();
  }
  
  public void addNode(Web web) {
    nodes.put(web, new IGNode(web));
  }
  
  public void linkNodes(Web w1, Web w2) {
    if (w1 == w2) {
      return;
    }
    
    IGNode n1, n2;
    n1 = nodes.get(w1);
    n2 = nodes.get(w2);
    n1.addNeighbor(n2);
    n2.addNeighbor(n1);
  }
  
  private IGNode removeLowestDegree() {
    int min = Integer.MAX_VALUE;
    IGNode minNode = null;
    for (IGNode node : nodes.values()) {
      if (node.wasRemoved()) {
        continue;
      }
      if (node.getDegree() < min) {
        min = node.getDegree();
        minNode = node;
      }
    }
    
    minNode.simulateRemove();
    return minNode;
  }
  
  private int colorNode(IGNode node) {
    HashSet<Integer> colors = new HashSet<Integer>();
    for (IGNode n : node.getNeighbors()) {
      colors.add(n.getColor());
    }
    
    int color = 0;
    while (colors.contains(color)) {
      color++;
    }
    
    node.setColor(color);
    return color;
  }

  public int colorGraph() {
    Stack<IGNode> stack = new Stack<IGNode>();
    int numColors = 0;
    
    for (int i = 0; i < nodes.size(); i++) {
      stack.push(removeLowestDegree());
    }
    
    int color;
    while (!stack.empty()) {
      color = colorNode(stack.pop());
      if (color >= numColors) {
        numColors = color + 1;
      }
    }
    return numColors;
  }

  public static class IGNode implements Comparable<IGNode> {
    private Web web;
    private int color = -1;
    private int degree = 0;
    private boolean removed = false;
    // Trying to keep things deterministic
    private SortedSet<IGNode> neighbors = new TreeSet<IGNode>();
    
    public IGNode(Web web) {
      this.web = web;
    }
    
    public void reset() {
      removed = false;
      degree = neighbors.size();
    }
    
    public boolean wasRemoved() {
      return removed;
    }
    
    public int getDegree() {
      return degree;
    }
    
    public int getColor() {
      return color;
    }
 
    public void setColor(int color) {
      this.color = color;
      this.web.setColor(color);
    }
    
    public Set<IGNode> getNeighbors() {
      return neighbors;
    }
    
    public void addNeighbor(IGNode node) {
      if (neighbors.contains(node)) {
        return;
      }
      
      neighbors.add(node);
      degree += 1;
    }
  
    public void simulateRemoveNeighbor(IGNode node) {
      if (!neighbors.contains(node)) {
        return;
      }
      
      degree -= 1;
    }
    
    public void simulateRemove() {
      for (IGNode n : neighbors) {
        n.simulateRemoveNeighbor(this);
      }
      removed = true;
    }
    
    @Override
    public String toString() {
      return "NODE\ndegree: " + getDegree() + "\n"
           + "color: " + color + "\n"
           + "removed: " + removed + "\n"
           + "# neighbors: " + neighbors.size() + "\n";
    }
    
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof IGNode)) return false;
      IGNode other = (IGNode) o;
      return this.web.equals(other.web);
    }
    
    @Override
    public int hashCode() {
      return this.web.hashCode();
    }

    @Override
    public int compareTo(IGNode node) {
      return this.web.compareTo(node.web);
    }

  };
}
