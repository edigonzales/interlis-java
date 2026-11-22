package guru.interlis.db;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import guru.interlis.db.DbMapping.Kind;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.Symbol;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.TextType;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.BooleanValue;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import guru.interlis.transfer.TransferValue.Undefined;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The ili2db representation of single-field values stored together in one column. */
record DbArray(Optional<CollectionKind> collection, Optional<Wrapper> wrapper, Kind kind, TypeDescriptor type,
               Optional<SymbolRef> target, Optional<DbEnumeration> enumeration) {
    record Wrapper(SymbolRef structure, AttributeSlot field) { }

    private static final int MAX_ELEMENTS = 1_000_000;

    static DbArray describe(TransferPlan plan, DbInheritance layout, ObjectDescriptor owner, AttributeSlot outer) {
        String path = outer.name().declared().toString();
        var type = DbStructureShape.type(plan, outer);
        Optional<CollectionKind> collection = Optional.empty();
        if (type instanceof CollectionType repeated) {
            collection = Optional.of(repeated.collectionKind());
            type = plan.type(new TypeRef(plan.snapshotId(), repeated.elementType())).known().orElseThrow();
        }
        Optional<Wrapper> wrapper = Optional.empty();
        if (type instanceof StructureValueType) {
            var structure = DbStructureShape.target(plan, type)
                    .filter(target -> target.kind() == SymbolKind.STRUCTURE && !target.modifiers().contains(Modifier.ABSTRACT))
                    .orElseThrow(() -> DbMapping.unsupported("coalesced array structure at " + path));
            var fields = DbMapping.attributes(structure);
            if (fields.size() != 1 || structure.baseType().isPresent()) {
                throw DbMapping.unsupported("coalesced array requires one structure field at " + path);
            }
            var field = fields.getFirst();
            wrapper = Optional.of(new Wrapper(structure.symbol(), field));
            type = DbStructureShape.type(plan, field);
        } else if (collection.isEmpty()) {
            throw DbMapping.unsupported("direct coalesced array of " + path);
        }
        while (type instanceof DomainReferenceType alias) {
            var domain = DbDomains.domain(plan, alias.targetDomain());
            type = plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        }
        if (type instanceof ReferenceType reference) {
            var target = reference.target().map(id -> new SymbolRef(plan.snapshotId(), id)).flatMap(plan::object).orElse(null);
            if (reference.external() || !reference.restrictions().isEmpty() || target == null
                    || target.kind() != SymbolKind.CLASS || !target.topic().equals(owner.topic())
                    || target.modifiers().contains(Modifier.ABSTRACT)) {
                throw DbMapping.unsupported("coalesced array requires a concrete local reference target at " + path);
            }
            var symbol = target.symbol();
            if (layout.relations().stream().noneMatch(relation -> relation.object().symbol().equals(symbol)
                    && relation.base().isEmpty() && !relation.discriminator())) {
                throw DbMapping.unsupported("coalesced array requires a single target table at " + path);
            }
            return new DbArray(collection, wrapper, Kind.REFERENCE, type, Optional.of(symbol), Optional.empty());
        }
        var kind = DbMapping.scalarKind(type);
        if (kind != Kind.TEXT && kind != Kind.BOOLEAN && kind != Kind.INTEGER && kind != Kind.ENUMERATION) {
            throw DbMapping.unsupported("coalesced array element at " + path);
        }
        return new DbArray(collection, wrapper, kind, type, Optional.empty(), Optional.empty());
    }

    DbArray withEnumeration(DbEnumeration encoding) {
        return new DbArray(collection, wrapper, kind, type, target, Optional.of(encoding));
    }

    Symbol enumDefinition(TransferPlan plan, AttributeSlot outer) {
        var member = wrapper.map(Wrapper::field).orElse(outer);
        return DbEnumeration.definition(plan, member, declaredElement(plan, outer));
    }

    TypeDescriptor declaredElement(TransferPlan plan, AttributeSlot outer) {
        var ref = wrapper.map(shape -> shape.field().declaredType()).orElseGet(outer::declaredType);
        var declared = plan.type(ref).known().orElseThrow();
        if (wrapper.isEmpty() && declared instanceof CollectionType repeated) {
            return plan.type(new TypeRef(plan.snapshotId(), repeated.elementType())).known().orElseThrow();
        }
        return declared;
    }

    int textLength() { return ((TextType) type).maxLength().orElse(0); }

    String pgElementType() {
        return switch (kind) {
            case TEXT -> textLength() == 0 ? "text" : "varchar";
            case ENUMERATION -> enumeration.isPresent() ? "int8" : "varchar";
            case BOOLEAN -> "bool";
            case INTEGER -> "int4";
            case REFERENCE -> "int8";
            default -> throw new IllegalStateException("Unsupported PostgreSQL array element " + kind);
        };
    }

    TransferValue assemble(List<?> values, String context) {
        if (values.isEmpty()) return null;
        if (collection.isEmpty() && values.size() != 1) {
            throw invalid("Single coalesced structure has multiple array elements", context);
        }
        var items = new ArrayList<TransferValue>(values.size());
        for (var value : values) {
            var element = value == null ? null : readElement(value, context);
            if (wrapper.isPresent()) {
                var structureType = wrapper.orElseThrow();
                items.add(new Structured(structureType.structure(), element == null ? Map.of()
                        : Map.of(structureType.field().symbol(), element)));
            } else {
                if (element == null) throw invalid("Null element in direct coalesced array", context);
                items.add(element);
            }
        }
        return collection.<TransferValue>map(kind -> new Repeated(kind, items)).orElseGet(items::getFirst);
    }

    List<Object> disassemble(TransferValue value, String context) {
        if (value == null || value == Undefined.INSTANCE) return List.of();
        List<TransferValue> items;
        if (collection.isEmpty()) {
            items = List.of(value);
        } else if (value instanceof Repeated repeated && repeated.kind() == collection.orElseThrow()) {
            items = repeated.values();
        } else {
            throw invalid("Unexpected array collection", context);
        }
        checkCount(items.size(), context);
        var result = new ArrayList<>(items.size());
        for (var item : items) {
            var element = item;
            if (wrapper.isPresent()) {
                var structureType = wrapper.orElseThrow();
                var field = structureType.field().symbol();
                if (!(item instanceof Structured structureValue) || !structureType.structure().equals(structureValue.type())
                        || structureValue.values().keySet().stream().anyMatch(key -> !key.equals(field))) {
                    throw invalid("Unexpected array structure", context);
                }
                element = structureValue.values().get(field);
            }
            boolean missing = element == null || element == Undefined.INSTANCE;
            if (target.isPresent() && missing) {
                throw invalid("Missing reference in coalesced array", context);
            }
            result.add(missing ? null : writeElement(element, context));
        }
        return result;
    }

    private TransferValue readElement(Object value, String context) {
        return switch (value) {
            case String text when kind == Kind.TEXT -> new Text(text);
            case String text when kind == Kind.ENUMERATION && enumeration.isEmpty() -> DbTransfer.readEnumeration(text, context);
            case Long code when enumeration.isPresent() -> enumeration.orElseThrow().read(code.longValue(), context);
            case Boolean bool when kind == Kind.BOOLEAN -> new BooleanValue(bool);
            case Integer number when kind == Kind.INTEGER -> new NumberValue(number.toString(), BigDecimal.valueOf(number));
            case Reference reference when kind == Kind.REFERENCE -> reference;
            default -> throw unexpectedElement(context);
        };
    }

    private Object writeElement(TransferValue value, String context) {
        return switch (value) {
            case Text text when kind == Kind.TEXT -> text.value();
            case Enumeration enumValue when kind == Kind.ENUMERATION -> enumeration.<Object>map(binding -> binding.number(enumValue, context))
                    .orElseGet(() -> String.join(".", enumValue.path()));
            case BooleanValue bool when kind == Kind.BOOLEAN -> bool.value();
            case NumberValue number when kind == Kind.INTEGER -> DbTransfer.integerValue(number, context);
            case Reference reference when kind == Kind.REFERENCE -> reference;
            default -> throw unexpectedElement(context);
        };
    }

    List<Object> readJson(String json, String context) {
        if (json == null) return List.of();
        if (json.isEmpty() || json.length() > DbJsonStructure.MAX_CODE_UNITS) throw invalid("Invalid or oversized array JSON", context);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            reader.beginArray();
            var values = new ArrayList<>();
            while (reader.hasNext()) {
                checkCount(values.size() + 1, context);
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    values.add(null);
                } else {
                    values.add(readJsonElement(reader, context));
                }
            }
            reader.endArray();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Unexpected data after array JSON", context);
            return values;
        } catch (IOException | IllegalStateException failure) {
            throw new DbException("Invalid array JSON at " + context, failure);
        }
    }

    String writeJson(List<Object> values, String context) {
        var output = new StringWriter();
        try (var writer = new JsonWriter(output)) {
            writer.setStrictness(Strictness.STRICT);
            writer.beginArray();
            for (var item : values) {
                switch (item) {
                    case null -> writer.nullValue();
                    case String text -> writer.value(text);
                    case Boolean bool -> writer.value(bool);
                    case Integer integer -> writer.value(integer);
                    case Long id when kind == Kind.REFERENCE -> writer.value(id.toString());
                    case Long code when enumeration.isPresent() -> writer.value(code);
                    default -> throw unexpectedElement(context);
                }
            }
            writer.endArray();
        } catch (IOException failure) {
            throw new DbException("Cannot encode array JSON at " + context, failure);
        }
        if (output.getBuffer().length() > DbJsonStructure.MAX_CODE_UNITS) throw invalid("Oversized array JSON", context);
        return output.toString();
    }

    private Object readJsonElement(JsonReader reader, String context) throws IOException {
        var expected = switch (kind) {
            case TEXT, REFERENCE -> JsonToken.STRING;
            case ENUMERATION -> enumeration.isPresent() ? JsonToken.NUMBER : JsonToken.STRING;
            case BOOLEAN -> JsonToken.BOOLEAN;
            case INTEGER -> JsonToken.NUMBER;
            default -> throw new IllegalStateException("Unsupported JSON array element " + kind);
        };
        if (reader.peek() != expected) throw unexpectedElement(context);
        return switch (kind) {
            case TEXT -> reader.nextString();
            case ENUMERATION -> {
                String code = reader.nextString();
                yield enumeration.isPresent() ? numericElement(code, context) : code;
            }
            case BOOLEAN -> reader.nextBoolean();
            case INTEGER -> DbTransfer.readInteger(reader.nextString(), context).exactValue().intValueExact();
            case REFERENCE -> numericElement(reader.nextString(), context);
            default -> throw new IllegalStateException("Unsupported JSON array element " + kind);
        };
    }

    private long numericElement(String value, String context) {
        try {
            long number = Long.parseLong(value);
            if (!value.equals(Long.toString(number))) throw unexpectedElement(context);
            return number;
        } catch (NumberFormatException failure) {
            throw unexpectedElement(context);
        }
    }

    private DbException unexpectedElement(String context) {
        return invalid("Expected a " + kind.name().toLowerCase(java.util.Locale.ROOT) + " array element", context);
    }

    static void checkCount(int size, String context) {
        if (size > MAX_ELEMENTS) throw invalid("Array exceeds " + MAX_ELEMENTS + " elements", context);
    }

    private static DbException invalid(String message, String context) {
        return new DbException(message + " at " + context);
    }
}
