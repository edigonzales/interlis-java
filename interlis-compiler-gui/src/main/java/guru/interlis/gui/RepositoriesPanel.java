package guru.interlis.gui;

import guru.interlis.diagnostics.DiagnosticMessageFormatter;
import guru.interlis.gui.application.ConfiguredRepository;
import guru.interlis.gui.application.RepositoryIndexSnapshot;
import guru.interlis.gui.application.RepositoryCatalogService;
import guru.interlis.gui.application.RootChoice;
import guru.interlis.gui.controller.RepositoryBrowserController;
import guru.interlis.gui.controller.SessionController;
import guru.interlis.gui.presentation.SourcePreviewModel;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositorySearchStep;
import guru.interlis.repository.RepositorySource;
import guru.interlis.source.SourceId;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class RepositoriesPanel extends JPanel implements AutoCloseable {
    private static final String ALL_LANGUAGES = "All languages";
    private final SessionController session;
    private final RepositoryBrowserController browser;
    private final JList<String> repositories = new JList<>();
    private final CatalogTable model = new CatalogTable();
    private final JTable table = new JTable(model);
    private final TableRowSorter<CatalogTable> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField(14);
    private final JComboBox<String> language = new JComboBox<>();
    private final JCheckBox browseOnly = new JCheckBox("Include browse-only", true);
    private final JLabel counts = new JLabel();
    private final JLabel notice = new JLabel();
    private final JButton add = new JButton("Add repository…");
    private final JButton remove = new JButton("Remove");
    private final JButton up = new JButton("↑");
    private final JButton down = new JButton("↓");
    private final JButton toggle = new JButton("Enable / disable");
    private final JButton load = new JButton("Reload");
    private final JButton cancelLoad = new JButton("Cancel load");
    private final JButton preview = new JButton("Preview source");
    private final JButton cancelPreview = new JButton("Cancel preview");
    private final JButton addRoot = new JButton("Add exact root");
    private final JButton compare = new JButton("Compare metadata");
    private final JTextArea metadata = new JTextArea();
    private final SourcePreviewPanel source = new SourcePreviewPanel();
    private final JTable comparison = new JTable();
    private final JTabbedPane inspector = new JTabbedPane();
    private List<ConfiguredRepository> configured = List.of();
    private RepositoryBrowserController.View browserView;
    private boolean sessionEditable;
    private boolean refreshing;
    private Optional<guru.interlis.gui.application.CompileResult.SourceSnapshot> visiblePreview = Optional.empty();
    private String localNotice = "";

    RepositoriesPanel(SessionController session, Path directory) {
        super(new BorderLayout());
        this.session = session;
        browser = new RepositoryBrowserController(directory, new RepositoryCatalogService(), this::renderBrowser);
        browserView = browser.view();
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        repositories.setName("repository-list");
        repositories.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        repositories.setCellRenderer(new PlainListRenderer());
        repositories.addListSelectionListener(event -> {
            if (!refreshing && !event.getValueIsAdjusting()) {
                localNotice = "";
                refreshCatalog();
            }
        });
        var list = new JPanel(new BorderLayout());
        list.setBorder(BorderFactory.createTitledBorder("Repositories"));
        list.add(new JScrollPane(repositories), BorderLayout.CENTER);
        add.setName("add-repository");
        add.addActionListener(event -> {
            String input = JOptionPane.showInputDialog(this, "Local directory or HTTP(S) repository base",
                    "Add repository", JOptionPane.PLAIN_MESSAGE);
            if (input != null) {
                addLocation(input);
            }
        });
        remove.setName("remove-repository");
        remove.addActionListener(event -> editRepository(index -> {
            var updated = new ArrayList<>(configured);
            updated.remove(index);
            return updated;
        }));
        up.setName("repository-up");
        down.setName("repository-down");
        up.addActionListener(event -> move(-1));
        down.addActionListener(event -> move(1));
        toggle.setName("repository-toggle");
        toggle.addActionListener(event -> editRepository(index -> {
            var updated = new ArrayList<>(configured);
            var previous = updated.get(index);
            updated.set(index, new ConfiguredRepository(previous.source(), !previous.enabled()));
            return updated;
        }));
        var configuration = new JPanel(new BorderLayout());
        configuration.add(buttons(add, remove), BorderLayout.NORTH);
        configuration.add(buttons(up, down, toggle), BorderLayout.SOUTH);
        list.add(configuration, BorderLayout.SOUTH);

        table.setName("repository-catalog");
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultRenderer(Object.class, new PlainTableRenderer());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!refreshing && !event.getValueIsAdjusting()) {
                updateSelection();
            }
        });
        search.setName("repository-search");
        search.getAccessibleContext().setAccessibleName("Search repository models");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { applyFilter(); }
        });
        language.setName("repository-language");
        language.addItem(ALL_LANGUAGES);
        language.addActionListener(event -> applyFilter());
        browseOnly.setName("repository-include-browse-only");
        browseOnly.addActionListener(event -> applyFilter());
        load.setName("reload-repositories");
        load.addActionListener(event -> {
            localNotice = "";
            browser.load(scope());
        });
        cancelLoad.setName("cancel-repository-load");
        cancelLoad.addActionListener(event -> browser.cancelLoad());
        counts.setName("repository-counts");
        notice.setName("repository-notice");
        notice.putClientProperty("html.disable", true);
        var searchControls = new JPanel(new BorderLayout());
        searchControls.add(buttons(new JLabel("Search model"), search, language), BorderLayout.NORTH);
        searchControls.add(buttons(browseOnly, load, cancelLoad), BorderLayout.SOUTH);
        var catalog = new JPanel(new BorderLayout());
        catalog.setBorder(BorderFactory.createTitledBorder("Model catalog"));
        catalog.add(searchControls, BorderLayout.NORTH);
        catalog.add(new JScrollPane(table), BorderLayout.CENTER);
        catalog.add(counts, BorderLayout.SOUTH);

        metadata.setName("repository-metadata");
        metadata.setEditable(false);
        metadata.setLineWrap(true);
        metadata.setWrapStyleWord(true);
        comparison.setName("repository-comparison");
        comparison.setDefaultRenderer(Object.class, new PlainTableRenderer());
        inspector.setName("repository-inspector");
        inspector.addTab("Metadata", new JScrollPane(metadata));
        inspector.addTab("Source preview", source);
        inspector.addTab("Comparison", new JScrollPane(comparison));
        preview.setName("repository-preview");
        preview.addActionListener(event -> selectedEntries().stream().findFirst().ifPresent(entry -> {
            localNotice = "";
            inspector.setSelectedIndex(1);
            browser.preview(entry);
        }));
        cancelPreview.setName("cancel-repository-preview");
        cancelPreview.addActionListener(event -> browser.cancelPreview());
        addRoot.setName("repository-add-exact-root");
        addRoot.addActionListener(event -> selectedEntries().stream().findFirst().ifPresent(this::addRoot));
        compare.setName("repository-compare");
        compare.addActionListener(event -> compareSelected());
        var commands = new JPanel(new BorderLayout());
        commands.add(buttons(preview, cancelPreview), BorderLayout.NORTH);
        commands.add(buttons(addRoot, compare), BorderLayout.SOUTH);
        var right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Selected entries"));
        right.add(inspector, BorderLayout.CENTER);
        right.add(commands, BorderLayout.SOUTH);

        var main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catalog, right);
        main.setResizeWeight(.60);
        main.setDividerLocation(550);
        var layout = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, list, main);
        layout.setDividerLocation(235);
        layout.setResizeWeight(.18);
        add(layout, BorderLayout.CENTER);
        add(notice, BorderLayout.SOUTH);
        rebuildRepositoryList(Optional.empty());
        refreshCatalog();
    }

    void render(SessionController.View view) {
        sessionEditable = view.job() == SessionController.Job.IDLE;
        var preserve = selectedRepository().map(row -> row.source().base());
        boolean changed = !configured.equals(view.repositories());
        configured = view.repositories();
        if (changed) {
            rebuildRepositoryList(preserve);
        }
        browser.configure(configured, view.options());
        if (changed) {
            refreshCatalog();
        }
        updateActions();
    }

    void addLocation(String input) {
        localNotice = "";
        browser.validateLocation(input, repository -> {
            if (configured.stream().anyMatch(row -> row.source().base().equals(repository.base()))) {
                localNotice = "That repository is already configured.";
            } else {
                var updated = new ArrayList<>(configured);
                updated.add(new ConfiguredRepository(repository, true));
                if (!session.configureRepositories(updated)) {
                    localNotice = "Wait until compilation finishes to change repositories.";
                }
            }
            updateActions();
        });
    }

    private void move(int delta) {
        editRepository(index -> {
            var updated = new ArrayList<>(configured);
            java.util.Collections.swap(updated, index, index + delta);
            return updated;
        });
    }

    private void editRepository(java.util.function.IntFunction<List<ConfiguredRepository>> edit) {
        int index = repositories.getSelectedIndex() - 1;
        if (index >= 0 && sessionEditable) {
            localNotice = "";
            session.configureRepositories(edit.apply(index));
        }
    }

    private void rebuildRepositoryList(Optional<RepositoryBase> preserve) {
        refreshing = true;
        try {
            var list = new ArrayList<String>();
            list.add("All enabled");
            for (int i = 0; i < configured.size(); i++) {
                var repository = configured.get(i);
                var loaded = browserView.catalogs().get(repository.source().base());
                list.add((i + 1) + ". " + shortBase(repository.source().base()) + " · "
                        + (repository.enabled() ? state(loaded) : "Disabled · " + state(loaded)));
            }
            repositories.setListData(list.toArray(String[]::new));
            int selected = 0;
            if (preserve.isPresent()) {
                for (int i = 0; i < configured.size(); i++) {
                    if (preserve.orElseThrow().equals(configured.get(i).source().base())) {
                        selected = i + 1;
                        break;
                    }
                }
            }
            repositories.setSelectedIndex(selected);
        } finally {
            refreshing = false;
        }
    }

    private Optional<ConfiguredRepository> selectedRepository() {
        int index = repositories.getSelectedIndex() - 1;
        return index >= 0 && index < configured.size() ? Optional.of(configured.get(index)) : Optional.empty();
    }

    private List<RepositorySource> scope() {
        return selectedRepository().map(repository -> List.of(repository.source())).orElseGet(() ->
                configured.stream().filter(ConfiguredRepository::enabled).map(ConfiguredRepository::source).toList());
    }

    private void renderBrowser(RepositoryBrowserController.View view) {
        boolean catalogChanged = !browserView.catalogs().equals(view.catalogs());
        browserView = view;
        if (catalogChanged) {
            rebuildRepositoryList(selectedRepository().map(row -> row.source().base()));
            refreshCatalog();
        }
        if (!visiblePreview.equals(view.preview())) {
            visiblePreview = view.preview();
            if (view.preview().isPresent()) {
                SourcePreviewModel.from(view.preview().orElseThrow().source(), Optional.empty()).ifPresent(source::show);
            } else {
                source.clear("Select an entry and request its source.");
            }
        }
        if (view.previewLoading()) {
            source.clear("Loading selected source…");
        } else if (view.preview().isEmpty() && inspector.getSelectedIndex() == 1) {
            source.clear(view.notice().isEmpty() ? "Select an entry and request its source." : view.notice());
        }
        updateActions();
    }

    private void refreshCatalog() {
        if (refreshing) {
            return;
        }
        var selected = selectedEntries().stream().map(RepositoryIndexSnapshot.Entry::id).toList();
        var rows = scope().stream().map(repo -> browserView.catalogs().get(repo.base()))
                .filter(java.util.Objects::nonNull).flatMap(index -> index.entries().stream()).toList();
        if (!model.rows.equals(rows)) {
            refreshing = true;
            try {
                var wantedLanguage = language.getSelectedItem();
                table.clearSelection();
                model.rows = rows;
                model.fireTableDataChanged();
                language.removeAllItems();
                language.addItem(ALL_LANGUAGES);
                rows.stream().map(row -> row.metadata().schemaLanguage()).distinct().sorted().forEach(language::addItem);
                language.setSelectedItem(wantedLanguage);
                if (language.getSelectedIndex() < 0) {
                    language.setSelectedIndex(0);
                }
                setFilter();
                for (int i = 0; i < rows.size(); i++) {
                    int row = table.convertRowIndexToView(i);
                    if (row >= 0 && selected.contains(rows.get(i).id())) {
                        table.addRowSelectionInterval(row, row);
                    }
                }
            } finally {
                refreshing = false;
            }
        }
        updateSelection();
    }

    private void applyFilter() {
        if (!refreshing) {
            setFilter();
            updateSelection();
        }
    }

    private void setFilter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        Object schema = language.getSelectedItem();
        boolean include = browseOnly.isSelected();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends CatalogTable, ? extends Integer> entry) {
                var item = model.rows.get(entry.getIdentifier()).metadata();
                return (include || !item.browseOnly()) && (ALL_LANGUAGES.equals(schema) || item.schemaLanguage().equals(schema))
                        && item.name().toLowerCase(Locale.ROOT).contains(query);
            }
        });
    }

    private List<RepositoryIndexSnapshot.Entry> selectedEntries() {
        return Arrays.stream(table.getSelectedRows()).map(table::convertRowIndexToModel)
                .mapToObj(model.rows::get).toList();
    }

    private void updateSelection() {
        var selected = selectedEntries();
        browser.select(selected.size() == 1 ? Optional.of(selected.getFirst().id()) : Optional.empty());
        if (selected.size() == 1) {
            var fields = fields(selected.getFirst());
            var body = new StringBuilder();
            for (int i = 0; i < FIELDS.size(); i++) {
                body.append(FIELDS.get(i)).append(": ").append(fields.get(i)).append('\n');
            }
            metadata.setText(body.toString());
        } else {
            metadata.setText(selected.isEmpty() ? "Select a model entry." : "Select exactly two entries to compare their metadata.");
        }
        metadata.setCaretPosition(0);
        comparison.setModel(new CompareTable(List.of(), List.of()));
        if (inspector.getSelectedIndex() == 2) {
            inspector.setSelectedIndex(0);
        }
        updateActions();
    }

    private void compareSelected() {
        var selected = selectedEntries();
        if (selected.size() == 2) {
            comparison.setModel(new CompareTable(fields(selected.getFirst()), fields(selected.getLast())));
            inspector.setSelectedIndex(2);
        }
    }

    private void addRoot(RepositoryIndexSnapshot.Entry entry) {
        var item = entry.metadata();
        if (item.browseOnly()) {
            return;
        }
        var root = new RootChoice.Repository(item.name(), item.version(), new SourceId(item.location().toString()),
                entry.id().repository());
        localNotice = switch (session.addRepositoryRoot(root)) {
            case ADDED -> "Exact root added. Compile to check the session.";
            case ALREADY_SELECTED -> "This exact root is already selected.";
            case CONFLICT -> "A different root with that name is selected. Remove it in Check before adding this entry.";
            case BUSY -> "Wait until compilation finishes to add a root.";
        };
        updateActions();
    }

    private void updateActions() {
        boolean browserIdle = browserView.activity() == RepositoryBrowserController.Activity.IDLE;
        boolean edit = sessionEditable && browserIdle;
        int index = repositories.getSelectedIndex() - 1;
        add.setEnabled(edit);
        remove.setEnabled(edit && index >= 0);
        toggle.setEnabled(edit && index >= 0);
        up.setEnabled(edit && index > 0);
        down.setEnabled(edit && index >= 0 && index + 1 < configured.size());
        load.setEnabled(browserIdle && !scope().isEmpty());
        cancelLoad.setEnabled(browserView.activity() == RepositoryBrowserController.Activity.LOADING
                || browserView.activity() == RepositoryBrowserController.Activity.VALIDATING);
        var selected = selectedEntries();
        boolean one = selected.size() == 1;
        preview.setEnabled(one && !browserView.previewLoading());
        cancelPreview.setEnabled(browserView.previewLoading());
        compare.setEnabled(selected.size() == 2);
        boolean eligible = one && !selected.getFirst().metadata().browseOnly();
        boolean enabledRepo = one && configured.stream().anyMatch(row -> row.enabled()
                && row.source().base().equals(selected.getFirst().id().repository()));
        addRoot.setEnabled(sessionEditable && eligible && enabledRepo);
        addRoot.setToolTipText(!one ? null : !eligible ? "Browse-only entries are excluded from automatic model resolution."
                : !enabledRepo ? "Enable this repository before selecting its model as a root." : null);
        var scope = scope();
        long loaded = scope.stream().map(repo -> browserView.catalogs().get(repo.base())).filter(java.util.Objects::nonNull)
                .filter(indexResult -> indexResult.state() == RepositoryIndexSnapshot.State.LOADED
                        || indexResult.state() == RepositoryIndexSnapshot.State.EMPTY).count();
        counts.setText(table.getRowCount() + " of " + model.rows.size() + " entries · " + loaded + "/" + scope.size() + " indexes loaded");
        String activity = switch (browserView.activity()) {
            case IDLE -> "";
            case VALIDATING -> "Validating repository…";
            case LOADING -> "Loading indexes…";
            case CANCELLING -> "Cancelling repository operation…";
        };
        String selectedProblem = selectedRepository().map(row -> browserView.catalogs().get(row.source().base()))
                .flatMap(RepositoryIndexSnapshot::problem).map(RepositoriesPanel::plain).orElse("");
        String message = !localNotice.isEmpty() ? localNotice : !activity.isEmpty() ? activity
                : !selectedProblem.isEmpty() ? selectedProblem : browserView.notice();
        notice.setText(message.isEmpty() && configured.isEmpty() ? "Add a local directory or HTTP(S) repository to browse models." : message);
    }

    private static final List<String> FIELDS = List.of("Name", "Version", "Language", "Repository", "Source",
            "Precursor version", "Browse only", "MD5");

    private static List<String> fields(RepositoryIndexSnapshot.Entry entry) {
        var model = entry.metadata();
        return List.of(plain(model.name()), plain(model.version()), plain(model.schemaLanguage()), baseLabel(entry.id().repository()),
                plain(RepositorySearchStep.safeUri(model.location().toString())), model.precursorVersion().map(RepositoriesPanel::plain)
                        .orElse("Not provided"), model.browseOnly() ? "Yes" : "No",
                model.md5().map(RepositoriesPanel::plain).orElse("Not provided"));
    }

    private static String state(RepositoryIndexSnapshot catalog) {
        if (catalog == null) {
            return "Not loaded";
        }
        String label = switch (catalog.state()) {
            case LOADED -> "Loaded";
            case EMPTY -> "Empty";
            case UNAVAILABLE -> "Unavailable";
            case OFFLINE_UNAVAILABLE -> "Offline unavailable";
            case INVALID -> "Invalid index";
        };
        return catalog.provenance().filter("verified-cache"::equals).isPresent() ? label + " (verified cache)" : label;
    }

    private static String shortBase(RepositoryBase base) {
        var uri = base.uri();
        if (!"file".equals(uri.getScheme())) {
            return plain(uri.getHost() + (uri.getPath().equals("/") ? "" : uri.getPath()));
        }
        String path = uri.getPath();
        String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int separator = trimmed.lastIndexOf('/');
        return plain(separator >= 0 && separator + 1 < trimmed.length() ? trimmed.substring(separator + 1) : trimmed);
    }

    private static String baseLabel(RepositoryBase base) {
        return plain(RepositorySearchStep.safeUri(base.uri().toString()));
    }

    private static String plain(String value) {
        return DiagnosticMessageFormatter.safeText(value);
    }

    private static JPanel buttons(java.awt.Component... items) {
        var row = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 1));
        for (var item : items) {
            row.add(item);
        }
        return row;
    }

    @Override
    public void close() {
        browser.close();
    }

    private static final class CatalogTable extends AbstractTableModel {
        private static final List<String> COLUMNS = List.of("Model", "Version", "Repository", "Status");
        private List<RepositoryIndexSnapshot.Entry> rows = List.of();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.size(); }
        @Override public String getColumnName(int column) { return COLUMNS.get(column); }

        @Override
        public Object getValueAt(int row, int column) {
            var entry = rows.get(row);
            return switch (column) {
                case 0 -> plain(entry.metadata().name());
                case 1 -> plain(entry.metadata().version());
                case 2 -> shortBase(entry.id().repository());
                case 3 -> entry.metadata().browseOnly() ? "Browse only" : "Indexed";
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private static final class CompareTable extends AbstractTableModel {
        private final List<String> left;
        private final List<String> right;

        private CompareTable(List<String> left, List<String> right) {
            this.left = left;
            this.right = right;
        }

        @Override public int getRowCount() { return left.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int column) { return List.of("Field", "Left entry", "Right entry").get(column); }

        @Override
        public Object getValueAt(int row, int column) {
            return switch (column) {
                case 0 -> FIELDS.get(row) + (left.get(row).equals(right.get(row)) ? "" : " · differs");
                case 1 -> left.get(row);
                case 2 -> right.get(row);
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private static final class PlainTableRenderer extends DefaultTableCellRenderer {
        private PlainTableRenderer() {
            putClientProperty("html.disable", true);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                               boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            String detail = value == null ? "" : value.toString();
            if (table.getModel() instanceof CatalogTable catalog && table.convertColumnIndexToModel(column) == 2) {
                detail = baseLabel(catalog.rows.get(table.convertRowIndexToModel(row)).id().repository());
            }
            setToolTipText(tooltip(detail));
            return this;
        }
    }

    private final class PlainListRenderer extends javax.swing.DefaultListCellRenderer {
        private PlainListRenderer() {
            putClientProperty("html.disable", true);
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            setToolTipText(index > 0 && index <= configured.size()
                    ? tooltip(baseLabel(configured.get(index - 1).source().base())) : null);
            return this;
        }
    }

    private static String tooltip(String value) {
        return "<html>" + value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</html>";
    }
}
