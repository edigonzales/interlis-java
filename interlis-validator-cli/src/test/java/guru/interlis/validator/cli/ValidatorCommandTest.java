package guru.interlis.validator.cli;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
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
    }

    @Test
    void parsesArgumentsBeforeIoAndRecognisesCancellation() {
        assertEquals(0, run("--help").exit());
        assertEquals(0, run("--version").exit());
        for (var arguments : List.of(List.of("--unknown"), List.of("--offline", "--offline", "file"),
                List.of("--repository"), List.of("first", "second"), List.of("--model", "--offline", "file"))) {
            assertEquals(2, run(arguments.toArray(String[]::new)).exit(), arguments.toString());
        }
        assertEquals(Path.of("-input.xtf"), ValidatorOptions.parse(List.of("--", "-input.xtf")).input());
        assertEquals(4, ValidatorCommand.run(List.of("missing.xtf"), directory,
                new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter()), () -> true));
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

    private record Result(int exit, String output, String error) { }
}
