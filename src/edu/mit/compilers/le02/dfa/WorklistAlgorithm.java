package edu.mit.compilers.le02.dfa;

import java.util.Collection;
import java.util.LinkedList;

public class WorklistAlgorithm {
  /*
   */
  public static <T> void runForward(Collection<? extends WorklistItem<T>> items,
      Lattice<T, ?> lattice,
      WorklistItem<T> startItem,
      T startInfo) {
    LinkedList<WorklistItem<T>> worklist =
      new LinkedList<WorklistItem<T>>(items);

    // Initialize edge maps
    for (WorklistItem<T> item : items) {
      item.setIn(lattice.bottom());
      item.setOut(item.transferFunction(lattice.bottom()));
    }

    // Initialize the first item
    startItem.setIn(startInfo);
    startItem.setOut(startItem.transferFunction(startInfo));
    boolean validItems = worklist.remove(startItem);

    // Assert that startItem was in the given items
    assert validItems;

    while (!worklist.isEmpty()) {
      WorklistItem<T> item = worklist.remove();

      // Calculate the least upper bound of all the predecessors
      T sup = lattice.bottom();
      for(WorklistItem<T> pred : item.predecessors()) {
        sup = lattice.leastUpperBound(pred.getOut(), sup);
      }
      item.setIn(sup);

      // Calculate the new out value for this item
      T newOut = item.transferFunction(sup);

      // If the value has changed, update it and add successors
      // to the worklist.
      if (!newOut.equals(item.getOut())) {
        item.setOut(newOut);

        worklist.addAll(item.successors());
      }
    }
  }

  // Before calling runBackwards, you need to ensure that every
  // WorklistItem with no successors has an out set
  // These out sets are the initial conditions for the algorithm
  public static <T> void runBackwards(
      Collection<? extends WorklistItem<T>> items,
      Lattice<T, ?> lattice) {
    LinkedList<WorklistItem<T>> worklist =
      new LinkedList<WorklistItem<T>>(items);

    // Initialize edge maps
    for (WorklistItem<T> item : items) {
      if (!item.successors().isEmpty()) {
        item.setOut(lattice.bottom());
      } else {
        worklist.remove(item);
      }
      item.setIn(item.transferFunction(item.getOut()));
    }

    while (!worklist.isEmpty()) {
      WorklistItem<T> item = worklist.remove();

      // Calculate the least upper bound of all the successors
      T sup = lattice.bottom();
      for(WorklistItem<T> succ : item.successors()) {
        sup = lattice.leastUpperBound(succ.getIn(), sup);
      }
      item.setOut(sup);

      // Calculate the new out value for this item
      T newIn = item.transferFunction(sup);

      // If the value has changed, update it and add predecessors
      // to the worklist.
      if (!newIn.equals(item.getIn())) {
        item.setIn(newIn);

        worklist.addAll(item.predecessors());
      }
    }
  }
}
