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
            case TEXT_TOO_LONG -> "Text exceeds its model length.";
            case TEXT_CHARACTER_NOT_ALLOWED -> "Text contains a character outside its repertoire.";
            case INVALID_NAME -> "Value is not an INTERLIS name.";
            case INVALID_URI -> "Value is not an INTERLIS URI.";
            case VALUE_OUT_OF_RANGE -> "Value is outside its model range.";
            case ENUM_VALUE_NOT_ALLOWED -> "Enumeration value is outside its model domain.";
            case INVALID_TEMPORAL_VALUE -> "Value does not match its calendar or time domain.";
            case INVALID_OID -> "Identifier does not match its OID domain.";
            case REQUIRED_OID_MISSING -> "The required stable identifier is absent.";
            case REQUIRED_TID_MISSING -> "The required transfer identifier is absent.";
            case COORDINATE_DIMENSION_MISMATCH -> "Coordinate has the wrong number of axes.";
            case SEGMENT_FORM_NOT_ALLOWED -> "Segment form is not allowed by the model.";
            case MODEL_VALUE_NOT_ALLOWED -> "Model name is outside the allowed type or attribute path.";
            case STRUCTURE_TYPE_MISMATCH -> "Structure type is outside the allowed hierarchy.";
        };
    }
}
