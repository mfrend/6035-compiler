package edu.mit.compilers.le02;


public class GlobalLocation extends VariableLocation {
  private String symbol;

  public GlobalLocation(String id) {
    this.type = LocationType.GLOBAL;
    this.symbol = id;
  }


  public String getSymbol() {
    return this.symbol;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GlobalLocation)) return false;

    GlobalLocation other = (GlobalLocation) o;
    return this.symbol.equals(other.symbol);
  }

  @Override
  public String toString() {
    return symbol;
  }
}
