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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
* @author Maxim.Medvedev
*/
class ClassNameDiffersFromFileNamePredicate implements PsiElementPredicate {
  private final boolean mySearchForClassInMultiClassFile;

  ClassNameDiffersFromFileNamePredicate(boolean searchForClassInMultiClassFile) {
    mySearchForClassInMultiClassFile = searchForClassInMultiClassFile;
  }

  ClassNameDiffersFromFileNamePredicate() {
    this(false);
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrTypeDefinition)) return false;
    final String name = ((GrTypeDefinition)element).getName();
    if (name == null || name.length() == 0) return false;
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof GroovyFile)) return false;
    if (!file.isPhysical()) return false;
    if (name.equals(FileUtil.getNameWithoutExtension(file.getName()))) return false;
    if (mySearchForClassInMultiClassFile) {
      return ((GroovyFile)file).getClasses().length > 1;
    }
    else {
      return !((GroovyFile)file).isScript();
    }
  }
}
