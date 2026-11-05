package guru.interlis.verification;

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
    static String projectVersion(Path root) throws Exception {
        var parser=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        parser.setFeature("http://xml.org/sax/features/external-general-entities",false);
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        parser.setXIncludeAware(false);parser.setExpandEntityReferences(false);
        var document=parser.newDocumentBuilder().parse(root.resolve("pom.xml").toFile());
        for(var node=document.getDocumentElement().getFirstChild();node!=null;node=node.getNextSibling())
            if(node instanceof org.w3c.dom.Element element && element.getTagName().equals("version")) {
                String value=element.getTextContent().trim();require(value.matches("[0-9][A-Za-z0-9_.-]*"),"Invalid project version");return value;
            }
        throw new IllegalArgumentException("Missing project version");
    }
    static Path cli(Path root) { return VerificationLayout.forInvocation(root).compilerLauncher(System.getProperty("os.name").startsWith("Windows")); }
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
        return run(root, command, timeout, Map.of());
    }
    static Result run(Path root, List<String> command, Duration timeout, Map<String,String> environment) throws Exception {
        Path directory=Files.createTempDirectory("interlis-process-");
        Path stdout=directory.resolve("stdout"),stderr=directory.resolve("stderr");
        Process process=null;
        try {
            long started=System.nanoTime();
            var builder=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            builder.environment().putAll(environment);
            if (System.getProperty("os.name").equals("Mac OS X") && command.getFirst().equals("/usr/bin/script"))
                builder.redirectInput(new File("/dev/null"));
            process=builder.start();
            if(!process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS)) throw new IOException("Process timeout: "+command);
            return new Result(List.copyOf(command),process.exitValue(),strictUtf8(stdout,command),strictUtf8(stderr,command),(System.nanoTime()-started)/1_000_000.0);
        } finally {
            if(process!=null && process.isAlive()) {process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor();}
            Files.deleteIfExists(stdout);Files.deleteIfExists(stderr);Files.deleteIfExists(directory);
        }
    }
    private static String strictUtf8(Path file, List<String> command) throws IOException {
        try { return Files.readString(file); }
        catch (java.nio.charset.CharacterCodingException failure) {
            byte[] bytes=Files.readAllBytes(file);
            throw new IOException("Non-UTF-8 process output from "+command+"; first bytes="
                    +HexFormat.of().formatHex(Arrays.copyOf(bytes,Math.min(bytes.length,4096))),failure);
        }
    }
    /** Constructs one host child launch; named callers remain named without a failure fallback. */
    static List<String> javaCommand(String main) { return javaCommand(main,false); }
    static List<String> javaCommand(String main,boolean testPatch) {
        var command=new ArrayList<String>();command.add(java().toString());
        if(HostChecks.class.getModule().isNamed()) {
            String modulePath=System.getProperty("jdk.module.path");require(modulePath!=null&&!modulePath.isBlank(),"Missing host module path");
            command.addAll(List.of("--module-path",modulePath));
            if(testPatch) {
                String patch=System.getProperty("interlis.test.patch");require(patch!=null&&!patch.isBlank(),"Missing explicit test patch");
                command.addAll(List.of("--patch-module","guru.interlis.verification="+patch,"--add-reads","guru.interlis.verification=ALL-UNNAMED","-cp",System.getProperty("java.class.path")));
            }
            command.addAll(List.of("--module","guru.interlis.verification/"+main));
        } else command.addAll(List.of("-cp",System.getProperty("java.class.path"),main));
        return List.copyOf(command);
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
