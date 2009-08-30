package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class AbstractShowDiffAction extends AbstractVcsAction{

  protected void update(VcsContext vcsContext, Presentation presentation) {
    updateDiffAction(presentation, vcsContext, getKey());
  }

  protected static void updateDiffAction(final Presentation presentation, final VcsContext vcsContext,
                                         final VcsBackgroundableActions actionKey) {
    presentation.setEnabled(isEnabled(vcsContext, actionKey));
    presentation.setVisible(isVisible(vcsContext));
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected abstract VcsBackgroundableActions getKey();

  protected static boolean isVisible(final VcsContext vcsContext) {
    final Project project = vcsContext.getProject();
    if (project == null) return false;
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for (AbstractVcs vcs : vcss) {
      if (vcs.getDiffProvider() != null) {
        return true;
      }
    }
    return false;
  }

  protected static boolean isEnabled(final VcsContext vcsContext, final VcsBackgroundableActions actionKey) {
    if (!(isVisible(vcsContext))) return false;

    final Project project = vcsContext.getProject();
    if (project == null) return false;
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);

    final VirtualFile[] selectedFilePaths = vcsContext.getSelectedFiles();
    if (selectedFilePaths == null || selectedFilePaths.length != 1) return false;

    final VirtualFile selectedFile = selectedFilePaths[0];
    if (selectedFile.isDirectory()) return false;

    final BackgroundableActionEnabledHandler handler = ((ProjectLevelVcsManagerImpl)vcsManager).getBackgroundableActionHandler(actionKey);
    if (handler.isInProgress(VcsBackgroundableActions.keyFrom(selectedFile))) return false;

    final AbstractVcs vcs = vcsManager.getVcsFor(selectedFile);
    if (vcs == null) return false;

    final DiffProvider diffProvider = vcs.getDiffProvider();

    if (diffProvider == null) return false;

    return AbstractVcs.fileInVcsByFileStatus(project, new FilePathImpl(selectedFile)) ;
  }


  protected void actionPerformed(VcsContext vcsContext) {
    final Project project = vcsContext.getProject();
    final VirtualFile selectedFile = vcsContext.getSelectedFiles()[0];

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    final AbstractVcs vcs = vcsManager.getVcsFor(selectedFile);
    final DiffProvider diffProvider = vcs.getDiffProvider();

    final DiffActionExecutor actionExecutor = getExecutor(diffProvider, selectedFile, project);
    actionExecutor.showDiff();
  }

  protected DiffActionExecutor getExecutor(final DiffProvider diffProvider, final VirtualFile selectedFile, final Project project) {
    return new DiffActionExecutor.CompareToCurrentExecutor(diffProvider, selectedFile, project, getKey());
  }
}