package edu.mit.compilers.le02.asm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.tools.CLI;

/**
 * Represents an asm instruction to be used in output
 * 
 * @author mfrend@mit.edu (Maria Frendberg)
 * 
 */

public class AsmInstruction implements AsmObject {

	AsmOpCode opcode;
	SourceLocation loc;
	String first_operand, second_operand;

	public AsmInstruction(AsmOpCode opCode, SourceLocation loc) {
		this(opCode, "", "", loc);
	}

	public AsmInstruction(AsmOpCode opCode, String first_operand,
			SourceLocation loc) {
		this(opCode, first_operand, "", loc);
	}

	public AsmInstruction(AsmOpCode opCode, Register first_operand,
			SourceLocation loc) {
		this(opCode, first_operand.toString(), "", loc);
	}

	public AsmInstruction(AsmOpCode opCode, Register first_operand,
			Register second_operand, SourceLocation loc) {

		this(opCode, first_operand.toString(), second_operand.toString(), loc);
	}

	public AsmInstruction(AsmOpCode opCode, Register first_operand,
			String second_operand, SourceLocation loc) {
		
		this(opCode, first_operand.toString(), second_operand, loc);
	}

	public AsmInstruction(AsmOpCode opCode, String first_operand,
			Register second_operand, SourceLocation loc) {

		this(opCode, first_operand, second_operand.toString(), loc);
	}

	public AsmInstruction(AsmOpCode opCode, String first_operand,
			String second_operand, SourceLocation loc) {

		this.opcode = opCode;
		this.first_operand = first_operand;
		this.second_operand = second_operand;
		this.loc = loc;
	}

	public String toString() {
		String result = "  " + opcode.toString();
		if (this.first_operand != "") {
			result += " " + first_operand;
			if (this.second_operand != "") {
				result += ", " + second_operand;
			}
		}
		return result + getOriginalSource(loc);
	}

	/**
	 * Attempts to pull the original source line corresponding to an ASM op.
	 */
	protected static String getOriginalSource(SourceLocation loc) {
		if (loc.getLine() >= 0 && loc.getCol() >= 0
				&& !loc.getFilename().equals(CLI.STDIN)) {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(loc
						.getFilename()));
				int line = 0;
				String lineContents = "";
				while (line < loc.getLine()) {
					lineContents = reader.readLine();
					line++;
				}
				return " # " + lineContents.substring(0, loc.getCol()) + "@"
						+ lineContents.substring(loc.getCol());
			} catch (IOException ioe) {
				return "";
			} catch (StringIndexOutOfBoundsException oob) {
				return "";
			}
		} else {
			return "";
		}
	}

}
