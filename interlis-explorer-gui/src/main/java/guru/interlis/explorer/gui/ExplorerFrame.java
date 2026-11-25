package guru.interlis.explorer.gui;

import guru.interlis.explorer.DuckDbStore.StoredObject;
import guru.interlis.explorer.ExplorerQueries;
import guru.interlis.explorer.ExplorerQueries.Occurrence;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.xtf24.Xtf24Exception;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

final class ExplorerFrame extends JFrame {
    private static final String LAST_TRANSFER_DIRECTORY = "lastTransferDirectory";
    private static final int REFERENCE_ARROW_WIDTH = 28;
    private static final int REFERENCE_ARROW_GAP = 6;
    private static final Icon REFERENCE_ARROW = new GlyphIcon("→", REFERENCE_ARROW_WIDTH);

    private record Visit(long objectPosition, List<Integer> rowPath) {}

    private record OpenedDataset(ExplorerDataset dataset, ExplorerQueries queries, ExplorerQueries.SqlCatalog catalog,
                                 Set<SymbolRef> basketTopics) {}

    private final JButton back = new JButton("Back");
    private final ArrayDeque<Visit> history = new ArrayDeque<>();
    private final JButton open = new JButton("Open dataset…");
    private final JLabel status = new JLabel("Choose the local model files and an XTF 2.4 FULL transfer.");
    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final JCheckBox showAllModels = new JCheckBox("Show all models");
    private final JLabel classTitle = new JLabel("Select a class");
    private final JLabel count = new JLabel(" ");
    private final JList<StoredObject> objectList = new JList<>();
    private final JLabel objectTitle = new JLabel("Select an object");
    private final InspectorTableModel attributes = new InspectorTableModel();
    private final JTable inspector = inspectorTable();
    private final JTabbedPane tabs = new JTabbedPane();
    private final QueryPanel queryPanel = new QueryPanel(this::openQueryOccurrence);

    private ExplorerDataset dataset;
    private Set<SymbolRef> basketTopics = Set.of();
    private boolean navigating;
    private volatile boolean closing;

    ExplorerFrame() {
        super("INTERLIS Object Explorer");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (closing) {
                    return;
                }
                closing = true;
                setVisible(false);
                queryPanel.cancelQuery();
                if (open.isEnabled()) {
                    closeWindow();
                }
            }
        });

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(26);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            private final Icon modelIcon = new GlyphIcon("M");
            private final Icon topicIcon = new GlyphIcon("T");
            private final Icon classIcon = new GlyphIcon("C");

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                          boolean expanded, boolean leaf, int row, boolean focused) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
                var node = (DefaultMutableTreeNode) value;
                setIcon(switch (node.getUserObject()) {
                    case ModelDescriptor model -> {
                        setText(model.name().declared().last());
                        yield modelIcon;
                    }
                    case TopicDescriptor topic -> {
                        setText(topic.name().declared().last());
                        yield topicIcon;
                    }
                    case ObjectDescriptor type -> {
                        setText(type.name().declared().last());
                        yield classIcon;
                    }
                    case null, default -> {
                        setText("");
                        yield null;
                    }
                });
                return this;
            }
        });
        tree.addTreeSelectionListener(event -> {
            if (!navigating) {
                selectClass();
            }
        });

        objectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        objectList.setFixedCellHeight(26);
        var objectRenderer = new DefaultListCellRenderer();
        objectList.setCellRenderer((list, object, index, selected, focused) ->
                objectRenderer.getListCellRendererComponent(list, objectLabel(object), index, selected, focused));
        objectList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                inspectObject();
            }
        });

        var navigation = section(new JLabel("Model"), tree, showAllModels);
        showAllModels.setEnabled(false);
        showAllModels.addActionListener(event -> refreshModelTree());
        var instances = section(classTitle, objectList);
        instances.add(count, BorderLayout.SOUTH);
        var inspection = section(objectTitle, inspector);
        var content = split(JSplitPane.HORIZONTAL_SPLIT, navigation,
                split(JSplitPane.VERTICAL_SPLIT, instances, inspection, 230, 0.35), 250, 0.25);

        var toolbar = new JPanel(new BorderLayout());
        var actions = Box.createHorizontalBox();
        actions.add(open);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(back);
        toolbar.add(actions, BorderLayout.WEST);
        open.addActionListener(event -> chooseDataset());
        back.setEnabled(false);
        back.addActionListener(event -> goBack());
        status.putClientProperty("html.disable", true);
        var panel = new JPanel(new BorderLayout(0, 14));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        panel.add(toolbar, BorderLayout.NORTH);
        tabs.addTab("Objects", content);
        tabs.addTab("Query", queryPanel);
        tabs.addChangeListener(event -> back.setVisible(tabs.getSelectedIndex() == 0));
        panel.add(tabs, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        setContentPane(panel);
        setMinimumSize(new Dimension(640, 470));
        setSize(1000, 690);
        setLocationByPlatform(true);
    }

    private JTable inspectorTable() {
        var table = new JTable(attributes);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(175);
        var valueColumn = table.getColumnModel().getColumn(1);
        valueColumn.setPreferredWidth(420);
        valueColumn.setMinWidth(REFERENCE_ARROW_WIDTH + REFERENCE_ARROW_GAP + 4);
        var valueRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                            boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                boolean linked = navigationTarget(row).isPresent();
                setIcon(linked ? REFERENCE_ARROW : null);
                setToolTipText(linked ? "Click the arrow or press Enter to open" : null);
                return this;
            }
        };
        valueRenderer.setHorizontalTextPosition(SwingConstants.LEFT);
        valueRenderer.setIconTextGap(REFERENCE_ARROW_GAP);
        valueRenderer.putClientProperty("html.disable", true);
        valueColumn.setCellRenderer(valueRenderer);
        var attributeRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                            boolean focused, int row, int column) {
                var label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                int indent = 6 + attributes.depth(row) * 18;
                if (attributes.expandable(row)) {
                    label.setText((attributes.expanded(row) ? "▾ " : "▸ ") + value);
                } else {
                    indent += 14;
                }
                var border = label.getBorder();
                label.setBorder(BorderFactory.createCompoundBorder(border,
                        BorderFactory.createEmptyBorder(0, indent, 0, 0)));
                return label;
            }
        };
        attributeRenderer.putClientProperty("html.disable", true);
        table.getColumnModel().getColumn(0).setCellRenderer(attributeRenderer);
        var mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 1) {
                    return;
                }
                var point = event.getPoint();
                int row = table.rowAtPoint(point);
                if (table.columnAtPoint(point) == 0) {
                    attributes.toggle(row);
                } else if (isReferenceArrow(table, row, point)) {
                    followReference(row);
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                var point = event.getPoint();
                boolean overArrow = ExplorerFrame.this.getCursor().getType() != Cursor.WAIT_CURSOR
                        && isReferenceArrow(table, table.rowAtPoint(point), point);
                table.setCursor(overArrow ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                table.setCursor(null);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                table.setCursor(null);
            }
        };
        table.addMouseListener(mouse);
        table.addMouseMotionListener(mouse);
        attributes.addTableModelListener(event -> table.setCursor(null));
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleAttribute");
        table.getActionMap().put("toggleAttribute", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                attributes.toggle(table.getSelectedRow());
            }
        });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openReference");
        table.getActionMap().put("openReference", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                followReference(table.getSelectedRow());
            }
        });
        table.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent source) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent source) {
                int row = table.getSelectedRow();
                if (row < 0) {
                    return null;
                }
                return new StringSelection(attributes.getValueAt(table.convertRowIndexToModel(row), 1));
            }
        });
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcut), "copy");
        table.getActionMap().put("copy", TransferHandler.getCopyAction());
        return table;
    }

    private record GlyphIcon(String glyph, int width) implements Icon {
        private static final int GLYPH_SIZE = 16;

        GlyphIcon(String glyph) {
            this(glyph, GLYPH_SIZE);
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return GLYPH_SIZE;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var paint = graphics.create();
            try {
                var foreground = component.getForeground();
                paint.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 204));
                paint.setFont(component.getFont().deriveFont(Font.PLAIN, 11f));
                var metrics = paint.getFontMetrics();
                int left = x + (GLYPH_SIZE - metrics.stringWidth(glyph)) / 2;
                int baseline = y + (getIconHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                paint.drawString(glyph, left, baseline);
            } finally {
                paint.dispose();
            }
        }
    }

    private static JPanel section(JLabel title, JComponent view) {
        return section(title, view, null);
    }

    private static JPanel section(JLabel title, JComponent view, JComponent action) {
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.putClientProperty("html.disable", true);
        var scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        if (action == null) {
            panel.add(title, BorderLayout.NORTH);
        } else {
            var header = new JPanel(new BorderLayout(8, 0));
            header.add(title, BorderLayout.WEST);
            header.add(action, BorderLayout.EAST);
            panel.add(header, BorderLayout.NORTH);
        }
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    static JSplitPane split(int direction, JComponent first, JComponent second, int divider, double weight) {
        var pane = new JSplitPane(direction, first, second);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setDividerLocation(divider);
        pane.setResizeWeight(weight);
        if (pane.getUI() instanceof BasicSplitPaneUI ui) {
            var line = UIManager.getColor("Separator.foreground");
            ui.getDivider().setBorder(direction == JSplitPane.HORIZONTAL_SPLIT
                    ? BorderFactory.createMatteBorder(0, 1, 0, 0, line)
                    : BorderFactory.createMatteBorder(1, 0, 0, 0, line));
        }
        return pane;
    }

    private static File lastTransferDirectory() {
        try {
            var preferences = Preferences.userNodeForPackage(ExplorerFrame.class);
            var path = preferences.get(LAST_TRANSFER_DIRECTORY, "");
            if (!path.isEmpty()) {
                var directory = new File(path);
                if (directory.isDirectory() && directory.canRead() && directory.canExecute()) {
                    return directory;
                }
            }
        } catch (SecurityException | IllegalStateException ignored) {
            // The chooser can still use its default directory.
        }
        return null;
    }

    private static void rememberTransferDirectory(Path xtf) {
        try {
            var preferences = Preferences.userNodeForPackage(ExplorerFrame.class);
            preferences.put(LAST_TRANSFER_DIRECTORY, xtf.toAbsolutePath().getParent().toString());
        } catch (SecurityException | IllegalStateException ignored) {
            // A preferences failure does not affect the imported dataset.
        }
    }

    private void chooseDataset() {
        var modelChooser = new JFileChooser(lastTransferDirectory());
        modelChooser.setDialogTitle("Select INTERLIS model files and their imports");
        modelChooser.setMultiSelectionEnabled(true);
        modelChooser.setAcceptAllFileFilterUsed(false);
        modelChooser.setFileFilter(new FileNameExtensionFilter("INTERLIS models (*.ili)", "ili"));
        if (modelChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        var files = Arrays.stream(modelChooser.getSelectedFiles()).map(File::toPath).toList();

        var transferChooser = new JFileChooser(modelChooser.getCurrentDirectory());
        transferChooser.setDialogTitle("Select an XTF 2.4 FULL transfer");
        transferChooser.setAcceptAllFileFilterUsed(false);
        transferChooser.setFileFilter(new FileNameExtensionFilter("INTERLIS transfer (*.xtf)", "xtf"));
        if (transferChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openDataset(files, transferChooser.getSelectedFile().toPath());
        }
    }

    private void openDataset(List<Path> files, Path xtf) {
        open.setEnabled(false);
        var previousCursor = getCursor();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        status.setText("Compiling models and importing " + xtf.getFileName() + "…");
        new SwingWorker<OpenedDataset, Void>() {
            @Override
            protected OpenedDataset doInBackground() throws Exception {
                var opened = ExplorerDataset.openWithSpatial(files, xtf, () -> closing);
                try {
                    var queries = new ExplorerQueries(opened.store());
                    return new OpenedDataset(opened, queries, queries.sqlCatalog(), opened.store().basketTopics());
                } catch (SQLException | RuntimeException | Error failure) {
                    try {
                        opened.close();
                    } catch (IOException | SQLException cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
            }

            @Override
            protected void done() {
                setCursor(previousCursor);
                try {
                    var result = get();
                    var opened = result.dataset();
                    if (closing) {
                        release(opened);
                    } else {
                        var previous = dataset;
                        dataset = opened;
                        queryPanel.setDataset(result.queries(), result.catalog(), () -> release(previous));
                        history.clear();
                        back.setEnabled(false);
                        basketTopics = result.basketTopics();
                        showAllModels.setSelected(false);
                        showAllModels.setEnabled(true);
                        tree.setModel(new DefaultTreeModel(modelTree(opened.plan(), Optional.of(basketTopics))));
                        selectClass();
                        status.setText("Opened " + xtf.getFileName()
                                + (basketTopics.isEmpty() ? " · No baskets in this transfer." : ""));
                        rememberTransferDirectory(xtf);
                    }
                } catch (ExecutionException failure) {
                    if (!closing) {
                        status.setText(dataset == null ? "Dataset could not be opened."
                                : "Dataset could not be opened. The previous dataset is still open.");
                        var cause = failure.getCause();
                        var context = cause instanceof Xtf24Exception ? "Expected an XTF 2.4 FULL transfer.\n" : "";
                        showError("Open dataset", context + cause.getMessage());
                    } else if (failure.getCause().getSuppressed().length > 0) {
                        showError("Clean up dataset", failure.getCause().getSuppressed()[0].getMessage());
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    status.setText("Opening was interrupted.");
                } finally {
                    if (closing) {
                        closeWindow();
                    } else {
                        open.setEnabled(true);
                    }
                }
            }
        }.execute();
    }

    static DefaultMutableTreeNode modelTree(TransferPlan plan, Optional<Set<SymbolRef>> visibleTopics) {
        var root = new DefaultMutableTreeNode();
        for (var model : plan.models()) {
            var modelNode = new DefaultMutableTreeNode(model);
            for (var topicRef : model.topics()) {
                if (visibleTopics.isPresent() && !visibleTopics.orElseThrow().contains(topicRef)) {
                    continue;
                }
                var topic = plan.topic(topicRef).orElseThrow();
                var topicNode = new DefaultMutableTreeNode(topic);
                modelNode.add(topicNode);
                for (var classRef : topic.objectTypes()) {
                    var type = plan.object(classRef).orElseThrow();
                    if (type.kind() == SymbolKind.CLASS) {
                        topicNode.add(new DefaultMutableTreeNode(type, false));
                    }
                }
            }
            if (visibleTopics.isEmpty() || modelNode.getChildCount() > 0) {
                root.add(modelNode);
            }
        }
        return root;
    }

    private void refreshModelTree() {
        if (closing || dataset == null) {
            return;
        }
        var topics = showAllModels.isSelected() ? Optional.<Set<SymbolRef>>empty() : Optional.of(basketTopics);
        var root = modelTree(dataset.plan(), topics);
        boolean selectionKept;
        navigating = true;
        try {
            selectionKept = replaceModelTree(tree, root);
        } finally {
            navigating = false;
        }
        if (!selectionKept) {
            selectClass();
        }
    }

    static boolean replaceModelTree(JTree tree, DefaultMutableTreeNode root) {
        var oldRoot = new TreePath(tree.getModel().getRoot());
        var descendants = tree.getExpandedDescendants(oldRoot);
        var expanded = descendants == null ? List.<TreePath>of() : Collections.list(descendants);
        var selected = tree.getSelectionPath();
        tree.setModel(new DefaultTreeModel(root));
        for (var path : expanded) {
            var replacement = findPath(root, path);
            if (replacement != null) {
                tree.expandPath(replacement);
            }
        }
        var replacement = selected == null ? null : findPath(root, selected);
        if (replacement == null) {
            return false;
        }
        tree.setSelectionPath(replacement);
        tree.scrollPathToVisible(replacement);
        return true;
    }

    private static TreePath findPath(DefaultMutableTreeNode root, TreePath path) {
        var node = root;
        for (int i = 1; i < path.getPathCount(); i++) {
            var descriptor = ((DefaultMutableTreeNode) path.getPathComponent(i)).getUserObject();
            var children = node.children();
            node = null;
            while (children.hasMoreElements()) {
                var child = (DefaultMutableTreeNode) children.nextElement();
                if (descriptor.equals(child.getUserObject())) {
                    node = child;
                    break;
                }
            }
            if (node == null) {
                return null;
            }
        }
        return new TreePath(node.getPath());
    }

    private void selectClass() {
        if (closing || dataset == null) {
            return;
        }
        objectList.setListData(new StoredObject[0]);
        inspectObject();
        classTitle.setText("Select a class");
        count.setText(" ");
        if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof ObjectDescriptor type) {
            classTitle.setText(type.name().declared().toString());
            try {
                var objects = dataset.objects(type);
                showObjects(type, objects);
            } catch (SQLException | RuntimeException failure) {
                count.setText("Object list could not be loaded.");
                showError("Read objects", failure.getMessage());
            }
        }
    }

    private void inspectObject() {
        if (closing || dataset == null) {
            return;
        }
        var selected = objectList.getSelectedValue();
        if (selected == null) {
            attributes.clear();
            objectTitle.setText("Select an object");
            return;
        }
        objectTitle.setText(objectLabel(selected));
        try {
            var relationships = InspectorRelationships.read(dataset.plan(), dataset.store(), selected);
            attributes.show(dataset.plan(), selected.object().data(), relationships);
        } catch (SQLException | RuntimeException failure) {
            attributes.show(dataset.plan(), selected.object().data(), InspectorRelationships.EMPTY);
            showError("Read relationships", failure.getMessage());
        }
    }

    private void showObjects(ObjectDescriptor type, List<StoredObject> objects) {
        classTitle.setText(type.name().declared().toString());
        objectList.setListData(objects.toArray(StoredObject[]::new));
        count.setText(objects.isEmpty() ? "No objects of this class."
                : objects.size() + " shown (up to " + ExplorerDataset.OBJECT_LIMIT + ")");
    }

    private boolean isReferenceArrow(JTable table, int row, Point point) {
        if (navigationTarget(row).isEmpty()) {
            return false;
        }
        var cell = table.getCellRect(row, 1, false);
        if (!cell.contains(point)) {
            return false;
        }
        var label = (JLabel) table.prepareRenderer(table.getCellRenderer(row, 1), row, 1);
        var insets = label.getInsets();
        var content = new Rectangle(cell.x + insets.left, cell.y + insets.top,
                cell.width - insets.left - insets.right, cell.height - insets.top - insets.bottom);
        var arrow = new Rectangle();
        SwingUtilities.layoutCompoundLabel(label, label.getFontMetrics(label.getFont()), label.getText(), label.getIcon(),
                label.getVerticalAlignment(), label.getHorizontalAlignment(), label.getVerticalTextPosition(),
                label.getHorizontalTextPosition(), content, arrow, new Rectangle(), label.getIconTextGap());
        return point.x >= arrow.x && point.x < arrow.x + arrow.width;
    }

    private OptionalLong navigationTarget(int row) {
        var target = attributes.target(row);
        var source = objectList.getSelectedValue();
        if (source == null || target.isEmpty() || target.getAsLong() == source.position()) {
            return OptionalLong.empty();
        }
        return target;
    }

    private void followReference(int row) {
        var target = navigationTarget(row);
        if (target.isEmpty()) {
            return;
        }
        var visit = new Visit(objectList.getSelectedValue().position(), attributes.pathTo(row));
        if (openOccurrence(target.getAsLong())) {
            history.push(visit);
            back.setEnabled(true);
        }
    }

    private void goBack() {
        var visit = history.peek();
        if (visit == null || !openOccurrence(visit.objectPosition())) {
            return;
        }
        history.pop();
        back.setEnabled(!history.isEmpty());
        selectInspectorRow(attributes.reveal(visit.rowPath()));
    }

    private void openQueryOccurrence(Occurrence occurrence) {
        if (!openOccurrence(occurrence.sourcePosition())) {
            return;
        }
        tabs.setSelectedIndex(0);
        if (occurrence.valuePath().isEmpty()) {
            objectList.requestFocusInWindow();
            return;
        }
        var selected = objectList.getSelectedValue();
        int row = selected == null ? -1 : attributes.revealOccurrence(selected.object().data(), occurrence.valuePath());
        if (row < 0) {
            showError("Open in Inspector", "The result entry is unavailable.");
        } else {
            selectInspectorRow(row);
        }
    }

    private void selectInspectorRow(int row) {
        if (row >= 0) {
            inspector.setRowSelectionInterval(row, row);
            inspector.scrollRectToVisible(inspector.getCellRect(row, 1, true));
            inspector.requestFocusInWindow();
        }
    }

    private boolean openOccurrence(long position) {
        if (closing || dataset == null) {
            return false;
        }
        try {
            var target = dataset.store().objectAt(position).orElse(null);
            if (target == null) {
                showError("Open object", "The target object is unavailable.");
                return false;
            }
            var type = dataset.plan().object(target.object().type()).orElseThrow();
            var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
            for (var nodes = root.depthFirstEnumeration(); nodes.hasMoreElements();) {
                var node = (DefaultMutableTreeNode) nodes.nextElement();
                if (type.equals(node.getUserObject())) {
                    var objects = dataset.objectsIncluding(type, target);
                    var path = new TreePath(node.getPath());
                    navigating = true;
                    try {
                        tree.setSelectionPath(path);
                        tree.scrollPathToVisible(path);
                    } finally {
                        navigating = false;
                    }
                    showObjects(type, objects);
                    objectList.setSelectedValue(target, true);
                    return true;
                }
            }
            showError("Open object", "The target is outside the class browser.");
        } catch (SQLException | RuntimeException failure) {
            showError("Open object", failure.getMessage());
        }
        return false;
    }

    static String objectLabel(StoredObject entry) {
        var identity = entry.object().identity();
        if (identity.tid().isPresent()) {
            return "TID " + identity.tid().orElseThrow().value();
        }
        return identity.oid().map(oid -> "OID " + oid.value()).orElse("Store position " + entry.position());
    }

    private void closeWindow() {
        var previous = dataset;
        dataset = null;
        open.setEnabled(false);
        queryPanel.setDataset(null, null, () -> {
            release(previous);
            dispose();
        });
    }

    private void release(ExplorerDataset toClose) {
        if (toClose != null) {
            try {
                toClose.close();
            } catch (IOException | SQLException failure) {
                showError("Clean up dataset", failure.getMessage());
            }
        }
    }

    private void showError(String title, String message) {
        var details = new JTextArea(message, 5, 52);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(details), title, JOptionPane.ERROR_MESSAGE);
    }
}
