package guru.interlis.compiler.internal.antlr;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionScannerTest {
    @Test
    void detectsUnsupportedVersion() {
        var source = new SourceText(new SourceId("memory:/v.ili"),
                new SourceRevision(1), "INTERLIS 1.0; MODEL M AT \"x\" VERSION \"1\" = END M.");
        var detection = new VersionHeaderScanner(source).detect();
        assertEquals("UNSUPPORTED", detection.state().name());
    }
}
