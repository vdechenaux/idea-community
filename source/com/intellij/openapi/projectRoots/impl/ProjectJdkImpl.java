package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.projectRoots.ex.ProjectRootContainer;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;

import java.util.*;

public class ProjectJdkImpl implements JDOMExternalizable, ProjectJdk, SdkModificator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectJdkImpl");
  private final ProjectRootContainerImpl myRootContainer;
  private String myName;
  private String myVersionString;
  private String myHomePath = "";
  private final MyRootProvider myRootProvider = new MyRootProvider();
  private ProjectJdkImpl myOrigin = null;
  private SdkAdditionalData myAdditionalData = null;
  private SdkType mySdkType;

  public ProjectJdkImpl(String name, SdkType sdkType) {
    mySdkType = sdkType;
    myRootContainer = new ProjectRootContainerImpl(true);
    myName = name;
    myRootContainer.addProjectRootContainerListener(myRootProvider);
  }

  public SdkType getSdkType() {
    return mySdkType;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    LOG.assertTrue(name != null);
    myName = name;
  }

  public final void setVersionString(String versionString) {
    myVersionString = (versionString == null || "".equals(versionString)) ? null : versionString;
  }

  public String getVersionString() {
    if (myVersionString == null) {
      String homePath = getHomePath();
      if (homePath != null && homePath.length() > 0) {
        this.setVersionString(mySdkType.getVersionString(homePath));
      }
    }
    return myVersionString;
  }

  public String getHomePath() {
    return myHomePath;
  }

  public VirtualFile getHomeDirectory() {
    if (myHomePath == null) {
      return null;
    }
    return LocalFileSystem.getInstance().findFileByPath(myHomePath);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myName = element.getChild("name").getAttributeValue("value");
    final Element typeChild = element.getChild("type");
    final String sdkTypeName = typeChild != null? typeChild.getAttributeValue("value") : null;
    if (sdkTypeName != null) {
      mySdkType = getSdkTypeByName(sdkTypeName);
    }
    else {
      // assume java sdk by default
      mySdkType = JavaSdk.getInstance();
    }
    final Element version = element.getChild("version");
    this.setVersionString((version != null) ? version.getAttributeValue("value") : null);

    if (element.getAttribute("version") == null || !"2".equals(element.getAttributeValue("version"))) {
      myRootContainer.startChange();
      myRootContainer.readOldVersion(element.getChild("roots"));
      final List children = element.getChild("roots").getChildren("root");
      for (Iterator iterator = children.iterator(); iterator.hasNext();) {
        Element root = (Element)iterator.next();
        for (Iterator j = root.getChildren("property").iterator(); j.hasNext();) {
          Element prop = (Element)j.next();
          if ("type".equals(prop.getAttributeValue("name")) && "jdkHome".equals(prop.getAttributeValue("value"))) {
            myHomePath = VirtualFileManager.extractPath(root.getAttributeValue("file"));
          }
        }
      }
      myRootContainer.finishChange();
    }
    else {
      myHomePath = element.getChild("homePath").getAttributeValue("value");
      myRootContainer.readExternal(element.getChild("roots"));
    }

    final Element additional = element.getChild("additional");
    myAdditionalData = (additional != null)? mySdkType.loadAdditionalData(additional) : null;
  }

  private static SdkType getSdkTypeByName(String sdkTypeName) {
    final SdkType[] allSdkTypes = ApplicationManager.getApplication().getComponents(SdkType.class);
    for (int idx = 0; idx < allSdkTypes.length; idx++) {
      final SdkType type = allSdkTypes[idx];
      if (type.getName().equals(sdkTypeName)) {
        return type;
      }
    }
    return UnknownSdkType.getInstance(sdkTypeName);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute("version", "2");

    final Element name = new Element("name");
    name.setAttribute("value", myName);
    element.addContent(name);

    if (mySdkType != null) {
      final Element sdkType = new Element("type");
      sdkType.setAttribute("value", mySdkType.getName());
      element.addContent(sdkType);
    }

    if (myVersionString != null) {
      final Element version = new Element("version");
      version.setAttribute("value", myVersionString);
      element.addContent(version);
    }

    final Element home = new Element("homePath");
    home.setAttribute("value", myHomePath);
    element.addContent(home);

    Element roots = new Element("roots");
    myRootContainer.writeExternal(roots);
    element.addContent(roots);

    Element additional = new Element("additional");
    if (myAdditionalData != null) {
      mySdkType.saveAdditionalData(myAdditionalData, additional);
    }
    element.addContent(additional);
  }

  public void setHomePath(String path) {
    final boolean changes = myHomePath == null? path != null : !myHomePath.equals(path);
    myHomePath = path;
    if (changes) {
      myVersionString = null; // clear cached value if home path changed
    }
  }

  public final String getBinPath() {
    return mySdkType.getBinPath(this);
  }

  public final String getToolsPath() {
    return mySdkType.getToolsPath(this);
  }

  public final String getVMExecutablePath() {
    return mySdkType.getVMExecutablePath(this);
  }

  public final String getRtLibraryPath() {
    return mySdkType.getRtLibraryPath(this);
  }

  public Object clone() throws CloneNotSupportedException {
    ProjectJdkImpl newJdk = new ProjectJdkImpl("", mySdkType);
    copyTo(newJdk);
    return newJdk;
  }

  public RootProvider getRootProvider() {
    return myRootProvider;
  }

  public void copyTo(ProjectJdkImpl dest){
    final String previousName = dest.getName();
    final String name = getName();
    dest.setName(name);
    dest.setHomePath(getHomePath());
    dest.setVersionString(getVersionString());
    dest.setSdkAdditionalData(getSdkAdditionalData());
    dest.myRootContainer.startChange();
    dest.myRootContainer.removeAllRoots();
    copyRoots(myRootContainer, dest.myRootContainer, ProjectRootType.CLASS);
    copyRoots(myRootContainer, dest.myRootContainer, ProjectRootType.SOURCE);
    copyRoots(myRootContainer, dest.myRootContainer, ProjectRootType.JAVADOC);
    dest.myRootContainer.finishChange();
  }

  private static void copyRoots(ProjectRootContainer srcContainer, ProjectRootContainer destContainer, ProjectRootType type){
    final ProjectRoot[] newRoots = srcContainer.getRoots(type);
    for (int i = 0; i < newRoots.length; i++){
      destContainer.addRoot(newRoots[i], type);
    }
  }

  private class MyRootProvider extends RootProviderBaseImpl implements ProjectRootListener {
    public String[] getUrls(OrderRootType rootType) {
      final VirtualFile[] rootFiles = myRootContainer.getRootFiles((ProjectRootType)ourOrderRootsToProjectRoots.get(rootType));
      final ArrayList<String> result = new ArrayList<String>();
      for (int i = 0; i < rootFiles.length; i++) {
        result.add(rootFiles[i].getUrl());
      }
      return result.toArray(new String[result.size()]);
    }

    private Set<RootSetChangedListener> myListeners = new HashSet<RootSetChangedListener>();

    public void addRootSetChangedListener(RootSetChangedListener listener) {
      synchronized (this) {
        myListeners.add(listener);
      }
      super.addRootSetChangedListener(listener);
    }

    public void removeRootSetChangedListener(RootSetChangedListener listener) {
      super.removeRootSetChangedListener(listener);
      synchronized (this) {
        myListeners.remove(listener);
      }
    }

    public void rootsChanged() {
      synchronized (this) {
        if (myListeners.size() == 0) {
          return;
        }
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          fireRootSetChanged();
        }
      });
    }
  }

  private final static HashMap ourOrderRootsToProjectRoots = new HashMap();

  static {
    ourOrderRootsToProjectRoots.put(OrderRootType.CLASSES, ProjectRootType.CLASS);
    ourOrderRootsToProjectRoots.put(OrderRootType.CLASSES_AND_OUTPUT, ProjectRootType.CLASS);
    ourOrderRootsToProjectRoots.put(OrderRootType.COMPILATION_CLASSES, ProjectRootType.CLASS);
    ourOrderRootsToProjectRoots.put(OrderRootType.SOURCES, ProjectRootType.SOURCE);
    ourOrderRootsToProjectRoots.put(OrderRootType.JAVADOC, ProjectRootType.JAVADOC);
  }

  // SdkModificator implementation

  public SdkModificator getSdkModificator() {
    try {
      ProjectJdkImpl sdk = (ProjectJdkImpl)clone();
      sdk.myOrigin = this;
      sdk.myRootContainer.startChange();
      sdk.update();
      return sdk;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e); // should not happen
      return null;
    }
  }

  public void commitChanges() {
    LOG.assertTrue(isWritable());
    myRootContainer.finishChange();
    copyTo(myOrigin);
    myOrigin = null;
  }

  public SdkAdditionalData getSdkAdditionalData() {
    return myAdditionalData;
  }

  public void setSdkAdditionalData(SdkAdditionalData data) {
    myAdditionalData = data;
  }

  public VirtualFile[] getRoots(ProjectRootType rootType) {
    final ProjectRoot[] roots = myRootContainer.getRoots(rootType); // use getRoots() cause the data is most up-to-date there
    final List<VirtualFile> files = new ArrayList<VirtualFile>(roots.length);
    for (int idx = 0; idx < roots.length; idx++) {
      ProjectRoot root = roots[idx];
      files.addAll(Arrays.asList(root.getVirtualFiles()));
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public void addRoot(VirtualFile root, ProjectRootType rootType) {
    myRootContainer.addRoot(root, rootType);
  }

  public void removeRoot(VirtualFile root, ProjectRootType rootType) {
    myRootContainer.removeRoot(root, rootType);
  }

  public void removeRoots(ProjectRootType rootType) {
    myRootContainer.removeAllRoots(rootType);
  }

  public void removeAllRoots() {
    myRootContainer.removeAllRoots();
  }

  public boolean isWritable() {
    return myOrigin != null;
  }

  public void update() {
    myRootContainer.update();
  }
}