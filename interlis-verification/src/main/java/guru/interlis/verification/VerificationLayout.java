package guru.interlis.verification;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;

/** Explicit repository layout; paths are absolute and normalized, never inferred from user.dir. */
final class VerificationLayout {
    private final Path root;
    VerificationLayout(Path repositoryRoot) { root = Objects.requireNonNull(repositoryRoot).toAbsolutePath().normalize(); }
    Path repositoryRoot() { return root; }
    Path compilerDistribution() { return root.resolve("interlis-compiler-cli/build/install/interlis-compiler"); }
    Path compilerLauncher(boolean windows) { return compilerDistribution().resolve("bin/interlis-compiler" + (windows ? ".bat" : "")); }
    Path junitDirectory(String module, String taskName) {
        segment(module); segment(taskName);
        return root.resolve(module).resolve("build/test-results").resolve(taskName);
    }
    Path verificationUnitReports() { return junitDirectory("interlis-verification", "test"); }
    Path verificationContractReports() { return junitDirectory("interlis-verification", "contractTest"); }
    /** Relative report path only; existing symlinks may not escape build/reports. */
    Path rootReport(String relativeName) throws IOException {
        Path relative = Path.of(relativeName);
        if (relative.isAbsolute() || relativeName.isBlank() || relativeName.contains("\\")
                || relativeName.matches("^[A-Za-z]:.*") || java.util.List.of(relativeName.split("/")).contains(".."))
            throw new IllegalArgumentException("Unsafe report path");
        Path base = root.resolve("build/reports"), target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) throw new IllegalArgumentException("Report escapes directory");
        // Also check the build/reports ancestry: a symlinked build must not redirect writes.
        for (Path p = target; p != null && p.startsWith(root); p = p.getParent()) {
            if (Files.isSymbolicLink(p) && !p.toRealPath().startsWith(root.toRealPath().resolve("build/reports")))
                throw new IllegalArgumentException("Report ancestor symlink escapes directory");
            if (Files.exists(p) && !p.toRealPath().startsWith(root.toRealPath().resolve("build/reports"))
                    && p.startsWith(base)) throw new IllegalArgumentException("Report symlink escapes directory");
        }
        return target;
    }
    private static void segment(String value) {
        if (!Objects.requireNonNull(value).matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("Expected one module/task segment");
    }
}
