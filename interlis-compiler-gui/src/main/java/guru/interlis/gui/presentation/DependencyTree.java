package guru.interlis.gui.presentation;

import guru.interlis.compiler.ModelLocation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Lazily expands graph occurrences, terminating cycles only on the current path. */
public final class DependencyTree {
    public static final int MAX_DEPTH = 48;
    private static final int MAX_CHILDREN = 1000;

    public record Occurrence(String name, DependencySnapshot.Status status, Optional<ModelLocation> target,
                             Optional<DependencySnapshot.Edge> edge, List<ModelLocation> candidates,
                             List<ModelLocation> ancestors, boolean cycle, boolean limit) {
        public Occurrence {
            ancestors = List.copyOf(ancestors);
            candidates = List.copyOf(candidates);
        }

        @Override
        public String toString() {
            if (limit) {
                return "More dependencies · display limit reached";
            }
            String version = target.flatMap(ModelLocation::modelVersion).filter(value -> !value.isEmpty())
                    .map(value -> " · " + guru.interlis.diagnostics.DiagnosticMessageFormatter.safeText(value)).orElse("");
            return guru.interlis.diagnostics.DiagnosticMessageFormatter.safeText(name) + version
                    + (cycle ? " · cycle reference" : status == DependencySnapshot.Status.RESOLVED ? "" : " · " + status);
        }
    }

    private final DependencySnapshot graph;
    private final String search;
    private final boolean unresolvedOnly;
    private final Set<ModelLocation> relevant;

    public DependencyTree(DependencySnapshot graph, String search, boolean unresolvedOnly) {
        this.graph = graph;
        this.search = search.strip().toLowerCase(Locale.ROOT);
        this.unresolvedOnly = unresolvedOnly;
        relevant = this.search.isEmpty() && !unresolvedOnly ? graph.models() : relevantModels();
    }

    public List<Occurrence> roots() {
        return limited(graph.roots().stream().filter(root -> included(root.name(), root.status(), root.target()))
                .limit(MAX_CHILDREN + 1L).map(root -> new Occurrence(root.name(), root.status(), root.target(), Optional.empty(),
                        root.candidates(), List.of(), false, false)).toList());
    }

    public boolean hasChildren(Occurrence occurrence) {
        return !occurrence.limit() && !occurrence.cycle() && occurrence.target().map(target ->
                graph.directImports(target).stream().anyMatch(edge -> included(edge.site().importedModel(),
                        edge.status(), edge.target()))).orElse(false);
    }

    public List<Occurrence> children(Occurrence parent) {
        if (parent.limit() || parent.cycle() || parent.target().isEmpty()) {
            return List.of();
        }
        ModelLocation target = parent.target().orElseThrow();
        var edges = graph.directImports(target).stream().filter(edge -> included(edge.site().importedModel(),
                edge.status(), edge.target())).toList();
        if (edges.isEmpty()) {
            return List.of();
        }
        if (parent.ancestors().size() >= MAX_DEPTH) {
            return List.of(limit());
        }
        var path = new java.util.ArrayList<>(parent.ancestors());
        path.add(target);
        return limited(edges.stream().limit(MAX_CHILDREN + 1L).map(edge -> new Occurrence(edge.site().importedModel(), edge.status(), edge.target(),
                Optional.of(edge), List.of(), path, edge.target().filter(path::contains).isPresent(), false)).toList());
    }

    private boolean included(String name, DependencySnapshot.Status status, Optional<ModelLocation> target) {
        return matches(name, status) || target.filter(relevant::contains).isPresent();
    }

    private boolean matches(String name, DependencySnapshot.Status status) {
        return name.toLowerCase(Locale.ROOT).contains(search) && (!unresolvedOnly || status.unresolved());
    }

    private Set<ModelLocation> relevantModels() {
        var relevant = new HashSet<ModelLocation>();
        var queue = new ArrayDeque<ModelLocation>();
        for (var model : graph.models()) {
            if (matches(model.modelName(), DependencySnapshot.Status.RESOLVED) || graph.directImports(model).stream()
                    .anyMatch(edge -> matches(edge.site().importedModel(), edge.status()))) {
                queue.addLast(model);
            }
        }
        while (!queue.isEmpty()) {
            var model = queue.removeFirst();
            if (relevant.add(model)) {
                graph.usedBy(model).forEach(edge -> queue.addLast(edge.importer()));
            }
        }
        return Set.copyOf(relevant);
    }

    private static List<Occurrence> limited(List<Occurrence> children) {
        if (children.size() <= MAX_CHILDREN) {
            return children;
        }
        var result = new java.util.ArrayList<>(children.subList(0, MAX_CHILDREN));
        result.add(limit());
        return List.copyOf(result);
    }

    public static Occurrence limit() {
        return new Occurrence("", DependencySnapshot.Status.NOT_AVAILABLE, Optional.empty(), Optional.empty(),
                List.of(), List.of(), false, true);
    }
}
