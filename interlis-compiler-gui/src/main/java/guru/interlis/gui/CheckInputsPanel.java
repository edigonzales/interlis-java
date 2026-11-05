package guru.interlis.gui;

import guru.interlis.gui.application.LocalInput;
import guru.interlis.gui.application.RootChoice;
import guru.interlis.gui.controller.SessionController;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

final class CheckInputsPanel extends JPanel {
    private final DefaultListModel<LocalInput> files = new DefaultListModel<>();
    private final DefaultListModel<RootChoice.Local> models = new DefaultListModel<>();
    private final JList<LocalInput> fileList = new JList<>(files);
    private final JList<RootChoice.Local> modelList = new JList<>(models);
    private final JLabel rootMode = new JLabel("All input models");
    private final JLabel checkSummary = new JLabel("Add INTERLIS files to start a check.");
    private final JButton remove = new JButton("Remove");
    private final JButton useSelected = new JButton("Use selected roots");
    private final JButton useAll = new JButton("Use all inputs");
    private final JLabel message = new JLabel();
    private final SessionController session;
    private final ProblemsPanel problems;
    private boolean editable;
    private List<RootChoice> activeRoots = List.of();

    CheckInputsPanel(SessionController session, Action add, Consumer<String> clipboard) {
        super(new BorderLayout());
        this.session = session;
        problems = new ProblemsPanel(clipboard);
        fileList.setName("local-files");
        fileList.setCellRenderer(renderer((LocalInput file) -> file.path().getFileName() + file.problem()
                .map(problem -> " · " + problem.message())
                .orElseGet(() -> file.models().isEmpty() ? " · no model declarations found" : ""),
                file -> file.path().toString()));
        modelList.setName("local-models");
        modelList.setCellRenderer(renderer((RootChoice.Local model) -> (activeRoots.contains(model) ? "Root · " : "") + model.name()
                + model.version().map(version -> " · " + version).orElse(""), model -> model.source().value()));
        fileList.addListSelectionListener(event -> updateButtons());
        modelList.addListSelectionListener(event -> updateButtons());
        remove.setName("remove-files");
        remove.addActionListener(event -> session.removeFiles(fileList.getSelectedValuesList().stream().map(LocalInput::path).toList()));
        useSelected.setName("use-selected-roots");
        useSelected.addActionListener(event -> session.setLocalRoots(modelList.getSelectedValuesList()));
        useAll.setName("use-all-inputs");
        useAll.addActionListener(event -> {
            modelList.clearSelection();
            session.useAllInputs();
        });
        rootMode.setName("root-mode");
        message.putClientProperty("html.disable", true);
        message.setName("input-notice");
        checkSummary.setName("check-summary");
        checkSummary.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        var filePanel = section("Inputs", new JScrollPane(fileList), actions(new JButton(add), remove));
        var rootActions = actions(useSelected, useAll);
        var rootPanel = section("Root models", new JScrollPane(modelList), rootActions);
        var rootHeader = new JPanel(new BorderLayout());
        rootHeader.add(rootMode, BorderLayout.NORTH);
        rootPanel.add(rootHeader, BorderLayout.NORTH);
        var selectors = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filePanel, rootPanel);
        selectors.setResizeWeight(.50);
        var summary = new JPanel(new BorderLayout());
        summary.add(checkSummary, BorderLayout.NORTH);
        summary.add(problems, BorderLayout.CENTER);
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, selectors, summary);
        split.setResizeWeight(.40);
        split.setDividerLocation(395);
        add(split, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);
        updateButtons();
    }

    void render(SessionController.View view, String runSummary) {
        editable = view.job() == SessionController.Job.IDLE;
        var selectedPaths = fileList.getSelectedValuesList().stream().map(LocalInput::path).toList();
        var selectedModels = modelList.getSelectedValuesList();
        if (!modelItems(files).equals(view.files())) {
            files.clear();
            files.addAll(view.files());
            for (int i = 0; i < files.size(); i++) {
                if (selectedPaths.contains(files.get(i).path())) {
                    fileList.addSelectionInterval(i, i);
                }
            }
        }
        var modelChoices = view.files().stream().flatMap(file -> file.models().stream()).toList();
        if (!modelItems(models).equals(modelChoices)) {
            models.clear();
            models.addAll(modelChoices);
            for (int i = 0; i < models.size(); i++) {
                if (selectedModels.contains(models.get(i))) {
                    modelList.addSelectionInterval(i, i);
                }
            }
        }
        activeRoots = view.roots();
        modelList.repaint();
        rootMode.setText(view.roots().isEmpty() ? "All input models" : "Explicit roots: " + view.roots().size());
        message.setText(view.notice());
        checkSummary.setText(runSummary);
        problems.render(view.last().orElse(null), view.options().diagnosticLanguage());
        updateButtons();
    }

    void showRelated(guru.interlis.source.SourceRange range) {
        problems.showRelated(range);
    }

    private void updateButtons() {
        remove.setEnabled(editable && !fileList.isSelectionEmpty());
        useSelected.setEnabled(editable && !modelList.isSelectionEmpty());
        useAll.setEnabled(editable && !models.isEmpty());
    }

    private static <T> List<T> modelItems(DefaultListModel<T> model) {
        return java.util.Collections.list(model.elements());
    }

    private static JPanel actions(JButton... buttons) {
        var result = new JPanel(new FlowLayout(FlowLayout.LEADING));
        for (JButton button : buttons) {
            result.add(button);
        }
        return result;
    }

    private static JPanel section(String title, JScrollPane list, JPanel buttons) {
        var result = new JPanel(new BorderLayout());
        result.setBorder(BorderFactory.createTitledBorder(title));
        result.add(list, BorderLayout.CENTER);
        result.add(buttons, BorderLayout.SOUTH);
        return result;
    }

    private static <T> DefaultListCellRenderer renderer(Function<T, String> label, Function<T, String> detail) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, "", index, selected, focus);
                putClientProperty("html.disable", true);
                @SuppressWarnings("unchecked")
                T row = (T) value;
                setText(label.apply(row));
                setToolTipText("<html>" + escape(detail.apply(row)) + "</html>");
                return this;
            }
        };
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
