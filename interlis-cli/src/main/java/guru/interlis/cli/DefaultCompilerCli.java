package guru.interlis.cli;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
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
                default -> usage(environment, "unknown command: " + command);
            };
        } catch (OperationCancelledException cancelled) {
            return result(CliTermination.CANCELLED, List.of(),
                    "cancelled: " + cancelled.reason());
        } catch (SourceLoadException failure) {
            return result(CliTermination.USAGE_ERROR, List.of(),
                    "source load failed: " + failure.reason());
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
                + "\"features\":[\"MODEL\",\"TOPIC\",\"CLASS\",\"STRUCTURE\",\"DOMAIN\","
                + "\"ATTRIBUTE\",\"INHERITANCE\",\"ASSOCIATION_ROLE\",\"MANDATORY_CONSTRAINT\","
                + "\"IMPORTS\",\"COORD\",\"MULTICOORD\"],"
                + "\"notImplemented\":[\"VIEW\",\"GRAPHIC\",\"FUNCTION\",\"UNIT\",\"CONTEXT\","
                + "\"METAOBJECT\",\"TRANSLATION\",\"DEFERRED_GENERICS\",\"FORMAT\",\"OID\","
                + "\"BLACKBOX\",\"TEMPORAL\",\"ENUMTREEVAL\",\"EXTERNAL_CONSTRAINTS\"],"
                + "\"protocol\":\"interlis.compiler/1.0\"}";
        environment.out().println(output);
        return result(CliTermination.SUCCESS, List.of(), output);
    }

    private CliExecutionResult compile(List<String> args, CliEnvironment environment,
                                       CancellationToken token) {
        ParsedArguments parsed = parseFiles(args);
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
        ParsedArguments parsed = parseFiles(args);
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
        ParsedArguments parsed = parseFiles(args);
        if (parsed.files().isEmpty()) {
            return usage(environment, "no source files");
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

    private static Workspace workspace(Loaded loaded) {
        Workspace workspace = Workspaces.create(CompilerEngines.create(),
                WorkspaceConfiguration.defaults());
        for (SourceText source : loaded.sources().values()) {
            workspace.update(new SourceUpdate(source.id(), new SourceExpectation.Absent(),
                    new SourceChange.Replace(source.text())));
        }
        return workspace;
    }

    private static ParsedArguments parseFiles(List<String> args) {
        List<String> files = new ArrayList<>();
        List<String> roots = new ArrayList<>();
        boolean json = false;
        String format = "mermaid";
        for (int i = 1; i < args.size(); i++) {
            switch (args.get(i)) {
                case "--json" -> json = true;
                case "--root" -> {
                    if (i + 1 >= args.size()) {
                        throw new IllegalArgumentException("--root needs a value");
                    }
                    roots.add(args.get(++i));
                }
                case "--format" -> {
                    if (i + 1 >= args.size()) {
                        throw new IllegalArgumentException("--format needs a value");
                    }
                    format = args.get(++i);
                }
                default -> files.add(args.get(i));
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
                + "<version|capabilities|check|compile|analyze|diagram> [options] [files]");
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
