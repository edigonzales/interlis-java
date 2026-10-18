package guru.interlis.lsp;

import org.eclipse.lsp4j.PublishDiagnosticsParams;

/** Narrow diagnostic publication port; keeps the adapter testable without a client. */
@FunctionalInterface
interface DiagnosticSink {
    void publish(PublishDiagnosticsParams diagnostics);
}
