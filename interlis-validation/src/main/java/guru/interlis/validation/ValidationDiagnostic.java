package guru.interlis.validation;

import guru.interlis.diagnostics.Severity;

import java.util.List;
import java.util.Objects;

/** A local violation, with separate data and model provenance. */
public record ValidationDiagnostic(ValidationCode code, Severity severity, DataLocation data,
                                   RuleLocation rule, List<String> arguments) {
    public ValidationDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(rule, "rule");
        arguments = List.copyOf(arguments);
    }

    /** English fallback message for presentation. */
    public String message() {
        return switch (code) {
            case REQUIRED_VALUE_MISSING -> "Required attribute is absent.";
            case REQUIRED_VALUE_UNDEFINED -> "Required value is undefined.";
            case CARDINALITY_VIOLATION -> "Collection cardinality is outside its bounds.";
            case TYPE_MISMATCH -> "Value has a different family than the model type.";
            case STRUCTURE_TYPE_MISMATCH -> "Structure type is outside the allowed hierarchy.";
        };
    }
}
