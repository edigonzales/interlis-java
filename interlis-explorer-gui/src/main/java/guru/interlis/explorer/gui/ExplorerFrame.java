package guru.interlis.explorer.gui;

import guru.interlis.explorer.DuckDbStore.StoredObject;
import guru.interlis.model.SymbolKind;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.xtf24.Xtf24Exception;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

final class ExplorerFrame extends JFrame {
    private final JButton open = new JButton("Open dataset…");
    private final JLabel status = new JLabel("Choose the local model files and an XTF 2.4 FULL transfer.");
    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final JLabel classTitle = new JLabel("Select a class");
    private final JLabel count = new JLabel(" ");
    private final JList<String> objectList = new JList<>();
    private final JLabel objectTitle = new JLabel("Select an object");
    private final DefaultTableModel attributes = new DefaultTableModel(new String[]{"Attribute", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private ExplorerDataset dataset;
    private List<StoredObject> objects = List.of();
    private volatile boolean closing;

    ExplorerFrame() {
        super("INTERLIS Object Explorer");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closing = true;
                if (open.isEnabled()) {
                    closeWindow();
                } else {
                    status.setText("Closing dataset…");
                }
            }
        });

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(26);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                          boolean expanded, boolean leaf, int row, boolean focused) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
                setIcon(null);
                var node = (DefaultMutableTreeNode) value;
                setText(switch (node.getUserObject()) {
                    case ModelDescriptor model -> model.name().declared().last();
                    case TopicDescriptor topic -> topic.name().declared().last();
                    case ObjectDescriptor type -> type.name().declared().last();
                    case null, default -> "";
                });
                return this;
            }
        });
        tree.addTreeSelectionListener(event -> selectClass());

        objectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        objectList.setFixedCellHeight(26);
        objectList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                inspectObject();
            }
        });

        var table = new JTable(attributes);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setCellSelectionEnabled(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(175);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        var plain = new DefaultTableCellRenderer();
        plain.putClientProperty("html.disable", true);
        table.setDefaultRenderer(Object.class, plain);

        var navigation = section(new JLabel("Model"), tree);
        var instances = section(classTitle, objectList);
        instances.add(count, BorderLayout.SOUTH);
        var inspector = section(objectTitle, table);
        var content = split(JSplitPane.HORIZONTAL_SPLIT, navigation,
                split(JSplitPane.VERTICAL_SPLIT, instances, inspector, 230, 0.35), 250, 0.25);

        var toolbar = new JPanel(new BorderLayout());
        toolbar.add(open, BorderLayout.WEST);
        open.addActionListener(event -> chooseDataset());
        status.putClientProperty("html.disable", true);
        var panel = new JPanel(new BorderLayout(0, 14));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        setContentPane(panel);
        setMinimumSize(new Dimension(640, 470));
        setSize(1000, 690);
        setLocationByPlatform(true);
    }

    private static JPanel section(JLabel title, JComponent view) {
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.putClientProperty("html.disable", true);
        var scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        panel.add(title, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JSplitPane split(int direction, JComponent first, JComponent second, int divider, double weight) {
        var pane = new JSplitPane(direction, first, second);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setDividerLocation(divider);
        pane.setResizeWeight(weight);
        return pane;
    }

    private void chooseDataset() {
        var modelChooser = new JFileChooser();
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
        status.setText("Compiling models and importing " + xtf.getFileName() + "…");
        new SwingWorker<ExplorerDataset, Void>() {
            @Override
            protected ExplorerDataset doInBackground() throws Exception {
                return ExplorerDataset.open(files, xtf, () -> closing);
            }

            @Override
            protected void done() {
                try {
                    var opened = get();
                    if (closing) {
                        release(opened);
                    } else {
                        var previous = dataset;
                        dataset = opened;
                        tree.setModel(new DefaultTreeModel(modelTree(opened.plan())));
                        selectClass();
                        status.setText("Opened " + xtf.getFileName());
                        release(previous);
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

    static DefaultMutableTreeNode modelTree(TransferPlan plan) {
        var root = new DefaultMutableTreeNode();
        for (var model : plan.models()) {
            var modelNode = new DefaultMutableTreeNode(model);
            root.add(modelNode);
            for (var topicRef : model.topics()) {
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
        }
        return root;
    }

    private void selectClass() {
        objects = List.of();
        objectList.setListData(new String[0]);
        inspectObject();
        classTitle.setText("Select a class");
        count.setText(" ");
        if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof ObjectDescriptor type) {
            classTitle.setText(type.name().declared().toString());
            try {
                objects = dataset.objects(type);
                objectList.setListData(objects.stream().map(ExplorerFrame::objectLabel).toArray(String[]::new));
                count.setText(objects.isEmpty() ? "No objects of this class."
                        : objects.size() + " shown (up to " + ExplorerDataset.OBJECT_LIMIT + ")");
            } catch (SQLException | RuntimeException failure) {
                count.setText("Object list could not be loaded.");
                showError("Read objects", failure.getMessage());
            }
        }
    }

    private void inspectObject() {
        attributes.setRowCount(0);
        objectTitle.setText("Select an object");
        int index = objectList.getSelectedIndex();
        if (index >= 0 && index < objects.size()) {
            var selected = objects.get(index);
            objectTitle.setText(objectLabel(selected));
            for (var attribute : dataset.attributes(selected.object())) {
                attributes.addRow(new String[]{attribute.getKey(), attribute.getValue()});
            }
        }
    }

    private static String objectLabel(StoredObject entry) {
        var identity = entry.object().identity();
        if (identity.tid().isPresent()) {
            return "TID " + identity.tid().orElseThrow().value();
        }
        return identity.oid().map(oid -> "OID " + oid.value()).orElse("Store position " + entry.position());
    }

    private void closeWindow() {
        release(dataset);
        dataset = null;
        dispose();
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
