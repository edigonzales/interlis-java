package guru.interlis.compiler.internal;

import guru.interlis.compiler.RejectionReason;
import guru.interlis.diagnostics.DiagnosticId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Selected root models and explicit selection problems. */
record RootSelection(List<ModelDecl> roots, List<DiagnosticId> diagnostics,
                     Set<RejectionReason> reasons) {
    RootSelection {
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        reasons = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(reasons, "reasons")));
    }
}
