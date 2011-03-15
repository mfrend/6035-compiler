package edu.mit.compilers.le02.asm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.StringNode;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
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
  }

  public void writeStrings() {
    for (String name : cfg.getAllStringData()) {
      ps.println(name + ":");
      StringNode node = cfg.getStringData(name);
      // We want the explicitly escaped version.
      ps.println(".string " + node.toString());
    }
  }

  public void writeGlobals() {
    for (String globalName : cfg.getGlobals()) {
      ps.println(globalName + ":");
      cfg.getGlobal(globalName);
      ps.println();
    }
  }

  public void writeMethods() {
    for (String methodName : cfg.getMethods()) {
      BasicBlockNode node = (BasicBlockNode)cfg.getMethod(methodName);
      ps.println("# MethodNode: " + methodName);
      ps.println(methodName + ":");
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
            writeOp("movq", arg1, "" + resultReg, sl);
            break;
           case ADD:
            writeOp("addq", arg1, arg2, sl);
            break;
           case SUBTRACT:
            writeOp("subq", arg1, arg2, sl);
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
            MethodDescriptor returnMethod = st.getMethod(methodName);
            generateMethodReturn(arg1, returnMethod, sl);
            break;
           case ENTER:
             MethodDescriptor enterMethod = st.getMethod(methodName);
            generateMethodHeader(enterMethod);
            break;
           default:
            ErrorReporting.reportError(new AsmException(
              sl, "Unknown opcode."));
            continue;
          }
          writeOp("movq", "" + resultReg,
            convertVariableLocation(op.getResult()), sl);
          if (op.getOp() == AsmOp.DIVIDE || op.getOp() == AsmOp.MODULO) {
            writeOp("popq", "%rdx", sl);
          }
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

  protected void processBoolean(AsmOp op, String arg1, String arg2,
      SourceLocation sl) {
    writeOp("xorq", "" + Register.RAX, "" + Register.RAX, sl);
    writeOp("cmpq", arg1, arg2, sl);
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
    writeOp(cmovOp, "$1", "" + Register.RAX, sl);
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
    writeOp("enter", "$0", sl);
    
    // TODO: Allocate enough space for the method's locals.

    for (Register reg : desc.getUsedCalleeRegisters()) {
      writeOp("pushq", "" + reg, sl); // Save registers used in method.
    }
  }

  protected void generateMethodReturn(String arg1, MethodDescriptor desc,
                                      SourceLocation sl) {
    writeOp("movq", arg1, "%rax", sl); // Save result in return register

    List<Register> usedRegisters = desc.getUsedCalleeRegisters();
    Collections.reverse(usedRegisters);
    for (Register reg : usedRegisters) {
      writeOp("popq", "" + reg, sl); // Save registers used in method.
    }

    writeOp("leave", sl); // Push old base pointer.
    // Caller cleans up arguments.
    writeOp("ret", sl);
  }

  protected void generateCall(CallStatement call) {
    SourceLocation sl = call.getNode().getSourceLoc();
    // Push variables we need to save. for now, we can assume none need saving.
    // because we are not using registers at all, but this won't fly in future
    // We'd instead find the parent MethodDescriptor and use
    // getUsedCallerRegisters().

    // Empty %rax to cope with printf vararg issue
    writeOp("xorq", "" + Register.RAX, "" + Register.RAX, sl);
    // Now we're ready to make the call.
    // This automatically pushes the return address; callee removes return addr
    writeOp("call", call.getMethodName(), sl);

    // Pop the saved usedCallerRegisters back onto the stack. (not needed yet)

    // Move RAX into the correct save location.
    writeOp("movq", "" + Register.RAX,
      convertVariableLocation(call.getResult()), sl);
  }

  protected String prepareArgument(Argument arg,
      boolean first, SourceLocation sl) {
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
      // The immediate values in decaf cannot exceed 32 bits, so we are fine.
      writeOp("movq",
        "$" + ((ConstantArgument)arg).getInt(), "" + tempStorage, sl);
      break;
     case VARIABLE:
      writeOp("movq",
        convertVariableLocation(((VariableArgument)arg).getLoc()),
        "" + tempStorage, sl);
      break;
     case ARRAY_VARIABLE:
      ArrayVariableArgument ava = (ArrayVariableArgument)arg;
      // Arrays can only be declared as globals in decaf
      assert(ava.getLoc().getLocationType() == LocationType.GLOBAL);
      // Earlier steps must have preprocessed A[B[0]] => temp = B[0]; A[temp]
      Register index = tempStorage;
      prepareArgument(ava.getIndex(), first, sl);
      writeOp("movq",
        "(" + convertVariableLocation(ava.getLoc()) + "," + index + ", $8)",
        "" + tempStorage, sl);
      break;
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
    writeOp(opcode, "", "", loc);
  }

  protected void writeOp(String opcode,
                         String first_operand,
                         SourceLocation loc) {
    writeOp(opcode, first_operand, "", loc);
  }

  protected void writeOp(String opcode,
                         String first_operand,
                         String second_operand,
                         SourceLocation loc) {
    ps.println(
      opcode + " " +
      first_operand + " " +
      second_operand + " " +
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
