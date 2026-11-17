package guru.interlis.validator.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorCommandTest {
    @TempDir Path directory;

    @Test
    void validatesXtf23UsingANeighbouringModelAndExplicitModelSelection() throws Exception {
        model(directory, "2.3", "");
        write("input.data", legacy("<name>valid</name>"));
        var result = run("--model", "Checks", "input.data", "--offline");
        assertEquals(0, result.exit(), result.error());
        assertTrue(result.error().contains("Objects checked: 1"), result.error());
        assertEquals("", result.output());
    }

    @Test
    void validatesPrefixedXtf24WithABomInAnExplicitLocalRepository() throws Exception {
        var repository = Files.createDirectory(directory.resolve("local models"));
        model(repository, "2.4", "");
        Files.writeString(directory.resolve("data"), "\uFEFF" + """
                <?xml version="1.0" encoding="UTF-8"?>
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                  xmlns:m="http://www.interlis.ch/xtf/2.4/Checks">
                  <!-- prefix and encoding are independent of the filename -->
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection><m:Data i:bid="basket"><m:Item i:tid="one">
                    <m:name>valid</m:name>
                  </m:Item></m:Data></i:datasection>
                </i:transfer>
                """, StandardCharsets.UTF_8);
        var result = run("--offline", "--repository", "local models", "data");
        assertEquals(0, result.exit(), result.error());
        assertTrue(result.error().contains("Objects checked: 1"), result.error());
    }

    @Test
    void usesRepositoryOrderForLooseAndIndexedModels() throws Exception {
        var wide = Files.createDirectory(directory.resolve("wide"));
        var narrow = Files.createDirectory(directory.resolve("narrow"));
        model(wide, "2.3", "");
        model(narrow, "2.3", "");
        var file = narrow.resolve("Checks.ili");
        Files.writeString(file, Files.readString(file).replace("TEXT*8", "TEXT*4"));
        write("input.xtf", legacy("<name>abcde</name>"));
        assertRepositoryOrder();
        Files.writeString(narrow.resolve("ilimodels.xml"), """
                <RepositoryIndex><ModelMetadata>
                  <Name>Checks</Name><SchemaLanguage>ili2_3</SchemaLanguage>
                  <File>Checks.ili</File><Version>1</Version>
                </ModelMetadata></RepositoryIndex>
                """);
        assertRepositoryOrder();
    }

    private void assertRepositoryOrder() {
        var firstWide = run("--offline", "--repository", "wide", "--repository", "narrow", "input.xtf");
        assertEquals(0, firstWide.exit(), firstWide.error());
        var firstNarrow = run("--offline", "--repository", "narrow", "--repository", "wide", "input.xtf");
        assertEquals(1, firstNarrow.exit(), firstNarrow.error());
        assertTrue(firstNarrow.error().contains("TEXT_TOO_LONG"), firstNarrow.error());
    }

    @Test
    void reportsADataViolationAtTheObjectAndItsModelRule() throws Exception {
        model(directory, "2.3", "");
        write("bad.xtf", legacy("<name>too many characters</name>"));
        var result = run("--offline", "bad.xtf");
        assertEquals(1, result.exit(), result.error());
        assertTrue(result.error().contains("TEXT_TOO_LONG") && result.error().contains("TID=one")
                && result.error().contains("/name") && result.error().contains("model rule name"), result.error());
    }

    @Test
    void readsToPhysicalEofAfterAValidHeaderAndDataset() throws Exception {
        model(directory, "2.3", "");
        write("early.xtf", legacy("<name>valid</wrong>"));
        write("trail.xtf", legacy("<name>valid</name>") + "<trailing/>");
        for (var file : List.of("early.xtf", "trail.xtf")) {
            var result = run("--offline", file);
            assertEquals(1, result.exit(), result.error());
            assertTrue(result.error().contains("transfer: MALFORMED_XML"), result.error());
            assertFalse(result.error().contains("No violations"));
        }
    }

    @Test
    void separatesUnresolvedModelsAndFileFailuresFromMalformedHeaders() throws Exception {
        write("unknown.xtf", legacy("<name>valid</name>"));
        write("header.xtf", legacy("").replace(" SENDER=\"test\"", ""));
        var missingModel = run("--offline", "unknown.xtf");
        assertEquals(3, missingModel.exit(), missingModel.error());
        assertTrue(missingModel.error().contains("model resolution") && missingModel.error().contains("Checks"),
                missingModel.error());
        assertEquals(3, run("--offline", "missing.xtf").exit());
        var invalid = run("--offline", "header.xtf");
        assertEquals(1, invalid.exit(), invalid.error());
        assertTrue(invalid.error().contains("SENDER"), invalid.error());
    }

    @Test
    void doesNotProcessDtdsOrExternalEntities() throws Exception {
        write("unsafe.xtf", legacy("").replace("<TRANSFER", """
                <!DOCTYPE TRANSFER [<!ENTITY external SYSTEM "file:///no-such-file">]>
                <TRANSFER"""));
        var result = run("--offline", "unsafe.xtf");
        assertEquals(1, result.exit(), result.error());
        assertTrue(result.error().startsWith("header:"), result.error());
    }

    @Test
    void usesJtsForTopology() throws Exception {
        model(directory, "2.3", """
                shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.001;
                """);
        var ring = """
                <shape><SURFACE><BOUNDARY><POLYLINE>
                <COORD><C1>0</C1><C2>0</C2></COORD>
                <COORD><C1>2</C1><C2>2</C2></COORD>
                <COORD><C1>2</C1><C2>0</C2></COORD>
                <COORD><C1>0</C1><C2>2</C2></COORD>
                <COORD><C1>0</C1><C2>0</C2></COORD>
                </POLYLINE></BOUNDARY></SURFACE></shape>
                """;
        write("crossed.xtf", legacy("<name>valid</name>" + ring));
        var result = run("--offline", "crossed.xtf");
        assertEquals(1, result.exit(), result.error());
        assertTrue(result.error().contains("GEOMETRY_SELF_INTERSECTION"), result.error());
    }

    @Test
    void reportsAnUncheckedConstraintWithoutReportingSuccess() throws Exception {
        write("Checks.ili", """
                INTERLIS 2.3;
                CONTRACTED MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  FUNCTION ok(value: TEXT): BOOLEAN;
                  TOPIC Data =
                    CLASS Item =
                      name : MANDATORY TEXT*8;
                      MANDATORY CONSTRAINT Checks.ok(name);
                    END Item;
                  END Data;
                END Checks.
                """);
        write("unchecked.xtf", legacy("<name>valid</name>"));
        var result = run("--offline", "unchecked.xtf");
        assertEquals(5, result.exit(), result.error());
        assertTrue(result.error().contains("UNSUPPORTED_EXPRESSION"), result.error());
        assertFalse(result.error().contains("No violations"));
        write("rejected.xtf", legacy("<name>too many characters</name>"));
        var rejected = run("--json", "--offline", "rejected.xtf");
        assertEquals(1, rejected.exit(), rejected.output());
        assertTrue(rejected.json().get("completed").getAsBoolean());
        assertEquals(1, rejected.json().get("violations").getAsLong());
        assertTrue(rejected.json().getAsJsonArray("unchecked").size() > 0);
    }

    @Test
    void parsesArgumentsBeforeIoAndRecognisesCancellation() {
        assertEquals(0, run("--help").exit());
        assertEquals(0, run("--version").exit());
        for (var arguments : List.of(List.of("--unknown"), List.of("--offline", "--offline", "file"),
                List.of("--repository"), List.of("--refdata", "second"), List.of("--model", "--offline", "file"),
                List.of("--temp-dir", ".", "file"), List.of("--max-diagnostics", "0", "file"),
                List.of("--storage", "memory", "--storage", "mvstore", "file"))) {
            assertEquals(2, run(arguments.toArray(String[]::new)).exit(), arguments.toString());
        }
        assertEquals(List.of(Path.of("-input.xtf")), ValidatorOptions.parse(List.of("--", "-input.xtf")).primary());
        assertEquals(4, ValidatorCommand.run(List.of("missing.xtf"), directory,
                new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter()), () -> true));
    }

    @Test
    void globalUniqueProducesTheSameBoundedJsonWithBothStores() throws Exception {
        model(directory, "2.3", "UNIQUE name;");
        write("first file.xtf", legacy("<name>alpha</name>").replace("BID=\"basket\"", "BID=\"first\""));
        write("second.xtf", legacy("<name>alpha</name>").replace("BID=\"basket\"", "BID=\"second\""));
        write("third.xtf", legacy("<name>beta</name>").replace("BID=\"basket\"", "BID=\"third\""));
        write("fourth.xtf", legacy("<name>beta</name>").replace("BID=\"basket\"", "BID=\"fourth\""));
        var passed = run("--offline", "--json", "--verbose", "first file.xtf");
        assertEquals(0, passed.exit(), passed.error());
        assertEquals("passed", passed.json().get("status").getAsString());
        assertTrue(passed.error().contains("Preparing"));
        assertEquals(1, passed.json().get("objects").getAsLong());
        var args = List.of("--offline", "--json", "--max-diagnostics", "1", "first file.xtf", "second.xtf", "third.xtf", "fourth.xtf");
        var memory = run(args.toArray(String[]::new));
        var diskArgs = new java.util.ArrayList<>(args);
        diskArgs.addAll(List.of("--storage", "mvstore", "--temp-dir", "."));
        var disk = run(diskArgs.toArray(String[]::new));
        assertEquals(1, memory.exit(), memory.error() + memory.output());
        assertEquals(memory.exit(), disk.exit(), disk.error() + disk.output());
        assertEquals(memory.json(), disk.json());
        var json = memory.json();
        assertEquals("rejected", json.get("status").getAsString());
        assertTrue(json.get("completed").getAsBoolean());
        assertEquals(4, json.get("objects").getAsLong());
        assertEquals(2, json.get("violations").getAsLong());
        assertTrue(json.get("diagnosticsTruncated").getAsBoolean());
        var diagnostics = json.getAsJsonArray("diagnostics");
        assertEquals(1, diagnostics.size());
        var diagnostic = diagnostics.get(0).getAsJsonObject();
        assertEquals("DATASET_UNIQUE_DUPLICATE", diagnostic.get("code").getAsString());
        assertTrue(diagnostic.getAsJsonObject("location").get("source").getAsString().endsWith(".xtf"));
        assertTrue(diagnostic.getAsJsonObject("ruleLocation").get("source").getAsString().endsWith(".ili"));
        assertEquals(1, diagnostic.getAsJsonArray("relatedLocations").size());
        assertNoRunDirectories();
    }

    @Test
    void resolvesStableReferenceAcrossPrimaryAndReferenceFiles() throws Exception {
        write("Checks.ili", """
                INTERLIS 2.3;
                MODEL Catalogue AT "https://example.org" VERSION "1" =
                  DOMAIN Id = OID TEXT*20;
                  TOPIC Lists =
                    CLASS Target =
                      OID AS Id;
                      name : MANDATORY TEXT*2;
                      SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
                    END Target;
                  END Lists;
                END Catalogue.
                MODEL Checks AT "https://example.org" VERSION "1" =
                  IMPORTS Catalogue;
                  TOPIC Data =
                    DEPENDS ON Catalogue.Lists;
                    STRUCTURE Pointer = peer: REFERENCE TO (EXTERNAL) Catalogue.Lists.Target; END Pointer;
                    CLASS Item = link: Pointer; END Item;
                  END Data;
                END Checks.
                """);
        var primary = legacy("<link><Checks.Data.Pointer><peer REF=\"ids:target\" BID=\"target-basket\"/></Checks.Data.Pointer></link>")
                .replace("</MODELS>", "</MODELS><OIDSPACES><OIDSPACE NAME=\"ids\" OIDDOMAIN=\"Catalogue.Id\"/></OIDSPACES>");
        var target = legacy("<name>ok</name>").replace("NAME=\"Checks\"", "NAME=\"Catalogue\"")
                .replace("Checks.Data.Item", "Catalogue.Lists.Target").replace("Checks.Data", "Catalogue.Lists")
                .replace("TID=\"one\"", "TID=\"ids:target\"").replace("BID=\"basket\"", "BID=\"target-basket\"")
                .replace("</MODELS>", "</MODELS><OIDSPACES><OIDSPACE NAME=\"ids\" OIDDOMAIN=\"Catalogue.Id\"/></OIDSPACES>");
        write("source.xtf", primary);
        write("target.xtf", target);
        write("witness.xtf", target.replace("<name>ok</name>", "<name>invalid for primary</name>"));
        var missing = run("--json", "--offline", "source.xtf");
        assertEquals(1, missing.exit(), missing.output());
        assertEquals("REFERENCE_TARGET_MISSING", missing.json().getAsJsonArray("diagnostics").get(0)
                .getAsJsonObject().get("code").getAsString());
        var partial = run("--json", "--offline", "--partial", "source.xtf");
        assertEquals(5, partial.exit(), partial.output());
        assertEquals("partial-dataset", partial.json().get("scope").getAsString());
        assertTrue(partial.json().get("completed").getAsBoolean());
        assertEquals(0, partial.json().get("violations").getAsLong());
        assertTrue(partial.json().getAsJsonArray("unchecked").size() > 0);
        var both = run("--json", "--offline", "source.xtf", "target.xtf");
        assertEquals(0, both.exit(), both.output());
        var witness = run("--json", "--offline", "source.xtf", "--refdata", "witness.xtf");
        assertEquals(0, witness.exit(), witness.output());
        assertEquals("reference", witness.json().getAsJsonArray("inputs").get(1).getAsJsonObject().get("role").getAsString());
        var duplicated = run("--json", "--offline", "source.xtf", "target.xtf", "--refdata", "witness.xtf");
        assertEquals(1, duplicated.exit(), duplicated.output());
        assertTrue(duplicated.json().getAsJsonArray("diagnostics").asList().stream()
                .map(d -> d.getAsJsonObject())
                .anyMatch(d -> "AMBIGUOUS_OBJECT_IDENTITY".equals(d.get("code").getAsString())
                        && !d.getAsJsonArray("relatedLocations").isEmpty()), duplicated.output());
    }

    @Test
    void anExtensionUsedOnlyAsReferenceCreatesNoEmptySetPopulation() throws Exception {
        model(directory, "2.3", "");
        write("Extra.ili", """
                INTERLIS 2.3;
                MODEL Extra AT "https://example.org/checks" VERSION "1" =
                  IMPORTS Checks;
                  TOPIC Lists EXTENDS Checks.Data =
                    CLASS Witness =
                      SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
                    END Witness;
                  END Lists;
                END Extra.
                """);
        write("primary.xtf", legacy("<name>ok</name>"));
        var extended = legacy("").replace("NAME=\"Checks\"", "NAME=\"Extra\"")
                .replace("Checks.Data.Item", "Extra.Lists.Witness").replace("Checks.Data", "Extra.Lists");
        write("reference.xtf", extended);
        var referenced = run("--offline", "--json", "primary.xtf", "--refdata", "reference.xtf");
        assertEquals(0, referenced.exit(), referenced.output());
        assertEquals(1, referenced.json().get("objects").getAsLong());

        write("extended-primary.xtf", extended.replaceFirst("(?s)<Extra.Lists.Witness.*?</Extra.Lists.Witness>", ""));
        var delivered = run("--offline", "--json", "--model", "Checks",
                "primary.xtf", "extended-primary.xtf", "--refdata", "reference.xtf");
        assertEquals(1, delivered.exit(), delivered.output());
        assertTrue(delivered.output().contains("SET_CONSTRAINT_FALSE"), delivered.output());
    }

    @Test
    void anExplicitPrimaryModelCannotPassAnUnrelatedTransferLoadedForReference() throws Exception {
        model(directory, "2.3", "");
        write("Other.ili", Files.readString(directory.resolve("Checks.ili")).replace("MODEL Checks", "MODEL Other")
                .replace("END Checks.", "END Other."));
        var foreign = legacy("<name>too many characters</name>").replace("NAME=\"Checks\"", "NAME=\"Other\"")
                .replace("Checks.Data", "Other.Data");
        write("foreign-primary.xtf", foreign);
        write("reference.xtf", foreign);
        var mismatched = run("--json", "--offline", "--model", "Checks", "foreign-primary.xtf",
                "--refdata", "reference.xtf");
        assertFailed(mismatched, 1, "transfer");
        assertTrue(mismatched.output().contains("PRIMARY_MODEL_MISMATCH"), mismatched.output());

        write("checks.xtf", legacy("<name>ok</name>"));
        var matched = run("--json", "--offline", "--model", "Checks", "checks.xtf", "--refdata", "reference.xtf");
        assertEquals(0, matched.exit(), matched.output());
        assertEquals(1, matched.json().get("objects").getAsLong());
    }

    @Test
    void reportsEarlyAndOperationalFailuresWithoutACompletedResult() throws Exception {
        model(directory, "2.3", "");
        write("bad.xtf", legacy("<name>too many characters</name>"));
        write("wrong.xtf", legacy("<name>ok</name>").replace("VERSION=\"1\"", "VERSION=\"2\""));
        write("trailing.xtf", legacy("<name>ok</name>") + "<trailing/>");
        write("limit.xtf", legacy("<name>" + "x".repeat(1_048_577) + "</name>"));
        assertFailed(run("--json", "--offline", "bad.xtf", "wrong.xtf"), 1, "header");
        var malformed = run("--json", "--offline", "bad.xtf", "trailing.xtf",
                "--storage", "mvstore", "--temp-dir", ".");
        assertFailed(malformed, 1, "transfer");
        assertTrue(malformed.output().contains("MALFORMED_XML"), malformed.output());
        assertTrue(malformed.output().contains("TEXT_TOO_LONG"), malformed.output());
        var resourceFailure = run("--json", "--offline", "bad.xtf", "limit.xtf",
                "--storage", "mvstore", "--temp-dir", ".");
        assertFailed(resourceFailure, 3, "transfer");
        assertTrue(resourceFailure.output().contains("LIMIT_EXCEEDED"), resourceFailure.output());
        assertTrue(resourceFailure.output().contains("TEXT_TOO_LONG"), resourceFailure.output());
        assertEquals(1, resourceFailure.json().get("violations").getAsLong());
        assertFailed(run("--json", "--offline", "bad.xtf", "--refdata", "missing.xtf"), 3, "input");
        assertFailed(run("--json", "--offline", "bad.xtf", "--storage", "mvstore",
                "--temp-dir", "missing-directory"), 3, "storage");
        assertFailed(run("--json", "--refdata", "bad.xtf"), 2, "options");
        assertFailed(run("--json", "--offline", "wrong.xtf"), 3, "model resolution");
        assertNoRunDirectories();
        var version = run("--version", "--json");
        assertEquals(0, version.exit());
        assertEquals("interlis-validator", version.json().get("tool").getAsString());
    }

    @Test
    void aLaterUnsupportedBasketDoesNotEraseProvenViolations() throws Exception {
        model(directory, "2.3", "");
        write("bad.xtf", legacy("<name>too many characters</name>"));
        write("incremental.xtf", legacy("<name>ok</name>").replace("BID=\"basket\"", "BID=\"next\" KIND=\"INITIAL\""));
        var unsupported = run("--offline", "--json", "incremental.xtf");
        assertFailed(unsupported, 5, "transfer");
        assertEquals(0, unsupported.json().get("violations").getAsLong());
        assertTrue(unsupported.output().contains("UNSUPPORTED"), unsupported.output());

        var rejected = run("--offline", "--json", "bad.xtf", "incremental.xtf");
        assertFailed(rejected, 1, "transfer");
        assertEquals("rejected", rejected.json().get("status").getAsString());
        assertEquals(1, rejected.json().get("violations").getAsLong());
        assertTrue(rejected.output().contains("UNSUPPORTED") && rejected.output().contains("TEXT_TOO_LONG"),
                rejected.output());

        var capped = run("--offline", "--json", "--max-diagnostics", "1", "bad.xtf", "incremental.xtf");
        assertFailed(capped, 1, "transfer");
        assertEquals(1, capped.json().get("violations").getAsLong());
        assertEquals(1, capped.json().getAsJsonArray("diagnostics").size());
        assertTrue(capped.json().get("diagnosticsTruncated").getAsBoolean());
    }

    @Test
    void reportsAnUndeterminedSetRuleEvenWithNoCandidates() throws Exception {
        model(directory, "2.3", "SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;");
        write("empty.xtf", legacy("").replaceFirst("(?s)<DATASECTION>.*?</DATASECTION>", "<DATASECTION/>"));
        var supplied = run("--json", "--offline", "empty.xtf");
        assertEquals(1, supplied.exit(), supplied.output());
        assertTrue(supplied.output().contains("SET_CONSTRAINT_FALSE"), supplied.output());
        var partial = run("--json", "--offline", "--partial", "empty.xtf");
        assertEquals(5, partial.exit(), partial.output());
        assertTrue(partial.json().get("completed").getAsBoolean());
        assertEquals(0, partial.json().get("violations").getAsLong());
        assertTrue(partial.output().contains("UNDETERMINED"), partial.output());
    }

    @Test
    void cleansUpTheStoreOnCancellationDuringTransfer() throws Exception {
        model(directory, "2.3", "");
        write("ok.xtf", legacy("<name>ok</name>"));
        var output = new StringWriter();
        var errors = new StringWriter();
        CancellationToken afterStorageOpened = () -> {
            try (var files = Files.list(directory)) {
                return files.anyMatch(path -> path.getFileName().toString().startsWith("interlis-validation-"));
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        };
        int code = ValidatorCommand.run(List.of("--json", "--offline", "--storage", "mvstore",
                "--temp-dir", ".", "ok.xtf"), directory, new PrintWriter(output), new PrintWriter(errors), afterStorageOpened);
        assertFailed(new Result(code, output.toString(), errors.toString()), 4, "transfer");
        assertNoRunDirectories();
    }

    @Test
    void treatsBrokenJsonOutputAsAnOperationalFailure() throws Exception {
        model(directory, "2.3", "");
        write("ok.xtf", legacy("<name>ok</name>"));
        var err = new StringWriter();
        var broken = new Writer() {
            @Override public void write(char[] chars, int offset, int length) throws IOException {
                throw new IOException("broken");
            }
            @Override public void flush() throws IOException { throw new IOException("broken"); }
            @Override public void close() { }
        };
        assertEquals(3, ValidatorCommand.run(List.of("--json", "--offline", "ok.xtf"),
                directory, new PrintWriter(broken), new PrintWriter(err), CancellationToken.none()));
        assertTrue(err.toString().contains("Output failure"));
    }

    private static void assertFailed(Result result, int code, String phase) {
        assertEquals(code, result.exit(), result.output() + result.error());
        assertEquals(code, result.json().get("exitCode").getAsInt());
        assertEquals(phase, result.json().get("phase").getAsString());
        assertFalse(result.json().get("completed").getAsBoolean());
    }

    private void assertNoRunDirectories() throws IOException {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("interlis-validation-")));
        }
    }

    private Result run(String... arguments) {
        var output = new StringWriter();
        var error = new StringWriter();
        int exit = ValidatorCommand.run(List.of(arguments), directory, new PrintWriter(output),
                new PrintWriter(error), CancellationToken.none());
        return new Result(exit, output.toString(), error.toString());
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(directory.resolve(name), content);
    }

    private static void model(Path path, String version, String attributes) throws Exception {
        Files.writeString(path.resolve("Checks.ili"), """
                INTERLIS %s;
                MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  DOMAIN Point = COORD 0.000 .. 10.000, 0.000 .. 10.000;
                  TOPIC Data =
                    CLASS Item =
                      name : MANDATORY TEXT*8;
                      %s
                    END Item;
                  END Data;
                END Checks.
                """.formatted(version, attributes));
    }

    private static String legacy(String fields) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="Checks" VERSION="1" URI="https://example.org/checks"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION><Checks.Data BID="basket"><Checks.Data.Item TID="one">
                    %s
                  </Checks.Data.Item></Checks.Data></DATASECTION>
                </TRANSFER>
                """.formatted(fields);
    }

    private record Result(int exit, String output, String error) {
        JsonObject json() { return JsonParser.parseString(output).getAsJsonObject(); }
    }
}
