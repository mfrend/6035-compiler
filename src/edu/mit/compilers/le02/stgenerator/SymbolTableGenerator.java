package edu.mit.compilers.le02.stgenerator;

import java.util.List;
import java.util.ArrayList;

import edu.mit.compilers.le02.ast.ASTNode;
import edu.mit.compilers.le02.ast.ASTNodeVisitor;
import edu.mit.compilers.le02.ast.ArrayDeclNode;
import edu.mit.compilers.le02.ast.BlockNode;
import edu.mit.compilers.le02.ast.ClassNode;
import edu.mit.compilers.le02.ast.FieldDeclNode;
import edu.mit.compilers.le02.ast.ForNode;
import edu.mit.compilers.le02.ast.MethodDeclNode;
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
  public static SymbolTable generateSymbolTable(ASTNode root) {
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
  public SymbolTable createClassST(ClassNode root) {
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
    return st;
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
    SymbolTable methodSymbolTable = new SymbolTable(parent);
    List<String> params = new ArrayList<String>();
    currParent = methodSymbolTable;
    isParam = true;
    for (VarDeclNode v : node.getParams()) {
      ParamDescriptor param = (ParamDescriptor)v.accept(this);
      param.setIndex(i);
      i++;

      methodSymbolTable.put(v.getName(), param, v.getSourceLoc());
      params.add(v.getName());
    }
    isParam = false;

    // Create the local table for this block (and any nested blocks)
    BlockNode body = node.getBody();
    // Create and fill method locals
    for (VarDeclNode v : body.getDecls()) {
      methodSymbolTable.put(v.getName(), (LocalDescriptor) v.accept(this), 
                           v.getSourceLoc());
    }

    // Create the local symbol table for any nested blocks
    for (StatementNode s : body.getStatements()) {
      s.accept(this);
    }
    body.setSymbolTable(methodSymbolTable);
    
    currParent = parent;
    return new MethodDescriptor(parent, node.getName(), node.getType(),
                                methodSymbolTable, params,
                                node.getBody());
  }


  @Override
  public Descriptor visit(ForNode node) {
    BlockNode body = node.getBody();
    String name = node.getInit().getLoc().getName();

    body.accept(this);
    body.getSymbolTable().put(
      name, new LocalDescriptor(currParent, name, DecafType.INT),
      node.getInit().getLoc().getSourceLoc());
    return null;
  }


  @Override
  public Descriptor visit(BlockNode node) {
    SymbolTable parent = new SymbolTable(currParent);

    // Create and fill localSymbolTable
    SymbolTable localSymbolTable = new SymbolTable(parent);
    currParent = localSymbolTable;
    for (VarDeclNode v : node.getDecls()) {
      localSymbolTable.put(v.getName(), (LocalDescriptor) v.accept(this), 
                           v.getSourceLoc());
    }

    // Create the local symbol table for any nested blocks
    for (StatementNode s : node.getStatements()) {
      s.accept(this);
    }

    currParent = parent;
    node.setSymbolTable(localSymbolTable);
    return null;
  }

  @Override
  public Descriptor visit(ArrayDeclNode node) {
    return new FieldDescriptor(currParent, node.getName(), node.getType());
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
      return new LocalDescriptor(currParent, node.getName(), node.getType());
    }
  }
}
