package edu.mit.compilers.le02;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.VariableLocation.LocationType;

public class GlobalLocation extends VariableLocation {
  private String symbol;

  public GlobalLocation(String id) {
    this.type = LocationType.GLOBAL;
    this.symbol = id;
  }


  public String getSymbol() {
    return this.symbol;
  }

}
