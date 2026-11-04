package build.interlis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/** Deterministic file bindings. Missing files and escaping symbolic links fail closed. */
public final class JvmBuildInputs {
    public static final List<String> MODULES = List.of("interlis-model", "interlis-compiler",
            "interlis-workspace", "interlis-repository", "interlis-projection", "interlis-documentation",
            "interlis-wasm-bridge", "interlis-compiler-cli", "interlis-lsp", "interlis-verification");

    public static void requireProject(Path root) throws Exception {
        if (Runtime.version().feature() != 25) throw new IllegalStateException("Java25 required");
        var gradle = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("gradle.properties"))) { gradle.load(reader); }
        var pom = xml(root.resolve("pom.xml"));
        String version = pom.getDocumentElement().getElementsByTagName("version").item(0).getTextContent();
        if (!version.equals(gradle.getProperty("projectVersion")))
            throw new IllegalStateException("Product version drift: POM=" + version);
        var nodes = pom.getElementsByTagName("module");
        var actual = new ArrayList<String>();
        for (int i = 0; i < nodes.getLength(); i++) actual.add(nodes.item(i).getTextContent());
        if (actual.size() != MODULES.size() || !new HashSet<>(actual).equals(new HashSet<>(MODULES)))
            throw new IllegalStateException("Expected exactly ten JVM modules: " + actual);
        for (String module : MODULES) {
            var child = xml(root.resolve(module + "/pom.xml"));
            var parent = child.getElementsByTagName("parent").item(0);
            var versions = ((org.w3c.dom.Element) parent).getElementsByTagName("version");
            if (!versions.item(0).getTextContent().equals(version))
                throw new IllegalStateException("Child version drift: " + module);
        }
        // Source hashes are verified independently of the asserted upstream version.
        String manifest = Files.readString(root.resolve("build/JENESIS-UPSTREAM.json"));
        var entry = java.util.regex.Pattern.compile("\"([^\"]+)\": \"([0-9a-f]{64})\"").matcher(manifest);
        var expected = new TreeSet<Path>();
        while (entry.find()) {
            Path path = confined(root.resolve("build/jenesis"), entry.group(1));
            if (!sha256(path).equals(entry.group(2))) throw new IllegalStateException("Changed engine: " + path);
            expected.add(path);
        }
        try (var files = Files.walk(root.resolve("build/jenesis"))) {
            var actualFiles = new TreeSet<>(files.filter(Files::isRegularFile).toList());
            if (expected.isEmpty() || !expected.equals(actualFiles)) throw new IllegalStateException("Engine file set drift");
        }
    }

    public static Document xml(Path file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try (var input = Files.newInputStream(file)) { return factory.newDocumentBuilder().parse(input); }
    }

    public static Path confined(Path base, String relative) throws IOException {
        Path fragment = Path.of(relative);
        if (fragment.isAbsolute() || relative.contains("\\") || relative.contains(":"))
            throw new IllegalArgumentException("Expected relative input: " + relative);
        Path file = base.resolve(fragment).normalize();
        if (!file.startsWith(base) || !file.toRealPath().startsWith(base.toRealPath()))
            throw new IllegalArgumentException("Input escapes root: " + relative);
        return file;
    }

    public static String fingerprint(Path root, String module) throws Exception {
        return fingerprint(snapshot(root,module));
    }

    /** Relative names and content hashes in deterministic order; no clock or HEAD in cache inputs. */
    public record InputSnapshot(Map<String,String> files) {
        public InputSnapshot { files=Collections.unmodifiableMap(new TreeMap<>(files)); }
    }

    public static InputSnapshot snapshot(Path root,String module) throws Exception {
        var paths = new TreeSet<Path>();
        for (String p : List.of("pom.xml", "jenesis.properties", "build/InterlisJvmBuild.java", "build/interlis",
                "build/JENESIS-UPSTREAM.json", "build.jenesis", module + "/pom.xml", module + "/src")) {
            add(root, p, paths);
        }
        if (Files.exists(root.resolve(module + "/build.jenesis"))) add(root, module + "/build.jenesis", paths);
        Path matrix = root.resolve(module + "/build.jenesis/test-inputs.properties");
        if (Files.exists(matrix)) {
            var inputs = new Properties();
            try (var reader = Files.newBufferedReader(matrix)) { inputs.load(reader); }
            for (String value : inputs.values().stream().map(Object::toString).toList()) add(root, value, paths);
        }
        if (module.equals("interlis-verification"))
            for (String source : JenesisBridge.currentBindingInputs(root)) add(root, source, paths);
        var hashes=new TreeMap<String,String>();
        for(Path file:paths)hashes.put(root.relativize(file).toString().replace('\\','/'),sha256(file));
        return new InputSnapshot(hashes);
    }

    public static String fingerprint(InputSnapshot snapshot) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update("interlis-jvm-inputs-v1\n".getBytes(StandardCharsets.UTF_8));
        for (var file : snapshot.files().entrySet()) {
            digest.update((file.getKey() + "\0" + file.getValue() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void add(Path root, String relative, Set<Path> files) throws IOException {
        Path path = confined(root, relative);
        if (Files.isDirectory(path)) {
            try (var walk = Files.walk(path)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) files.add(confined(root, root.relativize(p).toString()));
            }
        } else files.add(path);
    }

    public static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) >= 0;) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
