package guru.interlis.validation.jts;

import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.jts.internal.CurveChecks;

/** Explicit entry point for JVM geometry validation using LocationTech JTS. */
public final class JtsGeometryEngine implements GeometryEngine {
    private static final JtsGeometryEngine STANDARD = new JtsGeometryEngine(600_000);

    private final long maxAreaComponents;

    private JtsGeometryEngine(long maxAreaComponents) {
        this.maxAreaComponents = maxAreaComponents;
    }

    /** Returns the shared stateless engine. Each invocation owns its temporary geometry. */
    public static JtsGeometryEngine standard() {
        return STANDARD;
    }

    /**
     * Returns an immutable engine with the given maximum component visits per origin or preparation
     * pass of an AREA coverage check. Each invocation owns its temporary geometry and counters.
     *
     * @throws IllegalArgumentException if the limit is less than one
     */
    public static JtsGeometryEngine withAreaComponentLimit(long maxAreaComponents) {
        if (maxAreaComponents < 1) throw new IllegalArgumentException("AREA component limit must be positive");
        return new JtsGeometryEngine(maxAreaComponents);
    }

    @Override public Check checkValue(Input input, Control control) {
        return CurveChecks.check(input, control);
    }

    @Override public Measurement measure(Measure operation, Input input, Control control) {
        return CurveChecks.measure(operation, input, control);
    }

    @Override public Check checkCoverage(Source source, CoveragePurpose purpose, Control control) {
        return CurveChecks.coverage(source, purpose, control, maxAreaComponents);
    }
}
