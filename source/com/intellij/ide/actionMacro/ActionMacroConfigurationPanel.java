package com.intellij.ide.actionMacro;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ListUtil;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 22, 2003
 * Time: 4:01:10 PM
 * To change this template use Options | File Templates.
 */
public class ActionMacroConfigurationPanel {
  private JPanel myPanel;
  private JButton myDeleteButton;
  private JButton myRenameButton;
  private JButton myExcludeActionButton;
  private JList myMacrosList;
  private JList myMacroActionsList;

  final DefaultListModel myMacrosModel = new DefaultListModel();
  private ActionMacro mySelectedMacro;

  public ActionMacroConfigurationPanel() {
    ListUtil.addRemoveListener(myDeleteButton, myMacrosList);

    myMacrosList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myMacroActionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myMacrosList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int selIndex = myMacrosList.getSelectedIndex();
        if (selIndex == -1) {
          ((DefaultListModel) myMacroActionsList.getModel()).removeAllElements();
          myExcludeActionButton.setEnabled(false);
          myRenameButton.setEnabled(false);
        } else {
          myRenameButton.setEnabled(true);
          initActionList((ActionMacro)myMacrosModel.getElementAt(selIndex));
        }
      }
    });

    myMacroActionsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int selIdx = myMacroActionsList.getSelectedIndex();
        myExcludeActionButton.setEnabled(selIdx != -1);
      }
    });

    myExcludeActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selIndex = myMacrosList.getSelectedIndex();

        if (selIndex != -1) {
          final ActionMacro macro = (ActionMacro)myMacrosModel.getElementAt(selIndex);
          macro.deleteAction(myMacroActionsList.getSelectedIndex());
        }
        ListUtil.removeSelectedItems(myMacroActionsList);
      }
    });

    myRenameButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selIndex = myMacrosList.getSelectedIndex();
        if (selIndex == -1) return;
        final ActionMacro macro = (ActionMacro) myMacrosModel.getElementAt(selIndex);
        final String newName = Messages.showInputDialog(myPanel, "Enter New Name", "Rename Macro",
                                                        Messages.getQuestionIcon(), macro.getName(), null);
        if (newName != null) {
          macro.setName(newName);
          myMacrosList.repaint();
        }
      }
    });
  }

  public void reset() {
    final ActionMacro[] allMacros = ActionMacroManager.getInstance().getAllMacros();
    for (int i = 0; i < allMacros.length; i++) {
      ActionMacro macro = allMacros[i];
      myMacrosModel.addElement(macro.clone());
    }
    myMacrosList.setModel(myMacrosModel);
    ListScrollingUtil.ensureSelectionExists(myMacrosList);
  }

  public void apply() {
    final ActionMacroManager manager = ActionMacroManager.getInstance();
    ActionMacro[] macros = manager.getAllMacros();
    HashSet<String> removedIds = new HashSet<String>();
    for (int i = 0; i < macros.length; i++) {
      removedIds.add(macros[i].getActionId());
    }

    manager.removeAllMacros();

    final Enumeration newMacros = myMacrosModel.elements();
    while (newMacros.hasMoreElements()) {
      ActionMacro macro = (ActionMacro) newMacros.nextElement();
      manager.addMacro(macro);
      removedIds.remove(macro.getActionId());
    }
    manager.registerActions();

    for (Iterator<String> iterator = removedIds.iterator(); iterator.hasNext();) {
      String id = iterator.next();
      Keymap[] allKeymaps = KeymapManagerEx.getInstanceEx().getAllKeymaps();
      for (int i = 0; i < allKeymaps.length; i++) {
        Keymap keymap = allKeymaps[i];
        keymap.removeAllActionShortcuts(id);
      }
    }
  }

  public boolean isModified() {
    final ActionMacro[] allMacros = ActionMacroManager.getInstance().getAllMacros();
    if (allMacros.length != myMacrosModel.getSize()) return true;
    for (int i = 0; i < allMacros.length; i++) {
      ActionMacro macro = allMacros[i];
      ActionMacro newMacro = (ActionMacro) myMacrosModel.get(i);
      if (!macro.equals(newMacro)) return true;
    }
    return false;
  }

  private void initActionList(ActionMacro macro) {
    mySelectedMacro = macro;
    DefaultListModel actionModel = new DefaultListModel();
    final ActionMacro.ActionDescriptor[] actions = macro.getActions();
    for (int i = 0; i < actions.length; i++) {
      ActionMacro.ActionDescriptor action = actions[i];
      actionModel.addElement(action);
    }
    myMacroActionsList.setModel(actionModel);
    ListScrollingUtil.ensureSelectionExists(myMacroActionsList);
  }

  public JPanel getPanel() {
    return myPanel;
  }

}
