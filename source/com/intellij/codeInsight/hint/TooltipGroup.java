/**
 * @author cdr
 */
package com.intellij.codeInsight.hint;

public class TooltipGroup implements Comparable<TooltipGroup> {
  private final String myName;
  // the higher priority the more probable this tooltip will overlap other tooltips
  private final int myPriority;

  public TooltipGroup(String name, int priority) {
    myName = name;
    myPriority = priority;
  }

  public int compareTo(TooltipGroup tooltipGroup) {
    return myPriority - tooltipGroup.myPriority;
  }

  public String toString() {
    return myName;
  }
}