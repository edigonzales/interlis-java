package guru.interlis.gui;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.WarningPolicy;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.application.CompileService;
import guru.interlis.gui.application.SessionOptions;
import guru.interlis.gui.controller.SessionController;
import guru.interlis.gui.presentation.SourcePreviewModel;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.RepositoryConfig;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class MainFrame extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JTabbedPane views = new JTabbedPane();
    private final JLabel inputs = new JLabel();
    private final JLabel status = new JLabel();
    private final SessionController session;
    private final CheckInputsPanel check;
    private final DependenciesPanel dependencies;
    private final RepositoriesPanel repositories;
    private JDialog sourceInspector;
    private SourcePreviewPanel inspectorPreview;
    private CompileResult displayedResult;
    private final Action add;
    private final Action compile;
    private final Action cancel;
    private final Action offline;
    private final Action rejectWarnings;

    MainFrame(Path directory, CompileService backend) {
        this(directory, backend::compile, backend::inspect);
    }

    MainFrame(Path directory, SessionController.CompileWork backend, SessionController.InputDiscovery discovery) {
        this(directory, backend, discovery, text -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null));
    }

    MainFrame(Path directory, SessionController.CompileWork backend, SessionController.InputDiscovery discovery,
              Consumer<String> clipboard) {
        super("INTERLIS Compiler");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("The compiler window must be created on the event dispatch thread");
        }
        session = new SessionController(Executors.newSingleThreadExecutor(Thread.ofPlatform().name("interlis-session").factory()),
                backend, discovery, RunContextFactory.defaults(), directory, SessionOptions.defaults(), this::render);
        setName("compiler-window");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 700));
        setSize(1280, 860);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                repositories.close();
                session.close();
            }
        });

        add = action("Add files…", event -> chooseFiles());
        compile = action("Compile", event -> session.compile());
        cancel = action("Cancel", event -> session.cancel());
        offline = action("Offline", event -> toggleOffline());
        rejectWarnings = action("Reject warnings", event -> {
            var options = session.view().options();
            session.updateOptions(options.withWarnings(options.warnings() == WarningPolicy.REJECT_WARNINGS
                    ? WarningPolicy.ALLOW_WARNINGS : WarningPolicy.REJECT_WARNINGS));
        });
        Action settings = unavailable("Settings…");
        setJMenuBar(menuBar(settings));

        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        toolbar.setName("session-actions");
        toolbar.add(button(add, "add-files"));
        toolbar.add(button(compile, "compile"));
        toolbar.add(button(cancel, "cancel"));
        toolbar.add(new JToggleButton(offline));
        toolbar.add(new JButton(settings));
        inputs.setName("session-inputs");
        inputs.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        var header = new JPanel(new BorderLayout());
        header.add(toolbar, BorderLayout.NORTH);
        header.add(inputs, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        views.setName("work-areas");
        check = new CheckInputsPanel(session, add, clipboard);
        views.addTab("Check", check);
        dependencies = new DependenciesPanel(this::showSourceInspector, range -> {
            check.showRelated(range);
            views.setSelectedIndex(0);
        });
        views.addTab("Dependencies", dependencies);
        repositories = new RepositoriesPanel(session, directory);
        views.addTab("Repositories", repositories);
        add(views, BorderLayout.CENTER);

        status.setName("session-status");
        status.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        var footer = new JPanel(new BorderLayout());
        footer.add(status, BorderLayout.CENTER);
        footer.add(new JButton(unavailable("Show log")), BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        setTransferHandler(new FileDrop());
        render(session.view());
    }

    private void render(SessionController.View view) {
        boolean idle = view.job() == SessionController.Job.IDLE;
        add.setEnabled(idle);
        compile.setEnabled(view.canCompile());
        cancel.setEnabled(view.job() == SessionController.Job.COMPILING);
        offline.setEnabled(idle);
        offline.putValue(Action.SELECTED_KEY, view.options().repositories().fetchPolicy().offline());
        rejectWarnings.setEnabled(idle);
        rejectWarnings.putValue(Action.SELECTED_KEY, view.options().warnings() == WarningPolicy.REJECT_WARNINGS);
        inputs.setText("Inputs: " + view.files().size() + " files · " + view.roots().size() + " explicit roots · "
                + view.options().repositories().sources().size() + " enabled repositories");
        String running = switch (view.job()) {
            case IDLE -> "";
            case ADDING -> " · Reading inputs…";
            case COMPILING -> " · Compiling…";
            case CANCELLING -> " · Cancelling…";
        };
        String last = view.last().map(result -> runSummary(result) + " · " + TIME.format(result.completedAt())).orElse("None");
        status.setText("Last run: " + last + running + (view.outdated() ? " · Inputs changed — compile again" : ""));
        check.render(view, view.last().map(MainFrame::runSummary).orElse("Add INTERLIS files to start a check."));
        CompileResult result = view.last().orElse(null);
        if (result != displayedResult && sourceInspector != null) {
            sourceInspector.setVisible(false);
            inspectorPreview.clear("Select a source location.");
        }
        displayedResult = result;
        dependencies.render(result);
        repositories.render(view);
    }

    private static String runSummary(CompileResult result) {
        String outcome = switch (result.outcome()) {
            case CompileResult.Compiled(var compiled) -> switch (compiled) {
                case CompilationOutcome.Succeeded ignored -> "Succeeded";
                case CompilationOutcome.Rejected(var analysis, var rejection) -> "Rejected · "
                        + String.join(", ", rejection.reasons().stream().map(reason -> display(reason.name())).toList());
                case CompilationOutcome.Cancelled(var analysis, var reason) -> "Cancelled · " + display(reason.name());
                case CompilationOutcome.Failed(var id, var diagnostic) -> "Internal failure " + id.value();
            };
            case CompileResult.InputFailed(var problems) -> "Input failure · " + problems.size() + " unreadable files";
            case CompileResult.RepositoryRejected(var rejection) -> "Repository resolution rejected · "
                    + display(rejection.problems().getFirst().reason().name());
            case CompileResult.ResolutionStale ignored -> "Repository resolution became stale";
            case CompileResult.Cancelled(var reason) -> "Cancelled · " + display(reason.name());
            case CompileResult.Failed ignored -> "Internal failure";
        };
        long errors = result.diagnostics().stream().filter(diagnostic -> diagnostic.severity() == Severity.ERROR).count();
        long warnings = result.diagnostics().stream().filter(diagnostic -> diagnostic.severity() == Severity.WARNING).count();
        return outcome + " · " + errors + " errors · " + warnings + " warnings · "
                + String.format(Locale.ROOT, "%.2f s", result.elapsed().toMillis() / 1000.0);
    }

    private static String display(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void toggleOffline() {
        var options = session.view().options();
        var policy = options.repositories().fetchPolicy();
        session.updateOptions(new SessionOptions(new RepositoryConfig(options.repositories().sources(),
                new FetchPolicy(!policy.offline(), policy.maxBytes(), policy.connectTimeout(), policy.readTimeout(), policy.requestTimeout())),
                options.cache(), options.proxy(), options.warnings(), options.diagnosticLanguage()));
    }

    private void chooseFiles() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("INTERLIS models (*.ili)", "ili"));
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addFiles(Arrays.stream(chooser.getSelectedFiles()).map(File::toPath).toList());
        }
    }

    void addFiles(List<Path> paths) {
        views.setSelectedIndex(0);
        session.addFiles(paths);
    }

    private void showSourceInspector(SourcePreviewModel model) {
        if (sourceInspector == null) {
            sourceInspector = new JDialog(this, "Captured source");
            sourceInspector.setName("dependency-source-inspector");
            sourceInspector.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            inspectorPreview = new SourcePreviewPanel();
            sourceInspector.add(inspectorPreview);
            sourceInspector.setSize(780, 500);
            sourceInspector.setLocationRelativeTo(this);
        }
        inspectorPreview.show(model);
        sourceInspector.setVisible(true);
        sourceInspector.toFront();
    }

    private JMenuBar menuBar(Action settings) {
        var menus = new JMenuBar();
        var file = new JMenu("File");
        file.add(add);
        file.add(compile);
        file.add(cancel);
        file.addSeparator();
        file.add(action("Close", event -> dispose()));
        menus.add(file);
        var view = new JMenu("View");
        for (var title : new String[]{"Check", "Dependencies", "Repositories"}) {
            view.add(action(title, event -> views.setSelectedIndex(views.indexOfTab(title))));
        }
        menus.add(view);
        var preferences = new JMenu("Settings");
        preferences.add(new JCheckBoxMenuItem(rejectWarnings));
        var language = new JMenu("Diagnostic language");
        var languages = new ButtonGroup();
        for (DiagnosticLanguage value : List.of(DiagnosticLanguage.EN, DiagnosticLanguage.DE)) {
            var item = new JRadioButtonMenuItem(action(value == DiagnosticLanguage.EN ? "English" : "German",
                    event -> session.setDiagnosticLanguage(value)));
            item.setName("diagnostic-language-" + value.name());
            item.setSelected(session.view().options().diagnosticLanguage() == value);
            languages.add(item);
            language.add(item);
        }
        preferences.add(language);
        preferences.add(settings);
        menus.add(preferences);
        var help = new JMenu("Help");
        help.add(action("About INTERLIS Compiler", event -> JOptionPane.showMessageDialog(MainFrame.this,
                "INTERLIS Compiler", "About", JOptionPane.INFORMATION_MESSAGE)));
        menus.add(help);
        return menus;
    }

    private static JButton button(Action action, String name) {
        var button = new JButton(action);
        button.setName(name);
        return button;
    }

    private static Action action(String name, Consumer<ActionEvent> perform) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
                perform.accept(event);
            }
        };
    }

    private static Action unavailable(String name) {
        var action = action(name, event -> { throw new IllegalStateException("Disabled action: " + name); });
        action.setEnabled(false);
        return action;
    }

    private final class FileDrop extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return add.isEnabled() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                var files = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                addFiles(files.stream().map(File.class::cast).map(File::toPath).toList());
                return true;
            } catch (IOException | UnsupportedFlavorException | ClassCastException failure) {
                return false;
            }
        }
    }
}
