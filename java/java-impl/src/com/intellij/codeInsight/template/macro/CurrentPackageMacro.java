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
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;


/**
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: May 13, 2003
 * Time: 8:36:42 PM
 * To change this template use Options | File Templates.
 */
class CurrentPackageMacro implements Macro {
  public String getName() {
    return "currentPackage";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.current.package");
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    Project project = context.getProject();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    if (!(file instanceof PsiJavaFile)) return new TextResult ("");
    PsiDocumentManager.getInstance(project).commitDocument(context.getEditor().getDocument());
    return new TextResult (((PsiJavaFile)file).getPackageName());
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    return LookupElement.EMPTY_ARRAY;
  }


}
