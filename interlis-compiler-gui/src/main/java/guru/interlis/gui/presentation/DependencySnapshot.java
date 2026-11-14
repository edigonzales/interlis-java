package guru.interlis.gui.presentation;

import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.ImportSite;
import guru.interlis.compiler.ModelLocation;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.RootResolution;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.model.LanguageVersion;
import guru.interlis.repository.RepositorySearchStep;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.ModelSyntax;
import guru.interlis.workspace.resolution.ResolutionDependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Directed graph from one strict compiler capture, with explicit missing and partial states. */
public final class DependencySnapshot {
    public enum Completeness { COMPLETE, PARTIAL, UNAVAILABLE }

    public enum Status {
        RESOLVED, MISSING, AMBIGUOUS, BLOCKED, BUILTIN, NOT_AVAILABLE;

        public boolean unresolved() {
            return this != RESOLVED && this != BUILTIN;
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    public record Node(ModelLocation id, Optional<LanguageVersion> language, Optional<SourceRange> declaration) {
    }

    public record Edge(ModelLocation importer, ImportSite site, Status status, Optional<ImportResolution> resolution) {
        public Optional<ModelLocation> target() {
            return resolution.filter(ImportResolution.Resolved.class::isInstance).map(ImportResolution.Resolved.class::cast)
                    .map(ImportResolution.Resolved::target);
        }
    }

    public record Root(String name, Optional<ModelLocation> target, Status status, List<ModelLocation> candidates) {
        public Root {
            candidates = List.copyOf(candidates);
        }
    }

    private final Completeness completeness;
    private final List<Root> roots;
    private final Map<ModelLocation, Node> nodes;
    private final Map<ModelLocation, List<Edge>> outgoing;
    private final Map<ModelLocation, List<Edge>> incoming;
    private final List<String> trace;
    private final List<RepositorySearchStep> steps;

    private DependencySnapshot(Completeness completeness, List<Root> roots, Map<ModelLocation, Node> nodes,
                               Map<ModelLocation, List<Edge>> outgoing, Map<ModelLocation, List<Edge>> incoming,
                               List<String> trace, List<RepositorySearchStep> steps) {
        this.completeness = completeness;
        this.roots = List.copyOf(roots);
        this.nodes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.outgoing = ownedEdges(outgoing);
        this.incoming = ownedEdges(incoming);
        this.trace = List.copyOf(trace);
        this.steps = List.copyOf(steps);
    }

    private static Map<ModelLocation, List<Edge>> ownedEdges(Map<ModelLocation, List<Edge>> input) {
        var copy = new LinkedHashMap<ModelLocation, List<Edge>>();
        input.forEach((id, edges) -> copy.put(id, List.copyOf(edges)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    public static DependencySnapshot from(CompileResult result) {
        if (result.capture().isEmpty()) {
            return new DependencySnapshot(Completeness.UNAVAILABLE, List.of(), Map.of(), Map.of(), Map.of(),
                    List.of(), result.repositorySteps());
        }
        var capture = result.capture().orElseThrow();
        var closure = capture.resolution();
        var declarations = new LinkedHashMap<ModelLocation, ModelSyntax>();
        for (var syntax : capture.reuse().previousSyntax().values()) {
            for (ModelSyntax model : syntax.root().models()) {
                var identity = new ModelLocation(syntax.source().id(), syntax.source().revision(), model.name(),
                        Optional.of(model.modelVersion()), model.range().startOffset());
                declarations.put(identity, model);
            }
        }
        var bindings = new LinkedHashMap<ImportSite, ImportResolution>();
        for (ImportBinding binding : closure.sourceSet().imports()) {
            bindings.put(binding.site(), binding.resolution());
        }
        var nodes = new LinkedHashMap<ModelLocation, Node>();
        var outgoing = new LinkedHashMap<ModelLocation, List<Edge>>();
        var incoming = new LinkedHashMap<ModelLocation, List<Edge>>();
        var queue = new ArrayDeque<>(closure.rootLocations());
        boolean unavailable = false;
        while (!queue.isEmpty()) {
            ModelLocation id = queue.removeFirst();
            if (nodes.containsKey(id)) {
                continue;
            }
            var syntax = Optional.ofNullable(declarations.get(id));
            nodes.put(id, new Node(id, syntax.map(ModelSyntax::languageVersion), syntax.map(ModelSyntax::range)));
            unavailable |= syntax.isEmpty();
            for (var imported : syntax.map(ModelSyntax::imports).orElse(List.of())) {
                var site = new ImportSite(imported.range(), id.modelName(), imported.modelName());
                var resolution = Optional.ofNullable(bindings.get(site));
                Status status = resolution.map(DependencySnapshot::status)
                        .orElseGet(() -> site.importedModel().equals("INTERLIS") ? Status.BUILTIN : Status.NOT_AVAILABLE);
                unavailable |= status == Status.NOT_AVAILABLE;
                var edge = new Edge(id, site, status, resolution);
                outgoing.computeIfAbsent(id, ignored -> new ArrayList<>()).add(edge);
                edge.target().ifPresent(target -> {
                    incoming.computeIfAbsent(target, ignored -> new ArrayList<>()).add(edge);
                    queue.addLast(target);
                });
            }
        }
        var roots = new ArrayList<Root>();
        Set<ModelLocation> displayed = new HashSet<>();
        for (ModelSelector selector : capture.request().roots()) {
            var decision = selector instanceof ModelSelector.ByName named
                    ? capture.request().sources().rootResolutions().get(named) : null;
            var chosen = switch (decision) {
                case RootResolution.Resolved resolved -> List.of(resolved.target());
                case RootResolution.Missing ignored -> List.<ModelLocation>of();
                case RootResolution.Ambiguous ignored -> List.<ModelLocation>of();
                case null -> closure.rootLocations().stream().filter(location -> matches(selector, location)).toList();
            };
            if (chosen.isEmpty()) {
                String name = switch (selector) {
                    case ModelSelector.ByName(var model, var version, var source) -> model;
                    case ModelSelector.BySource(var source) -> DiagnosticPresenter.shortSource(source);
                };
                var status = switch (decision) {
                    case RootResolution.Missing ignored -> Status.MISSING;
                    case RootResolution.Ambiguous ignored -> Status.AMBIGUOUS;
                    case null, default -> Status.NOT_AVAILABLE;
                };
                roots.add(new Root(name, Optional.empty(), status,
                        decision instanceof RootResolution.Ambiguous ambiguous ? ambiguous.candidates() : List.of()));
            } else {
                for (var location : chosen) {
                    if (displayed.add(location)) {
                        roots.add(new Root(location.modelName(), Optional.of(location), Status.RESOLVED, List.of()));
                    }
                }
            }
        }
        return new DependencySnapshot(closure.complete() && !unavailable ? Completeness.COMPLETE : Completeness.PARTIAL,
                roots, nodes, outgoing, incoming, closure.trace().decisions(), result.repositorySteps());
    }

    private static Status status(ImportResolution resolution) {
        return switch (resolution) {
            case ImportResolution.Resolved ignored -> Status.RESOLVED;
            case ImportResolution.Missing ignored -> Status.MISSING;
            case ImportResolution.Ambiguous ignored -> Status.AMBIGUOUS;
            case ImportResolution.Blocked ignored -> Status.BLOCKED;
        };
    }

    private static boolean matches(ModelSelector selector, ModelLocation location) {
        return switch (selector) {
            case ModelSelector.BySource(var source) -> source.equals(location.source());
            case ModelSelector.ByName(var name, var version, var source) -> name.equals(location.modelName())
                    && (version.isEmpty() || version.equals(location.modelVersion()))
                    && (source.isEmpty() || source.orElseThrow().equals(location.source()));
        };
    }

    public Completeness completeness() {
        return completeness;
    }

    public List<Root> roots() {
        return roots;
    }

    public Optional<Node> model(ModelLocation id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public List<Edge> directImports(ModelLocation id) {
        return outgoing.getOrDefault(id, List.of());
    }

    public List<Edge> usedBy(ModelLocation id) {
        return incoming.getOrDefault(id, List.of());
    }

    public List<Edge> builtinUsers() {
        return outgoing.values().stream().flatMap(List::stream).filter(edge -> edge.status() == Status.BUILTIN).toList();
    }

    public Set<ModelLocation> models() {
        return nodes.keySet();
    }

    public List<String> trace() {
        return trace;
    }

    public List<RepositorySearchStep> repositorySteps() {
        return steps;
    }
}
