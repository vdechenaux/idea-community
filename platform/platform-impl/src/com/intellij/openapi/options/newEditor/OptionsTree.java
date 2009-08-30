package com.intellij.openapi.options.newEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.GroupedElementsRenderer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class OptionsTree extends JPanel implements Disposable, OptionsEditorColleague {
  Project myProject;
  final SimpleTree myTree;
  List<ConfigurableGroup> myGroups;
  FilteringTreeBuilder myBuilder;
  Root myRoot;
  OptionsEditorContext myContext;

  Map<Configurable, EditorNode> myConfigurable2Node = new HashMap<Configurable, EditorNode>();

  MergingUpdateQueue mySelection;
  private final OptionsTree.Renderer myRendrer;

  public OptionsTree(Project project, ConfigurableGroup[] groups, OptionsEditorContext context) {
    myProject = project;
    myGroups = Arrays.asList(groups);
    myContext = context;


    myRoot = new Root();
    final SimpleTreeStructure structure = new SimpleTreeStructure() {
      public Object getRootElement() {
        return myRoot;
      }
    };

    myTree = new MyTree();
    myTree.setBorder(new EmptyBorder(0, 1, 0, 0));

    myTree.setRowHeight(-1);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myRendrer = new Renderer();
    myTree.setCellRenderer(myRendrer);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myBuilder = new FilteringTreeBuilder(myProject, myTree, myContext.getFilter(), structure, new WeightBasedComparator(false)) {
      @Override
      protected boolean isSelectable(final Object nodeObject) {
        return nodeObject instanceof EditorNode;
      }

      @Override
      public boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
        if (nodeDescriptor instanceof SimpleNode) {
          final SimpleNode node = (SimpleNode)getOriginalNode(((SimpleNode)nodeDescriptor));
          if (myContext.isHoldingFilter()) return true;
          if (node.getParent() == myRoot) return false;
        }
        return super.isAutoExpandNode(nodeDescriptor);
      }
    };
    myBuilder.setFilteringMerge(300, null);
    Disposer.register(this, myBuilder);

    myBuilder.updateFromRoot();

    setLayout(new BorderLayout());

    myTree.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        revalidateTree();
      }

      @Override
      public void componentMoved(final ComponentEvent e) {
        revalidateTree();
      }

      @Override
      public void componentShown(final ComponentEvent e) {
        revalidateTree();
      }
    });

    final JScrollPane scrolls = new JScrollPane(myTree);
    scrolls.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

    add(scrolls, BorderLayout.CENTER);

    mySelection = new MergingUpdateQueue("OptionsTree", 150, false, this, this, this).setRestartTimerOnAdd(true);
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        final TreePath path = e.getNewLeadSelectionPath();
        if (path == null) {
          queueSelection(null);
        }
        else {
          final Base base = extractNode(path.getLastPathComponent());
          queueSelection(base != null ? base.getConfigurable() : null);
        }
      }
    });
    myTree.addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        _onTreeKeyEvent(e);
      }

      public void keyPressed(final KeyEvent e) {
        _onTreeKeyEvent(e);
      }

      public void keyReleased(final KeyEvent e) {
        _onTreeKeyEvent(e);
      }
    });
  }

  protected void _onTreeKeyEvent(KeyEvent e) {
    final KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);

    final Object action = myTree.getInputMap().get(stroke);
    if (action == null) {
      onTreeKeyEvent(e);
    }
  }

  protected void onTreeKeyEvent(KeyEvent e) {

  }


  ActionCallback select(@Nullable Configurable configurable) {
    return queueSelection(configurable);
  }

  public void selectFirst() {
    for (ConfigurableGroup eachGroup : myGroups) {
      final Configurable[] kids = eachGroup.getConfigurables();
      if (kids.length > 0) {
        queueSelection(kids[0]);
        return;
      }
    }
  }

  ActionCallback queueSelection(final Configurable configurable) {
    final ActionCallback callback = new ActionCallback();
    final Update update = new Update(this) {
      public void run() {
        if (configurable == null) {
          myTree.getSelectionModel().clearSelection();
          myContext.fireSelected(null, OptionsTree.this);
        }
        else {
          final EditorNode editorNode = myConfigurable2Node.get(configurable);
          myBuilder.select(myBuilder.getVisibleNodeFor(editorNode), new Runnable() {
            public void run() {
              myContext.fireSelected(configurable, OptionsTree.this).doWhenProcessed(new Runnable() {
                public void run() {
                  callback.setDone();
                }
              });
            }
          });
        }
      }

      @Override
      public void setRejected() {
        super.setRejected();
        callback.setRejected();
      }
    };
    mySelection.queue(update);
    return callback;
  }

  void revalidateTree() {
    myTree.invalidate();
    myTree.setRowHeight(myTree.getRowHeight() == -1 ? -2 : -1);
    myTree.revalidate();
    myTree.repaint();
  }

  public JTree getTree() {
    return myTree;
  }

  public List<Configurable> getPathToRoot(final Configurable configurable) {
    final ArrayList<Configurable> path = new ArrayList<Configurable>();

    EditorNode eachNode = myConfigurable2Node.get(configurable);
    if (eachNode == null) return path;

    while (eachNode != null) {
      path.add(eachNode.getConfigurable());
      final SimpleNode parent = eachNode.getParent();
      if (parent instanceof EditorNode) {
        eachNode = (EditorNode)parent;
      }
      else {
        break;
      }
    }

    return path;
  }

  @Nullable
  public Configurable getParentFor(final Configurable configurable) {
    final List<Configurable> path = getPathToRoot(configurable);
    if (path.size() > 1) {
      return path.get(1);
    }
    else {
      return null;
    }
  }

  public SimpleNode findNodeFor(final Configurable toSelect) {
    return myConfigurable2Node.get(toSelect);
  }

  class Renderer extends GroupedElementsRenderer.Tree implements TreeCellRenderer {


    private JLabel myHandle;

    @Override
    protected void layout() {
      myRendererComponent.setOpaqueActive(false);

      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);

      final NonOpaquePanel content = new NonOpaquePanel(new BorderLayout());
      myHandle = new JLabel("", JLabel.CENTER);
      if (!SystemInfo.isMac) {
        myHandle.setBorder(new EmptyBorder(0, 2, 0, 2));
      }
      myHandle.setOpaque(false);
      content.add(myHandle, BorderLayout.WEST);
      content.add(myComponent, BorderLayout.CENTER);
      myRendererComponent.add(content, BorderLayout.CENTER);
    }

    public Component getTreeCellRendererComponent(final JTree tree,
                                                  final Object value,
                                                  final boolean selected,
                                                  final boolean expanded,
                                                  final boolean leaf,
                                                  final int row,
                                                  final boolean hasFocus) {


      JComponent result;
      Color fg = UIUtil.getTreeTextForeground();

      final Base base = extractNode(value);
      if (base instanceof EditorNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;

        final EditorNode editor = (EditorNode)base;
        ConfigurableGroup group = null;
        if (editor.getParent() == myRoot) {
          final DefaultMutableTreeNode prevValue = ((DefaultMutableTreeNode)value).getPreviousSibling();
          if (prevValue == null || prevValue instanceof LoadingNode) {
            group = editor.getGroup();
          }
          else {
            final Base prevBase = extractNode(prevValue);
            if (prevBase instanceof EditorNode) {
              final EditorNode prevEditor = (EditorNode)prevBase;
              if (prevEditor.getGroup() != editor.getGroup()) {
                group = editor.getGroup();
              }
            }
          }
        }

        int forcedWidth = 2000;
        TreePath path = tree.getPathForRow(row);
        if (path == null) {
          if (value instanceof DefaultMutableTreeNode) {
            path = new TreePath(((DefaultMutableTreeNode)value).getPath());
          }
        }

        final boolean toStretch = tree.isVisible() && path != null;

        if (toStretch) {
          final Rectangle visibleRect = tree.getVisibleRect();

          int nestingLevel = tree.isRootVisible() ? path.getPathCount() - 1 : path.getPathCount() - 2;

          final int left = UIManager.getInt("Tree.leftChildIndent");
          final int right = UIManager.getInt("Tree.rightChildIndent");

          final Insets treeInsets = tree.getInsets();

          int indent = (left + right) * nestingLevel + (treeInsets != null ? treeInsets.left + treeInsets.right : 0);

          forcedWidth = visibleRect.width > 0 ? visibleRect.width - indent : forcedWidth;
        }

        result = configureComponent(base.getText(), base.getText(), null, null, row == -1 ? true : selected, group != null,
                                    group != null ? group.getDisplayName() : null, forcedWidth - 4);


        if (base.isError()) {
          fg = Color.red;
        }
        else if (base.isModified()) {
          fg = Color.blue;
        }

      }
      else {
        result = configureComponent(value.toString(), null, null, null, selected, false, null, -1);
      }

      if (value instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        TreePath nodePath = new TreePath(node.getPath());
        myHandle.setIcon(((SimpleTree)tree).getHandleIcon(node, nodePath));
      } else {
        myHandle.setIcon(null);
      }


      myTextLabel.setForeground(selected ? UIUtil.getTreeSelectionForeground() : fg);

      myTextLabel.setOpaque(selected);

      return result;
    }

    protected JComponent createItemComponent() {
      myTextLabel = new ErrorLabel();
      return myTextLabel;
    }

    public boolean isUnderHandle(final Point point) {
      final Point handlePoint = SwingUtilities.convertPoint(myRendererComponent, point, myHandle);
      final Rectangle bounds = myHandle.getBounds();
      return bounds.x < handlePoint.x && bounds.getMaxX() >= handlePoint.x;
    }
  }

  @Nullable
  private Base extractNode(Object object) {
    if (object instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode uiNode = (DefaultMutableTreeNode)object;
      final Object o = uiNode.getUserObject();
      if (o instanceof FilteringTreeStructure.Node) {
        return (Base)((FilteringTreeStructure.Node)o).getDelegate();
      }
    }

    return null;
  }

  abstract class Base extends CachingSimpleNode {

    protected Base(final SimpleNode aParent) {
      super(aParent);
    }

    String getText() {
      return null;
    }

    boolean isModified() {
      return false;
    }

    boolean isError() {
      return false;
    }

    Configurable getConfigurable() {
      return null;
    }
  }

  class Root extends Base {

    Root() {
      super(null);
    }

    protected SimpleNode[] buildChildren() {
      ArrayList<SimpleNode> result = new ArrayList<SimpleNode>();
      for (int i = 0; i < myGroups.size(); i++) {
        ConfigurableGroup eachGroup = myGroups.get(i);
        result.addAll(buildGroup(eachGroup));
      }

      return result.toArray(new SimpleNode[result.size()]);
    }

    private List<EditorNode> buildGroup(final ConfigurableGroup eachGroup) {
      List<EditorNode> result = new ArrayList<EditorNode>();
      final Configurable[] kids = eachGroup.getConfigurables();
      if (kids.length > 0) {
        for (Configurable eachKid : kids) {
          if (isInvisibleNode(eachKid)) {
            result.addAll(OptionsTree.this.buildChildren(eachKid, this, eachGroup));
          }
          else {
            result.add(new EditorNode(this, eachKid, eachGroup));
          }
        }

      }
      return sort(result);
    }
  }

  private boolean isInvisibleNode(final Configurable child) {
    return child instanceof SearchableConfigurable.Parent && !((SearchableConfigurable.Parent)child).isVisible();
  }

  private static List<EditorNode> sort(List<EditorNode> c) {
    List<EditorNode> cc = new ArrayList<EditorNode>(c);
    Collections.sort(cc, new Comparator<EditorNode>() {
      public int compare(final EditorNode o1, final EditorNode o2) {
        return getConfigurableDisplayName(o1.getConfigurable()).compareToIgnoreCase(getConfigurableDisplayName(o2.getConfigurable()));
      }
    });
    return cc;
  }

  private static String getConfigurableDisplayName(final Configurable c) {
    final String name = c.getDisplayName();
    return name != null ? name : "{ Unnamed Page:" + c.getClass().getSimpleName() + " }";
  }

  private List<EditorNode> buildChildren(final Configurable configurable, SimpleNode parent, final ConfigurableGroup group) {
    if (configurable instanceof Configurable.Composite) {
      final Configurable[] kids = ((Configurable.Composite)configurable).getConfigurables();
      final List<EditorNode> result = new ArrayList<EditorNode>(kids.length);
      for (Configurable child : kids) {
        if (isInvisibleNode(child)) {
          result.addAll(buildChildren(child, parent, group));
        }
        result.add(new EditorNode(parent, child, group));
        myContext.registerKid(configurable, child);
      }
      return result; // TODO: DECIDE IF INNERS SHOULD BE SORTED: sort(result);
    }
    else {
      return Collections.EMPTY_LIST;
    }
  }

  class EditorNode extends Base {

    Configurable myConfigurable;
    ConfigurableGroup myGroup;

    EditorNode(SimpleNode parent, Configurable configurable, @Nullable ConfigurableGroup group) {
      super(parent);
      myConfigurable = configurable;
      myGroup = group;
      myConfigurable2Node.put(configurable, this);
      addPlainText(getConfigurableDisplayName(configurable));
    }

    protected EditorNode[] buildChildren() {
      List<EditorNode> list = OptionsTree.this.buildChildren(myConfigurable, this, null);
      return list.toArray(new EditorNode[list.size()]);
    }

    @Override
    public boolean isContentHighlighted() {
      return getParent() == myRoot;
    }

    @Override
    Configurable getConfigurable() {
      return myConfigurable;
    }

    @Override
    public int getWeight() {
      if (getParent() == myRoot) {
        return Integer.MAX_VALUE - myGroups.indexOf(myGroup);
      }
      else {
        return 0;
      }
    }

    public ConfigurableGroup getGroup() {
      return myGroup;
    }

    @Override
    String getText() {
      return getConfigurableDisplayName(myConfigurable).replace("\n", " ");
    }

    @Override
    boolean isModified() {
      return myContext.getModified().contains(myConfigurable);
    }

    @Override
    boolean isError() {
      return myContext.getErrors().containsKey(myConfigurable);
    }
  }

  public void dispose() {
  }

  public ActionCallback onSelected(final Configurable configurable, final Configurable oldConfigurable) {
    return queueSelection(configurable);
  }

  public ActionCallback onModifiedAdded(final Configurable colleague) {
    myTree.repaint();
    return new ActionCallback.Done();
  }

  public ActionCallback onModifiedRemoved(final Configurable configurable) {
    myTree.repaint();
    return new ActionCallback.Done();
  }

  public ActionCallback onErrorsChanged() {
    return new ActionCallback.Done();
  }

  public void processTextEvent(KeyEvent e) {
    myTree.processKeyEvent(e);
  }

  private class MyTree extends SimpleTree {

    private MyTree() {
      getInputMap().clear();
    }

    @Override
    protected boolean highlightSingleNode() {
      return false;
    }

    @Override
    public void setUI(final TreeUI ui) {
      TreeUI actualUI = ui;
      if (!(ui instanceof MyTreeUi)) {
        actualUI = new MyTreeUi();
      }
      super.setUI(actualUI);
    }

    @Override
    protected void configureUiHelper(final TreeUIHelper helper) {
      helper.installToolTipHandler(this);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }


    @Override
    public void processKeyEvent(final KeyEvent e) {
      TreePath path = myTree.getSelectionPath();
      if (path != null) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
          if (isExpanded(path)) {
            collapsePath(path);
            return;
          }
        } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          if (isCollapsed(path)) {
            expandPath(path);
            return;
          }
        }
      }

      super.processKeyEvent(e);
    }

    @Override
    protected void processMouseEvent(final MouseEvent e) {
      final MyTreeUi ui = (MyTreeUi)myTree.getUI();
      if (e.getID() == MouseEvent.MOUSE_RELEASED && UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !ui.isToggleEvent(e)) {
        final TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          final Rectangle bounds = getPathBounds(path);
          if (bounds != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            final boolean selected = isPathSelected(path);
            final boolean expanded = isExpanded(path);
            final Component comp =
              myRendrer.getTreeCellRendererComponent(this, node, selected, expanded, node.isLeaf(), getRowForPath(path), isFocusOwner());

            comp.setBounds(bounds);
            comp.validate();

            Point point = new Point(e.getX() - bounds.x, e.getY() - bounds.y);
            if (myRendrer.isUnderHandle(point)) {
              ui.toggleExpandState(path);
              e.consume();
              return;
            }
          }
        }
      }

      super.processMouseEvent(e);
    }

    private class MyTreeUi extends BasicTreeUI {

      @Override
      public void toggleExpandState(final TreePath path) {
        super.toggleExpandState(path);
      }

      @Override
      public boolean isToggleEvent(final MouseEvent event) {
        return super.isToggleEvent(event);
      }

      @Override
      protected boolean shouldPaintExpandControl(final TreePath path,
                                                 final int row,
                                                 final boolean isExpanded,
                                                 final boolean hasBeenExpanded,
                                                 final boolean isLeaf) {
        return false;
      }

      @Override
      protected void paintHorizontalPartOfLeg(final Graphics g,
                                              final Rectangle clipBounds,
                                              final Insets insets,
                                              final Rectangle bounds,
                                              final TreePath path,
                                              final int row,
                                              final boolean isExpanded,
                                              final boolean hasBeenExpanded,
                                              final boolean isLeaf) {

      }

      @Override
      protected void paintVerticalPartOfLeg(final Graphics g, final Rectangle clipBounds, final Insets insets, final TreePath path) {
      }
    }
  }
}