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
            case MANDATORY_CONSTRAINT_FALSE -> "Mandatory constraint evaluated to false.";
            case LOCAL_UNIQUE_DUPLICATE -> "Local structure key duplicates an earlier occurrence.";
            case AMBIGUOUS_OBJECT_IDENTITY -> "More than one object has the referenced identity.";
            case REFERENCE_TARGET_MISSING -> "The referenced object is absent from the closed extent.";
            case ORDER_POSITION_DUPLICATE -> "Ordered relationship contains a duplicate position.";
            case ORDER_POSITION_GAP -> "Complete ordered relationship contains a gap.";
            case COMPOSITION_MULTIPLE_OWNERS -> "A composition part has more than one owner.";
            case COMPOSITION_CYCLE -> "Composition ownership contains a cycle.";
            case REFERENCE_TARGET_TYPE -> "The referenced object is outside the allowed type hierarchy.";
            case REFERENCE_TARGET_BASKET -> "The referenced object is outside the allowed basket.";
            case DOMAIN_CONSTRAINT_FALSE -> "Domain constraint evaluated to false.";
            case STRUCTURE_TYPE_MISMATCH -> "Structure type is outside the allowed hierarchy.";
        };
    }
}
