/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.JavaSourceTransformingCompiler;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 10, 2004
 */
public class DummyTransformingCompiler implements JavaSourceTransformingCompiler{
  public boolean isTransformable(VirtualFile file) {
    return "A.java".equals(file.getName());
  }

  public boolean transform(CompileContext context, final VirtualFile file, VirtualFile originalFile) {
    System.out.println("DummyTransformingCompiler.transform");
    final String url = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return file.getPresentableUrl();
      }
    });
    context.getProgressIndicator().setText("Transforming file: " + url);
    try {
      FileOutputStream fos = new FileOutputStream(new File(url));
      DataOutput out = new DataOutputStream(fos);
      out.writeBytes("package a; ");
      out.writeBytes("public class A { public static void main(String[] args) { System.out.println(\"Hello from modified class\");} }");
      fos.close();
      return true;
    }
    catch (FileNotFoundException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    return false;
  }

  public String getDescription() {
    return "a dummy compiler for testing";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }
}
