package edu.mit.compilers.le02.asm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.NOPStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

/**
 * Represents the asm instructions corresponding to a BasicBlock.
 * AsmInstructions are generated from the inputed BasicBlock
 * 
 * @author lizfong@mit.edu (Liz Fong)
 * @author mfrend@mit.edu (Maria Frendberg)
 * 
 */

public class AsmBasicBlock extends AsmObject {
	private String methodName;
	private BasicBlockNode methodNode;
	private MethodDescriptor thisMethod;
	private SymbolTable st;

	private ArrayList<AsmObject> instructions;

	public AsmBasicBlock(String methodName, BasicBlockNode methodNode,
			MethodDescriptor thisMethod, SymbolTable st) {
		this.instructions = new ArrayList<AsmObject>();

		this.methodName = methodName;
		this.methodNode = methodNode;
		this.thisMethod = thisMethod;
		this.st = st;

		this.processBlock();
	}

	public void addInstruction(AsmObject instruction) {
		this.instructions.add(instruction);
	}

	public void reorderInstructions() {
		// TODO @mfrend Finish Method
	}

	public ArrayList<AsmObject> getBlock() {
		return this.instructions;
	}

	private void processBlock() {
		List<BasicBlockNode> nodesToProcess = new ArrayList<BasicBlockNode>();
		Set<BasicBlockNode> processed = new HashSet<BasicBlockNode>();
		nodesToProcess.add(methodNode);

		while (!nodesToProcess.isEmpty()) {
			// Pop top element of queue to process.
			BasicBlockNode node = nodesToProcess.remove(0);
			// If we've seen this node already, we don't need to output it
			// again.
			if (processed.contains(node)) {
				continue;
			}
			// Mark this node processed.
			processed.add(node);

			// If this node has successors, queue them for processing.
			BasicBlockNode branch = node.getBranchTarget();
			if (node.isBranch()) {
				nodesToProcess.add(branch);
			}
			BasicBlockNode next = node.getNext();
			if (next != null) {
				nodesToProcess.add(next);
			}

			// Start the output.
			instructions.add(new AsmString(AsmFile.writeLabel(node.getId())));

			// Process each statement.
			for (BasicStatement stmt : node.getStatements()) {
				processStatement(stmt, methodName, thisMethod);
			}

			// Generate an appropriate SourceLocation for the block trailer.
			SourceLocation loc = SourceLocation
					.getSourceLocationWithoutDetails();
			if (node.getLastStatement() != null
					&& node.getLastStatement().getNode() != null) {
				loc = node.getLastStatement().getNode().getSourceLoc();
			}

			// Write the block trailer. There are three cases to consider:
			// If the node is a branch, write the branch trailer.
			// Otherwise, if there's a next node, write an unconditional
			// jump.
			// Finally, if there's no next node, insert an implicit return.
			if (node.isBranch()) {
				processBranch(node, branch, next, methodName, loc);
			} else if (next != null) {
				addInstruction(new AsmInstruction("jmp", next.getId(), loc));
			} else if (!(node.getLastStatement() instanceof OpStatement && ((OpStatement) node
					.getLastStatement()).getOp() == AsmOp.RETURN)) {
				// Insert an implicit return.
				MethodDescriptor returnMethod = st.getMethod(methodName);
				generateMethodReturn(null, returnMethod, loc);
			}
		}
	}

	/**
	 * Writes a statement from the block to the ASM output stream.
	 */
	protected void processStatement(BasicStatement stmt, String methodName,
			MethodDescriptor thisMethod) {
		// Save the location of the statement we are processing, if known.
		SourceLocation sl = SourceLocation.getSourceLocationWithoutDetails();
		if (stmt.getNode() != null) {
			sl = stmt.getNode().getSourceLoc();
		}

		if (stmt instanceof OpStatement) {
			processOpStatement((OpStatement) stmt, methodName, thisMethod, sl);
		} else if (stmt instanceof CallStatement) {
			generateCall((CallStatement) stmt, thisMethod);
		} else if (stmt instanceof NOPStatement) {
			// This is a nop; ignore it and continue onwards.
			return;
		} else {
			// We have an ArgumentStatement that made it to ASM generation.
			// These are supposed to be filtered out during CFG pass 2.
			ErrorReporting.reportError(new AsmException(sl,
					"Low level statement found at codegen time"));
			return;
		}
	}

	/**
	 * Writes a trailer for the current block, assuming ends in a branch.
	 */
	protected void processBranch(BasicBlockNode node, BasicBlockNode branch,
			BasicBlockNode next, String methodName, SourceLocation loc) {
		// If the last operation carried out was a conditional, the flags from
		// that comparison will still be set; we just need to retrieve the op
		// so we can perform the correct jump using those flags.
		// It's also possible it was a NOT which leaves its return value to R10,
		// or that a MOVE was executed specially to retrieve a value from memory
		// or a register; if this is the case, the conditional value lives in
		// R11.
		String conditionalJump = "";
		Register resultRegister = null;

		if (node.getConditional() instanceof OpStatement) {
			OpStatement condition = (OpStatement) node.getConditional();
			switch (condition.getOp()) {
			case EQUAL:
				conditionalJump = "je";
				break;
			case NOT_EQUAL:
				conditionalJump = "jne";
				break;
			case LESS_THAN:
				conditionalJump = "jl";
				break;
			case LESS_OR_EQUAL:
				conditionalJump = "jle";
				break;
			case GREATER_THAN:
				conditionalJump = "jg";
				break;
			case GREATER_OR_EQUAL:
				conditionalJump = "jge";
				break;
			case NOT:
				// NOT leaves value in R10.
				resultRegister = Register.R10;
				break;
			case MOVE:
				// MOV leaves value in R11.
				resultRegister = Register.R11;
				break;
			default:
				// Something has gone wrong. This shouldn't happen.
				ErrorReporting.reportError(new AsmException(loc,
						"Bad opcode for conditional"));
				return;
			}
		} else {
			// We just came back from a call e.g. if (foo()) [...]
			// The return value of the call will still be in RAX.
			resultRegister = Register.RAX;
		}

		// If we had a CMP earlier, perform the conditional jump now.
		// Otherwise, we need to compare the boolean to true and jump if it is
		// in fact true ($1).
		if (conditionalJump != "") {
			addInstruction(new AsmInstruction(conditionalJump, branch.getId(),
					loc));
		} else {
			addInstruction(new AsmInstruction("movq", "$1", Register.R12, loc));
			addInstruction(new AsmInstruction("cmpq", Register.R12,
					resultRegister, loc));
			addInstruction(new AsmInstruction("je", branch.getId(), loc));
		}

		// Write the alternate unconditional jump to the next block since by
		// this
		// point we've failed the conditional jump check.
		if (next != null) {
			addInstruction(new AsmInstruction("jmp", next.getId(), loc));
		} else {
			// Insert an implicit return, since there are no more basicblocks
			// left
			// in this method to jump to.
			MethodDescriptor returnMethod = st.getMethod(methodName);
			generateMethodReturn(null, returnMethod, loc);
		}
	}

	/** Contains the registers used for argument passing in order. */
	public static Register[] argumentRegisters = { Register.RDI, // 1st arg
			Register.RSI, // 2nd arg
			Register.RDX, // 3rd arg
			Register.RCX, // 4th arg
			Register.R8, // 5th arg
			Register.R9, // 6th arg
	};

	/**
	 * Generates the header for a method entry. Requires the method descriptor
	 * and the number of locals to initialize.
	 */
	protected void generateMethodHeader(MethodDescriptor desc, int numLocals) {
		SourceLocation sl = desc.getSourceLocation();
		addInstruction(new AsmInstruction("enter", "$" + numLocals, "$0", sl));

		for (Register reg : desc.getUsedCalleeRegisters()) {
			addInstruction(new AsmInstruction("pushq", reg, sl)); // Save
																	// registers
																	// used in
			// method.
		}
		for (int ii = 0; ii < Math.min(desc.getParams().size(), 6); ii++) {
			desc.markRegisterUsed(argumentRegisters[ii]);
		}
		desc.markRegisterUsed(Register.R12);
	}

	/**
	 * Generates the method trailer for returning from a method. Detects if it's
	 * being asked to return without an argument and the method is non-void; if
	 * so, writes an error handler that will be called at runtime if we reach
	 * this ending by falling off the method end.
	 */
	protected void generateMethodReturn(String arg1, MethodDescriptor desc,
			SourceLocation sl) {
		if (desc.getType() != DecafType.VOID && arg1 == null) {
			addInstruction(new AsmInstruction("movq", "$." + desc.getId()
					+ "_name", Register.R12, sl));
			addInstruction(new AsmInstruction("jle",
					"nonvoid_noreturn_error_handler", sl));
			return;
		}
		if (arg1 != null) {
			// Save result in return register.
			addInstruction(new AsmInstruction("movq", arg1, Register.RAX, sl));
		} else {
			// Clear %rax to prevent confusion and non-zero exit codes since
			// decaf allows main() to be either void or int and system reads
			// the return value regardless of whether main is void.
			addInstruction(new AsmInstruction("xorq", Register.RAX,
					Register.RAX, sl));
		}

		// Restore callee-saved registers we used.
		List<Register> usedRegisters = desc.getUsedCalleeRegisters();
		Collections.reverse(usedRegisters);
		for (Register reg : usedRegisters) {
			addInstruction(new AsmInstruction("popq", reg, sl));
		}

		// Push old base pointer.
		addInstruction(new AsmInstruction("leave", sl));
		// Caller cleans up arguments.
		addInstruction(new AsmInstruction("ret", sl));
	}

	/**
	 * Processes a single OpStatement into assembly instructions.
	 */
	protected void processOpStatement(OpStatement op, String methodName,
			MethodDescriptor thisMethod, SourceLocation sl) {
		// Default to saving results in R10; we can pull results from a
		// different register if the CPU spec mandates it.
		Register resultReg = Register.R11;

		// prepareArgument loads an argument from memory/another register
		// into R10 or R11 and returns the reg it stored the argument in.
		String arg1 = "<error>";
		if (op.getArg1() != null && op.getOp() != AsmOp.ENTER) {
			arg1 = prepareArgument(op.getArg1(), true, methodName, sl);
		}
		String arg2 = "<error>";
		if (op.getArg2() != null && op.getOp() != AsmOp.MOVE) {
			arg2 = prepareArgument(op.getArg2(), false, methodName, sl);
		}

		switch (op.getOp()) {
		case MOVE:
			arg2 = "" + Register.R11;
			addInstruction(new AsmInstruction("movq", arg1, arg2, sl));
			writeToArgument(op.getArg2(), methodName, sl);
			// Stop here because we don't need to move result again.
			return;
		case ADD:
			addInstruction(new AsmInstruction("addq", arg1, arg2, sl));
			break;
		case SUBTRACT:
			addInstruction(new AsmInstruction("subq", arg2, arg1, sl));
			// Subtract reverses the order of its arguments, so arg1 contains
			// the
			// modified result.
			resultReg = Register.R10;
			break;
		case MULTIPLY:
			addInstruction(new AsmInstruction("imulq", arg1, arg2, sl));
			break;
		case DIVIDE:
		case MODULO:
			// Division needs to use RAX for the dividend, and overwrites
			// RAX/RDX
			// to store its outputs.
			addInstruction(new AsmInstruction("movq", arg1, "%rax", sl));
			// Unfortunately, RDX may contain the first argument to the
			// function.
			// We need to push it to memory to save it.
			addInstruction(new AsmInstruction("pushq", "%rdx", sl));
			addInstruction(new AsmInstruction("xorq", "%rdx", "%rdx", sl));
			addInstruction(new AsmInstruction("idivq", arg2, sl));
			if (op.getOp() == AsmOp.DIVIDE) {
				// RDX is fixed to hold the quotient.
				resultReg = Register.RAX;
			} else {
				// RDX is fixed to hold the remainder.
				resultReg = Register.RDX;
			}
			break;
		case UNARY_MINUS:
			// Unary operations use R10 for input and output.
			addInstruction(new AsmInstruction("negq", arg1, sl));
			resultReg = Register.R10;
			break;
		case NOT:
			addInstruction(new AsmInstruction("xorq", "$1", arg1, sl));
			resultReg = Register.R10;
			break;
		case EQUAL:
		case NOT_EQUAL:
		case LESS_THAN:
		case LESS_OR_EQUAL:
		case GREATER_THAN:
		case GREATER_OR_EQUAL:
			processBoolean(op.getOp(), arg1, arg2, sl);
			resultReg = Register.RAX;
			break;
		case RETURN:
			if (op.getArg1() != null) {
				generateMethodReturn(arg1, thisMethod, sl);
			} else {
				generateMethodReturn(null, thisMethod, sl);
			}
			return;
		case ENTER:
			generateMethodHeader(thisMethod, ((ConstantArgument) op.getArg1())
					.getInt());
			return;
		default:
			ErrorReporting.reportError(new AsmException(sl, "Unknown opcode."));
			return;
		}
		if (op.getResult() != null) {
			addInstruction(new AsmInstruction("movq", resultReg,
					convertVariableLocation(op.getResult().getLocation()), sl));
		} else {
			addInstruction(new AsmString(
					"  /* Ignoring result assignment of conditional. */"));
		}
		if (op.getOp() == AsmOp.DIVIDE || op.getOp() == AsmOp.MODULO) {
			// Restore the register we displaced for division/modulo.
			addInstruction(new AsmInstruction("popq", "%rdx", sl));
		}
	}

	/**
	 * Performs a boolean comparison of two arguments. The arguments must have
	 * already been pulled out of memory.
	 */
	protected void processBoolean(AsmOp op, String arg1, String arg2,
			SourceLocation sl) {
		addInstruction(new AsmInstruction("xorq", Register.RAX, Register.RAX,
				sl));
		addInstruction(new AsmInstruction("cmpq", arg2, arg1, sl));
		String cmovOp = "";
		switch (op) {
		case EQUAL:
			cmovOp = "cmoveq";
			break;
		case NOT_EQUAL:
			cmovOp = "cmovneq";
			break;
		case LESS_THAN:
			cmovOp = "cmovlq";
			break;
		case LESS_OR_EQUAL:
			cmovOp = "cmovleq";
			break;
		case GREATER_THAN:
			cmovOp = "cmovgq";
			break;
		case GREATER_OR_EQUAL:
			cmovOp = "cmovgeq";
			break;
		}
		addInstruction(new AsmInstruction("movq", "$1", Register.R10, sl));
		addInstruction(new AsmInstruction(cmovOp, Register.R10, Register.RAX,
				sl));
	}

	/**
	 * Generates an outbound method call. Requires both the call statement for
	 * the current call and also the current method.
	 */
	protected void generateCall(CallStatement call, MethodDescriptor thisMethod) {
		SourceLocation sl = call.getNode().getSourceLoc();
		// Push caller-saved variables that we've used and need to keep.
		List<Register> usedRegisters = thisMethod.getUsedCallerRegisters();
		for (Register r : usedRegisters) {
			addInstruction(new AsmInstruction("pushq", r, sl));
		}

		// Push arguments.
		// First six go into registers, rest go on stack in right to left order
		List<Argument> args = call.getArgs();
		for (int ii = args.size() - 1; ii >= 0; ii--) {
			if (ii >= 6) {
				addInstruction(new AsmInstruction("pushq", prepareArgument(args
						.get(ii), true, thisMethod.getId(), sl), sl));
			} else {
				addInstruction(new AsmInstruction("movq", prepareArgument(args
						.get(ii), true, thisMethod.getId(), sl),
						argumentRegisters[ii].toString(), sl));
			}
		}

		// Empty %rax to cope with printf vararg issue
		addInstruction(new AsmInstruction("xorq", Register.RAX, Register.RAX,
				sl));

		// Now we're ready to make the call.
		// This automatically pushes the return address; callee removes return
		// addr
		addInstruction(new AsmInstruction("call", call.getMethodName(), sl));

		// Pop arguments back off the stack.
		if (args.size() > 6) {
			addInstruction(new AsmInstruction("addq", "$" + (args.size() - 6)
					* 8, Register.RSP, sl));
		}

		// Pop the saved usedCallerRegisters back onto the stack.
		Collections.reverse(usedRegisters);
		for (Register r : usedRegisters) {
			addInstruction(new AsmInstruction("popq", r, sl));
		}

		// Move RAX into the correct save location.
		addInstruction(new AsmInstruction("movq", Register.RAX,
				convertVariableLocation(call.getResult().getLocation()), sl));
	}

	/**
	 * Loads a variable from memory so that it can be used in subsequent
	 * computation. Saves to R10 if it's the first argument, R11 for the second.
	 */
	protected String prepareArgument(Argument arg, boolean first,
			String methodName, SourceLocation sl) {
		Register tempStorage = first ? Register.R10 : Register.R11;
		switch (arg.getType()) {
		case CONST_BOOL:
			if (((ConstantArgument) arg).getBool()) {
				return "$1";
			} else {
				return "$0";
			}
		case CONST_INT:
			// The immediate values in decaf cannot exceed 32 bits, so we don't
			// need
			// to mov-shl-mov-add, but if we had to deal with 64 bits, we'd do
			// this.
			// We still need to load to registers since some ops only work on
			// registers and not on immediates.
			addInstruction(new AsmInstruction("movq", "$"
					+ ((ConstantArgument) arg).getInt(), tempStorage, sl));
			break;
		case VARIABLE:
			addInstruction(new AsmInstruction("movq",
					convertVariableLocation(((VariableArgument) arg).getDesc()
							.getLocation()), tempStorage, sl));
			break;
		case ARRAY_VARIABLE:
			ArrayVariableArgument ava = (ArrayVariableArgument) arg;
			// Arrays can only be declared as globals in decaf
			assert (ava.getDesc().getLocation().getLocationType() == LocationType.GLOBAL);

			// Prepare the symbol and index names. The index needs recursive
			// resolution since it might be a variable or another array.
			// Symbol will always be a global address.
			String symbol = "." + ava.getDesc().getLocation().getSymbol();
			// The index will be a temporary register (either R10 or R11).
			// As it happens, this is also our return register, but that's okay.
			String index = prepareArgument(ava.getIndex(), first, methodName,
					sl);

			/*
			 * assert (ava.getDesc() instanceof FieldDescriptor); switch
			 * (ava.getIndex().getType()) { case ArgType.ARRAY_VARIABLE:
			 * 
			 * case ArgType.CONST_BOOL: assert (false); break; case
			 * ArgType.CONST_INT: assert (((ConstantArgument)
			 * ava.getIndex()).getInt() >= 0); assert (((ConstantArgument)
			 * ava.getIndex()).getInt() < ((FieldDescriptor) ava
			 * .getDesc()).getLength()); case ArgType.VARIABLE:
			 * assert(((VariableArgument)ava.getIndex()).getType()==) } assert
			 * (((FieldDescriptor) ava.getDesc()).getLength() > ava
			 * .getIndex()); assert (Integer.parseInt(index) >= 0);
			 */

			// Perform array bounds check. TODO(lizf): fix code duplication.
			addInstruction(new AsmInstruction("cmpq", index, symbol + "_size",
					sl));
			addInstruction(new AsmInstruction("movq", "$." + methodName
					+ "_name", Register.R12, sl));
			addInstruction(new AsmInstruction("jle", "array_oob_error_handler",
					sl));

			// Use R12 to store the global name to access.
			addInstruction(new AsmInstruction("movq", "$" + symbol,
					Register.R12, sl));

			// Finally, perform the indirection to look up from memory+offset.
			addInstruction(new AsmInstruction("movq", "(" + Register.R12 + ", "
					+ index + ", 8)", tempStorage, sl));
			break;
		}
		return "" + tempStorage;
	}

	/**
	 * Saves a result to a variable or an array variable. Results are always
	 * located in R11 for now.
	 */
	protected void writeToArgument(Argument arg, String methodName,
			SourceLocation sl) {
		switch (arg.getType()) {
		case VARIABLE:
			addInstruction(new AsmInstruction("movq", Register.R11,
					convertVariableLocation(((VariableArgument) arg).getDesc()
							.getLocation()), sl));
			break;
		case ARRAY_VARIABLE:
			ArrayVariableArgument ava = (ArrayVariableArgument) arg;
			// Arrays can only be declared as globals in decaf
			assert (ava.getDesc().getLocation().getLocationType() == LocationType.GLOBAL);

			// Prepare the symbol and index names. The index needs recursive
			// resolution since it might be a variable.
			// Symbol will always be a global address.
			String symbol = "." + ava.getDesc().getLocation().getSymbol();

			// The index will be an unused register (R10).
			// We don't want to use R11, which would clobber the result to
			// return.
			String index = prepareArgument(ava.getIndex(), true, methodName, sl);

			// Perform array bounds check. TODO(lizf): fix code duplication.
			addInstruction(new AsmInstruction("cmpq", index, symbol + "_size",
					sl));
			addInstruction(new AsmInstruction("movq", "$." + methodName
					+ "_name", Register.R12, sl));
			addInstruction(new AsmInstruction("jle", "array_oob_error_handler",
					sl));

			// Use R12 to store the global name to access.
			addInstruction(new AsmInstruction("movq", "$" + symbol,
					Register.R12, sl));

			// Finally, perform the indirection to save to memory+offset.
			addInstruction(new AsmInstruction("movq", Register.R11, "("
					+ Register.R12 + ", " + index + ", 8)", sl));
			break;
		}
	}

	/**
	 * Converts a VariableLocation object to the corresponding ASM string
	 * required to look it up as an op's argument.
	 */
	protected String convertVariableLocation(VariableLocation loc) {
		switch (loc.getLocationType()) {
		case GLOBAL:
			if (loc.getSymbol().startsWith(".str")) {
				return "$" + loc.getSymbol();
			} else {
				return "." + loc.getSymbol();
			}
		case REGISTER:
			return "" + loc.getRegister();
		case STACK:
			return loc.getOffset() + "(%rbp)";
		}
		return "";
	}
}
