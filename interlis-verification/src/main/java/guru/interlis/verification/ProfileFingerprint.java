package guru.interlis.verification;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static guru.interlis.verification.HostChecks.*;

/** Same explicit product/test/profile source binding as tools/verification/source-fingerprint.cjs. */
final class ProfileFingerprint {
    private ProfileFingerprint() { }
    static String commit(Path root) throws Exception {
        var result=run(root,List.of("git","rev-parse","HEAD"),Duration.ofSeconds(10));
        require(result.exitCode()==0&&result.stdout().trim().matches("[a-f0-9]{40}"),"Missing source commit");
        return result.stdout().trim();
    }
    static boolean dirty(Path root) throws Exception {
        var result=run(root,List.of("git","status","--porcelain"),Duration.ofSeconds(10));
        require(result.exitCode()==0,"Cannot inspect source state");return !result.stdout().isBlank();
    }
    static String current(Path root) throws Exception {
        var result=run(root,List.of("git","ls-files","--cached","--others","--exclude-standard","-z"),Duration.ofSeconds(10));
        require(result.exitCode()==0,"Cannot enumerate verification sources");
        var inputs = read(root.resolve("testdata/verification/source-inputs.json"));
        var paths = Set.copyOf(strings(inputs.get("paths")));
        var prefixes = strings(inputs.get("prefixes"));
        var suffixes = strings(inputs.get("suffixes"));
        var files = new TreeSet<String>();
        for (String file : result.stdout().split("\\x00")) {
            if (paths.contains(file) || prefixes.stream().anyMatch(file::startsWith)
                    || suffixes.stream().anyMatch(file::endsWith)) files.add(file);
        }
        require(files.size() > 500, "Incomplete verification source binding");
        Path realRoot = root.toRealPath();
        var text = new StringBuilder();
        for (String file : files) {
            Path path = realRoot.resolve(file);
            String digest = "<deleted>";
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(path) && path.toRealPath().equals(path), "Unsafe source input: " + file);
                digest = hash(path);
            }
            text.append(file).append('\0').append(digest).append('\n');
        }
        return hash(text.toString().getBytes(StandardCharsets.UTF_8));
    }
}
