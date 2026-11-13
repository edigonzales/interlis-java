package guru.interlis.validation.jts.internal;

import guru.interlis.validation.GeometryEngine;

public final class CurveChecks {
    private CurveChecks() {}

    public static GeometryEngine.Check check(GeometryEngine.Input input, GeometryEngine.Control control) {
        var value = new TransferGeometryAdapter(input, control).read();
        return new CurveValidity(input, control).check(value);
    }

    public static GeometryEngine.Check coverage(GeometryEngine.Source source, GeometryEngine.CoveragePurpose purpose,
                                                GeometryEngine.Control control) {
        return CurveCoverage.check(source, purpose, control);
    }
}
