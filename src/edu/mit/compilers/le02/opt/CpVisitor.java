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
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;

public class CpVisitor extends BasicBlockVisitor {
  private Map<LocalDescriptor, CseVariable> tmpToVar =
    new HashMap<LocalDescriptor, CseVariable>();
  private Map<CseVariable, Set<LocalDescriptor>> varToSet =
    new HashMap<CseVariable, Set<LocalDescriptor>>();

  protected void processNode(BasicBlockNode node) {
    List<BasicStatement> newStmts = new ArrayList<BasicStatement>();
    for (BasicStatement stmt : node.getStatements()) {
      if (stmt.getType() == BasicStatementType.CALL) {
        // Replace all arguments with alternatives if available.
        CallStatement call = (CallStatement)stmt;
        List<Argument> args = new ArrayList<Argument>();
        for (Argument arg : call.getArgs()) {
          args.add(convertArg(arg));
        }
        call.setArgs(args);

        // Invalidate all cached global variable values.
        // This necessitates enumerating all globals and dropping em.
        Iterator<CseVariable> it = varToSet.keySet().iterator();
        while (it.hasNext()) {
          if (it.next() instanceof FieldDescriptor) {
            it.remove();
          }
        }
        Iterator<Entry<LocalDescriptor, CseVariable>> it2 =
          tmpToVar.entrySet().iterator();
        while (it2.hasNext()) {
          Entry<LocalDescriptor, CseVariable> entry = it2.next();
          if (entry.getValue() instanceof FieldDescriptor) {
            entry.setValue(entry.getKey());
          }
        }
      }
      if (stmt.getType() != BasicStatementType.OP) {
        newStmts.add(stmt);
        continue;
      }

      OpStatement op = (OpStatement)stmt;
      CseVariable storedVar = null;

      switch (op.getOp()) {
       case MOVE:
        if (op.getArg2().getDesc() != null &&
            op.getArg2().getDesc() instanceof AnonymousDescriptor) {
          // This is either an implicit conditional assignment for je or
          // an implicit string MOV to push arguments for a callout.
          // In either case, not cacheable.
          op.setArg1(convertArg(op.getArg1()));
          newStmts.add(stmt);
          continue;
        }
        if (op.getArg2() instanceof ArrayVariableArgument) {
          storedVar = (ArrayVariableArgument)op.getArg2();
        } else if (op.getArg2().getDesc() != null) {
          storedVar = op.getArg2().getDesc();
        }
        break;
       case RETURN:
        Argument originalRetval = op.getArg1();
        if (originalRetval != null) {
          op.setArg1(convertArg(op.getArg1()));
        }
        // Deliberately fall through and continue.
       case ENTER:
        newStmts.add(stmt);
        continue;
       default:
        storedVar = op.getResult();
      }

      // Convert arguments.
      if (op.getArg1() != null) {
        op.setArg1(convertArg(op.getArg1()));
      }
      if (op.getArg2() != null) {
        op.setArg2(convertArg(op.getArg2()));
      }

      if (storedVar != null) {
        if (storedVar instanceof ArrayVariableArgument) {
          // This is a global array that we've scribbled on.
          // Invalidate all previous references to that array.
          Iterator<CseVariable> it = varToSet.keySet().iterator();
          while (it.hasNext()) {
            CseVariable var = it.next();
            if (var instanceof ArrayVariableArgument &&
                ((ArrayVariableArgument)var).getDesc().equals(
                    ((ArrayVariableArgument)storedVar).getDesc())) {
              it.remove();
            }
          }
          Iterator<Entry<LocalDescriptor, CseVariable>> it2 =
            tmpToVar.entrySet().iterator();
          while (it2.hasNext()) {
            Entry<LocalDescriptor, CseVariable> entry = it2.next();
            if (entry.getValue() instanceof ArrayVariableArgument &&
                ((ArrayVariableArgument)entry.getValue()).getDesc().equals(
                    ((ArrayVariableArgument)storedVar).getDesc())) {
              entry.setValue(entry.getKey());
            }
          }
        }

        // Update if we are writing into a temporary for archiving.
        if (storedVar instanceof LocalDescriptor &&
            ((LocalDescriptor)storedVar).isLocalTemporary()) {
          if (op.getOp() == AsmOp.MOVE &&
              op.getArg1() instanceof CseVariable) {
            CseVariable var = (CseVariable)op.getArg1();
            LocalDescriptor tmp = (LocalDescriptor)storedVar;
            tmpToVar.put(tmp, var);
            Set<LocalDescriptor> set = varToSet.get(var);
            if (set == null) {
              set = new HashSet<LocalDescriptor>();
              varToSet.put(var, set);
            }
            set.add(tmp);
          }
        } else {
          // We are writing into a non-temporary. We need to clear out
          // all the places where its value was archived.
          Set<LocalDescriptor> references = varToSet.get(storedVar);
          if (references != null) {
            for (LocalDescriptor tmp : references) {
              tmpToVar.remove(tmp);
            }
            varToSet.remove(storedVar);
          }
        }
      }

      // Finally, write the op.
      newStmts.add(stmt);
    }

    // Finally, overwrite the list of basicblock statements with our new list.
    node.setStatements(newStmts);
  }

  private Argument convertArg(Argument arg) {
    CseVariable result = tmpToVar.get(arg);
    if (result != null) {
      return Argument.makeArgument(result);
    } else {
      return arg;
    }
  }
}
