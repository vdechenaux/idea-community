/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

/**
 * @author dsl
 */
public interface ExportableOrderEntry extends OrderEntry {
  boolean isExported();
  void setExported(boolean value);
}
