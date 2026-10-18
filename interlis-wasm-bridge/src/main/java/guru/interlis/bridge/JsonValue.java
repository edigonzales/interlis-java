package guru.interlis.bridge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal typed JSON tree without reflection or runtime class names. */
public sealed interface JsonValue permits JsonValue.JsonObject, JsonValue.JsonArray,
        JsonValue.JsonString, JsonValue.JsonNumber, JsonValue.JsonBoolean, JsonValue.JsonNull {

    /**
     * JSON object with stable insertion order.
     *
     * @param members immutable member map
     */
    record JsonObject(Map<String, JsonValue> members) implements JsonValue {
        /** Creates a validated instance. */
        public JsonObject {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }
    }

    /**
     * JSON array.
     *
     * @param values immutable ordered values
     */
    record JsonArray(List<JsonValue> values) implements JsonValue {
        /** Creates a validated instance. */
        public JsonArray {
            values = Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    /**
     * JSON string.
     *
     * @param value string value
     */
    record JsonString(String value) implements JsonValue {
    }

    /**
     * JSON number with exact decimal value and original lexical form.
     *
     * @param value exact decimal value
     * @param raw original lexical form
     */
    record JsonNumber(BigDecimal value, String raw) implements JsonValue {
    }

    /**
     * JSON boolean.
     *
     * @param value boolean value
     */
    record JsonBoolean(boolean value) implements JsonValue {
    }

    /** JSON null singleton. */
    record JsonNull() implements JsonValue {
    }

    JsonNull NULL = new JsonNull();
}
