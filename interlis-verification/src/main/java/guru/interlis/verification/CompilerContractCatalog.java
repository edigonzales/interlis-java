package guru.interlis.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import static guru.interlis.verification.HostChecks.require;
import static guru.interlis.verification.HostChecks.rows;
import static guru.interlis.verification.HostChecks.s;
import static guru.interlis.verification.HostChecks.strings;

/** Current compiler requirements, source pins and exact JUnit invocations. */
public final class CompilerContractCatalog {
    private static final String PATH = "testdata/verification/compiler-contracts.json";
    private final JsonObject document;

    private CompilerContractCatalog(JsonObject document) {
        this.document = document;
    }

    /** Loads version 1 of the catalog and checks its internal and repository references. */
    public static CompilerContractCatalog load(Path root) throws IOException {
        try (var reader = new JsonReader(Files.newBufferedReader(resolveInput(root, PATH)))) {
            reader.setStrictness(Strictness.STRICT);
            var document = readValue(reader).getAsJsonObject();
            require(reader.peek() == JsonToken.END_DOCUMENT, "Trailing catalog data");
            var catalog = new CompilerContractCatalog(document);
            catalog.validateStructure();
            catalog.validateReferences(root);
            return catalog;
        }
    }

    /** Returns a defensive copy for the selection and verification processes. */
    public JsonObject document() {
        return document.deepCopy();
    }

    private void validateStructure() {
        require(new JsonPrimitive(1).equals(document.get("schemaVersion")), "Unsupported compiler catalog schema");
        var bindings = document.getAsJsonObject("bindings");
        require(!bindings.isEmpty(), "Empty compiler contract selection");
        var requirements = new HashSet<String>();
        var invocations = new HashSet<List<String>>();
        for (String section : List.of("conditions", "requirements", "grammar", "builtins", "predefined", "special")) {
            var ids = new HashSet<String>();
            for (var row : rows(document, section)) {
                require(ids.add(s(row, "id")), "Duplicate catalog ID in " + section);
                if (row.has("links")) validateLinks(row, bindings);
            }
            if (section.equals("requirements")) requirements.addAll(ids);
        }
        document.getAsJsonObject("grammarGroups").entrySet().forEach(e -> validateLinks(e.getValue().getAsJsonObject(), bindings));
        for (var entry : bindings.entrySet()) {
            var binding = entry.getValue().getAsJsonObject();
            String module = s(binding, "module");
            String suite = s(binding, "suite");
            String method = s(binding, "method");
            require(!entry.getKey().isBlank() && !module.isBlank() && !suite.isBlank() && !method.isBlank(),
                    "Incomplete compiler test binding");
            require(s(binding, "source").equals(module + "/src/test/java/" + suite.replace('.', '/') + ".java"),
                    "Wrong source for " + entry.getKey());
            var required = strings(binding.get("invocations"));
            require(!required.isEmpty(), "Missing invocations for " + entry.getKey());
            for (String invocation : required) {
                require(invocation.startsWith(method + "(") && invocation.contains(")")
                        && invocations.add(List.of(module, suite, invocation)), "Duplicate or invalid invocation: " + invocation);
            }
            require(requirements.containsAll(strings(binding.get("requirementIds"))), "Unknown binding requirement");
        }
    }

    private static void validateLinks(JsonObject row, JsonObject bindings) {
        var links = strings(row.get("links"));
        require(new HashSet<>(links).size() == links.size() && links.stream().allMatch(bindings::has),
                "Unknown or duplicate catalog link");
    }

    private void validateReferences(Path root) throws IOException {
        for (var pin : document.getAsJsonObject("sourcePins").entrySet()) resolveInput(root, pin.getKey());
        for (var entry : document.getAsJsonObject("bindings").entrySet()) {
            resolveInput(root, s(entry.getValue().getAsJsonObject(), "source"));
        }
        for (var row : rows(document, "requirements")) {
            if (row.has("source")) resolveInput(root, s(row, "source"));
            if (row.has("processEvidence")) {
                for (var evidence : rows(row, "processEvidence")) resolveInput(root, s(evidence, "source"));
            }
        }
    }

    private static Path resolveInput(Path root, String relative) throws IOException {
        Path path = Path.of(relative);
        require(!path.isAbsolute() && !relative.contains("\\") && path.equals(path.normalize())
                        && !List.of(relative.split("/", -1)).contains(".."),
                "Unsafe compiler catalog path: " + relative);
        Path resolved = root.resolve(path);
        require(Files.isRegularFile(resolved) && resolved.toRealPath().startsWith(root.toRealPath()),
                "Missing or foreign compiler catalog input: " + relative);
        return resolved;
    }

    private static JsonElement readValue(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var result = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    require(!result.has(key), "Duplicate compiler catalog key: " + key);
                    result.add(key, readValue(reader));
                }
                reader.endObject();
                yield result;
            }
            case BEGIN_ARRAY -> {
                var result = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) result.add(readValue(reader));
                reader.endArray();
                yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IOException("Expected catalog value at " + reader.getPath());
        };
    }
}
