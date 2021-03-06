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
package com.intellij.openapi.roots.ui.configuration.libraries.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class LibraryPresentationManagerImpl extends LibraryPresentationManager {
  private Map<LibraryKind<?>, LibraryPresentationProvider<?>> myPresentationProviders;

  private <P extends LibraryProperties> LibraryPresentationProvider<P> getPresentationProvider(LibraryKind<P> kind) {
    if (myPresentationProviders == null) {
      final Map<LibraryKind<?>, LibraryPresentationProvider<?>> providers = new HashMap<LibraryKind<?>, LibraryPresentationProvider<?>>();
      for (LibraryPresentationProvider provider : LibraryPresentationProvider.EP_NAME.getExtensions()) {
        providers.put(provider.getKind(), provider);
      }
      myPresentationProviders = providers;
    }
    //noinspection unchecked
    return (LibraryPresentationProvider<P>)myPresentationProviders.get(kind);
  }

  @NotNull
  @Override
  public Icon getNamedLibraryIcon(@NotNull Library library, @Nullable StructureConfigurableContext context) {
    final Icon icon = getCustomIcon(library, context);
    return icon != null ? icon : Icons.LIBRARY_ICON;
  }

  @Override
  public Icon getCustomIcon(@NotNull Library library, StructureConfigurableContext context) {
    final LibraryType<?> type = ((LibraryEx)library).getType();
    if (type != null) {
      return type.getIcon();
    }
    final List<Icon> icons = getCustomIcons(library, context);
    if (icons.size() == 1) {
      return icons.get(0);
    }
    return null;
  }

  @NotNull
  @Override
  public List<Icon> getCustomIcons(@NotNull Library library, StructureConfigurableContext context) {
    final VirtualFile[] files = getLibraryFiles(library, context);
    final List<Icon> icons = new SmartList<Icon>();
    LibraryDetectionManager.getInstance().processProperties(Arrays.asList(files), new LibraryDetectionManager.LibraryPropertiesProcessor() {
      @Override
      public <P extends LibraryProperties> boolean processProperties(@NotNull LibraryKind<P> kind, @NotNull P properties) {
        final LibraryPresentationProvider<P> provider = getPresentationProvider(kind);
        if (provider != null) {
          ContainerUtil.addIfNotNull(icons, provider.getIcon());
        }
        return true;
      }
    });
    return icons;
  }

  @Override
  public boolean isLibraryOfKind(@NotNull List<VirtualFile> files, @NotNull final LibraryKind<?> kind) {
    return !LibraryDetectionManager.getInstance().processProperties(files, new LibraryDetectionManager.LibraryPropertiesProcessor() {
      @Override
      public <P extends LibraryProperties> boolean processProperties(@NotNull LibraryKind<P> processedKind, @NotNull P properties) {
        return !kind.equals(processedKind);
      }
    });
  }

  @Override
  public boolean isLibraryOfKind(@NotNull List<VirtualFile> files, @NotNull final Set<? extends LibraryKind<?>> acceptedKinds) {
    return !LibraryDetectionManager.getInstance().processProperties(files, new LibraryDetectionManager.LibraryPropertiesProcessor() {
      @Override
      public <P extends LibraryProperties> boolean processProperties(@NotNull LibraryKind<P> processedKind, @NotNull P properties) {
        return !acceptedKinds.contains(processedKind);
      }
    });
  }

  public static List<LibraryKind<?>> getLibraryKinds(@NotNull Library library, StructureConfigurableContext context) {
    final List<LibraryKind<?>> result = new SmartList<LibraryKind<?>>();
    final VirtualFile[] files = getLibraryFiles(library, context);
    LibraryDetectionManager.getInstance().processProperties(Arrays.asList(files), new LibraryDetectionManager.LibraryPropertiesProcessor() {
      @Override
      public <P extends LibraryProperties> boolean processProperties(@NotNull LibraryKind<P> kind, @NotNull P properties) {
        result.add(kind);
        return true;
      }
    });
    return result;
  }

  @NotNull
  @Override
  public List<String> getDescriptions(@NotNull Library library, StructureConfigurableContext context) {
    final VirtualFile[] files = getLibraryFiles(library, context);
    return getDescriptions(files);
  }

  @NotNull
  private static VirtualFile[] getLibraryFiles(@NotNull Library library, @Nullable StructureConfigurableContext context) {
    if (((LibraryEx)library).isDisposed()) {
      return VirtualFile.EMPTY_ARRAY;
    }
    return context != null ? context.getLibraryFiles(library, OrderRootType.CLASSES) : library.getFiles(OrderRootType.CLASSES);
  }

  @NotNull
  @Override
  public List<String> getDescriptions(@NotNull VirtualFile[] classRoots) {
    final SmartList<String> result = new SmartList<String>();
    LibraryDetectionManager.getInstance().processProperties(Arrays.asList(classRoots), new LibraryDetectionManager.LibraryPropertiesProcessor() {
      @Override
      public <P extends LibraryProperties> boolean processProperties(@NotNull LibraryKind<P> kind, @NotNull P properties) {
        final LibraryPresentationProvider<P> provider = getPresentationProvider(kind);
        if (provider != null) {
          ContainerUtil.addIfNotNull(result, provider.getDescription(properties));
        }
        return true;
      }
    });
    return result;
  }

  @Override
  public List<Library> getLibraries(@NotNull Set<LibraryKind<?>> kinds, @NotNull Project project, @Nullable StructureConfigurableContext context) {
    List<Library> libraries = new ArrayList<Library>();
    if (context != null) {
      Collections.addAll(libraries, context.getProjectLibrariesProvider().getModifiableModel().getLibraries());
      Collections.addAll(libraries, context.getGlobalLibrariesProvider().getModifiableModel().getLibraries());
    }
    else {
      final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
      Collections.addAll(libraries, registrar.getLibraryTable(project).getLibraries());
      Collections.addAll(libraries, registrar.getLibraryTable().getLibraries());
    }

    final Iterator<Library> iterator = libraries.iterator();
    while (iterator.hasNext()) {
      Library library = iterator.next();
      final List<LibraryKind<?>> libraryKinds = getLibraryKinds(library, context);
      if (!ContainerUtil.intersects(libraryKinds, kinds)) {
        iterator.remove();
      }
    }
    return libraries;
  }
}
