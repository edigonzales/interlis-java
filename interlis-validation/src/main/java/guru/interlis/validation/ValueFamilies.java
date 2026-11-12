package guru.interlis.validation;

import guru.interlis.model.BlackboxKind;
import guru.interlis.model.BlackboxType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferValue;

final class ValueFamilies {
    private ValueFamilies() { }

    enum Match { YES, NO, UNSUPPORTED }

    static Match match(TypeDescriptor type, TransferValue value) {
        return switch (type.kind()) {
            case TEXT, FORMATTED -> is(value instanceof TransferValue.Text);
            case BOOLEAN -> is(value instanceof TransferValue.BooleanValue);
            case NUMERIC -> is(value instanceof TransferValue.NumberValue);
            case ALIGNMENT, ENUMERATION -> is(value instanceof TransferValue.Enumeration);
            case REFERENCE -> is(value instanceof TransferValue.Reference);
            case STRUCTURE_VALUE -> is(value instanceof TransferValue.Structured);
            case COLLECTION -> is(value instanceof TransferValue.Repeated);
            case OID -> is(value instanceof TransferValue.Oid);
            case CLASS_VALUE -> is(value instanceof TransferValue.ClassName);
            case ATTRIBUTE_PATH -> is(value instanceof TransferValue.AttributePath);
            case BLACKBOX -> is(((BlackboxType) type).blackboxKind() == BlackboxKind.BINARY
                    ? value instanceof TransferValue.Binary : value instanceof TransferValue.XmlFragment);
            case COORDINATE -> is(value instanceof TransferGeometry.Coordinate
                    || value instanceof TransferGeometry.MultiCoordinate);
            case GEOMETRY -> is(value instanceof TransferGeometry);
            case GENERIC, DEFERRED, ENUM_ARGUMENT, METAOBJECT, OBJECT_ARGUMENT, OBJECT_SET,
                 DOMAIN_REFERENCE, ERROR, PENDING -> Match.UNSUPPORTED;
        };
    }

    private static Match is(boolean matches) {
        return matches ? Match.YES : Match.NO;
    }
}
