/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2001
 * Time: 8:21:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.analysis.AlternativeWay;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class RefManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.reference.RefManager");

  private final Project myProject;
  private final AnalysisScope myScope;
  private final RefProject myRefProject;
  private final PsiManager myPsiManager;
  private HashMap<PsiElement, RefElement> myRefTable;
  private HashMap myPackages;
  private final ProjectIterator myProjectIterator;
  private boolean myDeclarationsFound;
  private PsiMethod myAppMainPattern;
  private PsiClass myApplet;
  private PsiClass myServlet;
  private boolean myIsInProcess = false;

  public interface RefIterator {
    void accept(RefElement refElement);
  }

  public RefManager(Project project, AnalysisScope scope) {
    myDeclarationsFound = false;
    myProject = project;
    myScope = scope;
    myRefProject = new RefProject(this);
    myRefTable = new HashMap<PsiElement, RefElement>();
    myPsiManager = PsiManager.getInstance(project);

    myProjectIterator = new ProjectIterator();

    PsiElementFactory factory = myPsiManager.getElementFactory();
    try {
      myAppMainPattern = factory.createMethodFromText("void main(String[] args);", null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    myApplet = myPsiManager.findClass("java.applet.Applet");
    myServlet = myPsiManager.findClass("javax.servlet.Servlet");
  }

  public void iterate(RefIterator iterator) {
    final HashMap<PsiElement, RefElement> refTable = getRefTable();
    for (Iterator<RefElement> refIterator = refTable.values().iterator(); refIterator.hasNext();) {
      RefElement refElement = refIterator.next();
      iterator.accept(refElement);
      if (refElement instanceof RefClass) {
        RefClass refClass = (RefClass)refElement;
        RefMethod refDefaultConstructor = refClass.getDefaultConstructor();
        if (refDefaultConstructor != null && refDefaultConstructor instanceof RefImplicitConstructor) {
          iterator.accept(refClass.getDefaultConstructor());
        }
      }
    }
  }

  public void cleanup() {
    myRefTable = null;
  }

  public AnalysisScope getScope() {
    return myScope;
  }


  public void findAllDeclarations() {
    if (!myDeclarationsFound) {
      long before = System.currentTimeMillis();
      getScope().accept(myProjectIterator);
      myDeclarationsFound = true;

      LOG.info("Total duration of processing project usages:" + (System.currentTimeMillis() - before));
    }
  }

  public void inspectionReadActionStarted() {
    myIsInProcess = true;
  }

  public void inspectionReadActionFinished() {
    myIsInProcess = false;
  }

  public PsiElement getPsiAtOffset(VirtualFile vFile, int textOffset) {
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
    if (psiFile == null) return null;

    PsiElement psiElem = psiFile.findElementAt(textOffset);

    while (psiElem != null) {
      if (psiElem instanceof PsiClass ||
          psiElem instanceof PsiMethod ||
          psiElem instanceof PsiField ||
          psiElem instanceof PsiParameter) {
        return psiElem.getTextOffset() == textOffset ? psiElem : null;
      }

      psiElem = psiElem.getParent();
    }

    return null;
  }

  public Project getProject() {
    return myProject;
  }

  public RefProject getRefProject() {
    return myRefProject;
  }

  public HashMap<PsiElement, RefElement> getRefTable() {
    return myRefTable;
  }

  public RefPackage getPackage(String packageName) {
    if (myPackages == null) {
      myPackages = new HashMap();
    }

    RefPackage refPackage = (RefPackage)myPackages.get(packageName);
    if (refPackage == null) {
      refPackage = new RefPackage(packageName);
      myPackages.put(packageName, refPackage);

      int dotIndex = packageName.lastIndexOf('.');
      if (dotIndex >= 0) {
        getPackage(packageName.substring(0, dotIndex)).add(refPackage);
      }
      else {
        getRefProject().add(refPackage);
      }
    }

    return refPackage;
  }

  public void removeReference(RefElement refElem) {
    final HashMap<PsiElement, RefElement> refTable = getRefTable();

    if (refElem instanceof RefMethod) {
      RefMethod refMethod = (RefMethod)refElem;
      RefParameter[] params = refMethod.getParameters();
      for (int i = 0; i < params.length; i++) {
        removeReference(params[i]);
      }
    }

    if (refTable.remove(refElem.getElement()) != null) return;

    //PsiElement may have been invalidated and new one returned by getElement() is different so we need to do this stuff.
    Set<PsiElement> keys = refTable.keySet();
    for (Iterator iterator = keys.iterator(); iterator.hasNext();) {
      PsiElement psiElement = (PsiElement)iterator.next();
      if (refTable.get(psiElement) == refElem) {
        refTable.remove(psiElement);
        return;
      }
    }
  }

  private class ProjectIterator extends PsiElementVisitor {
    public void visitElement(PsiElement element) {
      PsiElement[] children = element.getChildren();
      for (int i = 0; i < children.length; i++) {
        children[i].accept(this);
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitElement(expression);
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    }

    public void visitFile(PsiFile file) {
      if (file instanceof PsiJavaFile && !(file instanceof PsiCompiledElement)) {
        InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
        manager.incrementJobDoneAmount(InspectionManagerEx.BUILD_GRAPH, file.getVirtualFile().getPresentableUrl());
        if (RefUtil.belongsToScope(file, RefManager.this)) {
          AlternativeWay.processFile(file);
          super.visitFile(file);
          myPsiManager.dropResolveCaches();
        }
      }
    }

    public void visitClass(PsiClass aClass) {
      if (!(aClass instanceof PsiTypeParameter)) {
        super.visitClass(aClass);
        RefElement refClass = RefManager.this.getReference(aClass);
        refClass.buildReferences();
        ArrayList children = refClass.getChildren();
        if (children != null) {
          for (Iterator iterator = children.iterator(); iterator.hasNext();) {
            RefElement refChild = (RefElement)iterator.next();
            refChild.buildReferences();
          }
        }
      }
    }

    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      RefUtil.addTypeReference(variable, variable.getType(), RefManager.this);
    }

    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      RefUtil.addTypeReference(expression, expression.getCheckType().getType(), RefManager.this);
    }

    public void visitThisExpression(PsiThisExpression expression) {
      super.visitThisExpression(expression);
      if (expression.getQualifier() != null) {
        RefUtil.addTypeReference(expression, expression.getType(), RefManager.this);
        addInstanceReference(expression, (PsiClass)expression.getQualifier().resolve());
      }
    }

    private void addInstanceReference(PsiThisExpression psiElement, PsiClass psiClass) {
      RefClass ownerClass = RefUtil.getOwnerClass(RefManager.this, psiElement);

      if (ownerClass != null) {
        RefClass refClass = (RefClass)RefManager.this.getReference(psiClass);
        if (refClass != null) {
          refClass.addInstanceReference(ownerClass);
        }

        if (refClass != ownerClass) {
          ownerClass.setCanBeStatic(false);
        }
      }
    }
  }

  public PsiMethod getAppMainPattern() {
    return myAppMainPattern;
  }

  public PsiClass getApplet() {
    return myApplet;
  }

  public PsiClass getServlet() {
    return myServlet;
  }

  public RefElement getReference(PsiElement elem) {
    LOG.assertTrue(isValidPointForReference(), "References may become invalid after process is finished");
    if (elem != null && !(elem instanceof PsiPackage) && RefUtil.belongsToScope(elem, this)) {
      if (!elem.isValid()) return null;

      RefElement ref = getRefTable().get(elem);
      if (ref == null) {
        if (elem instanceof PsiClass) {
          ref = new RefClass((PsiClass)elem, this);
        }
        else if (elem instanceof PsiMethod) {
          ref = new RefMethod((PsiMethod)elem, this);
        }
        else if (elem instanceof PsiField) {
          ref = new RefField((PsiField)elem, this);
        }
        else {
          return null;
        }

        getRefTable().put(elem, ref);
      }

      return ref;
    }

    return null;
  }

  public RefMethod getMethodReference(RefClass refClass, PsiMethod psiMethod) {
    LOG.assertTrue(isValidPointForReference(), "References may become invalid after process is finished");

    RefMethod ref = (RefMethod)getRefTable().get(psiMethod);

    if (ref == null) {
      ref = new RefMethod(refClass, psiMethod, this);
      getRefTable().put(psiMethod, ref);
    }

    return ref;
  }

  public RefField getFieldReference(RefClass refClass, PsiField psiField) {
    LOG.assertTrue(isValidPointForReference(), "References may become invalid after process is finished");
    RefField ref = (RefField)getRefTable().get(psiField);

    if (ref == null) {
      ref = new RefField(refClass, psiField, this);
      getRefTable().put(psiField, ref);
    }

    return ref;
  }

  public RefParameter getParameterReference(PsiParameter param, int index) {
    LOG.assertTrue(isValidPointForReference(), "References may become invalid after process is finished");
    RefElement ref = getRefTable().get(param);

    if (ref == null) {
      ref = new RefParameter(param, index, this);
      getRefTable().put(param, ref);
    }

    return (RefParameter)ref;
  }

  private boolean isValidPointForReference() {
    return myIsInProcess || ApplicationManager.getApplication().isUnitTestMode();
  }
}
