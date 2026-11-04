package guru.interlis.verification;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Immutable host-only layout. Defaults preserve Gradle paths; an explicitly
 * selected Jenesis layout never falls back to them. All paths are root-relative
 * and confined, including existing symbolic-link ancestors.
 */
public final class VerificationLayout {
    private final Path root;
    private final Map<String, String> paths;
    public VerificationLayout(Path repositoryRoot) {
        this(repositoryRoot, Map.of());
    }
    private VerificationLayout(Path root, Map<String, String> paths) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        this.paths = Map.copyOf(paths);
    }
    public static VerificationLayout forInvocation(Path root) {
        String explicit = System.getProperty("verification.layout");
        if (explicit == null || explicit.isBlank()) return new VerificationLayout(root);
        try {
            Path file = Path.of(explicit).toAbsolutePath().normalize();
            String hash = System.getProperty("verification.layout.sha256");
            if (hash == null || !HostChecks.hash(file).equals(hash))
                throw new IllegalArgumentException("Missing/changed explicit layout hash");
            return read(root, file);
        } catch (IOException e) { throw new IllegalArgumentException("Cannot read explicit verification layout", e); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Cannot verify layout", e); }
    }
    public static VerificationLayout read(Path root, Path file) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        if (!file.toAbsolutePath().normalize().startsWith(normalized.resolve("target/jenesis-jvm"))
                || !file.toRealPath().startsWith(normalized.toRealPath().resolve("target/jenesis-jvm")))
            throw new IllegalArgumentException("Explicit layout outside Jenesis target");
        var data = new Properties();
        try (var reader = Files.newBufferedReader(file)) { data.load(reader); }
        if (!"1".equals(data.getProperty("schemaVersion")) || !"jenesis".equals(data.getProperty("builder"))
                || !normalized.toString().equals(data.getProperty("root")))
            throw new IllegalArgumentException("Foreign verification layout");
        Map<String, String> paths = new TreeMap<>();
        for (String key : data.stringPropertyNames()) {
            if (Set.of("schemaVersion", "builder", "root").contains(key)) continue;
            if (!Set.of("compiler", "lsp", "sdk", "reports", "norms").contains(key)
                    && !key.matches("junit\\.interlis-[a-z-]+\\.(test|contractTest|contractAssertionsTest)")
                    && !key.matches("manifest\\.interlis-[a-z-]+"))
                throw new IllegalArgumentException("Unknown layout key: " + key);
            paths.put(key, data.getProperty(key));
        }
        var layout = new VerificationLayout(normalized, paths);
        for (String key : paths.keySet()) layout.path(key, null);
        if (paths.isEmpty()) throw new IllegalArgumentException("Empty explicit layout");
        return layout;
    }
    public Path repositoryRoot() { return root; }
    public Path compilerDistribution() { return path("compiler", "interlis-compiler-cli/build/install/interlis-compiler"); }
    public Path compilerLauncher(boolean windows) { return compilerDistribution().resolve("bin/interlis-compiler" + (windows ? ".bat" : "")); }
    public Path lspDistribution() { return path("lsp", "interlis-lsp/build/install/interlis-lsp"); }
    public Path sdkArchive(String version) { segment(version); return path("sdk", "build/releases/interlis-java-sdk-" + version + ".zip"); }
    public Path junitDirectory(String module, String taskName) {
        segment(module); segment(taskName);
        return path("junit." + module + "." + taskName, module + "/build/test-results/" + taskName);
    }
    public Path contractAssertionManifest(String module) { segment(module); return path("manifest." + module, module + "/build/contract-assertions.json"); }
    public Path normativePdf(String languageVersion) {
        if (!Set.of("2.3", "2.4").contains(languageVersion)) throw new IllegalArgumentException("Unknown norm version");
        return path("norms", "build/norms").resolve("ili" + languageVersion.replace(".", "") + ".pdf");
    }
    public Path verificationUnitReports() { return junitDirectory("interlis-verification", "test"); }
    public Path verificationContractReports() { return junitDirectory("interlis-verification", "contractTest"); }
    public Path rootReport(String relativeName) throws IOException {
        Path relative = relative(relativeName);
        Path base = path("reports", "build/reports"), target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) throw new IllegalArgumentException("Report escapes directory");
        checkAncestors(target, base);
        return target;
    }
    private Path path(String key, String gradleDefault) {
        String value = paths.isEmpty() ? gradleDefault : paths.get(key);
        if (value == null) throw new IllegalArgumentException("Missing explicit layout path: " + key);
        Path target = root.resolve(relative(value)).normalize();
        Path boundary = paths.isEmpty() ? root : root.resolve("target/jenesis-jvm");
        if (!target.startsWith(boundary)) throw new IllegalArgumentException("Layout path escapes output boundary");
        try { checkAncestors(target, boundary); }
        catch (IOException e) { throw new IllegalArgumentException("Cannot resolve layout path", e); }
        return target;
    }
    private void checkAncestors(Path target, Path boundary) throws IOException {
        Path realBoundary = root.toRealPath().resolve(root.relativize(boundary));
        for (Path p = target; p != null && p.startsWith(boundary); p = p.getParent())
            if (Files.exists(p) && !p.toRealPath().startsWith(realBoundary))
                throw new IllegalArgumentException("Output ancestor symlink escapes directory");
        // A symbolic-link ancestor of the boundary itself must not redirect it.
        for (Path p = boundary.getParent(); p != null && p.startsWith(root); p = p.getParent())
            if (Files.isSymbolicLink(p) && !p.toRealPath().equals(root.toRealPath().resolve(root.relativize(p))))
                throw new IllegalArgumentException("Output root symlink escapes directory");
    }
    private static Path relative(String value) {
        Path path = Path.of(value);
        if (path.isAbsolute() || value.isBlank() || value.contains("\\") || value.contains(":")
                || List.of(value.split("/")).contains("..")) throw new IllegalArgumentException("Unsafe relative path");
        return path;
    }
    private static void segment(String value) {
        if (!Objects.requireNonNull(value).matches("[A-Za-z0-9][A-Za-z0-9_.-]*") || value.equals(".."))
            throw new IllegalArgumentException("Expected one module/task segment");
    }
}
