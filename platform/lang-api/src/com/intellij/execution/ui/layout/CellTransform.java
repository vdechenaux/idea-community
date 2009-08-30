package com.intellij.execution.ui.layout;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.content.Content;

import java.util.ArrayList;

public interface CellTransform {

  interface Restore {
    ActionCallback restoreInGrid();

    class List implements Restore {
      private final ArrayList<Restore> myActions = new ArrayList();
      private boolean myRestoringNow;

      public void add(Restore restore) {
        myActions.add(restore);
      }

      public ActionCallback restoreInGrid() {
        myRestoringNow = true;
        if (myActions.size() == 0) return new ActionCallback.Done();
        final ActionCallback topCallback = restore(0);
        return topCallback.doWhenDone(new Runnable() {
          public void run() {
            myActions.clear();
            myRestoringNow = false;
          }
        });
      }

      private ActionCallback restore(final int index) {
        final ActionCallback result = new ActionCallback();
        final Restore action = myActions.get(index);
        final ActionCallback actionCalback = action.restoreInGrid();
        actionCalback.doWhenDone(new Runnable() {
          public void run() {
            if (index < myActions.size() - 1) {
              restore(index + 1).notifyWhenDone(result);
            } else {
              result.setDone();
            }
          }
        });

        return result;
      }

      public boolean isRestoringNow() {
        return myRestoringNow;
      }
    }
  }


  interface Facade {
    void minimize(Content content, Restore restore);

    void moveToTab(final Content content);

    void moveToGrid(final Content content);

    Restore detach(final Content[] content);
  }
 
}