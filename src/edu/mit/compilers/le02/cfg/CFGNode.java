package edu.mit.compilers.le02.cfg;


public interface CFGNode {

  /**
   * Get the next node in the control flow graph.
   *
   * If this node may branch, the result of this method is where the control
   * flow will go if the branch is not taken. This may only return null at the
   * end of a method.
   * @return Next node in CFG, or null if we reached the end of the method.
   */
  public CFGNode getNext();

  /**
   * Get a representation of the node for a .dot file, for visualization
   */
  public void prepDotString();
  public String getDotString();

  /**
   * Returns true if this instruction may branch.
   * @return true if this instruction may branch, false otherwise.
   */
  public boolean isBranch();


  public BasicStatement getConditional();

  /**
   * Returns the branch target of the CFG node
   * @return CFGNode representing the target of the branch.
   */
  public CFGNode getBranchTarget();

}
