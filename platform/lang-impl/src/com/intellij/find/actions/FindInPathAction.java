
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

package com.intellij.find.actions;

import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public class FindInPathAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = e.getData(PlatformDataKeys.PROJECT);
    
    FindInProjectManager.getInstance(project).findInProject(dataContext);
  }

  public void update(AnActionEvent e){
    Presentation presentation = e.getPresentation();
    Project project = e.getData(PlatformDataKeys.PROJECT);
    presentation.setEnabled(project != null);
    if (project != null) {
      FindInProjectManager findManager = FindInProjectManager.getInstance(project);
      presentation.setEnabled(findManager.isEnabled());
    }
  }
}
