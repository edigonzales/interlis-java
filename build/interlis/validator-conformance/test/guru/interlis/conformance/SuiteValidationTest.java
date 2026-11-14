package guru.interlis.conformance;

import ch.interlis.testsuite.util.TestUtil;
import guru.interlis.execution.CancellationToken;
import guru.interlis.xtf23.Xtf23Exception;
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
    void validDataDoesNotRequireUnrelatedModelsOrUnusedRules() throws IOException {
        assertTrue(validate(transfer("Item", "abc")));
    }

    @Test
    void rejectsActualDataViolation() throws IOException {
        assertFalse(validate(transfer("Item", "abcd")));
    }

    @Test
    void rejectsMalformedXmlAndSeparatesIssuerMismatch() throws IOException {
        String valid = transfer("Item", "abc");
        assertFalse(validate(valid.replace("xmlns=\"", "xmlns=")));
        var failure = assertThrows(Xtf23Exception.class,
                () -> validate(valid.replace("https://example.org", "http://example.org")));
        assertTrue(failure.getMessage().startsWith("XTF issuer conflicts"));
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
