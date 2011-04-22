package edu.mit.compilers.le02.asm;

public enum AsmOpCode {
	ADDQ,
	CMOVEQ,
	CMOVGEQ,
	CMOVGQ,
	CMOVLEQ,
	CMOVLQ,
	CMOVNEQ,
	CMPQ,
	CALL,
	ENTER,
	IDIVQ,
	IMULQ,
	JE,
	JG,
	JGE,
	JL,
	JLE,
	JMP,
	JNE,
	LEAVE,
	MOVQ,
	NEGQ,
	POPQ,
	PUSHQ,
	RET,
	SUBQ,
	XORQ;
	
	
	public String toString() {
		  return name().toLowerCase();
		 }

}
