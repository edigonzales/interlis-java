package build.interlis;

import java.nio.file.*;
import java.util.*;

/** Finite output ownership. Callers hold the project lock; sources and dependency caches are never selected. */
public final class BuildOutputs {
    private static final Map<String,List<String>> SCOPES=Map.of(
            "jvm",List.of("engine","reports","tools","delivery","delivery-history","conformance","module-checks","runtime-checks"),
            "consumer",List.of("consumers","consumer-references","handoffs","editor-bundles","editor-runs"),
            "wasm",List.of("interlis-wasm-teavm/build","interlis-wasm-webimage/build"));

    public static void clean(Path root,String scope) throws Exception {
        if(!SCOPES.containsKey(scope))throw new IllegalArgumentException("Unknown clean scope: "+scope);
        if(!scope.equals("jvm")) {
            Path lock=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/consumer.lock");Files.createDirectories(lock.getParent());
            Path coordinator=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/wasm-run.lock");
            try(var editor=java.nio.channels.FileChannel.open(JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/editor-run.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                    var editorLease=editor.tryLock();
                    var orchestration=java.nio.channels.FileChannel.open(coordinator,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                    var runLease=orchestration.tryLock();
                    var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                    var lease=channel.tryLock()) {
                if(editorLease==null||runLease==null||lease==null)throw new IllegalStateException("A consumer is active");
                remove(root,scope);
            }
        } else remove(root,scope);
    }

    private static void remove(Path root,String scope) throws Exception {
        List<String> selected=SCOPES.get(scope);
        if(selected==null)throw new IllegalArgumentException("Unknown clean scope: "+scope);
        Path boundary=root.toRealPath(),base=scope.equals("wasm")?root:root.resolve("target/jenesis-jvm");
        for(String relative:selected) {
            Path output=base.resolve(relative).toAbsolutePath().normalize();
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
