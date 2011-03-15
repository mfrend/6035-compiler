package edu.mit.compilers.le02.cfg;

/**
 * Immutable class representing a fragment of a CFG with one entrance and
 * one exit. 
 * @author dkoh
 *
 */
public class CFGFragment {
  private SimpleCFGNode enter;
  private SimpleCFGNode exit;

  public CFGFragment(SimpleCFGNode enter, SimpleCFGNode exit) {
    this.enter = enter;
    this.exit = exit;
  }
  
  public SimpleCFGNode getEnter() {
    return enter;
  }
  
  public SimpleCFGNode getExit() {
    return exit;
  }
  
  public CFGFragment append(SimpleCFGNode node) {
    this.exit.setNext(node);
    return new CFGFragment(this.enter, node);
  }
  
  /**
   * Links this fragment's exit to the given fragments enter
   * @return new linked CFG fragment
   */
  public CFGFragment link(CFGFragment next) {
    this.exit.setNext(next.enter);
    return new CFGFragment(this.enter, next.exit);
  }
}
