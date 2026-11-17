package guru.interlis.conformance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs the installed validator and qualifies the outcome for an original assertion. */
public final class SuiteValidation {
    private SuiteValidation() { }

    record Observation(String fixture, List<String> evidence, boolean incomplete) { }

    private static final ThreadLocal<List<Observation>> observations = new ThreadLocal<>();

    static void observe(List<Observation> sink) {
        if (sink == null) observations.remove();
        else observations.set(sink);
    }

    static final class UnfinishedValidation extends IllegalStateException {
        UnfinishedValidation(String detail) {
            super(detail);
        }
    }

    public static boolean validate(Path input, Path modelDirectory) throws IOException {
        if (!Files.isRegularFile(input)) throw new IOException("Missing suite fixture: " + input);
        String launcher = System.getProperty("suite.validator");
        if (launcher == null) throw new IOException("Missing installed validator launcher");
        Path logs = Files.createTempDirectory("suite-validator-process-");
        Path stdout = logs.resolve("stdout");
        Path stderr = logs.resolve("stderr");
        Process process = null;
        try {
            var command = new ArrayList<>(List.of(launcher, "--json", "--offline", "--repository",
                    modelDirectory.toAbsolutePath().toString()));
            // These original fixtures deliberately name the wrong model or version in the header.
            // Supply the suite model closure so the reader can report that mismatch.
            switch (input.getFileName().toString()) {
                case "RHE.T05a.xtf", "RHE.T05b.xtf", "RSU.T17a.xtf", "RSU.T17d.xtf" ->
                        command.addAll(List.of("--model", "TestSuite", "--model", "TestSuite2"));
                default -> { }
            }
            command.addAll(List.of("--", input.toAbsolutePath().toString()));
            var builder = new ProcessBuilder(command).directory(logs.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
            process = builder.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) throw new IOException("Validator timed out: " + input);
            return qualify(input.getFileName().toString(), process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Validator process interrupted: " + input, interrupted);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
            Files.deleteIfExists(logs);
        }
    }

    static boolean qualify(String fixture, int exit, String stdout, String stderr) throws IOException {
        JsonObject report;
        try (var reader = new JsonReader(new StringReader(stdout))) {
            reader.setStrictness(Strictness.STRICT);
            report = JsonParser.parseReader(reader).getAsJsonObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing stdout");
            return qualifyReport(fixture, exit, report);
        } catch (UnfinishedValidation unfinished) {
            throw unfinished;
        } catch (RuntimeException | IOException invalid) {
            throw new IOException(fixture + " validator exit " + exit + ": " + stdout + stderr, invalid);
        }
    }

    private static boolean qualifyReport(String fixture, int exit, JsonObject report) throws IOException {
        if (!"interlis-validator".equals(report.get("tool").getAsString())
                || !Integer.toString(exit).equals(report.get("exitCode").getAsJsonPrimitive().toString())
                || !report.get("completed").getAsJsonPrimitive().isBoolean()) {
            throw new IOException("Inconsistent validator result");
        }
        String status = report.get("status").getAsString();
        String phase = report.get("phase").getAsString();
        boolean completed = report.get("completed").getAsBoolean();
        var evidence = new ArrayList<String>();
        var diagnostics = report.getAsJsonArray("diagnostics");
        for (var item : diagnostics) {
            var finding = item.getAsJsonObject();
            if (!finding.has("code")) continue;
            String code = finding.get("code").getAsString();
            evidence.add(code);
            System.out.println(fixture + (finding.has("location") ? " DATA " : " WIRE ") + code + " " + finding);
        }
        if (exit == 1 && !completed && status.equals("rejected") && List.of("header", "transfer").contains(phase)
                && evidence.stream().anyMatch(List.of("INVALID_WIRE", "MALFORMED_XML", "UNSAFE_XML", "DUPLICATE_ID")::contains)) {
            record(fixture, evidence, false);
            return false;
        }
        if (!completed || !phase.equals("validation")) throw new IOException("Incomplete or non-data validator process");
        if (!"supplied-dataset".equals(report.get("scope").getAsString())) throw new IOException("Wrong dataset scope");
        long violations = report.get("violations").getAsLong();
        long objects = report.get("objects").getAsLong();
        boolean truncated = report.get("diagnosticsTruncated").getAsBoolean();
        if (violations < evidence.size() || truncated != (violations > evidence.size()) || objects < 0
                || evidence.size() != diagnostics.size()
                || diagnostics.asList().stream().anyMatch(item -> !item.getAsJsonObject().has("location"))) {
            throw new IOException("Inconsistent data diagnostics");
        }
        if (truncated) evidence.add("OMITTED_DIAGNOSTICS");
        var unchecked = report.getAsJsonArray("unchecked");
        unchecked.forEach(gap -> evidence.add(gap.getAsString()));
        boolean incomplete = !unchecked.isEmpty();
        boolean accepted = exit == 0 && status.equals("passed") && violations == 0 && !incomplete;
        boolean rejected = exit == 1 && status.equals("rejected") && violations > 0;
        boolean unfinished = exit == 5 && status.equals("incomplete") && violations == 0 && incomplete;
        if (!accepted && !rejected && !unfinished) throw new IOException("Inconsistent validation outcome");
        record(fixture, evidence, unfinished);
        if (unfinished) throw new UnfinishedValidation(fixture + " " + unchecked);
        System.out.println(fixture + " EOF objects=" + objects + " violations=" + violations);
        return accepted;
    }

    private static void record(String fixture, List<String> evidence, boolean incomplete) {
        var sink = observations.get();
        if (sink != null) sink.add(new Observation(fixture, List.copyOf(evidence), incomplete));
    }
}
