package guru.interlis.explorer.gui;

import guru.interlis.explorer.ExplorerQueries;
import guru.interlis.explorer.ExplorerQueries.Mode;
import guru.interlis.explorer.ExplorerQueries.Occurrence;
import guru.interlis.explorer.ExplorerQueries.SqlCatalog;
import guru.interlis.explorer.ExplorerQueries.Suggestion;
import guru.interlis.explorer.Iql;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

final class QueryPanel extends JPanel {
    private static final SqlCatalog EMPTY_CATALOG = new SqlCatalog(Map.of(), List.of());

    private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
    private final RSyntaxTextArea editor = new RSyntaxTextArea(8, 60);
    private final JButton run = new JButton("Run");
    private final JButton cancel = new JButton("Cancel");
    private final Action openInInspector;
    private final JLabel status = new JLabel("Open a dataset to run a query.");
    private final ResultTable results = new ResultTable();
    private final EnumMap<Mode, String> drafts = new EnumMap<>(Mode.class);
    private final QueryAutoCompletion completion = new QueryAutoCompletion(this::suggestions);

    private Mode currentMode = Mode.IQL;
    private ExplorerQueries queries;
    private ExplorerQueries resultSource;
    private SqlCatalog catalog = EMPTY_CATALOG;
    private QueryWork active;

    QueryPanel(Consumer<Occurrence> inspectorNavigation) {
        super(new BorderLayout(0, 8));
        Objects.requireNonNull(inspectorNavigation);
        mode.setName("queryMode");
        editor.setName("queryEditor");
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        editor.setTabsEmulated(true);
        editor.setTabSize(4);
        editor.setHyperlinksEnabled(false);
        editor.setHighlightCurrentLine(false);
        editor.getAccessibleContext().setAccessibleName("Query editor");
        completion.setAutoActivationEnabled(true);
        completion.setAutoActivationDelay(250);
        completion.setAutoCompleteSingleChoices(false);
        int menuShortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        if ((menuShortcut & InputEvent.META_DOWN_MASK) != 0) {
            completion.setTriggerKey(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, menuShortcut | InputEvent.SHIFT_DOWN_MASK));
        }
        completion.install(editor);

        run.setName("runQuery");
        cancel.setName("cancelQuery");
        run.addActionListener(event -> runQuery());
        cancel.addActionListener(event -> cancelQuery());
        mode.addActionListener(event -> switchMode());
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuShortcut), "runQuery");
        editor.getActionMap().put("runQuery", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                runQuery();
            }
        });
        openInInspector = new AbstractAction("Open in Inspector") {
            @Override
            public void actionPerformed(ActionEvent event) {
                inspectorDestination().ifPresent(inspectorNavigation);
            }
        };
        var inspect = new JButton(openInInspector);
        inspect.setName("openQueryOccurrence");
        results.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openInInspector");
        results.getActionMap().put("openInInspector", openInInspector);
        results.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateInspectorAction();
        });

        status.setName("queryStatus");
        status.putClientProperty("html.disable", true);
        var actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        actions.add(mode);
        actions.add(run);
        actions.add(cancel);
        actions.add(inspect);
        var body = ExplorerFrame.split(javax.swing.JSplitPane.VERTICAL_SPLIT, new RTextScrollPane(editor),
                new JScrollPane(results), 200, 0.4);
        body.setContinuousLayout(true);
        add(actions, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setReady();
    }

    void setDataset(ExplorerQueries replacement, SqlCatalog replacementCatalog, Runnable releasePrevious) {
        completion.hideChildWindows();
        var previous = active;
        active = null;
        queries = replacement;
        resultSource = null;
        catalog = replacementCatalog == null ? EMPTY_CATALOG : replacementCatalog;
        results.showResult(new ExplorerQueries.Result(List.of(), List.of(), false));
        status.setToolTipText(null);
        status.setText(queries == null ? "Open a dataset to run a query." : " ");
        setReady();
        if (previous == null) {
            releasePrevious.run();
        } else {
            previous.release = releasePrevious;
            previous.requestCancellation();
        }
    }

    private void switchMode() {
        completion.hideChildWindows();
        drafts.put(currentMode, editor.getText());
        currentMode = (Mode) mode.getSelectedItem();
        editor.setText(drafts.getOrDefault(currentMode, ""));
        editor.setCaretPosition(0);
        resultSource = null;
        results.showResult(new ExplorerQueries.Result(List.of(), List.of(), false));
        status.setToolTipText(null);
        status.setText(queries == null ? "Open a dataset to run a query." : " ");
        updateInspectorAction();
    }

    private void setReady() {
        run.setEnabled(queries != null && active == null);
        mode.setEnabled(active == null);
        cancel.setEnabled(active != null);
        updateInspectorAction();
    }

    private void updateInspectorAction() {
        openInInspector.setEnabled(inspectorDestination().isPresent());
    }

    private Optional<Occurrence> inspectorDestination() {
        if (queries == null || resultSource != queries || active != null || currentMode != Mode.IQL) {
            return Optional.empty();
        }
        return results.selectedOccurrence();
    }

    private void runQuery() {
        if (queries == null || active != null) {
            return;
        }
        completion.hideChildWindows();
        if (editor.getText().isBlank()) {
            status.setText("Enter a query.");
            editor.requestFocusInWindow();
            return;
        }
        active = new QueryWork(queries, currentMode, editor.getText());
        setReady();
        status.setToolTipText(null);
        status.setText("Running query…");
        active.execute();
    }

    void cancelQuery() {
        if (active != null) {
            cancel.setEnabled(false);
            status.setText("Cancelling query…");
            active.requestCancellation();
        }
    }

    private List<Suggestion> suggestions(String text, int caret) {
        if (queries == null || active != null) {
            return List.of();
        }
        return currentMode == Mode.IQL ? queries.completeIql(text, caret) : catalog.complete(text, caret);
    }

    private final class QueryWork extends SwingWorker<ExplorerQueries.Result, Void> {
        private final ExplorerQueries source;
        private final Mode language;
        private final String text;
        private final ExplorerQueries.Cancellation cancellation = new ExplorerQueries.Cancellation();
        private Runnable release;

        private QueryWork(ExplorerQueries source, Mode language, String text) {
            this.source = source;
            this.language = language;
            this.text = text;
        }

        private void requestCancellation() {
            cancellation.cancel();
        }

        @Override
        protected ExplorerQueries.Result doInBackground() throws Exception {
            return source.execute(language, text, cancellation);
        }

        @Override
        protected void done() {
            if (active != this) {
                if (release != null) {
                    release.run();
                }
                return;
            }
            active = null;
            setReady();
            try {
                var result = get();
                if (cancellation.requested()) {
                    status.setText("Query cancelled.");
                    return;
                }
                resultSource = source;
                results.showResult(result);
                updateInspectorAction();
                status.setText(result.rows().size() + (result.rows().size() == 1 ? " row" : " rows")
                        + (result.truncated() ? " shown. More rows are available." : "."));
            } catch (ExecutionException failure) {
                if (cancellation.requested()) {
                    status.setText("Query cancelled.");
                    return;
                }
                var cause = failure.getCause();
                status.setText(cause.getMessage());
                status.setToolTipText(cause.getMessage());
                if (cause instanceof Iql.QueryException problem && text.equals(editor.getText())) {
                    var length = editor.getDocument().getLength();
                    editor.requestFocusInWindow();
                    editor.select(Math.clamp(problem.start(), 0, length), Math.clamp(problem.end(), 0, length));
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                status.setText("Query interrupted.");
            }
        }
    }

    static final class ResultTable extends JTable {
        ResultTable() {
            setName("queryResults");
            setFillsViewportHeight(true);
            setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            if (getTableHeader().getDefaultRenderer() instanceof JComponent headerRenderer) {
                headerRenderer.putClientProperty("html.disable", true);
            }
            var renderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                boolean focus, int row, int column) {
                    super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                    String description = null;
                    if (table.getModel() instanceof ResultModel model) {
                        int modelRow = table.convertRowIndexToModel(row);
                        int modelColumn = table.convertColumnIndexToModel(column);
                        if (model.result.isNull(modelRow, modelColumn)) {
                            var font = getFont();
                            setFont(font.deriveFont(font.getStyle() | Font.ITALIC));
                            description = "SQL NULL";
                        } else {
                            description = model.result.geometry(modelRow, modelColumn)
                                    .flatMap(ExplorerQueries.GeometryCell::projectionError).orElse(null);
                        }
                    }
                    setToolTipText(description);
                    getAccessibleContext().setAccessibleName(description);
                    return this;
                }
            };
            renderer.putClientProperty("html.disable", true);
            setDefaultRenderer(Object.class, renderer);
        }

        void showResult(ExplorerQueries.Result result) {
            setModel(new ResultModel(result));
            var metrics = getFontMetrics(getFont());
            var geometryWidths = new int[result.columns().size()];
            for (int row = 0; row < result.rows().size(); row++) {
                for (int column : result.geometriesByRow().get(row).keySet()) {
                    int width = metrics.stringWidth(result.rows().get(row).get(column)) + 32;
                    geometryWidths[column] = Math.max(geometryWidths[column], width);
                }
            }
            for (int column = 0; column < result.columns().size(); column++) {
                int heading = result.columns().get(column).length() * 8 + 32;
                int width = Math.clamp(Math.max(heading, geometryWidths[column]), 110, 400);
                getColumnModel().getColumn(column).setPreferredWidth(width);
            }
        }

        Optional<Occurrence> selectedOccurrence() {
            if (getSelectedRowCount() != 1 || !(getModel() instanceof ResultModel model)) {
                return Optional.empty();
            }
            return model.result.occurrence(convertRowIndexToModel(getSelectedRow()));
        }

        private static final class ResultModel extends AbstractTableModel {
            private final ExplorerQueries.Result result;

            private ResultModel(ExplorerQueries.Result result) {
                this.result = result;
            }

            @Override
            public int getRowCount() {
                return result.rows().size();
            }

            @Override
            public int getColumnCount() {
                return result.columns().size();
            }

            @Override
            public String getColumnName(int column) {
                return result.columns().get(column);
            }

            @Override
            public Object getValueAt(int row, int column) {
                return result.rows().get(row).get(column);
            }
        }
    }
}
