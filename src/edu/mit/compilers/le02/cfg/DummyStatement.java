package edu.mit.compilers.le02.cfg;


public class DummyStatement extends BasicStatement {

  public DummyStatement() {
    super(null, null);
    this.type = BasicStatementType.DUMMY;
  }
  
  @Override
  public String toString() {
    return "DummyStatement";
  }


}
