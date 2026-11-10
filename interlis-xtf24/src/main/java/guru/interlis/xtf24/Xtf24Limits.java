package guru.interlis.xtf24;

/**
 * Immutable resource limits for an XTF 2.4 stream. All limits are positive.
 *
 * @param maxDepth maximum nested XML element depth, including the root
 * @param maxScalarCodeUnits maximum UTF-16 code units in a scalar, encoded binary, or XML fragment including namespace context
 * @param maxIdentifierCodeUnits maximum UTF-16 code units in an identifier or technical name
 * @param maxObjectEvents maximum XML events processed for one object or one header/protocol operation
 * @param maxObjectCodeUnits maximum aggregate UTF-16 code units in XML names, namespaces, text and attributes per object or header/protocol operation
 * @param maxParserAdvanceBytes maximum input bytes read by one parser operation
 * @param maxIdentities maximum distinct basket and object identities per transfer
 */
public record Xtf24Limits(int maxDepth, int maxScalarCodeUnits, int maxIdentifierCodeUnits,
                          long maxObjectEvents, long maxObjectCodeUnits, long maxParserAdvanceBytes,
                          long maxIdentities) {
    /** Creates limits for a stream. @throws IllegalArgumentException if a limit is nonpositive */
    public Xtf24Limits {
        if (maxDepth <= 0 || maxScalarCodeUnits <= 0 || maxIdentifierCodeUnits <= 0
                || maxObjectEvents <= 0 || maxObjectCodeUnits <= 0 || maxParserAdvanceBytes <= 0
                || maxIdentities <= 0) {
            throw new IllegalArgumentException("XTF 2.4 resource limits must be positive");
        }
    }

    /** Returns the adapter's default limits. */
    public static Xtf24Limits defaults() {
        return new Xtf24Limits(128, 1_048_576, 4_096, 1_000_000, 16_777_216, 8_388_608, 1_000_000);
    }
}
