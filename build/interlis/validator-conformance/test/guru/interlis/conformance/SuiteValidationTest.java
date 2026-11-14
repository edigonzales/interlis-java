package guru.interlis.conformance;

import ch.interlis.testsuite.util.TestUtil;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class SuiteValidationTest {
    @TempDir Path directory;

    @BeforeEach
    void writeModel() throws IOException {
        Files.writeString(directory.resolve("TestSuite2.ili"), """
                INTERLIS 2.3;
                CONTRACTED MODEL TestSuite2 AT "https://example.org" VERSION "1" =
                  FUNCTION externalCheck(value: TEXT): BOOLEAN;
                  TOPIC Data =
                    CLASS Item = label: MANDATORY TEXT*3; END Item;
                    CLASS Unchecked =
                      label: MANDATORY TEXT;
                      MANDATORY CONSTRAINT externalCheck(label);
                    END Unchecked;
                  END Data;
                END TestSuite2.
                """);
        Files.writeString(directory.resolve("Roads.ili"), "unrelated invalid source");
    }

    @Test
    void validatesOriginalFullTransferWithTransferLocalIdentifiers() throws IOException {
        var suite = Path.of(System.getProperty("suite.directory"));
        assertTrue(SuiteValidation.validate(suite.resolve("data/RKA.T01a.xtf"),
                suite.resolve("data/models"), CancellationToken.none()));
    }

    @Test
    void validDataDoesNotRequireUnrelatedModelsOrUnusedRules() throws IOException {
        assertTrue(validate(transfer("Item", "abc")));
    }

    @Test
    void rejectsActualDataViolation() throws IOException {
        assertFalse(validate(transfer("Item", "abcd")));
    }

    @Test
    void originalNegativeFixtureFailsForTheMissingAttribute() throws Exception {
        var output = Files.createDirectory(directory.resolve("missing-attribute"));
        assertEquals(0, runOriginalTests(".*CardinalityAttributeTest\\.RKA_T01b", output));
        var diagnostics = Files.readString(output.resolve("stdout"));
        assertTrue(diagnostics.contains("RKA.T01b.xtf DATA REQUIRED_VALUE_MISSING"), diagnostics);
        assertTrue(diagnostics.contains("RKA.T01b.xtf EOF objects=2 violations=1"), diagnostics);
    }

    @Test
    void rejectsMalformedXml() throws IOException {
        String valid = transfer("Item", "abc");
        assertFalse(validate(valid.replace("xmlns=\"", "xmlns=")));
    }

    @Test
    void acceptsHistoricalIssuerMetadata() throws IOException {
        assertTrue(validate(transfer("Item", "abc").replace("https://example.org", "http://example.org")));
    }

    @Test
    void missingModelSourceIsATechnicalFailure() throws IOException {
        Files.delete(directory.resolve("TestSuite2.ili"));
        assertThrows(IOException.class, () -> validate(transfer("Item", "abcd")));
    }

    @Test
    void doesNotReturnFalseForTechnicalErrors() {
        assertThrows(IOException.class, () -> SuiteValidation.validate(
                directory.resolve("missing.xtf"), directory, CancellationToken.none()));
        assertThrows(IllegalArgumentException.class, () -> TestUtil.runJob("unknown", "missing.xtf"));
    }

    @Test
    void doesNotReturnTrueForRelevantUncheckedConstraint() {
        var failure = assertThrows(IllegalStateException.class,
                () -> validate(transfer("Unchecked", "abc")));
        assertTrue(failure.getMessage().contains("UNSUPPORTED_EXPRESSION"), failure::getMessage);
    }

    @Test
    void originalCrossBasketReferencesRequireAnExplicitBasketId() throws Exception {
        var output = Files.createDirectory(directory.resolve("cross-basket"));
        assertEquals(0, runOriginalTests(".*AssociationTest\\.RBe_T01[bd]", output));
        var diagnostics = Files.readString(output.resolve("stdout"));
        assertTrue(diagnostics.contains("RBe.T01b.xtf DATA REFERENCE_TARGET_BASKET"), diagnostics);
        assertTrue(diagnostics.contains("RBe.T01d.xtf DATA REFERENCE_TARGET_BASKET"), diagnostics);
    }

    @Test
    void originalStandaloneLinksRequireTheirEndpoints() throws Exception {
        var output = Files.createDirectory(directory.resolve("endpoints"));
        assertEquals(0, runOriginalTests(".*(RRiO_T03d|RBk_T03c)", output));
        var diagnostics = Files.readString(output.resolve("stdout"));
        assertTrue(diagnostics.contains("RRiO.T03d.xtf DATA REQUIRED_VALUE_MISSING"), diagnostics);
        assertTrue(diagnostics.contains("RBk.T03c.xtf DATA REQUIRED_VALUE_MISSING"), diagnostics);
    }

    @Test
    void originalDefinedConstraintRejectsAnEmptyAssociationAccess() throws Exception {
        var output = Files.createDirectory(directory.resolve("defined"));
        assertEquals(0, runOriginalTests(".*ConstraintTest\\.RKo_T11b", output));
        var diagnostics = Files.readString(output.resolve("stdout"));
        assertTrue(diagnostics.contains("RKo.T11b.xtf DATA MANDATORY_CONSTRAINT_FALSE"), diagnostics);
    }

    @Test
    void originalAreaAndSurfaceRejectContactInsideAnUnsplitEdge() throws Exception {
        var output = Files.createDirectory(directory.resolve("ring-contact"));
        assertEquals(0, runOriginalTests(".*SurfaceAreaAttributeTest\\.RSU_T03[ab]", output));
        var diagnostics = Files.readString(output.resolve("stdout"));
        assertTrue(diagnostics.contains("RSU.T03a.xtf DATA GEOMETRY_SELF_INTERSECTION"), diagnostics);
        assertTrue(diagnostics.contains("RSU.T03b.xtf DATA GEOMETRY_SELF_INTERSECTION"), diagnostics);
    }

    @Test
    void anEmptyTestSelectionFails() throws Exception {
        var output = Files.createDirectory(directory.resolve("empty"));
        assertEquals(1, runOriginalTests(".*NoSuchOriginalTest", output));
        assertTrue(Files.readString(output.resolve("summary.txt")).contains("0 tests started"));
    }

    @Test
    void runsTheOriginalTestThatIlivalidatorSkips() throws Exception {
        var output = Files.createDirectory(directory.resolve("historical"));
        runOriginalTests(".*HeaderSectionTest\\.RHE_T01b", output);
        var report = Files.readString(output.resolve("TEST-junit-vintage.xml"));
        assertTrue(report.contains("name=\"RHE_T01b\""));
        assertFalse(report.contains("<skipped"), report);
    }

    private int runOriginalTests(String filter, Path output) throws Exception {
        var suite = Path.of(System.getProperty("suite.directory"));
        var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        var process = new ProcessBuilder(javaExecutable.toString(), "-Dvendor=interlis-java", "-cp",
                System.getProperty("java.class.path"), SuiteJUnit.class.getName(),
                System.getProperty("suite.classes"), filter, output.toString())
                .directory(suite.resolve("routine").toFile())
                .redirectOutput(output.resolve("stdout").toFile())
                .redirectError(output.resolve("stderr").toFile()).start();
        try {
            assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS), "Original tests timed out");
            return process.exitValue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private boolean validate(String xml) throws IOException {
        var path = Files.writeString(directory.resolve("delivery.xtf"), xml);
        return SuiteValidation.validate(path, directory, CancellationToken.none());
    }

    private static String transfer(String type, String label) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="TestSuite2" VERSION="1" URI="https://example.org"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION><TestSuite2.Data BID="basket">
                    <TestSuite2.Data.%1$s TID="object"><label>%2$s</label></TestSuite2.Data.%1$s>
                  </TestSuite2.Data></DATASECTION>
                </TRANSFER>
                """.formatted(type, label);
    }
}
