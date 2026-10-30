package guru.interlis.cli;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.execution.RunContext;
import guru.interlis.execution.RunLifecycle;
import guru.interlis.execution.RunEvent;
import guru.interlis.execution.RunCompletion;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.FailureId;
import guru.interlis.format.FormatOptions;
import guru.interlis.format.FormatOutcome;
import guru.interlis.format.ModelFormatter;
import guru.interlis.format.ModelFormatters;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.VersionManifest;
import guru.interlis.projection.DiagramDocument;
import guru.interlis.projection.DiagramInput;
import guru.interlis.projection.DiagramJsonCodec;
import guru.interlis.projection.DiagramOptions;
import guru.interlis.projection.DiagramProjectionOutcome;
import guru.interlis.projection.DiagramProjectors;
import guru.interlis.projection.DiagramSelection;
import guru.interlis.projection.MermaidRenderer;
import guru.interlis.projection.PlantUmlRenderer;
import guru.interlis.repository.*;
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

/** Default CLI implementation using the same CompilerEngine as the SDK. */
public final class DefaultCompilerCli implements CliApplication {
    private final Optional<LegacyCliProfile> legacyProfile;
    private final CliOutputWriter fileWriter;

    /** Creates the new standard command profile. */
    public DefaultCompilerCli() { legacyProfile = Optional.empty(); fileWriter = new CliOutputWriter(); }

    DefaultCompilerCli(CliOutputWriter fileWriter) {
        legacyProfile = Optional.empty(); this.fileWriter = Objects.requireNonNull(fileWriter);
    }

    /** Creates an immutable checking-profile adapter; no run state is retained. */
    public DefaultCompilerCli(LegacyCliProfile profile) {
        legacyProfile = Optional.of(Objects.requireNonNull(profile, "profile"));
        fileWriter = new CliOutputWriter();
    }
    @Override
    public CliExecutionResult run(List<String> arguments, CliEnvironment environment,
                                  CancellationToken token) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(token, "token");
        var services = environment.services();
        var invocation = new Invocation(services.runContexts().createRoot());
        var observed = new guru.interlis.compiler.CompilerServices(event -> {
            services.observer().onEvent(event);
            if (invocation.trace && !(event instanceof RunEvent.Finished
                    && event.header().runId().equals(invocation.context.runId())))
                environment.err().println("Trace: " + event.getClass().getSimpleName()
                    + (event instanceof RunEvent.PhaseStarted phase ? " " + phase.phase() : ""));
        }, services.clock(), services.runContexts());
        var effective = new CliEnvironment(environment.workingDirectory(), environment.in(), environment.out(),
                environment.err(), environment.environment(), observed);
        var lifecycle = RunLifecycle.create(invocation.context, observed.observer(), services.clock());
        lifecycle.start(new RunEvent.RunMetadata("cli", List.of("2.3", "2.4"), 0,
                legacyProfile.map(LegacyCliProfile::id).orElse("cli-standard/v1")));
        CliExecutionResult outcome = execute(arguments, effective, token, invocation);
        if (invocation.legacyChecking && !invocation.quiet) {
            environment.err().println(outcome.termination() == CliTermination.SUCCESS
                    ? "Info: ...compiler run done" : "...compiler run failed");
        }
        try {
            environment.out().flush();
            environment.err().flush();
            if (environment.out().checkError() || environment.err().checkError()) {
                outcome = result(CliTermination.OPERATIONAL_FAILURE, outcome.diagnostics(), "output write failed");
            }
        } catch (RuntimeException failure) {
            outcome = result(CliTermination.OPERATIONAL_FAILURE, outcome.diagnostics(), "output flush failed");
        }
        lifecycle.finish(invocation.completion(outcome));
        return new CliExecutionResult(outcome.exitCode(), outcome.termination(), outcome.diagnostics(),
                outcome.output(), Optional.of(invocation.context.runId()), invocation.artifacts,
                Optional.ofNullable(invocation.rejection));
    }

    private CliExecutionResult execute(List<String> arguments, CliEnvironment environment,
                                       CancellationToken token, Invocation invocation) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(token, "token");
        try {
            if (legacyProfile.isPresent()) {
                var plan = new Ili2cInvocationPlanner().prepare(arguments, legacyProfile.orElseThrow(), environment, token);
                arguments = plan.arguments();
                invocation.rootOverride = plan.roots();
                invocation.quiet = plan.quiet();
                invocation.withoutWarnings = plan.withoutWarnings();
                invocation.trace = plan.trace();
                invocation.legacyChecking = plan.checking();
                if (plan.checking() && !plan.quiet())
                    environment.err().println("Info: interlis-java-" + VersionManifest.current().toolVersion());
            }
            if (arguments.isEmpty()) return usage(environment, "missing command");
            String command = arguments.getFirst();
            return switch (command) {
                case "help" -> help(arguments, environment);
                case "version" -> version(arguments, environment);
                case "capabilities" -> arguments.size() == 1 ? capabilities(environment)
                        : usage(environment, "capabilities accepts no arguments");
                case "check", "compile" -> compile(arguments, environment, token, invocation);
                case "analyze" -> analyze(arguments, environment, token, invocation);
                case "diagram" -> diagram(arguments, environment, token, invocation);
                case "format" -> format(arguments, environment, token, invocation);
                default -> usage(environment, "unknown command: " + command);
            };
        } catch (OperationCancelledException cancelled) {
            invocation.cancellation = cancelled.reason();
            environment.err().println("cancelled: " + cancelled.reason());
            return result(CliTermination.CANCELLED, List.of(),
                    "cancelled: " + cancelled.reason());
        } catch (SourceLoadException failure) {
            String message = "source load failed: " + failure.reason();
            environment.err().println((legacyProfile.isPresent() ? "Error: " : "") + message);
            return result(CliTermination.OPERATIONAL_FAILURE, List.of(), message);
        } catch (CliUsageException usage) {
            return usage(environment, usage.getMessage());
        } catch (RuntimeException failure) {
            invocation.failureId = new FailureId("cli-" + java.util.UUID.randomUUID());
            String message = "internal failure: " + invocation.failureId.value();
            environment.err().println(message);
            return result(CliTermination.INTERNAL_FAILURE, List.of(), message);
        }
    }

    private CliExecutionResult help(List<String> arguments, CliEnvironment environment) {
        parseCommand(arguments);
        if (legacyProfile.isPresent()) {
            var profile = legacyProfile.orElseThrow();
            var text = new StringBuilder(new ToolVersionRenderer().render(VersionManifest.current(), profile));
            text.append("USAGE\n  ili2c [options] file1.ili file2.ili ...\nOPTIONS\n");
            for (var option : profile.commandSpec().options())
                text.append("  ").append(option.name()).append(option.valueCount() > 0 ? " VALUE" : "").append('\n');
            text.append("Unsupported legacy options are rejected explicitly:\n");
            profile.unsupportedOptions().forEach((name, reason) -> text.append("  ").append(name).append(": ").append(reason).append('\n'));
            environment.err().print(text);
            return result(CliTermination.SUCCESS, List.of(), text.toString());
        }
        var help = new StringBuilder("interlis-java ")
                .append(VersionManifest.current().toolVersion()).append("\n");
        for (String command : List.of("help", "version", "capabilities", "check", "compile", "analyze", "diagram", "format")) {
            var specification = CommandSpec.standard(command);
            help.append(command);
            for (var option : specification.options()) {
                help.append(" [").append(option.name());
                for (int count = 0; count < option.valueCount(); count++) help.append(" VALUE");
                help.append(option.repeatable() ? "]..." : "]");
            }
            if (specification.acceptsFiles()) help.append(" [--] [files...]");
            help.append('\n');
        }
        environment.out().print(help);
        return result(CliTermination.SUCCESS, List.of(), help.toString());
    }

    private CliExecutionResult version(List<String> arguments, CliEnvironment environment) {
        VersionManifest manifest = VersionManifest.current();
        if (legacyProfile.isPresent()) {
            String text = new ToolVersionRenderer().render(manifest, legacyProfile.orElseThrow());
            environment.err().print(text);
            return result(CliTermination.SUCCESS, List.of(), text);
        }
        if (arguments.size() > 1) {
            if (arguments.size() == 2 && arguments.get(1).equals("--json")) {
                String json = "{\"tool\":\"interlis-java\",\"version\":\""
                        + manifest.toolVersion() + "\",\"apiVersion\":\""
                        + manifest.apiVersion() + "\",\"diagnosticCatalogVersion\":\""
                        + manifest.diagnosticCatalogVersion() + "\",\"protocol\":\""
                        + manifest.protocolIdentifier() + "\",\"languageProfiles\":\""
                        + manifest.languageProfiles() + "\",\"formatVersion\":\""
                        + manifest.formatVersion() + "\",\"diagramSchemaVersion\":\""
                        + manifest.diagramSchemaVersion() + "\"}";
                environment.out().println(json);
                return result(CliTermination.SUCCESS, List.of(), json);
            }
            return usage(environment, "version accepts only --json");
        }
        String output = "interlis-java " + manifest.toolVersion() + "\n"
                + "api: " + manifest.apiVersion() + "\n"
                + "diagnostics: " + manifest.diagnosticCatalogVersion() + "\n"
                + "protocol: " + manifest.protocolIdentifier() + "\n"
                + "language: INTERLIS " + manifest.languageProfiles() + "\n"
                + "format: " + manifest.formatVersion() + "\n"
                + "diagram: " + manifest.diagramSchemaVersion() + "\n"
                + "java: " + Runtime.version() + "\n"
                + "grammar: antlr-4.13.2 / geostandards-f8f9723";
        environment.out().println(output);
        return result(CliTermination.SUCCESS, List.of(), output);
    }

    private CliExecutionResult capabilities(CliEnvironment environment) {
        VersionManifest manifest = VersionManifest.current();
        String output = "{\"tool\":\"interlis-java\",\"version\":\"" + manifest.toolVersion() + "\","
                + "\"apiVersion\":\"" + manifest.apiVersion() + "\","
                + "\"diagnosticCatalogVersion\":\"" + manifest.diagnosticCatalogVersion() + "\","
                + "\"formatVersion\":\"" + manifest.formatVersion() + "\","
                + "\"diagramSchemaVersion\":\"" + manifest.diagramSchemaVersion() + "\","
                + "\"languageProfiles\":[\"2.3\",\"2.4\"],"
                + "\"languages\":[\"2.3\",\"2.4\"],"
                + "\"features\":[\"MODEL\",\"TOPIC\",\"CLASS\",\"STRUCTURE\",\"DOMAIN\",\"FORMAT\","
                + "\"ATTRIBUTE\",\"INHERITANCE\",\"ASSOCIATION_ROLE\",\"MANDATORY_CONSTRAINT\","
                + "\"IMPORTS\",\"COORD\",\"MULTICOORD\",\"FUNCTION\",\"TRANSLATION\","
                + "\"DEFERRED_GENERICS\",\"VIEW_SCHEMA\",\"GRAPHIC\",\"CONTEXT\","
                + "\"METADATA_BASKET\",\"RUNTIME_PARAMETER\",\"LINE_FORM\","
                + "\"FUNCTION_ARGUMENT_TYPE\",\"BUILTIN_SIGNATURE\"],"
                + "\"qualification\":\"P3_OPEN\","
                + "\"partialFeatures\":[],\"notImplemented\":[],"
                + "\"protocol\":\"" + manifest.protocolIdentifier() + "\"}";
        environment.out().println(output);
        return result(CliTermination.SUCCESS, List.of(), output);
    }

    private CliExecutionResult compile(List<String> args, CliEnvironment environment,
                                       CancellationToken token, Invocation invocation) {
        ParsedCommand supplied = parseCommand(args);
        var loader = new ConfigurationLoader();
        var configuration = loader.load(supplied, environment, token);
        var warningPolicy = switch (configuration.command().value("--warnings").orElseThrow()) {
            case "allow" -> guru.interlis.compiler.WarningPolicy.ALLOW_WARNINGS;
            case "reject" -> guru.interlis.compiler.WarningPolicy.REJECT_WARNINGS;
            default -> throw new CliUsageException("--warnings must be allow or reject");
        };
        ParsedArguments parsed = parseFiles(configuration.command());
        new OutputPlanner().plan(configuration.command(), environment, legacyProfile);
        if (supplied.has("--explain-config")) {
            String explanation = loader.explain(configuration);
            environment.out().println(explanation);
            return result(CliTermination.SUCCESS, List.of(), explanation);
        }
        if (parsed.files().isEmpty() && parsed.roots().isEmpty() && invocation.rootOverride.isEmpty())
            return usage(environment, "no source files or root models");
        Loaded loaded = load(parsed.files(), environment, token);
        try (Workspace workspace = workspace(loaded, environment, warningPolicy)) {
            List<ModelSelector> roots = invocation.rootOverride.isEmpty()
                    ? roots(parsed.roots(), loaded.sources()) : invocation.rootOverride;
            PreparedRepositoryUpdate update = null;
            if (!parsed.repositories().isEmpty()) {
                var sources = new ArrayList<RepositorySource>();
                for (String value : parsed.repositories()) {
                    String base = sourceUri(value, environment);
                    if (!base.endsWith("/")) base += "/";
                    sources.add(new RepositorySource(new RepositoryBase(java.net.URI.create(base)), new SourceLocator(base + "ilimodels.xml")));
                }
                if (parsed.writeLock().isPresent()) {
                    String target = hostPath(parsed.writeLock().orElseThrow(), environment).toUri().toString();
                    if (loaded.sources().keySet().stream().anyMatch(id -> id.value().equals(target))
                            || sources.stream().anyMatch(source -> {
                                var uri = java.net.URI.create(source.indexLocator().location());
                                return source.indexLocator().location().equals(target)
                                        || ("file".equalsIgnoreCase(uri.getScheme())
                                        && OutputPlanner.sameFile(Path.of(uri), hostPath(parsed.writeLock().orElseThrow(), environment)));
                            }))
                        throw new CliUsageException("lock output collides with a source or repository index");
                }
                var fetch = FetchPolicy.defaults();
                var config = new RepositoryConfig(sources, new FetchPolicy(parsed.offline(), fetch.maxBytes(), fetch.timeout()));
                Optional<ResolutionLock> lock = Optional.empty();
                if (parsed.readLock().isPresent()) {
                    try (var input = Files.newInputStream(hostPath(parsed.readLock().orElseThrow(), environment))) {
                        byte[] bytes = input.readNBytes(8_388_609);
                        if (bytes.length > 8_388_608) throw new CliUsageException("lock exceeds byte limit");
                        lock = Optional.of(new ResolutionLockCodec().decode(new String(bytes, StandardCharsets.UTF_8)));
                    } catch (java.io.IOException unavailable) { return result(CliTermination.OPERATIONAL_FAILURE, List.of(), "lock could not be read"); }
                    catch (IllegalArgumentException invalid) { throw new CliUsageException("invalid resolution lock"); }
                }
                var contentStore = parsed.cache().<ContentStore>map(path -> new DirectoryContentStore(hostPath(path, environment)))
                        .orElseGet(InMemoryContentStore::new);
                var outcome = new RepositoryClosureService(new RoutingSourceLoader(environment.workingDirectory()), contentStore)
                        .resolve(workspace, new AnalysisRequest(workspace.currentRevision(), roots),
                                new RepositoryResolveOptions(config, parsed.modelPins(), parsed.sourcePins(), lock), token);
                switch (outcome) {
                    case RepositoryResolutionOutcome.Prepared prepared -> { update = prepared.update(); update.apply(workspace); }
                    case RepositoryResolutionOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
                    case RepositoryResolutionOutcome.Stale ignored -> { return result(CliTermination.INCOMPLETE_ANALYSIS, List.of(), "workspace changed during resolution"); }
                    case RepositoryResolutionOutcome.Rejected rejected -> {
                        var problem = rejected.problems().getFirst();
                        String reason = problem.reason() + problem.loadReason().map(value -> "/" + value).orElse("");
                        String output = "resolution rejected: " + reason;
                        print(parsed.json(), environment, List.of(), output, loaded.sources(), invocation);
                        if (!parsed.json() && legacyProfile.isEmpty()) environment.err().println(output);
                        return result(problem.reason() == RepositoryProblem.Reason.LOAD_FAILURE || problem.reason() == RepositoryProblem.Reason.INDEX_FAILURE
                                ? CliTermination.OPERATIONAL_FAILURE : CliTermination.INPUT_UNRESOLVED, List.of(), output);
                    }
                }
            }
            invocation.rootCount = roots.size();
            CompilationOutcome outcome = workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(), roots),
                    environment.services().runContexts().createChild(invocation.context), token);
            invocation.compiled(outcome);
            if (outcome instanceof CompilationOutcome.Succeeded && update != null && parsed.writeLock().isPresent()) {
                Path target = hostPath(parsed.writeLock().orElseThrow(), environment);
                if (workspace.state().sourceRevisions().keySet().stream()
                        .map(id -> java.net.URI.create(id.value())).filter(uri -> "file".equalsIgnoreCase(uri.getScheme()))
                        .map(Path::of).anyMatch(source -> OutputPlanner.sameFile(source, target)))
                    throw new CliUsageException("lock output collides with a resolved model source");
                try {
                    byte[] bytes = new ResolutionLockCodec().encode(update.lock()).getBytes(StandardCharsets.UTF_8);
                    fileWriter.write(target, bytes, Optional.empty(), token);
                    invocation.artifacts.add(new CliExecutionResult.Artifact(CliExecutionResult.ArtifactKind.RESOLUTION_LOCK,
                            target.toUri(), bytes.length));
                } catch (java.io.IOException unavailable) {
                    environment.err().println("lock could not be written");
                    return result(CliTermination.OPERATIONAL_FAILURE, List.of(), "lock could not be written");
                }
            }
            CliTermination termination = new CliOutcomeMapper().classify(outcome);
            List<Diagnostic> diagnostics = diagnostics(outcome);
            var texts = new LinkedHashMap<SourceId, SourceText>();
            workspace.state().sourceRevisions().keySet().forEach(id -> workspace.source(id).ifPresent(text -> texts.put(id, text)));
            String output = summary(outcome);
            print(parsed.json(), environment, diagnostics, output, texts, invocation);
            return result(termination, diagnostics, output);
        }
    }

    private static Path hostPath(String value, CliEnvironment environment) {
        Path path = value.startsWith("file:") ? Path.of(java.net.URI.create(value)) : Path.of(value);
        return (path.isAbsolute() ? path : environment.workingDirectory().resolve(path)).toAbsolutePath().normalize();
    }
    private static String sourceUri(String value, CliEnvironment environment) {
        try {
            var uri = java.net.URI.create(value);
            if (uri.isAbsolute()) return "file".equalsIgnoreCase(uri.getScheme())
                    ? Path.of(uri).toAbsolutePath().normalize().toUri().toString() : uri.toString();
        } catch (IllegalArgumentException ignored) { /* a local path may contain spaces */ }
        return hostPath(value, environment).toUri().toString();
    }

    private CliExecutionResult analyze(List<String> args, CliEnvironment environment,
                                       CancellationToken token, Invocation invocation) {
        ParsedArguments parsed = parseFiles(args, false);
        if (parsed.files().isEmpty()) {
            return usage(environment, "no source files");
        }
        Loaded loaded = load(parsed.files(), environment, token);
        try (Workspace workspace = workspace(loaded, environment)) {
            invocation.rootCount = roots(parsed.roots(), loaded.sources()).size();
            var analysis = workspace.analyze(new AnalysisRequest(workspace.currentRevision(),
                    roots(parsed.roots(), loaded.sources())),
                    environment.services().runContexts().createChild(invocation.context), token);
            invocation.analysisComplete = !analysis.coverage().entries().isEmpty()
                    && analysis.coverage().incompleteEntries().isEmpty();
            print(parsed.json(), environment, analysis.diagnostics(), "analyzed", loaded.sources(), invocation);
            return result(CliTermination.SUCCESS, analysis.diagnostics(), "analyzed");
        }
    }

    private CliExecutionResult diagram(List<String> args, CliEnvironment environment,
                                       CancellationToken token, Invocation invocation) {
        ParsedArguments parsed = parseFiles(args, true);
        if (parsed.files().isEmpty()) {
            return usage(environment, "no source files");
        }
        if (parsed.json()) {
            return usage(environment, "--json is not supported by diagram; use --format json");
        }
        Loaded loaded = load(parsed.files(), environment, token);
        try (Workspace workspace = workspace(loaded, environment)) {
        List<ModelSelector> roots = roots(parsed.roots(), loaded.sources());
        invocation.rootCount = roots.size();
        CompilationOutcome outcome = workspace.compile(new WorkspaceCompileRequest(
                workspace.currentRevision(), roots),
                environment.services().runContexts().createChild(invocation.context), token);
        invocation.compiled(outcome);
        if (!(outcome instanceof CompilationOutcome.Succeeded succeeded)) {
            List<Diagnostic> diagnostics = diagnostics(outcome);
            print(true, environment, diagnostics, "diagram requires a checked schema",
                    loaded.sources(), invocation);
            return result(new CliOutcomeMapper().classify(outcome), diagnostics,
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
    }

    private CliExecutionResult format(List<String> args, CliEnvironment environment,
                                      CancellationToken token, Invocation invocation) {
        FormatArguments parsed = parseFormat(args);
        new OutputPlanner().plan(parseCommand(args), environment, legacyProfile);
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
            if (sources.putIfAbsent(source.id(), source) != null) {
                return usage(environment, "duplicate formatting source");
            }
            inputs.add(new FormatInput(source, Path.of(java.net.URI.create(loaded.sourceId().value())), environment.workingDirectory()));
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
                invocation.cancellation = cancelled.reason();
                return result(CliTermination.CANCELLED, List.of(),
                        "cancelled: " + cancelled.reason());
            }
            FormatOutcome.Formatted formatted = (FormatOutcome.Formatted) outcome;
            for (var notice : formatted.notices()) {
                environment.err().println(notice.code() + ": " + notice.message());
            }
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
                    byte[] bytes = output.text().getBytes(StandardCharsets.UTF_8);
                    Path target = output.input().resolved(environment);
                    fileWriter.write(target, bytes,
                            Optional.of(output.input().source().text().getBytes(StandardCharsets.UTF_8)), token);
                    invocation.artifacts.add(new CliExecutionResult.Artifact(CliExecutionResult.ArtifactKind.FORMATTED_SOURCE,
                            target.toAbsolutePath().normalize().toUri(), bytes.length));
                }
            } catch (java.io.IOException failure) {
                String message = "format write failed: " + failure.getMessage();
                environment.err().println(message);
                return result(CliTermination.OPERATIONAL_FAILURE, List.of(), message);
            }
            return result(CliTermination.SUCCESS, List.of(), "");
        }
        String text = outputs.stream().map(FormatOutput::text)
                .collect(java.util.stream.Collectors.joining());
        environment.out().print(text);
        return result(CliTermination.SUCCESS, List.of(), text);
    }

    private static FormatArguments parseFormat(List<String> args) {
        ParsedCommand command = parseCommand(args);
        int indent;
        try { indent = Integer.parseInt(command.value("--indent").orElse("2")); }
        catch (NumberFormatException invalid) { throw new CliUsageException("invalid --indent value"); }
        if (indent < 0 || indent > 16) throw new CliUsageException("--indent must be between 0 and 16");
        var ending = command.value("--line-ending").map(value -> switch (value) {
            case "lf" -> FormatOptions.LineEnding.LF;
            case "crlf" -> FormatOptions.LineEnding.CRLF;
            default -> throw new CliUsageException("--line-ending must be lf or crlf");
        }).orElse(FormatOptions.LineEnding.PRESERVE);
        return new FormatArguments(command.files(), command.has("--check"), command.has("--write"),
                new FormatOptions(indent, ending, true), null);
    }

    private static ParsedCommand parseCommand(List<String> args) {
        return switch (new DefaultCommandLineParser().parse(args, CommandSpec.standard(args.getFirst()))) {
            case ParsedCommand parsed -> parsed;
            case CliParseOutcome.HelpRequested requested -> requested.command();
            case CliParseOutcome.VersionRequested requested -> requested.command();
            case CliParseOutcome.InvalidCommandLine invalid -> throw new CliUsageException(invalid.message());
        };
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

    private static Workspace workspace(Loaded loaded, CliEnvironment environment) {
        return workspace(loaded, environment, guru.interlis.compiler.WarningPolicy.ALLOW_WARNINGS);
    }

    private static Workspace workspace(Loaded loaded, CliEnvironment environment,
                                       guru.interlis.compiler.WarningPolicy warningPolicy) {
        var defaults = WorkspaceConfiguration.defaults();
        var options = defaults.compilerOptions();
        var configuration = new WorkspaceConfiguration(new guru.interlis.compiler.CompilerOptions(
                options.semanticProfile(), options.allowedLanguages(), warningPolicy,
                options.budget(), options.overlays()), defaults.resolutionPolicy(),
                defaults.resolverCatalog(), defaults.cacheLimits());
        Workspace workspace = Workspaces.create(CompilerEngines.create(environment.services()),
                configuration);
        for (SourceText source : loaded.sources().values()) {
            workspace.update(new SourceUpdate(source.id(), new SourceExpectation.Absent(),
                    new SourceChange.Replace(source.text())));
        }
        return workspace;
    }

    private static ParsedArguments parseFiles(List<String> args, boolean allowFormat) {
        return parseFiles(parseCommand(args));
    }

    private static ParsedArguments parseFiles(ParsedCommand command) {
        if (command.value("--format").filter(value -> !java.util.Set.of("mermaid", "plantuml", "json").contains(value)).isPresent())
            throw new CliUsageException("--format must be mermaid, plantuml or json");
        var modelPins = new LinkedHashMap<String, ModelPin>();
        for (var values : command.occurrences("--model-pin")) {
            try {
                var pin = new ModelPin(values.get(0), values.get(1), new SourceId(values.get(2)),
                        new ContentDigest("sha256", values.get(3)));
                if (modelPins.putIfAbsent(pin.modelName(), pin) != null) throw new CliUsageException("duplicate model pin");
            } catch (IllegalArgumentException invalid) { throw new CliUsageException("invalid model pin"); }
        }
        var sourcePins = new LinkedHashMap<SourceId, ContentDigest>();
        for (var values : command.occurrences("--source-pin")) {
            try {
                var source = new SourceId(values.get(0));
                if (sourcePins.putIfAbsent(source, new ContentDigest("sha256", values.get(1))) != null)
                    throw new CliUsageException("duplicate source pin");
            } catch (IllegalArgumentException invalid) { throw new CliUsageException("invalid source pin"); }
        }
        boolean hostOptions = command.has("--repository") || command.has("--offline")
                || command.has("--read-lock") || command.has("--write-lock") || command.has("--cache")
                || !modelPins.isEmpty() || !sourcePins.isEmpty();
        if (hostOptions && !command.has("--repository")) throw new CliUsageException("repository options require --repository");
        return new ParsedArguments(command.files(), command.values("--root"), command.has("--json"),
                command.value("--format").orElse("mermaid"), command.values("--repository"),
                command.has("--offline"), Map.copyOf(modelPins), Map.copyOf(sourcePins),
                command.value("--read-lock"), command.value("--write-lock"), command.value("--cache"));
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

    private void print(boolean json, CliEnvironment environment,
                              List<Diagnostic> diagnostics, String summary,
                              Map<SourceId, SourceText> sources, Invocation invocation) {
        if (legacyProfile.isPresent()) {
            var renderer = new LegacyDiagnosticRenderer();
            for (var diagnostic : diagnostics) {
                if (invocation.withoutWarnings && diagnostic.severity() == guru.interlis.diagnostics.Severity.WARNING) continue;
                environment.err().println(renderer.render(diagnostic, sources));
            }
            if (diagnostics.isEmpty() && !summary.equals("ok")) environment.err().println("Error: " + summary);
            return;
        }
        if (json) {
            environment.out().println("{\"status\":\"" + JsonDiagnosticWriter.escape(summary)
                    + "\",\"diagnostics\":" + new JsonDiagnosticWriter().write(diagnostics) + "}");
        } else {
            ConsoleDiagnosticRenderer renderer = new ConsoleDiagnosticRenderer();
            for (Diagnostic diagnostic : diagnostics) {
                environment.err().println(renderer.render(diagnostic, sources));
            }
        }
    }

    private CliExecutionResult usage(CliEnvironment environment, String message) {
        if (legacyProfile.isPresent()) {
            environment.err().println("ili2c: " + message);
            return result(CliTermination.USAGE_ERROR, List.of(), message);
        }
        environment.err().println("usage: interlis-java "
                + "<version|capabilities|check|compile|analyze|diagram|format> [options] [files]");
        environment.err().println("check/compile: --repository DIR_OR_URI --root MODEL [--model-pin NAME VERSION SOURCE SHA256]");
        environment.err().println("               [--source-pin SOURCE SHA256] [--read-lock FILE] [--write-lock FILE] [--cache DIR] [--offline]");
        environment.err().println(message);
        return result(CliTermination.USAGE_ERROR, List.of(), message);
    }

    private CliExecutionResult result(CliTermination termination,
                                             List<Diagnostic> diagnostics, String output) {
        int exit = legacyProfile.map(profile -> new LegacyExitCodePolicy().exitCode(termination, profile))
                .orElseGet(() -> new ExitCodePolicy().exitCode(termination));
        return new CliExecutionResult(exit, termination,
                diagnostics, output);
    }

    private record ParsedArguments(List<String> files, List<String> roots, boolean json, String format,
                                   List<String> repositories, boolean offline, Map<String, ModelPin> modelPins,
                                   Map<SourceId, ContentDigest> sourcePins, Optional<String> readLock,
                                   Optional<String> writeLock, Optional<String> cache) {
    }

    private record Loaded(Map<SourceId, SourceText> sources) {
    }

    /** Mutable only for one invocation; never retained on the reusable CLI instance. */
    private static final class Invocation {
        final RunContext context;
        boolean schemaProduced;
        boolean analysisComplete;
        final List<CliExecutionResult.Artifact> artifacts = new ArrayList<>();
        boolean quiet, withoutWarnings, trace, legacyChecking;
        List<ModelSelector> rootOverride = List.of();
        int rootCount;
        CancellationReason cancellation = CancellationReason.USER_REQUESTED;
        FailureId failureId;
        guru.interlis.compiler.RejectionSummary rejection;

        Invocation(RunContext context) { this.context = context; }

        void compiled(CompilationOutcome outcome) {
            switch (outcome) {
                case CompilationOutcome.Succeeded ignored -> { schemaProduced = true; analysisComplete = true; }
                case CompilationOutcome.Cancelled value -> cancellation = value.reason();
                case CompilationOutcome.Failed value -> failureId = value.failureId();
                case CompilationOutcome.Rejected value -> rejection = value.rejection();
            }
        }

        RunCompletion completion(CliExecutionResult result) {
            int errors = (int) result.diagnostics().stream()
                    .filter(d -> d.severity() == guru.interlis.diagnostics.Severity.ERROR).count();
            return switch (result.termination()) {
                case SUCCESS -> new RunCompletion.Succeeded(errors, rootCount, analysisComplete, schemaProduced);
                case CANCELLED -> new RunCompletion.Cancelled(cancellation, Optional.empty(), errors);
                case INTERNAL_FAILURE -> new RunCompletion.Failed(
                        failureId != null ? failureId : new FailureId("cli-" + context.runId().value()), errors);
                default -> {
                    boolean gateRejection = rejection != null
                            && result.termination() != CliTermination.OPERATIONAL_FAILURE;
                    yield new RunCompletion.Rejected(gateRejection
                            ? rejection.reasons().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet())
                            : java.util.Set.of(result.termination().name()),
                            gateRejection ? rejection.diagnosticIds().stream().map(id -> id.value()).toList()
                                    : result.diagnostics().stream().map(d -> d.id().value()).toList(),
                            gateRejection ? rejection.unsupportedFeatureIds() : List.of(), errors, false);
                }
            };
        }
    }
}
