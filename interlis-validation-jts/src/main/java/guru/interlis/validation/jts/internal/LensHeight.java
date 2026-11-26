package guru.interlis.validation.jts.internal;

import guru.interlis.validation.GeometryEngine;
import guru.interlis.validation.jts.internal.CurveSegment.Point;
import guru.interlis.validation.jts.internal.ExactCircle.Rational;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Difference of the signed sagittas of the two branches at their common chord. */
final class LensHeight {
    record Assessment(boolean permitted, BigDecimal height) {}

    /** A decimal limit or the exact half-diagonal of a square with the stated model quantum. */
    record Tolerance(BigDecimal value, boolean trackWidth) {
        private static final Rational TWO = Rational.of(2);
        private static final BigDecimal HALF = new BigDecimal("0.5");

        static Optional<Tolerance> from(GeometryEngine.Input input) {
            return input.overlapTrackQuantum().map(q -> new Tolerance(q, true))
                    .or(() -> input.overlap().map(explicit -> new Tolerance(explicit, false)));
        }

        Tolerance max(Tolerance other) {
            if (trackWidth == other.trackWidth) {
                return value.compareTo(other.value) >= 0 ? this : other;
            }
            var implicit = trackWidth ? this : other;
            var explicit = trackWidth ? other : this;
            return explicit.value.signum() <= 0
                    || implicit.value.pow(2).multiply(HALF).compareTo(explicit.value.pow(2)) >= 0
                    ? implicit : explicit;
        }

        String description() {
            return trackWidth ? value.toPlainString() + " / sqrt(2)" : value.toPlainString();
        }

        private Radicals exact() {
            var amount = Rational.of(value);
            return trackWidth ? new Radicals(Rational.ZERO, amount.divide(TWO), TWO) : new Radicals(amount);
        }
    }

    private LensHeight() {}

    static Assessment compare(CurveSegment a, CurveSegment b, Point shared, Tolerance tolerance, Runnable visit) {
        visit.run();
        var limit = tolerance.exact();
        if (a instanceof CircularArc left && b instanceof CircularArc right) {
            return compareArcs(left, right, shared, limit, visit);
        }
        var arc = (CircularArc) (a instanceof CircularArc ? a : b);
        var line = a instanceof CircularArc ? b : a;
        var end = line.start().equals(shared) ? line.end() : line.start();
        var x = Rational.of(end.exactX().subtract(shared.exactX()));
        var y = Rational.of(end.exactY().subtract(shared.exactY()));
        var norm = x.square().add(y.square());
        var centreX = new Radicals(arc.definition().x().subtract(Rational.of(shared.exactX())),
                arc.definition().offsetX(), arc.definition().radicand());
        var centreY = new Radicals(arc.definition().y().subtract(Rational.of(shared.exactY())),
                arc.definition().offsetY(), arc.definition().radicand());
        var height = new Radicals(Rational.ZERO, Rational.of(side(arc, shared)), arc.definition().radiusSquared());
        var signedDistance = centreY.scale(x).minus(centreX.scale(y))
                .times(new Radicals(Rational.ZERO, Rational.ONE.divide(norm), norm));
        height = height.plus(signedDistance);
        if (height.minus(limit).terms.isEmpty() || height.plus(limit).terms.isEmpty()) {
            return new Assessment(true, limit.interval(Interval.INITIAL_DIGITS).middle());
        }
        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            visit.run();
            var assessment = compare(height.interval(digits).abs(), limit.interval(digits));
            if (assessment != null) {
                return assessment;
            }
        }
        return null;
    }

    private static Assessment compareArcs(CircularArc a, CircularArc b, Point shared, Radicals limit,
                                          Runnable visit) {
        var first = a.definition();
        var second = b.definition();
        var xA = new Radicals(first.x(), first.offsetX(), first.radicand());
        var yA = new Radicals(first.y(), first.offsetY(), first.radicand());
        var dx = new Radicals(second.x(), second.offsetX(), second.radicand()).minus(xA);
        var dy = new Radicals(second.y(), second.offsetY(), second.radicand()).minus(yA);
        var distanceSquared = dx.times(dx).plus(dy.times(dy));
        var radiusDifference = new Radicals(Rational.ZERO, Rational.of(side(a, shared)), first.radiusSquared())
                .plus(new Radicals(Rational.ZERO, Rational.of(-side(b, shared)), second.radiusSquared()));
        // (−dy, dx) lies on the radical axis. Use the known root to orient it towards the other root.
        var towardCentreX = xA.minus(new Radicals(Rational.of(shared.exactX())));
        var towardCentreY = yA.minus(new Radicals(Rational.of(shared.exactY())));
        var direction = dx.times(towardCentreY).minus(dy.times(towardCentreX));
        var plus = limit.minus(radiusDifference);
        var minus = limit.plus(radiusDifference).scale(Rational.of(-1));
        boolean plusEquality = plus.times(plus).minus(distanceSquared).terms.isEmpty();
        boolean minusEquality = minus.times(minus).minus(distanceSquared).terms.isEmpty();
        for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
            visit.run();
            int sign = direction.interval(digits).sign();
            if (sign != 1 && sign != -1) {
                continue;
            }
            if (plusEquality && plus.interval(digits).sign() == sign
                    || minusEquality && minus.interval(digits).sign() == sign) {
                return new Assessment(true, limit.interval(digits).middle());
            }
            var distance = distanceSquared.interval(digits);
            if (distance.high().signum() <= 0) {
                continue;
            }
            var normalOffset = sign == 1 ? distance.sqrt() : distance.sqrt().negate();
            var assessment = compare(radiusDifference.interval(digits).add(normalOffset).abs(), limit.interval(digits));
            if (assessment != null) {
                return assessment;
            }
        }
        return null;
    }

    private static int side(CircularArc arc, Point shared) {
        return arc.start().equals(shared) ? arc.arcSide() : -arc.arcSide();
    }

    private static Assessment compare(Interval height, Interval tolerance) {
        if (height.high().compareTo(tolerance.low()) <= 0) {
            return new Assessment(true, height.middle());
        }
        if (height.low().compareTo(tolerance.high()) > 0) {
            return new Assessment(false, height.middle());
        }
        return null;
    }

    /** A small sum of rational multiples of square roots, grouped by rational square class. */
    static final class Radicals {
        private final Map<Rational, Rational> terms = new LinkedHashMap<>();

        Radicals(Rational constant) {
            add(Rational.ONE, constant);
        }

        Radicals(Rational constant, Rational coefficient, Rational radicand) {
            this(constant);
            add(radicand, coefficient);
        }

        private void add(Rational root, Rational coefficient) {
            if (root.signum() == 0 || coefficient.signum() == 0) {
                return;
            }
            var rational = root.rationalSquareRoot();
            if (rational != null) {
                coefficient = coefficient.multiply(rational);
                root = Rational.ONE;
            }
            for (var term : terms.entrySet()) {
                var ratio = root.divide(term.getKey()).rationalSquareRoot();
                if (ratio != null) {
                    var updated = term.getValue().add(coefficient.multiply(ratio));
                    if (updated.signum() == 0) {
                        terms.remove(term.getKey());
                    } else {
                        term.setValue(updated);
                    }
                    return;
                }
            }
            terms.put(root, coefficient);
        }

        Radicals plus(Radicals other) {
            var result = new Radicals(Rational.ZERO);
            result.terms.putAll(terms);
            other.terms.forEach(result::add);
            return result;
        }

        Radicals minus(Radicals other) {
            return plus(other.scale(Rational.of(-1)));
        }

        Radicals scale(Rational factor) {
            return times(new Radicals(factor));
        }

        Radicals times(Radicals other) {
            var result = new Radicals(Rational.ZERO);
            terms.forEach((root, multiplier) -> other.terms.forEach((otherRoot, otherMultiplier) ->
                    result.add(root.multiply(otherRoot), multiplier.multiply(otherMultiplier))));
            return result;
        }

        int sign(Runnable visit) {
            if (terms.isEmpty()) {
                return 0;
            }
            for (int digits = Interval.INITIAL_DIGITS; digits <= Interval.MAX_DIGITS; digits *= 2) {
                visit.run();
                int sign = interval(digits).sign();
                if (Math.abs(sign) == 1) {
                    return sign;
                }
            }
            return Interval.OVERLAPS_ZERO;
        }

        Interval interval(int digits) {
            var result = Interval.exact(BigDecimal.ZERO, digits);
            for (var term : terms.entrySet()) {
                result = result.add(term.getValue().interval(digits).multiply(term.getKey().interval(digits).sqrt()));
            }
            return result;
        }
    }
}
