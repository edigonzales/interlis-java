package guru.interlis.validation.jts.internal;

import guru.interlis.validation.GeometryEngine;

public final class CurveChecks {
    private CurveChecks() {}

    public static GeometryEngine.Check check(GeometryEngine.Input input, GeometryEngine.Control control) {
        var value = new TransferGeometryAdapter(input, control).read();
        return new CurveValidity(input, control).check(value);
    }

    public static GeometryEngine.Measurement measure(GeometryEngine.Measure operation, GeometryEngine.Input input,
                                                     GeometryEngine.Control control) {
        var value = new TransferGeometryAdapter(input, control).read();
        if (value.threeDimensional()) {
            return new GeometryEngine.Measurement.Unavailable(GeometryEngine.Check.UNSUPPORTED);
        }
        var checked = new CurveValidity(input, control).check(value);
        if (checked != GeometryEngine.Check.VALID) {
            return new GeometryEngine.Measurement.Unavailable(checked);
        }
        double sum = 0;
        switch (operation) {
            case LENGTH -> {
                if (!(input.value() instanceof guru.interlis.transfer.TransferGeometry.Polyline
                        || input.value() instanceof guru.interlis.transfer.TransferGeometry.MultiPolyline)) {
                    return new GeometryEngine.Measurement.Unavailable(GeometryEngine.Check.UNSUPPORTED);
                }
                for (var part : value.lines()) {
                    for (var segment : part.segments()) {
                        control.visit().run();
                        sum += segment.curve().length();
                    }
                }
            }
            case AREA -> {
                if (!(input.value() instanceof guru.interlis.transfer.TransferGeometry.Surface
                        || input.value() instanceof guru.interlis.transfer.TransferGeometry.MultiSurface)) {
                    return new GeometryEngine.Measurement.Unavailable(GeometryEngine.Check.UNSUPPORTED);
                }
                for (var surface : value.surfaces()) {
                    control.visit().run();
                    double area = Math.abs(CurveValidity.signedArea(CurveValidity.flat(surface.shell())));
                    for (var hole : surface.holes()) {
                        control.visit().run();
                        area -= Math.abs(CurveValidity.signedArea(CurveValidity.flat(hole)));
                    }
                    sum += area;
                }
            }
        }
        return Double.isFinite(sum) && sum >= 0 ? new GeometryEngine.Measurement.Value(sum)
                : new GeometryEngine.Measurement.Unavailable(GeometryEngine.Check.NUMERICALLY_UNDETERMINED);
    }

    public static GeometryEngine.Check coverage(GeometryEngine.Source source, GeometryEngine.CoveragePurpose purpose,
                                                GeometryEngine.Control control) {
        return CurveCoverage.check(source, purpose, control);
    }
}
