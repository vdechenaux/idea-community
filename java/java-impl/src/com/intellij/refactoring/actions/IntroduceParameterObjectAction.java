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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceparameterobject.IntroduceParameterObjectHandler;

public class IntroduceParameterObjectAction extends BaseRefactoringAction {

  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(final PsiElement[] elements) {
    return elements.length == 1 && PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false) != null;
  }

  protected RefactoringActionHandler getHandler(DataContext context) {
    return new IntroduceParameterObjectHandler();
  }
}
