
/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsHandler;

public class EncapsulateFieldsAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length == 1) {
      return elements[0] instanceof PsiClass || isAcceptedField(elements[0]);
    }
    else if (elements.length > 1){
      for (int  idx = 0;  idx < elements.length;  idx++) {
        if (!isAcceptedField(elements[idx])) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new EncapsulateFieldsHandler();
  }

  private static boolean isAcceptedField(PsiElement element) {
    if (element instanceof PsiField) {
      if (((PsiField)element).getContainingClass() != null) {
        return true;
      }
    }
    return false;
  }
}