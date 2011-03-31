package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.ArrayVariableArgument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.CFGGenerator;
import edu.mit.compilers.le02.cfg.CallStatement;
import edu.mit.compilers.le02.cfg.ConstantArgument;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.Argument.ArgType;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.AnonymousDescriptor;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

public class CseVisitor extends BasicBlockVisitor {
  private static class Value {
    private int index;
    private Value(int idx) {
      index = idx;
    }
    @SuppressWarnings("unused")
    public int getIndex() {
      return index;
    }
    @Override
    public String toString() {
      return "" + index;
    }
    @Override
    public boolean equals(Object o) {
      return (o instanceof Value && ((Value)o).index == index);
    }
    @Override
    public int hashCode() {
      return index;
    }

    private static int nextIndex = 0;
    public static Value nextIndex() {
      Value ret = new Value(nextIndex);
      nextIndex++;
      return ret;
    }
  }

  private class ValExp {
    private AsmOp op;
    private Value left;
    private Value right;
    public ValExp(OpStatement stmt) {
      this.op = stmt.getOp();

      CseVariable left;
      if (stmt.getArg1().getType() == ArgType.CONST_INT ||
          stmt.getArg1().getType() == ArgType.CONST_BOOL) {
        left = (ConstantArgument)stmt.getArg1();
      } else {
        left = stmt.getArg1().getDesc();
      }
      if (!varToVal.containsKey(left)) {
        varToVal.put(left, Value.nextIndex());
      }
      this.left = varToVal.get(left);

      if (stmt.getArg2() == null) {
        return;
      }
      CseVariable right;
      if (stmt.getArg2().getType() == ArgType.CONST_INT ||
          stmt.getArg2().getType() == ArgType.CONST_BOOL) {
        right = (ConstantArgument)stmt.getArg2();
      } else {
        right = stmt.getArg2().getDesc();
      }
      if (!varToVal.containsKey(right)) {
        varToVal.put(right, Value.nextIndex());
      }
      this.right = varToVal.get(right);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ValExp)) {
        return false;
      }
      ValExp other = (ValExp)o;
      return op.equals(other.op) &&
        ((left != null) ? left.equals(other.left) : (other.left == null)) &&
        ((right != null) ? right.equals(other.right) : (other.right == null));
    }
    @Override
    public int hashCode() {
      return op.hashCode() +
        ((left != null) ? left.hashCode() : 0) +
        ((right != null) ? right.hashCode() : 0);
    }
  }

  private Map<CseVariable, Value> varToVal =
    new HashMap<CseVariable, Value>();
  private Map<ValExp, Value> expToVal = new HashMap<ValExp, Value>();
  private Map<ValExp, LocalDescriptor> expToTmp =
    new HashMap<ValExp, LocalDescriptor>();

  @Override
  protected void processNode(BasicBlockNode node) {
    List<BasicStatement> newStmts = new ArrayList<BasicStatement>();
    for (BasicStatement stmt : node.getStatements()) {
      CseVariable storedVar = null;
      if (stmt.getType() == BasicStatementType.CALL) {
        // Invalidate all cached global variable values.
        // This necessitates enumerating all globals and dropping em.
        Iterator<CseVariable> it = varToVal.keySet().iterator();
        while (it.hasNext()) {
          if (it.next() instanceof FieldDescriptor) {
            it.remove();
          }
        }
        // If the result of the call is assigned somewhere, remove it from
        // var to val mapping.
        TypedDescriptor callResult = ((CallStatement)stmt).getResult();
        if (callResult != null) {
          varToVal.remove(callResult);
        }
      }
      if (stmt.getType() != BasicStatementType.OP) {
        newStmts.add(stmt);
        continue;
      }
      OpStatement op = (OpStatement)stmt;
      switch (op.getOp()) {
       case MOVE:
        if (op.getArg1().equals(op.getArg2())) {
          // We can silently drop a self-assignment.
          continue;
        }
        if (op.getArg2().getDesc() != null &&
            op.getArg2().getDesc() instanceof AnonymousDescriptor) {
          // This is either an implicit conditional assignment for je or
          // an implicit string MOV to push arguments for a callout.
          // In either case, not cacheable.
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
       case ENTER:
        newStmts.add(stmt);
        continue;
       default:
        storedVar = op.getResult();
      }

      ValExp valexp = new ValExp(op);

      // If this operation has been cached, rewrite silently to a MOV.
      LocalDescriptor cachedValue = expToTmp.get(valexp);
      if (cachedValue != null) {
        newStmts.add(new OpStatement(stmt.getNode(), AsmOp.MOVE,
          Argument.makeArgument(cachedValue),
          Argument.makeArgument(storedVar),
          null));
        varToVal.put(storedVar, expToVal.get(valexp));
        continue;
      } else {
        newStmts.add(stmt);
      }
      if (storedVar != null) {
        if (storedVar instanceof ArrayVariableArgument) {
          // This is a global array that we've scribbled on.
          // Invalidate all previous references to that array.
          Iterator<CseVariable> it = varToVal.keySet().iterator();
          while (it.hasNext()) {
            CseVariable var = it.next();
            if (var instanceof ArrayVariableArgument &&
                ((ArrayVariableArgument)var).getDesc().equals(
                    ((ArrayVariableArgument)storedVar).getDesc())) {
              it.remove();
            }
          }
        }

        Value val = Value.nextIndex();
        varToVal.put(storedVar, val);
        expToVal.put(valexp, val);

        if (storedVar instanceof LocalDescriptor &&
            ((LocalDescriptor)storedVar).isLocalTemporary()) {
          // This is an immutable temporary, but need to register the
          // correspondence.
          expToTmp.put(valexp, (LocalDescriptor)storedVar);
        } else {
          // We need to archive a copy of this variable just in case.
          // Allocate a new temporary.
          LocalDescriptor tempDescriptor =
            CFGGenerator.makeTemp(stmt.getNode(), storedVar.getFlattenedType());
          newStmts.add(new OpStatement(stmt.getNode(), AsmOp.MOVE,
            Argument.makeArgument(storedVar),
            Argument.makeArgument(tempDescriptor),
            null));
          expToTmp.put(valexp, tempDescriptor);
        }
      }
    }

    // Finally, overwrite the list of basicblock statements with our new list.
    node.setStatements(newStmts);
  }
}
