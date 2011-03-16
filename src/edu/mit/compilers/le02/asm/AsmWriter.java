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

  public AsmWriter(ControlFlowGraph graph, SymbolTable table,
      PrintStream writer) {
    cfg = graph;
    ps = writer;
    st = table;
  }

  public void write() {
    ps.println("# Assembly output generated from " +
      CLI.getInputFilename());
    ps.println(".global main");

    writeStrings();
    writeGlobals();
    writeMethods();
    ps.println("error_handler:");
    generateImmediateExit("Unspecified runtime error",
      SourceLocation.getSourceLocationWithoutDetails());
  }

  public void writeStrings() {
    ps.println(".section .rodata");
    for (String name : cfg.getAllStringData()) {
      ps.println(name + ":");
      StringNode node = cfg.getStringData(name);
      // We want the explicitly escaped version.
      ps.println("  .string " + node.toString());
    }
    for (String methodName : cfg.getMethods()) {
      ps.println("." + methodName + "_name:");
      ps.println("  .string \"" + methodName + "\"");
    }
    ps.println(".aoob_msg:");
    ps.println(
      "  .string \"*** RUNTIME ERROR ***: Array out of Bounds access in method \\\"%s\\\"\"");
  }

  public void writeGlobals() {
    for (String globalName : cfg.getGlobals()) {
      ps.println(".bss");
      ps.println(globalName + ":");
      FieldDescriptor desc = cfg.getGlobal(globalName);
      switch (desc.getType()) {
       case INT:
       case BOOLEAN:
        ps.println("  .quad 0");
        break;
       case BOOLEAN_ARRAY:
       case INT_ARRAY:
        int size = desc.getLength();
        ps.println("  .rept " + size);
        ps.println("  .quad 0");
        ps.println("  .endr");
        ps.println(".section .rodata");
        ps.println(globalName + "_size:");
        ps.println("  .quad " + size);
      }
    }
  }

  public void writeMethods() {
    ps.println(".section .rodata");
    for (String methodName : cfg.getMethods()) {
      BasicBlockNode methodNode = (BasicBlockNode)cfg.getMethod(methodName);
      MethodDescriptor thisMethod = st.getMethod(methodName);

      List<BasicBlockNode> nodesToProcess = new ArrayList<BasicBlockNode>();
      Set<BasicBlockNode> processed = new HashSet<BasicBlockNode>();
      nodesToProcess.add(methodNode);

      while (!nodesToProcess.isEmpty()) {
        // Pop top element to work with
        BasicBlockNode node = nodesToProcess.remove(0);
        if (processed.contains(node)) {
          continue;
        }
        processed.add(node);
        BasicBlockNode branch = node.getBranchTarget();
        if (node.isBranch()) {
          nodesToProcess.add(branch);
        }
        BasicBlockNode next = node.getNext();
        if (next != null) {
          nodesToProcess.add(next);
        }

        ps.println(node.getId() + ":");
        for (BasicStatement stmt : node.getStatements()) {
          SourceLocation sl =
            SourceLocation.getSourceLocationWithoutDetails();
          if (stmt.getNode() != null) {
            sl = stmt.getNode().getSourceLoc();
          }

          if (stmt instanceof OpStatement) {
            // Default to saving results in R10; we can pull results from a
            // different register if the CPU spec mandates it.
            Register resultReg = Register.R11;
            OpStatement op = (OpStatement)stmt;

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
              // Continue here because we don't need to move result again
              continue;
             case ADD:
              writeOp("addq", arg1, arg2, sl);
              break;
             case SUBTRACT:
              writeOp("subq", arg2, arg1, sl);
              resultReg = Register.R10;
              break;
             case MULTIPLY:
              writeOp("imulq", arg1, arg2, sl);
              break;
              // Result in arg2 (R11)
             case DIVIDE:
             case MODULO:
              writeOp("movq", arg1, "%rax", sl);
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
              continue;
             case ENTER:
              generateMethodHeader(thisMethod,
                ((ConstantArgument)op.getArg1()).getInt());
              continue;
             default:
              ErrorReporting.reportError(new AsmException(
                sl, "Unknown opcode."));
              continue;
            }
            if (op.getResult() != null) {
              writeOp("movq", resultReg,
                convertVariableLocation(op.getResult()), sl);
            } else {
              ps.println("  /* Ignoring result assignment of conditional. */");
            }
            if (op.getOp() == AsmOp.DIVIDE || op.getOp() == AsmOp.MODULO) {
              writeOp("popq", "%rdx", sl);
            }
          } else if (stmt instanceof CallStatement) {
            generateCall((CallStatement)stmt, thisMethod);
          } else if (stmt instanceof NOPStatement) {
            // This is a nop; ignore it and continue onwards.
            continue;
          } else {
            // We have a DummyStatement or ArgumentStatement that made it to
            // ASM generation.
            ErrorReporting.reportError(new AsmException(
              sl, "Low level statement found at codegen time"));
            continue;
          }
        }
        SourceLocation loc = SourceLocation.getSourceLocationWithoutDetails();
        if (node.getLastStatement() != null &&
            node.getLastStatement().getNode() != null) {
          loc = node.getLastStatement().getNode().getSourceLoc();
        }
        if (node.isBranch()) {
          // The last operation carried out will be a conditional.
          // Find out what that conditional was.
          if (node.getConditional() instanceof OpStatement) {
            String conditionalJump = "";
            Register resultRegister = null;
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
              resultRegister = Register.R10;
              break;
             case MOVE:
              resultRegister = Register.R11;
              break;
             default:
               ErrorReporting.reportError(new AsmException(
                 loc, "Bad opcode for conditional"));
               continue;
            }
            if (conditionalJump != "") {
              writeOp(conditionalJump, branch.getId(), loc);
            } else {
              writeOp("movq", "$1", Register.R12, loc);
              writeOp("cmpq", Register.R12, resultRegister, loc);
              writeOp("je", branch.getId(), loc);
            }
          } else {
            // We just came back from a call.
            writeOp("movq", "$1", Register.R12, loc);
            writeOp("cmpq", Register.R12, Register.RAX, loc);
            writeOp("je", branch.getId(), loc);
          }
          if (next != null) {
            writeOp("jmp", next.getId(), loc);
          } else {
            // insert an implicit return.
            MethodDescriptor returnMethod = st.getMethod(methodName);
            generateMethodReturn(null, returnMethod, loc);
          }
        } else if (next != null) {
          writeOp("jmp", next.getId(), loc);
        } else if (!(node.getLastStatement() instanceof OpStatement &&
            ((OpStatement)node.getLastStatement()).getOp() == AsmOp.RETURN)) {
          // insert an implicit return.
          MethodDescriptor returnMethod = st.getMethod(methodName);
          generateMethodReturn(null, returnMethod, loc);
        }
      }
    }
  }

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

  public static Register[] argumentRegisters = {
    Register.RDI, // 1st arg
    Register.RSI, // 2nd arg
    Register.RDX, // 3rd arg
    Register.RCX, // 4th arg
    Register.R8, // 5th arg
    Register.R9, // 6th arg
  };

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

  protected void generateImmediateExit(String error, SourceLocation sl) {
    ps.println("/* " + error + " at " + sl + " */");
    writeOp("xorq", Register.RAX, Register.RAX, sl);
    writeOp("movq", Register.R12, Register.RSI, sl);
    writeOp("movq", "$.aoob_msg", Register.RDI, sl);
    writeOp("call", "printf", sl);
    writeOp("movq", "$1", Register.RAX, sl);
    writeOp("xorq", Register.RBX, Register.RBX, sl);
    writeOp("int", "$0x80", sl);
  }

  protected void generateMethodReturn(String arg1, MethodDescriptor desc,
                                      SourceLocation sl) {
    if (desc.getType() != DecafType.VOID && arg1 == null) {
      generateImmediateExit("No value returned from non-void function", sl);
      return;
    }
    if (arg1 != null) {
      writeOp("movq", arg1, Register.RAX, sl); // Save result in return register
    } else {
      // Clear %rax to prevent confusion and non-zero exit codes.
      writeOp("xorq", Register.RAX, Register.RAX, sl);
    }

    List<Register> usedRegisters = desc.getUsedCalleeRegisters();
    Collections.reverse(usedRegisters);
    for (Register reg : usedRegisters) {
      writeOp("popq", reg, sl); // Save registers used in method.
    }

    writeOp("leave", sl); // Push old base pointer.
    // Caller cleans up arguments.
    writeOp("ret", sl);
  }

  protected void generateCall(CallStatement call,
      MethodDescriptor thisMethod) {
    SourceLocation sl = call.getNode().getSourceLoc();
    // Push variables we need to save. for now, we can assume none need saving.
    // because we are not using registers at all, but this won't fly in future
    // We'd instead find the parent MethodDescriptor and use
    List<Register> usedRegisters = thisMethod.getUsedCallerRegisters();
    for (Register r : usedRegisters) {
      writeOp("pushq", r, sl);
    }

    // Push arguments
    // First six go into registers, rest go on stack in right to left order
    List<Argument> args = call.getArgs();
    for (int ii = args.size() - 1; ii >= 0; ii--) {
      if (ii >= 6) {
        writeOp("pushq", prepareArgument(args.get(ii), true, thisMethod.getId(), sl), sl);
      } else {
        writeOp("movq", prepareArgument(args.get(ii), true, thisMethod.getId(), sl),
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
      // The immediate values in decaf cannot exceed 32 bits, so we are fine.
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
      String symbol = "." + ava.getLoc().getSymbol();
      String index = prepareArgument(ava.getIndex(), first, methodName, sl);
      writeOp("cmpq", index, symbol + "_size", sl);
      writeOp("movq", "$." + methodName + "_name", Register.R12, sl);
      writeOp("jle", "error_handler", sl);
      writeOp("movq", "$" + symbol, Register.R12, sl);
      writeOp("movq",
        "(" + Register.R12 + ", " + index + ", 8)",
        tempStorage, sl);
      break;
    }
    return "" + tempStorage;
  }

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
      String symbol = "." + ava.getLoc().getSymbol();
      String index = prepareArgument(ava.getIndex(), true, methodName, sl);
      writeOp("cmpq", index, symbol + "_size", sl);
      writeOp("movq", "$." + methodName + "_name", Register.R12, sl);
      writeOp("jle", "error_handler", sl);
      writeOp("movq", "$" + symbol, Register.R12, sl);
      writeOp("movq", Register.R11,
        "(" + Register.R12 + ", " + index + ", 8)", sl);
      break;
    }
  }

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

  protected void writeOp(String opcode,
                         SourceLocation loc) {
    ps.println("  " +
        opcode +
        " # " + getOriginalSource(loc));
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
        " # " + getOriginalSource(loc));
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

  protected void writeOp(String opcode,
                         String first_operand,
                         String second_operand,
                         SourceLocation loc) {
    ps.println("  " +
      opcode + " " +
      first_operand + ", " +
      second_operand +
      " # " + getOriginalSource(loc));
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
        return lineContents.substring(0, loc.getCol()) + "@" +
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
