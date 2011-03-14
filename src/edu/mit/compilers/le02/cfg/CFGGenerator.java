package edu.mit.compilers.le02.cfg;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.le02.CompilerException;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.VariableLocation;
import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ArrayLocationNode;
import edu.mit.compilers.le02.ast.AssignNode;
import edu.mit.compilers.le02.ast.BoolOpNode;
import edu.mit.compilers.le02.ast.BoolOpNode.BoolOp;
import edu.mit.compilers.le02.ast.BooleanNode;
import edu.mit.compilers.le02.ast.CallStatementNode;
import edu.mit.compilers.le02.ast.CallNode;
import edu.mit.compilers.le02.ast.ClassNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.ForNode;
import edu.mit.compilers.le02.ast.IfNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.MathOpNode;
import edu.mit.compilers.le02.ast.MethodCallNode;
import edu.mit.compilers.le02.ast.MethodDeclNode;
import edu.mit.compilers.le02.ast.MinusNode;
import edu.mit.compilers.le02.ast.NotNode;
import edu.mit.compilers.le02.ast.ReturnNode;
import edu.mit.compilers.le02.ast.ScalarLocationNode;
import edu.mit.compilers.le02.ast.VariableNode;
import edu.mit.compilers.le02.cfg.OpStatement.AsmOp;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;

public final class CFGGenerator extends ASTNodeVisitor<Argument> {
    private static CFGGenerator instance = null;
    private static BasicBlockNode curNode, blockBegin, blockEnd;
    private static int id;
  
    public static CFGGenerator getInstance() {
        if (instance == null) {
            instance = new CFGGenerator();
            id = 0;
        }
        return instance;
    }

    public static String nextID() {
        return Integer.toString(id++);
    }

    private VariableLocation makeTemp(ASTNode node, DecafType type) {
        SymbolTable st = node.getSymbolTable();
    
        int nextIndex = st.getLargestLocalOffset() - 8;
        VariableLocation loc = new VariableLocation();
        loc.setStackLocation(nextIndex);
    
        LocalDescriptor ld = new LocalDescriptor(st, Math.abs(nextIndex) + "lcltmp", type);
        st.put(ld.getId(), ld, node.getSourceLoc());
        return loc;
    }
  
    public static void generateCFG(ASTNode root) {
        assert(root instanceof ClassNode);
        root.accept(getInstance());
    }

    @Override
    public Argument visit(MethodDeclNode node) {
        curNode = new BasicBlockNode(nextID(), null, null, null);

        defaultBehavior(node);
        return null;
    }

    /*
     * Statement visit methods
     * We don't need to override visit for BlockNodes, because they have
     * no effect on the structure of the CFG
     */
    @Override
    public Argument visit(AssignNode node) {
        VariableLocation destLoc = node.getLoc().getDesc().getLocation();
        Argument src = node.getValue().accept(this);
        Argument dest = Argument.makeArgument(destLoc);
    
        curNode.addStatement(new OpStatement(node, AsmOp.MOVE, src, dest, null));
        return dest;
    }

    @Override
    public Argument visit(ReturnNode node) {
        Argument returnValue = null;
        if (node.hasValue()) {
            returnValue = node.getRetValue().accept(this);
        }
    
        curNode.addStatement(new OpStatement(node, AsmOp.RETURN, returnValue, null, null));
        return null;
    }

    @Override
    public Argument visit(ForNode node) {
        Argument loopVar = node.getInit().accept(this);
        Argument endVar = node.getEnd().accept(this);

        String loopID = nextID();
        String postLoopID = nextID();

        curNode.setTrueBranch(loopID);

        curNode = new BasicBlockNode(loopID, null, null, null);
        node.getBody().accept(this);

        // TODO: Create a BoolOpNode which expresses the for loop condition
        VariableLocation temp = makeTemp(node.getBody(), DecafType.BOOLEAN);
        BasicStatement condition = new OpStatement(null, AsmOp.LESS_THAN, loopVar, endVar, temp);
        curNode.setConditional(condition);
        curNode.setTrueBranch(loopID);
        curNode.setFalseBranch(postLoopID);

        curNode = new BasicBlockNode(postLoopID, null, null, null);

        return null;
    }

    @Override
    public Argument visit(IfNode node) {
        String thenID = nextID();
        String elseID = null;
        if (node.hasElse()) {
            elseID = nextID();
        }
        String postIfID = nextID();

        // TODO: Create a BoolOpNode which expresses the if condition
        VariableLocation temp = makeTemp(node, DecafType.BOOLEAN);
        BasicStatement condition = new OpStatement(null, AsmOp.EQUAL,
                node.getCondition().accept(this), new ConstantArgument(true), temp);
        curNode.setConditional(condition);
        curNode.setTrueBranch(thenID);
        curNode.setFalseBranch(elseID);

        curNode = new BasicBlockNode(thenID, null, null, null);
        node.getThenBlock().accept(this);
        curNode.setTrueBranch(postIfID);

        if (node.hasElse()) {
            curNode = new BasicBlockNode(elseID, null, null, null);
            node.getElseBlock().accept(this);
            curNode.setTrueBranch(postIfID);
        }

        curNode = new BasicBlockNode(postIfID, null, null, null);

        return null;
    }

    /* TODO: Create visit methods for MethodCallNodes and SystemCallNodes
       Leaving a method written by dkoh here for now
    public Argument visit(MethodCallNode node) {
        VariableLocation loc = makeTemp(node, node.getType());
    
        List<Argument> args = new ArrayList<Argument>();
        for (ExpressionNode n : node.getArgs()) {
            args.add(n.accept(this));
        }
    
        CallStatement s = new CallStatement(node, node.getDesc(), args, loc);
        curNode.addStatement(s);
    
        return Argument.makeArgument(loc);
    }*/

    /*
     * Expression visit methods
     */
    public Argument visit(BoolOpNode node) {
        Argument arg1 = node.getLeft().accept(this);
        VariableLocation destLoc = makeTemp(node, DecafType.BOOLEAN);
        Argument dest = Argument.makeArgument(destLoc);

        if ((node.getOp() == BoolOp.AND) || (node.getOp() == BoolOp.OR)) {
            boolean shortCircuitValue = true;
            if (node.getOp() == BoolOp.AND) {
                shortCircuitValue = false;
            }

            String shortCircuitID = nextID();
            String recomputeID = nextID();
            String postExprID = nextID();

            VariableLocation temp = makeTemp(node, DecafType.BOOLEAN);
            BasicStatement condition = new OpStatement(null, AsmOp.EQUAL,
                arg1, new ConstantArgument(shortCircuitValue), temp);
            curNode.setConditional(condition);
            curNode.setTrueBranch(shortCircuitID);
            curNode.setFalseBranch(recomputeID);

            curNode = new BasicBlockNode(shortCircuitID, null, null, null);
            // TODO: Create an AssignNode for this MOVE operation
            curNode.addStatement(new OpStatement(null, AsmOp.MOVE, arg1, dest, null));
            curNode.setTrueBranch(postExprID);

            curNode = new BasicBlockNode(recomputeID, null, null, null);
            Argument arg2 = node.getRight().accept(this);
            // TODO: Create an AssignNode for this MOVE operation
            curNode.addStatement(new OpStatement(null, AsmOp.MOVE, arg2, dest, null));
            curNode.setTrueBranch(postExprID);

            curNode = new BasicBlockNode(postExprID, null, null, null);
        } else {
            Argument arg2 = node.getRight().accept(this);
            curNode.addStatement(new OpStatement(node, getAsmOp(node), arg1, arg2, destLoc));
        }
        return dest;
    }
  
    public Argument visit(MathOpNode node) {
        Argument arg1 = node.getLeft().accept(this);
        Argument arg2 = node.getRight().accept(this);
        VariableLocation loc = makeTemp(node, DecafType.INT);
    
        OpStatement s = new OpStatement(node, getAsmOp(node), arg1, arg2, loc);
        curNode.addStatement(s);
        return Argument.makeArgument(loc);
    }
  
    public Argument visit(NotNode node) {
        VariableLocation loc = makeTemp(node, DecafType.BOOLEAN);
    
        OpStatement s = new OpStatement(node, AsmOp.NOT, 
                node.getExpr().accept(this), null, loc);
        curNode.addStatement(s);
        return Argument.makeArgument(loc);
    }
  
    public Argument visit(MinusNode node) {
        VariableLocation loc = makeTemp(node, DecafType.INT);
    
        OpStatement s = new OpStatement(node, AsmOp.UNARY_MINUS, 
                node.getExpr().accept(this), null, loc);
        curNode.addStatement(s);
        return Argument.makeArgument(loc);
    }
   
    /*
     * Location and Constant visit methods 
     */
    public Argument visit(VariableNode node) {
        return node.getLoc().accept(this);
    }

    public Argument visit(ScalarLocationNode node) {
        return Argument.makeArgument(node.getDesc().getLocation());
    }
  
    public Argument visit(ArrayLocationNode node) {
        Argument a = node.getIndex().accept(this);
        return Argument.makeArgument(node.getDesc().getLocation(), a);
    }
  
    public Argument visit(BooleanNode node) {
        return Argument.makeArgument(node.getValue());
    }
  
    public Argument visit(IntNode node) {
        return Argument.makeArgument(node.getValue());
    }

    /*
     * Utility methods
     */
    private AsmOp getAsmOp(MathOpNode node) {
        switch(node.getOp()) {
        case ADD:
            return AsmOp.ADD;
        case SUBTRACT:
            return AsmOp.SUBTRACT;
        case MULTIPLY:
            return AsmOp.MULTIPLY;
        case DIVIDE:
            return AsmOp.DIVIDE;
        case MODULO:
            return AsmOp.MODULO;
        default:
            ErrorReporting.reportError(new CompilerException(node.getSourceLoc(), 
                    "MathOp " + node.getOp() + " cannot be converted into an AsmOp."));
        return null;
        }
    }
  
    private AsmOp getAsmOp(BoolOpNode node) {
        switch(node.getOp()) {
        case LE:
            return AsmOp.LESS_OR_EQUAL;
        case LT:
            return AsmOp.LESS_THAN;
        case GE:
            return AsmOp.GREATER_OR_EQUAL;
        case GT:
            return AsmOp.GREATER_THAN;
        case EQ:
            return AsmOp.EQUAL;
        case NEQ:
            return AsmOp.NOT_EQUAL;
        default:
            ErrorReporting.reportError(new CompilerException(node.getSourceLoc(), 
                    "BoolOp " + node.getOp() + " cannot be converted into an AsmOp."));
            return null;
        }
    }
}

