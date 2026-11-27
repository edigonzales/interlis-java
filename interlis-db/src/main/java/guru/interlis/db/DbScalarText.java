package guru.interlis.db;

import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferSlot;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.AttributePath;
import guru.interlis.transfer.TransferValue.Binary;
import guru.interlis.transfer.TransferValue.BooleanValue;
import guru.interlis.transfer.TransferValue.ClassName;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.transfer.TransferValue.Oid;
import guru.interlis.transfer.TransferValue.Text;
import guru.interlis.transfer.TransferValue.XmlFragment;
import guru.interlis.xtf24.Xtf24Limits;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The textual SQL representation of a simple INTERLIS attribute. */
final class DbScalarText {
    private static final int BLACKBOX_LIMIT = Xtf24Limits.defaults().maxScalarCodeUnits();
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern TIME = Pattern.compile("[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?");
    private static final Pattern DATETIME = Pattern.compile(DATE.pattern() + "T" + TIME.pattern());

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
                case DATE, TIME, DATETIME -> new Text(temporalLexeme(kind, value, context));
                case TEXT, FORMATTED -> new Text(value);
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
                case CLASS_NAME -> new ClassName(QualifiedName.of(value.split("\\.", -1)));
                case ATTRIBUTE_PATH -> new AttributePath(QualifiedName.of(value.split("\\.", -1)));
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
            case ClassName name -> name.name().toString();
            case AttributePath path -> path.name().toString();
            case Binary binary -> Base64.getEncoder().encodeToString(binary.bytes());
            case XmlFragment fragment -> fragment.xml();
            default -> throw new IllegalStateException("Expected a simple attribute value");
        };
    }

    static Set<SymbolRef> rawSlots(DbMapping mapping) {
        if (!mapping.schemaOptions().contains(Ili2dbLayout.SchemaOption.SCALAR_TEXT)) {
            throw new DbException("--raw-scalar-text requires SETTINGS.SqlColsAsText=enable");
        }
        return mapping.tables().stream().flatMap(table -> table.columns().stream())
                .filter(column -> column.scalarText(mapping.schemaOptions()))
                .map(DbMapping.Column::slot).map(TransferSlot::symbol).collect(Collectors.toUnmodifiableSet());
    }

    static String temporalLexeme(DbMapping.Kind kind, String value, String context) {
        var pattern = switch (kind) {
            case DATE -> DATE;
            case TIME -> TIME;
            case DATETIME -> DATETIME;
            default -> null;
        };
        if (pattern != null && !pattern.matcher(value).matches()) {
            throw new DbException("Invalid " + kind + " text at " + context);
        }
        return value;
    }

    private static String blackbox(String value) {
        if (value.length() > BLACKBOX_LIMIT) throw new IllegalArgumentException("Oversized BLACKBOX");
        return value;
    }
}
