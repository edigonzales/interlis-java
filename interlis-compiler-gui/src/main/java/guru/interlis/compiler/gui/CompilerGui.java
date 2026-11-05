package guru.interlis.compiler.gui;

import guru.interlis.compiler.CompilerEngines;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Desktop compiler host. JavaFX state is owned exclusively by the FX application thread. */
public final class CompilerGui extends Application {
    private final DesktopCompiler compiler = new DesktopCompiler(CompilerEngines.create());
    private final ListView<DesktopCompiler.Input> modelFiles = new ListView<>();
    private final TreeView<String> dependencies = new TreeView<>();
    private final TableView<DesktopCompiler.Problem> problems = new TableView<>();
    private final Label status = new Label("Select INTERLIS model files or load Example.");
    private final Label diagramStatus = new Label("Loading diagram renderer…");
    private final Button compile = new Button("Compile");
    private final Bridge bridge = new Bridge(); // WebEngine holds the bridge weakly.
    private final StringBuilder events = new StringBuilder();
    private LocalWebServer server;
    private Stage stage;
    private WebEngine web;
    private Path webData;
    private JSObject diagram;
    private Task<DesktopCompiler.Result> active;
    private long revision;
    private Path report;
    private boolean finished;
    private boolean passed;
    private boolean negativeProbe;

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage primaryStage) throws IOException {
        stage = primaryStage;
        var arguments = getParameters().getRaw();
        if (arguments.size() == 2 && arguments.getFirst().equals("--probe")) report = Path.of(arguments.get(1)).toAbsolutePath();
        else if (!arguments.isEmpty()) throw new IllegalArgumentException("Expected no arguments or --probe <report>");
        server = new LocalWebServer();
        var view = new WebView();
        web = view.getEngine();
        webData = Files.createTempDirectory("interlis-gui-webview-");
        web.setUserDataDirectory(webData.toFile());
        web.setOnError(event -> bridge.report("ERROR", event.getMessage()));
        web.getLoadWorker().stateProperty().addListener((observable, before, current) -> {
            if (current == Worker.State.SUCCEEDED) {
                try {
                    ((JSObject) web.executeScript("window")).setMember("javaHost", bridge);
                    web.executeScript("window.guiEvents.forEach(function(e){javaHost.report('EARLY',e);});");
                    attachRenderer();
                } catch (RuntimeException failure) { bridge.report("ERROR", "Renderer startup: " + failure); }
            } else if (current == Worker.State.FAILED) bridge.report("ERROR", String.valueOf(web.getLoadWorker().getException()));
        });
        var open = new Button("Open"); open.setOnAction(event -> openFiles());
        var example = new Button("Example"); example.setOnAction(event -> setInputs(List.of(
                DesktopCompiler.Input.example("GuiExample.ili"), DesktopCompiler.Input.example("Interactive.ili"))));
        compile.setDisable(true); compile.setOnAction(event -> compile());
        var toolbar = new ToolBar(open, compile, example);
        problems.getColumns().setAll(List.of(column("Severity", DesktopCompiler.Problem::severity, 85),
                column("Position", DesktopCompiler.Problem::position, 300), column("Code", DesktopCompiler.Problem::code, 135),
                column("Message", DesktopCompiler.Problem::message, 580)));
        problems.setPlaceholder(new Label("No compiler diagnostics"));
        dependencies.setShowRoot(false);
        var inputs = new SplitPane(panel("Model files", modelFiles), panel("Dependencies", dependencies));
        inputs.setOrientation(Orientation.VERTICAL); inputs.setDividerPositions(.47);
        var upper = new SplitPane(inputs, new BorderPane(view, diagramStatus, null, null, null)); upper.setDividerPositions(.25);
        var content = new SplitPane(upper, panel("Problems", problems));
        content.setOrientation(Orientation.VERTICAL); content.setDividerPositions(.78);
        stage.setScene(new Scene(new BorderPane(content, toolbar, null, status, null), 1230, 850));
        stage.setTitle("INTERLIS Compiler"); stage.show();
        web.load(server.url());
        if (report != null) {
            var timeout = new PauseTransition(Duration.seconds(50));
            timeout.setOnFinished(event -> finish(false, "Timed out before packaged compiler/renderer probe completed"));
            timeout.play();
        }
    }

    private void attachRenderer() {
        if (diagram != null || finished) return;
        if (web.executeScript("window.guiDiagram") instanceof JSObject renderer) {
            diagram = renderer;
            diagramStatus.setText("Compile model files to show a diagram.");
            if (report != null) Platform.runLater(this::startPositiveProbe);
        }
    }

    private static VBox panel(String title, javafx.scene.layout.Region content) {
        var heading = new Label(title); heading.setStyle("-fx-font-weight: bold; -fx-padding: 4px 6px;");
        javafx.scene.layout.VBox.setVgrow(content, javafx.scene.layout.Priority.ALWAYS);
        return new VBox(heading, content);
    }
    private static TableColumn<DesktopCompiler.Problem, String> column(String name, Function<DesktopCompiler.Problem, String> text, int width) {
        var column = new TableColumn<DesktopCompiler.Problem, String>(name);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        return column;
    }
    private void openFiles() {
        var chooser = new FileChooser(); chooser.setTitle("Open INTERLIS models");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("INTERLIS model files", "*.ili", "*.ILI"));
        var chosen = chooser.showOpenMultipleDialog(stage);
        if (chosen != null) setInputs(chosen.stream().map(file -> file.toPath().toAbsolutePath().normalize()).distinct().map(DesktopCompiler.Input::file).toList());
    }
    private void setInputs(List<DesktopCompiler.Input> values) {
        invalidate();
        modelFiles.getItems().setAll(values);
        status.setText(values.size() + " model file(s) loaded. Ready to compile.");
        updateCompileButton();
    }
    private void invalidate() {
        revision++;
        if (active != null) { active.cancel(true); active = null; }
        problems.getItems().clear();
        dependencies.setRoot(null);
        if (diagram != null) diagram.call("clear");
        diagramStatus.setText("No diagram for the current inputs");
    }
    private void updateCompileButton() { compile.setDisable(modelFiles.getItems().isEmpty() || active != null); }
    private void compile() {
        invalidate();
        var inputs = List.copyOf(modelFiles.getItems());
        long current = revision;
        var task = new Task<DesktopCompiler.Result>() {
            @Override protected DesktopCompiler.Result call() throws IOException {
                if (Platform.isFxApplicationThread()) throw new IllegalStateException("Compiler ran on the FX thread");
                return compiler.compile(inputs, this::isCancelled);
            }
        };
        active = task;
        status.setText("Compiling " + inputs.size() + " model file(s)…");
        updateCompileButton();
        task.setOnSucceeded(event -> {
            if (current != revision || active != task) return;
            active = null; updateCompileButton();
            var result = task.getValue();
            problems.getItems().setAll(result.problems());
            var tree = new TreeItem<String>("Models");
            tree.getChildren().setAll(result.dependencies().stream().map(CompilerGui::treeItem).toList());
            dependencies.setRoot(tree); tree.setExpanded(true);
            status.setText(result.status() + " — " + problems.getItems().size() + " diagnostic(s)");
            if (report != null) record("COMPILE", "background=true, " + status.getText());
            if (report != null && negativeProbe) { checkRejectedProbe(result); return; }
            if (report != null && (!result.accepted() || result.diagram().isEmpty() || !hasExampleDependency(tree))) {
                finish(false, "Valid fixture did not produce an accepted schema, bound import and diagram: " + result); return;
            }
            if (report != null) record("DEPENDENCY", "GuiExample -> Interactive");
            result.diagram().ifPresent(json -> {
                if (diagram == null) diagramStatus.setText("Diagram renderer unavailable; compile again after it loads.");
                else diagram.call("show", json, Long.toString(current));
            });
        });
        task.setOnFailed(event -> {
            if (current != revision || active != task) return;
            active = null; updateCompileButton();
            status.setText("Cannot compile: " + task.getException().getMessage());
            if (report != null) finish(false, status.getText());
        });
        Thread.ofVirtual().name("interlis-gui-compile").start(task);
    }
    private static TreeItem<String> treeItem(DesktopCompiler.Dependency item) {
        var row = new TreeItem<>(item.name()); row.setExpanded(true);
        row.getChildren().setAll(item.imports().stream().map(CompilerGui::treeItem).toList());
        return row;
    }
    private static boolean hasExampleDependency(TreeItem<String> tree) {
        return tree.getChildren().stream().filter(model -> model.getValue().equals("GuiExample"))
                .flatMap(model -> model.getChildren().stream()).anyMatch(child -> child.getValue().startsWith("Interactive"));
    }
    private void startPositiveProbe() {
        if (finished) return;
        setInputs(List.of(probeInput("GuiExample.ili"), probeInput("Interactive.ili")));
        compile.fire();
    }
    private DesktopCompiler.Input probeInput(String name) {
        return DesktopCompiler.Input.file(report.getParent().resolve("probe-models").resolve(name));
    }
    private void checkRejectedProbe(DesktopCompiler.Result result) {
        var diagnostic = problems.getItems().stream().filter(row -> row.severity().equals("ERROR")
                && row.position().contains("Broken.ili:5:") && row.message().contains("UndefinedDomain")).findFirst();
        if (result.accepted() || result.diagram().isPresent() || diagnostic.isEmpty()
                || Boolean.TRUE.equals(web.executeScript("!!document.querySelector('#canvas svg.sprotty-graph')"))) {
            finish(false, "Invalid fixture did not show the expected source diagnostic and clear the diagram: " + result); return;
        }
        record("DIAGNOSTIC", diagnostic.orElseThrow().toString());
        finish(true, "Accepted compile, resolved import, interactive diagram, exports and rejected compile verified");
    }

    /** JavaScript can report renderer events and request exports; only Java chooses file destinations. */
    public final class Bridge {
        public boolean isProbe() { return report != null; }
        public void report(String kind, String message) {
            record(kind, message);
            diagramStatus.setText(message);
            if (kind.equals("HOST_READY")) Platform.runLater(CompilerGui.this::attachRenderer);
            if (report != null && (kind.equals("ERROR") || kind.equals("EXPORT_ERROR") || kind.equals("EARLY") && message.startsWith("ERROR:"))) {
                finish(false, kind + ": " + message);
            } else if (report != null && kind.equals("PROBE_RENDERED")) {
                Platform.runLater(() -> {
                    if (finished) return;
                    negativeProbe = true;
                    setInputs(List.of(probeInput("Broken.ili")));
                    compile.fire();
                });
            }
        }
        public void exportFile(String format, String payload) {
            try {
                if (!format.equals("SVG") && !format.equals("PNG")) throw new IllegalArgumentException("Unsupported export format");
                String name = "diagram." + format.toLowerCase(Locale.ROOT);
                byte[] bytes = format.equals("PNG") ? Base64.getDecoder().decode(payload) : payload.getBytes(StandardCharsets.UTF_8);
                if (report != null) {
                    if (format.equals("SVG") && !payload.contains("<svg")) throw new IOException("Invalid SVG export");
                    if (format.equals("PNG")) {
                        var image = new Image(new java.io.ByteArrayInputStream(bytes));
                        if (image.isError() || image.getWidth() < 1 || image.getHeight() < 1) throw new IOException("Invalid PNG export");
                    }
                    Files.write(report.resolveSibling(name), bytes);
                    record("EXPORT", name + " " + bytes.length + " bytes");
                    return;
                }
                var chooser = new FileChooser(); chooser.setInitialFileName(name);
                var target = chooser.showSaveDialog(stage);
                if (target != null) { Files.write(target.toPath(), bytes); diagramStatus.setText("Exported " + target.getName()); }
            } catch (Exception failure) { report("EXPORT_ERROR", failure.toString()); }
        }
    }
    private void record(String kind, String message) {
        String line = kind + ": " + message;
        if (report != null) events.append(line).append('\n');
        System.out.println(line);
    }
    private void finish(boolean success, String message) {
        if (finished) return; finished = true; passed = success;
        try { Files.writeString(report, "java=" + System.getProperty("java.runtime.version") + "\njavafx=" + System.getProperty("javafx.runtime.version")
                + "\nos=" + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
                + "\njava.home=" + System.getProperty("java.home") + "\nresult=" + (success ? "PASS" : "FAIL") + "\n" + message + "\n" + events);
        } catch (IOException failure) { passed = false; failure.printStackTrace(); }
        Platform.exit();
    }
    @Override public void stop() {
        if (active != null) active.cancel(true);
        if (server != null) server.close();
        if (webData != null) {
            try (var tree = Files.walk(webData)) {
                for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (IOException failure) { System.err.println("Cannot clean temporary WebView data: " + failure.getMessage()); }
        }
        if (report != null && !passed) System.exit(1);
    }
}
