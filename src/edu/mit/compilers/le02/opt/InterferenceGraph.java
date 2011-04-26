package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class InterferenceGraph {
  private HashMap<Web, IGNode> nodes = new HashMap<Web, IGNode>();
  
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
  
  private IGNode removeHighestDegree() {
    int max = -1;
    IGNode maxNode = null;
    for (IGNode node : nodes.values()) {
      if (node.getDegree() > max) {
        max = node.getDegree();
        maxNode = node;
      }
    }
    
    maxNode.removeFromNeighbors();
    
    return maxNode;
  }
  
  private int colorNode(IGNode node) {
    HashSet<Integer> colors = new HashSet<Integer>();
    for (IGNode n : node.getNeighbors()) {
      colors.add(n.getColor());
    }
    
    int color = 0;
    while (colors.contains(color)) {}
    
    node.setColor(color);
    return color;
  }

  public int colorGraph() {
    Stack<IGNode> stack = new Stack<IGNode>();
    int numColors = 0;
    
    while (!isEmpty()) {
      stack.push(removeHighestDegree());
    }
    
    int color;
    while (!stack.empty()) {
      color = colorNode(stack.pop());
      if (color >= numColors) {
        numColors = color += 1;
      }
    }
    return numColors;
  }

  public static class IGNode {
    private Web web;
    private int color = -1;
    private int degree = 0;
    private Set<IGNode> neighbors = new HashSet<IGNode>();
    
    public IGNode(Web web) {
      this.web = web;
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
    
    public void removeNeighbor(IGNode node) {
      if (!neighbors.contains(node)) {
        return;
      }
      
      neighbors.remove(node);
      degree -= 1;
    }
    
    public void removeFromNeighbors() {
      for (IGNode n : neighbors) {
        n.removeNeighbor(this);
      }
    }

  };
}
