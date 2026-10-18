package guru.interlis.diagnostics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure display projection over an already canonical diagnostic list. */
public final class DiagnosticPresentation {
    /**
     * Executes {@code project}.
     *
     * @param diagnostics the {@code diagnostics} argument
     * @param policy the {@code policy} argument
     *
     * @return the result
     */
    public DiagnosticView project(List<Diagnostic> diagnostics, DiagnosticDisplayPolicy policy) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(policy, "policy");
        List<Diagnostic> visible = new ArrayList<>();
        List<DiagnosticView.SuppressedDiagnostic> suppressed = new ArrayList<>();
        Set<DiagnosticId> candidateVisible = new HashSet<>();
        for (Diagnostic diagnostic : diagnostics) {
            if (visible.size() >= policy.maxVisible()) {
                suppressed.add(new DiagnosticView.SuppressedDiagnostic(diagnostic,
                        DiagnosticView.SuppressionReason.DISPLAY_LIMIT,
                        visible.isEmpty() ? diagnostic.id() : visible.get(0).id()));
            } else {
                visible.add(diagnostic);
                candidateVisible.add(diagnostic.id());
            }
        }
        int hidden = suppressed.size();
        if (policy.suppressDependentOnVisibleError()) {
            Map<DiagnosticId, Diagnostic> byId = new HashMap<>();
            for (Diagnostic diagnostic : visible) {
                byId.put(diagnostic.id(), diagnostic);
            }
            List<Diagnostic> kept = new ArrayList<>();
            for (Diagnostic diagnostic : visible) {
                DiagnosticId cause = rootCause(diagnostic, byId);
                if (cause != null && byId.containsKey(cause)) {
                    suppressed.add(new DiagnosticView.SuppressedDiagnostic(diagnostic,
                            DiagnosticView.SuppressionReason.DEPENDENT_ON_VISIBLE_ERROR, cause));
                } else {
                    kept.add(diagnostic);
                }
            }
            hidden = suppressed.size();
            visible = kept;
        }
        return new DiagnosticView(visible, suppressed, hidden);
    }

    private static DiagnosticId rootCause(Diagnostic diagnostic, Map<DiagnosticId, Diagnostic> visible) {
        Diagnostic current = diagnostic;
        Set<DiagnosticId> seen = new HashSet<>();
        DiagnosticId lastCause = null;
        while (current.causeId().isPresent()) {
            DiagnosticId cause = current.causeId().get();
            if (!seen.add(cause)) {
                return null;
            }
            Diagnostic parent = visible.get(cause);
            if (parent == null) {
                return lastCause;
            }
            lastCause = cause;
            current = parent;
        }
        return lastCause;
    }
}
