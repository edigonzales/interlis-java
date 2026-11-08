package build.interlis;

import java.nio.file.*;
import java.util.*;

/** Removes only the declared JVM or editor outputs. */
public final class BuildOutputs {
    private static final Map<String, List<String>> SCOPES = Map.of(
            "jvm", List.of("target/jenesis-jvm/engine", "target/jenesis-jvm/reports",
                    "target/jenesis-jvm/tools", "target/jenesis-jvm/delivery", "target/jenesis-jvm/delivery-history",
                    "target/jenesis-jvm/conformance", "target/jenesis-jvm/module-checks", "target/jenesis-jvm/runtime-checks",
                    "target/jenesis-jvm/gui", "target/jenesis-jvm/build-revisions"),
            "editor", List.of("target/jenesis-jvm/editor-bundles", "target/jenesis-jvm/editor-runs",
                    "target/jenesis-jvm/reports/editor-bundle.json", "frontend/diagram-ui/dist", "frontend/diagram-ui/build"));

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
            // walk does not follow symbolic links; a link is removed, never its target.
            try(var paths=Files.walk(output)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);
            }
        }
    }
}
