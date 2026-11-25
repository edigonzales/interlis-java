package guru.interlis.explorer.gui;

import guru.interlis.explorer.DuckDbStore.StoredObject;
import guru.interlis.explorer.ExplorerQueries;
import guru.interlis.explorer.ExplorerQueries.Occurrence;
import guru.interlis.explorer.gui.ExplorerDataset.Link;
import guru.interlis.explorer.gui.ExplorerDataset.LinkedText;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.validation.DataLocation;
import guru.interlis.validation.UncheckedRuleInfo;
import guru.interlis.validation.ValidationCoverage;
import guru.interlis.validation.ValidationReport;
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
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

final class ExplorerFrame extends JFrame {
    private static final String LAST_TRANSFER_DIRECTORY = "lastTransferDirectory";
    private static final String SELECT_OBJECT_TYPE = "Select a class or association";

    private record Visit(long objectPosition, TreePath typePath, List<Integer> rowPath) {}

    private record OpenedDataset(ExplorerDataset dataset, ExplorerQueries queries, ExplorerQueries.SqlCatalog catalog,
                                 Set<SymbolRef> basketTopics) {}

    private final JButton back = new JButton("Back");
    private final ArrayDeque<Visit> history = new ArrayDeque<>();
    private final JButton open = new JButton("Open dataset…");
    private final JLabel status = new JLabel("Choose the local model files and an XTF 2.4 FULL transfer.");
    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final JCheckBox showAllModels = new JCheckBox("Show all models");
    private final JLabel classTitle = new JLabel(SELECT_OBJECT_TYPE);
    private final JLabel count = new JLabel(" ");
    private final JList<StoredObject> objectList = new JList<>();
    private final DefaultListCellRenderer objectRenderer = new DefaultListCellRenderer();
    private final ReferenceCell objectLinks = new ReferenceCell();
    private final ReferenceCell inspectorLinks = new ReferenceCell();
    private final JPopupMenu objectMenu = new JPopupMenu();
    private final JPopupMenu inspectorMenu = new JPopupMenu();
    private final JLabel objectTitle = new JLabel("Select an object");
    private final AbstractAction validateObject = new AbstractAction("Validate object") {
        @Override
        public void actionPerformed(ActionEvent event) {
            validateSelectedObject();
        }
    };
    private final JButton validateButton = new JButton(validateObject);
    private final InspectorTableModel attributes = new InspectorTableModel();
    private final JTable inspector = inspectorTable();
    private final JTabbedPane tabs = new JTabbedPane();
    private final QueryPanel queryPanel = new QueryPanel(this::openQueryOccurrence);

    private ExplorerDataset dataset;
    private Set<SymbolRef> basketTopics = Set.of();
    private Map<Long, LinkedText> associationLabels = Map.of();
    private AtomicBoolean validationCancelled;
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
                cancelValidation();
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
            private final Icon associationIcon = new GlyphIcon("A");

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
                        yield type.kind() == SymbolKind.ASSOCIATION ? associationIcon : classIcon;
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
        objectList.setCellRenderer((list, object, index, selected, focused) -> renderObject(object, index, selected, focused));
        ToolTipManager.sharedInstance().registerComponent(objectList);
        configureObjectLinks();
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
        var inspection = section(objectTitle, inspector, validateButton);
        updateValidationAction();
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
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "back");
        getRootPane().getActionMap().put("back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                goBack();
            }
        });
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
        var valueRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                            boolean focused, int row, int column) {
                var target = navigationTarget(row);
                String text = target.isPresent() ? "→ " + value : value.toString();
                super.getTableCellRendererComponent(table, text, selected, focused, row, column);
                setToolTipText(target.isPresent() ? "Open the target or press Enter" : null);
                var links = target.isPresent() ? List.of(new Link(0, text.length(), text, target.getAsLong()))
                        : List.<Link>of();
                return inspectorLinks.render(this, new LinkedText(text, links));
            }
        };
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
                if (!plainClick(event)) {
                    return;
                }
                var point = event.getPoint();
                int row = table.rowAtPoint(point);
                if (table.columnAtPoint(point) == 0) {
                    attributes.toggle(row);
                } else if (inspectorLinkAt(table, row, point)) {
                    followReference(row);
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                var point = event.getPoint();
                boolean overLink = inspectorLinkAt(table, table.rowAtPoint(point), point);
                table.setCursor(overLink ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
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
        configurePopup(table, this::showInspectorMenu);
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

    private JLabel renderObject(StoredObject object, int index, boolean selected, boolean focused) {
        var label = associationLabels.get(object.position());
        var text = label == null ? ExplorerDataset.objectLabel(object) : label.text();
        objectRenderer.getListCellRendererComponent(objectList, text, index, selected, focused);
        objectRenderer.setToolTipText(label == null ? null : text);
        return label == null ? objectRenderer : objectLinks.render(objectRenderer, label);
    }

    private void configureObjectLinks() {
        var mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (plainClick(event) && canNavigate()) {
                    var point = event.getPoint();
                    objectLinkAt(point).ifPresent(link -> {
                        var source = objectList.getModel().getElementAt(objectList.locationToIndex(point));
                        follow(source, link.targetPosition(), List.of());
                    });
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                var linked = canNavigate() && objectLinkAt(event.getPoint()).isPresent();
                objectList.setCursor(linked ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                objectList.setCursor(null);
            }
        };
        objectList.addMouseListener(mouse);
        objectList.addMouseMotionListener(mouse);
        configurePopup(objectList, this::showObjectMenu);
    }

    private static boolean plainClick(MouseEvent event) {
        return SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 1
                && !event.isPopupTrigger() && !event.isControlDown() && !event.isMetaDown()
                && !event.isAltDown() && !event.isShiftDown();
    }

    private Optional<Link> objectLinkAt(Point point) {
        int index = objectList.locationToIndex(point);
        if (index < 0) {
            return Optional.empty();
        }
        var bounds = objectList.getCellBounds(index, index);
        if (!bounds.contains(point)) {
            return Optional.empty();
        }
        var label = renderObject(objectList.getModel().getElementAt(index), index, objectList.isSelectedIndex(index),
                objectList.hasFocus() && objectList.getLeadSelectionIndex() == index);
        return label == objectLinks
                ? objectLinks.linkAt(new Point(point.x - bounds.x, point.y - bounds.y), bounds.height)
                : Optional.empty();
    }

    private static void configurePopup(JComponent view, Consumer<Point> showMenu) {
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                popup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                popup(event);
            }

            private void popup(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showMenu.accept(event.getPoint());
                    event.consume();
                }
            }
        });
        var keys = view.getInputMap(JComponent.WHEN_FOCUSED);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "showTargetMenu");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK), "showTargetMenu");
        view.getActionMap().put("showTargetMenu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                showMenu.accept(null);
            }
        });
    }

    private void showObjectMenu(Point point) {
        if (!canNavigate()) {
            return;
        }
        int index = point == null ? objectList.getSelectedIndex() : objectList.locationToIndex(point);
        if (index < 0) {
            return;
        }
        var bounds = objectList.getCellBounds(index, index);
        if (point != null && !bounds.contains(point)) {
            return;
        }
        objectList.requestFocusInWindow();
        objectList.setSelectedIndex(index);
        var object = objectList.getModel().getElementAt(index);
        objectMenu.removeAll();
        objectMenu.add(new JMenuItem(validateObject));
        var label = associationLabels.get(object.position());
        if (label != null && !label.links().isEmpty()) {
            var targets = new JMenu("Go to target");
            for (var link : label.links()) {
                var item = new JMenuItem(link.menuLabel());
                item.addActionListener(event -> follow(object, link.targetPosition(), List.of()));
                targets.add(item);
            }
            objectMenu.add(targets);
        }
        if (point == null) {
            objectList.ensureIndexIsVisible(index);
        }
        objectMenu.show(objectList, point == null ? bounds.x : point.x,
                point == null ? bounds.y + bounds.height : point.y);
    }

    private void showInspectorMenu(Point point) {
        int row = point == null ? inspector.getSelectedRow() : inspector.rowAtPoint(point);
        if (navigationTarget(row).isEmpty()) {
            return;
        }
        selectInspectorRow(row);
        inspectorMenu.removeAll();
        var item = new JMenuItem("Go to target");
        item.addActionListener(event -> followReference(row));
        inspectorMenu.add(item);
        var bounds = inspector.getCellRect(row, 1, true);
        inspectorMenu.show(inspector, point == null ? bounds.x : point.x,
                point == null ? bounds.y + bounds.height : point.y);
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
        cancelValidation();
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
        cancelValidation();
        open.setEnabled(false);
        updateValidationAction();
        back.setEnabled(false);
        objectMenu.setVisible(false);
        objectMenu.removeAll();
        inspectorMenu.setVisible(false);
        inspectorMenu.removeAll();
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
                        associationLabels = Map.of();
                        queryPanel.setDataset(result.queries(), result.catalog(), () -> release(previous));
                        history.clear();
                        back.setEnabled(false);
                        basketTopics = result.basketTopics();
                        showAllModels.setSelected(false);
                        showAllModels.setEnabled(true);
                        tree.setModel(new DefaultTreeModel(modelTree(opened.plan(), Optional.of(basketTopics),
                                opened.storedObjectTypes())));
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
                        updateValidationAction();
                        back.setEnabled(!history.isEmpty());
                    }
                }
            }
        }.execute();
    }

    static DefaultMutableTreeNode modelTree(TransferPlan plan, Optional<Set<SymbolRef>> visibleTopics,
                                           Set<QualifiedName> storedObjectTypes) {
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
                for (var typeRef : topic.objectTypes()) {
                    var type = plan.object(typeRef).orElseThrow();
                    if (type.kind() == SymbolKind.CLASS || type.kind() == SymbolKind.ASSOCIATION
                            && storedObjectTypes.contains(type.name().declared())) {
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
        var root = modelTree(dataset.plan(), topics, dataset.storedObjectTypes());
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
        associationLabels = Map.of();
        objectList.setListData(new StoredObject[0]);
        inspectObject();
        classTitle.setText(SELECT_OBJECT_TYPE);
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
        cancelValidation();
        updateValidationAction();
        if (closing || dataset == null) {
            return;
        }
        var selected = objectList.getSelectedValue();
        if (selected == null) {
            attributes.clear();
            objectTitle.setText("Select an object");
            return;
        }
        objectTitle.setText(ExplorerDataset.objectLabel(selected));
        try {
            var relationships = InspectorRelationships.read(dataset.plan(), dataset.store(), selected);
            attributes.show(dataset.plan(), selected.object().data(), relationships);
        } catch (SQLException | RuntimeException failure) {
            attributes.show(dataset.plan(), selected.object().data(), InspectorRelationships.EMPTY);
            showError("Read relationships", failure.getMessage());
        }
    }

    private void showObjects(ObjectDescriptor type, List<StoredObject> objects) throws SQLException {
        associationLabels = dataset.associationLabels(type, objects);
        classTitle.setText(type.name().declared().toString());
        objectList.setCursor(null);
        objectList.setListData(objects.toArray(StoredObject[]::new));
        String scope = objects.size() > ExplorerDataset.OBJECT_LIMIT ? "includes selected object"
                : "up to " + ExplorerDataset.OBJECT_LIMIT;
        count.setText(objects.isEmpty() ? "No objects of this type." : objects.size() + " shown (" + scope + ")");
    }

    private boolean inspectorLinkAt(JTable table, int row, Point point) {
        if (navigationTarget(row).isEmpty() || table.columnAtPoint(point) != 1) {
            return false;
        }
        var cell = table.getCellRect(row, 1, false);
        if (!cell.contains(point)) {
            return false;
        }
        var label = table.prepareRenderer(table.getCellRenderer(row, 1), row, 1);
        return label == inspectorLinks
                && inspectorLinks.linkAt(new Point(point.x - cell.x, point.y - cell.y), cell.height).isPresent();
    }

    private boolean canNavigate() {
        return dataset != null && open.isEnabled() && !closing;
    }

    private OptionalLong navigationTarget(int row) {
        var target = attributes.target(row);
        var source = objectList.getSelectedValue();
        if (!canNavigate() || source == null || target.isEmpty() || target.getAsLong() == source.position()) {
            return OptionalLong.empty();
        }
        return target;
    }

    private void followReference(int row) {
        navigationTarget(row).ifPresent(target -> follow(objectList.getSelectedValue(), target, attributes.pathTo(row)));
    }

    private void follow(StoredObject source, long target, List<Integer> rowPath) {
        if (!canNavigate() || source == null || source.position() == target) {
            return;
        }
        var visit = new Visit(source.position(), tree.getSelectionPath(), rowPath);
        if (openOccurrence(target)) {
            history.push(visit);
            back.setEnabled(true);
            objectList.requestFocusInWindow();
        }
    }

    private void goBack() {
        var visit = history.peek();
        if (!canNavigate() || tabs.getSelectedIndex() != 0 || visit == null
                || !openOccurrence(visit.objectPosition(), visit.typePath())) {
            return;
        }
        history.pop();
        back.setEnabled(!history.isEmpty());
        if (visit.rowPath().isEmpty()) {
            objectList.requestFocusInWindow();
        } else {
            selectInspectorRow(attributes.reveal(visit.rowPath()));
        }
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
        return openOccurrence(position, null);
    }

    private boolean openOccurrence(long position, TreePath previousPath) {
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
            var path = occurrencePath(type, previousPath);
            if (path == null && !showAllModels.isSelected()) {
                showAllModels.setSelected(true);
                refreshModelTree();
                path = occurrencePath(type, previousPath);
            }
            if (path == null) {
                showError("Open object", "The target type is unavailable in the model tree.");
                return false;
            }
            var objects = dataset.objectsIncluding(type, target);
            showObjects(type, objects);
            navigating = true;
            try {
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
            } finally {
                navigating = false;
            }
            objectList.setSelectedValue(target, true);
            return true;
        } catch (SQLException | RuntimeException failure) {
            showError("Open object", failure.getMessage());
        }
        return false;
    }

    private TreePath occurrencePath(ObjectDescriptor type, TreePath previousPath) {
        var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        if (previousPath != null) {
            var restored = findPath(root, previousPath);
            if (restored != null) {
                return restored;
            }
        }
        for (var nodes = root.depthFirstEnumeration(); nodes.hasMoreElements();) {
            var node = (DefaultMutableTreeNode) nodes.nextElement();
            if (type.equals(node.getUserObject())) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }

    private void cancelValidation() {
        if (validationCancelled != null) {
            validationCancelled.set(true);
        }
    }

    private void updateValidationAction() {
        boolean selected = objectList.getSelectedValue() != null;
        validateButton.setVisible(selected);
        validateObject.setEnabled(selected && canNavigate() && validationCancelled == null);
        validateButton.setText(validationCancelled != null && !validationCancelled.get()
                ? "Validating…" : "Validate object");
    }

    private void validateSelectedObject() {
        if (!validateObject.isEnabled()) {
            return;
        }
        var activeDataset = dataset;
        var selected = objectList.getSelectedValue();
        ExplorerDataset.ObjectValidation request;
        try {
            request = activeDataset.objectValidation(selected);
        } catch (SQLException | RuntimeException failure) {
            showError("Object validation failed", failure.getMessage());
            return;
        }
        var cancelled = new AtomicBoolean();
        validationCancelled = cancelled;
        updateValidationAction();
        new SwingWorker<ValidationReport, Void>() {
            @Override
            protected ValidationReport doInBackground() {
                return request.validate(cancelled::get);
            }

            @Override
            protected void done() {
                validationCancelled = null;
                updateValidationAction();
                if (cancelled.get() || closing || dataset != activeDataset || objectList.getSelectedValue() != selected) {
                    return;
                }
                try {
                    var plan = activeDataset.plan();
                    var type = plan.object(selected.object().type()).orElseThrow().name().declared();
                    var association = associationLabels.get(selected.position());
                    var title = association == null ? ExplorerDataset.objectLabel(selected) : association.text();
                    var label = title + " (" + type + ", basket " + request.basket().id().value() + ")";
                    showDetails("Object validation", validationText(plan, label, get()), 16,
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (ExecutionException failure) {
                    showError("Object validation failed", failure.getCause().getMessage());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    showError("Object validation failed", "Validation was interrupted.");
                }
            }
        }.execute();
    }

    private static String validationText(TransferPlan plan, String label, ValidationReport report) {
        var text = new StringBuilder(label).append("\n\n");
        long violations = report.detectedViolations();
        if (violations == 0) {
            text.append("No violations found in local checks\n");
        } else {
            text.append(violations).append(violations == 1 ? " violation\n" : " violations\n");
            for (var finding : report.examples()) {
                text.append('\n').append(dataPath(plan, finding.data())).append("  ").append(finding.message());
                if (finding.data().path().isEmpty()) {
                    text.append(" (").append(symbolName(plan, finding.rule().declaration())).append(')');
                }
            }
            long omitted = violations - report.examples().size();
            if (omitted > 0) {
                text.append("\n").append(omitted).append(" more violations omitted from the examples.");
            }
            text.append('\n');
        }
        text.append("\nLocal checks use the basket as context. Basket rules, reference targets, uniqueness across "
                + "objects and other dataset rules were not checked.\n");

        var coverage = report.coverage();
        var constraintGaps = new EnumMap<ValidationCoverage.ConstraintStatus, Long>(ValidationCoverage.ConstraintStatus.class);
        for (var rule : coverage.constraints()) {
            constraintGaps.merge(rule.status(), rule.occurrences(), Long::sum);
        }
        appendGap(text, "Incomplete context", coverage.incompleteContext(),
                constraintGaps.getOrDefault(ValidationCoverage.ConstraintStatus.INCOMPLETE_CONTEXT, 0L));
        appendGap(text, "Missing runtime context", coverage.missingRuntimeContext(),
                constraintGaps.getOrDefault(ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT, 0L));
        appendGap(text, "Unsupported values", coverage.unsupportedValues(),
                constraintGaps.getOrDefault(ValidationCoverage.ConstraintStatus.UNSUPPORTED_VALUE, 0L));
        appendGap(text, "Unsupported expressions", List.of(),
                constraintGaps.getOrDefault(ValidationCoverage.ConstraintStatus.UNSUPPORTED_EXPRESSION, 0L));
        if (coverage.notChecked().stream().anyMatch(rule -> rule.reason() == UncheckedRuleInfo.Reason.UNSUPPORTED_EXPRESSION)) {
            text.append("\nThe model plan also contains expressions outside the local validator's capabilities.\n");
        }
        return text.toString();
    }

    private static void appendGap(StringBuilder text, String reason, List<ValidationCoverage.RuleCount> rules, long constraints) {
        long skipped = rules.stream().mapToLong(ValidationCoverage.RuleCount::occurrences).sum() + constraints;
        if (skipped > 0) {
            text.append('\n').append(reason).append(" prevented ").append(skipped)
                    .append(skipped == 1 ? " check.\n" : " checks.\n");
        }
    }

    private static String dataPath(TransferPlan plan, DataLocation location) {
        var path = new StringBuilder();
        for (var step : location.path()) {
            switch (step) {
                case DataLocation.Slot slot -> path.append('/').append(symbolName(plan, slot.symbol()));
                case DataLocation.Index index -> path.append('[').append(index.position()).append(']');
                case DataLocation.GeometryComponent component -> path.append('/')
                        .append(component.kind().name().toLowerCase(Locale.ROOT))
                        .append('[').append(component.position()).append(']');
            }
        }
        return path.isEmpty() ? "Object" : path.substring(1);
    }

    private static String symbolName(TransferPlan plan, SymbolRef ref) {
        return plan.symbol(ref).known().map(symbol -> symbol.name().value()).orElseGet(() -> ref.symbol().value());
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
        showDetails(title, message, 5, JOptionPane.ERROR_MESSAGE);
    }

    private void showDetails(String title, String message, int rows, int messageType) {
        var details = new JTextArea(message, rows, 52);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(details), title, messageType);
    }
}
