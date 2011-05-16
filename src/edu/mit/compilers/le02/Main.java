// Copyright (c) 2011 Liz Fong <lizfong@mit.edu>
// All rights reserved.

package edu.mit.compilers.le02;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.EnumSet;

import antlr.ANTLRException;
import antlr.ASTFactory;
import antlr.CharStreamException;
import antlr.DumpASTVisitor;
import antlr.Token;
import antlr.TokenStreamRecognitionException;
import antlr.debug.misc.ASTFrame;
import edu.mit.compilers.le02.asm.AsmFile;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.AstPrettyPrinter;
import edu.mit.compilers.le02.cfg.BasicBlockGraph;
import edu.mit.compilers.le02.cfg.CFGGenerator;
import edu.mit.compilers.le02.cfg.CFGVisualizer;
import edu.mit.compilers.le02.cfg.ControlFlowGraph;
import edu.mit.compilers.le02.grammar.DecafParser;
import edu.mit.compilers.le02.grammar.DecafParserTokenTypes;
import edu.mit.compilers.le02.grammar.DecafScanner;
import edu.mit.compilers.le02.grammar.DecafScannerTokenTypes;
import edu.mit.compilers.le02.grammar.LineNumberedAST;
import edu.mit.compilers.le02.grammar.ScanException;
import edu.mit.compilers.le02.ir.IrGenerator;
import edu.mit.compilers.le02.semanticchecks.MasterChecker;
import edu.mit.compilers.le02.stgenerator.SymbolTableGenerator;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;
import edu.mit.compilers.tools.CLI;
import edu.mit.compilers.tools.CLI.Action;

/**
 * Main class used to invoke subcomponents of the compiler.
 *
 * @author lizfong@mit.edu (Liz Fong)
 * @author mfrend@mit.edu (Maria Frendberg)
 */
public class Main {
  /** Enumerates all valid return codes. */
  public enum ReturnCode {
    SUCCESS(0), SCAN_FAILED(1), PARSE_FAILED(2), SEMANTICS_FAILED(3),
    CFG_FAILED(4), ASM_FAILED(5),
    FILE_NOT_FOUND(126), NO_SUCH_ACTION(127);

    private int numericCode;

    private ReturnCode(int code) {
      numericCode = code;
    }

    public int numericCode() {
      return numericCode;
    }
  };

  public enum Optimization {
    LOCAL_COMMON_SUBEXPR("lcse"),
    GLOBAL_COMMON_SUBEXPR("gcse"),
    COPY_PROPAGATION("cp"),
    DEAD_CODE("dc"),
    CONSECUTIVE_COPY("cc"),
    LOOP_ARRAY_BOUNDS_CHECKS("abc"),
    REGISTER_ALLOCATION("regalloc"),
    ASM_PEEPHOLE("asm_peephole"),
    ;
    private String flagName;

    private Optimization(String flag) {
      flagName = flag;
    }

    public String flagName() {
      return flagName;
    }
  }

  /**
   * Main entry point for compiler.
   */
  public static void main(String[] args) {
    // We should exit successfully unless something goes awry.
    ReturnCode retCode = ReturnCode.SUCCESS;
    // Default to reading from stdin unless we get a valid file input.
    InputStream inputStream = System.in;

    // Prepare the list of all optimization flags for the CLI utility.
    EnumSet<Optimization> enabledOpts = EnumSet.noneOf(Optimization.class);
    CLI.parse(args, enabledOpts);

    // If we have a valid file input, set up the input stream.
    if (CLI.infile != null) {
      try {
        inputStream = new FileInputStream(CLI.infile);
      } catch (IOException e) {
        // print the error:
        ErrorReporting.reportErrorCompat(e);
        System.exit(ReturnCode.FILE_NOT_FOUND.numericCode());
      }
    }

    switch (CLI.target) {
     case SCAN:
      if (!runScanner(inputStream) || !ErrorReporting.noErrors()) {
        retCode = ReturnCode.SCAN_FAILED;
      }
      break;
     case PARSE:
      if (!runParser(inputStream) || !ErrorReporting.noErrors()) {
        retCode = ReturnCode.PARSE_FAILED;
      }
      break;
     case INTER:
      if (!generateIR(inputStream) || !ErrorReporting.noErrors()) {
        retCode = ReturnCode.SEMANTICS_FAILED;
      }
      break;
     case CFG:
      if (!generateCFG(inputStream, enabledOpts) ||
          !ErrorReporting.noErrors()) {
        retCode = ReturnCode.CFG_FAILED;
      }
      break;
     case DEFAULT:
     case ASSEMBLY:
      if (!generateAsm(inputStream, enabledOpts) ||
          !ErrorReporting.noErrors()) {
        retCode = ReturnCode.ASM_FAILED;
      }
      break;
     default:
      retCode = ReturnCode.NO_SUCH_ACTION;
      ErrorReporting.reportErrorCompat(new NoSuchMethodException(
        "Action " + CLI.target + " not yet implemented."));
    }
    ErrorReporting.printErrors(System.err);
    System.exit(retCode.numericCode());
  }

  /**
   * Initializes a DecafScanner and sets debug and infile parameters.
   *
   * @param inputStream The file to be scanned
   * @return An initalized DecafScanner
   */
  protected static DecafScanner initializeScanner(InputStream inputStream) {
    DecafScanner scanner = new DecafScanner(new DataInputStream(inputStream));

    // If debug mode is set, enable tracing in the scanner.
    if (CLI.target == Action.SCAN) {
      scanner.setTrace(CLI.debug);
    }
    if (!CLI.compat) {
      scanner.setFilename(CLI.infile);
    }

    return scanner;
  }

  /**
   * Runs the scanner on an input and displays all tokens successfully parsed
   * from the input, along with any error messages.
   *
   * @param inputStream The stream to read input from.
   * @return true if scanner ran without errors, false if errors found.
   */
  protected static boolean runScanner(InputStream inputStream) {
    DecafScanner scanner = initializeScanner(inputStream);
    boolean success = true;

    Token token;
    boolean done = false;
    while (!done) {
      try {
        for (token = scanner.nextToken();
             token.getType() != DecafParserTokenTypes.EOF;
             token = scanner.nextToken()) {
          String type = "";
          String text = token.getText();

          switch (token.getType()) {
           case DecafScannerTokenTypes.ID:
            type = " IDENTIFIER";
            break;
           case DecafScannerTokenTypes.CHAR:
            type = " CHARLITERAL";
            break;
           case DecafScannerTokenTypes.STRING:
            type = " STRINGLITERAL";
            break;
           case DecafScannerTokenTypes.INT:
            type = " INTLITERAL";
            break;
           case DecafScannerTokenTypes.TK_false:
           case DecafScannerTokenTypes.TK_true:
            type = " BOOLEANLITERAL";
            break;
          }
          System.out.println(token.getLine() + type + " " + text);
        }
        done = true;
      } catch (ANTLRException e) {
        // Print the error and continue by discarding the invalid token.
        // We hope that this gets us onto the right track again.
        if (CLI.compat) {
          ErrorReporting.reportErrorCompat(e);
        } else {
          System.out.println(e);
          if (e instanceof TokenStreamRecognitionException) {
            ErrorReporting.reportError(new ScanException(
              (TokenStreamRecognitionException) e));
          } else {
            ErrorReporting.reportError(
              new ScanException(e.getMessage()));
          }
        }
        try {
          scanner.consume();
        } catch (CharStreamException cse) {
          if (CLI.compat) {
            ErrorReporting.reportErrorCompat(cse);
          } else {
            ErrorReporting.reportError(new ScanException(cse
                .getMessage()));
          }
        }
        success = false;
      }
    }
    return success;
  }

  /**
   * Initialize a DecafParser and set ASTFactory, Debug, and inputStream
   * params
   *
   * @param inputStream The file to be parsed
   * @return The initialized DecafParser
   */
  protected static DecafParser initializeParser(InputStream inputStream) {
    DecafScanner scanner = initializeScanner(inputStream);
    final DecafParser parser = new DecafParser(scanner);
    // Save the line/column numbers so we get meaningful parse data.
    ASTFactory factory = new ASTFactory();
    factory.setASTNodeClass(LineNumberedAST.class);
    parser.setASTFactory(factory);

    // The default instantiation is unaware of underlying filenames when
    // pretty-printing exceptions. Set the values appropriately.
    if (inputStream instanceof FileInputStream) {
      scanner.setFilename(CLI.infile);
      parser.setFilename(CLI.infile);
    }
    // If debug mode is set, enable tracing in the parser.
    if (CLI.target == Action.PARSE) {
      parser.setTrace(CLI.debug);
    }

    return parser;
  }

  /**
   * Runs the parser on an input and displays any error messages found while
   * parsing.
   *
   * @param inputStream The stream to read input from.
   * @return true if parser ran without errors, false if errors found.
   */
  protected static boolean runParser(InputStream inputStream) {
    boolean success = true;
    try {
      final DecafParser parser = initializeParser(inputStream);

      // Invoke the parser.
      parser.program();

      // If we are in debug mode, output the AST so it can be inspected.
      if (CLI.debug) {
        DumpASTVisitor dumper = new DumpASTVisitor();
        dumper.visit(parser.getAST());
        if (CLI.graphics) {
          Thread thread = new Thread() {
            @Override
            public void run() {
              ASTFrame frame =
                new ASTFrame(CLI.getInputFilename(), parser.getAST());
              frame.validate();
              frame.setVisible(true);
            }
          };

          thread.run();
          try {
            // TODO(lizfong): fix kludge.
            Thread.sleep(3 * 60 * 1000);
          } catch (InterruptedException ie) {
            // ignore
          }
        }
      }

      // If any errors were printed by the parser, note unsuccessful
      // parse.
      if (parser.getError()) {
        success = false;
      }
    } catch (ANTLRException e) {
      ErrorReporting.reportErrorCompat(e);
      success = false;
    }
    return success;
  }

  /**
   * Runs the parser on an input and displays any error messages found while
   * parsing.
   *
   * @param inputStream The stream to read input from.
   * @return true if parser ran without errors, false if errors found.
   */
  protected static boolean generateIR(InputStream inputStream) {
    boolean success = true;
    try {
      // Initialize and invoke the parser.
      DecafParser parser = initializeParser(inputStream);
      parser.program();

      ASTNode parent = IrGenerator.generateIR(parser.getAST());
      SymbolTableGenerator.generateSymbolTable(parent);
      MasterChecker.checkAll(parent);

      if (CLI.debug) {
        parent.accept(new AstPrettyPrinter());
      }
    } catch (ANTLRException e) {
      ErrorReporting.reportErrorCompat(e);
      success = false;
    }
    return success;
  }

  /**
   * Runs the parser on an input and displays any error messages found while
   * parsing.
   *
   * @param inputStream The stream to read input from.
   * @return true if parser ran without errors, false if errors found.
   */
  protected static boolean generateCFG(InputStream inputStream,
                                       EnumSet<Optimization> opts) {
    boolean success = true;
    try {
      // Initialize and invoke the parser.
      DecafParser parser = initializeParser(inputStream);
      parser.program();

      ASTNode parent = IrGenerator.generateIR(parser.getAST());
      SymbolTableGenerator.generateSymbolTable(parent);
      MasterChecker.checkAll(parent);

      if (!ErrorReporting.noErrors()) {
        return false;
      }

      ControlFlowGraph lowCfg = CFGGenerator.generateCFG(parent, opts);
      ControlFlowGraph cfg = BasicBlockGraph.makeBasicBlockGraph(lowCfg, opts);

      if (CLI.graphics) {
        if (CLI.debug) {
          CFGVisualizer.writeToDotFile(CLI.outfile, lowCfg, true);
        }
        else {
          CFGVisualizer.writeToDotFile(CLI.outfile, cfg, false);
        }
      } else {
        if (CLI.debug) {
          CFGVisualizer.writeCfgToFile(CLI.outfile, lowCfg, true);
        }
        else {
          CFGVisualizer.writeCfgToFile(CLI.outfile, cfg, false);
        }
      }

    } catch (ANTLRException e) {
      ErrorReporting.reportErrorCompat(e);
      success = false;
    }
    return success;
  }

  protected static boolean generateAsm(InputStream inputStream,
                                       EnumSet<Optimization> opts) {
    boolean success = true;
    try {
      // Initialize and invoke the parser.
      DecafParser parser = initializeParser(inputStream);
      parser.program();

      ASTNode parent = IrGenerator.generateIR(parser.getAST());
      SymbolTable st =
        SymbolTableGenerator.generateSymbolTable(parent).getSymbolTable();
      MasterChecker.checkAll(parent);

      if (!ErrorReporting.noErrors()) {
        return false;
      }

      ControlFlowGraph lowCfg = CFGGenerator.generateCFG(parent, opts);
      ControlFlowGraph cfg = BasicBlockGraph.makeBasicBlockGraph(lowCfg, opts);
      for (FieldDescriptor global : st.getFields()) {
        cfg.putGlobal("." + global.getId(), global);
      }
      AsmFile asm = new AsmFile(cfg, st, new PrintStream(CLI.outfile), opts);
      asm.write();
    } catch (ANTLRException e) {
      ErrorReporting.reportErrorCompat(e);
      success = false;
    } catch (IOException ioe) {
      ErrorReporting.reportErrorCompat(ioe);
      success = false;
    }
    return success;
  }
}
