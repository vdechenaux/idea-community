/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

/**
 * author: lesya
 */
public interface TransactionProvider {
  void startTransaction(Object parameters) throws VcsException;
  void commitTransaction(Object parameters) throws VcsException;
  void rollbackTransaction(Object parameters);

}
