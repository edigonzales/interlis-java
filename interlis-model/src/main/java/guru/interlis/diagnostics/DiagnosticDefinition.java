package guru.interlis.diagnostics;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable catalog entry. {@code positional} records whether a primary source
 * range is mandatory, optional or forbidden for the code.
 */
public record DiagnosticDefinition(
        DiagnosticCode code,
        String meaning,
        Severity defaultSeverity,
        Set<Phase> allowedPhases,
        String messageKey,
        List<ArgumentDefinition> arguments,
        PositionRule positionRule,
        String catalogVersion) {

    /** Creates a validated instance. */
    public DiagnosticDefinition {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(meaning, "meaning");
        Objects.requireNonNull(defaultSeverity, "defaultSeverity");
        allowedPhases = Set.copyOf(Objects.requireNonNull(allowedPhases, "allowedPhases"));
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(positionRule, "positionRule");
        Objects.requireNonNull(catalogVersion, "catalogVersion");
    }

    /**
     * Executes {@code argument}.
     *
     * @param name the {@code name} argument
     *
     * @return the result
     */
    public Optional<ArgumentDefinition> argument(String name) {
        return arguments.stream().filter(a -> a.name().equals(name)).findFirst();
    }

    public enum PositionRule {
        /** The {@code REQUIRED} constant. */
        REQUIRED,
        /** The {@code OPTIONAL} constant. */
        OPTIONAL,
        /** The {@code FORBIDDEN} constant. */
        FORBIDDEN
    }
}
