package guru.interlis.gui;

import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.ModelLocation;
import guru.interlis.diagnostics.DiagnosticMessageFormatter;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.presentation.DependencySnapshot;
import guru.interlis.gui.presentation.DependencyTree;
import guru.interlis.gui.presentation.DiagnosticPresenter;
import guru.interlis.gui.presentation.SourcePreviewModel;
import guru.interlis.source.SourceRange;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class DependenciesPanel extends JPanel {
    private final JTextField search = new JTextField(17);
    private final JCheckBox unresolved = new JCheckBox("Unresolved only");
    private final JLabel completeness = new JLabel();
    private final JTree tree = new JTree();
    private final JTextArea overview = textArea("dependency-overview");
    private final JTextArea direct = textArea("dependency-direct-imports");
    private final JTextArea incoming = textArea("dependency-used-by");
    private final JTextArea resolution = textArea("dependency-resolution");
    private final JButton showSource = new JButton("Show source");
    private final JButton showProblems = new JButton("Show related problems");
    private final Consumer<SourcePreviewModel> openSource;
    private final Consumer<SourceRange> openProblems;
    private CompileResult result;
    private DependencySnapshot graph;

    DependenciesPanel(Consumer<SourcePreviewModel> openSource, Consumer<SourceRange> openProblems) {
        super(new BorderLayout());
        this.openSource = openSource;
        this.openProblems = openProblems;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        search.setName("dependency-search");
        search.getAccessibleContext().setAccessibleName("Find model");
        unresolved.setName("dependency-unresolved-only");
        completeness.setName("dependency-completeness");
        tree.setName("dependency-tree");
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            {
                putClientProperty("html.disable", true);
            }

            @Override
            public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                                    boolean expanded, boolean leaf, int row,
                                                                    boolean focused) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
                String tooltip = value instanceof DependencyTree.Occurrence occurrence
                        ? occurrence.target().map(ModelLocation::source).map(DiagnosticPresenter::displaySource).orElse(null)
                        : null;
                setToolTipText(tooltip);
                return this;
            }
        });
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeSelectionListener(event -> updateInspector());

        var controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(new JLabel("Find model"));
        controls.add(search);
        controls.add(unresolved);
        controls.add(completeness);
        add(controls, BorderLayout.NORTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshTree();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshTree();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshTree();
            }
        });
        unresolved.addActionListener(event -> refreshTree());

        var details = new JTabbedPane();
        details.setName("dependency-inspector");
        details.addTab("Direct imports", new JScrollPane(direct));
        details.addTab("Used by", new JScrollPane(incoming));
        details.addTab("Resolution", new JScrollPane(resolution));
        var right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Selected model or import"));
        var top = new JScrollPane(overview);
        overview.setRows(6);
        right.add(top, BorderLayout.NORTH);
        right.add(details, BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING));
        showSource.setName("dependency-show-source");
        showProblems.setName("dependency-show-problems");
        showSource.addActionListener(event -> selection().flatMap(this::range).flatMap(this::preview).ifPresent(openSource));
        showProblems.addActionListener(event -> selection().flatMap(this::range).ifPresent(openProblems));
        buttons.add(showSource);
        buttons.add(showProblems);
        right.add(buttons, BorderLayout.SOUTH);
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), right);
        split.setResizeWeight(.38);
        split.setDividerLocation(340);
        add(split, BorderLayout.CENTER);
        refreshTree();
    }

    void render(CompileResult next) {
        if (result == next) {
            return;
        }
        result = next;
        graph = next == null ? null : DependencySnapshot.from(next);
        refreshTree();
    }

    private void refreshTree() {
        tree.setModel(graph == null ? new OccurrenceModel(null)
                : new OccurrenceModel(new DependencyTree(graph, search.getText(), unresolved.isSelected())));
        completeness.setText(graph == null ? "No compiler run" : switch (graph.completeness()) {
            case COMPLETE -> "Resolution captured";
            case PARTIAL -> "Partial resolution";
            case UNAVAILABLE -> "Dependency data unavailable";
        });
        updateInspector();
    }

    private Optional<DependencyTree.Occurrence> selection() {
        return Optional.ofNullable(tree.getLastSelectedPathComponent())
                .filter(DependencyTree.Occurrence.class::isInstance).map(DependencyTree.Occurrence.class::cast)
                .filter(item -> !item.limit());
    }

    private void updateInspector() {
        var selected = selection();
        if (selected.isEmpty()) {
            setText(overview, graph == null ? "Compile the session to inspect dependencies."
                    : "Select a model or import in the captured run.");
            setText(direct, "");
            setText(incoming, "");
            setText(resolution, trace(Optional.empty()));
        } else {
            var item = selected.orElseThrow();
            setText(overview, describe(item));
            setText(direct, item.target().map(id -> lines("Direct imports", graph.directImports(id).stream()
                    .map(edge -> safe(edge.site().importedModel()) + " · " + edge.status()
                            + edge.target().map(target -> " · " + DiagnosticPresenter.shortSource(target.source())).orElse(""))
                    .toList())).orElse("No resolved target."));
            var users = item.status() == DependencySnapshot.Status.BUILTIN ? graph.builtinUsers()
                    : item.target().map(graph::usedBy).orElse(List.of());
            setText(incoming, lines("Direct incoming imports", users.stream()
                    .map(edge -> safe(edge.importer().modelName()) + " · "
                            + DiagnosticPresenter.shortSource(edge.importer().source())).toList()));
            setText(resolution, trace(Optional.of(item.name())));
        }
        var range = selected.flatMap(this::range);
        showSource.setEnabled(range.flatMap(this::source).isPresent());
        showProblems.setEnabled(range.isPresent());
    }

    private Optional<SourceRange> range(DependencyTree.Occurrence selected) {
        return selected.target().flatMap(graph::model).flatMap(DependencySnapshot.Node::declaration)
                .or(() -> selected.edge().map(edge -> edge.site().nameRange()));
    }

    private Optional<SourcePreviewModel> preview(SourceRange range) {
        return source(range).flatMap(snapshot -> SourcePreviewModel.from(snapshot.source(), Optional.of(range)));
    }

    private Optional<CompileResult.SourceSnapshot> source(SourceRange range) {
        return Optional.ofNullable(result.sources().get(range.source()))
                .filter(snapshot -> snapshot.source().revision().equals(range.revision())
                        && range.endOffset() <= snapshot.source().length());
    }

    private String describe(DependencyTree.Occurrence item) {
        var report = new StringBuilder(safe(item.name())).append(" · ").append(item.status());
        if (item.cycle()) {
            report.append(" · cycle reference");
        }
        item.target().ifPresent(target -> {
            report.append("\nModel version: ").append(target.modelVersion().filter(version -> !version.isEmpty())
                    .map(DependenciesPanel::safe).orElse("Not provided"));
            report.append("\nINTERLIS: ").append(graph.model(target).flatMap(DependencySnapshot.Node::language)
                    .map(guru.interlis.model.LanguageVersion::literal).orElse("Not available"));
            report.append("\nSource: ").append(DiagnosticPresenter.displaySource(target.source()))
                    .append(" · revision ").append(target.revision().value());
            var source = result.sources().get(target.source());
            if (source != null) {
                report.append("\nOrigin: ").append(source.origin() == CompileResult.Origin.LOCAL_INPUT ? "Local input" : "Repository");
                source.digest().ifPresent(digest -> report.append(" · SHA-256 ").append(digest.value()));
            }
        });
        item.candidates().forEach(candidate -> candidate(report, candidate));
        item.edge().flatMap(DependencySnapshot.Edge::resolution).ifPresent(binding -> {
            switch (binding) {
                case ImportResolution.Missing(var problem) -> report.append("\n").append(problem.reason()).append(" · ").append(safe(problem.safeDetail()));
                case ImportResolution.Blocked(var problem) -> report.append("\n").append(problem.reason()).append(" · ").append(safe(problem.safeDetail()));
                case ImportResolution.Ambiguous(var candidates, var problem) -> {
                    report.append("\n").append(problem.reason()).append(" · ").append(safe(problem.safeDetail()));
                    candidates.forEach(candidate -> candidate(report, candidate));
                }
                case ImportResolution.Resolved ignored -> { }
            }
        });
        return report.toString();
    }

    private static void candidate(StringBuilder report, ModelLocation location) {
        report.append("\nCandidate: ").append(safe(location.modelName())).append(" · ")
                .append(location.modelVersion().map(DependenciesPanel::safe).orElse("Not provided"))
                .append(" · ").append(DiagnosticPresenter.displaySource(location.source()));
    }

    private String trace(Optional<String> name) {
        if (graph == null) {
            return "";
        }
        var steps = graph.repositorySteps().stream().filter(step -> name.isEmpty() || name.orElseThrow().equals(step.modelName()))
                .map(step -> step.kind() + " · " + safe(step.modelName()) + " · "
                        + safe(step.repository().toString()) + " · " + safe(step.detail())).toList();
        return lines("Observed repository decisions", steps) + "\n\n"
                + lines("Captured workspace resolution trace", graph.trace().stream().map(DependenciesPanel::safe).toList());
    }

    private static String lines(String heading, List<String> lines) {
        return heading + "\n" + (lines.isEmpty() ? "None captured" : lines.stream().collect(Collectors.joining("\n")));
    }

    private static String safe(String text) {
        return DiagnosticMessageFormatter.safeText(text);
    }

    private static JTextArea textArea(String name) {
        var area = new JTextArea();
        area.setName(name);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static void setText(JTextArea area, String text) {
        area.setText(text);
        area.setCaretPosition(0);
    }

    private static final class OccurrenceModel implements TreeModel {
        private static final int MAX_OCCURRENCES = 6000;
        private final Object root = "Dependencies";
        private final DependencyTree tree;
        private final List<DependencyTree.Occurrence> roots;
        private final Map<DependencyTree.Occurrence, List<DependencyTree.Occurrence>> loaded = new HashMap<>();
        private int count;

        private OccurrenceModel(DependencyTree tree) {
            this.tree = tree;
            roots = tree == null ? List.of() : tree.roots();
            count = roots.size();
        }

        private List<DependencyTree.Occurrence> children(Object parent) {
            if (parent == root) {
                return roots;
            }
            var occurrence = (DependencyTree.Occurrence) parent;
            if (!tree.hasChildren(occurrence)) {
                return List.of();
            }
            return loaded.computeIfAbsent(occurrence, key -> {
                int remaining = MAX_OCCURRENCES - count;
                if (remaining <= 0) {
                    return List.of(DependencyTree.limit());
                }
                var children = tree.children(key);
                count += Math.min(remaining, children.size());
                if (children.size() <= remaining) {
                    return children;
                }
                var limited = new java.util.ArrayList<>(children.subList(0, remaining));
                limited.add(DependencyTree.limit());
                return List.copyOf(limited);
            });
        }

        @Override public Object getRoot() { return root; }
        @Override public Object getChild(Object parent, int index) { return children(parent).get(index); }
        @Override public int getChildCount(Object parent) { return children(parent).size(); }
        @Override public int getIndexOfChild(Object parent, Object child) { return children(parent).indexOf(child); }
        @Override public boolean isLeaf(Object node) { return node != root && !tree.hasChildren((DependencyTree.Occurrence) node); }
        @Override public void valueForPathChanged(TreePath path, Object value) { }
        @Override public void addTreeModelListener(TreeModelListener listener) { }
        @Override public void removeTreeModelListener(TreeModelListener listener) { }
    }
}
