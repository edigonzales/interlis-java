package build.interlis;

import build.InterlisJvmBuild.BuildRequest;
import build.jenesis.*;
import build.jenesis.maven.*;
import java.nio.file.*;
import java.util.*;

/** Binds the GUI only to products of the current, locked Jenesis JVM invocation. */
final class GuiSdk {
    record Inputs(MavenRepository repository, List<ModuleArtifacts.Artifact> runtime, String digest) {
        void verify(List<Path> resolved) throws Exception {
            var hashes = new HashSet<String>();
            for (Path path : resolved) hashes.add(JvmBuildInputs.sha256(path));
            for (var artifact : runtime) if (!hashes.contains(artifact.sha256()))
                throw new IllegalStateException("GUI resolved different SDK bytes: " + artifact.coordinates());
        }
    }

    static Inputs prepare(Path root, Environment environment) throws Exception {
        var outcome = JenesisBridge.run(new BuildRequest("artifacts", null, null, root));
        var byCoordinate = new TreeMap<String, ModuleArtifacts.Artifact>();
        for (String module : List.of("interlis-projection", "interlis-workspace")) {
            for (var artifact : ModuleArtifacts.runtime(root, module, outcome)) {
                var previous = byCoordinate.putIfAbsent(artifact.coordinates(), artifact);
                if (previous != null && !previous.sha256().equals(artifact.sha256()))
                    throw new IllegalStateException("Conflicting SDK artifact: " + artifact.coordinates());
            }
        }
        var runtime = List.copyOf(byCoordinate.values());
        var files = new TreeMap<String, Path>();
        for (var artifact : runtime) {
            String[] id = artifact.coordinates().split("/");
            if (id[1].equals("guru.interlis")) {
                files.put(id[2] + "/" + id[3] + "/jar", artifact.file());
                files.put(id[2] + "/" + id[3] + "/pom", ModuleArtifacts.pom(outcome, id[2]));
            }
        }
        var fingerprint = new TreeMap<String, String>();
        for (var entry : files.entrySet()) fingerprint.put(entry.getKey(), JvmBuildInputs.sha256(entry.getValue()));
        for (var artifact : runtime) fingerprint.put(artifact.coordinates(), artifact.sha256());
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        String hash = HexFormat.of().formatHex(digest.digest(JsonOutput.write(fingerprint).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var central = MavenDefaultRepository.ofEnvironment(environment);
        MavenRepository repository = (executor, group, artifact, version, type, classifier, checksum) -> {
            if (!group.equals("guru.interlis")) return central.fetch(executor, group, artifact, version, type, classifier, checksum);
            Path file = files.get(artifact + "/" + version + "/" + Objects.requireNonNullElse(type, "jar"));
            return classifier != null || file == null ? Optional.empty() : Optional.of(RepositoryItem.ofFile(file, true));
        };
        return new Inputs(repository, runtime, hash);
    }
}
