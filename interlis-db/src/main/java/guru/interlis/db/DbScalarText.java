package guru.interlis.db;

import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Binary;
import guru.interlis.transfer.TransferValue.BooleanValue;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.transfer.TransferValue.Oid;
import guru.interlis.transfer.TransferValue.Text;
import guru.interlis.transfer.TransferValue.XmlFragment;
import guru.interlis.xtf24.Xtf24Limits;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Set;

/** The textual SQL representation of a simple INTERLIS attribute. */
final class DbScalarText {
    private static final int BLACKBOX_LIMIT = Xtf24Limits.defaults().maxScalarCodeUnits();

    private DbScalarText() { }

    static void checkProfile(Set<Ili2dbLayout.SchemaOption> options, DbEnumOptions enums,
                             Set<Ili2dbLayout.AttributeTransformation> transformations) {
        if (!options.contains(Ili2dbLayout.SchemaOption.SCALAR_TEXT)) return;
        if (!enums.equals(DbEnumOptions.DEFAULT)) {
            throw DbMapping.unsupported("SqlColsAsText with enumeration schema options");
        }
        if (transformations.contains(Ili2dbLayout.AttributeTransformation.EXPANDED_STRUCTURE)) {
            throw DbMapping.unsupported("SqlColsAsText with expanded structures");
        }
    }

    static TransferValue read(DbMapping.Kind kind, String value, String context) {
        if (value == null) return null;
        try {
            return switch (kind) {
                case TEXT, FORMATTED, DATE, TIME, DATETIME -> new Text(value);
                case BOOLEAN -> switch (value) {
                    case "true" -> new BooleanValue(true);
                    case "false" -> new BooleanValue(false);
                    default -> throw new IllegalArgumentException("Expected true or false");
                };
                case INTEGER, BIGINT, DECIMAL -> {
                    if (value.length() > 4096) throw new IllegalArgumentException("Oversized numeric lexeme");
                    yield new NumberValue(value, new BigDecimal(value));
                }
                case ENUMERATION -> DbTransfer.readEnumeration(value, context);
                case UUID, OID_TEXT -> new Oid(value);
                case BINARY -> new Binary(Base64.getDecoder().decode(blackbox(value).replaceAll("[ \\r\\n\\t]", "")));
                case XML -> new XmlFragment(blackbox(value));
                default -> throw new IllegalStateException("Expected a simple attribute: " + kind);
            };
        } catch (IllegalArgumentException failure) {
            throw new DbException("Invalid " + kind + " text at " + context, failure);
        }
    }

    static String write(TransferValue value) {
        return switch (value) {
            case Text text -> text.value();
            case BooleanValue bool -> Boolean.toString(bool.value());
            case NumberValue number -> number.lexeme();
            case Enumeration enumeration -> String.join(".", enumeration.path());
            case Oid oid -> oid.lexeme();
            case Binary binary -> Base64.getEncoder().encodeToString(binary.bytes());
            case XmlFragment fragment -> fragment.xml();
            default -> throw new IllegalStateException("Expected a simple attribute value");
        };
    }

    private static String blackbox(String value) {
        if (value.length() > BLACKBOX_LIMIT) throw new IllegalArgumentException("Oversized BLACKBOX");
        return value;
    }
}
