package edu.mit.compilers.le02.asm;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.Main.Optimization;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.ast.StringNode;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;
import edu.mit.compilers.tools.CLI;

/**
 * Represents the asm file which will be output
 *
 * @author lizfong@mit.edu (Liz Fong)
 * @author mfrend@mit.edu (Maria Frendberg)
 *
 */

public class AsmFile {
  private SymbolTable st;
  private ControlFlowGraph cfg;
  private PrintStream ps;

  private List<AsmObject> header = new ArrayList<AsmObject>();
  private List<AsmObject> strings = new ArrayList<AsmObject>();
  private List<AsmObject> globals = new ArrayList<AsmObject>();
  private List<AsmObject> methods = new ArrayList<AsmObject>();
  private List<AsmObject> errors = new ArrayList<AsmObject>();

  private AsmBasicBlock current;

  public AsmFile(ControlFlowGraph graph, SymbolTable table,
      PrintStream writer, EnumSet<Optimization> opts) {
    cfg = graph;
    ps = writer;
    st = table;

    writeHeader();
    writeStrings();
    writeGlobals();
    writeMethods(opts);
    writeErrors();
  }

  /**
   * Writes the necessary header information
   */
  private void writeHeader() {
    header.add(new AsmString("# Assembly output generated from " +
        CLI.getInputFilename()));
    header.add(new AsmString(".global main"));
  }

  /**
   * Writes the global string table, plus runtime error messages.
   * Additionally, saves the names of each method so errors can refer to which
   * method triggered them.
   */
  public void writeStrings() {
    // Strings are read-only data.
    strings.add(new AsmString(".section .rodata"));

    // Strings from the string table.
    for (String name : cfg.getAllStringData()) {
      strings.add(writeLabel(name));
      StringNode node = cfg.getStringData(name);
      // We want the explicitly escaped version.
      strings.add(new AsmString("  .string " + node.toString()));
    }

    // Method names.
    for (String methodName : cfg.getMethods()) {
      strings.add(writeLabel("." + methodName + "_name"));
      strings.add(new AsmString("  .string \"" + methodName + "\""));
    }

    // Error handler messages.
    strings.add(writeLabel(".nonvoid_noreturn_msg"));
    strings.add(new AsmString("  .string \"*** RUNTIME ERROR ***: " +
        "No return value from non-void method \\\"%s\\\"\\n\""));
  }

  /**
   * Reserves space for global variables and zero-initializes them.
   */
  public void writeGlobals() {
    for (String globalName : cfg.getGlobals()) {
      // Globals belong in bss (zero-initialized, writeable memory)
      globals.add(new AsmString(".bss"));
      globals.add(writeLabel(globalName));
      FieldDescriptor desc = cfg.getGlobal(globalName);
      switch (desc.getType()) {
      case INT:
      case BOOLEAN:
        // Initialize a single 64-bit variable to 0.
        globals.add(new AsmString("  .quad 0"));
        break;
      case BOOLEAN_ARRAY:
      case INT_ARRAY:
        // Initialize the values in the array's memory range to zero.
        int size = desc.getLength();
        globals.add(new AsmString("  .rept " + size));
        globals.add(new AsmString("  .quad 0"));
        globals.add(new AsmString("  .endr"));
      }
    }
  }

  /**
   * Writes the blocks associated with each method to the assembly file.
   */
  public void writeMethods(EnumSet<Optimization> opts) {
    methods.add(new AsmString(".section .rodata"));
    for (String methodName : cfg.getMethods()) {
      BasicBlockNode methodNode =
        (BasicBlockNode) cfg.getMethod(methodName);
      MethodDescriptor thisMethod = st.getMethod(methodName);

      current = new AsmBasicBlock(
        methodName, methodNode, thisMethod, st, opts);
      if (opts.contains(Optimization.ASM_PEEPHOLE)) {
        current.peepholeInstructions();
      }
      //current.reorderInstructions();
      methods.add(current);
    }
  }

  /**
   * Writes the necessary error information
   */
  private void writeErrors() {
    errors.add(writeLabel("nonvoid_noreturn_error_handler"));
    generateImmediateExit("nonvoid_noreturn_msg");
  }

  /**
   * Displays the error message located at errmsgLabel, then immediately
   * exits. Used for error handling. If R12 is set, it will be passed as the
   * first format string argument.
   */
  protected void generateImmediateExit(String errmsgLabel) {
    SourceLocation sl = SourceLocation.getSourceLocationWithoutDetails();
    errors.add(new AsmInstruction(
        AsmOpCode.XORQ, Register.RAX, Register.RAX, sl));
    errors.add(new AsmInstruction(
        AsmOpCode.MOVSXD, Register.R12D, Register.RSI, sl));
    errors.add(new AsmInstruction(
        AsmOpCode.MOVQ, new StringAsmArg("$." + errmsgLabel),
        Register.RDI, sl));
    errors.add(new AsmInstruction(
        AsmOpCode.CALL,new StringAsmArg("printf"), sl));
    errors.add(new AsmInstruction(
        AsmOpCode.XORQ, Register.RAX, Register.RAX, sl));
    errors.add(new AsmInstruction(
        AsmOpCode.XORQ, Register.RDI, Register.RDI, sl));
    errors.add(new AsmInstruction(
        AsmOpCode.CALL, new StringAsmArg("exit"), sl));
  }

  /**
   * Writes a label to the ASM output stream.
   */
  protected static AsmString writeLabel(String label) {
    return new AsmString(label + ":");
  }

  /**
   * Write the AsmFile to file
   */
  public void write() {
    printHeader();
    printStrings();
    printGlobals();
    printMethods();
    printErrors();

  }

  private void printHeader() {
    for (AsmObject s : header) {
      ps.println(s.toString());
    }
  }

  private void printStrings() {
    for (AsmObject s : strings) {
      ps.println(s.toString());
    }
  }

  private void printGlobals() {
    for (AsmObject s : globals) {
      ps.println(s.toString());
    }
  }

  private void printMethods() {
    for (AsmObject s : methods) {
      if (s instanceof AsmBasicBlock){
        AsmBasicBlock method = (AsmBasicBlock)s;
        for (int ii = 0; ii < method.getBlock().size(); ii++) {
          AsmObject obj = method.getBlock().get(ii);
          if (obj instanceof AsmInstruction) {
            AsmInstruction inst = (AsmInstruction)obj;
            if (inst.opcode == AsmOpCode.JMP &&
                (ii + 1) < method.getBlock().size()) {
              AsmObject next = method.getBlock().get(ii + 1);
              String label = next.toString();
              if (label.equals(inst.first_operand + ":")) {
                ps.println("  # Falling to " + inst.first_operand + ":");
                continue;
              }
            }
          }
          ps.println(obj.toString());
        }
      } else {
        ps.println(s.toString());
      }
    }
  }

  private void printErrors() {
    for (AsmObject s : errors) {
      ps.println(s.toString());
    }
  }

}
