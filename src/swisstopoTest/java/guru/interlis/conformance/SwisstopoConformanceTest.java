package guru.interlis.conformance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("swisstopo-originals")
final class SwisstopoConformanceTest {
    @Test
    void originalSuiteMatchesTheQualifiedBaseline() throws Exception {
        var output = Path.of(System.getProperty("suite.output"));
        assertTrue(SuiteJUnit.run(Path.of(System.getProperty("suite.classes")),
                System.getProperty("suite.filter"), output, Path.of(System.getProperty("suite.deviations"))),
                () -> "Validator conformance failed. See " + output.resolve("summary.txt"));
    }
}
