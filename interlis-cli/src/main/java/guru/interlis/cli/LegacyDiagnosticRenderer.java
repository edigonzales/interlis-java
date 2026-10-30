package guru.interlis.cli;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import java.util.Map;

/** Checking-profile text projection of canonical diagnostics, never a second validator. */
public final class LegacyDiagnosticRenderer {
    public String render(Diagnostic diagnostic, Map<SourceId, SourceText> sources) {
        String prefix = switch (diagnostic.severity()) {
            case ERROR -> "Error: ";
            case WARNING -> "Warning: ";
            case INFO, HINT -> "Info: ";
        };
        return prefix + new ConsoleDiagnosticRenderer().render(diagnostic, sources);
    }
}
