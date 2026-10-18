package guru.interlis.compiler.internal;

import guru.interlis.source.ContentFingerprint;

import guru.interlis.source.Hex;
import guru.interlis.source.Sha256;

import java.nio.charset.StandardCharsets;

/** Canonical fingerprint helpers. */
public final class Fingerprints {
    public static final String COMPILER_VERSION = "interlis-java/0.1.0-SNAPSHOT";
    public static final String GRAMMAR_VERSION =
            "geostandards-ch/interlis-antlr4@f8f97238c2540a49915f8bb97ee99bbf434b74da+antlr-4.13.2";
    public static final String SEMANTIC_PROFILE = "interlis-2.3-2.4/v1";

    private Fingerprints() {
    }

    public static String sha256(String value) {
        return Hex.encode(Sha256.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String syntax(String sourceFingerprint, String mode, String language,
                                String budget) {
        return sha256("syntax|" + sourceFingerprint + "|" + GRAMMAR_VERSION + "|" + mode
                + "|" + language + "|" + budget);
    }

    public static ContentFingerprint content(String value) {
        return ContentFingerprint.sha256(sha256(value));
    }
}
