/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * An interface allowing access and modification of the data associated with the current compile session
 */
public interface CompileContext extends UserDataHolder {

  /**
   * Allows to add a message to be shown in Compiler message view.
   * If correct url, line and columnt numers are supplied, the navigation to the specified file is available from the view.
   *
   * @param category the category of a message (information, error, warning)
   * @param message the text of the message
   * @param url a url to the file to which the message applies, null if not available
   * @param lineNum a line number, -1 if not available
   * @param columnNum a columnt number, -1 if not available
   */
  void addMessage(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum);

  /**
   * @param category
   * @return all compiler messages of the specified category
   */
  CompilerMessage[] getMessages(CompilerMessageCategory category);

  /**
   * @param category
   * @return the number of messages of the specified category
   */
  int getMessageCount(CompilerMessageCategory category);

  /**
   * @return a progress indicator of the compilation process
   */
  ProgressIndicator getProgressIndicator();

  /**
   * @return current compile scope
   */
  CompileScope getCompileScope();

  /**
   * A compiler may call this method in order to request complete project rebuild.
   * This may be neccesary, for example, when compiler caches are corrupted
   */
  void requestRebuildNextTime(String message);

  /**
   * This method is aware of the file->module mapping for generated files. Compilers should use this method in order to
   * determine the module to wchich the specified file belongs
   * @param file
   * @return the module to which the file belongs
   */
  Module getModuleByFile(VirtualFile file);

  /**
   * @return module's source roots as well as source roots for generated sources that are attributed to the module
   */
  VirtualFile[] getSourceRoots(Module module);

  /**
   * @return a list of all configured output directories from all modules (including output directories for tests)
   */
  VirtualFile[] getAllOutputDirectories();

  /**
   * @param module
   * @return the output directory for the module specified, null if corresponding VirtualFile is not valid or directory not specified
   */
  VirtualFile getModuleOutputDirectory(Module module);

  /**
   * @param module
   * @return the tests output directory the module specified, null if corresponding VirtualFile is not valid. If in Paths settings
   * output directory for tests is not configured explicitly, but the output path is present, the output path will be returned.
   */
  VirtualFile getModuleOutputDirectoryForTests(Module module);
}
