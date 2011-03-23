package edu.mit.compilers.le02.stgenerator;

import java.util.List;
import java.util.ArrayList;

import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ArrayDeclNode;
import edu.mit.compilers.le02.ast.AssignNode;
import edu.mit.compilers.le02.ast.BlockNode;
import edu.mit.compilers.le02.ast.BooleanNode;
import edu.mit.compilers.le02.ast.ClassNode;
import edu.mit.compilers.le02.ast.ExpressionNode;
import edu.mit.compilers.le02.ast.FieldDeclNode;
import edu.mit.compilers.le02.ast.ForNode;
import edu.mit.compilers.le02.ast.IntNode;
import edu.mit.compilers.le02.ast.MethodDeclNode;
import edu.mit.compilers.le02.ast.ScalarLocationNode;
import edu.mit.compilers.le02.ast.StatementNode;
import edu.mit.compilers.le02.ast.VarDeclNode;
import edu.mit.compilers.le02.DecafType;
import edu.mit.compilers.le02.symboltable.ClassDescriptor;
import edu.mit.compilers.le02.symboltable.Descriptor;
import edu.mit.compilers.le02.symboltable.FieldDescriptor;
import edu.mit.compilers.le02.symboltable.LocalDescriptor;
import edu.mit.compilers.le02.symboltable.MethodDescriptor;
import edu.mit.compilers.le02.symboltable.ParamDescriptor;
import edu.mit.compilers.le02.symboltable.SymbolTable;


public class SymbolTableGenerator extends ASTNodeVisitor<Descriptor> {
  private SymbolTable currParent = null;
  private boolean isField = false;
  private boolean isParam = false;

  /** Holds the SymbolTableVisitor singleton. */
  private static SymbolTableGenerator instance;

  /**
   * Retrieves the SymbolTableVisitor singleton, creating if necessary.
   */
  public static SymbolTableGenerator getInstance() {
    if (instance == null) {
      instance = new SymbolTableGenerator();
    }
    return instance;
  }

  /**
   * Generates an symbol table based on an input IR.
   */
  public static ClassDescriptor generateSymbolTable(ASTNode root) {
    assert(root instanceof ClassNode);
    return getInstance().createClassST((ClassNode)root);
  }

  /**
   * Converts a ClassNode into a SymbolTable.  The root of all ASTNode trees
   * must be a ClassNode, otherwise we should throw an exception
   *
   * @param root The root of our AST tree
   * @return SymbolTable The expanded SymbolTable
   */
  public ClassDescriptor createClassST(ClassNode root) {
    SymbolTable st = new SymbolTable(null);
    currParent = st;
    ClassDescriptor desc = (ClassDescriptor) root.accept(this);
    st.put(root.getName(), desc, root.getSourceLoc());

    // Set the descriptors for the AST
    ASTDescriptorVisitor v = new ASTDescriptorVisitor();
    v.setASTDescriptors(root, desc);

    // Set the parents for the AST (so they can find the symbol tables)
    ASTParentVisitor pv = new ASTParentVisitor();
    pv.visit(root);
    return desc;
  }

  @Override
  public Descriptor visit(ClassNode node) {
    SymbolTable parent = currParent;

    // Create and fill globalSymbolTable with fields
    SymbolTable globalSymbolTable = new SymbolTable(parent);
    currParent = globalSymbolTable;
    isField = true;
    for (FieldDeclNode n : node.getFields()) {
      globalSymbolTable.put(n.getName(), (FieldDescriptor) n.accept(this),
                            n.getSourceLoc());
    }
    isField = false;

    // Create and fill globalSymbolTable with methods
    for (MethodDeclNode m : node.getMethods()) {
      globalSymbolTable.put(m.getName(), (MethodDescriptor) m.accept(this),
                            m.getSourceLoc());
    }

    currParent = parent;
    return new ClassDescriptor(parent, node.getName(), globalSymbolTable);
  }

  @Override
  public Descriptor visit(MethodDeclNode node) {
    SymbolTable parent = currParent;
    int i = 0;

    // Create and fill paramSymbolTable
    currParent = new SymbolTable(parent);
    List<String> params = new ArrayList<String>();
    isParam = true;
    for (VarDeclNode v : node.getParams()) {
      ParamDescriptor param = (ParamDescriptor)v.accept(this);
      param.setIndex(i);
      i++;

      currParent.put(v.getName(), param, v.getSourceLoc());
      params.add(v.getName());
    }
    isParam = false;

    // Create the local table for this block and any nested blocks
    this.handleBlock(node.getBody());

    MethodDescriptor desc =
      new MethodDescriptor(parent, node.getName(), node.getType(),
                           currParent, params, node.getBody(),
                           node.getSourceLoc());
    node.setDescriptor(desc);

    currParent = parent;
    return desc;
  }


  @Override
  public Descriptor visit(ForNode node) {
    BlockNode body = node.getBody();
    String name = node.getInit().getLoc().getName();

    body.accept(this);
    int offset = body.getSymbolTable().getNonconflictingOffset();
    body.getSymbolTable().put(
      name, new LocalDescriptor(body.getSymbolTable(), name,
                                DecafType.INT, offset),
      node.getInit().getLoc().getSourceLoc());
    return null;
  }


  @Override
  public Descriptor visit(BlockNode node) {
    SymbolTable parent = currParent;
    currParent = new SymbolTable(parent);

    this.handleBlock(node);

    currParent = parent;
    return null;
  }

  private void handleBlock(BlockNode node) {
    // Fill the current symbol table with the block locals
    for (VarDeclNode v : node.getDecls()) {
      currParent.put(v.getName(), (LocalDescriptor) v.accept(this),
                     v.getSourceLoc());
      addLocalInitializer(node, v);
    }

    // Create the local symbol table for any nested blocks
    for (StatementNode s : node.getStatements()) {
      s.accept(this);
    }

    node.setSymbolTable(currParent);
  }

  private static void addLocalInitializer(BlockNode node, VarDeclNode decl) {
    ArrayList<StatementNode> statements =
        new ArrayList<StatementNode>(node.getStatements());
    ScalarLocationNode loc =
        new ScalarLocationNode(decl.getSourceLoc(), decl.getName());

    ExpressionNode val = new IntNode(decl.getSourceLoc(), 0);
    if (decl.getType() == DecafType.BOOLEAN) {
      val = new BooleanNode(decl.getSourceLoc(), false);
    }

    AssignNode init = new AssignNode(decl.getSourceLoc(), loc, val);
    statements.add(0, init);
    node.setStatements(statements);
  }

  @Override
  public Descriptor visit(ArrayDeclNode node) {
    return new FieldDescriptor(currParent, node.getName(), node.getType(),
      node.getLength());
  }

  @Override
  public Descriptor visit(VarDeclNode node) {
    if (isField) {
      return new FieldDescriptor(currParent, node.getName(), node.getType());
    }
    else if (isParam) {
      return new ParamDescriptor(currParent, node.getName(), node.getType());
    }
    else {
      return new LocalDescriptor(currParent, node.getName(), node.getType(),
                                 currParent.getNonconflictingOffset());
    }
  }
}
