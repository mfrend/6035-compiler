package edu.mit.compilers.le02.asm;

import java.io.PrintStream;

import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public class AsmWriter {
	private SymbolTable st;
	private ControlFlowGraph cfg;

	private AsmFile file;

	/**
	 * Generates a new AsmWriter class and configures its inputs/outputs.
	 */
	public AsmWriter(ControlFlowGraph graph, SymbolTable table,
			PrintStream writer) {
		cfg = graph;
		st = table;
		
		file = new AsmFile(cfg,st,writer);	
		file.write();
	}

	
	



}
