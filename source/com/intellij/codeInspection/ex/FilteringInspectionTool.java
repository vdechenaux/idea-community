package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.codeInspection.ui.InspectionPackageNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;

import java.util.*;

/**
 * @author max
 */
public abstract class FilteringInspectionTool extends InspectionTool {
  public abstract RefFilter getFilter();
  HashMap<String, Set<RefElement>> myPackageContents = null;

  public InspectionTreeNode[] getContents() {
    List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    Set<String> packages = myPackageContents.keySet();
    for (Iterator<String> iterator = packages.iterator(); iterator.hasNext();) {
      String p = iterator.next();
      InspectionPackageNode pNode = new InspectionPackageNode(p);
      Set<RefElement> elements = myPackageContents.get(p);
      for(Iterator<RefElement> iterator1 = elements.iterator(); iterator1.hasNext(); ) {
        RefElement refElement = iterator1.next();
        pNode.add(new RefElementNode(refElement, shouldLoadElementCallees()));
      }
      content.add(pNode);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  protected boolean shouldLoadElementCallees() {
    return false;
  }

  public void updateContent() {
    myPackageContents = new HashMap<String, Set<RefElement>>();
    getManager().getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (refElement.isValid() && getFilter().accepts(refElement)) {
          String packageName = RefUtil.getPackageName(refElement);
          Set<RefElement> content = myPackageContents.get(packageName);
          if (content == null) {
            content = new HashSet<RefElement>();
            myPackageContents.put(packageName, content);
          }
          content.add(refElement);
        }
      }
    });
  }

  public boolean hasReportedProblems() {
    return myPackageContents.size() > 0;
  }

  public Map<String, Set<RefElement>> getPackageContent() {
    return myPackageContents;
  }
}
