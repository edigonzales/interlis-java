package guru.interlis.db;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import guru.interlis.db.DbMapping.Column;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.BooleanValue;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import guru.interlis.transfer.TransferValue.Undefined;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes the ili2db JSON representation of a structure value. */
record DbJsonStructure(SymbolRef structure, String typeName, Map<String, Column> fields) {
    static final int MAX_CODE_UNITS = 16_777_216;
    private static final String TYPE = "@type";

    DbJsonStructure {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    static DbJsonStructure describe(TransferPlan plan, AttributeSlot outer) {
        var object = DbStructureShape.target(plan, DbStructureShape.type(plan, outer))
                .filter(target -> target.kind() == SymbolKind.STRUCTURE && !target.modifiers().contains(Modifier.ABSTRACT))
                .orElseThrow(() -> DbMapping.unsupported("JSON structure at " + outer.name().declared()));
        var fields = new LinkedHashMap<String, Column>();
        for (var slot : object.slots()) {
            if (!(slot instanceof AttributeSlot field) || field.attribute().transientAttribute()) continue;
            fields.put(field.name().declared().last(), DbMapping.structureScalar(plan, object, field));
        }
        return new DbJsonStructure(object.symbol(), object.name().declared().toString(), fields);
    }

    TransferValue decode(String json, String context) {
        if (json == null) return null;
        if (json.isEmpty() || json.length() > MAX_CODE_UNITS) throw new DbException("Invalid or oversized ili2db JSON at " + context);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            var value = readObject(reader);
            requireToken(reader, JsonToken.END_DOCUMENT);
            return value;
        } catch (IOException | IllegalStateException | DbException failure) {
            throw new DbException("Invalid ili2db JSON at " + context + ": " + failure.getMessage(), failure);
        }
    }

    String encode(TransferValue value, String context) {
        if (value == null || value == Undefined.INSTANCE) return null;
        var output = new StringWriter();
        try (var writer = new JsonWriter(output)) {
            writer.setStrictness(Strictness.STRICT);
            writeObject(writer, value);
        } catch (IOException | IllegalStateException | DbException failure) {
            throw new DbException("Cannot encode ili2db JSON at " + context + ": " + failure.getMessage(), failure);
        }
        if (output.getBuffer().length() > MAX_CODE_UNITS) throw new DbException("Oversized ili2db JSON at " + context);
        return output.toString();
    }

    private Structured readObject(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT);
        reader.beginObject();
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        var seen = new HashSet<String>();
        String type = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!seen.add(name)) throw new DbException("Duplicate JSON member " + name);
            if (TYPE.equals(name)) {
                requireToken(reader, JsonToken.STRING);
                type = reader.nextString();
                continue;
            }
            var field = fields.get(name);
            if (field == null) throw new DbException("Unknown JSON structure member " + name);
            var value = readValue(reader, field);
            if (value != null) values.put(field.slot().symbol(), value);
        }
        reader.endObject();
        if (!typeName.equals(type)) throw new DbException("Unexpected JSON structure type " + type);
        return new Structured(structure, values);
    }

    private void writeObject(JsonWriter writer, TransferValue value) throws IOException {
        if (!(value instanceof Structured item) || !structure.equals(item.type())) {
            throw new DbException("Expected JSON structure " + typeName);
        }
        writer.beginObject();
        writer.name(TYPE).value(typeName);
        int used = 0;
        for (var entry : fields.entrySet()) {
            var field = entry.getValue();
            var member = item.values().get(field.slot().symbol());
            if (member == null) continue;
            used++;
            if (member == Undefined.INSTANCE) continue;
            writer.name(entry.getKey());
            switch (member) {
                case Text text -> writer.value(text.value());
                case BooleanValue bool -> writer.value(bool.value());
                case NumberValue number -> writer.value(DbTransfer.readInteger(number.lexeme(), field).exactValue());
                case Enumeration enumeration -> writer.value(String.join(".", enumeration.path()));
                default -> throw new DbException("Unsupported JSON structure member " + entry.getKey());
            }
        }
        if (used != item.values().size()) throw new DbException("Unmapped JSON structure member in " + typeName);
        writer.endObject();
    }

    private static TransferValue readValue(JsonReader reader, Column field) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return switch (field.kind()) {
            case TEXT -> {
                requireToken(reader, JsonToken.STRING);
                yield new Text(reader.nextString());
            }
            case BOOLEAN -> {
                requireToken(reader, JsonToken.BOOLEAN);
                yield new BooleanValue(reader.nextBoolean());
            }
            case INTEGER -> {
                requireToken(reader, JsonToken.NUMBER);
                yield DbTransfer.readInteger(reader.nextString(), field);
            }
            case ENUMERATION -> {
                requireToken(reader, JsonToken.STRING);
                yield DbTransfer.readEnumeration(reader.nextString(), field);
            }
            default -> throw new IllegalArgumentException("Expected a JSON scalar member");
        };
    }

    private static void requireToken(JsonReader reader, JsonToken expected) throws IOException {
        if (reader.peek() != expected) throw new DbException("Expected " + expected + " at " + reader.getPath());
    }
}
