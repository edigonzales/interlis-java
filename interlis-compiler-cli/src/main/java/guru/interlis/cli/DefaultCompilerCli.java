package guru.interlis.cli;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticPresentation;
import guru.interlis.diagnostics.DiagnosticDisplayPolicy;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.Severity;
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
import guru.interlis.model.VersionManifest;
import guru.interlis.projection.AssociationPresentation;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Default CLI implementation using the same CompilerEngine as the SDK. */
public final class DefaultCompilerCli implements CliApplication {
    private static final DateTimeFormatter RUN_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private final CliOutputWriter fileWriter;
    private final ModelFormatter formatter;

    /** Creates the new standard command profile. */
    public DefaultCompilerCli() { this(new CliOutputWriter()); }

    DefaultCompilerCli(CliOutputWriter fileWriter) {
        this(fileWriter, ModelFormatters.create());
    }

    DefaultCompilerCli(CliOutputWriter fileWriter, ModelFormatter formatter) {
        this.fileWriter = Objects.requireNonNull(fileWriter);
        this.formatter = Objects.requireNonNull(formatter);
    }

    @Override
    public CliExecutionResult run(List<String> arguments, CliEnvironment environment,
                                  CancellationToken token) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(token, "token");
        var services = environment.services();
        var invocation = new Invocation(services.runContexts().createRoot());
        var observed = services;
        var effective = new CliEnvironment(environment.workingDirectory(), environment.in(), environment.out(),
                environment.err(), environment.environment(), observed, environment.terminalCapabilities(), environment.homeDirectory());
        var lifecycle = RunLifecycle.create(invocation.context, observed.observer(), services.clock());
        invocation.lifecycle = lifecycle;
        lifecycle.start(new RunEvent.RunMetadata("cli", List.of("2.3", "2.4"), 0,
                "cli-standard/v1"));
        CliExecutionResult outcome = execute(arguments, effective, token, invocation);
        try {
            environment.out().flush();
            environment.err().flush();
            if (environment.out().checkError() || environment.err().checkError()) {
                outcome = result(CliTermination.OPERATIONAL_FAILURE, outcome.diagnostics(), "output write failed");
            }
            if (invocation.runBannerStarted) {
                var timestamp = RUN_TIMESTAMP.withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(services.clock().wallTimeEpochMillis()));
                Severity severity = switch (outcome.termination()) {
                    case SUCCESS, CANCELLED -> Severity.INFO;
                    default -> Severity.ERROR;
                };
                environment.err().println(ConsoleDiagnosticRenderer.renderLog(severity,
                        runEndMessage(outcome.termination(), invocation.language) + " " + timestamp, invocation.ansiEnabled));
                if (environment.err().checkError()) {
                    outcome = result(CliTermination.OPERATIONAL_FAILURE, outcome.diagnostics(), "output write failed");
                }
            }
        } catch (RuntimeException failure) {
            outcome = result(CliTermination.OPERATIONAL_FAILURE, outcome.diagnostics(), "output flush failed");
        }
        lifecycle.finish(invocation.completion(outcome));
        return new CliExecutionResult(outcome.exitCode(), outcome.termination(), outcome.diagnostics(),
                outcome.output(), Optional.of(invocation.context.runId()), invocation.artifacts,
                Optional.ofNullable(invocation.rejection),Optional.ofNullable(invocation.resolutionLock),invocation.repositorySteps);
    }

    private CliExecutionResult execute(List<String> arguments, CliEnvironment environment,
                                       CancellationToken token, Invocation invocation) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(token, "token");
        try {
            if (arguments.isEmpty()) return usage(environment, "missing command");
            String command = arguments.getFirst();
            if (arguments.size() == 1) command = switch (command) {
                case "--help", "-h" -> "help";
                case "--version" -> "version";
                default -> command;
            };
            if (!command.equals(arguments.getFirst())) arguments = List.of(command);
            if (java.util.Set.of("check", "compile", "analyze", "diagram", "format").contains(command)) {
                invocation.command = parseCommand(arguments);
                ColorMode colorMode = switch (invocation.command.value("--color").orElse("auto")) {
                    case "always" -> ColorMode.ALWAYS;
                    case "never" -> ColorMode.NEVER;
                    default -> ColorMode.AUTO;
                };
                invocation.ansiEnabled = new ColorPolicy().useAnsi(colorMode, environment.terminalCapabilities(),
                        environment.environment(), invocation.command.has("--json"));
                invocation.language = switch (invocation.command.value("--diagnostic-language").orElse("en")) {
                    case "en" -> DiagnosticLanguage.EN;
                    case "de" -> DiagnosticLanguage.DE;
                    default -> throw new CliUsageException("--diagnostic-language must be de or en");
                };
            }
            return switch (command) {
                case "help" -> help(arguments, environment);
                case "version" -> version(arguments, environment);
                case "capabilities" -> arguments.size() == 1 ? capabilities(environment)
                        : usage(environment, "capabilities accepts no arguments");
                case "check", "compile", "analyze", "diagram" -> semantic(environment, token, invocation);
                case "format" -> format(environment, token, invocation);
                default -> usage(environment, "unknown command: " + command);
            };
        } catch (OperationCancelledException cancelled) {
            invocation.cancellation = cancelled.reason();
            environment.err().println("cancelled: " + cancelled.reason());
            return result(CliTermination.CANCELLED, List.of(),
                    "cancelled: " + cancelled.reason());
        } catch (SourceLoadException failure) {
            String reason = switch (failure.reason()) {
                case NOT_FOUND -> invocation.language == DiagnosticLanguage.DE ? "Quelle nicht gefunden" : "source not found";
                case ACCESS_DENIED -> invocation.language == DiagnosticLanguage.DE ? "Zugriff auf die Quelle verweigert" : "access to the source was denied";
                default -> invocation.language == DiagnosticLanguage.DE ? "Quelle konnte nicht gelesen werden" : "source could not be read";
            };
            String message = invocation.language == DiagnosticLanguage.DE ? "Laden fehlgeschlagen: " + reason : "Source loading failed: " + reason;
            environment.err().println(guru.interlis.diagnostics.DiagnosticMessageFormatter.safeText(message));
            return result(CliTermination.OPERATIONAL_FAILURE, List.of(), message);
        } catch (CliUsageException usage) {
            return usage(environment, usage.getMessage());
        } catch (RuntimeException failure) {
            invocation.failureId = new FailureId("cli-" + java.util.UUID.randomUUID());
            String message = "internal failure: " + invocation.failureId.value();
            environment.err().println(guru.interlis.diagnostics.DiagnosticMessageFormatter.safeText(message));
            return result(CliTermination.INTERNAL_FAILURE, List.of(), message);
        }
    }

    private static String runEndMessage(CliTermination termination, DiagnosticLanguage language) {
        boolean german = language == DiagnosticLanguage.DE;
        return switch (termination) {
            case SUCCESS -> german ? "...Compilerlauf abgeschlossen" : "...compiler run done";
            case CANCELLED -> german ? "...Compilerlauf abgebrochen" : "...compiler run cancelled";
            default -> german ? "...Compilerlauf fehlgeschlagen" : "...compiler run failed";
        };
    }

    static String compilerVersionLine() {
        String version = "interlis-compiler " + VersionManifest.current().toolVersion();
        try (var resource = DefaultCompilerCli.class.getResourceAsStream("build-revision.txt")) {
            if (resource != null) {
                String revision = new String(resource.readNBytes(65), StandardCharsets.US_ASCII);
                if (revision.matches("[0-9a-f]{40}|[0-9a-f]{64}")) return version + " (git " + revision + ")";
            }
        } catch (java.io.IOException unavailable) {
            // The build revision is optional presentation metadata.
        }
        return version;
    }

    private CliExecutionResult help(List<String> arguments, CliEnvironment environment) {
        parseCommand(arguments);
        var help = new StringBuilder("interlis-compiler ")
                .append(VersionManifest.current().toolVersion()).append("\n\n");
        help.append("""
                Usage: interlis-compiler <command> [options]
                       interlis-compiler <command> [options] [--] [files...]  (model commands)

                Commands
                  check         Validate models and their imports.
                  compile       Compile models and require a valid schema.
                  analyze       Report analysis, including partial results on model errors.
                  diagram       Generate a class diagram.
                  format        Format or check local model files.
                  version       Show version information.
                  capabilities  List supported features as JSON.
                  help          Show this help.

                Examples
                  interlis-compiler check model.ili
                  interlis-compiler diagram --format json model.ili
                """);
        var modelOptions = CommandSpec.standard("check").options();
        appendHelpOptions(help, "Model selection (check, compile, analyze, diagram)", modelOptions, "--root");
        appendHelpOptions(help, "Repositories and locks (check, compile, analyze, diagram)", modelOptions,
                "--repository", "--repositories", "--model-pin", "--source-pin", "--read-lock", "--write-lock");
        help.append("  Defaults: source directories, then https://models.interlis.ch/.\n");
        appendHelpOptions(help, "Network and cache (check, compile, analyze, diagram)", modelOptions,
                "--connect-timeout", "--read-timeout", "--request-timeout", "--proxy", "--proxyPort",
                "--no-proxy", "--cache", "--no-cache", "--offline");
        appendHelpOptions(help, "Configuration (check, compile, analyze, diagram)", modelOptions,
                "--config", "--explain-config", "--warnings");
        appendHelpOptions(help, "Text diagnostics (check, compile, analyze, diagram, format)", modelOptions,
                "--diagnostic-context", "--diagnostic-language", "--color");
        help.append("  Also accepted: --color=auto|always|never.\n");
        help.append("  Auto color applies only to interactive stderr. NO_COLOR, TERM=dumb, and Windows disable it.\n");
        appendHelpOptions(help, "Model search logging (check, compile, analyze, diagram)", modelOptions, "--verbose");
        appendHelpOptions(help, "JSON output (check, compile, analyze, version)", modelOptions, "--json");
        appendHelpOptions(help, "Diagram output (diagram)", CommandSpec.standard("diagram").options(), "--format");
        appendHelpOptions(help, "Formatting (format)", CommandSpec.standard("format").options(),
                "--check", "--write", "--indent", "--line-ending", "--max-line-width");
        environment.out().print(help);
        return result(CliTermination.SUCCESS, List.of(), help.toString());
    }

    private static void appendHelpOptions(StringBuilder help, String heading,
                                          List<CommandSpec.Option> options, String... names) {
        help.append('\n').append(heading).append('\n');
        for (String name : names) {
            var option = options.stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
            var detail = switch (option.name()) {
                case "--root" -> new HelpOption("MODEL", "Start with this model and follow its imports.");
                case "--repository" -> new HelpOption("DIR_OR_URI_OR_XML", "Look up models in this repository.");
                case "--repositories" -> new HelpOption("ENTRIES", "Look up models in a semicolon-separated list of repositories.");
                case "--model-pin" -> new HelpOption("NAME VERSION SOURCE SHA256",
                        "Require an exact model version, source URI, and content hash.");
                case "--source-pin" -> new HelpOption("SOURCE SHA256", "Verify the bytes at a model or index URI.");
                case "--read-lock" -> new HelpOption("FILE", "Replay a saved repository resolution.");
                case "--write-lock" -> new HelpOption("FILE", "Save the repository resolution for a later run.");
                case "--connect-timeout" -> new HelpOption("SECONDS", "Limit the time to connect.");
                case "--read-timeout" -> new HelpOption("SECONDS", "Limit how long a read can remain idle.");
                case "--request-timeout" -> new HelpOption("SECONDS|off", "Limit the total request time.");
                case "--proxy" -> new HelpOption("HOST", "Use an explicit HTTP proxy.");
                case "--proxyPort" -> new HelpOption("PORT", "Set the HTTP proxy port.");
                case "--no-proxy" -> new HelpOption("", "Connect directly.");
                case "--cache" -> new HelpOption("DIR", "Store repository content in this directory.");
                case "--no-cache" -> new HelpOption("", "Disable the repository cache.");
                case "--offline" -> new HelpOption("", "Use verified cached repository content only.");
                case "--config" -> new HelpOption("FILE", "Read settings from a Java properties file.");
                case "--explain-config" -> new HelpOption("", "Show effective settings and where they came from.");
                case "--warnings" -> new HelpOption("allow|reject", "Choose whether warnings fail validation.");
                case "--verbose" -> new HelpOption("", "Show compiler version and completion, model searches, selections, and cache activity.");
                case "--diagnostic-context" -> new HelpOption("", "Show source excerpts, related locations, and hints.");
                case "--diagnostic-language" -> new HelpOption("de|en", "Choose the diagnostic language (default: en).");
                case "--color" -> new HelpOption("auto|always|never", "Color text diagnostics (default: auto).");
                case "--json" -> new HelpOption("", "Write the result as JSON.");
                case "--format" -> new HelpOption("mermaid|plantuml|json", "Choose the diagram format (default: mermaid).");
                case "--check" -> new HelpOption("", "Report whether the files need formatting.");
                case "--write" -> new HelpOption("", "Replace the files with formatted text.");
                case "--indent" -> new HelpOption("SPACES", "Set indentation (0..16, default: 2).");
                case "--line-ending" -> new HelpOption("lf|crlf", "Choose the output line ending.");
                case "--max-line-width" -> new HelpOption("COLUMNS", "Set the target width (40..240, default: "
                        + FormatOptions.DEFAULT_MAX_LINE_WIDTH + ").");
                default -> throw new IllegalArgumentException("No help for " + option.name());
            };
            var signature = new StringBuilder(option.name());
            var arguments = detail.arguments().isEmpty() ? List.<String>of() : List.of(detail.arguments().split(" "));
            for (int index = 0; index < option.valueCount(); index++) {
                signature.append(' ').append(arguments.get(index));
            }
            help.append("  %-40s %s%s\n".formatted(signature, detail.description(),
                    option.repeatable() ? " Repeatable." : ""));
        }
    }

    private record HelpOption(String arguments, String description) { }

    private CliExecutionResult version(List<String> arguments, CliEnvironment environment) {
        VersionManifest manifest = VersionManifest.current();
        if (arguments.size() > 1) {
            if (arguments.size() == 2 && arguments.get(1).equals("--json")) {
                String json = "{\"tool\":\"interlis-compiler\",\"version\":\""
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
        String output = "interlis-compiler " + manifest.toolVersion() + "\n"
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
        String output = "{\"tool\":\"interlis-compiler\",\"version\":\"" + manifest.toolVersion() + "\","
                + "\"apiVersion\":\"" + manifest.apiVersion() + "\","
                + "\"diagnosticCatalogVersion\":\"" + manifest.diagnosticCatalogVersion() + "\","
                + "\"formatVersion\":\"" + manifest.formatVersion() + "\","
                + "\"diagramSchemaVersion\":\"" + manifest.diagramSchemaVersion() + "\","
                + "\"formatOptions\":{\"maxLineWidth\":{\"min\":40,\"max\":240,\"default\":"
                + FormatOptions.DEFAULT_MAX_LINE_WIDTH + "}},"
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

    private CliExecutionResult semantic(CliEnvironment environment,
                                       CancellationToken token, Invocation invocation) {
        ParsedCommand supplied = invocation.command;
        var loader = new ConfigurationLoader();
        var configuration = loader.load(supplied, environment, token);
        var warningPolicy = switch (configuration.command().value("--warnings").orElseThrow()) {
            case "allow" -> guru.interlis.compiler.WarningPolicy.ALLOW_WARNINGS;
            case "reject" -> guru.interlis.compiler.WarningPolicy.REJECT_WARNINGS;
            default -> throw new CliUsageException("--warnings must be allow or reject");
        };
        ParsedArguments parsed = parseFiles(configuration.command());
        boolean diagram = supplied.command().equals("diagram");
        if (diagram && parsed.json())
            return usage(environment, "--json is not supported by diagram; use --format json");
        new OutputPlanner().plan(configuration.command(), environment);
        if (supplied.has("--explain-config")) {
            String explanation = loader.explain(configuration);
            environment.out().println(explanation);
            return result(CliTermination.SUCCESS, List.of(), explanation);
        }
        if (parsed.files().isEmpty() && parsed.roots().isEmpty())
            return usage(environment, "no source files or root models");
        if (supplied.has("--verbose") && !parsed.json()) {
            environment.err().println(ConsoleDiagnosticRenderer.renderLog(
                    Severity.INFO, compilerVersionLine(), invocation.ansiEnabled));
            invocation.runBannerStarted = true;
        }
        Loaded loaded = load(parsed.files(), environment, token);
        try (Workspace workspace = workspace(loaded, environment, warningPolicy)) {
            List<ModelSelector> roots = roots(parsed.roots(), loaded.sources());
            invocation.rootCount = roots.size();
            PreparedRepositoryUpdate update = null;
            boolean useDefaults = configuration.origins().get("--repository") == ConfigurationLoader.Origin.PROFILE_DEFAULT;
            var sources = parsed.repositories().stream().map(value -> repositorySource(value, environment)).toList();
            var sourcePins = new LinkedHashMap<>(parsed.sourcePins());
            boolean pendingModels = useDefaults && workspace.capture(
                    new AnalysisRequest(workspace.currentRevision(), roots), token).resolution().pendingFetches()
                    .stream().anyMatch(request -> !request.modelName().isEmpty());
            if (!useDefaults || pendingModels || parsed.readLock().isPresent() || parsed.writeLock().isPresent()
                    || !parsed.modelPins().isEmpty() || !sourcePins.isEmpty()) {
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
                var config = new RepositoryConfig(sources, RepositoryNetworkOptions.fetchPolicy(configuration.command()));
                Optional<ResolutionLock> lock = Optional.empty();
                if (parsed.readLock().isPresent()) {
                    try (var input = Files.newInputStream(hostPath(parsed.readLock().orElseThrow(), environment))) {
                        byte[] bytes = input.readNBytes(8_388_609);
                        if (bytes.length > 8_388_608) throw new CliUsageException("lock exceeds byte limit");
                        lock = Optional.of(new ResolutionLockCodec().decode(new String(bytes, StandardCharsets.UTF_8)));
                    } catch (java.io.IOException unavailable) { return result(CliTermination.OPERATIONAL_FAILURE, List.of(), "lock could not be read"); }
                    catch (IllegalArgumentException invalid) { throw new CliUsageException("invalid resolution lock"); }
                }
                boolean logResolution = invocation.command.has("--verbose") && !parsed.json();
                var resolutionRenderer = new RepositoryResolutionRenderer();
                if (pendingModels && !loaded.sources().isEmpty()) {
                    new LocalModelDiscovery(environment).addNeighbours(workspace, loaded.sources().keySet(),
                            lock, sourcePins, directory -> {
                                if (logResolution) environment.err().println(resolutionRenderer.renderLocalSearch(
                                        directory, invocation.language, invocation.ansiEnabled));
                            }, token);
                }
                var cacheConfiguration = parsed.cache().map(path -> RepositoryCacheConfiguration.at(hostPath(path, environment)))
                        .orElseGet(RepositoryCacheConfiguration::disabled);
                var access = HostRepositories.createObserved(environment.workingDirectory(), cacheConfiguration, observation -> {
                    var event=observation.event();
                    invocation.lifecycle.cacheLookup("repository", event==RepositoryCacheEvent.FRESH_HIT || event==RepositoryCacheEvent.NOT_MODIFIED || event==RepositoryCacheEvent.OFFLINE_HIT,event.name());
                    if (logResolution) {
                        new RepositoryCacheRenderer().render(observation,invocation.language,invocation.ansiEnabled)
                                .ifPresent(environment.err()::println);
                    }
                    if(event==RepositoryCacheEvent.WRITE_UNAVAILABLE) {
                        var warning=RepositoryDiagnostics.cacheUnavailable();
                        environment.err().println(new ConsoleDiagnosticRenderer().render(warning, Map.of(),
                                invocation.language, diagnosticOptions(invocation)));
                    }
                }, RepositoryNetworkOptions.proxy(configuration.command()));
                var outcome = access.closureService()
                        .resolve(workspace, new AnalysisRequest(workspace.currentRevision(), roots),
                                new RepositoryResolveOptions(config, parsed.modelPins(), sourcePins, lock), observation -> {
                                    if (logResolution) {
                                        resolutionRenderer.render(observation, invocation.language, invocation.ansiEnabled)
                                                .ifPresent(environment.err()::println);
                                    }
                                }, token);
                switch (outcome) {
                    case RepositoryResolutionOutcome.Prepared prepared -> {
                        update = prepared.update();
                        invocation.resolutionLock=update.lock(); invocation.repositorySteps=update.lock().steps();
                        try { update.apply(workspace); }
                        catch (guru.interlis.workspace.UpdateRejectedException conflict) {
                            if (conflict.code() != guru.interlis.workspace.UpdateRejectionCode.VERSION_CONFLICT) throw conflict;
                            return result(CliTermination.INCOMPLETE_ANALYSIS, List.of(), "workspace changed during resolution");
                        }
                    }
                    case RepositoryResolutionOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
                    case RepositoryResolutionOutcome.Stale ignored -> { return result(CliTermination.INCOMPLETE_ANALYSIS, List.of(), "workspace changed during resolution"); }
                    case RepositoryResolutionOutcome.Rejected rejected -> {
                        invocation.repositorySteps=rejected.steps();
                        var problem = rejected.problems().getFirst();
                        if (useDefaults && lock.isEmpty() && parsed.writeLock().isEmpty()
                                && parsed.modelPins().isEmpty() && parsed.sourcePins().isEmpty()
                                && problem.reason() == RepositoryProblem.Reason.NOT_FOUND
                                && rejected.steps().stream().noneMatch(step -> step.kind() == RepositorySearchStep.Kind.SELECTED)) {
                            // The workspace already has all available sources. Let the compiler
                            // report the missing input together with independent source diagnostics.
                            break;
                        }
                        String output = "resolution rejected";
                        print(parsed.json(), environment, rejected.diagnostics(), output, rejected.sources(), invocation);
                        return result(problem.reason() == RepositoryProblem.Reason.LOAD_FAILURE || problem.reason() == RepositoryProblem.Reason.INDEX_FAILURE
                                ? CliTermination.OPERATIONAL_FAILURE : CliTermination.INPUT_UNRESOLVED, rejected.diagnostics(), output);
                    }
                }
            }
            invocation.rootCount = roots.size();
            if (supplied.command().equals("analyze")) {
                var analysis = workspace.analyze(new AnalysisRequest(workspace.currentRevision(), roots),
                        environment.services().runContexts().createChild(invocation.context), token);
                invocation.analysisComplete = !analysis.coverage().entries().isEmpty()
                        && analysis.coverage().incompleteEntries().isEmpty();
                writeLock(parsed, update, workspace, environment, token, invocation);
                print(parsed.json(), environment, analysis.diagnostics(), "analyzed", texts(workspace), invocation);
                return result(CliTermination.SUCCESS, analysis.diagnostics(), "analyzed");
            }
            CompilationOutcome outcome = workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(), roots),
                    environment.services().runContexts().createChild(invocation.context), token);
            invocation.compiled(outcome);
            List<Diagnostic> diagnostics = diagnostics(outcome);
            String output = diagram ? "diagram requires a checked schema" : summary(outcome);
            if (outcome instanceof CompilationOutcome.Succeeded succeeded) {
                if (diagram) {
                    var presentation = parsed.format().equals("mermaid")
                            ? AssociationPresentation.COMPACT_BINARY : AssociationPresentation.ENTITY;
                    var projection = DiagramProjectors.create().project(new DiagramInput.FromSchema(succeeded.schema()),
                            DiagramOptions.defaults(new DiagramSelection(List.of(succeeded.schema().rootModels().getFirst())),
                                    presentation), token);
                    if (projection instanceof DiagramProjectionOutcome.Rejected rejected)
                        return result(CliTermination.OPERATIONAL_FAILURE, List.of(), rejected.reasons().toString());
                    DiagramDocument document = ((DiagramProjectionOutcome.Produced) projection).document();
                    output = switch (parsed.format()) {
                        case "mermaid" -> new MermaidRenderer().render(document);
                        case "plantuml" -> new PlantUmlRenderer().render(document);
                        case "json" -> new DiagramJsonCodec().encode(document);
                        default -> throw new IllegalArgumentException("unknown format: " + parsed.format());
                    };
                }
                writeLock(parsed, update, workspace, environment, token, invocation);
            }
            print(parsed.json(), environment, diagnostics, output, texts(workspace), invocation);
            if (diagram && outcome instanceof CompilationOutcome.Succeeded) environment.out().println(output);
            return result(new CliOutcomeMapper().classify(outcome), diagnostics, output);
        } catch (java.io.IOException unavailable) {
            environment.err().println("lock could not be written");
            return result(CliTermination.OPERATIONAL_FAILURE, List.of(), "lock could not be written");
        }
    }

    /** A lock records resolved source identities; it is not a semantic validity certificate. */
    private void writeLock(ParsedArguments parsed, PreparedRepositoryUpdate update, Workspace workspace,
                           CliEnvironment environment, CancellationToken token, Invocation invocation)
            throws java.io.IOException {
        if (update == null || parsed.writeLock().isEmpty()) return;
        Path target = hostPath(parsed.writeLock().orElseThrow(), environment);
        if (workspace.state().sourceRevisions().keySet().stream()
                .map(id -> java.net.URI.create(id.value())).filter(uri -> "file".equalsIgnoreCase(uri.getScheme()))
                .map(Path::of).anyMatch(source -> OutputPlanner.sameFile(source, target)))
            throw new CliUsageException("lock output collides with a resolved model source");
        if (java.util.stream.Stream.concat(update.lock().indexes().keySet().stream(),update.lock().unavailable().keySet().stream()).map(id -> java.net.URI.create(id.value()))
                .filter(uri -> "file".equalsIgnoreCase(uri.getScheme())).map(Path::of)
                .anyMatch(source -> OutputPlanner.sameFile(source,target)))
            throw new CliUsageException("lock output collides with repository metadata");
        byte[] bytes = new ResolutionLockCodec().encode(update.lock()).getBytes(StandardCharsets.UTF_8);
        fileWriter.write(target, bytes, Optional.empty(), token);
        invocation.artifacts.add(new CliExecutionResult.Artifact(CliExecutionResult.ArtifactKind.RESOLUTION_LOCK,
                target.toUri(), bytes.length));
    }

    private static Map<SourceId, SourceText> texts(Workspace workspace) {
        var texts = new LinkedHashMap<SourceId, SourceText>();
        workspace.state().sourceRevisions().keySet().forEach(id -> workspace.source(id).ifPresent(text -> texts.put(id, text)));
        return texts;
    }

    private static Path hostPath(String value, CliEnvironment environment) {
        Path path = value.startsWith("file:") ? Path.of(java.net.URI.create(value)) : Path.of(value);
        return (path.isAbsolute() ? path : environment.workingDirectory().resolve(path)).toAbsolutePath().normalize();
    }
    private static RepositorySource repositorySource(String value, CliEnvironment environment) {
        try {
            var uri = value.matches("(?i)(?:https?|file):.*")
                    ? java.net.URI.create(value.replace(" ", "%20"))
                    : hostPath(value, environment).toUri();
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                uri = Path.of(uri).toAbsolutePath().normalize().toUri();
            }
            if (uri.getPath() != null && uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
                return new RepositorySource(new RepositoryBase(uri.resolve(".")), new SourceLocator(uri.toString()));
            }
            String directory = uri.toString();
            if (!directory.endsWith("/")) directory += "/";
            return new RepositorySource(new RepositoryBase(java.net.URI.create(directory)),
                    new SourceLocator(directory + "ilimodels.xml"));
        } catch (IllegalArgumentException invalid) {
            throw new CliUsageException("invalid repository location");
        }
    }

    private CliExecutionResult format(CliEnvironment environment,
                                      CancellationToken token, Invocation invocation) {
        FormatArguments parsed = parseFormat(invocation.command);
        new OutputPlanner().plan(invocation.command, environment);
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
        List<FormatOutput> outputs = new ArrayList<>();
        for (FormatInput input : inputs) {
            FormatOutcome outcome = formatter.format(input.source(), parsed.options(), token);
            if (outcome instanceof FormatOutcome.Rejected rejected) {
                ConsoleDiagnosticRenderer renderer = new ConsoleDiagnosticRenderer();
                for (Diagnostic diagnostic : rejected.diagnostics()) {
                    environment.err().println(renderer.render(diagnostic, sources, invocation.language, diagnosticOptions(invocation)));
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
                String message = invocation.language == DiagnosticLanguage.DE && notice.code().equals("VALUE_LINE_ENDING_PRESERVED")
                        ? "Zeilenenden in einem werttragenden Literal oder Metawert bleiben zur Werterhaltung unverändert."
                        : guru.interlis.diagnostics.DiagnosticMessageFormatter.safeText(notice.message());
                environment.err().println(guru.interlis.diagnostics.DiagnosticMessageFormatter.safeText(notice.code()) + ": " + message);
            }
            outputs.add(new FormatOutput(input, formatted.text()));
        }
        if (parsed.check()) {
            List<String> changed = outputs.stream()
                    .filter(output -> !output.text().equals(output.input().source().text()))
                    .map(output -> output.input().file().toString()).toList();
            if (!changed.isEmpty()) {
                changed.forEach(path -> environment.err().println("would format: " + ConsoleDiagnosticRenderer.delimitedLocation(path)));
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
                environment.err().println(guru.interlis.diagnostics.DiagnosticMessageFormatter.safeText(message));
                return result(CliTermination.OPERATIONAL_FAILURE, List.of(), message);
            }
            return result(CliTermination.SUCCESS, List.of(), "");
        }
        String text = outputs.stream().map(FormatOutput::text)
                .collect(java.util.stream.Collectors.joining());
        environment.out().print(text);
        return result(CliTermination.SUCCESS, List.of(), text);
    }

    private static FormatArguments parseFormat(ParsedCommand command) {
        int indent;
        try { indent = Integer.parseInt(command.value("--indent").orElse("2")); }
        catch (NumberFormatException invalid) { throw new CliUsageException("invalid --indent value"); }
        if (indent < 0 || indent > 16) throw new CliUsageException("--indent must be between 0 and 16");
        var ending = command.value("--line-ending").map(value -> switch (value) {
            case "lf" -> FormatOptions.LineEnding.LF;
            case "crlf" -> FormatOptions.LineEnding.CRLF;
            default -> throw new CliUsageException("--line-ending must be lf or crlf");
        }).orElse(FormatOptions.LineEnding.PRESERVE);
        int width;
        try {
            width = Integer.parseInt(command.value("--max-line-width")
                    .orElse(Integer.toString(FormatOptions.DEFAULT_MAX_LINE_WIDTH)));
        } catch (NumberFormatException invalid) {
            throw new CliUsageException("invalid --max-line-width value");
        }
        if (width < 40 || width > 240) {
            throw new CliUsageException("--max-line-width must be between 40 and 240");
        }
        return new FormatArguments(command.files(), command.has("--check"), command.has("--write"),
                new FormatOptions(indent, ending, true, width));
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
                                   FormatOptions options) { }

    private record FormatInput(SourceText source, Path file, Path workingDirectory) {
        Path resolved(CliEnvironment environment) {
            return file.isAbsolute() ? file : environment.workingDirectory().resolve(file);
        }
    }

    private record FormatOutput(FormatInput input, String text) {
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
        var repositories = command.values("--repository");
        if (repositories.stream().anyMatch(String::isBlank)) throw new CliUsageException("empty repository location");
        return new ParsedArguments(command.files(), command.values("--root"), command.has("--json"),
                command.value("--format").orElse("mermaid"), repositories,
                Map.copyOf(modelPins), Map.copyOf(sourcePins),
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
            case CompilationOutcome.Succeeded succeeded -> succeeded.analysis().requestDiagnostics();
            case CompilationOutcome.Rejected rejected -> rejected.analysis().requestDiagnostics();
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
        if (json) {
            environment.out().println("{\"status\":\"" + JsonDiagnosticWriter.escape(summary)
                    + "\",\"diagnostics\":" + new JsonDiagnosticWriter().write(diagnostics, invocation.language)
                    + new RepositoryReportWriter().json(invocation.resolutionLock,invocation.repositorySteps) + "}");
        } else {
            ConsoleDiagnosticRenderer renderer = new ConsoleDiagnosticRenderer();
            for (Diagnostic diagnostic : new DiagnosticPresentation().project(diagnostics, new DiagnosticDisplayPolicy(true, Integer.MAX_VALUE)).visibleDiagnostics()) {
                environment.err().println(renderer.render(diagnostic, sources, invocation.language, diagnosticOptions(invocation)));
            }
        }
    }

    private static RenderOptions diagnosticOptions(Invocation invocation) {
        return new RenderOptions(invocation.ansiEnabled, 1, 1, 160, invocation.command.has("--diagnostic-context"));
    }

    private CliExecutionResult usage(CliEnvironment environment, String message) {
        environment.err().println(guru.interlis.diagnostics.DiagnosticMessageFormatter.safeText(message));
        environment.err().print("""
                Usage: interlis-compiler <command> [options] [files...]
                Example: interlis-compiler check <File.ili>
                Run interlis-compiler --help for commands and options.
                """);
        return result(CliTermination.USAGE_ERROR, List.of(), message);
    }

    private CliExecutionResult result(CliTermination termination,
                                             List<Diagnostic> diagnostics, String output) {
        int exit = new ExitCodePolicy().exitCode(termination);
        return new CliExecutionResult(exit, termination,
                diagnostics, output);
    }

    private record ParsedArguments(List<String> files, List<String> roots, boolean json, String format,
                                   List<String> repositories, Map<String, ModelPin> modelPins,
                                   Map<SourceId, ContentDigest> sourcePins, Optional<String> readLock,
                                   Optional<String> writeLock, Optional<String> cache) {
    }

    private record Loaded(Map<SourceId, SourceText> sources) {
    }

    /** Mutable only for one invocation; never retained on the reusable CLI instance. */
    private static final class Invocation {
        final RunContext context;
        RunLifecycle lifecycle;
        boolean ansiEnabled;
        boolean runBannerStarted;
        boolean schemaProduced;
        boolean analysisComplete;
        final List<CliExecutionResult.Artifact> artifacts = new ArrayList<>();
        ResolutionLock resolutionLock;
        List<RepositorySearchStep> repositorySteps=List.of();
        ParsedCommand command;
        DiagnosticLanguage language = DiagnosticLanguage.EN;
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
