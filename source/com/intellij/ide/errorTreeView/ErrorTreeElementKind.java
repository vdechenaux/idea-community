/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

import com.intellij.util.ui.MessageCategory;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class ErrorTreeElementKind {
  public static final ErrorTreeElementKind INFO = new ErrorTreeElementKind("INFO", "Information: ");
  public static final ErrorTreeElementKind ERROR = new ErrorTreeElementKind("ERROR", "Error: ");
  public static final ErrorTreeElementKind WARNING = new ErrorTreeElementKind("WARNING", "Warning: ");
  public static final ErrorTreeElementKind GENERIC = new ErrorTreeElementKind("GENERIC", "");

  private final String myText;
  private final String myPresentableText;

  private ErrorTreeElementKind(String text, String presentableText) {
    myText = text;
    myPresentableText = presentableText;
  }

  public String toString() {
    return myText; // for debug purposes
  }

  public String getPresentableText() {
    return myPresentableText;
  }

  public static ErrorTreeElementKind convertMessageFromCompilerErrorType(int type) {
    switch(type) {
      case MessageCategory.ERROR : return ERROR;
      case MessageCategory.WARNING : return WARNING;
      case MessageCategory.INFORMATION : return INFO;
      case MessageCategory.STATISTICS : return INFO;
      case MessageCategory.SIMPLE : return GENERIC;
      default : return GENERIC;
    }
  }
}
