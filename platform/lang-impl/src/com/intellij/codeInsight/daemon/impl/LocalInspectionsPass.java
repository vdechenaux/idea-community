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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.concurrency.JobUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class LocalInspectionsPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LocalInspectionsPass");
  private final int myStartOffset;
  private final int myEndOffset;
  private final TextRange myPriorityRange;
  private final ConcurrentMap<PsiFile, List<InspectionResult>> result = new ConcurrentHashMap<PsiFile, List<InspectionResult>>();
  static final String PRESENTABLE_NAME = DaemonBundle.message("pass.inspection");
  private volatile List<HighlightInfo> myInfos = Collections.emptyList();
  private final String myShortcutText;
  private final SeverityRegistrar mySeverityRegistrar;
  private final InspectionProfileWrapper myProfileWrapper;
  private boolean myFailFastOnAcquireReadAction;

  public LocalInspectionsPass(@NotNull PsiFile file, @Nullable Document document, int startOffset, int endOffset) {
    this(file, document, startOffset, endOffset, new TextRange(0, 0));
  }
  public LocalInspectionsPass(@NotNull PsiFile file, @Nullable Document document, int startOffset, int endOffset, @NotNull TextRange priorityRange) {
    super(file.getProject(), document, PRESENTABLE_NAME, file, true);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPriorityRange = priorityRange;
    setId(Pass.LOCAL_INSPECTIONS);

    final KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null) {
      final Keymap keymap = keymapManager.getActiveKeymap();
      myShortcutText = keymap == null ? "" : "(" + KeymapUtil.getShortcutsText(keymap.getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
    }
    else {
      myShortcutText = "";
    }
    InspectionProfileWrapper customProfile = file.getUserData(InspectionProfileWrapper.KEY);
    myProfileWrapper = customProfile == null ? InspectionProjectProfileManager.getInstance(myProject).getProfileWrapper() : customProfile;
    mySeverityRegistrar = ((SeverityProvider)myProfileWrapper.getInspectionProfile().getProfileManager()).getSeverityRegistrar();
    LOG.assertTrue(mySeverityRegistrar != null);

    // initial guess
    setProgressLimit(300 * 2);
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    try {
      if (!HighlightLevelUtil.shouldInspect(myFile)) return;
      final InspectionManagerEx iManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
      final InspectionProfileWrapper profile = myProfileWrapper;
      final List<LocalInspectionTool> tools = DumbService.getInstance(myProject).filterByDumbAwareness(getInspectionTools(profile));
      inspect(tools, iManager, true, true, true, progress);
    }
    finally {
      disposeDescriptors();
    }
  }

  private void disposeDescriptors() {
    for (List<InspectionResult> list : result.values()) {
      for (InspectionResult inspectionResult : list) {
         for (ProblemDescriptor pd: inspectionResult.foundProblems) {
           ((ProblemDescriptorImpl)pd).dispose();
         }
      }
    }
    result.clear();
  }

  public void doInspectInBatch(final InspectionManagerEx iManager, List<InspectionProfileEntry> toolWrappers, boolean ignoreSuppressed) {
    Map<LocalInspectionTool, LocalInspectionToolWrapper> tool2Wrapper = new THashMap<LocalInspectionTool, LocalInspectionToolWrapper>(toolWrappers.size());
    for (InspectionProfileEntry toolWrapper : toolWrappers) {
      tool2Wrapper.put(((LocalInspectionToolWrapper)toolWrapper).getTool(), (LocalInspectionToolWrapper)toolWrapper);
    }
    List<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>(tool2Wrapper.keySet());

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    inspect(tools, iManager, false, ignoreSuppressed, false, progress);
    addDescriptorsFromInjectedResults(tool2Wrapper, iManager);
    List<InspectionResult> resultList = result.get(myFile);
    if (resultList == null) return;
    for (InspectionResult inspectionResult : resultList) {
      LocalInspectionTool tool = inspectionResult.tool;
      LocalInspectionToolWrapper toolWrapper = tool2Wrapper.get(tool);
      if (toolWrapper == null) continue;
      for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
        toolWrapper.addProblemDescriptors(Collections.singletonList(descriptor), ignoreSuppressed);
      }
    }
  }

  private void addDescriptorsFromInjectedResults(Map<LocalInspectionTool, LocalInspectionToolWrapper> tool2Wrapper, InspectionManagerEx iManager) {
    Set<TextRange> emptyActionRegistered = new THashSet<TextRange>();
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);

    for (Map.Entry<PsiFile, List<InspectionResult>> entry : result.entrySet()) {
      PsiFile file = entry.getKey();
      if (file == myFile) continue; // not injected
      DocumentWindow documentRange = (DocumentWindow)documentManager.getDocument(file);
      List<InspectionResult> resultList = entry.getValue();
      for (InspectionResult inspectionResult : resultList) {
        LocalInspectionTool tool = inspectionResult.tool;
        HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), myFile).getSeverity();
        for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {

          PsiElement psiElement = descriptor.getPsiElement();
          if (InspectionManagerEx.inspectionResultSuppressed(psiElement, tool)) continue;
          HighlightInfoType level = highlightTypeFromDescriptor(descriptor, severity);
          HighlightInfo info = createHighlightInfo(descriptor, tool, level,emptyActionRegistered);
          if (info == null) continue;
          List<TextRange> editables = ilManager.intersectWithAllEditableFragments(file, new TextRange(info.startOffset, info.endOffset));
          for (TextRange editable : editables) {
            TextRange hostRange = documentRange.injectedToHost(editable);
            QuickFix[] fixes = descriptor.getFixes();
            LocalQuickFix[] localFixes = null;
            if (fixes != null) {
              localFixes = new LocalQuickFix[fixes.length];
              for (int k = 0; k < fixes.length; k++) {
                QuickFix fix = fixes[k];
                localFixes[k] = (LocalQuickFix)fix;
              }
            }
            ProblemDescriptor patchedDescriptor = iManager.createProblemDescriptor(myFile, hostRange, descriptor.getDescriptionTemplate(),
                                                                                   descriptor.getHighlightType(), true, localFixes);
            LocalInspectionToolWrapper toolWrapper = tool2Wrapper.get(tool);
            toolWrapper.addProblemDescriptors(Collections.singletonList(patchedDescriptor), true);
          }
        }
      }
    }
  }

  private void inspect(final List<LocalInspectionTool> tools,
                       final InspectionManagerEx iManager,
                       final boolean isOnTheFly,
                       final boolean ignoreSuppressed,
                       boolean failFastOnAcquireReadAction,
                       @NotNull final ProgressIndicator indicator) {
    myFailFastOnAcquireReadAction = failFastOnAcquireReadAction;
    if (tools.isEmpty()) return;

    ArrayList<PsiElement> inside = new ArrayList<PsiElement>();
    ArrayList<PsiElement> outside = new ArrayList<PsiElement>();
    Divider.divideInsideAndOutside(myFile, myStartOffset, myEndOffset, myPriorityRange, inside, outside,
                                   HighlightLevelUtil.AnalysisLevel.HIGHLIGHT_AND_INSPECT,true);

    setProgressLimit(1L * tools.size() * 2);
    final LocalInspectionToolSession session = new LocalInspectionToolSession(myFile, myStartOffset, myEndOffset);

    List<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>> init = new ArrayList<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>>();
    boolean finished = false;
    try {
      visitPriorityElementsAndInit(tools, iManager, isOnTheFly, ignoreSuppressed, indicator, inside, session, init);
      visitRestElementsAndCleanup(tools,iManager,isOnTheFly,ignoreSuppressed, indicator, outside, session, init);
      finished = true;
    }
    finally {
      if (!finished) {
        synchronized (init) {
          for (Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor> trinity : init) {
            List<ProblemDescriptor> results = trinity.second.getResults();
            if (results != null) {
              for (ProblemDescriptor pd : results) {
                ((ProblemDescriptorImpl)pd).dispose();
              }
            }
          }
        }
      }
    }

    indicator.checkCanceled();

    myInfos = new ArrayList<HighlightInfo>();
    addHighlightsFromResults(myInfos, ignoreSuppressed);
  }

  private void visitPriorityElementsAndInit(@NotNull List<LocalInspectionTool> tools,
                                            @NotNull final InspectionManagerEx iManager,
                                            final boolean isOnTheFly,
                                            final boolean ignoreSuppressed,
                                            @NotNull final ProgressIndicator indicator,
                                            @NotNull final List<PsiElement> elements,
                                            @NotNull final LocalInspectionToolSession session,
                                            @NotNull final List<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>> init) {
    boolean result = JobUtil.invokeConcurrentlyUnderProgress(tools, new Processor<LocalInspectionTool>() {
      public boolean process(final LocalInspectionTool tool) {
        indicator.checkCanceled();

        ApplicationManager.getApplication().assertReadAccessAllowed();

        final boolean[] applyIncrementally = {isOnTheFly};
        ProblemsHolder holder = new ProblemsHolder(iManager, myFile, isOnTheFly) {
          @Override
          public void registerProblem(@NotNull ProblemDescriptor descriptor) {
            super.registerProblem(descriptor);
            if (applyIncrementally[0]) {
              addDescriptorIncrementally(descriptor, tool, ignoreSuppressed, indicator);
            }
          }
        };
        PsiElementVisitor visitor = createVisitorAndAcceptElements(tool, holder, isOnTheFly, session, elements, indicator);

        synchronized (init) {
          init.add(Trinity.create(tool, holder, visitor));
        }
        advanceProgress(1);

        if (holder.hasResults()) {
          appendDescriptors(myFile, holder.getResults(), tool);
        }
        applyIncrementally[0] = false; // do not apply incrementally outside visible range
        return true;
      }
    }, myFailFastOnAcquireReadAction, indicator);
    if (!result) throw new ProcessCanceledException();
    inspectInjectedPsi(elements, tools, isOnTheFly, ignoreSuppressed, indicator, iManager, true);
  }

  private static PsiElementVisitor createVisitorAndAcceptElements(@NotNull LocalInspectionTool tool,
                                                                  @NotNull ProblemsHolder holder,
                                                                  boolean isOnTheFly,
                                                                  @NotNull LocalInspectionToolSession session,
                                                                  @NotNull List<PsiElement> elements,
                                                                  @NotNull ProgressIndicator indicator) {
    PsiElementVisitor visitor = tool.buildVisitor(holder, isOnTheFly, session);
    //noinspection ConstantConditions
    if(visitor == null) {
      LOG.error("Tool " + tool + " must not return null from the buildVisitor() method");
    }
    assert !(visitor instanceof PsiRecursiveElementVisitor || visitor instanceof PsiRecursiveElementWalkingVisitor)
      : "The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive. "+tool;

    tool.inspectionStarted(session);
    for (PsiElement element : elements) {
      indicator.checkCanceled();
      element.accept(visitor);
    }
    return visitor;
  }

  private void visitRestElementsAndCleanup(@NotNull List<LocalInspectionTool> tools,
                                           @NotNull InspectionManagerEx iManager,
                                           final boolean isOnTheFly,
                                           final boolean ignoreSuppressed,
                                           @NotNull final ProgressIndicator indicator,
                                           @NotNull final List<PsiElement> elements,
                                           @NotNull final LocalInspectionToolSession session,
                                           @NotNull List<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>> init) {
    Processor<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>> processor =
      new Processor<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>>() {
        @Override
        public boolean process(Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor> trinity) {
          LocalInspectionTool tool = trinity.first;
          indicator.checkCanceled();

          ApplicationManager.getApplication().assertReadAccessAllowed();

          ProblemsHolder holder = trinity.second;
          PsiElementVisitor elementVisitor = trinity.third;
          for (int i = 0, elementsSize = elements.size(); i < elementsSize; i++) {
            PsiElement element = elements.get(i);
            indicator.checkCanceled();
            element.accept(elementVisitor);
          }

          advanceProgress(1);

          tool.inspectionFinished(session, holder);

          if (holder.hasResults()) {
            appendDescriptors(myFile, holder.getResults(), tool);
          }
          return true;
        }
      };
    boolean result = JobUtil.invokeConcurrentlyUnderProgress(init, processor, myFailFastOnAcquireReadAction, indicator);
    if (!result) {
      throw new ProcessCanceledException();
    }
    inspectInjectedPsi(elements, tools, isOnTheFly, ignoreSuppressed, indicator, iManager, false);
  }

  private void inspectInjectedPsi(@NotNull final List<PsiElement> elements,
                                  @NotNull final List<LocalInspectionTool> tools,
                                  final boolean onTheFly,
                                  final boolean ignoreSuppressed,
                                  @NotNull final ProgressIndicator indicator,
                                  @NotNull final InspectionManagerEx iManager,
                                  final boolean inVisibleRange) {
    final Set<PsiFile> injected = new THashSet<PsiFile>();
    for (PsiElement element : elements) {
      InjectedLanguageUtil.enumerate(element, myFile, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          injected.add(injectedPsi);
        }
      }, false);
    }
    if (injected.isEmpty()) return;
    if (!JobUtil.invokeConcurrentlyUnderProgress(new ArrayList<PsiFile>(injected), new Processor<PsiFile>() {
      public boolean process(final PsiFile injectedPsi) {
        doInspectInjectedPsi(injectedPsi, tools, onTheFly, ignoreSuppressed, indicator, iManager, inVisibleRange);
        return true;
      }
    }, myFailFastOnAcquireReadAction, indicator)) throw new ProcessCanceledException();
  }

  public Collection<HighlightInfo> getHighlights() {
    List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();

    addHighlightsFromResults(highlights, true);
    return highlights;
  }

  @Nullable
  private HighlightInfo highlightInfoFromDescriptor(@NotNull ProblemDescriptor problemDescriptor,
                                                    @NotNull HighlightInfoType highlightInfoType,
                                                    @NotNull String message,
                                                    String toolTip) {
    TextRange textRange = ((ProblemDescriptorImpl)problemDescriptor).getTextRange();
    PsiElement element = problemDescriptor.getPsiElement();
    if (textRange == null || element == null) return null;
    boolean isFileLevel = element instanceof PsiFile && textRange.equals(element.getTextRange());

    final HighlightSeverity severity = highlightInfoType.getSeverity(element);
    TextAttributes attributes = mySeverityRegistrar.getTextAttributesBySeverity(severity);
    return new HighlightInfo(attributes, highlightInfoType, textRange.getStartOffset(),
                             textRange.getEndOffset(), message, toolTip,
                             severity, problemDescriptor.isAfterEndOfLine(), null, isFileLevel);
  }

  private final AtomicBoolean haveInfosToProcess = new AtomicBoolean();
  private final ConcurrentLinkedQueue<Pair<ProblemDescriptor, LocalInspectionTool>> infosToAdd = new ConcurrentLinkedQueue<Pair<ProblemDescriptor, LocalInspectionTool>>();
  private final Set<TextRange> emptyActionRegistered = Collections.synchronizedSet(new HashSet<TextRange>());

  private void addDescriptorIncrementally(@NotNull final ProblemDescriptor descriptor,
                                          @NotNull final LocalInspectionTool tool,
                                          final boolean ignoreSuppressed,
                                          @NotNull final ProgressIndicator indicator) {
    if (ignoreSuppressed && InspectionManagerEx.inspectionResultSuppressed(descriptor.getPsiElement(), tool)) {
      return;
    }

    infosToAdd.offer(Pair.create(descriptor, tool));
    if (haveInfosToProcess.getAndSet(true)) return;
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    // extra invoke later is harmless, missing invoke is not
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
        InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
        List<HighlightInfo> infos = new ArrayList<HighlightInfo>(2);
        while (haveInfosToProcess.compareAndSet(true, false)) {
          for (Pair<ProblemDescriptor, LocalInspectionTool> pair = infosToAdd.poll(); pair != null; pair = infosToAdd.poll()) {
            if (indicator.isCanceled()) {
              infosToAdd.clear();
              return;
            }

            ProblemDescriptor descriptor = pair.first;
            LocalInspectionTool tool = pair.second;
            PsiElement psiElement = descriptor.getPsiElement();
            if (psiElement == null) continue;
            PsiFile file = psiElement.getContainingFile();
            Document thisDocument = documentManager.getDocument(file);

            HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), file).getSeverity();

            infos.clear();
            createHighlightsForDescriptor(infos, emptyActionRegistered, ilManager, file, thisDocument, tool, severity, descriptor, ignoreSuppressed);
            for (HighlightInfo info : infos) {
              final EditorColorsScheme colorsScheme = getColorsScheme();

              UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, myDocument, myFile, myStartOffset, myEndOffset,
                                                                         info, colorsScheme, getId());
            }
          }
        }
      }
    });
  }

  private void appendDescriptors(PsiFile file, List<ProblemDescriptor> descriptors, LocalInspectionTool tool) {
    InspectionResult res = new InspectionResult(tool, descriptors);
    appendResult(file, res);
  }

  private void appendResult(PsiFile file, InspectionResult res) {
    List<InspectionResult> resultList = result.get(file);
    if (resultList == null) {
      resultList = ConcurrencyUtil.cacheOrGet(result, file, new ArrayList<InspectionResult>());
    }
    synchronized (resultList) {
      resultList.add(res);
    }
  }

  @NotNull
  private HighlightInfoType highlightTypeFromDescriptor(final ProblemDescriptor problemDescriptor, final HighlightSeverity severity) {
    final ProblemHighlightType highlightType = problemDescriptor.getHighlightType();
    switch (highlightType) {
      case GENERIC_ERROR_OR_WARNING:
        return mySeverityRegistrar.getHighlightInfoTypeBySeverity(severity);
      case LIKE_DEPRECATED:
        return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.DEPRECATED.getAttributesKey());
      case LIKE_UNKNOWN_SYMBOL:
        if (severity == HighlightSeverity.ERROR) {
          return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.WRONG_REF.getAttributesKey());
        }
        else if (severity == HighlightSeverity.WARNING) {
          return new HighlightInfoType.HighlightInfoTypeImpl(severity, CodeInsightColors.WEAK_WARNING_ATTRIBUTES);
        }
        else {
          return mySeverityRegistrar.getHighlightInfoTypeBySeverity(severity);
        }
      case LIKE_UNUSED_SYMBOL:
        return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.UNUSED_SYMBOL.getAttributesKey());
      case INFO:
        return HighlightInfoType.INFO;
       case WEAK_WARNING:
        return HighlightInfoType.WEAK_WARNING;
      case ERROR:
        return HighlightInfoType.WRONG_REF;
      case GENERIC_ERROR:
        return HighlightInfoType.ERROR;
      case INFORMATION:
        final TextAttributesKey attributes = ((ProblemDescriptorImpl)problemDescriptor).getEnforcedTextAttributes();
        if (attributes != null) {
          return new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, attributes);
        }
        return HighlightInfoType.INFORMATION;
    }
    throw new RuntimeException("Cannot map " + highlightType);
  }

  protected void applyInformationWithProgress() {
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myInfos, getColorsScheme(), getId());
  }

  private void addHighlightsFromResults(final List<HighlightInfo> outInfos, boolean ignoreSuppressed) {
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    Set<TextRange> emptyActionRegistered = new THashSet<TextRange>();

    for (Map.Entry<PsiFile, List<InspectionResult>> entry : result.entrySet()) {
      PsiFile file = entry.getKey();
      Document documentRange = documentManager.getDocument(file);
      if (documentRange == null) continue;
      List<InspectionResult> resultList = entry.getValue();
      for (InspectionResult inspectionResult : resultList) {
        LocalInspectionTool tool = inspectionResult.tool;
        HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), file).getSeverity();
        for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
          createHighlightsForDescriptor(outInfos, emptyActionRegistered, ilManager, file, documentRange, tool, severity, descriptor,
                                        ignoreSuppressed);
        }
      }
    }
  }

  private void createHighlightsForDescriptor(List<HighlightInfo> outInfos,
                                             Set<TextRange> emptyActionRegistered,
                                             InjectedLanguageManager ilManager,
                                             PsiFile file,
                                             Document documentRange,
                                             LocalInspectionTool tool,
                                             HighlightSeverity severity,
                                             ProblemDescriptor descriptor, boolean ignoreSuppressed) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return;
    if (ignoreSuppressed && InspectionManagerEx.inspectionResultSuppressed(psiElement, tool)) return;
    HighlightInfoType level = highlightTypeFromDescriptor(descriptor, severity);
    HighlightInfo info = createHighlightInfo(descriptor, tool, level, emptyActionRegistered);
    if (info == null) return;

    if (file == myFile) {
      // not injected
      outInfos.add(info);
      return;
    }
    // todo we got to separate our "internal" prefixes/suffixes from user-defined ones
    // todo in the latter case the errors should be highlighted, otherwise not
    List<TextRange> editables = ilManager.intersectWithAllEditableFragments(file, new TextRange(info.startOffset, info.endOffset));
    for (TextRange editable : editables) {
      TextRange hostRange = ((DocumentWindow)documentRange).injectedToHost(editable);
      HighlightInfo patched = HighlightInfo.createHighlightInfo(info.type, psiElement, hostRange.getStartOffset(),
                                                                 hostRange.getEndOffset(), info.description, info.toolTip);
      if (patched != null) {
        registerQuickFixes(tool, descriptor, patched, emptyActionRegistered);
        outInfos.add(patched);
      }
    }
  }

  @Nullable
  private HighlightInfo createHighlightInfo(final ProblemDescriptor descriptor,
                                            final LocalInspectionTool tool,
                                            final HighlightInfoType level,
                                            final Set<TextRange> emptyActionRegistered) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return null;
    @NonNls String message = ProblemDescriptionNode.renderDescriptionMessage(descriptor);

    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    final InspectionProfile inspectionProfile = myProfileWrapper.getInspectionProfile();
    if (!inspectionProfile.isToolEnabled(key, myFile)) return null;

    HighlightInfoType type = new HighlightInfoType.HighlightInfoTypeImpl(level.getSeverity(psiElement), level.getAttributesKey());
    final String plainMessage = message.startsWith("<html>") ? StringUtil.unescapeXml(message.replaceAll("<[^>]*>", "")) : message;
    @NonNls final String link = "<a href=\"#inspection/" + tool.getShortName() + "\"> " + DaemonBundle.message("inspection.extended.description") +
                                "</a>" + myShortcutText;

    @NonNls String tooltip = null;
    if (descriptor.showTooltip()) {
      if (message.startsWith("<html>")) {
        tooltip = message.contains("</body>") ? message.replace("</body>", link + "</body>") : message.replace("</html>", link + "</html>");
      }
      else {
        tooltip = "<html><body>" + XmlStringUtil.escapeString(message) + link + "</body></html>";
      }
    }
    HighlightInfo highlightInfo = highlightInfoFromDescriptor(descriptor, type, plainMessage, tooltip);
    if (highlightInfo != null) {
      registerQuickFixes(tool, descriptor, highlightInfo, emptyActionRegistered);
    }
    return highlightInfo;
  }

  private static void registerQuickFixes(final LocalInspectionTool tool,
                                         final ProblemDescriptor descriptor,
                                         @NotNull HighlightInfo highlightInfo,
                                         final Set<TextRange> emptyActionRegistered) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    boolean needEmptyAction = true;
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length > 0) {
      for (int k = 0; k < fixes.length; k++) {
        if (fixes[k] != null) { // prevent null fixes from var args
          QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixWrapper.wrap(descriptor, k), key);
          needEmptyAction = false;
        }
      }
    }
    HintAction hintAction = ((ProblemDescriptorImpl)descriptor).getHintAction();
    if (hintAction != null) {
      QuickFixAction.registerQuickFixAction(highlightInfo, hintAction, key);
      needEmptyAction = false;
    }
    if (((ProblemDescriptorImpl)descriptor).getEnforcedTextAttributes() != null) {
      needEmptyAction = false;
    }
    if (needEmptyAction && emptyActionRegistered.add(new TextRange(highlightInfo.fixStartOffset, highlightInfo.fixEndOffset))) {
      EmptyIntentionAction emptyIntentionAction = new EmptyIntentionAction(tool.getDisplayName());
      QuickFixAction.registerQuickFixAction(highlightInfo, emptyIntentionAction, key);
    }
  }

  private static List<PsiElement> getElementsFrom(PsiFile file) {
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<PsiElement> result = new LinkedHashSet<PsiElement>();
    final PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      @Override public void visitElement(PsiElement element) {
        ProgressManager.checkCanceled();
        PsiElement child = element.getFirstChild();
        if (child == null) {
          // leaf element
        }
        else {
          // composite element
          while (child != null) {
            child.accept(this);
            result.add(child);

            child = child.getNextSibling();
          }
        }
      }
    };
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile psiRoot = viewProvider.getPsi(language);
      if (!HighlightLevelUtil.shouldInspect(psiRoot)) {
        continue;
      }
      psiRoot.accept(visitor);
      result.add(psiRoot);
    }
    return new ArrayList<PsiElement>(result);
  }

  List<LocalInspectionTool> getInspectionTools(InspectionProfileWrapper profile) {
    return profile.getHighlightingLocalInspectionTools(myFile);
  }

  private void doInspectInjectedPsi(@NotNull PsiFile injectedPsi,
                                    @NotNull List<LocalInspectionTool> tools,
                                    final boolean isOnTheFly,
                                    final boolean ignoreSuppressed,
                                    @NotNull final ProgressIndicator indicator,
                                    @NotNull InspectionManagerEx iManager,
                                    final boolean inVisibleRange) {
    final PsiElement host = injectedPsi.getContext();

    final List<PsiElement> elements = getElementsFrom(injectedPsi);
    if (elements.isEmpty()) {
      return;
    }
    for (final LocalInspectionTool tool : tools) {
      indicator.checkCanceled();
      if (host != null && ignoreSuppressed && InspectionManagerEx.inspectionResultSuppressed(host, tool)) {
        continue;
      }
      ProblemsHolder holder = new ProblemsHolder(iManager, injectedPsi, isOnTheFly) {
        @Override
        public void registerProblem(@NotNull ProblemDescriptor descriptor) {
          super.registerProblem(descriptor);
          if (isOnTheFly && inVisibleRange) {
            addDescriptorIncrementally(descriptor, tool, ignoreSuppressed, indicator);
          }
        }
      };

      LocalInspectionToolSession injSession = new LocalInspectionToolSession(injectedPsi, 0, injectedPsi.getTextLength());
      createVisitorAndAcceptElements(tool, holder, isOnTheFly, injSession, elements, indicator);
      tool.inspectionFinished(injSession,holder);
      List<ProblemDescriptor> problems = holder.getResults();
      if (problems != null && !problems.isEmpty()) {
        InspectionResult res = new InspectionResult(tool, problems);
        appendResult(injectedPsi, res);
      }
    }
  }

  public List<HighlightInfo> getInfos() {
    return myInfos;
  }

  private static class InspectionResult {
    public final LocalInspectionTool tool;
    public final List<ProblemDescriptor> foundProblems;

    private InspectionResult(final LocalInspectionTool tool, final List<ProblemDescriptor> foundProblems) {
      this.tool = tool;
      this.foundProblems = foundProblems;
    }
  }
}
