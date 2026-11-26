package guru.interlis.validation.jts.internal;

/** A binary64 enclosure for the inexpensive sign filters. An inconclusive sign triggers exact arithmetic. */
record DoubleInterval(double low, double high) {
    static DoubleInterval around(double nearest) {
        return Double.isFinite(nearest) ? rounded(nearest, nearest) : unknown();
    }

    private static DoubleInterval rounded(double low, double high) {
        return new DoubleInterval(Math.nextDown(low), Math.nextUp(high));
    }

    private static DoubleInterval unknown() {
        return new DoubleInterval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    DoubleInterval add(DoubleInterval other) {
        return rounded(low + other.low, high + other.high);
    }

    DoubleInterval subtract(DoubleInterval other) {
        return rounded(low - other.high, high - other.low);
    }

    DoubleInterval multiply(DoubleInterval other) {
        double a = low * other.low;
        double b = low * other.high;
        double c = high * other.low;
        double d = high * other.high;
        return rounded(Math.min(Math.min(a, b), Math.min(c, d)), Math.max(Math.max(a, b), Math.max(c, d)));
    }

    DoubleInterval divide(DoubleInterval other) {
        if (!(other.low > 0 || other.high < 0)) {
            return unknown();
        }
        return multiply(rounded(1 / other.high, 1 / other.low));
    }

    DoubleInterval square() {
        var absolute = low > 0 ? this : high < 0 ? new DoubleInterval(-high, -low)
                : new DoubleInterval(0, Math.max(-low, high));
        return absolute.multiply(absolute);
    }

    DoubleInterval sqrt() {
        return rounded(Math.sqrt(Math.max(0, low)), Math.sqrt(high));
    }

    int sign() {
        // Both bounds must be ordered and non-NaN (overflow can make a filter inconclusive).
        if (low <= high) {
            if (low > 0) {
                return 1;
            }
            if (high < 0) {
                return -1;
            }
        }
        return Interval.OVERLAPS_ZERO;
    }
}
