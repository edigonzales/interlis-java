package guru.interlis.verification.contracts;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.StreamSupport;

/** Small read-only helpers for the existing evidence format, never an acceptance engine. */
final class ContractFiles {
    private final Path repositoryRoot;
    ContractFiles(Path root) { repositoryRoot = root.toAbsolutePath().normalize(); }
    Path inside(String relative) throws IOException { return inside(repositoryRoot, relative); }
    String readText(String relative) throws IOException { return Files.readString(inside(relative)); }
    JsonObject readJson(String relative) throws IOException { return JsonParser.parseString(readText(relative)).getAsJsonObject(); }
    void requireHash(String relative, String sha) throws IOException { require(hash(inside(relative)).equals(sha), "Changed hash: " + relative); }
    static Path root() {
        String value = System.getProperty("repo.root");
        require(value != null && !value.isBlank(), "Missing repo.root for repository contracts");
        return Path.of(value).toAbsolutePath().normalize();
    }
    static final String BASE = "docs/evidence/stabilization/";
    static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
    static Path inside(Path root, String relative) throws IOException {
        Path rel = Path.of(relative);
        require(!rel.isAbsolute() && !List.of(relative.split("[/\\\\]")).contains(".."), "Unsafe evidence path: " + relative);
        Path path = root.resolve(rel).normalize();
        require(path.startsWith(root) && path.toRealPath().startsWith(root.toRealPath()), "Evidence escapes root: " + relative);
        return path;
    }
    static String text(String relative) throws IOException { return Files.readString(inside(root(), relative)); }
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
        require(hash(inside(root(), relative)).equals(sha), "Changed hash: " + relative);
    }
    static void pins(JsonObject pins) throws IOException {
        for (var entry : pins.entrySet()) pin(entry.getKey(), entry.getValue().getAsString());
    }
    /** Historic pypdf/Python offsets count Unicode code points, NOT UTF-16 code units. */
    static String slice(String text, int start, int end) {
        require(0 <= start && start <= end && end <= text.codePointCount(0, text.length()), "Invalid code-point range");
        return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
    }
    static Set<String> ids(List<JsonObject> rows, String key) {
        Set<String> result = new LinkedHashSet<>();
        for (var row : rows) require(result.add(s(row, key)), "Duplicate " + key + ": " + s(row, key));
        return result;
    }
}
