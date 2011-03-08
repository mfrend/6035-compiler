package edu.mit.compilers.le02.symboltable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mit.compilers.le02.ErrorReporting;
import edu.mit.compilers.le02.SourceLocation;
import edu.mit.compilers.le02.stgenerator.SymbolTableException;

public class SymbolTable {
  private SymbolTable parent;
  private Map<String, Descriptor> table;
  private List<LocalDescriptor> locals;
  private List<ParamDescriptor> params;
  private List<FieldDescriptor> fields;
  private List<MethodDescriptor> methods;

  public enum SymbolType {
    METHOD,
    VARIABLE,
    EITHER
  }

  public SymbolTable(SymbolTable parent) {
    this.parent = parent;
    this.table = new HashMap<String, Descriptor>();
    
    this.locals = new ArrayList<LocalDescriptor>(this.parent.locals);
    this.params = new ArrayList<ParamDescriptor>(this.parent.params);
    this.fields = new ArrayList<FieldDescriptor>(this.parent.fields);
    this.methods = new ArrayList<MethodDescriptor>(this.parent.methods);
  }

  public boolean put(String id, ClassDescriptor descriptor, SourceLocation sl) {
    return this.putHelper(id, descriptor, sl);
  }
  
  public boolean put(String id, LocalDescriptor descriptor, SourceLocation sl) {
    this.locals.add(descriptor);
    return this.putHelper(id, descriptor, sl);
  }

  public boolean put(String id, ParamDescriptor descriptor, SourceLocation sl) {
    this.params.add(descriptor);
    return this.putHelper(id, descriptor, sl);
  }

  public boolean put(String id, FieldDescriptor descriptor, SourceLocation sl) {
    this.fields.add(descriptor);
    return this.putHelper(id, descriptor, sl);
  }

  public boolean put(String id, 
                     MethodDescriptor descriptor, SourceLocation sl) {
    this.methods.add(descriptor);
    return this.putHelper(id, descriptor, sl);
  }
  
  /**
   * Add a new entry to the symbol table. Verify that it does not already
   * exist in this table or any ancestor
   *
   * @param id The identifier of the new entry
   * @param descriptor The descriptor of the new entry
   * @return True if entry was successful
   */
  private boolean putHelper(String id, 
                            Descriptor descriptor, SourceLocation sl) {
    if (table.containsKey(id)) {
      ErrorReporting.reportError(
      new SymbolTableException(sl, "Duplicate identifier " + id));
      return false;
    } else {
      if (descriptor != null) {
        this.table.put(id, descriptor);
      }
      return true;
    }
  }

  /**
   * Finds a descriptor and recurses upwards until found or at top
   * @param id The id of the descriptor
   * @param type Whether the string is a primitive or a method,
   *                  or either
   * @return Returns the requested descriptor, or null if not found
   */
  public Descriptor get(String id, SymbolType type) {
    Descriptor d;
    SymbolTable st = this;
    while (st != null) {
      if (st.getMap().containsKey(id)) {
        d = st.getMap().get(id);
        if (type == SymbolType.EITHER) {
          return d;
        } else if ((type == SymbolType.VARIABLE
                    && d instanceof TypedDescriptor
                    && !(d instanceof MethodDescriptor)) ||
                   (type == SymbolType.METHOD
                    && d instanceof MethodDescriptor)) {
          return d;
        } else {
          // Found descriptor of the wrong type
          return null;
        }

      }
      st = st.getParent();
    }
    return null;
  }



  /**
   * Convenience method to get a FieldDescriptor
   * @param id The id of the descriptor
   * @return Returns the requested descriptor, or null if not found
   * @see SymbolTable.get
   */
  public FieldDescriptor getField(String id) {
    return (FieldDescriptor) get(id, SymbolType.VARIABLE);
  }


  /**
   * Convenience method to get a ParamDescriptor
   * @param id The id of the descriptor
   * @return Returns the requested descriptor, or null if not found
   * @see SymbolTable.get
   */
  public ParamDescriptor getParam(String id) {
    return (ParamDescriptor) get(id, SymbolType.VARIABLE);
  }


  /**
   * Convenience method to get a LocalDescriptor
   * @param id The id of the descriptor
   * @return Returns the requested descriptor, or null if not found
   * @see SymbolTable.get
   */
  public LocalDescriptor getLocal(String id) {
    return (LocalDescriptor) get(id, SymbolType.VARIABLE);
  }

  /**
   * Convenience method to get a TypedDescriptor for a variable
   * @param id The id of the descriptor
   * @return Returns the requested descriptor, or null if not found
   * @see SymbolTable.get
   */
  public TypedDescriptor getTypedVar(String id) {
    return (TypedDescriptor) get(id, SymbolType.VARIABLE);
  }

  /**
   * Convenience method to get a MethodDescriptor
   * @param id The id of the descriptor
   * @return Returns the requested descriptor, or null if not found
   * @see SymbolTable.get
   */
  public MethodDescriptor getMethod(String id) {
    return (MethodDescriptor) get(id, SymbolType.METHOD);
  }
  
  public int getHighestLocalIndex() {
    Comparator<LocalDescriptor> c = new Comparator<LocalDescriptor>() {
      public int compare(LocalDescriptor d1, LocalDescriptor d2) {
        return d1.getIndex() - d2.getIndex();
      }
    };
    
    return Collections.max(locals, c).getIndex();
  }

  /**
   * Checks if this symbol table or any ancestor contains the query id
   *
   * @param id The id to be searched for
   * @param primitive Whether the string is a primitive or a method,
   *                  null for either
   * @return True if found, false otherwise
   */
  public boolean contains(String id, SymbolType primitive) {
    SymbolTable st = this;
    Descriptor desc = st.get(id, primitive);
    return (desc != null);
  }

  public SymbolTable getParent() {
    return this.parent;
  }

  private Map<String, Descriptor> getMap() {
    return table;
  }

  public int size() {
    if (parent == null) {
      return table.size();
    }

    return table.size() + parent.size();
  }

  @Override
  public String toString() {
    String output = "";
    for (String k : this.table.keySet()) {
      output += k + ":[" + table.get(k).toString() + "],";
    }
    if (output.length() > 0) {
      output = output.substring(0, output.length() - 1);
    }
    return output;
  }

}
