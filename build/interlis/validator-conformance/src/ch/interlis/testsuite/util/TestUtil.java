package ch.interlis.testsuite.util;

import guru.interlis.conformance.SuiteValidation;
import guru.interlis.execution.CancellationToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** The only TestUtil compiled alongside the original suite sources. */
public final class TestUtil {
    private TestUtil() { }

    public static boolean runJob(String vendor, String xtf) {
        if (!"interlis-java".equals(vendor)) {
            throw new IllegalArgumentException("Unexpected suite vendor: " + vendor);
        }
        Path input = Path.of(xtf).toAbsolutePath().normalize();
        try {
            return SuiteValidation.validate(input, Path.of("../data/models").toRealPath(), CancellationToken.none());
        } catch (IOException e) {
            throw new UncheckedIOException("Suite input " + input, e);
        }
    }
}
