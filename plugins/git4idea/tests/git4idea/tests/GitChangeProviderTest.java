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
package git4idea.tests;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.pending.MockChangeListManagerGate;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.ui.GuiUtils;
import git4idea.GitVcs;
import git4idea.changes.GitChangeProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vcs.FileStatus.*;
import static org.testng.Assert.*;

/**
 * Tests GitChangeProvider functionality. Scenario is the same for all tests:
 * 1. Modifies files on disk (creates, edits, deletes, etc.)
 * 2. Manually adds them to a dirty scope (better to use VcsDirtyScopeManagerImpl, but it's too asynchronous - couldn't overcome this for now.
 * 3. Calls ChangeProvider.getChanges() and checks that the changes are there.
 * @author Kirill Likhodedov
 */
public class GitChangeProviderTest extends GitSingleUserTest {

  private GitChangeProvider myChangeProvider;
  private VcsModifiableDirtyScope myDirtyScope;
  private Map<String, VirtualFile> myFiles;
  private VirtualFile afile;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myChangeProvider = (GitChangeProvider) GitVcs.getInstance(myProject).getChangeProvider();
    myDirtyScope = new VcsDirtyScopeImpl(GitVcs.getInstance(myProject), myProject);

    myFiles = GitTestUtil.createFileStructure(myProject, myRepo, "a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt");
    afile = myFiles.get("a.txt"); // the file is commonly used, so save it in a field.
    myRepo.commit();
  }

  @Test
  public void testCreateFile() throws Exception {
    VirtualFile bfile = myRepo.createFile("new.txt");
    assertChanges(bfile, ADDED);
  }

  @Test
  public void testCreateFileInDir() throws Exception {
    VirtualFile dir = createDirInCommand(myRepo.getDir(), "newdir");
    VirtualFile bfile = createFileInCommand(dir, "new.txt", "initial b");
    assertChanges(new VirtualFile[] {bfile, dir}, new FileStatus[] { ADDED, null} );
  }

  @Test
  public void testEditFile() throws Exception {
    editFileInCommand(afile, "new content");
    assertChanges(afile, MODIFIED);
  }

  @Test
  public void testDeleteFile() throws Exception {
    deleteFileInCommand(afile);
    assertChanges(afile, DELETED);
  }

  @Test
  public void testDeleteDirRecursively() throws Exception {
    GuiUtils.runOrInvokeAndWait(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            FileUtil.delete(new File(myRepo.getDir().getPath(), "dir"));
          }
        });
      }
    });
    assertChanges(new VirtualFile[] { myFiles.get("dir/c.txt"), myFiles.get("dir/subdir/d.txt") }, new FileStatus[] { DELETED, DELETED });
  }

  @Test
  public void testSimultaneousOperationsOnMultipleFiles() throws Exception {
    VirtualFile dfile = myFiles.get("dir/subdir/d.txt");
    VirtualFile cfile = myFiles.get("dir/c.txt");

    editFileInCommand(afile, "new content");
    editFileInCommand(cfile, "new content");
    deleteFileInCommand(dfile);
    VirtualFile newfile = createFileInCommand("newfile.txt", "new content");

    assertChanges(new VirtualFile[] {afile, cfile, dfile, newfile}, new FileStatus[] {MODIFIED, MODIFIED, DELETED, ADDED});
  }

  /**
   * "modify-modify" merge conflict.
   * 1. Create a file and commit it.
   * 2. Create new branch and switch to it.
   * 3. Edit the file in that branch and commit.
   * 4. Switch to master, conflictly edit the file and commit.
   * 5. Merge the branch on master.
   * Merge conflict "modify-modify" happens.
   */
  @Test
  public void testConflictMM() throws Exception {
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.MODIFY);
    assertChanges(afile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Modify-Delete conflict.
   */
  @Test
  public void testConflictMD() throws Exception {
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.DELETE);
    assertChanges(afile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Delete-Modify conflict.
   */
  @Test
  public void testConflictDM() throws Exception {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.MODIFY);
    assertChanges(afile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Create a file with conflicting content.
   */
  @Test
  public void testConflictCC() throws Exception {
    modifyFileInBranches("z.txt", FileAction.CREATE, FileAction.CREATE);
    VirtualFile zfile = myRepo.getDir().findChild("z.txt");
    assertChanges(zfile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  @Test
  public void testConflictRD() throws Exception {
    modifyFileInBranches("a.txt", FileAction.RENAME, FileAction.DELETE);
    VirtualFile newfile = myRepo.getDir().findChild("a.txt_master_new"); // renamed in master
    assertChanges(newfile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  @Test
  public void testConflictDR() throws Exception {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.RENAME);
    VirtualFile newFile = myRepo.getDir().findChild("a.txt_feature_new"); // deleted in master, renamed in feature
    assertChanges(newFile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  private void modifyFileInBranches(String filename, FileAction masterAction, FileAction featureAction) throws Exception {
    myRepo.createBranch("feature");
    performActionOnFileAndRecordToIndex(filename, "feature", featureAction);
    myRepo.commit();
    myRepo.checkout("master");
    performActionOnFileAndRecordToIndex(filename, "master", masterAction);
    myRepo.commit();
    myRepo.merge("feature");
    myRepo.refresh();
  }

  private enum FileAction {
    CREATE, MODIFY, DELETE, RENAME
  }

  private void performActionOnFileAndRecordToIndex(String filename, String branchName, FileAction action) throws Exception {
    VirtualFile file = myRepo.getDir().findChild(filename);
    switch (action) {
      case CREATE:
        createFileInCommand(filename, "initial content in branch " + branchName);
        myRepo.add(filename);
        break;
      case MODIFY:
        editFileInCommand(file, "new content in branch " + branchName);
        myRepo.add(filename);
        break;
      case DELETE:
        myRepo.rm(filename);
        break;
      case RENAME:
        String name = filename + "_" + branchName.replaceAll("\\s", "_") + "_new";
        myRepo.mv(filename, name);
        break;
      default:
        break;
    }
  }

  /**
   * Checks that the given files have respective statuses in the change list retrieved from myChangesProvider.
   * Pass null in the fileStatuses array to indicate that proper file has not changed.
   */
  private void assertChanges(VirtualFile[] virtualFiles, FileStatus[] fileStatuses) throws VcsException {
    Map<FilePath, Change> result = getChanges(virtualFiles);
    for (int i = 0; i < virtualFiles.length; i++) {
      FilePath fp = new FilePathImpl(virtualFiles[i]);
      FileStatus status = fileStatuses[i];
      if (status == null) {
        assertFalse(result.containsKey(fp), "File [" + fp + " shouldn't be in the change list, but it was.");
        continue;
      }
      assertTrue(result.containsKey(fp), "File [" + fp + "] didn't change. Changes: " + result);
      assertEquals(result.get(fp).getFileStatus(), status, "File statuses don't match for file [" + fp + "]");
    }
  }

  private void assertChanges(VirtualFile virtualFile, FileStatus fileStatus) throws VcsException {
    assertChanges(new VirtualFile[] { virtualFile }, new FileStatus[] { fileStatus });
  }

  /**
   * Marks the given files dirty in myDirtyScope, gets changes from myChangeProvider and groups the changes in the map.
   * Assumes that only one change for a file happened.
   */
  private Map<FilePath, Change> getChanges(VirtualFile... changedFiles) throws VcsException {
    final List<FilePath> changedPaths = ObjectsConvertor.vf2fp(Arrays.asList(changedFiles));

    // populate dirty scope
    //for (FilePath path : changedPaths) {
    //  myDirtyScope.addDirtyFile(path);
    //}
    VcsDirtyScopeManagerImpl.getInstance(myProject).markEverythingDirty();
    myDirtyScope.addDirtyDirRecursively(new FilePathImpl(myRepo.getDir()));

    // get changes
    MockChangelistBuilder builder = new MockChangelistBuilder();
    myChangeProvider.getChanges(myDirtyScope, builder, new EmptyProgressIndicator(), new MockChangeListManagerGate(ChangeListManager.getInstance(myProject)));
    List<Change> changes = builder.getChanges();

    // get changes for files
    final Map<FilePath, Change> result = new HashMap<FilePath, Change>();
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      FilePath filePath = null;
      if (file == null) { // if a file was deleted, just find the reference in the original list of files and use it. 
        String path = change.getBeforeRevision().getFile().getPath();
        for (FilePath fp : changedPaths) {
          if (fp.getPath().equals(path)) {
            filePath = fp;
            break;
          }
        }
      } else {
        filePath = new FilePathImpl(file);
      }
      result.put(filePath, change);
    }
    return result;
  }

}
