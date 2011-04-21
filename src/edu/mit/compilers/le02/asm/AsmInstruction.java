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

public class AsmInstruction extends AsmObject {

	String opcode;
	SourceLocation loc;
	Object first_operand, second_operand;

	/**
	 * Create an instruction with no operands
	 * 
	 * @param opCode
	 *            The opCode to use
	 * @param loc
	 *            The SourceLocation associated with this instruction
	 */
	public AsmInstruction(String opCode, SourceLocation loc) {
		this.opcode = opCode;
		this.loc = loc;
	}

	/**
	 * Create an instruction with one operands
	 * 
	 * @param opCode
	 *            The opCode to use
	 * @param first_operand
	 *            The location of the first operand
	 * @param loc
	 *            The SourceLocation associated with this instruction
	 */
	public AsmInstruction(String opCode, Object first_operand,
			SourceLocation loc) {
		assert (first_operand instanceof String || first_operand instanceof Register);

		this.opcode = opCode;
		this.first_operand = first_operand;
		this.loc = loc;
	}

	/**
	 * Create an instruction with two operands
	 * 
	 * @param opCode
	 *            The opCode to use
	 * @param first_operand
	 *            The location of the first operand
	 * @param second_operand
	 *            The location of the second operand
	 * @param loc
	 *            The SourceLocation associated with this instruction
	 */
	public AsmInstruction(String opCode, Object first_operand,
			Object second_operand, SourceLocation loc) {
		assert (first_operand instanceof String || first_operand instanceof Register);
		assert (second_operand instanceof String || first_operand instanceof Register);

		this.opcode = opCode;
		this.first_operand = first_operand;
		this.second_operand = second_operand;
		this.loc = loc;
	}

	public String toString() {
		if (this.first_operand == null) {
			return "  " + opcode + getOriginalSource(loc);
		} else if (this.second_operand == null) {
			return "  " + opcode + " " + first_operand + getOriginalSource(loc);
		} else {
			return "  " + opcode + " " + first_operand + ", " + second_operand
					+ getOriginalSource(loc);
		}
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
