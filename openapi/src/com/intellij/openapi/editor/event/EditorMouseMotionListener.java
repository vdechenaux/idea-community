/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import java.util.EventListener;

public interface EditorMouseMotionListener extends EventListener {
  void mouseMoved(EditorMouseEvent e);
  void mouseDragged(EditorMouseEvent e);
}
