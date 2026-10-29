package guru.interlis.cli.evidence;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/** Small read-only helpers for the existing evidence format, never an acceptance engine. */
final class EvidenceFiles {
    static final Path ROOT = Path.of(System.getProperty("repo.root")).toAbsolutePath().normalize();
    static final String BASE = "docs/evidence/stabilization/";
    static final Set<String> STEPS = Set.of("B1", "B2", "B3", "C1", "C2", "C3", "C4", "D1", "D2", "D3",
            "E1", "E2", "E3", "F1", "F2", "F3", "G1", "G2", "G3", "H1", "H2", "H3");
    static final String FUNCTIONS = "interlis-compiler/src/main/java/guru/interlis/compiler/internal/BuiltinFunctions.java";
    static final String PROFILE = "interlis-compiler/src/main/java/guru/interlis/compiler/internal/antlr/LanguageProfile.java";
    private EvidenceFiles() { }
    static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
    static Path inside(Path root, String relative) throws IOException {
        Path rel = Path.of(relative);
        require(!rel.isAbsolute() && !List.of(relative.split("[/\\\\]")).contains(".."), "Unsafe evidence path: " + relative);
        Path path = root.resolve(rel).normalize();
        require(path.startsWith(root) && path.toRealPath().startsWith(root.toRealPath()), "Evidence escapes root: " + relative);
        return path;
    }
    static String text(String relative) throws IOException { return Files.readString(inside(ROOT, relative)); }
    static JsonObject json(String relative) throws IOException { return JsonParser.parseString(text(relative)).getAsJsonObject(); }
    static JsonObject data(String name) throws IOException { return json(BASE + "g2-" + name + ".json"); }
    static List<JsonObject> rows(JsonObject value, String key) {
        return StreamSupport.stream(value.getAsJsonArray(key).spliterator(), false).map(JsonElement::getAsJsonObject).toList();
    }
    static List<String> strings(JsonElement array) {
        return StreamSupport.stream(array.getAsJsonArray().spliterator(), false).map(JsonElement::getAsString).toList();
    }
    static String s(JsonObject row, String key) { return row.get(key).getAsString(); }
    static int n(JsonObject row, String key) { return row.get(key).getAsInt(); }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    static String hash(String text) { return hash(text.getBytes(StandardCharsets.UTF_8)); }
    static String hash(Path path) throws IOException { return hash(Files.readAllBytes(path)); }
    static void pin(String relative, String sha) throws IOException {
        require(hash(inside(ROOT, relative)).equals(sha), "Changed hash: " + relative);
    }
    static void pins(JsonObject pins) throws IOException {
        for (var entry : pins.entrySet()) pin(entry.getKey(), entry.getValue().getAsString());
    }
    /** Historic pypdf/Python offsets count Unicode code points, NOT UTF-16 code units. */
    static String slice(String text, int start, int end) {
        require(0 <= start && start <= end && end <= text.codePointCount(0, text.length()), "Invalid code-point range");
        return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
    }
    static void anchor(JsonObject ref) throws IOException {
        String source = text(s(ref, "source"));
        require(n(ref, "start") < n(ref, "end") && hash(slice(source, n(ref, "start"), n(ref, "end"))).equals(s(ref, "sha256")), "Changed source anchor");
    }
    static String method(JsonObject row, String classKey, String methodKey) throws IOException {
        String source = s(row, "source"), full = text(source);
        require(source.startsWith("interlis-") && source.contains("/src/test/java/"), "Not a test source");
        String suite = s(row, classKey), method = s(row, methodKey);
        String simple = suite.substring(suite.lastIndexOf('.') + 1);
        var pkg = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(full);
        require(pkg.find() && suite.equals(pkg.group(1) + "." + simple) && source.endsWith("/" + simple + ".java"), "Wrong test class");
        String body = slice(full, n(row, "methodStart"), n(row, "methodEnd"));
        require(Pattern.compile("@Test\\s+(?:public\\s+)?void\\s+" + Pattern.quote(method) + "\\s*\\(").matcher(body).find()
                && hash(body).equals(s(row, "methodSha256")), "Changed or invented @Test method");
        if (row.has("sourceSha256")) require(hash(full).equals(s(row, "sourceSha256")), "Changed assertion source hash");
        return body;
    }
    static Set<String> ids(List<JsonObject> rows, String key) {
        Set<String> result = new LinkedHashSet<>();
        for (var row : rows) require(result.add(s(row, key)), "Duplicate " + key + ": " + s(row, key));
        return result;
    }
    static void acceptance(JsonObject row) {
        var a = row.getAsJsonObject("acceptance");
        require(a.keySet().equals(Set.of("positive", "negative", "modelOrQuery"))
                && a.entrySet().stream().allMatch(e -> !e.getValue().getAsString().isBlank()), "Incomplete acceptance mapping");
    }
    /** Python's historical sort_keys/ensure_ascii=False JSON hash, including U+2028/U+2029. */
    static String canonicalJson(JsonElement value) {
        if (value.isJsonObject()) return value.getAsJsonObject().keySet().stream().sorted()
                .map(k -> canonicalJson(new JsonPrimitive(k)) + ":" + canonicalJson(value.getAsJsonObject().get(k)))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        if (value.isJsonArray()) return StreamSupport.stream(value.getAsJsonArray().spliterator(), false)
                .map(EvidenceFiles::canonicalJson).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return value.toString();
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.getAsString().toCharArray()) switch (c) {
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
            case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f"); case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
        }
        return out.append('"').toString();
    }
}
