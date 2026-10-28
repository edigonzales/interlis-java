package guru.interlis.compiler.internal;

import guru.interlis.compiler.RejectionReason;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.source.SourceId;
import guru.interlis.syntax.ImportSyntax;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Positive and negative import closure for the requested roots. */
record Closure(List<ModelDecl> roots, List<ModelDecl> models, Set<SourceId> sources,
               List<ImportEdge> importEdges, List<DiagnosticId> diagnostics,
               Set<RejectionReason> reasons) {
    Closure {
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        models = List.copyOf(Objects.requireNonNull(models, "models"));
        sources = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(sources, "sources")));
        importEdges = List.copyOf(Objects.requireNonNull(importEdges, "importEdges"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        reasons = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(reasons, "reasons")));
    }

    boolean complete() {
        return reasons.isEmpty();
    }

    /** Semantic locations belong to models, not to every model sharing their file. */
    boolean contains(guru.interlis.source.SourceRange range) {
        if (!sources.contains(range.source())) return false;
        if (range.isEmpty() && range.startOffset() == 0) return true;
        return models.stream().anyMatch(model -> model.sourceId().equals(range.source())
                && model.syntax().range().revision().equals(range.revision())
                && model.syntax().range().startOffset() <= range.startOffset()
                && model.syntax().range().endOffset() >= range.endOffset());
    }

    record ImportEdge(ModelDecl importer, ImportSyntax site, ModelDecl target) {
        ImportEdge {
            Objects.requireNonNull(importer, "importer");
            Objects.requireNonNull(site, "site");
            Objects.requireNonNull(target, "target");
        }
    }
}
