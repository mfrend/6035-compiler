package edu.mit.compilers.le02.asm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map.Entry;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.MethodDeclNode;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.cfg.OpStatement;
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
        if (stmt instanceof CallStatement) {
          generateCall((CallStatement)stmt);
        } else if (stmt instanceof OpStatement) {
          OpStatement op = (OpStatement)stmt;
          String arg1 = prepareArgument(op.getArg1());
          String arg2 = prepareArgument(op.getArg2());
          String result = convertVariableLocation(op.getResult());
          SourceLocation sl = stmt.getNode().getSourceLoc();
          switch(op.getOp()) {
           case MOVE:
            writeOp("mov", arg1, result, sl);
           case ADD:
            writeOp("add", arg1, arg2, result, sl);
           case SUBTRACT:
            writeOp("sub", arg1, arg2, result, sl);
           case MULTIPLY:
            writeOp("imul", arg1, arg2, result, sl);
           case DIVIDE:
            writeOp("idiv", arg1, arg2, result, sl);
           case MODULO:
            writeOp("mod", arg1, arg2, result, sl);
           case UNARY_MINUS:
            writeOp("add", arg1, arg2, result, sl);
           case NOT:
            writeOp("add", arg1, arg2, result, sl);
           case EQUAL:
            writeOp("add", arg1, arg2, result, sl);
           case NOT_EQUAL:
            writeOp("add", arg1, arg2, result, sl);
           case LESS_THAN:
            writeOp("add", arg1, arg2, result, sl);
           case LESS_OR_EQUAL:
            writeOp("add", arg1, arg2, result, sl);
           case GREATER_THAN:
            writeOp("add", arg1, arg2, result, sl);
           case GREATER_OR_EQUAL:
            writeOp("add", arg1, arg2, result, sl);
           case RETURN:
            writeOp("add", arg1, arg2, result, sl);
           case METHOD_PREAMBLE:
            MethodDeclNode decl = (MethodDeclNode)stmt.getNode().getParent();
            generateCallHeader(decl.getDescriptor());
            break;
           default:
            ErrorReporting.reportError(new AsmException(
              sl, "Unknown opcode."));
          }
        } else {
          // We have an UnexpandedStatement that made it to ASM generation.
          ErrorReporting.reportError(new AsmException(
            stmt.getNode().getSourceLoc(),
            "UnexpandedStatement found at codegen time."));
        }
      }
    }
  }

  protected void generateCallHeader(MethodDescriptor desc) {
    SourceLocation sl = desc.getSourceLocation();
    writeOp("push", "%rbp", sl); // Push old base pointer
    writeOp("mov", "%rsp", "%rbp", sl); // Set new base pointer.
    for (Register reg : desc.getUsedRegisters()) {
      writeOp("push", "%rax", sl); // Save rbx
    }
  }

  protected void generateCall(CallStatement call) {
    writeOp("call", call.getMethod().getId(),
      call.getNode().getSourceLoc());
  }

  protected String prepareArgument(Argument arg) {
    assert(arg != null);
    switch (arg.getType()) {
     case CONST_BOOL:
     if (((ConstantArgument)arg).getBool()) {
       return "1";
     } else {
       return "0";
     }
     case CONST_INT:
      return "" + ((ConstantArgument)arg).getInt();
     case ARRAY_VARIABLE:
     case VARIABLE:
      
    }
    return "";
  }

  protected String convertVariableLocation(VariableLocation loc) {
    return "";
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
