/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Maxim.Medvedev
 */
public class MoveClassToNewFileIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final GrTypeDefinition psiClass = (GrTypeDefinition)element;
    final String name = psiClass.getName();

    final PsiFile file = psiClass.getContainingFile();
    final String fileExtension = FileUtil.getExtension(file.getName());
    final String newFileName = name + "." + fileExtension;
    final PsiDirectory dir = file.getParent();
    if (dir != null) {
      if (dir.findFile(newFileName) != null) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          CommonRefactoringUtil
            .showErrorHint(project, editor, GroovyIntentionsBundle.message("file.exists", newFileName, dir.getName()), getFamilyName(), null);
        }
        return;
      }
    }

    final GroovyFile newFile = (GroovyFile)GroovyTemplatesFactory.createFromTemplate(dir, name, newFileName, "GroovyClass.groovy");
    final GrTypeDefinition template = newFile.getTypeDefinitions()[0];
    final PsiElement newClass = template.replace(psiClass);
    psiClass.delete();
    CreateClassActionBase.putCursor(project, newClass.getContainingFile(), newClass.getNavigationElement());
  }


  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ClassNameDiffersFromFileNamePredicate(true);
  }
}
