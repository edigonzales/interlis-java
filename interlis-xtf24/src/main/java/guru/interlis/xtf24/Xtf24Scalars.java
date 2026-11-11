package guru.interlis.xtf24;

import guru.interlis.model.FormattedType;
import guru.interlis.model.OidType;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;

final class Xtf24Scalars {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern NUMBER = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?(?:[Ee][+-]?[0-9]+)?");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern TIME = Pattern.compile("[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?");
    private static final Pattern DATE_TIME = Pattern.compile(DATE.pattern() + "T" + TIME.pattern());
    private static final int MAX_NUMERIC_UNITS = 4_096;
    private static final int MAX_EXPONENT = 10_000;

    private Xtf24Scalars() {}

    static TransferValue read(TypeDescriptor type, String value, Xtf24Limits limits) {
        return switch (type) {
            case TextType text -> text(value, text.textKind() == TextKind.MTEXT);
            case FormattedType formatted -> formatted(formatted, value);
            case OidType oid -> oid(oid, value, limits);
            default -> switch (type.kind()) {
                case BOOLEAN -> switch (value) {
                    case "true" -> new BooleanValue(true);
                    case "false" -> new BooleanValue(false);
                    default -> throw invalid("Expected XTF BOOLEAN true or false");
                };
                case ENUMERATION, ALIGNMENT -> enumeration(value, limits);
                case NUMERIC -> number(value);
                case CLASS_VALUE -> new ClassName(path(value, 2, 3, limits));
                case ATTRIBUTE_PATH -> new AttributePath(path(value, 3, 4, limits));
                default -> throw Xtf24Wire.failure(UNSUPPORTED, "XTF scalar type " + type.kind());
            };
        };
    }

    static String write(TypeDescriptor type, TransferValue value, Xtf24Limits limits) {
        if (value == Undefined.INSTANCE) {
            throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 cannot express an explicit undefined value");
        }
        String encoded = switch (type.kind()) {
            case TEXT, FORMATTED -> require(value, Text.class).value();
            case OID -> require(value, Oid.class).lexeme();
            case BOOLEAN -> Boolean.toString(require(value, BooleanValue.class).value());
            case ENUMERATION, ALIGNMENT -> String.join(".", require(value, Enumeration.class).path());
            case NUMERIC -> require(value, NumberValue.class).lexeme();
            case CLASS_VALUE -> require(value, ClassName.class).name().toString();
            case ATTRIBUTE_PATH -> require(value, AttributePath.class).name().toString();
            default -> throw Xtf24Wire.failure(UNSUPPORTED, "XTF scalar type " + type.kind());
        };
        if (!value.equals(read(type, encoded, limits))) {
            throw invalid("XTF value does not agree with its lexical representation");
        }
        return encoded;
    }

    static <T extends TransferValue> T require(TransferValue value, Class<T> expected) {
        if (!expected.isInstance(value)) {
            throw invalid("Expected XTF value of kind " + expected.getSimpleName());
        }
        return expected.cast(value);
    }

    static NumberValue number(String value) {
        if (value.length() > MAX_NUMERIC_UNITS) {
            throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XTF numeric lexeme exceeds its length limit");
        }
        if (!NUMBER.matcher(value).matches()) {
            throw invalid("Invalid XTF numeric lexeme");
        }
        try {
            BigDecimal exact = new BigDecimal(value);
            if (Math.abs((long) exact.scale()) > MAX_EXPONENT + MAX_NUMERIC_UNITS
                    || Math.abs((long) exact.precision() - exact.scale() - 1) > MAX_EXPONENT + MAX_NUMERIC_UNITS) {
                throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XTF numeric exponent is too large");
            }
            return new NumberValue(value, exact);
        } catch (NumberFormatException e) {
            throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XTF numeric exponent is too large");
        }
    }

    private static Enumeration enumeration(String value, Xtf24Limits limits) {
        var parts = path(value, 1, Integer.MAX_VALUE, limits).segments();
        if (parts.size() > 1 && parts.contains("OTHERS")) {
            throw invalid("OTHERS must be a complete XTF enumeration value");
        }
        return new Enumeration(parts);
    }

    private static Oid oid(OidType type, String value, Xtf24Limits limits) {
        Xtf24IdentityScope.requireIdentifier(value, limits.maxIdentifierCodeUnits());
        if (type.oidKind() == guru.interlis.model.OidKind.NUMERIC) {
            number(value.substring(value.indexOf(':') + 1));
        }
        return new Oid(value);
    }

    private static Text formatted(FormattedType type, String value) {
        type.predefined().ifPresent(kind -> {
            Pattern grammar = switch (kind) {
                case DATE -> DATE;
                case TIMEOFDAY -> TIME;
                case DATETIME -> DATE_TIME;
            };
            if (!grammar.matcher(value).matches()) {
                throw invalid("Invalid XTF date or time syntax");
            }
        });
        return text(value, false);
    }

    private static Text text(String value, boolean multiline) {
        if (!multiline && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\t') >= 0)) {
            throw invalid("Expected single-line XTF text");
        }
        return new Text(value);
    }

    private static QualifiedName path(String value, int min, int max, Xtf24Limits limits) {
        if (value.length() > limits.maxIdentifierCodeUnits()) {
            throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XTF model or enumeration path is too long");
        }
        var parts = List.of(value.split("\\.", -1));
        if (parts.size() < min || parts.size() > max || parts.stream().anyMatch(part -> !NAME.matcher(part).matches())) {
            throw invalid("Invalid XTF model or enumeration path");
        }
        return new QualifiedName(parts);
    }

    private static Xtf24Exception invalid(String message) {
        return Xtf24Wire.failure(INVALID_WIRE, message);
    }
}
