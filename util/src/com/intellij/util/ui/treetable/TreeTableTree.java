package com.intellij.util.ui.treetable;

import com.intellij.util.ui.Tree;
import com.intellij.util.ui.treetable.TreeTable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * author: lesya
 */
public class TreeTableTree extends Tree{
  private Border myBorder;
  private final TreeTable myTreeTable;
  protected int myVisibleRow;



  public TreeTableTree(TreeModel model, TreeTable treeTable) {
    super(model);
    myTreeTable = treeTable;
    setCellRenderer(getCellRenderer());
  }

  public void updateUI() {
    super.updateUI();
    TreeCellRenderer tcr = super.getCellRenderer();
    if (tcr instanceof DefaultTreeCellRenderer) {
      DefaultTreeCellRenderer dtcr = ((DefaultTreeCellRenderer)tcr);
      dtcr.setTextSelectionColor(UIManager.getColor("Table.selectionForeground"));
      dtcr.setBackgroundSelectionColor(UIManager.getColor("Table.selectionBackground"));
    }
  }

  public void setRowHeight(int rowHeight) {
    if (rowHeight > 0) {
      super.setRowHeight(rowHeight);
      if (myTreeTable != null && myTreeTable.getRowHeight() != rowHeight) {
        myTreeTable.setRowHeight(getRowHeight());
      }
    }
  }

  public void setBounds(int x, int y, int w, int h) {
    super.setBounds(x, 0, w, myTreeTable.getHeight());
  }

  public void paint(Graphics g) {
    Graphics g1 = g.create();
    g1.translate(0, -myVisibleRow * getRowHeight());
    super.paint(g1);
    g1.dispose();
    if (myBorder != null){
      myBorder.paintBorder(this, g, 0, 0, myTreeTable.getWidth(), getRowHeight());
    }
  }

  public void setBorder(Border border) {
    super.setBorder(border);
    myBorder = border;
  }

  public void setTreeTableTreeBorder(Border border) {
    myBorder = border;
  }

  public void setVisibleRow(int row) {
    myVisibleRow  = row;
    setPreferredSize(new Dimension(getPreferredSize().height, getRowBounds(myVisibleRow).width));
  }

  public void _processKeyEvent(KeyEvent e){
    super.processKeyEvent(e);
  }

  public void setCellRenderer(final TreeCellRenderer x) {
    super.setCellRenderer(
        new TreeCellRenderer() {
          public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                        boolean selected, boolean expanded,
                                                        boolean leaf, int row, boolean hasFocus) {
            hasFocus = SwingUtilities.findFocusOwner(myTreeTable)!=null
                && row == myTreeTable.getSelectedRow();
            return x.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
          }
        }
    );
  }

}
