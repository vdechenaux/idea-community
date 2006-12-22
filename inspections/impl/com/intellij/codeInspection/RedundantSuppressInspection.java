package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.BidirectionalMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class RedundantSuppressInspection extends GlobalInspectionTool{
  private BidirectionalMap<String, QuickFix> myQuickFixes = null;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.RedundantSuppressInspection");

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.GENERAL_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.suppression.name");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "RedundantSuppression";
  }


  public void runInspection(final AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefVisitor() {
      public void visitClass(RefClass refClass) {
        if (!globalContext.shouldCheck(refClass, RedundantSuppressInspection.this)) return;
        CommonProblemDescriptor[] descriptors = checkElement(refClass, manager, globalContext.getProject());
        if (descriptors != null) {
          for (CommonProblemDescriptor descriptor : descriptors) {
            if (descriptor instanceof ProblemDescriptor) {
              final PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
              final PsiMember member = PsiTreeUtil.getParentOfType(psiElement, PsiMember.class);
              final RefElement refElement = globalContext.getRefManager().getReference(member);
              if (refElement != null) {
                problemDescriptionsProcessor.addProblemElement(refElement, descriptor);
                continue;
              }
            }
            problemDescriptionsProcessor.addProblemElement(refClass, descriptor);
          }
        }
      }
    });
  }

  @Nullable
  private CommonProblemDescriptor[] checkElement(RefClass refEntity, InspectionManager manager, final Project project) {
    final PsiElement psiElement = refEntity.getElement();
    final Map<PsiElement, Collection<String>> suppressedScopes = new THashMap<PsiElement, Collection<String>>();
    psiElement.accept(new PsiRecursiveElementVisitor() {
      public void visitModifierList(PsiModifierList list) {
        super.visitModifierList(list);
        final PsiElement parent = list.getParent();
        if (parent instanceof PsiModifierListOwner && !(parent instanceof PsiClass)) {
          checkElement(parent);
        }
      }

      public void visitComment(PsiComment comment) {
        checkElement(comment);
      }

      public void visitClass(PsiClass aClass) {
        if (aClass == psiElement) {
          super.visitClass(aClass);
          checkElement(aClass);
        }
      }


      private void checkElement(final PsiElement owner) {
        String idsString = InspectionManagerEx.getSuppressedInspectionIdsIn(owner);
        if (idsString != null && idsString.length() != 0) {
          List<String> ids = StringUtil.split(idsString, ",");
          Collection<String> suppressed = suppressedScopes.get(owner);
          if (suppressed == null) {
            suppressed = ids;
          }
          else {
            for (String id : ids) {
              if (!suppressed.contains(id)) {
                suppressed.add(id);
              }
            }
          }
          suppressedScopes.put(owner, suppressed);
        }
      }
    });

    if (suppressedScopes.values().isEmpty()) return null;
    // have to visit all file from scratch since inspections can be written in any perversive way including checkFile() overriding
    final ModifiableModel model = InspectionProjectProfileManager.getInstance(manager.getProject()).getInspectionProfile(psiElement).getModifiableModel();
    InspectionProfileWrapper profile = new InspectionProfileWrapper((InspectionProfile)model);
    profile.init(manager.getProject());
    Collection<InspectionTool> suppressedTools = new THashSet<InspectionTool>();

    InspectionTool[] tools = profile.getInspectionTools();
    for (Collection<String> ids : suppressedScopes.values()) {
      for (String id : ids) {
        String shortName = id.trim();
        for (InspectionTool tool : tools) {
          if (tool.getShortName().equals(shortName)) {
            suppressedTools.add(tool);
          }
        }
      }
    }

    final AnalysisScope scope = new AnalysisScope(psiElement.getContainingFile());    
    GlobalInspectionContextImpl globalContext = ((InspectionManagerEx)InspectionManager.getInstance(project)).createNewGlobalContext(false);
    globalContext.setCurrentScope(scope);
    final RefManagerImpl refManager = ((RefManagerImpl)globalContext.getRefManager());
    refManager.inspectionReadActionStarted();
    final List<ProblemDescriptor> result;
    try {
      result = new ArrayList<ProblemDescriptor>();
      for (InspectionTool tool : suppressedTools) {
        String toolId = tool.getShortName();
        tool.initialize(globalContext);
        Collection<CommonProblemDescriptor> descriptors;
        if (tool instanceof LocalInspectionToolWrapper) {
          LocalInspectionToolWrapper local = (LocalInspectionToolWrapper)tool;
          if (local.getTool() instanceof UnfairLocalInspectionTool) continue; //cant't work with passes other than LocalInspectionPass
          local.processFile(psiElement.getContainingFile(), false, manager);
          descriptors = local.getProblemDescriptors();
        }
        else if (tool instanceof GlobalInspectionToolWrapper) {
          GlobalInspectionToolWrapper global = (GlobalInspectionToolWrapper)tool;
          if (global.getTool().isGraphNeeded()) {
            refManager.findAllDeclarations();
          }
          global.processFile(scope, manager, globalContext, false);
          descriptors = global.getProblemDescriptors();
        }
        else {
          continue;
        }
        for (PsiElement suppressedScope : suppressedScopes.keySet()) {
          Collection<String> suppressedIds = suppressedScopes.get(suppressedScope);
          if (!suppressedIds.contains(toolId)) continue;
          boolean hasErrorInsideSuppressedScope = false;
          for (CommonProblemDescriptor descriptor : descriptors) {
            if (!(descriptor instanceof ProblemDescriptor)) continue;
            PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement();
            if (element == null) continue;
            PsiElement annotation = InspectionManagerEx.getElementToolSuppressedIn(element, toolId);
            if (annotation != null && PsiTreeUtil.isAncestor(suppressedScope, annotation, false)) {
              hasErrorInsideSuppressedScope = true;
              break;
            }
          }
          if (!hasErrorInsideSuppressedScope) {
            PsiMember psiMember;
            if (suppressedScope instanceof PsiMember) {
              psiMember = (PsiMember)suppressedScope;
            } else {
              psiMember = PsiTreeUtil.getParentOfType(suppressedScope, PsiDocCommentOwner.class);
            }
            if (psiMember != null && psiMember.isValid()) {
              String description = InspectionsBundle.message("inspection.redundant.suppression.description");
              if (myQuickFixes == null) myQuickFixes = new BidirectionalMap<String, QuickFix>();
              QuickFix fix = myQuickFixes.get(toolId);
              if (fix == null) {
                fix = new RemoveSuppressWarningAction(toolId);
                myQuickFixes.put(toolId, fix);
              }
              PsiElement identifier = null;
              if (psiMember instanceof PsiMethod) {
                identifier = ((PsiMethod)psiMember).getNameIdentifier();
              } else if (psiMember instanceof PsiField) {
                identifier = ((PsiField)psiMember).getNameIdentifier();
              } else if (psiMember instanceof PsiClass) {
                identifier = ((PsiClass)psiMember).getNameIdentifier();
              }
              if (identifier == null) {
                identifier = psiMember;
              }
              result.add(manager.createProblemDescriptor(identifier, description, (LocalQuickFix)fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
        }
      }
    }
    finally {
      refManager.inspectionReadActionFinished();
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }


  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return myQuickFixes != null ? myQuickFixes.get(hint) : new RemoveSuppressWarningAction(hint);
  }


  @Nullable
  public String getHint(final QuickFix fix) {
    if (myQuickFixes != null) {
      final List<String> list = myQuickFixes.getKeysByValue(fix);
      if (list != null) {
        LOG.assertTrue(list.size() == 1);
        return list.get(0);
      }
    }
    return null;
  }

  public boolean isGraphNeeded() {
    return true;
  }
}
