package edu.mit.compilers.le02.asm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.MethodDeclNode;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.SyscallStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.cfg.Argument.ArgType;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.tools.CLI;

public class AsmWriter {
  private ControlFlowGraph cfg;
  private PrintStream ps;

  public AsmWriter(ControlFlowGraph graph, PrintStream writer) {
    cfg = graph;
    ps = writer;
  }

  public void write(PrintStream writer) {
    writer.println("# Assembly output generated from " +
      CLI.getInputFilename());
    writer.println(".global main");

    Iterator<Entry<String, BasicBlockNode>> blockIter = cfg.getBasicBlocks();
    while (blockIter.hasNext()) {
      Entry<String, BasicBlockNode> entry = blockIter.next();
      String blockName = entry.getKey();
      BasicBlockNode node = entry.getValue();
      writer.println("# BlockNode: " + blockName);
      writer.println(blockName + ":");
      for (BasicStatement stmt : node.getStatements()) {
        if (stmt instanceof OpStatement) {
          // Default to saving results in R10; we can pull results from a
          // different register if the CPU spec mandates it.
          Register resultReg = Register.R11;
          OpStatement op = (OpStatement)stmt;
          SourceLocation sl = stmt.getNode().getSourceLoc();

          // prepareArgument loads an argument from memory into R10 or R11
          // and returns the register it stored the argument in.
          // If the argument was a register to begin with, 
          String arg1 = prepareArgument(op.getArg1(), true, sl);
          String arg2 = prepareArgument(op.getArg2(), false, sl);

          switch(op.getOp()) {
           case MOVE:
            writeOp("mov", arg1, "" + resultReg, sl);
           case ADD:
            writeOp("add", arg1, arg2, sl);
           case SUBTRACT:
            writeOp("sub", arg1, arg2, sl);
           case MULTIPLY:
            writeOp("imul", arg1, arg2, sl);
            // Result in arg2 (R11)
           case DIVIDE:
           case MODULO:
            writeOp("mov", arg1, "%rax", sl);
            writeOp("idiv", arg2, sl);
            if (op.getOp() == AsmOp.DIVIDE) {
              // RDX is fixed to hold the quotient.
              resultReg = Register.RAX;
            } else {
              // RDX is fixed to hold the remainder.
              resultReg = Register.RDX;
            }
           case UNARY_MINUS:
            writeOp("neg", arg1, sl);
            resultReg = Register.R10;
           case NOT:
            writeOp("xor", "$1", arg1, sl);
            resultReg = Register.R10;
           case EQUAL:
           case NOT_EQUAL:
           case LESS_THAN:
           case LESS_OR_EQUAL:
           case GREATER_THAN:
           case GREATER_OR_EQUAL:
            writeOp("xor", "" + Register.RAX, "" + Register.RAX, sl);
            writeOp("cmp", arg1, arg2, sl);
            String cmovOp = "";
            switch (op.getOp()) {
             case EQUAL:
              cmovOp = "cmove";
              break;
             case NOT_EQUAL:
              cmovOp = "cmovne";
              break;
             case LESS_THAN:
              cmovOp = "cmovl";
              break;
             case LESS_OR_EQUAL:
              cmovOp = "cmovle";
              break;
             case GREATER_THAN:
              cmovOp = "cmovg";
              break;
             case GREATER_OR_EQUAL:
              cmovOp = "cmovge";
              break;
            }
            writeOp(cmovOp, "$1", "" + Register.RAX, sl);
            resultReg = Register.RAX;
           case RETURN:
            // This is categorically wrong. the return statement needs to
            // provide at least the name of, if not the descriptor for,
            // the method we're returning from; otherwise we don't know
            // how much to pop back off.
            MethodDeclNode method = (MethodDeclNode)stmt.getNode().getParent();
            generateMethodReturn(arg1, method.getDescriptor(), sl);
           case METHOD_PREAMBLE:
            MethodDeclNode decl = (MethodDeclNode)stmt.getNode().getParent();
            generateMethodHeader(decl.getDescriptor());
            break;
           default:
            ErrorReporting.reportError(new AsmException(
              sl, "Unknown opcode."));
            continue;
          }
          writeOp("mov", "" + resultReg, convertVariableLocation(op.getResult()), sl);
        } else if (stmt instanceof CallStatement) {
          generateCall((CallStatement)stmt);
        } else {
          // We have an UnexpandedStatement that made it to ASM generation.
          ErrorReporting.reportError(new AsmException(
            stmt.getNode().getSourceLoc(),
            "UnexpandedStatement found at codegen time."));
        }
      }
    }
  }

  public static Register[] argumentRegisters = {
    Register.RDI, // 1st arg
    Register.RSI, // 2nd arg
    Register.RDX, // 3rd arg
    Register.RCX, // 4th arg
    Register.R8, // 5th arg
    Register.R9, // 6th arg
  };

  protected void generateMethodHeader(MethodDescriptor desc) {
    SourceLocation sl = desc.getSourceLocation();
    writeOp("push", "%rbp", sl); // Push old base pointer.
    writeOp("mov", "%rsp", "%rbp", sl); // Set new base pointer.
    // Pop any arguments from registers onto the stack.
    
    int numRegisterArguments = Math.min(desc.getParams().size(), 6);
    for (int ii = 0; ii < numRegisterArguments; ii++) {
      writeOp("push", "" + argumentRegisters[ii], sl);
    }
    for (Register reg : desc.getUsedCalleeRegisters()) {
      writeOp("push", "" + reg, sl); // Save registers used in method.
    }
    // TODO: Allocate enough space for the method's locals.
  }

  protected void generateMethodReturn(String arg1, MethodDescriptor desc,
                                      SourceLocation sl) {
    writeOp("mov", arg1, "%rax", sl); // Save result in return register
    // TODO: Deallocate enough to remove all the locals we allocated.
    List<Register> usedRegisters = desc.getUsedCalleeRegisters();
    Collections.reverse(usedRegisters);
    for (Register reg : usedRegisters) {
      writeOp("pop", "" + reg, sl); // Save registers used in method.
    }
    // Take everything off the stack. This automatically removes the
    // arguments we pushed into place.
    // This could also just be expressed with the leave opcode;
    writeOp("mov", "%rbp", "%rsp", sl);
    writeOp("pop", "%rbp", sl); // Push old base pointer.
    // Caller cleans up arguments.
    writeOp("ret", sl);
  }

  protected void generateCall(CallStatement call) {
    SourceLocation sl = call.getNode().getSourceLoc();
    // Push variables we need to save. for now, we can assume none need saving.
    // because we are not using registers at all, but this won't fly in future
    // We'd instead find the parent MethodDescriptor and use
    // getUsedCallerRegisters().

    // Push arguments
    // First six go into registers, rest go on stack in right to left order
    List<Argument> args = call.getArgs();
    for (int ii = args.size() - 1; ii > 0; ii--) {
      if (ii > 6) {
        writeOp("push", prepareArgument(args.get(ii), true, sl), sl);
      } else {
        writeOp("mov", prepareArgument(args.get(ii), true, sl),
                argumentRegisters[ii].toString(), sl);
      }
    }

    // Now we're ready to make the call.
    // This needs to be genericized with simple "call.getMethodName()" to cover
    // both cases of syscalls and method calls which we now treat
    // interchangeably.
    // This automatically pushes the return address; callee removes return addr
    writeOp("call", call.getMethod().getId(), sl);

    // Clean up the call arguments pushed onto stack.
    if (args.size() > 6) {
      int argsToPop = (args.size() - 6);
      writeOp("add", "$-" + (argsToPop * 8), "" + Register.RSP, sl);
    }

    // Pop the saved usedCallerRegisters back onto the stack. (not needed yet)

    // Move RAX into the correct save location.
    writeOp("mov", "" + Register.RAX, convertVariableLocation(call.getResult()), sl);
  }

  protected String prepareArgument(Argument arg, boolean first, SourceLocation sl) {
    assert(arg != null);
    Register tempStorage = first ? Register.R10 : Register.R11;
    switch (arg.getType()) {
     case CONST_BOOL:
      if (((ConstantArgument)arg).getBool()) {
        return "$1";
      } else {
        return "$0";
      }
     case CONST_INT:
      // It's possible for an immediate value to have >32 bits. For now,
      // store the immediate value to a 64-bit register to be sure we don't
      // truncate bits by accident when doing IMUL, etc.
      writeOp("mov", "$" + ((ConstantArgument)arg).getInt(), "" + tempStorage, sl);
      break;
     case ARRAY_VARIABLE:
      ArrayVariableArgument ava = (ArrayVariableArgument)arg;
      // Arrays can only be declared as globals in decaf
      assert(ava.getLoc().getLocationType() == LocationType.GLOBAL);
      // Earlier steps must have preprocessed A[B[0]] => temp = B[0]; A[temp]
      Register index = tempStorage;
      if (ava.getIndex().getType() == ArgType.CONST_INT) {
        
      } else if (ava.getIndex().getType() == ArgType.VARIABLE) {
        writeOp("mov", convertVariableLocation(((VariableArgument)arg).getLoc()), "" + index, sl);
      } else {
        ErrorReporting.reportError(new AsmException(sl,
          "Attempted array access with invalid index."));
      }
      writeOp("mov", convertVariableLocation(ava.getLoc()) + "[" + index + "]", "" + tempStorage, sl);
      break;
     case VARIABLE:
      writeOp("mov", convertVariableLocation(((VariableArgument)arg).getLoc()), "" + tempStorage, sl);
    }
    return "" + tempStorage;
  }

  protected String convertVariableLocation(VariableLocation loc) {
    switch (loc.getLocationType()) {
     case GLOBAL:
      return loc.getSymbol();
     case REGISTER:
      return "" + loc.getRegister();
     case STACK:
      return loc.getOffset() + "(%rbp)";
    }
    return "";
  }

  protected void writeOp(String opcode,
                         SourceLocation loc) {
    writeOp(opcode, "", "", "", loc);
  }

  protected void writeOp(String opcode,
                         String first_operand,
                         SourceLocation loc) {
    writeOp(opcode, first_operand, "", "", loc);
  }

  protected void writeOp(String opcode,
                         String first_operand,
                         String output_operand,
                         SourceLocation loc) {
    writeOp(opcode, first_operand, "", output_operand, loc);
  }

  protected void writeOp(String opcode,
                         String first_operand,
                         String second_operand,
                         String output_operand,
                         SourceLocation loc) {
    ps.println(
      opcode + " " +
      first_operand + " " +
      second_operand + " " +
      output_operand +
      "# " + getOriginalSource(loc));
  }

  protected void writeLabel(String label) {
    ps.println(label + ":");
  }

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
        int end_of_token = lineContents.indexOf(' ', loc.getCol());
        return lineContents.substring(0, loc.getCol()) + "{" +
          lineContents.substring(loc.getCol(), end_of_token) + "}" +
          lineContents.substring(end_of_token);
      } catch (IOException ioe) {
        return "";
      }
    } else {
      return "";
    }
  }
}
