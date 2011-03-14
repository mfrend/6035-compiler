package edu.mit.compilers.le02;

import edu.mit.compilers.le02.VariableLocation;

public class GlobalLocation extends VariableLocation {
  private String symbol;

  public GlobalLocation(String id) {
    this.symbol = id;
  }


  public String getSymbol() {
    return this.symbol;
  }

}
