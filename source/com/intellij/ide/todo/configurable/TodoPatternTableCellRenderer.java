package com.intellij.ide.todo.configurable;

import com.intellij.psi.search.TodoPattern;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

final class TodoPatternTableCellRenderer extends DefaultTableCellRenderer{
  private List<TodoPattern> myPatterns;

  public TodoPatternTableCellRenderer(List<TodoPattern> patterns){
    myPatterns=patterns;
  }

  public Component getTableCellRendererComponent(
    JTable table,
    Object value,
    boolean isSelected,
    boolean hasFocus,
    int row,
    int column
  ){
    super.getTableCellRendererComponent(table,value,isSelected,hasFocus,row,column);
    TodoPattern pattern=myPatterns.get(row);
    if(isSelected){
      setForeground(UIManager.getColor("Table.selectionForeground"));
    }else{
      if(pattern.getPattern()==null){
        setForeground(Color.RED);
      }else{
        setForeground(UIManager.getColor("Table.foreground"));
      }
    }
    return this;
  }
}
