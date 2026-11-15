package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.PrecisionOrigin;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.NumberValue;

import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builds the validator's numeric value view before local and dataset rules run. */
final class NumericRounding {
    private final ValidationPlan plan;
    private final ValidationSession.BasketContext basket;
    private final ValidationOptions options;
    private final CancellationToken cancellation;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private long visits;

    private NumericRounding(ValidationPlan plan, ValidationSession.BasketContext basket,
                            ValidationOptions options, CancellationToken cancellation) {
        this.plan = plan;
        this.basket = basket;
        this.options = options;
        this.cancellation = cancellation;
    }

    static ObjectEvent prepare(ObjectEvent event, ValidationPlan plan, ValidationSession.BasketContext basket,
                               ValidationOptions options, CancellationToken cancellation) {
        if (!options.numericRounding()) {
            return event;
        }
        var rounding = new NumericRounding(plan, basket, options, cancellation);
        var object = event.object();
        var result = new ArrayList<TransferValue>(1);
        rounding.value(object.data(), null, 0, 0, result::add);
        while (!rounding.pending.isEmpty()) {
            rounding.pending.pop().run();
        }
        var data = (TransferValue.Structured) result.getFirst();
        return data == object.data() ? event
                : new ObjectEvent(new TransferObject(object.identity(), data), event.operation(), event.consistency());
    }

    private void value(TransferValue value, ObjectValidationPlan.FieldRule field, int level, int depth,
                       Consumer<TransferValue> accept) {
        visit();
        if (depth > options.maxNestedDepth() + 1L) {
            throw new ValidationLimitException("Maximum value depth exceeded");
        }
        switch (value) {
            case TransferValue.Structured structure -> {
                var type = plan.object(structure.type());
                var fields = new LinkedHashMap<SymbolRef, TransferValue>();
                var changed = new boolean[1];
                children(structure.values().entrySet().iterator(), (entry, next) -> {
                    var rule = type == null ? null : plan.field(type, entry.getKey().symbol());
                    value(entry.getValue(), rule, 0, depth + 1, item -> {
                        fields.put(entry.getKey(), item);
                        changed[0] |= item != entry.getValue();
                        next.run();
                    });
                }, () -> accept.accept(changed[0] ? new TransferValue.Structured(structure.type(), fields) : structure));
            }
            case TransferValue.Repeated repeated -> {
                var items = new ArrayList<TransferValue>();
                var changed = new boolean[1];
                children(repeated.values().iterator(), (original, next) ->
                    value(original, field, level + 1, depth + 1, item -> {
                        items.add(item);
                        changed[0] |= item != original;
                        next.run();
                    }), () -> accept.accept(changed[0] ? new TransferValue.Repeated(repeated.kind(), items) : repeated));
            }
            default -> accept.accept(scalar(value, field, level));
        }
    }

    private <T> void children(Iterator<T> entries, BiConsumer<T, Runnable> transform, Runnable finish) {
        // Schedule one child at a time so nested transfer values do not grow the Java call stack.
        pending.push(() -> {
            if (entries.hasNext()) {
                transform.accept(entries.next(), () -> children(entries, transform, finish));
            } else {
                pending.push(finish);
            }
        });
    }

    private TransferValue scalar(TransferValue value, ObjectValidationPlan.FieldRule field, int level) {
        if (field == null || level >= field.values().size()) {
            return value;
        }
        var rule = field.values().get(level);
        var type = rule.genericDomain().isPresent()
                ? rule.genericDomain().map(basket.domains()::get).flatMap(plan::domainType).orElse(null)
                : rule.type();
        if (type instanceof NumericType numeric && value instanceof NumberValue number) {
            return number(number, numeric.range());
        }
        if (value instanceof TransferGeometry geometry) {
            var vertices = switch (type) {
                case CoordinateType coordinate -> coordinate;
                case GeometryType curve -> curve.vertexDomain()
                        .map(id -> GeometryParameters.coordinate(plan, basket, id)).orElse(null);
                case null, default -> null;
            };
            if (vertices != null) {
                return geometry(geometry, vertices);
            }
        }
        return value;
    }

    private TransferGeometry geometry(TransferGeometry value, CoordinateType axes) {
        return switch (value) {
            case Coordinate position -> coordinate(position, axes);
            case MultiCoordinate multi -> new MultiCoordinate(multi.coordinates().stream()
                    .map(position -> coordinate(position, axes)).toList());
            case Polyline line -> line(line, axes);
            case MultiPolyline multi -> new MultiPolyline(multi.lines().stream().map(line -> line(line, axes)).toList());
            case Surface surface -> surface(surface, axes);
            case MultiSurface multi -> new MultiSurface(multi.surfaces().stream().map(s -> surface(s, axes)).toList());
        };
    }

    private Surface surface(Surface surface, CoordinateType axes) {
        visit();
        return new Surface(ring(surface.shell(), axes), surface.holes().stream().map(hole -> ring(hole, axes)).toList());
    }

    private Ring ring(Ring ring, CoordinateType axes) {
        visit();
        return new Ring(ring.lines().stream().map(line -> line(line, axes)).toList());
    }

    private Polyline line(Polyline line, CoordinateType axes) {
        visit();
        return new Polyline(line.parts().stream().map(part -> new CurvePart(coordinate(part.start(), axes),
                part.segments().stream().map(segment -> switch (segment) {
                    case Straight straight -> (Segment) new Straight(coordinate(straight.end(), axes));
                    case Arc arc -> {
                        // The intermediate point determines the curve and retains its supplied precision.
                        visit();
                        yield new Arc(arc.support(), coordinate(arc.end(), axes), arc.radius());
                    }
                }).toList())).toList(), line.lineAttributes());
    }

    private Coordinate coordinate(Coordinate coordinate, CoordinateType axes) {
        visit();
        var ordinates = new ArrayList<>(coordinate.ordinates());
        for (int i = 0; i < Math.min(axes.dimensions(), ordinates.size()); i++) {
            ordinates.set(i, number(ordinates.get(i), axes.axes().get(i)));
        }
        return new Coordinate(ordinates);
    }

    private static NumberValue number(NumberValue value, NumericRange range) {
        var lower = range.minimum().orElse(null);
        if (lower == null || range.maximum().isEmpty() || lower.precision().origin() != PrecisionOrigin.LEXICAL) {
            return value;
        }
        var exact = value.exactValue();
        ScalarRules.requireNumericBudget(exact);
        int places = lower.precision().fractionalDigits();
        if (exact.scale() <= places) {
            return value;
        }
        var rounded = exact.setScale(places, exact.signum() < 0 ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP);
        return rounded.compareTo(exact) == 0 ? value : new NumberValue(rounded.toString(), rounded);
    }

    private void visit() {
        cancellation.throwIfCancellationRequested();
        if (++visits > options.maxValuesPerObject()) {
            throw new ValidationLimitException("Numeric rounding traversal budget exceeded");
        }
    }
}
