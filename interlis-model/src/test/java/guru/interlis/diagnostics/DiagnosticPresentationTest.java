package guru.interlis.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticPresentationTest {
    @Test
    void dependentSuppressionUsesExplicitCauseIdsNotText() {
        Diagnostic cause = diagnostic("diag-cause", "diagnostic.example.cause",
                Optional.empty(), List.of());
        Diagnostic dependent = diagnostic("diag-dependent", "diagnostic.example.dependent",
                Optional.of(cause.id()), List.of());
        Diagnostic independent = diagnostic("diag-independent", "diagnostic.example.other",
                Optional.empty(), List.of());
        DiagnosticView view = new DiagnosticPresentation().project(
                List.of(cause, dependent, independent),
                new DiagnosticDisplayPolicy(true, 100));
        assertEquals(List.of(cause, independent), view.visibleDiagnostics());
        assertEquals(1, view.suppressedDiagnostics().size());
        assertEquals(DiagnosticView.SuppressionReason.DEPENDENT_ON_VISIBLE_ERROR,
                view.suppressedDiagnostics().getFirst().reason());
        assertEquals(cause.id(), view.suppressedDiagnostics().getFirst().visibleCause());
        assertEquals(1, view.hiddenCount());
    }

    @Test
    void fullListRemainsAvailableWhenPolicyDoesNotSuppress() {
        Diagnostic cause = diagnostic("diag-cause", "diagnostic.example.cause",
                Optional.empty(), List.of());
        Diagnostic dependent = diagnostic("diag-dependent", "diagnostic.example.dependent",
                Optional.of(cause.id()), List.of());
        DiagnosticView view = new DiagnosticPresentation().project(
                List.of(cause, dependent), new DiagnosticDisplayPolicy(false, 100));
        assertEquals(2, view.visibleDiagnostics().size());
        assertTrue(view.suppressedDiagnostics().isEmpty());
    }

    private static Diagnostic diagnostic(String id, String messageKey,
                                         Optional<DiagnosticId> cause,
                                         List<DiagnosticArgument> arguments) {
        return new Diagnostic(new DiagnosticId(id), new DiagnosticCode("ILI-SEM-0001"),
                Severity.ERROR, Phase.BINDING, messageKey, arguments, Optional.empty(),
                List.of(), cause, List.of(), Optional.empty(), Optional.empty(), List.of());
    }
}
