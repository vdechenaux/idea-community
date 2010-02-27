/*
 * Copyright 2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for code style settings panels supporting multiple programming languages.
 *
 * @author rvishnyakov
 */
public abstract class MultilanguageCodeStyleAbstractPanel extends CodeStyleAbstractPanel {

  private Language myLanguage;
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.codeStyle.MultilanguageCodeStyleAbstractPanel");

  protected MultilanguageCodeStyleAbstractPanel(CodeStyleSettings settings) {
    super(settings);
  }

  /**
   * @return Always true for multilanguage panel.
   */
  @Override
  protected final boolean isMultilanguage() {
    return true;
  }

  public void setLanguage(Language language) {
    myLanguage = language;
    updatePreviewEditor();
  }

  protected abstract LanguageCodeStyleSettingsProvider.SettingsType getSettingsType();

  @Override
  protected String getPreviewText() {
    if (myLanguage == null) return "";
    return LanguageCodeStyleSettingsProvider.getCodeSample(myLanguage, getSettingsType());
  }

  @NotNull
  @Override
  protected final FileType getFileType() {
    if (myLanguage != null) {
      return myLanguage.getAssociatedFileType();
    }
    LanguageFileType availTypes[] = LanguageCodeStyleSettingsProvider.getLanguageFileTypes();
    if (availTypes.length > 0) {
      myLanguage = availTypes[0].getLanguage();
      return availTypes[0];
    }
    return StdFileTypes.JAVA;
  }

  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    if (getFileType() instanceof LanguageFileType) {
      return ((LanguageFileType)getFileType()).getEditorHighlighter(project, null, scheme);
    }
    return null;
  }


  protected PsiFile createFileFromText(Project project, String text) {
    final PsiFile psiFile =
      PsiFileFactory.getInstance(project).createFileFromText("a", getFileType(), text, LocalTimeCounter.currentTime(), true);
    return psiFile;
  }

  @Override
  protected PsiFile doReformat(final Project project, final PsiFile psiFile) {
    final String text = psiFile.getText();
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    final Document doc = manager.getDocument(psiFile);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            doc.replaceString(0, doc.getTextLength(), text);
            manager.commitDocument(doc);
            try {
              CodeStyleManager.getInstance(project).reformat(psiFile);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "", "");
    manager.commitDocument(doc);
    return psiFile;
  }
}