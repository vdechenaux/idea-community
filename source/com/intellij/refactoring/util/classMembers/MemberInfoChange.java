/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:41:29
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.refactoring.util.classMembers.MemberInfo;

public class MemberInfoChange {
  private final MemberInfo[] myChangedMembers;

  public MemberInfoChange(MemberInfo[] changedMembers) {
    myChangedMembers = changedMembers;
  }

  public MemberInfo[] getChangedMembers() {
    return myChangedMembers;
  }
}
