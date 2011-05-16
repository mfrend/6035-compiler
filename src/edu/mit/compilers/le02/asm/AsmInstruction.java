package edu.mit.compilers.le02.asm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.tools.CLI;

/**
 * Represents an asm instruction to be used in output
 *
 * @author mfrend@mit.edu (Maria Frendberg)
 *
 */

public class AsmInstruction implements AsmObject {

  AsmOpCode opcode;
  SourceLocation loc;
  String first_operand, second_operand;
  
  private int id;
  private List<Integer> parents;
  private List<Integer> children;
  private int heuristic;

  public int getHeuristic() {
	return heuristic;
}

public void setHeuristic(int heuristic) {
	this.heuristic = heuristic;
}

public List<Integer> getChildren() {
	return children;
}

public AsmInstruction(AsmOpCode opCode, SourceLocation loc) {
    this(opCode, "", "", loc);
  }

  public AsmInstruction(AsmOpCode opCode, AsmArg first_operand,
      SourceLocation loc) {

    this(opCode, first_operand.toString(), "", loc);
  }

  public AsmInstruction(AsmOpCode opCode, AsmArg first_operand,
      AsmArg second_operand, SourceLocation loc) {
    this(opCode, first_operand != null ? first_operand.toString() : "",
         second_operand != null ? second_operand.toString() : "", loc);
  }

  private AsmInstruction(AsmOpCode opCode, String first_operand,
      String second_operand, SourceLocation loc) {

    this.opcode = opCode;
    this.first_operand = first_operand;
    this.second_operand = second_operand;
    this.loc = loc;
    this.heuristic = 0;
  }

  public List<String> getReads(){
	  ArrayList<String> reads = new ArrayList<String>();
	  switch(opcode){
	  case ADDQ:
	  case ADDL:
	  case CMPL:
	  case IDIVL:
	  case IMULL:
	  case SUBL:
		  reads.add(second_operand);
		  reads.add(first_operand);
		  break;
	  case MOVQ:
	  case MOVL:
	  case MOVSXD:
	  case SARL:
	  case SHLL:
		  
		  reads.add(first_operand);
		  break;
	  case CMOVEL:
	  case CMOVGEL:
	  case CMOVGL:
	  case CMOVLEL:
	  case CMOVLL:
	  case CMOVNEL:
		  reads.add("compare");
	  //case read0:
		  
	  }
	  
	  return reads;
	  
  }
  
  public List<String> getWrites(){
	  ArrayList<String> writes = new ArrayList<String>();
	  switch(opcode){
	  case ADDQ:
	  case ADDL:
	  case CMOVEL:
	  case CMOVGEL:
	  case CMOVGL:
	  case CMOVLEL:
	  case CMOVLL:
	  case CMOVNEL:
	  case IDIVL:
	  case IMULL:
	  case SUBL:
		  writes.add(second_operand);
		  break;
	  case SARL:
	  case SHLL:
		  writes.add(first_operand);
		  break;
	  case CMPL:
		  writes.add("compare");
		  break;
		  
	  }
	  
	  return writes;
  }
  
  public boolean moveable(){
	  switch(opcode){
	  case JE:
	  case JG:
	  case JGE:
	  case JL:
	  case JLE:
	  case JMP:
	  case JNE:
		  return false;
	  default:
		  return true;
	  }
  }
  
  public void addParent(int id){
	  parents.add(id);
  }
  
  public List<Integer> getParents() {
	return parents;
}

public void addChild(int id){
	  children.add(id);
  }
  
  public void setID(int it){
	  this.id = id;
  }
  
  public int getID(){
	  return id;
  }
  
  public String toString() {
    String result = "  " + opcode.toString();
    if (first_operand != "") {
      result += " " + first_operand;
      if (second_operand != "") {
        result += ", " + second_operand;
      }
    }
    return result + getOriginalSource(loc);
  }

  /**
   * Attempts to pull the original source line corresponding to an ASM op.
   */
  protected static String getOriginalSource(SourceLocation loc) {
    if (loc.getLine() >= 0 && loc.getCol() >= 0
        && !loc.getFilename().equals(CLI.STDIN)) {
      try {
        BufferedReader reader = new BufferedReader(
            new FileReader(loc.getFilename()));
        int line = 0;
        String lineContents = "";
        while (line < loc.getLine()) {
          lineContents = reader.readLine();
          line++;
        }
        return " # " + lineContents.substring(0, loc.getCol()) +
          "@"  + lineContents.substring(loc.getCol());
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
