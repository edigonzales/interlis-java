package guru.interlis.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

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
import guru.interlis.gui.infrastructure.GuiSettings;
import guru.interlis.gui.infrastructure.GuiSettingsStore;
import guru.interlis.repository.RepositoryCacheConfiguration;
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
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
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
import java.awt.event.KeyEvent;
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
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class MainFrame extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JTabbedPane views = new JTabbedPane();
    private final JLabel inputs = new JLabel();
    private final JLabel status = new JLabel();
    private final JLabel settingsWarning = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final JButton showLog = new JButton("Show log");
    private final Optional<WindowPreferences> preferences;
    private final java.util.EnumMap<DiagnosticLanguage, JRadioButtonMenuItem> languageItems =
            new java.util.EnumMap<>(DiagnosticLanguage.class);
    private GuiSettings.Theme theme;
    private Optional<Path> lastDirectory;
    private boolean initialized;
    private JDialog logWindow;
    private JTextArea logText;
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
    private final Action settings;

    MainFrame(Path directory, CompileService backend) {
        this(directory, backend::compile, backend::inspect);
    }

    MainFrame(Path directory, SessionController.CompileWork backend, SessionController.InputDiscovery discovery) {
        this(directory, backend, discovery, text -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null));
    }

    MainFrame(Path directory, SessionController.CompileWork backend, SessionController.InputDiscovery discovery,
              Consumer<String> clipboard) {
        this(directory, backend, discovery, clipboard, GuiSettings.defaults(), Optional.empty(), "");
    }

    MainFrame(Path directory, SessionController.CompileWork backend, SessionController.InputDiscovery discovery,
              Consumer<String> clipboard, GuiSettings saved, Optional<GuiSettingsStore> store, String warning) {
        super("INTERLIS Compiler");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("The compiler window must be created on the event dispatch thread");
        }
        theme = saved.theme();
        lastDirectory = saved.lastDirectory();
        settingsWarning.setName("settings-warning");
        settingsWarning.putClientProperty("html.disable", true);
        showSettingsWarning(warning);
        preferences = store.map(value -> new WindowPreferences(value, this::showSettingsWarning));
        session = new SessionController(Executors.newSingleThreadExecutor(Thread.ofPlatform().name("interlis-session").factory()),
                backend, discovery, RunContextFactory.defaults(), directory, saved.session(), this::render);
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
                persistPreferences();
                preferences.ifPresent(WindowPreferences::close);
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
        settings = action("Settings…", event -> editSettings());
        setJMenuBar(menuBar(settings));

        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        toolbar.setName("session-actions");
        toolbar.add(button(add, "add-files"));
        toolbar.add(button(compile, "compile"));
        toolbar.add(button(cancel, "cancel"));
        toolbar.add(new JToggleButton(offline));
        toolbar.add(button(settings, "compiler-settings"));
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
        showLog.setName("show-run-log");
        showLog.addActionListener(event -> showRunLog());
        progress.setName("compile-progress");
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(100, 16));
        var activity = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 4));
        activity.add(progress);
        activity.add(showLog);
        footer.add(activity, BorderLayout.EAST);
        footer.add(settingsWarning, BorderLayout.NORTH);
        add(footer, BorderLayout.SOUTH);
        setTransferHandler(new FileDrop());
        session.configureRepositories(saved.repositories());
        render(session.view());
        WindowPreferences.restore(this, saved);
        initialized = true;
    }

    private void render(SessionController.View view) {
        boolean idle = view.job() == SessionController.Job.IDLE;
        add.setEnabled(idle);
        compile.setEnabled(view.canCompile());
        cancel.setEnabled(view.job() == SessionController.Job.COMPILING);
        offline.setEnabled(idle);
        settings.setEnabled(idle);
        progress.setVisible(view.job() == SessionController.Job.COMPILING || view.job() == SessionController.Job.CANCELLING);
        showLog.setEnabled(view.last().isPresent());
        languageItems.get(view.options().diagnosticLanguage()).setSelected(true);
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
        if (result != displayedResult && logWindow != null && logWindow.isVisible()) {
            logText.setText(RunLog.text(result));
            logText.setCaretPosition(0);
        }
        displayedResult = result;
        dependencies.render(result);
        repositories.render(view);
        if (initialized && idle) {
            persistPreferences();
        }
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
        lastDirectory.ifPresent(path -> chooser.setCurrentDirectory(path.toFile()));
        chooser.setFileFilter(new FileNameExtensionFilter("INTERLIS models (*.ili)", "ili"));
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectory = Optional.ofNullable(chooser.getCurrentDirectory()).map(File::toPath);
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
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        file.add(add).setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut));
        file.add(compile).setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, shortcut));
        file.add(cancel).setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        file.addSeparator();
        file.add(action("Close", event -> dispose()));
        menus.add(file);
        var view = new JMenu("View");
        String[] titles = {"Check", "Dependencies", "Repositories"};
        for (int index = 0; index < titles.length; index++) {
            String title = titles[index];
            view.add(action(title, event -> views.setSelectedIndex(views.indexOfTab(title))))
                    .setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1 + index, shortcut));
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
            languageItems.put(value, item);
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

    private void editSettings() {
        Path home = Path.of(System.getProperty("user.home"));
        Path suggestedCache = RepositoryCacheConfiguration.defaults(home).directory().orElseThrow();
        SettingsDialog.edit(this, theme, session.view().options(), suggestedCache)
                .ifPresent(choice -> applySettings(choice.theme(), choice.options()));
    }

    void applySettings(GuiSettings.Theme chosenTheme, SessionOptions chosenOptions) {
        if (session.view().job() != SessionController.Job.IDLE) {
            return;
        }
        if (theme != chosenTheme) {
            if (!setupTheme(chosenTheme)) {
                showSettingsWarning("The selected theme could not be installed.");
                return;
            }
            theme = chosenTheme;
            FlatLaf.updateUI();
        }
        session.updateOptions(chosenOptions);
        persistPreferences();
    }

    static boolean setupTheme(GuiSettings.Theme theme) {
        return theme == GuiSettings.Theme.DARK ? FlatDarkLaf.setup() : FlatLightLaf.setup();
    }

    private void persistPreferences() {
        var view = session.view();
        preferences.ifPresent(saver -> saver.save(new GuiSettings(theme, view.options(), view.repositories(),
                WindowPreferences.bounds(this), WindowPreferences.dividers(this), lastDirectory)));
    }

    private void showSettingsWarning(String warning) {
        settingsWarning.setText(warning);
        settingsWarning.setVisible(!warning.isEmpty());
    }

    private void showRunLog() {
        if (logWindow == null) {
            logWindow = new JDialog(this, "Compiler run log");
            logWindow.setName("compiler-run-log");
            logWindow.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            logText = new JTextArea();
            logText.setName("compiler-run-log-text");
            logText.setEditable(false);
            logWindow.add(new JScrollPane(logText));
            logWindow.setSize(720, 440);
            logWindow.setLocationRelativeTo(this);
        }
        logText.setText(RunLog.text(displayedResult));
        logText.setCaretPosition(0);
        logWindow.setVisible(true);
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
