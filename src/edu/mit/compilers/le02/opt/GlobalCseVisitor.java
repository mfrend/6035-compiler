package edu.mit.compilers.le02.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.VariableLocation.LocationType;
import edu.mit.compilers.le02.cfg.Argument;
import edu.mit.compilers.le02.cfg.BasicBlockNode;
import edu.mit.compilers.le02.cfg.BasicStatement;
import edu.mit.compilers.le02.cfg.BasicStatement.BasicStatementType;
import edu.mit.compilers.le02.cfg.CFGGenerator;
import edu.mit.compilers.le02.cfg.OpStatement;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.cfg.VariableArgument;
import edu.mit.compilers.le02.dfa.AvailableExpressions;
import edu.mit.compilers.le02.dfa.AvailableExpressions.Expression;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.le02.symboltable.TypedDescriptor;

/**
 * Performs global cse on a given method.  Assumes that local cse has already
 * been performed (so does not perform it again).
 * @author David Koh (dkoh@mit.edu)
 *
 */
public class GlobalCseVisitor extends BasicBlockVisitor {
  private AvailableExpressions ae;
  private Pass pass;

  // See performSubstitution for why this is a Map, not a Set
  private Map<OpStatement, OpStatement> statementsToUpdate;
  private Map<Expression, LocalDescriptor> expressionToTemp;

  public enum Pass {
    FIND_EXPRESSIONS,
    PERFORM_SUBSTITUTION
  }

  public static void performGlobalCse(BasicBlockNode methodHead) {
    AvailableExpressions ae = new AvailableExpressions(methodHead);
    GlobalCseVisitor visitor = new GlobalCseVisitor(ae);

    visitor.pass = Pass.FIND_EXPRESSIONS;
    visitor.visit(methodHead);
    visitor.pass = Pass.PERFORM_SUBSTITUTION;
    visitor.visit(methodHead);
  }

  public GlobalCseVisitor(AvailableExpressions ae) {
    this.ae = ae;
    this.statementsToUpdate = new HashMap<OpStatement, OpStatement>();
    this.expressionToTemp = new HashMap<Expression, LocalDescriptor>();
  }

  @Override
  protected void processNode(BasicBlockNode node) {
    switch(pass) {
      case FIND_EXPRESSIONS:
        findExpressions(node);
        break;
      case PERFORM_SUBSTITUTION:
        performSubstitution(node);
        break;
    }
  }

  private void performSubstitution(BasicBlockNode node) {
    ArrayList<BasicStatement> newStmts = new ArrayList<BasicStatement>();

    for (BasicStatement s : node.getStatements()) {

      // Replace the expressions with updated versions.
      // Note: two statements can be equal without being the same.
      if (statementsToUpdate.containsKey(s)
          && s == statementsToUpdate.get(s)) {

        Expression expr = new Expression((OpStatement) s);
        LocalDescriptor tmp = expressionToTemp.get(expr);
        TypedDescriptor dest = expr.getStatement().getResult();

        // If this happens, our dest will be wrong
        assert expr.getStatement().getOp() != AsmOp.MOVE:

        newStmts.add(new OpStatement(s.getNode(), AsmOp.MOVE,
                                     Argument.makeArgument(tmp),
                                     Argument.makeArgument(dest),
                                     null));
        continue;
      }

      // If we've reached this point, we're not replacing the instruction
      newStmts.add(s);

      if (s.getType() == BasicStatementType.OP) {
        Expression expr = new Expression((OpStatement) s);

        if (expressionToTemp.containsKey(expr)) {
          LocalDescriptor ld = expressionToTemp.get(expr);

          // Store the result of the computation in a temporary variable
          newStmts.add(new OpStatement(s.getNode(), AsmOp.MOVE,
                                       Argument.makeArgument(s.getResult()),
                                       Argument.makeArgument(ld),
                                       null));
        }
      }

    }
  }

  private void findExpressions(BasicBlockNode node) {
    AvailableExpressions.BlockItem aeInfo = ae.getExpressions(node);
    boolean globalsClobbered = false;
    Set<VariableLocation> clobberedVariables = new HashSet<VariableLocation>();

    for (BasicStatement s : node.getStatements()) {
      if (s.getType() == BasicStatementType.CALL) {
        globalsClobbered = true;
      }
      else if (s.getType() == BasicStatementType.OP) {
        OpStatement expr = (OpStatement) s;

        if (!AvailableExpressions.isExpression(expr)) {
          continue;
        }

        // Check that the expression is available from other blocks, and also
        // that it has not been clobbered from this block.
        if (aeInfo.expressionIsAvailable(expr)
            && checkArg(expr.getArg1(), clobberedVariables, globalsClobbered)
            && checkArg(expr.getArg2(), clobberedVariables, globalsClobbered)) {
          statementsToUpdate.put(expr, expr);

          LocalDescriptor ld = CFGGenerator.makeTemp(expr.getNode(),
                                   expr.getResult().getFlattenedType());
          expressionToTemp.put(new Expression(expr), ld);
        }
      }

      clobberedVariables.add(getTarget(s));
    }
  }

  private boolean checkArg(Argument arg, Set<VariableLocation> clobbered,
                           boolean globalsClobbered) {
    if (!arg.isVariable()) {
      return true;
    }

    VariableArgument vArg = (VariableArgument) arg;
    if (globalsClobbered
        && vArg.getDesc().getLocation().getLocationType() == LocationType.GLOBAL) {
      return false;
    }

    return clobbered.contains(vArg.getDesc().getLocation());
  }


  private VariableLocation getTarget(BasicStatement s) {
    if (s.getType() != BasicStatementType.OP) {
      if (s.getResult() == null) {
        return null;
      }
      return s.getResult().getLocation();
    }

    OpStatement opSt = (OpStatement) s;
    if (opSt.getOp() == AsmOp.MOVE) {
      return opSt.getArg2().getDesc().getLocation();
    }
    else {
      if (opSt.getResult() == null) {
        return null;
      }
      return opSt.getResult().getLocation();
    }
  }
}
