package guru.interlis.validation;

import guru.interlis.model.CollectionType;
import guru.interlis.model.NumericType;
import guru.interlis.model.TypeId;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferValue;

final class RuntimeTypeChecks {
    private RuntimeTypeChecks() { }

    static boolean accepts(ValidationPlan plan, TypeId typeId, boolean mandatory, TransferValue value, Runnable visit) {
        visit.run();
        if (value == TransferValue.Undefined.INSTANCE) {
            return !mandatory;
        }
        var type = plan.effectiveType(typeId).orElseThrow();
        return switch (type.kind()) {
            case BOOLEAN -> value instanceof TransferValue.BooleanValue;
            case TEXT, FORMATTED -> value instanceof TransferValue.Text;
            case NUMERIC -> value instanceof TransferValue.NumberValue number && fits((NumericType) type, number);
            case ENUMERATION, ENUM_ARGUMENT, ALIGNMENT -> value instanceof TransferValue.Enumeration;
            case COORDINATE -> value instanceof TransferGeometry.Coordinate || value instanceof TransferGeometry.MultiCoordinate;
            case GEOMETRY -> value instanceof TransferGeometry;
            case STRUCTURE_VALUE, OBJECT_ARGUMENT -> value instanceof TransferValue.Structured;
            case CLASS_VALUE -> value instanceof TransferValue.ClassName;
            case ATTRIBUTE_PATH -> value instanceof TransferValue.AttributePath;
            case REFERENCE -> value instanceof TransferValue.Reference;
            case OID -> value instanceof TransferValue.Oid;
            case COLLECTION -> value instanceof TransferValue.Repeated repeated && collection(plan,
                    (CollectionType) type, repeated, visit);
            default -> false;
        };
    }

    private static boolean fits(NumericType type, TransferValue.NumberValue number) {
        ScalarRules.requireNumericBudget(number.exactValue());
        return type.range().minimum().stream().allMatch(bound -> number.exactValue().compareTo(bound.exactValue()) >= 0)
                && type.range().maximum().stream().allMatch(bound -> number.exactValue().compareTo(bound.exactValue()) <= 0);
    }

    private static boolean collection(ValidationPlan plan, CollectionType type, TransferValue.Repeated repeated,
                                      Runnable visit) {
        var size = java.math.BigInteger.valueOf(repeated.values().size());
        return repeated.kind() == type.collectionKind()
                && size.compareTo(type.cardinality().lower()) >= 0
                && (!(type.cardinality().upper() instanceof guru.interlis.model.CardinalityUpper.Finite max)
                    || size.compareTo(max.value()) <= 0)
                && repeated.values().stream().allMatch(value -> accepts(plan, type.elementType(), true, value, visit));
    }
}
