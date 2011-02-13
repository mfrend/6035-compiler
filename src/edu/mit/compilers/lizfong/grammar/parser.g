header { package edu.mit.compilers.lizfong.grammar; }

options
{
  mangleLiteralPrefix = "TK_";
  language = "Java";
}

class DecafParser extends Parser;
options
{
  importVocab = DecafScanner;
  k =3 ;
  buildAST = true;
}

{
  // Do our own reporting of errors so the parser can return a non-zero status
  // if any errors are detected.
  // TODO(lizfong): don't use native error reporting, and instead collect the
  // errors into an ArrayList so they can be pretty-printed along the lines of
  // the clang/llvm frontend or
  // http://www.milk.com/kodebase/antlr-tutorial/ErrorFormatter.java

  /** Reports if any errors were reported during parse. */ 
  private boolean error;

  @Override
  public void reportError (RecognitionException ex) {
    super.reportError(ex);
    error = true;
  }
  @Override
  public void reportError (String s) {
    super.reportError(s);
    error = true;
  }
  public boolean getError () {
    return error;
  }

  // Selectively turns on debug mode.

  /** Whether to display debug information. */
  private boolean trace = false;

  public void setTrace(boolean shouldTrace) {
    trace = shouldTrace;
  }
  @Override
  public void traceIn(String rname) throws TokenStreamException {
    if (trace) {
      super.traceIn(rname);
    }
  }
  @Override
  public void traceOut(String rname) throws TokenStreamException {
    if (trace) {
      super.traceOut(rname);
    }
  }
}

// Primitives
bool: TK_true | TK_false;
literal: INT | CHAR | bool;
type: TK_int | TK_boolean;
assign_op: ASSIGN | INC_ASSIGN | DEC_ASSIGN;
location: ID ( // Deliberately empty
               | LSQUARE expr RSQUARE);

// Declarations
program: TK_class ID LCURLY (field_decl)* (method_decl)* RCURLY EOF;
field_decl:
  type (ID ( // Deliberately empty
             | LSQUARE INT RSQUARE))
       (COMMA (ID ( // Deliberately empty
                    | LSQUARE INT RSQUARE)))* SEMICOLON;
method_decl:
  (type | TK_void) ID LPAREN (
    // Deliberately empty
    | type ID (COMMA type ID)*) RPAREN block;
var_decl: type ID (COMMA ID)* SEMICOLON;

// Control flows.
block: LCURLY (var_decl)* (statement)* RCURLY;
statement:
  location assign_op expr SEMICOLON |
  method_call SEMICOLON |
  TK_if LPAREN expr RPAREN block ( // Deliberately empty
                                   | TK_else block) |
  TK_for ID ASSIGN expr COMMA expr block |
  TK_return ( // Deliberately empty
              | expr) SEMICOLON |
  TK_break SEMICOLON |
  TK_continue SEMICOLON |
  block
;

// Method calls.
method_name: ID;
callout_arg: expr | STRING;
method_call:
  method_name LPAREN ( // Deliberately empty
                       | expr (COMMA expr)*) RPAREN |
  TK_callout LPAREN STRING (COMMA callout_arg)* RPAREN
;

// Rewriting the grammar for expression evaluation to not cascade left,
// and simultaneously ensuring order of operations is observed.
// Tier -1: ||
expr: term_zero expr_prime;
expr_prime: (LOGICAL_OR term_zero expr_prime |
             // Deliberately empty
            );
// Tier 0: &&
term_zero: term_one term_zero_prime;
term_zero_prime: (LOGICAL_AND term_one term_zero_prime |
                  // Deliberately empty
                 );
// Tier 1: ==, !=
term_one: term_two term_one_prime;
term_one_prime: (EQUALS term_two term_one_prime |
                 NOT_EQUALS term_two term_one_prime |
                 // Deliberately empty
                );
// Tier 2: <, >, <=, >=
term_two: term_three term_two_prime;
term_two_prime: (LT term_three term_two_prime |
                 GT term_three term_two_prime |
                 LE term_three term_two_prime |
                 GE term_three term_two_prime |
                 // Deliberately empty
                );
// Tier 3: +, -
term_three: term_four term_three_prime;
term_three_prime: (PLUS term_four term_three_prime |
                   MINUS term_four term_three_prime|
                   // Deliberately empty
                  );
// Tier 4: *, /, %
term_four: term_five term_four_prime;
term_four_prime: (TIMES term_five term_four_prime |
                  DIVIDE term_five term_four_prime |
                  MODULO term_five term_four_prime |
                  // Deliberately empty
                 );
// Tier 5: !
term_five: NOT term_five | term_six;
// Tier 6: urnary -
term_six: MINUS term_six | term_final;
// Tier 7: base expressions and parenthesized subexpressions.
term_final: location | method_call | literal | LPAREN expr RPAREN;

