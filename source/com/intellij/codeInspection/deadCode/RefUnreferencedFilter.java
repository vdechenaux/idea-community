/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 2, 2001
 * Time: 12:14:37 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefField;
import com.intellij.codeInspection.reference.RefParameter;

public class RefUnreferencedFilter extends RefUnreachableFilter {
  public int getElementProblemCount(RefElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    if (refElement.isEntry() || !refElement.isSuspicious()) return 0;

    if (refElement instanceof RefField) {
      RefField refField = (RefField) refElement;
      if (refField.isUsedForReading() && !refField.isUsedForWriting()) return 1;
      if (refField.isUsedForWriting() && !refField.isUsedForReading()) return 1;
    }

    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) return 0;
    if (!refElement.hasSuspiciousCallers() || refElement.isSuspiciousRecursive()) return 1;

    return 0;
  }
}
