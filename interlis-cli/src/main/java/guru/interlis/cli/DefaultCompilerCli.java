package guru.interlis.cli;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.format.FormatOptions;
import guru.interlis.format.FormatOutcome;
import guru.interlis.format.ModelFormatter;
import guru.interlis.format.ModelFormatters;
import guru.interlis.model.CompiledSchema;
import guru.interlis.projection.DiagramDocument;
import guru.interlis.projection.DiagramInput;
import guru.interlis.projection.DiagramJsonCodec;
import guru.interlis.projection.DiagramOptions;
import guru.interlis.projection.DiagramProjectionOutcome;
import guru.interlis.projection.DiagramProjectors;
import guru.interlis.projection.DiagramSelection;
import guru.interlis.projection.MermaidRenderer;
import guru.interlis.projection.PlantUmlRenderer;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.FileSourceLoader;
import guru.interlis.repository.LoadedSource;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.repository.SourceLocator;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.workspace.AnalysisRequest;
import guru.interlis.workspace.SourceChange;
import guru.interlis.workspace.SourceExpectation;
import guru.interlis.workspace.SourceUpdate;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceCompileRequest;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Default CLI implementation using the same CompilerEngine as the SDK. */
public final class DefaultCompilerCli implements CliApplication {
    @Override
    public CliExecutionResult run(List<String> arguments, CliEnvironment environment,
                                  CancellationToken token) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(token, "token");
        if (arguments.isEmpty()) {
            return usage(environment, "missing command");
        }
        String command = arguments.getFirst();
        try {
            return switch (command) {
                case "version" -> version(environment);
                case "capabilities" -> capabilities(environment);
                case "check", "compile" -> compile(arguments, environment, token);
                case "analyze" -> analyze(arguments, environment, token);
                case "diagram" -> diagram(arguments, environment, token);
                case "format" -> format(arguments, environment, token);
                default -> usage(environment, "unknown command: " + command);
            };
        } catch (OperationCancelledException cancelled) {
            return result(CliTermination.CANCELLED, List.of(),
                    "cancelled: " + cancelled.reason());
        } catch (SourceLoadException failure) {
            return result(CliTermination.USAGE_ERROR, List.of(),
                    "source load failed: " + failure.reason());
        } catch (CliUsageException usage) {
            return usage(environment, usage.getMessage());
        } catch (RuntimeException failure) {
            return result(CliTermination.INTERNAL_FAILURE, List.of(),
                    "internal failure: " + failure.getClass().getSimpleName());
        }
    }

    private CliExecutionResult version(CliEnvironment environment) {
        String output = "interlis-java 0.1.0-SNAPSHOT\n"
                + "language: INTERLIS 2.3, 2.4\n"
                + "java: " + Runtime.version() + "\n"
                + "grammar: antlr-4.13.2 / geostandards-f8f9723";
        environment.out().println(output);
        return result(CliTermination.SUCCESS, List.of(), output);
    }

    private CliExecutionResult capabilities(CliEnvironment environment) {
        String output = "{\"tool\":\"interlis-java\",\"version\":\"0.1.0-SNAPSHOT\","
                + "\"languages\":[\"2.3\",\"2.4\"],"
                + "\"features\":[\"MODEL\",\"TOPIC\",\"CLASS\",\"STRUCTURE\",\"DOMAIN\",\"FORMAT\","
                + "\"ATTRIBUTE\",\"INHERITANCE\",\"ASSOCIATION_ROLE\",\"MANDATORY_CONSTRAINT\","
                + "\"IMPORTS\",\"COORD\",\"MULTICOORD\"],"
                + "\"notImplemented\":[\"VIEW\",\"GRAPHIC\",\"FUNCTION\",\"UNIT\",\"CONTEXT\","
                + "\"METAOBJECT\",\"TRANSLATION\",\"DEFERRED_GENERICS\",\"OID\","
                + "\"BLACKBOX\",\"TEMPORAL\",\"ENUMTREEVAL\",\"EXTERNAL_CONSTRAINTS\"],"
                + "\"protocol\":\"interlis.compiler/1.0\"}";
        environment.out().println(output);
        return result(CliTermination.SUCCESS, List.of(), output);
    }

    private CliExecutionResult compile(List<String> args, CliEnvironment environment,
                                       CancellationToken token) {
        ParsedArguments parsed = parseFiles(args, false);
        if (parsed.files().isEmpty()) {
            return usage(environment, "no source files");
        }
        Loaded loaded = load(parsed.files(), environment, token);
        Workspace workspace = workspace(loaded);
        List<ModelSelector> roots = roots(parsed.roots(), loaded.sources());
        CompilationOutcome outcome = workspace.compile(new WorkspaceCompileRequest(
                workspace.currentRevision(), roots), token);
        CliTermination termination = new CliOutcomeMapper().classify(outcome);
        List<Diagnostic> diagnostics = diagnostics(outcome);
        String output = summary(outcome);
        print(parsed.json(), environment, diagnostics, output, loaded.sources());
        return result(termination, diagnostics, output);
    }

    private CliExecutionResult analyze(List<String> args, CliEnvironment environment,
                                       CancellationToken token) {
        ParsedArguments parsed = parseFiles(args, false);
        if (parsed.files().isEmpty()) {
            return usage(environment, "no source files");
        }
        Loaded loaded = load(parsed.files(), environment, token);
        Workspace workspace = workspace(loaded);
        var analysis = workspace.analyze(new AnalysisRequest(workspace.currentRevision(),
                roots(parsed.roots(), loaded.sources())), token);
        print(parsed.json(), environment, analysis.diagnostics(), "analyzed", loaded.sources());
        return result(CliTermination.SUCCESS, analysis.diagnostics(), "analyzed");
    }

    private CliExecutionResult diagram(List<String> args, CliEnvironment environment,
                                       CancellationToken token) {
        ParsedArguments parsed = parseFiles(args, true);
        if (parsed.files().isEmpty()) {
            return usage(environment, "no source files");
        }
        if (parsed.json()) {
            return usage(environment, "--json is not supported by diagram; use --format json");
        }
        Loaded loaded = load(parsed.files(), environment, token);
        Workspace workspace = workspace(loaded);
        List<ModelSelector> roots = roots(parsed.roots(), loaded.sources());
        CompilationOutcome outcome = workspace.compile(new WorkspaceCompileRequest(
                workspace.currentRevision(), roots), token);
        if (!(outcome instanceof CompilationOutcome.Succeeded succeeded)) {
            List<Diagnostic> diagnostics = diagnostics(outcome);
            print(true, environment, diagnostics, "diagram requires a checked schema",
                    loaded.sources());
            return result(CliTermination.SOURCE_REJECTED, diagnostics,
                    "diagram requires a checked schema");
        }
        CompiledSchema schema = succeeded.schema();
        DiagramSelection selection = new DiagramSelection(
                List.of(schema.rootModels().getFirst()));
        DiagramOptions options = DiagramOptions.defaults(selection);
        DiagramProjectionOutcome projection = DiagramProjectors.create()
                .project(new DiagramInput.FromSchema(schema), options, token);
        if (projection instanceof DiagramProjectionOutcome.Rejected rejected) {
            return result(CliTermination.OPERATIONAL_FAILURE, List.of(),
                    rejected.reasons().toString());
        }
        DiagramDocument document = ((DiagramProjectionOutcome.Produced) projection).document();
        String text = switch (parsed.format()) {
            case "mermaid" -> new MermaidRenderer().render(document);
            case "plantuml" -> new PlantUmlRenderer().render(document);
            case "json" -> new DiagramJsonCodec().encode(document);
            default -> throw new IllegalArgumentException("unknown format: " + parsed.format());
        };
        environment.out().println(text);
        return result(CliTermination.SUCCESS, List.of(), text);
    }

    private CliExecutionResult format(List<String> args, CliEnvironment environment,
                                      CancellationToken token) {
        FormatArguments parsed = parseFormat(args);
        if (parsed.error() != null) {
            return usage(environment, parsed.error());
        }
        if (parsed.files().isEmpty()) {
            return usage(environment, "no source files");
        }
        if (parsed.check() && parsed.write()) {
            return usage(environment, "--check and --write are mutually exclusive");
        }
        FileSourceLoader loader = new FileSourceLoader(environment.workingDirectory());
        Map<SourceId, SourceText> sources = new LinkedHashMap<>();
        List<FormatInput> inputs = new ArrayList<>();
        for (String file : parsed.files()) {
            LoadedSource loaded = loader.load(new SourceLocator(file), FetchPolicy.defaults(),
                    token);
            SourceText source = new SourceText(loaded.sourceId(), new SourceRevision(1),
                    loaded.text());
            sources.put(source.id(), source);
            inputs.add(new FormatInput(source, Path.of(file), environment.workingDirectory()));
        }
        ModelFormatter formatter = ModelFormatters.create();
        List<FormatOutput> outputs = new ArrayList<>();
        for (FormatInput input : inputs) {
            FormatOutcome outcome = formatter.format(input.source(), parsed.options(), token);
            if (outcome instanceof FormatOutcome.Rejected rejected) {
                ConsoleDiagnosticRenderer renderer = new ConsoleDiagnosticRenderer();
                for (Diagnostic diagnostic : rejected.diagnostics()) {
                    environment.err().println(renderer.render(diagnostic, sources));
                }
                return result(CliTermination.SOURCE_REJECTED, rejected.diagnostics(),
                        "format rejected");
            }
            if (outcome instanceof FormatOutcome.Cancelled cancelled) {
                return result(CliTermination.CANCELLED, List.of(),
                        "cancelled: " + cancelled.reason());
            }
            FormatOutcome.Formatted formatted = (FormatOutcome.Formatted) outcome;
            outputs.add(new FormatOutput(input, formatted.text()));
        }
        if (parsed.check()) {
            List<String> changed = outputs.stream()
                    .filter(output -> !output.text().equals(output.input().source().text()))
                    .map(output -> output.input().file().toString()).toList();
            if (!changed.isEmpty()) {
                changed.forEach(path -> environment.err().println("would format: " + path));
                return result(CliTermination.FORMAT_CHECK_FAILED, List.of(),
                        String.join("\n", changed));
            }
            return result(CliTermination.SUCCESS, List.of(), "");
        }
        if (parsed.write()) {
            try {
                for (FormatOutput output : outputs) {
                    writeAtomically(output.input().resolved(environment), output.text());
                }
            } catch (java.io.IOException failure) {
                return result(CliTermination.OPERATIONAL_FAILURE, List.of(),
                        "format write failed: " + failure.getMessage());
            }
            return result(CliTermination.SUCCESS, List.of(), "");
        }
        String text = outputs.stream().map(FormatOutput::text)
                .collect(java.util.stream.Collectors.joining());
        environment.out().print(text);
        return result(CliTermination.SUCCESS, List.of(), text);
    }

    private static void writeAtomically(Path target, String text) throws java.io.IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new java.io.IOException("target has no parent");
        }
        Path temp = Files.createTempFile(parent, ".interlis-format-", ".ili");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static FormatArguments parseFormat(List<String> args) {
        boolean check = false;
        boolean write = false;
        int indent = 2;
        FormatOptions.LineEnding lineEnding = FormatOptions.LineEnding.PRESERVE;
        List<String> files = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "--check" -> check = true;
                case "--write" -> write = true;
                case "--indent" -> {
                    if (i + 1 >= args.size()) {
                        return new FormatArguments(List.of(), check, write, indent, lineEnding,
                                "--indent needs a value");
                    }
                    try {
                        indent = Integer.parseInt(args.get(++i));
                    } catch (NumberFormatException failure) {
                        return new FormatArguments(List.of(), check, write, indent, lineEnding,
                                "invalid --indent value");
                    }
                    if (indent < 0 || indent > 16) {
                        return new FormatArguments(List.of(), check, write, indent, lineEnding,
                                "--indent must be between 0 and 16");
                    }
                }
                case "--line-ending" -> {
                    if (i + 1 >= args.size()) {
                        return new FormatArguments(List.of(), check, write, indent, lineEnding,
                                "--line-ending needs a value");
                    }
                    String value = args.get(++i);
                    lineEnding = switch (value) {
                        case "lf" -> FormatOptions.LineEnding.LF;
                        case "crlf" -> FormatOptions.LineEnding.CRLF;
                        default -> null;
                    };
                    if (lineEnding == null) {
                        return new FormatArguments(List.of(), check, write, indent,
                                FormatOptions.LineEnding.PRESERVE,
                                "--line-ending must be lf or crlf");
                    }
                }
                default -> {
                    if (arg.startsWith("-")) {
                        return new FormatArguments(List.of(), check, write, indent, lineEnding,
                                "unsupported option: " + arg);
                    }
                    files.add(arg);
                }
            }
        }
        FormatOptions options = new FormatOptions(indent, lineEnding, true);
        return new FormatArguments(List.copyOf(files), check, write, options, null);
    }

    private record FormatArguments(List<String> files, boolean check, boolean write,
                                   FormatOptions options, String error) {
        private FormatArguments(List<String> files, boolean check, boolean write,
                                int indent, FormatOptions.LineEnding lineEnding, String error) {
            this(files, check, write, new FormatOptions(indent, lineEnding, true), error);
        }
    }

    private record FormatInput(SourceText source, Path file, Path workingDirectory) {
        Path resolved(CliEnvironment environment) {
            return file.isAbsolute() ? file : environment.workingDirectory().resolve(file);
        }
    }

    private record FormatOutput(FormatInput input, String text) {
    }

    private static Workspace workspace(Loaded loaded) {
        Workspace workspace = Workspaces.create(CompilerEngines.create(),
                WorkspaceConfiguration.defaults());
        for (SourceText source : loaded.sources().values()) {
            workspace.update(new SourceUpdate(source.id(), new SourceExpectation.Absent(),
                    new SourceChange.Replace(source.text())));
        }
        return workspace;
    }

    private static ParsedArguments parseFiles(List<String> args, boolean allowFormat) {
        List<String> files = new ArrayList<>();
        List<String> roots = new ArrayList<>();
        boolean json = false;
        String format = "mermaid";
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "--json" -> json = true;
                case "--root" -> {
                    if (i + 1 >= args.size()) {
                        throw new CliUsageException("--root needs a value");
                    }
                    roots.add(args.get(++i));
                }
                case "--format" -> {
                    if (!allowFormat) {
                        throw new CliUsageException("--format is only supported by diagram");
                    }
                    if (i + 1 >= args.size()) {
                        throw new CliUsageException("--format needs a value");
                    }
                    format = args.get(++i);
                }
                default -> {
                    if (arg.startsWith("-")) {
                        throw new CliUsageException("unsupported option: " + arg);
                    }
                    files.add(arg);
                }
            }
        }
        return new ParsedArguments(files, roots, json, format);
    }

    private static List<ModelSelector> roots(List<String> roots,
                                             Map<SourceId, SourceText> sources) {
        if (!roots.isEmpty()) {
            return roots.stream().map(r -> (ModelSelector) new ModelSelector.ByName(r,
                    Optional.empty(), Optional.empty())).toList();
        }
        return sources.keySet().stream()
                .map(id -> (ModelSelector) new ModelSelector.BySource(id)).toList();
    }

    private static Loaded load(List<String> files, CliEnvironment environment,
                               CancellationToken token) {
        FileSourceLoader loader = new FileSourceLoader(environment.workingDirectory());
        Map<SourceId, SourceText> sources = new LinkedHashMap<>();
        for (String file : files) {
            LoadedSource loaded = loader.load(new SourceLocator(file), FetchPolicy.defaults(),
                    token);
            sources.put(loaded.sourceId(), new SourceText(loaded.sourceId(),
                    new SourceRevision(1), loaded.text()));
        }
        return new Loaded(sources);
    }

    private static List<Diagnostic> diagnostics(CompilationOutcome outcome) {
        return switch (outcome) {
            case CompilationOutcome.Succeeded succeeded -> succeeded.analysis().diagnostics();
            case CompilationOutcome.Rejected rejected -> rejected.analysis().diagnostics();
            case CompilationOutcome.Cancelled ignored -> List.of();
            case CompilationOutcome.Failed failed -> List.of(failed.error());
        };
    }

    private static String summary(CompilationOutcome outcome) {
        return switch (outcome) {
            case CompilationOutcome.Succeeded ignored -> "ok";
            case CompilationOutcome.Rejected rejected -> "rejected: " + rejected.rejection();
            case CompilationOutcome.Cancelled cancelled -> "cancelled: " + cancelled.reason();
            case CompilationOutcome.Failed failed -> "failed: " + failed.failureId().value();
        };
    }

    private static void print(boolean json, CliEnvironment environment,
                              List<Diagnostic> diagnostics, String summary,
                              Map<SourceId, SourceText> sources) {
        if (json) {
            environment.out().println("{\"status\":\"" + summary.replace("\"", "'")
                    + "\",\"diagnostics\":" + new JsonDiagnosticWriter().write(diagnostics) + "}");
        } else {
            ConsoleDiagnosticRenderer renderer = new ConsoleDiagnosticRenderer();
            for (Diagnostic diagnostic : diagnostics) {
                environment.err().println(renderer.render(diagnostic, sources));
            }
        }
    }

    private static CliExecutionResult usage(CliEnvironment environment, String message) {
        environment.err().println("usage: interlis-java "
                + "<version|capabilities|check|compile|analyze|diagram|format> [options] [files]");
        environment.err().println(message);
        return result(CliTermination.USAGE_ERROR, List.of(), message);
    }

    private static CliExecutionResult result(CliTermination termination,
                                             List<Diagnostic> diagnostics, String output) {
        return new CliExecutionResult(new ExitCodePolicy().exitCode(termination), termination,
                diagnostics, output);
    }

    private record ParsedArguments(List<String> files, List<String> roots, boolean json,
                                   String format) {
    }

    private record Loaded(Map<SourceId, SourceText> sources) {
    }
}
