package guru.interlis.validation.jts;

import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.jts.internal.CurveChecks;

/** Explicit entry point for JVM geometry validation using LocationTech JTS. */
public final class JtsGeometryEngine implements GeometryEngine {
    private static final JtsGeometryEngine STANDARD = new JtsGeometryEngine();

    private JtsGeometryEngine() {}

    /** Returns the shared stateless engine. Each invocation owns its temporary geometry. */
    public static JtsGeometryEngine standard() {
        return STANDARD;
    }

    @Override public Check checkValue(Input input, Control control) {
        return CurveChecks.check(input, control);
    }

    @Override public Measurement measure(Measure operation, Input input, Control control) {
        return new Measurement.Unavailable(Check.UNSUPPORTED);
    }

    @Override public Check checkCoverage(Source source, CoveragePurpose purpose, Control control) {
        return Check.UNSUPPORTED;
    }
}
