package guru.interlis.cli.verification;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Host-only verification utilities. Never linked into the compiler or CLI distribution. */
final class HostChecks {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    record Result(List<String> command, int exitCode, String stdout, String stderr, double durationMs) { }
    private HostChecks() { }
    static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static String hash(Path path) throws Exception { return hash(Files.readAllBytes(path)); }
    static JsonObject read(Path path) throws IOException { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    static void write(Path path, Object value) throws IOException { Files.createDirectories(path.getParent()); Files.writeString(path, JSON.toJson(value)+"\n"); }
    static String s(JsonObject row, String key) { return row.get(key).getAsString(); }
    static List<JsonObject> rows(JsonObject row, String key) { return row.getAsJsonArray(key).asList().stream().map(JsonElement::getAsJsonObject).toList(); }
    static List<String> strings(JsonElement array) { return array.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList(); }
    static Path cache(Path root, String variable, String fallback) {
        String configured=System.getenv(variable);
        return configured==null ? Path.of(System.getProperty("user.home"),".cache/interlis-java",fallback) : root.resolve(configured).toAbsolutePath().normalize();
    }
    static Path cli(Path root) { return root.resolve("interlis-cli/build/install/interlis-cli/bin/interlis-cli" + (System.getProperty("os.name").startsWith("Windows")?".bat":"")); }
    static Path java() { return Path.of(System.getProperty("java.home"),"bin","java"); }
    static void pin(Path path, String expected) throws Exception { require(Files.isRegularFile(path) && hash(path).equals(expected),"Changed or missing pinned input: "+path); }
    static void download(URI uri, Path target, String expected) throws Exception {
        Files.createDirectories(target.getParent());
        if(!Files.exists(target)) {
            Path temporary=Files.createTempFile(target.getParent(),"download-",".part");
            try {
                var connection=uri.toURL().openConnection();connection.setConnectTimeout(180000);connection.setReadTimeout(180000);
                try(var input=connection.getInputStream()) { Files.copy(input,temporary,StandardCopyOption.REPLACE_EXISTING); }
                pin(temporary,expected);Files.move(temporary,target);
            } finally { Files.deleteIfExists(temporary); }
        }
        pin(target,expected);
    }
    static Result run(Path root, List<String> command, Duration timeout) throws Exception {
        Path directory=Files.createTempDirectory("interlis-process-");
        Path stdout=directory.resolve("stdout"),stderr=directory.resolve("stderr");
        Process process=null;
        try {
            long started=System.nanoTime();
            process=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
            if(!process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS)) throw new IOException("Process timeout: "+command);
            return new Result(List.copyOf(command),process.exitValue(),Files.readString(stdout),Files.readString(stderr),(System.nanoTime()-started)/1_000_000.0);
        } finally {
            if(process!=null && process.isAlive()) {process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor();}
            Files.deleteIfExists(stdout);Files.deleteIfExists(stderr);Files.deleteIfExists(directory);
        }
    }
    static Result cli(Path root, List<String> arguments) throws Exception {
        var command=new ArrayList<String>();command.add(cli(root).toString());command.addAll(arguments);return run(root,command,Duration.ofSeconds(60));
    }
    static Map<String,String> artifacts(Path root) throws Exception {
        Map<String,String> result=new TreeMap<>();
        try(var paths=Files.list(cli(root).getParent().getParent().resolve("lib"))) {
            for(Path path:paths.filter(p->p.toString().endsWith(".jar")).toList()) result.put(root.relativize(path).toString(),hash(path));
        }
        require(!result.isEmpty(),"Missing SDK artifacts");return result;
    }
    static void checkPayload(Result result, boolean rejected) {
        require(result.exitCode()==(rejected?1:0) && result.stderr().isEmpty(),"Unexpected CLI exit/stderr: "+result);
        var payload=JsonParser.parseString(result.stdout()).getAsJsonObject();
        if(rejected) require(s(payload,"status").startsWith("rejected: RejectionSummary[reasons=[SOURCE_ERRORS],")
                && s(payload,"status").contains("unsupportedFeatureIds=[]") && !payload.getAsJsonArray("diagnostics").isEmpty(),"Not a source rejection");
        else require(payload.equals(JsonParser.parseString("{\"status\":\"ok\",\"diagnostics\":[]}")),"Unexpected successful CLI payload");
    }
}
