package guru.interlis.cli.verification;

import guru.interlis.cli.LegacyCliProfile;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Fresh external reference/replacement processes; never a production compiler dependency. */
public final class LegacyCliProcesses {
    private LegacyCliProcesses() { }

    record Expectation(String name, List<String> arguments, int referenceExit, int currentExit,
                       boolean compareReference) {
        Expectation { arguments = List.copyOf(arguments); }
    }

    static void compare(Expectation expected, Result reference, Result current) {
        require(current.exitCode() == expected.currentExit(), "Wrong replacement exit: " + expected.name() + current);
        require(current.stdout().isEmpty(), "Legacy checking stdout was polluted: " + expected.name());
        require(!current.stderr().contains("Exception in thread"), "Replacement crashed: " + expected.name());
        if (expected.compareReference()) {
            require(reference != null && reference.exitCode() == expected.referenceExit(),
                    "Wrong reference exit: " + expected.name() + reference);
            require(reference.stdout().isEmpty(), "Reference channel changed: " + expected.name());
            require(!ReferenceProcesses.processError(reference), "Reference process error: " + expected.name());
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path output = root.resolve("build/reports/p3/legacy/cli-comparison.json");
        Files.deleteIfExists(output);
        Path referenceJar = ReferenceProcesses.prepare(root);
        var profile = LegacyCliProfile.ili2c569();
        require(profile.referenceSha256().equals(ReferenceProcesses.JAR), "Reference profile pin mismatch");
        var help = ReferenceProcesses.reference(root, referenceJar, List.of("--help"));
        require(help.exitCode() == 0 && help.stdout().isEmpty(), "Reference help changed channel");
        var inventory = new TreeSet<String>();
        profile.commandSpec().options().forEach(option -> inventory.add(option.name()));
        inventory.addAll(profile.unsupportedOptions().keySet());
        for (String option : inventory)
            require(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(option) + "(?=[\\s|]|$)")
                    .matcher(help.stderr()).find(), "Option absent from actual reference help: " + option);
        Path launcher = cli(root).getParent().resolve("ili2c" + (System.getProperty("os.name").startsWith("Windows") ? ".bat" : ""));
        Path replacementJar = root.resolve("interlis-cli/build/install/interlis-cli/compat/ili2c.jar");
        require(Files.isRegularFile(launcher) && Files.isRegularFile(replacementJar), "Missing legacy distribution entries");
        var artifacts = new TreeMap<>(artifacts(root));
        artifacts.put(root.relativize(replacementJar).toString(), hash(replacementJar));
        Path folder = Files.createTempDirectory("interlis legacy ä-");
        Path multiple = Files.createTempFile("interlis-multiple-", ".ili");
        var results = new ArrayList<Map<String, Object>>();
        try {
            Path base = folder.resolve("Base.ili"), model = folder.resolve("Root.ili"), bad = folder.resolve("Bad.ili");
            Files.writeString(base, "INTERLIS 2.4; MODEL Base AT \"https://example.org\" VERSION \"1\" = DOMAIN BaseName = TEXT*20; END Base.");
            Files.writeString(model, "INTERLIS 2.4; MODEL Root AT \"https://example.org\" VERSION \"1\" = IMPORTS Base; DOMAIN RootName EXTENDS Base.BaseName = TEXT*10; END Root.");
            Files.writeString(bad, "INTERLIS 2.4; MODEL Bad AT \"https://example.org\" VERSION \"1\" = TOPIC T = CLASS C = value: Missing; END C; END T; END Bad.");
            Path syntax = folder.resolve("Syntax.ili");
            Files.writeString(multiple, Files.readString(base) + "\n" + Files.readString(model).replace("INTERLIS 2.4; ", ""));
            Files.writeString(syntax, "INTERLIS 2.4; MODEL Syntax AT \"x\" VERSION \"1\" = TOPIC");
            var cases = new ArrayList<Expectation>();
            for (String flag : List.of("-h", "--help", "-u", "--usage", "-v", "--version"))
                cases.add(new Expectation("information:" + flag, List.of(flag), 0, 0, true));
            cases.add(new Expectation("one-file", List.of("--no-auto", base.toString()), 0, 0, true));
            cases.add(new Expectation("two-files", List.of("--no-auto", "-o0", base.toString(), model.toString()), 0, 0, true));
            cases.add(new Expectation("multiple-models-one-file", List.of("--no-auto", multiple.toString()), 0, 0, true));
            cases.add(new Expectation("syntax-error", List.of("--no-auto", syntax.toString()), 1, 1, true));
            cases.add(new Expectation("semantic-error", List.of("--no-auto", bad.toString()), 1, 1, true));
            cases.add(new Expectation("missing-file", List.of("--no-auto", folder.resolve("missing.ili").toString()), 0, 1, true));
            cases.add(new Expectation("missing-import", List.of("--no-auto", model.toString()), 1, 1, true));
            cases.add(new Expectation("directory-import", List.of("--modeldir", folder.toString(), model.toString()), 0, 0, true));
            cases.add(new Expectation("named-root-extension", List.of("--modeldir", folder.toString(), "Root"), 1, 0, true));
            cases.add(new Expectation("quiet", List.of("--quiet", "--no-auto", base.toString()), 0, 0, true));
            cases.add(new Expectation("warnings-off", List.of("--without-warnings", "--no-auto", base.toString()), 0, 0, true));
            cases.add(new Expectation("trace", List.of("--trace", "--no-auto", base.toString()), 0, 0, true));
            cases.add(new Expectation("unknown-option", List.of("--unknown-p3", "--no-auto", base.toString()), 0, 1, true));
            for (String option : profile.unsupportedOptions().keySet())
                cases.add(new Expectation("unsupported:" + option, List.of(option, "unavailable.ili"), 0, 1, false));
            cases.add(new Expectation("empty-no-gui", List.of(), 0, 1, false));
            cases.add(new Expectation("missing-option-value", List.of("--modeldir"), 0, 1, false));
            for (var fixture : cases) {
                Result reference = fixture.compareReference() ? ReferenceProcesses.reference(root, referenceJar, fixture.arguments()) : null;
                var command = new ArrayList<String>(); command.add(launcher.toString()); command.addAll(fixture.arguments());
                var current = run(root, command, Duration.ofSeconds(45));
                compare(fixture, reference, current);
                var row = new LinkedHashMap<String, Object>();
                row.put("fixture", fixture); row.put("current", current);
                if (reference != null) row.put("reference", reference);
                else row.put("referenceNotExecuted", fixture.name().equals("empty-no-gui")
                        ? "original GUI invocation is outside the headless checking profile"
                        : fixture.name().equals("missing-option-value")
                        ? "original internal-error case already captured during preparation; replacement reports a usage error"
                        : "out-of-profile option; spelling verified against actual reference help");
                results.add(row);
            }
            var jarVersion = run(root, List.of(java().toString(), "-jar", replacementJar.toString(), "--version"), Duration.ofSeconds(45));
            require(jarVersion.exitCode() == 0 && jarVersion.stdout().isEmpty()
                    && jarVersion.stderr().contains(profile.id()), "java -jar checking entry failed");
            results.add(Map.of("fixture", "java-jar-version", "current", jarVersion));
            Path encodingSource = folder.resolve("Encoding.ili");
            Files.writeString(encodingSource, "INTERLIS 2.4; MODEL Encoding AT \"x\" VERSION \"1\" = END Encoding. !! Grüezi 😀\n");
            var encodingProbe = run(root, List.of(java().toString(), "-Dstdout.encoding=windows-1252",
                    "-Dstderr.encoding=windows-1252", "-cp", replacementJar.toString(),
                    "guru.interlis.cli.Main", "format", encodingSource.toString()), Duration.ofSeconds(45));
            require(encodingProbe.exitCode() == 0 && encodingProbe.stderr().isEmpty()
                    && encodingProbe.stdout().contains("Grüezi 😀"), "CLI output depends on native stream encoding");
            pin(referenceJar, profile.referenceSha256());
            var after = new TreeMap<>(artifacts(root)); after.put(root.relativize(replacementJar).toString(), hash(replacementJar));
            require(artifacts.equals(after), "Artifacts changed during CLI comparison");
            var report = new LinkedHashMap<String, Object>();
            report.put("status", "passed"); report.put("profile", profile.id());
            report.put("referenceVersion", profile.referenceVersion()); report.put("referenceSha256", profile.referenceSha256());
            report.put("os", System.getProperty("os.name")); report.put("architecture", System.getProperty("os.arch"));
            report.put("runtime", Runtime.version().toString()); report.put("artifacts", artifacts);
            report.put("optionInventory", inventory); report.put("cases", results);
            report.put("encodingProbe", encodingProbe);
            report.put("streamEncoding", "UTF-8: explicit replacement host streams and explicit stdout/stderr JVM properties for the external reference");
            report.put("textComparison", "Outcome/channel profile; replacement identity, timestamps and canonical diagnostic wording are explicit differences.");
            report.put("deviations", List.of("No legacy GUI for an empty invocation", "Unknown options and missing values fail strictly, even where the reference continues or crashes",
                    "A missing source is an operational failure; the reference prints a missing-file message but exits successfully",
                    "Bare model names are an explicit extension resolved by the shared engine; the reference accepts source paths only",
                    "Project language/norm profiles remain in force", "Generator, repository-administration and unsupported host options are rejected before IO"));
            report.put("sourceSha256", Map.of("Base.ili", hash(base), "Root.ili", hash(model), "Bad.ili", hash(bad)));
            report.put("additionalSourceSha256", Map.of("Multiple.ili", hash(multiple), "Syntax.ili", hash(syntax)));
            write(output, report);
            System.out.println("LEGACY_CLI_PASS " + results.size() + " actual replacement processes; " + inventory.size() + " inventoried options");
        } finally {
            Files.deleteIfExists(multiple);
            try (var files = Files.list(folder)) { for (Path file : files.toList()) Files.deleteIfExists(file); }
            Files.deleteIfExists(folder);
        }
    }
}
