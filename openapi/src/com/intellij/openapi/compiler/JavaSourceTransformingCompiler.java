/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * This compiler is called right before the java sources compiler
 */
public interface JavaSourceTransformingCompiler extends Compiler{

  /**
   * @param file an original file that is about to be compiled with java compiler
   * @return true if compiler would like to transform the file, false otherwise
   * If true is returned, a copy of ariginal file will be made and {@link #transform(CompileContext,com.intellij.openapi.vfs.VirtualFile,com.intellij.openapi.vfs.VirtualFile)}
   * method will be called on it. If transformation succeeded, the transformed copy will be passed to java compiler instead of original file
   */
  boolean isTransformable(VirtualFile file);

  /**
   * @param context
   * @param file a copy of original file to be transformed
   * @param originalFile  an original file. Since the copy that is supposed to be modified is located outside the project, it is not possible to use PSI for analysys.
   *  So the original file is provided. Note that it is passed for reference purposes only. It MUST NOT be transformed or changed in any way.
   *  For example, it is possible to obtain a PsiFile for the original file:<br><br>
   *   <code>PsiJavaFile originalPsiJavaFile = (JavaFile)PsiManager.getInstance(project).findFile(originalFile)</code>;<br><br>
   *  The obtained originalPsiJavaFile can be analysed, searched etc. For transforming the file by the means of PSI, there should be created a copy of the originalPsiJavaFile:<br><br>
   *   <code>PsiJavaFile psiFileCopy = (PsiJavaFile)originalPsiJavaFile.copy();</code><br><br>
   * The psiFileCopy can then be transformed, and its text saved to the first "file" argument:<br><br>
   *   <code>String text = psiFileCopy.getText();</code><br><br>
   *
   * <b>Note that transforming files by the means of PSI may be considerably slow down the overall make performance</b>  
   *
   * @return true is transform succeeded and false otherwise
   */
  boolean transform(CompileContext context, VirtualFile file, VirtualFile originalFile);
}
