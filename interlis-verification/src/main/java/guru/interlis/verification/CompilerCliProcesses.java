package guru.interlis.verification;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static guru.interlis.verification.HostChecks.*;

/** Real standard-entry processes, including migrated path, stream and failure cases. */
public final class CompilerCliProcesses {
    private CompilerCliProcesses() { }
    static final Set<String> REQUIRED = Set.of("help", "version", "bare-file", "empty", "unknown-command",
            "legacy-option", "invalid-option", "valid", "compile", "analyze", "diagram", "syntax",
            "semantic", "missing-file", "missing-import", "explicit-import", "multiple-models",
            "option-end", "format-utf8", "root-isolation", "foreign-warning", "relevant-warning",
            "diagnostic-de", "diagnostic-json", "no-model-root", "invalid-language", "repository-http",
            "repository-http-compile", "repository-http-analyze", "repository-http-diagram", "compact", "verbose",
            "color-auto", "color-always", "color-never", "color-no-color", "color-empty-no-color",
            "color-term-dumb", "color-always-override", "color-never-override", "color-invalid", "color-duplicate", "color-missing-inline", "color-usage-control", "color-format-check-control",
            "color-json-always", "color-json-never", "color-json-auto", "color-format-always", "color-format-never",
            "color-diagram-always", "color-diagram-never", "color-format-error", "color-compile", "color-analyze");

    static void verify(String name, Result result, int exit, boolean stdout) {
        require(result.exitCode() == exit, "Wrong compiler CLI exit: " + name + result);
        require(!result.stderr().contains("Exception in thread"), "Compiler CLI crashed: " + name);
        if (!stdout) require(result.stdout().isEmpty(), "Compiler CLI stdout polluted: " + name);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path suppliedLauncher = args.length > 1 ? Path.of(args[1]).toAbsolutePath() : null;
        Path output = root.resolve("build/reports/p3/compiler-cli/processes.json");
        Files.deleteIfExists(output);
        var before = artifacts(root);
        Path folder = Files.createTempDirectory("interlis compiler ä 😀 ");
        var cases = new LinkedHashMap<String, Result>();
        try {
            Path launcher = suppliedLauncher != null ? suppliedLauncher : copyDistribution(root, folder);
            String base = "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = DOMAIN Name = TEXT*20; END Base.";
            String imported = "MODEL Root AT \"x\" VERSION \"1\" = IMPORTS Base; DOMAIN Name EXTENDS Base.Name = TEXT*10; END Root.";
            Files.writeString(folder.resolve("Base.ili"), base);
            Files.writeString(folder.resolve("Control\u202e.ili"), base);
            Files.writeString(folder.resolve("Root.ili"), "INTERLIS 2.4; " + imported);
            Files.writeString(folder.resolve("-Gültig 😀 & [x]!.ili"), base);
            Files.writeString(folder.resolve("Multiple.ili"), base + "\n" + imported);
            String bad = "MODEL Bad AT \"x\" VERSION \"1\" = TOPIC T=CLASS C=value:Missing;END C;END T;END Bad.";
            Files.writeString(folder.resolve("Bad.ili"), "INTERLIS 2.4; " + bad);
            Files.writeString(folder.resolve("Mixed.ili"), base + "\n" + bad);
            Files.writeString(folder.resolve("Warnings.ili"), base + "\nMODEL Other AT \"x\" VERSION \"1\" = TOPIC T(ABSTRACT,ABSTRACT)=END T;END Other.");
            Files.writeString(folder.resolve("NoModel.ili"), "INTERLIS 2.4;\r\nGARBAGE\r\n");
            Files.writeString(folder.resolve("Syntax.ili"), "INTERLIS 2.4; MODEL Syntax AT \"x\" VERSION \"1\" = TOPIC");
            Files.writeString(folder.resolve("Encoding.ili"), base + " !! Grüezi 😀\n");
            record Fixture(String name, List<String> args, int exit, boolean stdout) { }
            var fixtures = List.of(
                    new Fixture("help", List.of("--help"), 0, true),
                    new Fixture("version", List.of("--version"), 0, true),
                    new Fixture("bare-file", List.of("https://invalid.example/no-access.ili"), 2, false),
                    new Fixture("empty", List.of(), 2, false),
                    new Fixture("unknown-command", List.of("typo", "missing.ili"), 2, false),
                    new Fixture("legacy-option", List.of("check", "--modeldir", "missing", "missing.ili"), 2, false),
                    new Fixture("invalid-option", List.of("check", "--unknown", "missing.ili"), 2, false),
                    new Fixture("root-isolation", List.of("check", "--root", "Base", "Mixed.ili"), 0, false),
                    new Fixture("foreign-warning", List.of("check", "--root", "Base", "--warnings", "reject", "Warnings.ili"), 0, false),
                    new Fixture("relevant-warning", List.of("check", "--root", "Other", "--warnings", "reject", "Warnings.ili"), 1, false),
                    new Fixture("diagnostic-de", List.of("check", "--diagnostic-language", "de", "Bad.ili"), 1, false),
                    new Fixture("diagnostic-json", List.of("check", "--json", "--diagnostic-language", "de", "Bad.ili"), 1, true),
                    new Fixture("no-model-root", List.of("check", "NoModel.ili"), 1, false),
                    new Fixture("invalid-language", List.of("check", "--diagnostic-language", "fr", "missing.ili"), 2, false),
                    new Fixture("valid", List.of("check", "Base.ili"), 0, false),
                    new Fixture("compile", List.of("compile", "Base.ili"), 0, false),
                    new Fixture("analyze", List.of("analyze", "Base.ili"), 0, false),
                    new Fixture("diagram", List.of("diagram", "Base.ili"), 0, true),
                    new Fixture("syntax", List.of("check", "Syntax.ili"), 1, false),
                    new Fixture("semantic", List.of("check", "Bad.ili"), 1, false),
                    new Fixture("compact", List.of("check", "Bad.ili"), 1, false),
                    new Fixture("verbose", List.of("check", "--verbose", "Bad.ili"), 1, false),
                    new Fixture("missing-file", List.of("check", "missing.ili"), 1, false),
                    new Fixture("missing-import", List.of("check", "Root.ili"), 1, false),
                    new Fixture("explicit-import", List.of("check", "--root", "Root", "Root.ili", "Base.ili"), 0, false),
                    new Fixture("multiple-models", List.of("check", "Multiple.ili"), 0, false),
                    new Fixture("option-end", List.of("check", "--", "-Gültig 😀 & [x]!.ili"), 0, false));
            for (var fixture : fixtures) {
                var command = new ArrayList<String>(); command.add(launcher.toString()); command.addAll(fixture.args());
                var result = run(folder, command, Duration.ofSeconds(45));
                verify(fixture.name(), result, fixture.exit(), fixture.stdout()); cases.put(fixture.name(), result);
            }
            var encoding = run(folder, List.of(launcher.toString(),"format","Encoding.ili"),
                    Duration.ofSeconds(45),Map.of("JAVA_OPTS","-Dstdout.encoding=windows-1252 -Dstderr.encoding=windows-1252"));
            verify("format-utf8", encoding, 0, true);
            require(encoding.stdout().contains("Grüezi 😀") && encoding.stderr().isEmpty(), "Native encoding leaked into UTF-8 streams");
            cases.put("format-utf8", encoding);
            require(cases.get("root-isolation").stderr().isEmpty()
                    && cases.get("foreign-warning").stderr().isEmpty(), "Foreign request diagnostics leaked");
            require(cases.get("semantic").stderr().contains("ILI-SEM-0001"), "Semantic process did not check an unresolved type");
            require(cases.get("relevant-warning").stderr().contains("ILI-SYN-0007"), "Relevant warning was lost");
            require(cases.get("no-model-root").stderr().contains("ILI-SYN-0004"), "Requested source syntax was lost");
            require(cases.get("diagnostic-de").stderr().contains("kann an dieser Stelle nicht aufgelöst werden")
                    && !cases.get("diagnostic-de").stderr().contains("diagnostic.semantics"), "German diagnostic prose missing");
            var json = com.google.gson.JsonParser.parseString(cases.get("diagnostic-json").stdout()).getAsJsonObject();
            var diagnostic = json.getAsJsonArray("diagnostics").get(0).getAsJsonObject();
            require(cases.get("diagnostic-json").stderr().isEmpty()
                    && diagnostic.get("code").getAsString().equals("ILI-SEM-0001")
                    && diagnostic.get("message").getAsString().contains("kann an dieser Stelle nicht aufgelöst werden"), "Canonical JSON or presentation missing");
            String compact = cases.get("compact").stderr(), verbose = cases.get("verbose").stderr();
            require(compact.lines().count() == 1 && !compact.contains(" | "), "Compact diagnostic contains details");
            require(verbose.contains(" | ") && verbose.startsWith(compact.stripTrailing()+"\n"), "Verbose diagnostic lost header/frame");
            repositoryHttp(folder, launcher, cases);
            colorCases(folder, launcher, cases);
            verifyColorCases(cases);
            var terminal = terminalCases(folder, launcher);
            require(cases.keySet().equals(REQUIRED), "Incomplete standard CLI process profile");
            require(before.equals(artifacts(root)), "CLI artifacts changed during processes");
            write(output, Map.of("status", "passed", "profile", "compiler-cli/v1", "cases", cases,
                    "terminal", terminal, "launcher", launcher.toString(),
                    "gitCommit", ProfileFingerprint.commit(root), "sourceFingerprint", ProfileFingerprint.current(root), "artifacts", before));
            System.out.println("COMPILER_CLI_PROCESSES_PASS " + cases.size());
        } finally {
            try (var files = Files.walk(folder)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    /** Exercises HTTP and XML from the real module launcher, not the test JVM. */
    private static void repositoryHttp(Path folder, Path launcher, Map<String,Result> cases) throws Exception {
        var requests=new java.util.concurrent.atomic.AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        String model="INTERLIS 2.4; MODEL Remote AT \"x\" VERSION \"1\"=DOMAIN Name=TEXT*20;END Remote.";
        String index="<TRANSFER><ModelMetadata><Name>Remote</Name><SchemaLanguage>ili2_4</SchemaLanguage>"
                +"<File>Remote.ili</File><Version>1</Version></ModelMetadata></TRANSFER>";
        server.createContext("/",exchange -> {
            requests.incrementAndGet();
            String body=exchange.getRequestURI().getPath().equals("/ilimodels.xml")?index:model;
            byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try (var output=exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            for (String command : List.of("check", "compile", "analyze", "diagram")) {
                var result=run(folder,List.of(launcher.toString(),command,"--repository",
                        "http://127.0.0.1:"+server.getAddress().getPort()+"/","--root","Remote"),Duration.ofSeconds(45));
                String key=command.equals("check") ? "repository-http" : "repository-http-"+command;
                verify(key,result,0,command.equals("diagram"));
                require(result.stderr().isEmpty()&&requests.get()>=2,"Real launcher did not load HTTP index/model");
                if (command.equals("diagram")) require(result.stdout().contains("classDiagram"), "Missing repository diagram");
                cases.put(key,result);
            }
        } finally { server.stop(0); }
    }

    private static Path copyDistribution(Path root, Path temporary) throws Exception {
        Path installed=cli(root).getParent().getParent(),target=temporary.resolve("distribution ä 😀");
        try (var files=Files.walk(installed)) {
            for (var source:files.sorted().toList()) {
                Path destination=target.resolve(installed.relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(destination);
                else Files.copy(source,destination,StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        return target.resolve("bin").resolve(cli(root).getFileName());
    }

    private static void colorCases(Path folder, Path launcher, Map<String,Result> cases) throws Exception {
        record ColorCase(String name, List<String> args, Map<String,String> environment, int exit, boolean stdout) { }
        var standard = Map.of("NO_COLOR", "", "TERM", "xterm");
        var muted = Map.of("NO_COLOR", "1", "TERM", "DuMb");
        var fixtures = List.of(
                new ColorCase("color-auto",List.of("check","--color=auto","Bad.ili"),standard,1,false),
                new ColorCase("color-always",List.of("check","--color","always","Bad.ili"),standard,1,false),
                new ColorCase("color-never",List.of("check","--color=never","Bad.ili"),standard,1,false),
                new ColorCase("color-no-color",List.of("check","--diagnostic-language","de","Bad.ili"),muted,1,false),
                new ColorCase("color-empty-no-color",List.of("check","Bad.ili"),standard,1,false),
                new ColorCase("color-term-dumb",List.of("check","Bad.ili"),Map.of("NO_COLOR","","TERM","DUMB"),1,false),
                new ColorCase("color-always-override",List.of("check","--color=always","Bad.ili"),muted,1,false),
                new ColorCase("color-never-override",List.of("check","--color=never","Bad.ili"),standard,1,false),
                new ColorCase("color-missing-inline",List.of("check","--config","--color=always","missing.ili"),standard,2,false),
                new ColorCase("color-usage-control",List.of("check","--bad\u001b[2J\u202e"),standard,2,false),
                new ColorCase("color-format-check-control",List.of("format","--check","--color=always","Control\u202e.ili"),standard,1,false),
                new ColorCase("color-invalid",List.of("check","--color=bad","missing.ili"),standard,2,false),
                new ColorCase("color-duplicate",List.of("check","--color=auto","--color","never","missing.ili"),standard,2,false),
                new ColorCase("color-json-always",List.of("check","--json","--color=always","--diagnostic-language","de","Bad.ili"),standard,1,true),
                new ColorCase("color-json-never",List.of("check","--json","--color=never","--diagnostic-language","de","Bad.ili"),standard,1,true),
                new ColorCase("color-json-auto",List.of("check","--json","--color=auto","--diagnostic-language","de","Bad.ili"),standard,1,true),
                new ColorCase("color-format-always",List.of("format","--color=always","Encoding.ili"),standard,0,true),
                new ColorCase("color-format-never",List.of("format","--color=never","Encoding.ili"),standard,0,true),
                new ColorCase("color-diagram-always",List.of("diagram","--color=always","Base.ili"),standard,0,true),
                new ColorCase("color-diagram-never",List.of("diagram","--color=never","Base.ili"),standard,0,true),
                new ColorCase("color-format-error",List.of("format","--color=always","Syntax.ili"),standard,1,false),
                new ColorCase("color-compile",List.of("compile","--color=always","Bad.ili"),standard,1,false),
                new ColorCase("color-analyze",List.of("analyze","--color=always","Bad.ili"),standard,0,false));
        for (var fixture : fixtures) {
            var command = new ArrayList<String>(); command.add(launcher.toString()); command.addAll(fixture.args()); command.add("--verbose");
            var result = run(folder, command, Duration.ofSeconds(45), fixture.environment());
            verify(fixture.name(),result,fixture.exit(),fixture.stdout()); cases.put(fixture.name(),result);
        }
    }

    /** Rejects report mutation as well as actual terminal byte/exit/channel regressions. */
    static void verifyColorCases(Map<String,Result> cases) {
        for (String name : REQUIRED.stream().filter(n -> n.startsWith("color-")).toList())
            require(cases.containsKey(name), "Missing color process: " + name);
        for (String name : REQUIRED.stream().filter(n -> n.startsWith("color-")).toList()) {
            boolean artifact=name.startsWith("color-json-") || name.startsWith("color-diagram-")
                    || name.equals("color-format-always") || name.equals("color-format-never");
            int exit=List.of("color-invalid","color-duplicate","color-missing-inline","color-usage-control").contains(name) ? 2
                    : artifact && !name.startsWith("color-json-") || name.equals("color-analyze") ? 0 : 1;
            verify(name,cases.get(name),exit,artifact);
        }
        verifySafeControlCases(cases);
        String plain = cases.get("color-never").stderr();
        require(plain.contains("ILI-SEM-0001") && plain.contains(" | ") && plain.contains("^"), "Missing real source diagnostic");
        for (String name : List.of("color-auto","color-empty-no-color","color-term-dumb","color-never-override"))
            require(cases.get(name).stderr().equals(plain), "Auto/plain mismatch: " + name);
        require(cases.get("color-no-color").stderr().contains("kann an dieser Stelle nicht aufgelöst werden")
                && !cases.get("color-no-color").stderr().contains("\u001b"), "NO_COLOR/DE failure");
        for (String name : List.of("color-always","color-always-override","color-compile","color-analyze")) {
            var text = cases.get(name).stderr();
            require(text.contains("\u001b[31mERROR\u001b[0m") && stripSgr(text).equals(plain), "ANSI parity/reset failure: " + name);
        }
        var formatError = cases.get("color-format-error");
        require(formatError.stderr().contains("\u001b[31mERROR\u001b[0m") && formatError.stderr().contains(" | "), "Formatter color missing");
        stripSgr(formatError.stderr());
        for (String name : List.of("color-json-always","color-json-never","color-json-auto")) {
            var result = cases.get(name);
            require(result.exitCode()==1 && result.stderr().isEmpty() && !result.stdout().contains("\u001b"), "ANSI/exit/channel leak in JSON");
            var json = com.google.gson.JsonParser.parseString(result.stdout()).getAsJsonObject();
            require(!json.getAsJsonArray("diagnostics").isEmpty(), "Missing JSON diagnostics");
            require(result.stdout().equals(cases.get("color-json-never").stdout()), "Color changed canonical JSON");
        }
        for (String command : List.of("format","diagram")) {
            var a=cases.get("color-"+command+"-always");var b=cases.get("color-"+command+"-never");
            require(a.exitCode()==0 && b.exitCode()==0 && a.stdout().equals(b.stdout()) && !a.stdout().contains("\u001b"), "ANSI in artifact stdout");
        }
        for (String name : List.of("color-invalid","color-duplicate","color-missing-inline","color-usage-control"))
            require(cases.get(name).exitCode()==2 && cases.get(name).stdout().isEmpty(), "Color usage failure");
    }

    private static void verifySafeControlCases(Map<String,Result> cases) {
        for (String name : List.of("color-usage-control","color-format-check-control")) {
            String text=cases.get(name).stderr();
            require(!text.contains("\u001b")&&!text.contains("\u202e")&&text.contains("\\u202e"), "Raw terminal control: "+name);
        }
        require(cases.get("color-usage-control").stderr().contains("\\u001b"), "Missing escaped ESC");
        require(cases.get("color-format-check-control").stderr().contains("would format: "), "Missing real format check");
    }

    static String stripSgr(String text) {
        String stripped=text.replaceAll("\\x1b\\[(?:0|31|33|34|36|90)m", "");
        require(!stripped.contains("\u001b"), "Non-SGR terminal control in diagnostic");
        return stripped;
    }

    private static Map<String,Object> terminalCases(Path folder, Path launcher) throws Exception {
        if (!System.getProperty("os.name").equals("Mac OS X"))
            return Map.of("hostDetection","conservative-or-headless", "realPty", false);
        Path script=folder.resolve("terminal-check.sh");
        var results=new LinkedHashMap<String,Result>();
        for (boolean redirect : List.of(false,true)) for (String mode : List.of("auto","always","never")) {
            Files.writeString(script,"test -t 1 && test -t 2 || exit 80\n\"$1\" check --color=\"$2\" \"$3\""
                    +(redirect?" 2>\"$4\"":"")+"\nresult=$?\ntest \"$result\" = 1 || exit 81\n");
            Path error=folder.resolve("redirect.err"),capture=folder.resolve("terminal.capture");
            var result=run(folder,List.of("/usr/bin/script","-q",capture.toString(),"/bin/sh",script.toString(),
                    launcher.toString(),mode,folder.resolve("Bad.ili").toString(),error.toString()),Duration.ofSeconds(45),Map.of("NO_COLOR","","TERM","xterm"));
            require(result.exitCode()==0,"Actual PTY process failed: "+result);
            String text=redirect?Files.readString(error):Files.readString(capture);
            require(text.contains("ILI-SEM-0001"),"PTY did not execute source rejection");
            require(text.contains("\u001b[31mERROR\u001b[0m") == (mode.equals("always") || mode.equals("auto")&&!redirect),"Wrong PTY/redirect color");
            // script itself may emit an initial EOT marker; inspect only CLI SGR/content here.
            results.put(mode+(redirect?"-stderr-file":"-stderr-pty"),result);
        }
        return Map.of("hostDetection","posix-fd2", "realPty", true,"cases",results);
    }

}
