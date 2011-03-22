package edu.mit.compilers.le02.asm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.StackLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.StringNode;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.cfg.NOPStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;
import edu.mit.compilers.tools.CLI;

public class AsmWriter {
  private SymbolTable st;
  private ControlFlowGraph cfg;
  private PrintStream ps;

  /**
   * Generates a new AsmWriter class and configures its inputs/outputs.
   */
  public AsmWriter(ControlFlowGraph graph, SymbolTable table,
      PrintStream writer) {
    cfg = graph;
    ps = writer;
    st = table;
  }

  /**
   * Processes inputs and writes assembly instructions to the output.
   */
  public void write() {
    ps.println("# Assembly output generated from " +
      CLI.getInputFilename());
    ps.println(".global main");

    writeStrings();
    writeGlobals();
    writeMethods();
    writeLabel("array_oob_error_handler");
    generateImmediateExit("aoob_msg");
    writeLabel("nonvoid_noreturn_error_handler");
    generateImmediateExit("nonvoid_noreturn_msg");
  }

  /**
   * Writes the global string table, plus runtime error messages.
   * Additionally, saves the names of each method so errors can refer to
   * which method triggered them.
   */
  public void writeStrings() {
    // Strings are read-only data.
    ps.println(".section .rodata");

    // Strings from the string table.
    for (String name : cfg.getAllStringData()) {
      writeLabel(name);
      StringNode node = cfg.getStringData(name);
      // We want the explicitly escaped version.
      ps.println("  .string " + node.toString());
    }

    // Method names.
    for (String methodName : cfg.getMethods()) {
      writeLabel("." + methodName + "_name");
      ps.println("  .string \"" + methodName + "\"");
    }

    // Error handler messages.
    writeLabel(".aoob_msg");
    ps.println(
      "  .string \"*** RUNTIME ERROR ***: " +
      "Array out of Bounds access in method \\\"%s\\\"\\n\"");
    writeLabel(".nonvoid_noreturn_msg");
    ps.println(
        "  .string \"*** RUNTIME ERROR ***: " +
        "No return value from non-void method \\\"%s\\\"\\n\"");
  }

  /**
   * Reserves space for global variables and zero-initializes them.
   */
  public void writeGlobals() {
    for (String globalName : cfg.getGlobals()) {
      // Globals belong in bss (zero-initialized, writeable memory)
      ps.println(".bss");
      writeLabel(globalName);
      FieldDescriptor desc = cfg.getGlobal(globalName);
      switch (desc.getType()) {
       case INT:
       case BOOLEAN:
        // Initialize a single 64-bit variable to 0.
        ps.println("  .quad 0");
        break;
       case BOOLEAN_ARRAY:
       case INT_ARRAY:
        // Initialize the values in the array's memory range to zero.
        int size = desc.getLength();
        ps.println("  .rept " + size);
        ps.println("  .quad 0");
        ps.println("  .endr");
        // Save the read-only size of the array for array index checks.
        ps.println(".section .rodata");
        writeLabel(globalName + "_size");
        ps.println("  .quad " + size);
      }
    }
  }

  /**
   * Writes the blocks associated with each method to the assembly file.
   */
  public void writeMethods() {
    ps.println(".section .rodata");
    for (String methodName : cfg.getMethods()) {
      BasicBlockNode methodNode = (BasicBlockNode)cfg.getMethod(methodName);
      MethodDescriptor thisMethod = st.getMethod(methodName);

      List<BasicBlockNode> nodesToProcess = new ArrayList<BasicBlockNode>();
      Set<BasicBlockNode> processed = new HashSet<BasicBlockNode>();
      nodesToProcess.add(methodNode);

      while (!nodesToProcess.isEmpty()) {
        // Pop top element of queue to process.
        BasicBlockNode node = nodesToProcess.remove(0);
        // If we've seen this node already, we don't need to output it again.
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
        writeLabel(node.getId());

        // Process each statement.
        for (BasicStatement stmt : node.getStatements()) {
          processStatement(stmt, methodName, thisMethod);
        }

        // Generate an appropriate SourceLocation for the block trailer.
        SourceLocation loc = SourceLocation.getSourceLocationWithoutDetails();
        if (node.getLastStatement() != null &&
            node.getLastStatement().getNode() != null) {
          loc = node.getLastStatement().getNode().getSourceLoc();
        }

        // Write the block trailer. There are three cases to consider:
        // If the node is a branch, write the branch trailer.
        // Otherwise, if there's a next node, write an unconditional jump.
        // Finally, if there's no next node, insert an implicit return.
        if (node.isBranch()) {
          processBranch(node, branch, next, methodName, loc);
        } else if (next != null) {
          writeOp("jmp", next.getId(), loc);
        } else if (!(node.getLastStatement() instanceof OpStatement &&
            ((OpStatement)node.getLastStatement()).getOp() == AsmOp.RETURN)) {
          // Insert an implicit return.
          MethodDescriptor returnMethod = st.getMethod(methodName);
          generateMethodReturn(null, returnMethod, loc);
        }
      }
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
    // or a register; if this is the case, the conditional value lives in R11.
    String conditionalJump = "";
    Register resultRegister = null;

    if (node.getConditional() instanceof OpStatement) {
      OpStatement condition = (OpStatement)node.getConditional();
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
        ErrorReporting.reportError(new AsmException(
          loc, "Bad opcode for conditional"));
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
      writeOp(conditionalJump, branch.getId(), loc);
    } else {
      writeOp("movq", "$1", Register.R12, loc);
      writeOp("cmpq", Register.R12, resultRegister, loc);
      writeOp("je", branch.getId(), loc);
    }

    // Write the alternate unconditional jump to the next block since by this
    // point we've failed the conditional jump check.
    if (next != null) {
      writeOp("jmp", next.getId(), loc);
    } else {
      // Insert an implicit return, since there are no more basicblocks left
      // in this method to jump to.
      MethodDescriptor returnMethod = st.getMethod(methodName);
      generateMethodReturn(null, returnMethod, loc);
    }
  }

  /**
   * Writes a statement from the block to the ASM output stream.
   */
  protected void processStatement(BasicStatement stmt, String methodName,
      MethodDescriptor thisMethod) {
    // Save the location of the statement we are processing, if known.
    SourceLocation sl =
      SourceLocation.getSourceLocationWithoutDetails();
    if (stmt.getNode() != null) {
      sl = stmt.getNode().getSourceLoc();
    }

    if (stmt instanceof OpStatement) {
      processOpStatement((OpStatement)stmt, methodName, thisMethod, sl);
    } else if (stmt instanceof CallStatement) {
      generateCall((CallStatement)stmt, thisMethod);
    } else if (stmt instanceof NOPStatement) {
      // This is a nop; ignore it and continue onwards.
      return;
    } else {
      // We have an ArgumentStatement that made it to ASM generation.
      // These are supposed to be filtered out during CFG pass 2.
      ErrorReporting.reportError(new AsmException(
        sl, "Low level statement found at codegen time"));
      return;
    }
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
    if (op.getArg1() != null) {
      arg1 = prepareArgument(op.getArg1(), true, methodName, sl);
    }
    String arg2 = "<error>";
    if (op.getArg2() != null && op.getOp() != AsmOp.MOVE) {
      arg2 = prepareArgument(op.getArg2(), false, methodName, sl);
    }

    switch(op.getOp()) {
     case MOVE:
      arg2 = "" + Register.R11;
      writeOp("movq", arg1, arg2, sl);
      writeToArgument(op.getArg2(), methodName, sl);
      // Stop here because we don't need to move result again.
      return;
     case ADD:
      writeOp("addq", arg1, arg2, sl);
      break;
     case SUBTRACT:
      writeOp("subq", arg2, arg1, sl);
      // Subtract reverses the order of its arguments, so arg1 contains the
      // modified result.
      resultReg = Register.R10;
      break;
     case MULTIPLY:
      writeOp("imulq", arg1, arg2, sl);
      break;
     case DIVIDE:
     case MODULO:
      // Division needs to use RAX for the dividend, and overwrites RAX/RDX
      // to store its outputs.
      writeOp("movq", arg1, "%rax", sl);
      // Unfortunately, RDX may contain the first argument to the function.
      // We need to push it to memory to save it.
      writeOp("pushq", "%rdx", sl);
      writeOp("idivq", arg2, sl);
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
      writeOp("negq", arg1, sl);
      resultReg = Register.R10;
      break;
     case NOT:
      writeOp("xorq", "$1", arg1, sl);
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
      generateMethodHeader(thisMethod,
        ((ConstantArgument)op.getArg1()).getInt());
      return;
     default:
      ErrorReporting.reportError(new AsmException(
        sl, "Unknown opcode."));
      return;
    }
    if (op.getResult() != null) {
      writeOp("movq", resultReg,
        convertVariableLocation(op.getResult()), sl);
    } else {
      ps.println("  /* Ignoring result assignment of conditional. */");
    }
    if (op.getOp() == AsmOp.DIVIDE || op.getOp() == AsmOp.MODULO) {
      // Restore the register we displaced for division/modulo.
      writeOp("popq", "%rdx", sl);
    }
  }

  /**
   * Performs a boolean comparison of two arguments.
   * The arguments must have already been pulled out of memory.
   */
  protected void processBoolean(AsmOp op, String arg1, String arg2,
      SourceLocation sl) {
    writeOp("xorq", Register.RAX, Register.RAX, sl);
    writeOp("cmpq", arg2, arg1, sl);
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
    writeOp("movq", "$1", Register.R10, sl);
    writeOp(cmovOp, Register.R10, Register.RAX, sl);
  }

  /** Contains the registers used for argument passing in order. */
  public static Register[] argumentRegisters = {
    Register.RDI, // 1st arg
    Register.RSI, // 2nd arg
    Register.RDX, // 3rd arg
    Register.RCX, // 4th arg
    Register.R8, // 5th arg
    Register.R9, // 6th arg
  };

  /**
   * Generates the header for a method entry.
   * Requires the method descriptor and the number of locals to initialize.
   */
  protected void generateMethodHeader(MethodDescriptor desc,
                                      int numLocals) {
    SourceLocation sl = desc.getSourceLocation();
    writeOp("enter", "$" + numLocals, "$0", sl);
    for (int ii = -8; ii >= -(numLocals + 8); ii -= 8) {
      writeOp("movq", "$0", convertVariableLocation(
        new StackLocation(ii)), sl);
    }

    for (Register reg : desc.getUsedCalleeRegisters()) {
      writeOp("pushq", reg, sl); // Save registers used in method.
    }
    for (int ii = 0; ii < Math.min(desc.getParams().size(), 6); ii++) {
      desc.markRegisterUsed(argumentRegisters[ii]);
    }
    desc.markRegisterUsed(Register.R12);
  }

  /**
   * Displays the error message located at errmsgLabel, then immediately
   * exits. Used for error handling.
   * If R12 is set, it will be passed as the first format string argument.
   */
  protected void generateImmediateExit(String errmsgLabel) {
    SourceLocation sl = SourceLocation.getSourceLocationWithoutDetails();
    writeOp("xorq", Register.RAX, Register.RAX, sl);
    writeOp("movq", Register.R12, Register.RSI, sl);
    writeOp("movq", "$." + errmsgLabel, Register.RDI, sl);
    writeOp("call", "printf", sl);
    writeOp("xorq", Register.RAX, Register.RAX, sl);
    writeOp("xorq", Register.RDI, Register.RDI, sl);
    writeOp("call", "exit", sl);
  }

  /**
   * Generates the method trailer for returning from a method.
   * Detects if it's being asked to return without an argument and the
   * method is non-void; if so, writes an error handler that will be called
   * at runtime if we reach this ending by falling off the method end.
   */
  protected void generateMethodReturn(String arg1, MethodDescriptor desc,
                                      SourceLocation sl) {
    if (desc.getType() != DecafType.VOID && arg1 == null) {
      writeOp("movq", "$." + desc.getId() + "_name", Register.R12, sl);
      writeOp("jle", "nonvoid_noreturn_error_handler", sl);
      return;
    }
    if (arg1 != null) {
      // Save result in return register.
      writeOp("movq", arg1, Register.RAX, sl);
    } else {
      // Clear %rax to prevent confusion and non-zero exit codes since
      // decaf allows main() to be either void or int and system reads
      // the return value regardless of whether main is void.
      writeOp("xorq", Register.RAX, Register.RAX, sl);
    }

    // Restore callee-saved registers we used.
    List<Register> usedRegisters = desc.getUsedCalleeRegisters();
    Collections.reverse(usedRegisters);
    for (Register reg : usedRegisters) {
      writeOp("popq", reg, sl);
    }

    // Push old base pointer.
    writeOp("leave", sl);
    // Caller cleans up arguments.
    writeOp("ret", sl);
  }

  /**
   * Generates an outbound method call. Requires both the call statement
   * for the current call and also the current method.
   */
  protected void generateCall(CallStatement call,
      MethodDescriptor thisMethod) {
    SourceLocation sl = call.getNode().getSourceLoc();
    // Push caller-saved variables that we've used and need to keep.
    List<Register> usedRegisters = thisMethod.getUsedCallerRegisters();
    for (Register r : usedRegisters) {
      writeOp("pushq", r, sl);
    }

    // Push arguments.
    // First six go into registers, rest go on stack in right to left order
    List<Argument> args = call.getArgs();
    for (int ii = args.size() - 1; ii >= 0; ii--) {
      if (ii >= 6) {
        writeOp("pushq",
                prepareArgument(args.get(ii),true, thisMethod.getId(), sl),
                sl);
      } else {
        writeOp("movq",
                prepareArgument(args.get(ii), true, thisMethod.getId(), sl),
                argumentRegisters[ii].toString(), sl);
      }
    }

    // Empty %rax to cope with printf vararg issue
    writeOp("xorq", Register.RAX, Register.RAX, sl);

    // Now we're ready to make the call.
    // This automatically pushes the return address; callee removes return addr
    writeOp("call", call.getMethodName(), sl);

    // Pop arguments back off the stack.
    if (args.size() > 6) {
      writeOp("addq", "$" + (args.size() - 6) * 8, Register.RSP, sl);
    }

    // Pop the saved usedCallerRegisters back onto the stack.
    Collections.reverse(usedRegisters);
    for (Register r : usedRegisters) {
      writeOp("popq", r, sl);
    }

    // Move RAX into the correct save location.
    writeOp("movq", Register.RAX,
      convertVariableLocation(call.getResult()), sl);
  }

  /**
   * Loads a variable from memory so that it can be used in subsequent
   * computation. Saves to R10 if it's the first argument, R11 for the second.
   */
  protected String prepareArgument(Argument arg,
      boolean first, String methodName, SourceLocation sl) {
    Register tempStorage = first ? Register.R10 : Register.R11;
    switch (arg.getType()) {
     case CONST_BOOL:
      if (((ConstantArgument)arg).getBool()) {
        return "$1";
      } else {
        return "$0";
      }
     case CONST_INT:
      // The immediate values in decaf cannot exceed 32 bits, so we don't need
      // to mov-shl-mov-add, but if we had to deal with 64 bits, we'd do this.
      // We still need to load to registers since some ops only work on
      // registers and not on immediates.
      writeOp("movq",
        "$" + ((ConstantArgument)arg).getInt(), tempStorage, sl);
      break;
     case VARIABLE:
      writeOp("movq",
        convertVariableLocation(((VariableArgument)arg).getLoc()),
        tempStorage, sl);
      break;
     case ARRAY_VARIABLE:
      ArrayVariableArgument ava = (ArrayVariableArgument)arg;
      // Arrays can only be declared as globals in decaf
      assert(ava.getLoc().getLocationType() == LocationType.GLOBAL);

      // Prepare the symbol and index names. The index needs recursive
      // resolution since it might be a variable or another array.
      // Symbol will always be a global address.
      String symbol = "." + ava.getLoc().getSymbol();
      // The index will be a temporary register (either R10 or R11).
      // As it happens, this is also our return register, but that's okay.
      String index = prepareArgument(ava.getIndex(), first, methodName, sl);

      // Perform array bounds check. TODO(lizf): fix code duplication.
      writeOp("cmpq", index, symbol + "_size", sl);
      writeOp("movq", "$." + methodName + "_name", Register.R12, sl);
      writeOp("jle", "array_oob_error_handler", sl);

      // Use R12 to store the global name to access.
      writeOp("movq", "$" + symbol, Register.R12, sl);

      // Finally, perform the indirection to look up from memory+offset.
      writeOp("movq",
        "(" + Register.R12 + ", " + index + ", 8)",
        tempStorage, sl);
      break;
    }
    return "" + tempStorage;
  }

  /**
   * Saves a result to a variable or an array variable.
   * Results are always located in R11 for now.
   */
  protected void writeToArgument(Argument arg, String methodName,
      SourceLocation sl) {
    switch (arg.getType()) {
     case VARIABLE:
      writeOp("movq",
        Register.R11,
        convertVariableLocation(((VariableArgument)arg).getLoc()), sl);
      break;
     case ARRAY_VARIABLE:
      ArrayVariableArgument ava = (ArrayVariableArgument)arg;
      // Arrays can only be declared as globals in decaf
      assert(ava.getLoc().getLocationType() == LocationType.GLOBAL);

      // Prepare the symbol and index names. The index needs recursive
      // resolution since it might be a variable or another array.
      // Symbol will always be a global address.
      String symbol = "." + ava.getLoc().getSymbol();

      // The index will be an unused register (R10).
      // We don't want to use R11, which would clobber the result to return.
      String index = prepareArgument(ava.getIndex(), true, methodName, sl);

      // Perform array bounds check. TODO(lizf): fix code duplication.
      writeOp("cmpq", index, symbol + "_size", sl);
      writeOp("movq", "$." + methodName + "_name", Register.R12, sl);
      writeOp("jle", "array_oob_error_handler", sl);

      // Use R12 to store the global name to access.
      writeOp("movq", "$" + symbol, Register.R12, sl);

      // Finally, perform the indirection to save to memory+offset.
      writeOp("movq", Register.R11,
        "(" + Register.R12 + ", " + index + ", 8)", sl);
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

  // Methods below are utility methods to ease repetitive casting.
  protected void writeOp(String opcode,
                         SourceLocation loc) {
    ps.println("  " +
        opcode +
        getOriginalSource(loc));
  }

  protected void writeOp(String opcode,
      Register first_operand,
      SourceLocation loc) {
    writeOp(opcode, "" + first_operand, loc);
  }

  protected void writeOp(String opcode,
                         String first_operand,
                         SourceLocation loc) {
    ps.println("  " +
        opcode + " " +
        first_operand +
        getOriginalSource(loc));
  }

  protected void writeOp(String opcode,
      Register first_operand,
      Register second_operand,
      SourceLocation loc) {
    writeOp(opcode, "" + first_operand, "" + second_operand, loc);
  }
  protected void writeOp(String opcode,
      Register first_operand,
      String second_operand,
      SourceLocation loc) {
    writeOp(opcode, "" + first_operand, second_operand, loc);
  }
  protected void writeOp(String opcode,
      String first_operand,
      Register second_operand,
      SourceLocation loc) {
    writeOp(opcode, first_operand, "" + second_operand, loc);
  }

  /**
   * Writes an operation to the ASM output stream.
   */
  protected void writeOp(String opcode,
                         String first_operand,
                         String second_operand,
                         SourceLocation loc) {
    ps.println("  " +
      opcode + " " +
      first_operand + ", " +
      second_operand +
      getOriginalSource(loc));
  }

  /**
   * Writes a label to the ASM output stream.
   */
  protected void writeLabel(String label) {
    ps.println(label + ":");
  }

  /**
   * Attempts to pull the original source line corresponding to an ASM op.
   */
  protected static String getOriginalSource(SourceLocation loc) {
    if (loc.getLine() >= 0 && loc.getCol() >= 0 &&
        !loc.getFilename().equals(CLI.STDIN)) {
      try {
        BufferedReader reader = new BufferedReader(
          new FileReader(loc.getFilename()));
        int line = 0;
        String lineContents = "";
        while (line < loc.getLine()) {
          lineContents = reader.readLine();
          line++;
        }
        return " # " + lineContents.substring(0, loc.getCol()) + "@" +
          lineContents.substring(loc.getCol());
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
