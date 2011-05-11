package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.tools.CLI;

public class CpVisitor extends BasicBlockVisitor {
  private Map<CseVariable, CseVariable> tmpToVar =
    new HashMap<CseVariable, CseVariable>();
  private Map<CseVariable, Set<CseVariable>> varToSet =
    new HashMap<CseVariable, Set<CseVariable>>();

  @Override
  protected void processNode(BasicBlockNode node) {
    tmpToVar.clear();
    varToSet.clear();

    List<BasicStatement> newStmts = new ArrayList<BasicStatement>();
    for (BasicStatement stmt : node.getStatements()) {
      if (stmt.getType() == BasicStatementType.CALL) {
        stmt = handleCall((CallStatement)stmt);
      }
      // Dealt with Calls above, Ops below; everything else doesn't affect CP.
      if (stmt.getType() != BasicStatementType.OP) {
        newStmts.add(stmt);
        continue;
      }

      OpStatement op = (OpStatement)stmt;

      CseVariable storedVar = determineStoredVar(newStmts, stmt, op);
      if (storedVar != null && storedVar instanceof SkipProcessing) {
        continue;
      }

      // Convert arguments.
      if (op.getArg1() != null) {
        Argument arg1 = convertArg(op.getArg1());
        if (arg1 != null && !arg1.equals(op.getArg1())) {
          op = new OpStatement(op.getNode(), op.getOp(),
                               arg1, op.getArg2(), op.getResult());
        }
      }
      if (op.getArg2() != null && op.getOp() != AsmOp.MOVE) {
        Argument arg2 = convertArg(op.getArg2());
        if (arg2 != null && !arg2.equals(op.getArg2())) {
          op = new OpStatement(op.getNode(), op.getOp(),
                               op.getArg1(), arg2, op.getResult());
        }
      }

      if (storedVar != null && storedVar instanceof ArrayVariableArgument) {
        // This is a global array that we've scribbled on.
        // Invalidate all previous references to that array.
        invalidateArray((ArrayVariableArgument)storedVar);
      } else if (storedVar != null &&
                 (!(storedVar instanceof LocalDescriptor) ||
                  !((LocalDescriptor)storedVar).isLocalTemporary())) {
        // We are writing into a non-temporary. We need to clear out
        // all the places where its value was archived.
        // For temporaries, they're write-once and thus this is moot.
        Set<CseVariable> references = varToSet.get(storedVar);
        if (references != null) {
          for (CseVariable temp : references) {
            tmpToVar.remove(temp);
          }
          varToSet.remove(storedVar);
        }
      }

      if (op.getOp() == AsmOp.MOVE &&
          (op.getArg1() instanceof VariableArgument)) {
        CseVariable var = null;
        if (op.getArg1() instanceof ArrayVariableArgument) {
          var = (CseVariable)convertArg(op.getArg1());
        } else if (op.getArg1().getDesc() != null) {
          var = op.getArg1().getDesc();
        }

        tmpToVar.put(storedVar, var);
        Set<CseVariable> set = varToSet.get(var);
        if (set == null) {
          set = new HashSet<CseVariable>();
          varToSet.put(var, set);
        }
        set.add(storedVar);
      }

      // Finally, write the op.
      newStmts.add(op);
    }

    // Finally, overwrite the list of basicblock statements with our new list.
    node.setStatements(newStmts);
  }

  /**
   * Determine what stored variable was written to.
   * Returns null if no further processing should occur.
   */
  private CseVariable determineStoredVar(List<BasicStatement> newStmts,
      BasicStatement stmt, OpStatement op) {
    switch (op.getOp()) {
     case MOVE:
      if (op.getArg2().getDesc() != null &&
          op.getArg2().getDesc() instanceof AnonymousDescriptor) {
        // This is either an implicit conditional assignment for je or
        // an implicit string MOV to push arguments for a callout.
        // In either case, not cacheable.
        Argument arg1 = convertArg(op.getArg1());
        if (arg1 != null && !arg1.equals(op.getArg1())) {
          op = new OpStatement(op.getNode(), op.getOp(),
                               arg1, op.getArg2(), op.getResult());
        }
        newStmts.add(op);
        return SkipProcessing.getInstance();
      }
      if (op.getArg2() instanceof ArrayVariableArgument) {
        return (ArrayVariableArgument)op.getArg2();
      } else if (op.getArg2().getDesc() != null) {
        return op.getArg2().getDesc();
      }
     case RETURN:
      Argument originalRetval = op.getArg1();
      if (originalRetval != null) {
        Argument arg1 = convertArg(op.getArg1());
        if (arg1 != null && !arg1.equals(op.getArg1())) {
          op = new OpStatement(op.getNode(), op.getOp(),
                               arg1, op.getArg2(), op.getResult());
        }
      }
      // Deliberately fall through and continue.
     case ENTER:
      newStmts.add(op);
      return SkipProcessing.getInstance();
     default:
      return op.getResult();
    }
  }

  /**
   * Invalidates all references in the variable set for the altered array.
   */
  private void invalidateArray(ArrayVariableArgument storedVar) {
    Iterator<CseVariable> it = varToSet.keySet().iterator();
    while (it.hasNext()) {
      CseVariable var = it.next();
      if (var instanceof ArrayVariableArgument &&
          ((ArrayVariableArgument)var).getDesc().equals(
              storedVar.getDesc())) {
        it.remove();
      }
    }
    Iterator<Entry<CseVariable, CseVariable>> it2 =
      tmpToVar.entrySet().iterator();
    while (it2.hasNext()) {
      Entry<CseVariable, CseVariable> entry = it2.next();
      if (entry.getValue() instanceof ArrayVariableArgument &&
          ((ArrayVariableArgument)entry.getValue()).getDesc().equals(
              storedVar.getDesc())) {
        entry.setValue(entry.getKey());
      }
    }
  }

  /**
   * Handles substitution and invalidation for a method call.
   */
  private CallStatement handleCall(CallStatement call) {
    // Replace all arguments with alternatives if available.
    List<Argument> args = new ArrayList<Argument>();
    for (Argument arg : call.getArgs()) {
      args.add(convertArg(arg));
    }
    call = new CallStatement(call.getNode(),
      call.getMethodName(), args, call.getResult(), call.isCallout());

    if (call.isCallout()) {
      // Callouts cannot tamper with our global variables.
      return call;
    }

    // Invalidate all cached global variable values.
    // This necessitates enumerating all globals and dropping em.
    Iterator<CseVariable> it = varToSet.keySet().iterator();
    while (it.hasNext()) {
      if (it.next() instanceof FieldDescriptor) {
        it.remove();
      }
    }
    Iterator<Entry<CseVariable, CseVariable>> it2 =
      tmpToVar.entrySet().iterator();
    while (it2.hasNext()) {
      Entry<CseVariable, CseVariable> entry = it2.next();
      if (entry.getValue() instanceof FieldDescriptor) {
        entry.setValue(entry.getKey());
      }
    }
    return call;
  }

  /**
   * Converts an argument into a corresponding non-temp if available.
   */
  private Argument convertArg(Argument arg) {
    CseVariable key;
    if (arg instanceof ArrayVariableArgument) {
      ArrayVariableArgument ava = (ArrayVariableArgument)arg;
      key = new ArrayVariableArgument(
        arg.getDesc(), convertArg(ava.getIndex()));
    } else {
      key = arg.getDesc();
    }
    CseVariable result = tmpToVar.get(key);
    if (result != null) {
      if (CLI.debug) {
        //System.out.println("Substituted " + result + " for " + key);
      }
      return Argument.makeArgument(result);
    } else {
      return arg;
    }
  }
}
