package edu.mit.compilers.le02.asm;

public class StringAsmArg implements AsmArg {
  private String string;

  public StringAsmArg(String str) {
    string = str;
  }

  public String toString() {
    return string;
  }
}
