package edu.mit.compilers.le02.asm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.RegisterLocation;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.Main.Optimization;
import edu.mit.compilers.le02.RegisterLocation.Register;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.cfg.ArgReassignStatement;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.HaltStatement;
import edu.mit.compilers.le02.cfg.NOPStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.cfg.Argument.ArgType;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;
import edu.mit.compilers.tools.CLI;

/**
 * Represents the asm instructions corresponding to a BasicBlock.
 * AsmInstructions are generated from the inputed BasicBlock
 *
 * @author lizfong@mit.edu (Liz Fong)
 * @author mfrend@mit.edu (Maria Frendberg)
 * @author dkoh@mit.edu (David Koh)
 *
 */

public class AsmBasicBlock implements AsmObject {
  private String methodName;
  private BasicBlockNode methodNode;
  private MethodDescriptor thisMethod;
  private SymbolTable st;

  private List<AsmObject> instructions;
  private EnumSet<Optimization> opts;

  public AsmBasicBlock(String methodName, BasicBlockNode methodNode,
      MethodDescriptor thisMethod, SymbolTable st,
      EnumSet<Optimization> opts) {
    instructions = new ArrayList<AsmObject>();

    this.methodName = methodName;
    this.methodNode = methodNode;
    this.thisMethod = thisMethod;
    this.st = st;
    this.opts = opts;

    processBlock();
  }

  public void addInstruction(AsmObject instruction) {
    instructions.add(instruction);
  }

  public void reorderInstructions() {
    // TODO @mfrend Finish Method and add optimization check
  }

  /**
   * Peepholes away instructions we don't want.
   * @author Liz Fong (lizfong@mit.edu)
   */
  public void peepholeInstructions() {
    Iterator<AsmObject> it = instructions.iterator();
    while (it.hasNext()) {
      AsmObject obj = it.next();
      if (obj instanceof AsmInstruction) {
        AsmInstruction inst = (AsmInstruction)obj;
        switch (inst.opcode) {
         case MOVL:
         case MOVQ:
          if (inst.first_operand.equals(inst.second_operand)) {
            it.remove();
          }
          break;
         case LEAL:
          if (inst.first_operand.startsWith("$")) {
            int factor = Integer.parseInt(inst.first_operand.substring(1));
            int leaMultiplier = -1;
            boolean add = false;
            switch (factor) {
             case 1:
              leaMultiplier = 1;
              add = false;
              break;
             case 2:
              leaMultiplier = 2;
              add = false;
              break;
             case 3:
              leaMultiplier = 2;
              add = true;
              break;
             case 4:
              leaMultiplier = 4;
              add = false;
              break;
             case 5:
              leaMultiplier = 4;
              add = true;
              break;
             case 8:
              leaMultiplier = 8;
              add = false;
              break;
             case 9:
              leaMultiplier = 8;
              add = true;
              break;
             default:
              continue;
            }
            inst.first_operand = "(";
            if (add) {
              inst.first_operand += inst.second_operand;
            }
            inst.first_operand += "," +
              inst.second_operand + "," + leaMultiplier + ")";
          }
        }
      }
    }
  }


  private void peepholeStatement(OpStatement stmt) {
    switch (stmt.getOp()) {
     case ADD:
      // Yay commutivity.
      if (!(stmt.getArg1() instanceof ConstantArgument) &&
          stmt.getArg2() instanceof ConstantArgument) {
        ConstantArgument c2 = (ConstantArgument)stmt.getArg2();
        stmt.setArg2(stmt.getArg1());
        stmt.setArg1(c2);
      }
      break;
     case MULTIPLY:
      if (stmt.getArg1() instanceof ConstantArgument) {
        ConstantArgument c1 = (ConstantArgument)stmt.getArg1();
        int val1 = c1.getInt();
        if (val1 > 0 && val1 < 10 && val1 != 6 && val1 != 7) {
          stmt.setOp(AsmOp.LEA);
          return;
        }
        if (val1 > 0 && Integer.bitCount(val1) == 1) {
          int shiftCount = 0;
          while (val1 > 1) {
            shiftCount++;
            val1 >>= 1;
          }
          stmt.setArg1(Argument.makeArgument(shiftCount));
          stmt.setOp(AsmOp.SHL);
          return;
        }
      }
      if (stmt.getArg2() instanceof ConstantArgument) {
        ConstantArgument c2 = (ConstantArgument)stmt.getArg2();
        int val2 = c2.getInt();
        if (val2 > 0 && val2 < 10 && val2 != 6 && val2 != 7) {
          stmt.setOp(AsmOp.LEA);
          stmt.setArg2(stmt.getArg1());
          stmt.setArg1(c2);
          return;
        }
        if (val2 > 0 && Integer.bitCount(val2) == 1) {
          int shiftCount = 0;
          while (val2 > 1) {
            shiftCount++;
            val2 >>= 1;
          }
          stmt.setArg2(stmt.getArg1());
          stmt.setArg1(Argument.makeArgument(shiftCount));
          stmt.setOp(AsmOp.SHL);
          return;
        }
      }
      break;
     case DIVIDE:
      if (stmt.getArg2() instanceof ConstantArgument) {
        ConstantArgument c2 = (ConstantArgument)stmt.getArg2();
        int val2 = c2.getInt();
        if (val2 > 0 && Integer.bitCount(val2) == 1) {
          int shiftCount = 0;
          while (val2 > 1) {
            shiftCount++;
            val2 >>= 1;
          }
          stmt.setArg2(stmt.getArg1());
          stmt.setArg1(Argument.makeArgument(shiftCount));
          stmt.setOp(AsmOp.SHR);
          return;
        }
      }
      break;
     case MODULO:
      if (stmt.getArg2() instanceof ConstantArgument) {
        ConstantArgument c2 = (ConstantArgument)stmt.getArg2();
        int val2 = c2.getInt();
        if (val2 > 0 && Integer.bitCount(val2) == 1) {
          stmt.setArg2(stmt.getArg1());
          stmt.setArg1(Argument.makeArgument(val2 - 1));
          stmt.setOp(AsmOp.BITWISE_AND);
          return;
        }
      }
      break;
    }
  }

  public List<AsmObject> getBlock() {
    return instructions;
  }

  private void processBlock() {
    LinkedList<BasicBlockNode> nodesToProcess =
      new LinkedList<BasicBlockNode>();
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
        nodesToProcess.addFirst(branch);
      }
      BasicBlockNode next = node.getNext();
      if (next != null) {
        nodesToProcess.addFirst(next);
      }

      // Start the output.
      instructions.add(AsmFile.writeLabel(node.getId()));

      // Process each statement.
      for (BasicStatement stmt : node.getStatements()) {
        processStatement(stmt, methodName, thisMethod);
      }

      // Generate an appropriate SourceLocation for the block trailer.
      SourceLocation loc =
        SourceLocation.getSourceLocationWithoutDetails();
      if (node.getLastStatement() != null &&
          node.getLastStatement().getNode() != null) {
        loc = node.getLastStatement().getNode().getSourceLoc();
      }

      // Write the block trailer. There are three cases to consider:
      // If the node is a branch, write the branch trailer.
      // Otherwise, if there's a next node, write an unconditional
      // jump. Finally, if there's no next node, insert an implicit return.
      if (node.isBranch() && !node.getBranchTarget().equals(node.getNext())) {
        processBranch(node, branch, next, methodName, loc);
      } else if (next != null) {
        addInstruction(new AsmInstruction(
            AsmOpCode.JMP, new StringAsmArg(next.getId()), loc));
      } else if (!(node.getLastStatement() instanceof OpStatement &&
          ((OpStatement) node.getLastStatement()).getOp() == AsmOp.RETURN)) {
        // Insert an implicit return.
        MethodDescriptor returnMethod = st.getMethod(methodName);
        generateMethodReturn(null, returnMethod, loc);
      }
    }
  }

  /**
   * Writes a statement from the block to the ASM output stream.
   */
  protected void processStatement(BasicStatement stmt, String methodName,
      MethodDescriptor thisMethod) {
    // Save the location of the statement we are processing, if known.
    SourceLocation sl = SourceLocation.getSourceLocationWithoutDetails();
    if (stmt.getNode() != null) {
      sl = stmt.getNode().getSourceLoc();
    }

    if (stmt instanceof OpStatement) {
      if (opts.contains(Optimization.ASM_PEEPHOLE)) {
        peepholeStatement((OpStatement)stmt);
      }
      processOpStatement((OpStatement)stmt, methodName, thisMethod, sl);
    } else if (stmt instanceof CallStatement) {
      generateCall((CallStatement) stmt, thisMethod);
    } else if (stmt instanceof ArgReassignStatement) {
      // TODO: make this algorithm reassign registers in a smarter way
      ArgReassignStatement ars = (ArgReassignStatement) stmt;
      Map<Register, List<Register>> regMap =
        new TreeMap<Register, List<Register>>();
      for (Entry<Register, Register> entry : ars.getRegMap().entrySet()) {
        List<Register> targets = new ArrayList<Register>();
        targets.add(entry.getValue());
        regMap.put(entry.getKey(), targets);
      }

      swapRegisters(regMap, -1, sl);
    } else if (stmt instanceof NOPStatement) {
      // This is a nop; ignore it and continue onwards.
      return;
    } else if (stmt instanceof HaltStatement) {
      addInstruction(new AsmInstruction(
          AsmOpCode.XORQ, Register.RAX, Register.RAX, sl));
      addInstruction(new AsmInstruction(
          AsmOpCode.XORQ, Register.RDI, Register.RDI, sl));
      addInstruction(new AsmInstruction(
          AsmOpCode.CALL, new StringAsmArg("exit"), sl));
    } else {
      // We have an ArgumentStatement that made it to ASM generation.
      // These are supposed to be filtered out during CFG pass 2.
      ErrorReporting.reportError(new AsmException(sl,
          "Low level statement found at codegen time"));
      return;
    }
  }

  private void swapRegisters(Map<Register, List<Register>> regMap,
      int numArgs, SourceLocation sl) {
    Map<Register, Register> subMap = new HashMap<Register, Register>();

    for (Entry<Register, List<Register>> entry : regMap.entrySet()) {
      Register value = entry.getKey();
      // If the register is not reassigned to itself, put it on a list
      // of registers to reassign.
      // rdx's value in rcx, needs to go to rax
      // when we swap rcx and rax:
      // rdx's value now lives in rax
      // the value in rax now lives in rcx.
      Register location =
        subMap.containsKey(value) ? subMap.get(value) : value;
      Register target = entry.getValue().get(0);
      Register targetValue = target;
      for (Entry<Register, Register> subEntry : subMap.entrySet()) {
        if (subEntry.getValue() == target) {
          targetValue = subEntry.getKey();
        }
      }
      if (target == location) {
        continue;
      }
      if (isArgumentRegister(location, numArgs) || numArgs < 0) {
        addInstruction(new AsmInstruction(
            AsmOpCode.XCHGQ, location, target, sl));
        subMap.put(value, target);
        subMap.put(targetValue, location);
      }
    }
    for (Register reg : regMap.keySet()) {
      List<Register> targets = regMap.get(reg);
      Register firstTarget = targets.get(0);
      if (numArgs >= 0 && !isArgumentRegister(reg, numArgs)) {
        addInstruction(new AsmInstruction(
            AsmOpCode.MOVQ, reg, firstTarget, sl));
      }
      for (int ii = 1; ii < regMap.get(reg).size(); ii++) {
        addInstruction(new AsmInstruction(
            AsmOpCode.MOVQ, firstTarget, targets.get(ii), sl));
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
    // or a register; if this is the case, the conditional value lives in
    // R11.
    AsmOpCode conditionalJump = null;
    Register resultRegister = null;

    if (node.getConditional() instanceof OpStatement) {
      OpStatement condition = (OpStatement) node.getConditional();
      switch (condition.getOp()) {
      case EQUAL:
        conditionalJump = AsmOpCode.JE;
        break;
      case NOT_EQUAL:
        conditionalJump = AsmOpCode.JNE;
        break;
      case LESS_THAN:
        conditionalJump = AsmOpCode.JL;
        break;
      case LESS_OR_EQUAL:
        conditionalJump = AsmOpCode.JLE;
        break;
      case GREATER_THAN:
        conditionalJump = AsmOpCode.JG;
        break;
      case GREATER_OR_EQUAL:
        conditionalJump = AsmOpCode.JGE;
        break;
      case NOT:
        // NOT leaves value in R10.
        resultRegister = Register.R10D;
        break;
      case MOVE:
        // MOV leaves value in R10 for writes to non-registers,
        // and in an arbitrary register otherwise.
        Argument target = condition.getArg2();
        if (target.isRegister()) {
          resultRegister =
            target.getDesc().getLocation().getRegister().thirtyTwo();
        } else {
          resultRegister = Register.R10D;
        }
        break;
      default:
        // Something has gone wrong. This shouldn't happen.
        ErrorReporting.reportError(new AsmException(loc,
            "Bad opcode for conditional"));
        return;
      }
    } else {
      // We just came back from a call e.g. if (foo()) [...]
      // The return value of the call will still be in RAX.
      resultRegister = Register.EAX;
    }

    // If we had a CMP earlier, perform the conditional jump now.
    // Otherwise, we need to compare the boolean to true and jump if it is
    // in fact true ($1).
    if (conditionalJump != null) {
      addInstruction(new AsmInstruction(
          conditionalJump, new StringAsmArg(branch.getId()), loc));
    } else {
      addInstruction(new AsmInstruction(
          AsmOpCode.CMPL, new StringAsmArg("$1"), resultRegister, loc));
      addInstruction(new AsmInstruction(
          AsmOpCode.JE, new StringAsmArg(branch.getId()), loc));
    }

    // Write the alternate unconditional jump to the next block since by
    // this point we've failed the conditional jump check.
    if (next != null) {
      addInstruction(new AsmInstruction(
          AsmOpCode.JMP, new StringAsmArg(next.getId()), loc));
    } else {
      // Insert an implicit return, since there are no more basicblocks
      // left in this method to jump to.
      MethodDescriptor returnMethod = st.getMethod(methodName);
      generateMethodReturn(null, returnMethod, loc);
    }
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
   * Generates the header for a method entry. Requires the method descriptor
   * and the number of locals to initialize.
   */
  protected void generateMethodHeader(MethodDescriptor desc, int numLocals) {
    SourceLocation sl = desc.getSourceLocation();
    addInstruction(new AsmInstruction(
        AsmOpCode.ENTER,
        new StringAsmArg("$" + numLocals), new StringAsmArg("$0"), sl));

    // R12 is a callee saved register and is modified during array accesses.
    desc.markRegisterUsed(Register.R12);

    for (Register reg : desc.getUsedCalleeRegisters()) {
      // Save registers used in method.
      addInstruction(new AsmInstruction(AsmOpCode.PUSHQ, reg, sl));
    }

    if (!opts.contains(Optimization.REGISTER_ALLOCATION)) {
      for (int ii = 0; ii < Math.min(desc.getParams().size(), 6); ii++) {
        desc.markRegisterUsed(argumentRegisters[ii]);
      }
    }
  }

  /**
   * Generates the method trailer for returning from a method. Detects if it's
   * being asked to return without an argument and the method is non-void; if
   * so, writes an error handler that will be called at runtime if we reach
   * this ending by falling off the method end.
   */
  protected void generateMethodReturn(AsmArg arg1, MethodDescriptor desc,
      SourceLocation sl) {
    if (desc.getType() != DecafType.VOID && arg1 == null) {
      addInstruction(new AsmInstruction(AsmOpCode.MOVQ,
          new StringAsmArg("$." + desc.getId()  + "_name"), Register.R12, sl));
      addInstruction(new AsmInstruction(
          AsmOpCode.JLE,
          new StringAsmArg("nonvoid_noreturn_error_handler"), sl));
      return;
    }
    if (arg1 != null) {
      // Save result in return register. If it's a literal, we can plop
      // directly into RAX with MOV to obey the 64-bit calling convention.
      // However, otherwise we need to sign extend for correctness if we are
      // using a 32-bit variable from program execution.
      if (arg1 instanceof StringAsmArg && arg1.toString().startsWith("$")) {
        addInstruction(new AsmInstruction(
          AsmOpCode.MOVQ, arg1, Register.RAX, sl));
      } else {
        addInstruction(new AsmInstruction(
          AsmOpCode.MOVSXD, arg1, Register.RAX, sl));
      }
    } else {
      // Clear %rax to prevent confusion and non-zero exit codes since
      // decaf allows main() to be either void or int and system reads
      // the return value regardless of whether main is void.
      addInstruction(new AsmInstruction(AsmOpCode.XORQ, Register.RAX,
          Register.RAX, sl));
    }

    // Restore callee-saved registers we used.
    List<Register> usedRegisters = desc.getUsedCalleeRegisters();
    Collections.reverse(usedRegisters);
    for (Register reg : usedRegisters) {
      addInstruction(new AsmInstruction(AsmOpCode.POPQ, reg, sl));
    }

    // Push old base pointer.
    addInstruction(new AsmInstruction(AsmOpCode.LEAVE, sl));
    // Caller cleans up arguments.
    addInstruction(new AsmInstruction(AsmOpCode.RET, sl));
  }

  /**
   * Processes a single OpStatement into assembly instructions.
   */
  protected void processOpStatement(OpStatement op, String methodName,
      MethodDescriptor thisMethod, SourceLocation sl) {
    // prepareArgument loads an argument from memory/another register
    // into R10 or R11 and returns the reg it stored the argument in.
    AsmArg arg1 = null;
    if (op.getArg1() != null && op.getOp() != AsmOp.ENTER) {
      arg1 = prepareArgument(op.getArg1(), op.getArg2(), true,
          op.getOp(), op.getDyingRegisters(), op.getResult(), false, sl);
    }
    AsmArg arg2 = null;
    if (op.getArg2() != null && op.getOp() != AsmOp.MOVE) {
      arg2 = prepareArgument(op.getArg1(), op.getArg2(), false, op.getOp(),
          op.getDyingRegisters(), op.getResult(), false, sl);
    }

    switch (op.getOp()) {
     case MOVE:
      if (op.getArg2().isRegister()) {
        addInstruction(new AsmInstruction(AsmOpCode.MOVL, arg1,
          op.getArg2().getDesc().getLocation().getRegister().thirtyTwo(), sl));
      } else {
        writeToArgument(op.getArg2(), true, sl);
      }
      // Stop here because we don't need to move result again.
      return;
     case ADD:
      addInstruction(new AsmInstruction(AsmOpCode.ADDL, arg1, arg2, sl));
      break;
     case SUBTRACT:
      addInstruction(new AsmInstruction(AsmOpCode.SUBL, arg2, arg1, sl));
      break;
     case SHL:
      addInstruction(new AsmInstruction(AsmOpCode.SHLL, arg1, arg2, sl));
      break;
     case SHR:
      addInstruction(new AsmInstruction(AsmOpCode.SARL, arg1, arg2, sl));
      break;
     case BITWISE_AND:
      addInstruction(new AsmInstruction(AsmOpCode.ANDL, arg1, arg2, sl));
      break;
     case LEA:
       addInstruction(new AsmInstruction(AsmOpCode.LEAL, arg1, arg2, sl));
       break;
     case MULTIPLY:
      addInstruction(new AsmInstruction(AsmOpCode.IMULL, arg1, arg2, sl));
      break;
     case DIVIDE:
     case MODULO:
      // TODO: Get register liveness information here and check before reg spill
      // Save the existing value in RAX
      addInstruction(new AsmInstruction(
        AsmOpCode.PUSHQ, Register.RAX, sl));
      // Division needs to use EAX for the dividend, and overwrites
      // EAX/EDX to store its outputs.
      addInstruction(new AsmInstruction(
        AsmOpCode.MOVL, arg1, Register.EAX, sl));
      // Unfortunately, RDX may contain the first argument to the
      // function. We need to push it to memory to save it.
      if (!(op.getResult().getLocation() instanceof RegisterLocation) ||
          !op.getResult().getLocation().getRegister().sixtyFour().equals(
              Register.RDX)) {
        addInstruction(new AsmInstruction(AsmOpCode.PUSHQ, Register.RDX, sl));
      }
      if ((arg2 instanceof Register) &&
          ((Register)arg2).sixtyFour().equals(Register.RDX)) {
        addInstruction(new AsmInstruction(
            AsmOpCode.MOVL, arg2, Register.R11D, sl));
        arg2 = Register.R11D;
      }
      addInstruction(new AsmInstruction(
        AsmOpCode.CDQ, sl));
      addInstruction(new AsmInstruction(AsmOpCode.IDIVL, arg2, sl));
      break;
     case UNARY_MINUS:
      // Unary operations use R10 for input and output.
      addInstruction(new AsmInstruction(AsmOpCode.NEGL, arg1, sl));
      break;
     case NOT:
      addInstruction(new AsmInstruction(
          AsmOpCode.XORL, new StringAsmArg("$1"), arg1, sl));
      break;
     case EQUAL:
     case NOT_EQUAL:
     case LESS_THAN:
     case LESS_OR_EQUAL:
     case GREATER_THAN:
     case GREATER_OR_EQUAL:
      processBoolean(op.getOp(), arg1, arg2, sl);
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
          ((ConstantArgument) op.getArg1()).getInt());
      return;
     default:
      ErrorReporting.reportError(new AsmException(sl, "Unknown opcode."));
      return;
    }

    Register resultReg = getResultRegister(op.getOp(), arg1, arg2);
    if (op.getResult() != null) {
      if (!(op.getResult().getLocation() instanceof RegisterLocation) ||
          !resultReg.equals(op.getResult().getLocation().getRegister())) {
        addInstruction(new AsmInstruction(AsmOpCode.MOVL, resultReg,
            convertVariableLocation(op.getResult().getLocation(), true), sl));
      }
    } else {
      addInstruction(
          new AsmString("  /* Ignoring result assignment of conditional. */"));
    }

    if (op.getOp() == AsmOp.DIVIDE || op.getOp() == AsmOp.MODULO) {
      // Restore the registers we displaced for division/modulo.
      if (!(op.getResult().getLocation() instanceof RegisterLocation) ||
          !resultReg.sixtyFour().equals(Register.RDX)) {
        addInstruction(new AsmInstruction(AsmOpCode.POPQ, Register.RDX, sl));
      }
      addInstruction(new AsmInstruction(AsmOpCode.POPQ, Register.RAX, sl));
    }
  }

  /**
   * Retrieves the correct result register for a given opcode.
   */
  protected Register getResultRegister(
      AsmOp op, AsmArg first, AsmArg second) {
    switch (op) {
     case SUBTRACT:
      // Subtract reverses the order of its arguments, so arg1 contains
      // the modified result.
     case NOT:
     case UNARY_MINUS:
      return ((Register)first).thirtyTwo();
     case DIVIDE:
      // RAX is fixed to hold the quotient.
     case EQUAL:
     case NOT_EQUAL:
     case LESS_THAN:
     case LESS_OR_EQUAL:
     case GREATER_THAN:
     case GREATER_OR_EQUAL:
      return Register.EAX;
     case MODULO:
      // RDX is fixed to hold the remainder.
      return Register.EDX;
     case MOVE:
     default:
      return ((Register)second).thirtyTwo();
    }
  }

  /**
   * Performs a boolean comparison of two arguments. The arguments must have
   * already been pulled out of memory.
   */
  protected void processBoolean(AsmOp op, AsmArg arg1, AsmArg arg2,
      SourceLocation sl) {
    addInstruction(new AsmInstruction(
        AsmOpCode.XORL, Register.EAX, Register.EAX, sl));
    addInstruction(new AsmInstruction(AsmOpCode.CMPL, arg2, arg1, sl));
    AsmOpCode cmovOp = null;
    switch (op) {
    case EQUAL:
      cmovOp = AsmOpCode.CMOVEL;
      break;
    case NOT_EQUAL:
      cmovOp = AsmOpCode.CMOVNEL;
      break;
    case LESS_THAN:
      cmovOp = AsmOpCode.CMOVLL;
      break;
    case LESS_OR_EQUAL:
      cmovOp = AsmOpCode.CMOVLEL;
      break;
    case GREATER_THAN:
      cmovOp = AsmOpCode.CMOVGL;
      break;
    case GREATER_OR_EQUAL:
      cmovOp = AsmOpCode.CMOVGEL;
      break;
    }
    addInstruction(new AsmInstruction(
        AsmOpCode.MOVL, new StringAsmArg("$1"), Register.R10D, sl));
    addInstruction(new AsmInstruction(cmovOp, Register.R10D, Register.EAX,
        sl));
  }

  /**
   * Generates an outbound method call. Requires both the call statement for
   * the current call and also the current method.
   */
  protected void generateCall(CallStatement call, MethodDescriptor thisMethod) {
    SourceLocation sl = call.getNode().getSourceLoc();
    // Push caller-saved variables that we've used and need to keep.
    List<Register> usedRegisters;
    if (opts.contains(Optimization.REGISTER_ALLOCATION)) {
      usedRegisters = call.getNonDyingCallerSavedRegisters();
    } else {
      usedRegisters = thisMethod.getUsedCallerRegisters();
    }

    for (Register r : usedRegisters) {
      addInstruction(new AsmInstruction(AsmOpCode.PUSHQ, r, sl));
    }

    // Push arguments.
    // First six go into registers, rest go on stack in right to left order
    List<Argument> args = call.getArgs();

    Map<Register, List<Register>> regMap =
      new TreeMap<Register, List<Register>>();
    for (int ii = args.size() - 1; ii >= 0; ii--) {
      // Arguments after arg 6 go on the stack in reverse order
      if (ii >= 6) {
        addInstruction(new AsmInstruction(AsmOpCode.PUSHQ,
            prepareArgument(args.get(ii), null, true, AsmOp.PUSH, null,
                true, sl), sl));
        continue;
      }

      // If the arguments before arg 6 are currently in an argument register,
      // and is not in the right one, put it into transposition map.
      Argument arg = args.get(ii);
      if (arg.isRegister()) {
        Register source = arg.getDesc().getLocation().getRegister();
        List<Register> targets;
        if (regMap.containsKey(source)) {
          targets = regMap.get(source);
        } else {
          targets = new ArrayList<Register>();
        }
        targets.add(argumentRegisters[ii]);
        regMap.put(source, targets);
      }
    }

    swapRegisters(regMap, args.size(), sl);

    for (int ii = 0; ii < Math.min(args.size(), 6); ii++) {
      Argument arg = args.get(ii);
      if (!arg.isRegister()) {
        // Arg needs to be moved into its register
        addInstruction(new AsmInstruction(AsmOpCode.MOVQ,
            prepareArgument(args.get(ii),
                Argument.makeArgument(new AnonymousDescriptor(
                    new RegisterLocation(argumentRegisters[ii]))),
                true, AsmOp.MOVE, null, true, sl),
            argumentRegisters[ii], sl));
      }
    }

    // Empty %rax to cope with printf vararg issue
    addInstruction(new AsmInstruction(AsmOpCode.XORQ, Register.RAX,
        Register.RAX, sl));

    // Now we're ready to make the call.
    // This automatically pushes the return address; callee removes return addr
    addInstruction(new AsmInstruction(
        AsmOpCode.CALL, new StringAsmArg(call.getMethodName()), sl));

    // Pop arguments back off the stack.
    if (args.size() > 6) {
      addInstruction(new AsmInstruction(AsmOpCode.ADDQ,
          new StringAsmArg("$"  + (args.size() - 6) * 8), Register.RSP, sl));
    }

    // Move RAX into the correct save location.
    if (call.getResult() != null) {
      addInstruction(new AsmInstruction(AsmOpCode.MOVL, Register.EAX,
        convertVariableLocation(call.getResult().getLocation(), true), sl));
    }

    // Pop the saved usedCallerRegisters back onto the stack.
    Collections.reverse(usedRegisters);
    for (Register r : usedRegisters) {
      addInstruction(new AsmInstruction(AsmOpCode.POPQ, r, sl));
    }
  }

  protected boolean isArgumentRegister(Register r, int numArgs) {
    for (int i = 0; i < Math.min(numArgs, 6); i++) {
      if (argumentRegisters[i] == r) {
        return true;
      }
    }
    return false;
  }

  protected AsmArg prepareArgument(Argument arg1, Argument arg2,
      boolean first, AsmOp op, TypedDescriptor target,
      boolean signExtend, SourceLocation sl) {
    Set<Register> set = Collections.emptySet();
    return prepareArgument(arg1, arg2, first, op, set,
        target, signExtend, sl);
  }
  /**
   * Loads a variable from memory so that it can be used in subsequent
   * computation. Uses R10 as a temp for first argument, R11 for the second
   * if needed. Otherwise, uses registers in place.
   */
  protected AsmArg prepareArgument(Argument arg1, Argument arg2,
      boolean first, AsmOp op, Set<Register> dyingRegs, TypedDescriptor target,
      boolean signExtend, SourceLocation sl) {

    Argument thisArg = first ? arg1 : arg2;
    Register tempStorage = first ? Register.R10 : Register.R11;
    int constValue;
    boolean inImmediatePos;
    if (op.inverted()) {
      inImmediatePos = !first;
    } else {
      inImmediatePos = first;
    }
    switch (thisArg.getType()) {
     case CONST_BOOL:
     case CONST_INT:
      if (thisArg.getType() == ArgType.CONST_BOOL) {
        if (((ConstantArgument) thisArg).getBool()) {
          constValue = 1;
        } else {
          constValue = 0;
        }
      } else {
        // The immediate values in decaf cannot exceed 32 bits, so we don't
        //need to mov-shl-mov-add, but if we had to deal with 64 bits, we'd
        //do this. We still need to load to registers since some ops only
        //work on registers and not on immediates.
        constValue = ((ConstantArgument) thisArg).getInt();
      }

      if (inImmediatePos && op.acceptsImmediateArg() &&
          !(op == AsmOp.MOVE && !arg2.isRegister())) {
        return new StringAsmArg("$" + constValue);
      } else {
        addInstruction(new AsmInstruction(AsmOpCode.MOVQ,
            new StringAsmArg("$" + constValue),
            tempStorage, sl));
      }
      break;
     case VARIABLE:
      VariableLocation loc =
        ((VariableArgument)thisArg).getDesc().getLocation();
      Register targetReg = null;
      if (target != null &&
          target.getLocation().getLocationType() == LocationType.REGISTER) {
        targetReg = target.getLocation().getRegister();
      } else if (op == AsmOp.MOVE && arg2.isRegister()) {
        targetReg = arg2.getDesc().getLocation().getRegister();
      }

      if (loc.getLocationType() == LocationType.REGISTER &&
          !(op == AsmOp.MOVE && !arg2.isRegister()) &&
          (inImmediatePos || loc.getRegister().equals(targetReg) ||
              !op.mutatesArgs() || dyingRegs.contains(loc.getRegister()))) {
        // We can pass raw registers IFF either this is the first unmodified
        // register, the op doesn't mutate args,
        // or the target is this register, or this reg is dying anyways.
        tempStorage = loc.getRegister();
      } else {
        addInstruction(new AsmInstruction(AsmOpCode.MOVQ,
           convertVariableLocation(
             ((VariableArgument) thisArg).getDesc().getLocation(), false),
           tempStorage, sl));
      }
      break;
     case ARRAY_VARIABLE:
      ArrayVariableArgument ava = (ArrayVariableArgument) thisArg;
      // Arrays can only be declared as globals in decaf
      assert(ava.getDesc().getLocation().getLocationType() ==
             LocationType.GLOBAL);

      // Prepare the symbol and index names. The index needs recursive
      // resolution since it might be a variable or another array.
      // Symbol will always be a global address.
      String symbol = "." + ava.getDesc().getLocation().getSymbol();
      // The index will be a temporary register (either R10 or R11).
      // As it happens, this is also our return register, but that's okay.
      AsmArg index = prepareArgument(
          ava.getIndex(), ava.getIndex(), first, AsmOp.ARRAY, null, false, sl);

      // Use R12 to store the global name to access.
      addInstruction(new AsmInstruction(AsmOpCode.MOVQ,
          new StringAsmArg("$" + symbol),
          Register.R12, sl));

      // Finally, perform the indirection to look up from memory+offset.
      // We have to upcast to 64-bit and perform sign extension since we are
      // performing memory access using a 64-bit offset.
      if (index instanceof Register) {
        Register indexReg = (Register)index;
        if (!indexReg.equals(indexReg.sixtyFour())) {
          addInstruction(new AsmInstruction(
            AsmOpCode.MOVSXD, index, indexReg.sixtyFour(), sl));
        }
        index = indexReg.sixtyFour();
      }
      addInstruction(new AsmInstruction(AsmOpCode.MOVQ,
          new StringAsmArg("(" + Register.R12 + ", " + index + ", 8)"),
          tempStorage, sl));
      break;
    }
    return signExtend ? tempStorage : tempStorage.thirtyTwo();
  }

  /**
   * Saves a result from a mov to a variable or an array variable.
   */
  protected void writeToArgument(Argument arg, boolean signExtend,
      SourceLocation sl) {
    switch (arg.getType()) {
     case VARIABLE:
      if (signExtend) {
        if(CLI.debug) {
          System.out.println(arg);
        }
        addInstruction(new AsmInstruction(AsmOpCode.MOVQ, Register.R10,
          convertVariableLocation(
            ((VariableArgument) arg).getDesc().getLocation(), false), sl));
      } else {
        addInstruction(new AsmInstruction(AsmOpCode.MOVSXD, Register.R10D,
          convertVariableLocation(
            ((VariableArgument) arg).getDesc().getLocation(), false), sl));
      }
      break;
     case ARRAY_VARIABLE:
      ArrayVariableArgument ava = (ArrayVariableArgument) arg;
      // Arrays can only be declared as globals in decaf
      assert(ava.getDesc().getLocation().getLocationType() ==
             LocationType.GLOBAL);

      // Prepare the symbol and index names. The index needs recursive
      // resolution since it might be a variable.
      // Symbol will always be a global address.
      String symbol = "." + ava.getDesc().getLocation().getSymbol();

      // The index will be an unused register (R11).
      // We don't want to use R10, which would clobber the result to
      // return.
      AsmArg index = prepareArgument(
        ava.getIndex(), ava.getIndex(), false, AsmOp.ARRAY, null, false, sl);

      // Use R12 to store the global name to access.
      addInstruction(new AsmInstruction(AsmOpCode.MOVQ,
          new StringAsmArg("$" + symbol), Register.R12, sl));

      // Finally, perform the indirection to save to memory+offset.
      if (signExtend) {
        addInstruction(new AsmInstruction(AsmOpCode.MOVSXD, Register.R10D,
          Register.R10, sl));
      }
      if (index instanceof Register) {
        Register indexReg = (Register)index;
        if (!indexReg.equals(indexReg.sixtyFour())) {
          addInstruction(new AsmInstruction(
            AsmOpCode.MOVSXD, index, indexReg.sixtyFour(), sl));
        }
        index = indexReg.sixtyFour();
      }
      addInstruction(new AsmInstruction(AsmOpCode.MOVQ, Register.R10,
          new StringAsmArg("("  + Register.R12 + ", " + index + ", 8)"), sl));
      break;
    }
  }

  /**
   * Converts a VariableLocation object to the corresponding ASM string
   * required to look it up as an op's argument.
   */
  protected AsmArg convertVariableLocation(
      VariableLocation loc, boolean thirtyTwo) {
    switch (loc.getLocationType()) {
    case GLOBAL:
      if (loc.getSymbol().startsWith(".str")) {
        return new StringAsmArg("$" + loc.getSymbol());
      } else {
        return new StringAsmArg("." + loc.getSymbol());
      }
    case REGISTER:
      return new StringAsmArg(
          "" + (thirtyTwo ? loc.getRegister().thirtyTwo() :
                            loc.getRegister().sixtyFour()));
    case STACK:
      return new StringAsmArg(loc.getOffset() + "(%rbp)");
    }
    return null;
  }
}