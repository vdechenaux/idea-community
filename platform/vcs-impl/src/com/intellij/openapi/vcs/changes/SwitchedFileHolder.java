/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// true = recursively, branch name
public class SwitchedFileHolder extends RecursiveFileHolder<Pair<Boolean, String>> {
  public SwitchedFileHolder(final Project project, final HolderType holderType) {
    super(project, holderType);
  }

  public void takeFrom(final SwitchedFileHolder holder) {
    myMap.clear();
    myMap.putAll(holder.myMap);
  }

  public synchronized SwitchedFileHolder copy() {
    final SwitchedFileHolder copyHolder = new SwitchedFileHolder(myProject, myHolderType);
    copyHolder.myMap.putAll(myMap);
    return copyHolder;
  }

  @Override
  protected boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
    if (scope == null) return true;
    if (fileDropped(file)) return true;
    final VirtualFile parent = file.getParent();
    return (parent != null) && (scope.isRecursivelyDirty(parent));
  }

  public Map<VirtualFile, String> getFilesMapCopy() {
    final HashMap<VirtualFile, String> result = new HashMap<VirtualFile, String>();
    for (final VirtualFile vf : myMap.keySet()) {
      result.put(vf, myMap.getExact(vf).getSecond());
    }
    return result;
  }

  public void addFile(final VirtualFile file, final String branch, final boolean recursive) {
    // without optimization here
    myMap.put(file, new Pair<Boolean, String>(recursive, branch));
  }

  public synchronized MultiMap<String, VirtualFile> getBranchToFileMap() {
    final MultiMap<String, VirtualFile> result = new MultiMap<String, VirtualFile>();
    for (final VirtualFile vf : myMap.keySet()) {
      result.putValue(myMap.getExact(vf).getSecond(), vf);
    }
    return result;
  }

  @Override
  public synchronized boolean containsFile(final VirtualFile file) {
    final Pair<VirtualFile, Pair<Boolean, String>> mapping = myMap.getMapping(file);
    if (mapping != null) {
      return mapping.getFirst().equals(file) || mapping.getSecond().getFirst();
    }
    return false;
  }

  @Nullable
  public String getBranchForFile(final VirtualFile file) {
    final Pair<VirtualFile, Pair<Boolean, String>> mapping = myMap.getMapping(file);
    if (mapping != null) {
      if (mapping.getFirst().equals(file) || mapping.getSecond().getFirst()) {
        return mapping.getSecond().getSecond();
      }
    }
    return null;
  }

  public void calculateChildren() {
    myMap.optimizeMap(MyOptimizeProcessor.getInstance());
  }

  private static class MyOptimizeProcessor implements PairProcessor<Pair<Boolean, String>, Pair<Boolean, String>> {
    private final static MyOptimizeProcessor ourInstance = new MyOptimizeProcessor();

    public static MyOptimizeProcessor getInstance() {
      return ourInstance;
    }

    @Override
    public boolean process(final Pair<Boolean, String> parentPair, final Pair<Boolean, String> childPair) {
      return Boolean.TRUE.equals(parentPair.getFirst()) && parentPair.getSecond().equals(childPair.getSecond());
    }
  }
}
