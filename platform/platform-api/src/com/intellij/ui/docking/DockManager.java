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
package com.intellij.ui.docking;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Set;

public abstract class DockManager implements ProjectComponent {

  public abstract void register(DockContainer container);
  public abstract void register(String id, DockContainerFactory factory);

  public static DockManager getInstance(Project project) {
    return project.getComponent(DockManager.class);
  }

  public abstract DragSession createDragSession(MouseEvent mouseEvent, DockableContent content);

  public abstract Set<DockContainer> getContainers();

  public abstract IdeFrame getIdeFrame(DockContainer container);

  public abstract String getDimensionKeyForFocus(@NotNull String key);

  @Nullable
  public abstract DockContainer getContainerFor(Component c);
}
