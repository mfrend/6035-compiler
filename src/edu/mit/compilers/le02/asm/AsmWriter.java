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
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
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
          String arg1 = convertArgument(op.getArg1());
          String arg2 = convertArgument(op.getArg2());
          String result = convertVariableLocation(op.getResult());
          SourceLocation sl = stmt.getNode().getSourceLoc();
          switch(op.getOp()) {
           case MOVE:
             writeOp("mov", arg1, result, sl);
           case ADD:
           case SUBTRACT:
           case MULTIPLY:
           case DIVIDE:
           case MODULO:
           case UNARY_MINUS:
           case NOT:
           case EQUAL:
           case NOT_EQUAL:
           case LESS_THAN:
           case LESS_OR_EQUAL:
           case GREATER_THAN:
           case GREATER_OR_EQUAL:
           case RETURN:
           case METHOD_PREAMBLE:
             
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
    writeOp("push", "%eax", desc.getCode().getSourceLoc());
  }

  protected void generateCall(CallStatement call) {
    writeOp("call", "C_" + call.getMethod().getId(),
      call.getNode().getSourceLoc());
  }

  protected String convertArgument(Argument arg) {
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
