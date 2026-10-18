package guru.interlis.bridge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON parser/writer for the portable protocol. */
public final class Json {
    /**
     * Returns the {@code Json} value.
     *
     * @return the value
     */
    private Json() {
    }

    /**
     * Executes {@code parse}.
     *
     * @param text the {@code text} argument
     *
     * @return the result
     */
    public static JsonValue parse(String text) {
        Parser parser = new Parser(text);
        JsonValue value = parser.value();
        parser.whitespace();
        if (!parser.end()) {
            throw new JsonException("trailing content at " + parser.position());
        }
        return value;
    }

    /**
     * Executes {@code write}.
     *
     * @param value the {@code value} argument
     *
     * @return the result
     */
    public static String write(JsonValue value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(JsonValue value, StringBuilder out) {
        switch (value) {
            case JsonValue.JsonObject object -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> entry : object.members().entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeString(entry.getKey(), out);
                    out.append(':');
                    write(entry.getValue(), out);
                }
                out.append('}');
            }
            case JsonValue.JsonArray array -> {
                out.append('[');
                for (int i = 0; i < array.values().size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    write(array.values().get(i), out);
                }
                out.append(']');
            }
            case JsonValue.JsonString string -> writeString(string.value(), out);
            case JsonValue.JsonNumber number -> out.append(number.raw());
            case JsonValue.JsonBoolean bool -> out.append(bool.value());
            case JsonValue.JsonNull ignored -> out.append("null");
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int j = hex.length(); j < 4; j++) {
                            out.append('0');
                        }
                        out.append(hex);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int position;

        private Parser(String text) {
            this.text = text;
        }

        /**
         * Returns the {@code value} value.
         *
         * @return the value
         */
        JsonValue value() {
            whitespace();
            if (end()) {
                throw new JsonException("unexpected end of JSON");
            }
            char c = text.charAt(position);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> new JsonValue.JsonString(string());
                case 't' -> literal("true", new JsonValue.JsonBoolean(true));
                case 'f' -> literal("false", new JsonValue.JsonBoolean(false));
                case 'n' -> literal("null", JsonValue.NULL);
                default -> number();
            };
        }

        /**
         * Returns the {@code object} value.
         *
         * @return the value
         */
        private JsonValue object() {
            expect('{');
            whitespace();
            Map<String, JsonValue> members = new LinkedHashMap<>();
            if (peek('}')) {
                position++;
                return new JsonValue.JsonObject(members);
            }
            while (true) {
                whitespace();
                String key = string();
                whitespace();
                expect(':');
                JsonValue value = value();
                if (members.put(key, value) != null) {
                    throw new JsonException("duplicate member " + key);
                }
                whitespace();
                if (peek(',')) {
                    position++;
                } else {
                    expect('}');
                    break;
                }
            }
            return new JsonValue.JsonObject(members);
        }

        /**
         * Returns the {@code array} value.
         *
         * @return the value
         */
        private JsonValue array() {
            expect('[');
            whitespace();
            List<JsonValue> values = new ArrayList<>();
            if (peek(']')) {
                position++;
                return new JsonValue.JsonArray(values);
            }
            while (true) {
                values.add(value());
                whitespace();
                if (peek(',')) {
                    position++;
                } else {
                    expect(']');
                    break;
                }
            }
            return new JsonValue.JsonArray(values);
        }

        /**
         * Returns the {@code string} value.
         *
         * @return the value
         */
        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!end()) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (end()) {
                        throw new JsonException("unterminated escape");
                    }
                    char escape = text.charAt(position++);
                    switch (escape) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (position + 4 > text.length()) {
                                throw new JsonException("short unicode escape");
                            }
                            out.append((char) Integer.parseInt(text.substring(position,
                                    position + 4), 16));
                            position += 4;
                        }
                        default -> throw new JsonException("invalid escape " + escape);
                    }
                } else {
                    out.append(c);
                }
            }
            throw new JsonException("unterminated string");
        }

        /**
         * Returns the {@code number} value.
         *
         * @return the value
         */
        private JsonValue number() {
            int start = position;
            if (peek('-')) {
                position++;
            }
            while (!end() && Character.isDigit(text.charAt(position))) {
                position++;
            }
            if (!end() && text.charAt(position) == '.') {
                position++;
                while (!end() && Character.isDigit(text.charAt(position))) {
                    position++;
                }
            }
            if (!end() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                position++;
                if (!end() && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                    position++;
                }
                while (!end() && Character.isDigit(text.charAt(position))) {
                    position++;
                }
            }
            if (start == position) {
                throw new JsonException("invalid value at " + position);
            }
            String raw = text.substring(start, position);
            try {
                return new JsonValue.JsonNumber(new BigDecimal(raw), raw);
            } catch (NumberFormatException e) {
                throw new JsonException("invalid number " + raw);
            }
        }

        private JsonValue literal(String literal, JsonValue value) {
            if (!text.startsWith(literal, position)) {
                throw new JsonException("invalid literal at " + position);
            }
            position += literal.length();
            return value;
        }

        private void expect(char expected) {
            if (end() || text.charAt(position) != expected) {
                throw new JsonException("expected '" + expected + "' at " + position);
            }
            position++;
        }

        private boolean peek(char c) {
            return !end() && text.charAt(position) == c;
        }

        /**
         * Returns the {@code end} value.
         *
         * @return the value
         */
        private boolean end() {
            return position >= text.length();
        }

        /**
         * Returns the {@code whitespace} value.
         *
         * @return the value
         */
        private void whitespace() {
            while (!end() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        /**
         * Returns the {@code position} value.
         *
         * @return the value
         */
        private int position() {
            return position;
        }
    }
}
