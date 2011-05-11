package edu.mit.compilers.le02.asm;

public enum AsmOpCode {
  ADDQ,
  ADDL,
  ANDL,
  CDQ,
  CMOVEL,
  CMOVGEL,
  CMOVGL,
  CMOVLEL,
  CMOVLL,
  CMOVNEL,
  CMPL,
  CALL,
  ENTER,
  IDIVL,
  IMULL,
  JE,
  JG,
  JGE,
  JL,
  JLE,
  JMP,
  JNE,
  LEAVE,
  MOVQ,
  MOVL,
  MOVSXD,
  NEGL,
  POPQ,
  PUSHQ,
  RET,
  SHLL,
  SARL,
  SUBL,
  XCHGQ,
  XORQ,
  XORL,
  ;

  public String toString() {
    return name().toLowerCase();
  }

}
