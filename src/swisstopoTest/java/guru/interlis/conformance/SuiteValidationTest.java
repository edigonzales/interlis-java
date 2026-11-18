package guru.interlis.conformance;

import ch.interlis.testsuite.util.TestUtil;
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
    void acceptsOriginalFullTransfersWithAndWithoutModelledUuidDomains() throws IOException {
        var suite = Path.of(System.getProperty("suite.directory"));
        assertTrue(SuiteValidation.validate(suite.resolve("data/RKA.T01a.xtf"),
                suite.resolve("data/models")));
        assertTrue(SuiteValidation.validate(suite.resolve("data/RAt.T11c.xtf"),
                suite.resolve("data/models")));
    }

    @Test
    void originalUuidViolationsIdentifyBasketAndObject() throws Exception {
        var output = Files.createDirectory(directory.resolve("uuid"));
        assertEquals(0, runOriginalTests(".*AttributeTest\\.RAt_T11[ab]", output));
        var diagnostics = Files.readString(output.resolve("stdout"));
        var basketFinding = diagnostics.lines().filter(line -> line.startsWith("RAt.T11a.xtf DATA INVALID_OID "))
                .findFirst().orElseThrow(() -> new AssertionError(diagnostics));
        assertTrue(basketFinding.contains("\"bid\":\"c91766e-ff86-4d5a-8cbf-9fb5620a4ee4\""), basketFinding);
        assertTrue(!basketFinding.contains("objectType"), basketFinding);
        var objectFinding = diagnostics.lines().filter(line -> line.startsWith("RAt.T11b.xtf DATA INVALID_OID "))
                .findFirst().orElseThrow(() -> new AssertionError(diagnostics));
        assertTrue(objectFinding.contains("\"tid\":\"a33305d486-cf9b-11e7-abc4-cec278b6b50a\""), objectFinding);
        assertTrue(!objectFinding.contains("\"oid\""), objectFinding);
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
                directory.resolve("missing.xtf"), directory));
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
    void originalEmbeddedAssociationChecksUniqueAcrossOwners() throws Exception {
        var output = Files.createDirectory(directory.resolve("embedded-unique"));
        assertEquals(0, runOriginalTests(".*ConstraintTest\\.RKo_T09a", output));
        var diagnostics = Files.readString(output.resolve("stdout"));
        assertTrue(diagnostics.contains("RKo.T09a.xtf DATA DATASET_UNIQUE_DUPLICATE"), diagnostics);
        assertTrue(diagnostics.contains("RKo.T09a.xtf EOF objects=3 violations=1"), diagnostics);
    }

    @Test
    void originalNumericRangeUsesModelPrecision() throws Exception {
        var output = Files.createDirectory(directory.resolve("numeric-rounding"));
        assertEquals(0, runOriginalTests(".*AttributeTest\\.RAt_T09[eg]", output));
        var diagnostics = Files.readString(output.resolve("stdout"));
        assertTrue(diagnostics.contains("RAt.T09e.xtf EOF objects=4 violations=0"), diagnostics);
        assertTrue(diagnostics.lines().anyMatch(line -> line.startsWith("RAt.T09g.xtf DATA VALUE_OUT_OF_RANGE ")
                && line.contains("\"arguments\":[\"10000.1\",\"1.0\",\"10000.0\"]")), diagnostics);
    }

    @Test
    void knownDeviationKeepsTheNativeFailureAndOnlyAppliesToSelectedCases() throws Exception {
        var output = Files.createDirectory(directory.resolve("known"));
        assertEquals(0, runOriginalTests(".*SurfaceAreaAttributeTest\\.RSU_T16a", output));
        assertTrue(Files.readString(output.resolve("summary.txt")).contains("Known deviations: 1"));
        assertTrue(Files.readString(output.resolve("TEST-junit-vintage.xml")).contains("<failure"));
    }

    @Test
    void comparesTheFixtureFailureKindAndCompleteEvidence() {
        var expected = new SuiteJUnit.Deviation("case.xtf", java.util.List.of("VALUE_OUT_OF_RANGE"), "reason");
        var actual = new SuiteValidation.Observation("case.xtf", expected.evidence(), false);
        assertTrue(expected.matches(actual, new AssertionError()));
        assertFalse(expected.matches(actual, null));
        assertFalse(expected.matches(actual, new IllegalStateException()));
        assertFalse(expected.matches(new SuiteValidation.Observation("case.xtf",
                java.util.List.of("REQUIRED_VALUE_MISSING"), false), new AssertionError()));
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

    @Test
    void unexpectedFailureFailsTheRunner() throws Exception {
        var baseline = Files.writeString(directory.resolve("empty.tsv"), "");
        var output = Files.createDirectory(directory.resolve("unexpected"));
        assertEquals(1, runOriginalTests(".*RSU_T16a", output, baseline));
        var summary = Files.readString(output.resolve("summary.txt"));
        assertTrue(summary.contains("Known deviations: 0"), summary);
        assertTrue(summary.contains("RSU_T16a"), summary);
    }

    @Test
    void previouslyFailingCaseThatNowPassesFailsTheRunner() throws Exception {
        var baseline = Files.writeString(directory.resolve("stale.tsv"),
                "ch.interlis.testsuite.ConstraintTest.RKo_T09a\tRKo.T09a.xtf\tDATASET_UNIQUE_DUPLICATE\tStale probe\n");
        var output = Files.createDirectory(directory.resolve("stale"));
        assertEquals(1, runOriginalTests(".*RKo_T09a", output, baseline));
        var summary = Files.readString(output.resolve("summary.txt"));
        assertTrue(summary.contains("observed SUCCESSFUL"), summary);
    }

    @Test
    void processErrorsCannotSatisfyNegativeOriginalTests() {
        var wrongPhase = """
                {"tool":"interlis-validator","exitCode":1,"status":"rejected","completed":false,
                 "phase":"compilation","diagnostics":[{"code":"INVALID_WIRE"}]}
                """;
        assertThrows(IOException.class, () -> SuiteValidation.qualify("x.xtf", 1, wrongPhase, ""));
        assertThrows(IOException.class, () -> SuiteValidation.qualify("x.xtf", 1, "", "crashed"));
        assertThrows(IOException.class, () -> SuiteValidation.qualify("x.xtf", 1, wrongPhase + " {}", ""));
        assertThrows(IOException.class, () -> SuiteValidation.qualify("x.xtf", 3,
                wrongPhase.replace("compilation", "transfer"), ""));
    }

    private int runOriginalTests(String filter, Path output) throws Exception {
        return runOriginalTests(filter, output,
                Path.of(System.getProperty("repo.root"), "testdata/conformance/swisstopo-deviations.tsv"));
    }

    private int runOriginalTests(String filter, Path output, Path baseline) throws Exception {
        var suite = Path.of(System.getProperty("suite.directory"));
        var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        var process = new ProcessBuilder(javaExecutable.toString(), "-Dvendor=interlis-java", "-Dsuite.validator=" + System.getProperty("suite.validator"), "-cp",
                System.getProperty("suite.classpath"), SuiteJUnit.class.getName(),
                System.getProperty("suite.classes"), filter, output.toString(), baseline.toString())
                .directory(suite.resolve("routine").toFile())
                .redirectOutput(output.resolve("stdout").toFile())
                .redirectError(output.resolve("stderr").toFile()).start();
        try {
            assertTrue(process.waitFor(45, java.util.concurrent.TimeUnit.SECONDS), "Original tests timed out");
            return process.exitValue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private boolean validate(String xml) throws IOException {
        var path = Files.writeString(directory.resolve("delivery.xtf"), xml);
        return SuiteValidation.validate(path, directory);
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
