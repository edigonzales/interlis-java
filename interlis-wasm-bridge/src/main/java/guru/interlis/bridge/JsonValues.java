package guru.interlis.bridge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small construction helpers; all object orders are explicit. */
final class JsonValues {
    /**
     * Returns the {@code JsonValues} value.
     *
     * @return the value
     */
    private JsonValues() {
    }

    static JsonValue.JsonObject object(Object... pairs) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (JsonValue) pairs[i + 1]);
        }
        return new JsonValue.JsonObject(map);
    }

    static JsonValue.JsonArray array(List<? extends JsonValue> values) {
        return new JsonValue.JsonArray(new ArrayList<>(values));
    }

    static JsonValue.JsonString string(String value) {
        return new JsonValue.JsonString(value);
    }

    static JsonValue.JsonNumber number(long value) {
        String raw = Long.toString(value);
        return new JsonValue.JsonNumber(new BigDecimal(raw), raw);
    }

    static JsonValue.JsonNumber decimal(BigDecimal value) {
        return new JsonValue.JsonNumber(value, value.toPlainString());
    }

    static JsonValue.JsonBoolean bool(boolean value) {
        return new JsonValue.JsonBoolean(value);
    }

    static JsonValue.JsonArray strings(List<String> values) {
        return array(values.stream().map(JsonValue.JsonString::new).toList());
    }
}
