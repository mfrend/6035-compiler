package edu.mit.compilers.le02.cfg;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import edu.mit.compilers.le02.RegisterLocation.Register;

/**
 * A simple class for recording whether or not registers are live.  All
 * registers are referred to by their 64-bit versions.
 * 
 * TODO: This can be more efficient by using BitSets or something similar
 * 
 * @author David Koh (dkoh@mit.edu)
 *
 */
public class RegisterLiveness {
  // Records the registers that are live for this statement
  private Set<Register> liveRegisters = new HashSet<Register>();
  
  // Records the registers that die on this statement (this statement is
  // their last use)
  private Set<Register> dyingRegisters = new HashSet<Register>();
  
  private static Set<Register> allRegisters = null;
  private static Set<Register> calleeRegisters = null;
  
  public static Set<Register> getAllRegisters() {
    if (allRegisters == null) {
      allRegisters = new HashSet<Register>();
      allRegisters.add(Register.RAX);
      allRegisters.add(Register.RBX);
      allRegisters.add(Register.RCX);
      allRegisters.add(Register.RDX);
      allRegisters.add(Register.RDI);
      allRegisters.add(Register.RSI);
      // We reserve RBP and RSP so as not to mess up stack calculations
      allRegisters.add(Register.RBP);
      allRegisters.add(Register.RSP);
      allRegisters.add(Register.R8);
      allRegisters.add(Register.R9);
      allRegisters.add(Register.R10);
      allRegisters.add(Register.R11);
      allRegisters.add(Register.R12);
      allRegisters.add(Register.R13);
      allRegisters.add(Register.R14);
      allRegisters.add(Register.R15);
    }
    return Collections.unmodifiableSet(allRegisters);
  }
  
  public static Set<Register> getCalleeSavedRegisters() {
    if (calleeRegisters == null) {
      calleeRegisters = new HashSet<Register>();
      calleeRegisters.add(Register.RBX);
      calleeRegisters.add(Register.RBP);
      calleeRegisters.add(Register.RSP);
      calleeRegisters.add(Register.R12);
      calleeRegisters.add(Register.R13);
      calleeRegisters.add(Register.R14);
      calleeRegisters.add(Register.R15);
    }
    return Collections.unmodifiableSet(calleeRegisters);
  }
  
  public RegisterLiveness() {
    liveRegisters.add(Register.RBP);
    liveRegisters.add(Register.RSP);
  }
  
  public boolean registerIsLive(Register r) {
    return liveRegisters.contains(r.sixtyFour());
  }
  
  public void setRegisterLiveness(Register r, boolean live) { 
    if (live) {
      liveRegisters.add(r.sixtyFour());
    } else {
      liveRegisters.remove(r.sixtyFour());
    }
  }
  
  public void setRegisterDying(Register r, boolean dying) {
    if (dying) {
      dyingRegisters.add(r.sixtyFour());
    } else {
      dyingRegisters.remove(r.sixtyFour());
    }
  }
  
  public Set<Register> getLiveRegisters() {
    return Collections.unmodifiableSet(liveRegisters);
  }
  
  public Set<Register> getNonDyingRegisters() {
    HashSet<Register> set = new HashSet<Register>(liveRegisters);
    set.removeAll(dyingRegisters);
    return Collections.unmodifiableSet(set);
  }
  
  public Set<Register> getLiveCalleeSavedRegisters() {
    HashSet<Register> set = new HashSet<Register>(liveRegisters);
    set.retainAll(getCalleeSavedRegisters());
    return Collections.unmodifiableSet(set);
  }
  
  public Set<Register> getNonDyingCallerSavedRegisters() {
    HashSet<Register> set = new HashSet<Register>(liveRegisters);
    set.removeAll(dyingRegisters);
    set.removeAll(getCalleeSavedRegisters());
    return Collections.unmodifiableSet(set);
  }
  
  public Set<Register> getDeadRegisters() {
    HashSet<Register> dead = new HashSet<Register>(getAllRegisters());
    dead.removeAll(liveRegisters);
    return Collections.unmodifiableSet(dead);
  }
  
  
}
