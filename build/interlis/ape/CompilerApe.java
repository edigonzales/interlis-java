package build.interlis.ape;

import build.interlis.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** Explicit optional APE investigation. Normal profiles never dispatch native work. */
public final class CompilerApe {
    public enum Operation { INSPECT, PREPARE, VERIFY, TOOLCHAIN, PROBE, BUILD, FUSE, REPORT }
    public enum Architecture { X86_64, AARCH64 }
    public record Request(Operation operation, Map<String,Path> paths, Architecture architecture) {
        public Request { paths = Map.copyOf(paths); }
        public Path path(String option) { return paths.get(option); }
    }

    /** Exact option sets; paths must be existing absolute files. No implicit phase execution. */
    public static Request parse(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("Expected ape operation");
        Operation operation;
        try { operation = Operation.valueOf(args[0].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown APE operation: " + args[0]); }
        Set<String> expected = switch (operation) {
            case INSPECT, PREPARE -> Set.of();
            case VERIFY -> Set.of("--inputs");
            case REPORT -> Set.of("--evidence");
            case TOOLCHAIN, PROBE -> Set.of("--lock");
            case BUILD -> Set.of("--inputs", "--lock", "--arch");
            case FUSE -> Set.of("--x64", "--arm64", "--lock");
        };
        var values = new HashMap<String,String>();
        for (int i = 1; i < args.length; i += 2) {
            if (i + 1 >= args.length || !expected.contains(args[i]) || values.put(args[i], args[i+1]) != null)
                throw new IllegalArgumentException("Unknown, duplicate or missing APE option: " + args[i]);
        }
        if (!values.keySet().equals(expected)) throw new IllegalArgumentException("Expected APE options: " + expected);
        var paths = new HashMap<String,Path>(); Architecture arch = null;
        for (var entry : values.entrySet()) {
            if (entry.getKey().equals("--arch")) {
                arch = switch(entry.getValue()) {
                    case "x86_64" -> Architecture.X86_64;
                    case "aarch64" -> Architecture.AARCH64;
                    default -> throw new IllegalArgumentException("Expected x86_64 or aarch64");
                };
            } else {
                Path path = Path.of(entry.getValue());
                if (!path.isAbsolute() || !(entry.getKey().equals("--evidence") ? Files.isDirectory(path) : Files.isRegularFile(path))) throw new IllegalArgumentException("Expected existing absolute file: " + entry.getKey());
                paths.put(entry.getKey(), path);
            }
        }
        return new Request(operation, paths, arch);
    }

    /** Inspect is read-only. Lock order is APE then JVM; native builds do not hold the JVM lock. */
    public static void run(Path root, String[] args) throws Exception {
        Request request = parse(args); JvmBuildInputs.requireProject(root);
        if (request.operation() == Operation.INSPECT) {
            System.out.println(CompilerRuntimeInputs.json(Map.of("mainClass", CompilerRuntimeInputs.MAIN,
                    "host", System.getProperty("os.name") + "/" + System.getProperty("os.arch"),
                    "jdk", Runtime.version().toString(), "source", CompilerRuntimeInputs.source(root),
                    "existingInventory", Files.isRegularFile(root.resolve("target/jenesis-jvm/reports/engine-results.properties")),
                    "output", root.resolve("target/compiler-ape").toString(), "nativeBuild", "explicit operations only")));
            return;
        }
        Path output = root.resolve("target/compiler-ape"); Files.createDirectories(output);
        JvmBuildInputs.confined(root, "target/compiler-ape");
        try (var channel = FileChannel.open(output.resolve("project.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Another APE operation is active");
            switch (request.operation()) {
                case PREPARE -> withJvmLock(root, () -> System.out.println("Compiler inputs: " + CompilerRuntimeInputs.prepare(root, output.resolve("inputs"))));
                case VERIFY -> withJvmLock(root, () -> {
                    CompilerRuntimeInputs.readVerified(root, request.path("--inputs"));
                    System.out.println("Verified compiler inputs: " + request.path("--inputs"));
                });
                case REPORT -> node(root, "report.cjs", request.path("--evidence").toString());
                case TOOLCHAIN -> node(root, "toolchain.cjs", request.path("--lock").toString(), output.resolve("toolchains").toString());
                case PROBE -> node(root, "probe.cjs", request.path("--lock").toString());
                case BUILD -> {
                    withJvmLock(root, () -> CompilerRuntimeInputs.readVerified(root, request.path("--inputs")));
                    node(root, "compiler-native.cjs", "build", request.path("--inputs").toString(),
                            request.architecture().name().toLowerCase(Locale.ROOT), request.path("--lock").toString());
                    withJvmLock(root, () -> CompilerRuntimeInputs.readVerified(root, request.path("--inputs")));
                }
                case FUSE -> {
                    withJvmLock(root, () -> {
                        CompilerRuntimeInputs.readVerified(root, inputsFromSlice(request.path("--x64")));
                        CompilerRuntimeInputs.readVerified(root, inputsFromSlice(request.path("--arm64")));
                    });
                    node(root, "compiler-native.cjs", "fuse", request.path("--x64").toString(), request.path("--arm64").toString(), request.path("--lock").toString());
                }
                default -> throw new IllegalStateException("Unexpected operation");
            }
        }
    }
    private static Path inputsFromSlice(Path slice) throws Exception {
        var data = (Map<?,?>) build.jenesis.Json.parse(Files.readString(slice));
        Path input = Path.of((String)data.get("compilerInputs"));
        if (!input.isAbsolute()) throw new IllegalArgumentException("Absolute slice inputs required");
        return input;
    }
    @FunctionalInterface private interface Step { void run() throws Exception; }
    private static void withJvmLock(Path root, Step step) throws Exception {
        Path jvm = root.resolve("target/jenesis-jvm"); Files.createDirectories(jvm);
        try (var channel = FileChannel.open(jvm.resolve("project.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Another JVM build is active");
            step.run();
        }
    }
    private static void node(Path root, String script, String... arguments) throws Exception {
        var command = new ArrayList<>(List.of("node", root.resolve("tools/ape").resolve(script).toString()));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start();
        if (process.waitFor() != 0) throw new IllegalStateException("APE step failed: " + script);
    }
}
