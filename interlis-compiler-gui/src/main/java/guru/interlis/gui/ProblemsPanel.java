package guru.interlis.gui;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.Severity;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.presentation.DiagnosticPresenter;
import guru.interlis.gui.presentation.SourcePreviewModel;
import guru.interlis.source.SourcePosition;
import guru.interlis.source.SourceRange;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class ProblemsPanel extends JPanel {
    private static final String ALL = "All severities";
    private final ProblemsTableModel tableModel = new ProblemsTableModel();
    private final JTable table = new JTable(tableModel);
    private final JComboBox<Object> severity = new JComboBox<>();
    private final JTextField search = new JTextField(13);
    private final JCheckBox group = new JCheckBox("Group related");
    private final JLabel counts = new JLabel();
    private final JLabel dependencyFilter = new JLabel();
    private final JTextArea details = new JTextArea(7, 20);
    private final JComboBox<DiagnosticPresenter.Related> locations = new JComboBox<>();
    private final JComboBox<Reference> references = new JComboBox<>();
    private final JButton openLocation = new JButton("Show location");
    private final JButton openReference = new JButton("Go to diagnostic");
    private final JButton copyMessage = new JButton("Copy message");
    private final JButton copyDetails = new JButton("Copy details");
    private final JButton clearFilters = new JButton("Clear filters");
    private final JLabel navigationNotice = new JLabel();
    private final SourcePreviewPanel preview = new SourcePreviewPanel();
    private DiagnosticPresenter presenter;
    private DiagnosticPresenter.Details selection;
    private DiagnosticLanguage language;
    private CompileResult result;
    private boolean refreshing;
    private Optional<SourceRange> scope = Optional.empty();

    private record Reference(String label, DiagnosticId id) {
        @Override
        public String toString() {
            return label + " · " + id.value();
        }
    }

    ProblemsPanel(Consumer<String> copy) {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Problems"));
        table.setName("problems");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new PlainTableRenderer());
        var sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(4, java.util.Comparator.comparingInt(SourcePosition::line)
                .thenComparingInt(SourcePosition::character));
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!refreshing && !event.getValueIsAdjusting()) {
                showSelection();
            }
        });

        var filters = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        severity.setName("diagnostic-severity");
        severity.addItem(ALL);
        for (Severity level : Severity.values()) {
            severity.addItem(level);
        }
        severity.addActionListener(event -> refresh(selectedId()));
        search.setName("diagnostic-search");
        search.getAccessibleContext().setAccessibleName("Search diagnostics");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refresh(selectedId());
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refresh(selectedId());
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refresh(selectedId());
            }
        });
        group.setName("group-diagnostics");
        group.addActionListener(event -> refresh(selectedId()));
        clearFilters.setName("reset-diagnostic-filter");
        clearFilters.addActionListener(event -> resetFilters());
        counts.setName("diagnostic-counts");
        filters.add(severity);
        filters.add(new JLabel("Search"));
        filters.add(search);
        filters.add(group);
        var controls = new JPanel(new BorderLayout());
        controls.add(filters, BorderLayout.NORTH);
        dependencyFilter.setName("dependency-problem-filter");
        dependencyFilter.putClientProperty("html.disable", true);
        controls.add(buttons(clearFilters, counts, dependencyFilter), BorderLayout.SOUTH);

        details.setName("diagnostic-details");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        var detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBorder(BorderFactory.createTitledBorder("Details"));
        detailPanel.setMinimumSize(new java.awt.Dimension(245, 100));
        detailPanel.add(new JScrollPane(details), BorderLayout.CENTER);
        var commands = new JPanel();
        commands.setLayout(new javax.swing.BoxLayout(commands, javax.swing.BoxLayout.Y_AXIS));
        locations.setName("related-locations");
        locations.setRenderer(new PlainListRenderer());
        locations.setPreferredSize(new java.awt.Dimension(150, locations.getPreferredSize().height));
        references.setName("diagnostic-references");
        references.setRenderer(new PlainListRenderer());
        references.setPreferredSize(new java.awt.Dimension(150, references.getPreferredSize().height));
        locations.addActionListener(event -> updateActions());
        references.addActionListener(event -> updateActions());
        openLocation.setName("open-related-location");
        openLocation.addActionListener(event -> {
            var related = (DiagnosticPresenter.Related) locations.getSelectedItem();
            if (related != null) {
                showLocation(Optional.of(related.range()));
            }
        });
        openReference.setName("open-diagnostic-reference");
        openReference.addActionListener(event -> {
            var reference = (Reference) references.getSelectedItem();
            if (reference != null) {
                navigate(reference.id());
            }
        });
        copyMessage.setName("copy-diagnostic-message");
        copyMessage.addActionListener(event -> copy(copy, selection.message().text()));
        copyDetails.setName("copy-diagnostic-details");
        copyDetails.addActionListener(event -> copy(copy, selection.report()));
        commands.add(buttons(locations, openLocation));
        commands.add(buttons(references, openReference));
        commands.add(buttons(copyMessage, copyDetails));
        navigationNotice.setName("diagnostic-navigation-notice");
        navigationNotice.putClientProperty("html.disable", true);
        commands.add(navigationNotice);
        detailPanel.add(commands, BorderLayout.SOUTH);

        var inspector = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, detailPanel, preview);
        inspector.setResizeWeight(.43);
        var content = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), inspector);
        content.setResizeWeight(.45);
        add(controls, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        refresh(Optional.empty());
    }

    void render(CompileResult next, DiagnosticLanguage nextLanguage) {
        if (result == next && language == nextLanguage) {
            return;
        }
        var preserve = result == next ? selectedId() : Optional.<DiagnosticId>empty();
        if (result != next) {
            scope = Optional.empty();
            dependencyFilter.setText("");
        }
        result = next;
        language = nextLanguage;
        presenter = next == null ? null : new DiagnosticPresenter(next, nextLanguage);
        refresh(preserve);
    }

    private Optional<DiagnosticId> selectedId() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(tableModel.idAt(table.convertRowIndexToModel(row)));
    }

    private void refresh(Optional<DiagnosticId> preserve) {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            var selected = severity.getSelectedItem();
            var levels = selected instanceof Severity level ? EnumSet.of(level) : EnumSet.allOf(Severity.class);
            var projection = presenter == null ? new DiagnosticPresenter.Projection(List.of(), 0, 0)
                    : presenter.present(levels, search.getText(), group.isSelected(), scope);
            table.clearSelection();
            tableModel.show(projection.visible());
            counts.setText(projection.visible().size() + " of " + projection.total() + " visible"
                    + (projection.grouped() > 0 ? " · " + projection.grouped() + " grouped" : ""));
            preserve.ifPresent(this::selectVisible);
        } finally {
            refreshing = false;
        }
        showSelection();
    }

    void showRelated(SourceRange range) {
        resetFilters();
        scope = Optional.of(range);
        dependencyFilter.setText("Dependency source: " + DiagnosticPresenter.shortSource(range.source()));
        refresh(Optional.empty());
    }

    private void resetFilters() {
        var preserve = selectedId();
        refreshing = true;
        try {
            severity.setSelectedItem(ALL);
            search.setText("");
            group.setSelected(false);
            scope = Optional.empty();
            dependencyFilter.setText("");
        } finally {
            refreshing = false;
        }
        refresh(preserve);
    }

    private void navigate(DiagnosticId id) {
        if (!selectVisible(id)) {
            if (presenter.details(id).isPresent()) {
                resetFilters();
                selectVisible(id);
            } else {
                navigationNotice.setText("That diagnostic is unavailable in this run.");
            }
        }
    }

    private boolean selectVisible(DiagnosticId id) {
        int row = tableModel.rowOf(id);
        if (row < 0) {
            return false;
        }
        int viewRow = table.convertRowIndexToView(row);
        table.setRowSelectionInterval(viewRow, viewRow);
        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
        return true;
    }

    private void showSelection() {
        navigationNotice.setText("");
        selection = presenter == null ? null : selectedId().flatMap(presenter::details).orElse(null);
        locations.removeAllItems();
        references.removeAllItems();
        if (selection == null) {
            details.setText("Select a diagnostic to see its details.");
            preview.clear("No source location selected.");
        } else {
            details.setText(selection.report());
            details.setCaretPosition(0);
            selection.related().forEach(locations::addItem);
            var diagnostic = selection.diagnostic();
            diagnostic.causeId().ifPresent(id -> references.addItem(new Reference("Cause", id)));
            diagnostic.dependsOn().stream().filter(id -> !diagnostic.causeId().filter(id::equals).isPresent())
                    .forEach(id -> references.addItem(new Reference("Depends on", id)));
            presenter.dependents(diagnostic.id()).forEach(id -> references.addItem(new Reference("Dependent", id)));
            showLocation(diagnostic.primaryRange());
        }
        updateActions();
    }

    private void showLocation(Optional<SourceRange> range) {
        var source = range.flatMap(presenter::source);
        var model = source.flatMap(text -> SourcePreviewModel.from(text, range));
        if (model.isPresent()) {
            preview.show(model.orElseThrow());
        } else {
            preview.clear(range.isPresent() ? "The matching source revision is unavailable." : "No source location is available.");
        }
    }

    private void copy(Consumer<String> sink, String value) {
        try {
            sink.accept(value);
        } catch (IllegalStateException failure) {
            navigationNotice.setText("The clipboard is unavailable. Try again.");
        }
    }

    private void updateActions() {
        openLocation.setEnabled(locations.getSelectedItem() != null);
        openReference.setEnabled(references.getSelectedItem() != null);
        copyMessage.setEnabled(selection != null);
        copyDetails.setEnabled(selection != null);
    }

    private static JPanel buttons(java.awt.Component... components) {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 1));
        for (var component : components) {
            panel.add(component);
        }
        return panel;
    }

    private static final class PlainTableRenderer extends DefaultTableCellRenderer {
        private PlainTableRenderer() {
            putClientProperty("html.disable", true);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                               boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            int modelColumn = table.convertColumnIndexToModel(column);
            var model = (ProblemsTableModel) table.getModel();
            String tooltip = modelColumn == 3 ? model.sourceDetailAt(table.convertRowIndexToModel(row)) : null;
            setToolTipText(tooltip);
            return this;
        }

        @Override
        protected void setValue(Object value) {
            super.setValue(value instanceof SourcePosition position ? DiagnosticPresenter.displayPosition(position) : value);
        }
    }

    private static final class PlainListRenderer extends javax.swing.DefaultListCellRenderer {
        private PlainListRenderer() {
            putClientProperty("html.disable", true);
        }
    }
}
