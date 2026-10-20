package guru.interlis.model;

import guru.interlis.diagnostics.DiagnosticCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionManifestTest {
    @Test
    void versionsAreSeparateAndConsistent() {
        VersionManifest manifest = VersionManifest.current();
        assertEquals(VersionManifest.TOOL_VERSION, manifest.toolVersion());
        assertEquals(VersionManifest.API_VERSION, manifest.apiVersion());
        assertEquals(DiagnosticCatalog.VERSION, manifest.diagnosticCatalogVersion());
        assertEquals("interlis.compiler/1.0", manifest.protocolIdentifier());
        assertEquals("2.3, 2.4", manifest.languageProfiles());
        assertEquals("1.0", manifest.formatVersion());
        assertEquals("1.0", manifest.diagramSchemaVersion());
    }

    @Test
    void blankVersionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new VersionManifest(
                " ", "0.1", "d", "p", "1", "2.3", "1", "1"));
    }
}
