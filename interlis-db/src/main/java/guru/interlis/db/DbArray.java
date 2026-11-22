package guru.interlis.db;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import guru.interlis.db.DbMapping.Kind;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.BooleanValue;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import guru.interlis.transfer.TransferValue.Undefined;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The ili2db representation of single-field values stored together in one column. */
record DbArray(CollectionKind collection, SymbolRef structure, AttributeSlot field, Kind kind, TypeDescriptor type) {
    private static final int MAX_ELEMENTS = 1_000_000;

    static DbArray describe(TransferPlan plan, AttributeSlot outer) {
        String path = outer.name().declared().toString();
        if (!(DbStructureShape.type(plan, outer) instanceof CollectionType repeated)
                || repeated.collectionKind() != CollectionKind.LIST) {
            throw DbMapping.unsupported("coalesced array at " + path);
        }
        var type = plan.type(new TypeRef(plan.snapshotId(), repeated.elementType())).known().orElseThrow();
        var structure = DbStructureShape.target(plan, type)
                .filter(target -> target.kind() == SymbolKind.STRUCTURE && !target.modifiers().contains(Modifier.ABSTRACT))
                .orElseThrow(() -> DbMapping.unsupported("coalesced array structure at " + path));
        var fields = DbMapping.attributes(structure);
        if (fields.size() != 1 || structure.baseType().isPresent()) {
            throw DbMapping.unsupported("coalesced array requires one structure field at " + path);
        }
        var field = fields.getFirst();
        var scalar = DbMapping.structureScalar(plan, structure, field);
        if (scalar.kind() != Kind.TEXT && scalar.kind() != Kind.BOOLEAN) throw DbMapping.unsupported("coalesced array element at " + path);
        return new DbArray(repeated.collectionKind(), structure.symbol(), field, scalar.kind(), scalar.effectiveType().orElseThrow());
    }

    String pgElementType() { return kind == Kind.BOOLEAN ? "bool" : "varchar"; }

    TransferValue assemble(List<?> values, String context) {
        if (values.isEmpty()) return null;
        var items = new ArrayList<TransferValue>(values.size());
        for (var value : values) {
            var element = value == null ? null : readElement(value, context);
            items.add(new Structured(structure, element == null ? Map.of() : Map.of(field.symbol(), element)));
        }
        return new Repeated(collection, items);
    }

    List<Object> disassemble(TransferValue value, String context) {
        if (value == null || value == Undefined.INSTANCE) return List.of();
        if (!(value instanceof Repeated repeated) || repeated.kind() != collection) {
            throw invalid("Unexpected array collection", context);
        }
        checkCount(repeated.values().size(), context);
        var result = new ArrayList<>(repeated.values().size());
        for (var item : repeated.values()) {
            if (!(item instanceof Structured structureValue) || !structure.equals(structureValue.type())
                    || structureValue.values().keySet().stream().anyMatch(key -> !key.equals(field.symbol()))) {
                throw invalid("Unexpected array structure", context);
            }
            var element = structureValue.values().get(field.symbol());
            result.add(element == null || element == Undefined.INSTANCE ? null : writeElement(element, context));
        }
        return result;
    }

    private TransferValue readElement(Object value, String context) {
        if (kind == Kind.BOOLEAN) {
            if (value instanceof Boolean bool) return new BooleanValue(bool);
            throw invalid("Expected a boolean array element", context);
        }
        if (value instanceof String text) return new Text(text);
        throw invalid("Expected a text array element", context);
    }

    private Object writeElement(TransferValue value, String context) {
        if (kind == Kind.BOOLEAN) {
            if (value instanceof BooleanValue bool) return bool.value();
            throw invalid("Expected a boolean array element", context);
        }
        if (value instanceof Text text) return text.value();
        throw invalid("Expected a text array element", context);
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
                } else if (kind == Kind.BOOLEAN) {
                    if (reader.peek() != JsonToken.BOOLEAN) throw invalid("Expected a boolean array element", context);
                    values.add(reader.nextBoolean());
                } else {
                    if (reader.peek() != JsonToken.STRING) throw invalid("Expected a text array element", context);
                    values.add(reader.nextString());
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
                if (kind == Kind.BOOLEAN) writer.value((Boolean) item);
                else writer.value((String) item);
            }
            writer.endArray();
        } catch (IOException failure) {
            throw new DbException("Cannot encode array JSON at " + context, failure);
        }
        if (output.getBuffer().length() > DbJsonStructure.MAX_CODE_UNITS) throw invalid("Oversized array JSON", context);
        return output.toString();
    }

    static void checkCount(int size, String context) {
        if (size > MAX_ELEMENTS) throw invalid("Array exceeds " + MAX_ELEMENTS + " elements", context);
    }

    private static DbException invalid(String message, String context) {
        return new DbException(message + " at " + context);
    }
}
