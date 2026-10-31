package guru.interlis.cli.verification;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Real standard-entry processes, including migrated path, stream and failure cases. */
public final class CompilerCliProcesses {
    private CompilerCliProcesses() { }
    static final Set<String> REQUIRED = Set.of("help", "version", "bare-file", "empty", "unknown-command",
            "legacy-option", "invalid-option", "valid", "compile", "analyze", "diagram", "syntax",
            "semantic", "missing-file", "missing-import", "explicit-import", "multiple-models",
            "option-end", "format-utf8");

    static void verify(String name, Result result, int exit, boolean stdout) {
        require(result.exitCode() == exit, "Wrong compiler CLI exit: " + name + result);
        require(!result.stderr().contains("Exception in thread"), "Compiler CLI crashed: " + name);
        if (!stdout) require(result.stdout().isEmpty(), "Compiler CLI stdout polluted: " + name);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path output = root.resolve("build/reports/p3/compiler-cli/processes.json");
        Files.deleteIfExists(output);
        var before = artifacts(root);
        Path folder = Files.createTempDirectory("interlis compiler ä 😀 ");
        var cases = new LinkedHashMap<String, Result>();
        try {
            String base = "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = DOMAIN Name = TEXT*20; END Base.";
            String imported = "MODEL Root AT \"x\" VERSION \"1\" = IMPORTS Base; DOMAIN Name EXTENDS Base.Name = TEXT*10; END Root.";
            Files.writeString(folder.resolve("Base.ili"), base);
            Files.writeString(folder.resolve("Root.ili"), "INTERLIS 2.4; " + imported);
            Files.writeString(folder.resolve("-Gültig 😀.ili"), base);
            Files.writeString(folder.resolve("Multiple.ili"), base + "\n" + imported);
            Files.writeString(folder.resolve("Bad.ili"), "INTERLIS 2.4; MODEL Bad AT \"x\" VERSION \"1\" = DOMAIN D = Missing; END Bad.");
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
                    new Fixture("valid", List.of("check", "Base.ili"), 0, false),
                    new Fixture("compile", List.of("compile", "Base.ili"), 0, false),
                    new Fixture("analyze", List.of("analyze", "Base.ili"), 0, false),
                    new Fixture("diagram", List.of("diagram", "Base.ili"), 0, true),
                    new Fixture("syntax", List.of("check", "Syntax.ili"), 1, false),
                    new Fixture("semantic", List.of("check", "Bad.ili"), 1, false),
                    new Fixture("missing-file", List.of("check", "missing.ili"), 1, false),
                    new Fixture("missing-import", List.of("check", "Root.ili"), 1, false),
                    new Fixture("explicit-import", List.of("check", "--root", "Root", "Root.ili", "Base.ili"), 0, false),
                    new Fixture("multiple-models", List.of("check", "Multiple.ili"), 0, false),
                    new Fixture("option-end", List.of("check", "--", "-Gültig 😀.ili"), 0, false));
            for (var fixture : fixtures) {
                var command = new ArrayList<String>(); command.add(cli(root).toString()); command.addAll(fixture.args());
                var result = run(folder, command, Duration.ofSeconds(45));
                verify(fixture.name(), result, fixture.exit(), fixture.stdout()); cases.put(fixture.name(), result);
            }
            Path lib = cli(root).getParent().getParent().resolve("lib");
            var encoding = run(folder, List.of(java().toString(), "-Dstdout.encoding=windows-1252",
                    "-Dstderr.encoding=windows-1252", "-cp", lib + java.io.File.separator + "*",
                    "guru.interlis.cli.Main", "format", "Encoding.ili"), Duration.ofSeconds(45));
            verify("format-utf8", encoding, 0, true);
            require(encoding.stdout().contains("Grüezi 😀") && encoding.stderr().isEmpty(), "Native encoding leaked into UTF-8 streams");
            cases.put("format-utf8", encoding);
            require(cases.keySet().equals(REQUIRED), "Incomplete standard CLI process profile");
            require(before.equals(artifacts(root)), "CLI artifacts changed during processes");
            write(output, Map.of("status", "passed", "profile", "compiler-cli/v1", "cases", cases,
                    "gitCommit", ProfileFingerprint.commit(root), "sourceFingerprint", ProfileFingerprint.current(root), "artifacts", before));
            System.out.println("COMPILER_CLI_PROCESSES_PASS " + cases.size());
        } finally {
            try (var files = Files.walk(folder)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
}
