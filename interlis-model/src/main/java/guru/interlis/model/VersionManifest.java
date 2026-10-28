package guru.interlis.model;

import guru.interlis.diagnostics.DiagnosticCatalog;

import java.util.Objects;

/**
 * Separately versioned public SDK, protocol and format surfaces.
 *
 * <p>Binary compatibility of the Java API, the diagnostic catalogue, the
 * bridge protocol, the language profiles and the serialized presentation
 * contracts may move independently. Callers must not infer one from another.</p>
 *
 * @param toolVersion released tool/package version
 * @param apiVersion Java SDK contract version
 * @param diagnosticCatalogVersion diagnostic message catalogue version
 * @param protocolName bridge protocol name
 * @param protocolVersion bridge protocol version
 * @param languageProfiles supported INTERLIS profiles
 * @param formatVersion source-formatting contract version
 * @param diagramSchemaVersion diagram document schema version
 */
public record VersionManifest(String toolVersion, String apiVersion,
                              String diagnosticCatalogVersion, String protocolName,
                              String protocolVersion, String languageProfiles,
                              String formatVersion, String diagramSchemaVersion) {
    /** Current tool/package version. */
    public static final String TOOL_VERSION = "0.2.0-SNAPSHOT";
    /** Current Java SDK contract version. */
    public static final String API_VERSION = "0.2";
    /** Current bridge protocol name. */
    public static final String PROTOCOL_NAME = "interlis.compiler";
    /** Current bridge protocol version. */
    public static final String PROTOCOL_VERSION = "1.0";
    /** Supported language profiles. */
    public static final String LANGUAGE_PROFILES = "2.3, 2.4";
    /** Current source-formatting contract version. */
    public static final String FORMAT_VERSION = "1.0";
    /** Current diagram document schema version. */
    public static final String DIAGRAM_SCHEMA_VERSION = "1.0";

    /** Creates a validated instance. */
    public VersionManifest {
        Objects.requireNonNull(toolVersion, "toolVersion");
        Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(diagnosticCatalogVersion, "diagnosticCatalogVersion");
        Objects.requireNonNull(protocolName, "protocolName");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(languageProfiles, "languageProfiles");
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(diagramSchemaVersion, "diagramSchemaVersion");
        if (toolVersion.isBlank() || apiVersion.isBlank() || diagnosticCatalogVersion.isBlank()
                || protocolName.isBlank() || protocolVersion.isBlank()
                || languageProfiles.isBlank() || formatVersion.isBlank()
                || diagramSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("version fields must not be blank");
        }
    }

    /**
     * Returns the manifest compiled into this artifact.
     *
     * @return current manifest
     */
    public static VersionManifest current() {
        return new VersionManifest(TOOL_VERSION, API_VERSION, DiagnosticCatalog.VERSION,
                PROTOCOL_NAME, PROTOCOL_VERSION, LANGUAGE_PROFILES, FORMAT_VERSION,
                DIAGRAM_SCHEMA_VERSION);
    }

    /**
     * Returns the protocol identifier in {@code name/version} form.
     *
     * @return protocol identifier
     */
    public String protocolIdentifier() {
        return protocolName + "/" + protocolVersion;
    }
}
