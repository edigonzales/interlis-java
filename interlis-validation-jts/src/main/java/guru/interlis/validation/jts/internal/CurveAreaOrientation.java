package guru.interlis.validation.jts.internal;

import guru.interlis.validation.jts.internal.ExactCircle.Rational;
import guru.interlis.validation.jts.internal.TransferGeometryAdapter.Segment;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

/** Sign of the Green integral, with exact chord sums and outward circular-segment bounds. */
final class CurveAreaOrientation {
    private static final Rational FOUR = Rational.of(4);
    private static final Rational HALF = Rational.ONE.divide(Rational.of(2));
    private static final BigDecimal DECIMAL_HALF = new BigDecimal("0.5");

    private record ArcTerm(Rational radiusSquared, Rational squaredHalfChordRatio, boolean major) {
        static ArcTerm of(CircularArc arc) {
            var circle = arc.definition();
            var ratio = ExactCircle.chordSquared(arc.start(), arc.end()).divide(circle.radiusSquared().multiply(FOUR));
            boolean major = circle.centreSide(arc.start(), arc.end()) == arc.arcSide();
            return new ArcTerm(circle.radiusSquared(), ratio, major);
        }

        Interval integral(int digits, Interval pi, Runnable visit) {
            var ratio = squaredHalfChordRatio;
            var complement = Rational.ONE.subtract(ratio);
            var sine = ratio.multiply(complement).interval(digits).sqrt().multiply(number(2, digits));
            boolean nearZero = ratio.compareTo(HALF) <= 0;
            var argument = nearZero ? ratio.divide(complement) : complement.divide(ratio);
            var angle = atan(argument.interval(digits).sqrt(), visit).multiply(number(2, digits));
            if (!nearZero) {
                angle = pi.subtract(angle);
            }
            var circularSegment = angle.subtract(sine);
            if (major) {
                circularSegment = pi.multiply(number(2, digits)).subtract(circularSegment);
            }
            return radiusSquared.interval(digits).multiply(circularSegment);
        }
    }

    private CurveAreaOrientation() {}

    static int sign(List<Segment> boundary, Runnable visit) {
        if (boundary.stream().anyMatch(segment -> !segment.curve().start().isExact() || !segment.curve().end().isExact())) {
            return enclosedSign(boundary, visit);
        }
        var chordArea = BigDecimal.ZERO;
        var terms = new LinkedHashMap<ArcTerm, Integer>();
        for (var segment : boundary) {
            visit.run();
            var curve = segment.curve();
            var start = curve.start();
            var end = curve.end();
            chordArea = chordArea.add(start.exactX().multiply(end.exactY())
                    .subtract(start.exactY().multiply(end.exactX())));
            if (curve instanceof CircularArc arc) {
                var term = ArcTerm.of(arc);
                terms.merge(term, -arc.arcSide(), Integer::sum);
            }
        }
        terms.values().removeIf(count -> count == 0);
        if (terms.isEmpty()) {
            return chordArea.signum();
        }
        if (chordArea.signum() >= 0 && terms.values().stream().allMatch(count -> count > 0)) {
            return 1;
        }
        if (chordArea.signum() <= 0 && terms.values().stream().allMatch(count -> count < 0)) {
            return -1;
        }
        boolean needsPi = terms.keySet().stream().anyMatch(term ->
                term.major() || term.squaredHalfChordRatio().compareTo(HALF) > 0);
        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            visit.run();
            var integral = Interval.exact(chordArea, digits);
            var pi = needsPi ? pi(digits, visit) : null;
            for (var entry : terms.entrySet()) {
                visit.run();
                integral = integral.add(entry.getKey().integral(digits, pi, visit)
                        .multiply(number(entry.getValue(), digits)));
            }
            if (Math.abs(integral.sign()) == 1) {
                return integral.sign();
            }
        }
        return 0;
    }

    private static int enclosedSign(List<Segment> boundary, Runnable visit) {
        boolean curved = boundary.stream().anyMatch(segment -> segment.curve() instanceof CircularArc);
        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            visit.run();
            var integral = number(0, digits);
            var pi = curved ? pi(digits, visit) : null;
            for (var segment : boundary) {
                visit.run();
                var curve = segment.curve();
                var sx = curve.start().xInterval(digits);
                var sy = curve.start().yInterval(digits);
                var ex = curve.end().xInterval(digits);
                var ey = curve.end().yInterval(digits);
                integral = integral.add(sx.multiply(ey).subtract(sy.multiply(ex)));
                if (curve instanceof CircularArc arc) {
                    integral = integral.add(enclosedArc(arc, ex.subtract(sx), ey.subtract(sy), sx, sy, pi, visit));
                }
            }
            if (Math.abs(integral.sign()) == 1) {
                return integral.sign();
            }
        }
        return 0;
    }

    private static Interval enclosedArc(CircularArc arc, Interval dx, Interval dy, Interval sx, Interval sy,
                                        Interval pi, Runnable visit) {
        int digits = dx.digits();
        var definition = arc.definition();
        var radiusSquared = definition.radiusSquared().interval(digits);
        var q = dx.square().add(dy.square()).divide(radiusSquared.multiply(number(4, digits)));
        // Both source and constructed endpoints lie on this circle.
        q = new Interval(q.low().max(BigDecimal.ZERO), q.high().min(BigDecimal.ONE), digits);
        var centreSide = dx.multiply(definition.centreY(digits).subtract(sy))
                .subtract(dy.multiply(definition.centreX(digits).subtract(sx))).sign();
        var minor = minorSegment(q, pi, visit);
        Interval chosen = minor;
        if (centreSide == arc.arcSide()) {
            chosen = pi.multiply(number(2, digits)).subtract(minor);
        } else if (centreSide == Interval.OVERLAPS_ZERO) {
            var major = pi.multiply(number(2, digits)).subtract(minor);
            chosen = new Interval(minor.low().min(major.low()), minor.high().max(major.high()), digits);
        }
        return radiusSquared.multiply(arc.arcSide() == 1 ? chosen.negate() : chosen);
    }

    private static Interval minorSegment(Interval q, Interval pi, Runnable visit) {
        int digits = q.digits();
        if (q.low().compareTo(DECIMAL_HALF) < 0 && q.high().compareTo(DECIMAL_HALF) > 0) {
            var low = minorSegment(new Interval(q.low(), DECIMAL_HALF, digits), pi, visit);
            var high = minorSegment(new Interval(DECIMAL_HALF, q.high(), digits), pi, visit);
            return new Interval(low.low().min(high.low()), low.high().max(high.high()), digits);
        }
        var complement = number(1, digits).subtract(q);
        boolean nearZero = q.high().compareTo(DECIMAL_HALF) <= 0;
        var argument = nearZero ? q.divide(complement) : complement.divide(q);
        var angle = atan(argument.sqrt(), visit).multiply(number(2, digits));
        if (!nearZero) {
            angle = pi.subtract(angle);
        }
        var sine = q.multiply(complement).sqrt().multiply(number(2, digits));
        return angle.subtract(sine);
    }

    private static Interval pi(int digits, Runnable visit) {
        // Machin's formula bounds pi using only rational inputs.
        return atan(Interval.ratio(BigDecimal.ONE, BigDecimal.valueOf(5), digits), visit)
                .multiply(number(16, digits))
                .subtract(atan(Interval.ratio(BigDecimal.ONE, BigDecimal.valueOf(239), digits), visit)
                        .multiply(number(4, digits)));
    }

    private static Interval atan(Interval argument, Runnable visit) {
        int digits = argument.digits();
        if (argument.high().signum() == 0) {
            return argument;
        }
        var one = number(1, digits);
        var x = argument.divide(one.add(one.add(argument.square()).sqrt()));
        var factor = x.square();
        var term = x;
        var sum = x;
        var limit = x.high().scaleByPowerOfTen(-digits);
        for (int i = 3; ; i += 2) {
            visit.run();
            term = term.multiply(factor).negate();
            var increment = term.divide(number(i, digits));
            if (increment.abs().high().compareTo(limit) <= 0) {
                // For the reduced argument (at most sqrt(2)−1), the first omitted term bounds the remainder.
                var remainder = new Interval(increment.low().min(BigDecimal.ZERO),
                        increment.high().max(BigDecimal.ZERO), digits);
                return sum.add(remainder).multiply(number(2, digits));
            }
            sum = sum.add(increment);
        }
    }

    private static Interval number(int value, int digits) {
        return Interval.exact(BigDecimal.valueOf(value), digits);
    }
}
