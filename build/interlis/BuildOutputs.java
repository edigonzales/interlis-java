package build.interlis;

import build.jenesis.BuildExecutor;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Removes only the declared JVM or editor outputs. */
public final class BuildOutputs {
    private static final Map<String, List<String>> SCOPES = Map.of(
            "jvm", List.of("target/jenesis-jvm/reports",
                    "target/jenesis-jvm/tools", "target/jenesis-jvm/delivery",
                    "target/jenesis-jvm/conformance", "target/jenesis-jvm/module-checks",
                    "target/jenesis-jvm/gui", "target/jenesis-jvm/build-revisions"),
            "editor", List.of("target/jenesis-jvm/editor-bundles", "target/jenesis-jvm/editor-runs",
                    "target/jenesis-jvm/reports/editor-bundle.json"));

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !SCOPES.containsKey(args[0])) {
            throw new IllegalArgumentException("Expected jvm or editor");
        }
        clean(Path.of("").toAbsolutePath().normalize(), args[0]);
    }

    public static void clean(Path root, String scope) throws Exception {
        List<String> selected = SCOPES.get(scope);
        if (selected == null) throw new IllegalArgumentException("Unknown clean scope: " + scope);
        if (scope.equals("jvm")) {
            try (var lease = JvmBuildLock.acquire(root)) {
                Path engine = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/engine");
                if (Files.isDirectory(engine)) {
                    Path lock = JvmBuildInputs.requireOutputRoot(root,
                            "target/jenesis-jvm/engine/" + BuildExecutor.LOCK_MARKER);
                    try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                            var build = channel.tryLock()) {
                        if (build == null) throw new IllegalStateException("A Jenesis build is active");
                        deleteTree(engine, Set.of(engine, lock));
                    }
                }
                remove(root, selected);
            }
            return;
        }
        Path lock = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/editor-run.lock");
        Files.createDirectories(lock.getParent());
        try (var channel = java.nio.channels.FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lease = channel.tryLock()) {
            if (lease == null) throw new IllegalStateException("An editor bundle run is active");
            remove(root, selected);
        }
    }

    private static void remove(Path root, List<String> selected) throws Exception {
        Path boundary = root.toRealPath();
        for(String relative:selected) {
            Path output=root.resolve(relative).toAbsolutePath().normalize();
            for(Path parent=output.getParent();parent!=null&&parent.startsWith(root);parent=parent.getParent())
                if(Files.isSymbolicLink(parent))throw new IllegalStateException("Symlink in output parent: "+parent);
            if(!Files.exists(output,LinkOption.NOFOLLOW_LINKS))continue;
            if(!output.startsWith(root)||!output.getParent().toRealPath().startsWith(boundary))
                throw new IllegalStateException("Foreign clean output: "+output);
            deleteTree(output, Set.of());
        }
    }

    static void deleteTree(Path output, Set<Path> retained) throws Exception {
        try (var paths = Files.walk(output)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!retained.contains(path)) Files.delete(path);
            }
        }
    }
}
