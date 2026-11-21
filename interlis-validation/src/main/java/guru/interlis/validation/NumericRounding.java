package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.PrecisionOrigin;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
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
import java.util.List;
import java.util.Optional;
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
            case TransferGeometry geometry -> {
                var type = effectiveType(field, level);
                var axes = switch (type) {
                    case CoordinateType coordinate -> coordinate;
                    case GeometryType curve -> curve.vertexDomain()
                            .map(id -> GeometryParameters.coordinate(plan, basket, id)).orElse(null);
                    case null, default -> null;
                };
                if (axes != null || type instanceof GeometryType curve && curve.lineAttributes().isPresent()) {
                    geometry(geometry, axes, depth, accept);
                } else {
                    accept.accept(geometry);
                }
            }
            default -> accept.accept(scalar(value, effectiveType(field, level)));
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

    private TypeDescriptor effectiveType(ObjectValidationPlan.FieldRule field, int level) {
        if (field == null || level >= field.values().size()) {
            return null;
        }
        var rule = field.values().get(level);
        return rule.genericDomain().isPresent()
                ? rule.genericDomain().map(basket.domains()::get).flatMap(plan::domainType).orElse(null)
                : rule.type();
    }

    private TransferValue scalar(TransferValue value, TypeDescriptor type) {
        return type instanceof NumericType numeric && value instanceof NumberValue number
                ? number(number, numeric.range()) : value;
    }

    private void geometry(TransferGeometry value, CoordinateType axes, int depth, Consumer<TransferValue> accept) {
        switch (value) {
            case Coordinate point -> accept.accept(coordinate(point, axes));
            case MultiCoordinate multi -> accept.accept(new MultiCoordinate(multi.coordinates().stream()
                    .map(point -> coordinate(point, axes)).toList()));
            case Polyline line -> line(line, axes, depth, accept::accept);
            case MultiPolyline multi -> mapChildren(multi.lines(),
                    (line, result) -> line(line, axes, depth, result),
                    lines -> accept.accept(new MultiPolyline(lines)));
            case Surface surface -> surface(surface, axes, depth, accept::accept);
            case MultiSurface multi -> mapChildren(multi.surfaces(),
                    (surface, result) -> surface(surface, axes, depth, result),
                    surfaces -> accept.accept(new MultiSurface(surfaces)));
        }
    }

    private void surface(Surface surface, CoordinateType axes, int depth, Consumer<Surface> accept) {
        visit();
        ring(surface.shell(), axes, depth, shell ->
                mapChildren(surface.holes(), (hole, result) -> ring(hole, axes, depth, result),
                        holes -> accept.accept(new Surface(shell, holes))));
    }

    private void ring(Ring ring, CoordinateType axes, int depth, Consumer<Ring> accept) {
        visit();
        mapChildren(ring.lines(), (line, result) -> line(line, axes, depth, result),
                lines -> accept.accept(new Ring(lines)));
    }

    private void line(Polyline line, CoordinateType axes, int depth, Consumer<Polyline> accept) {
        visit();
        var parts = axes == null ? line.parts() : line.parts().stream()
                .map(part -> new CurvePart(coordinate(part.start(), axes), part.segments().stream().map(segment ->
                        switch (segment) {
                            case Straight straight -> (Segment) new Straight(coordinate(straight.end(), axes));
                            case Arc arc -> {
                                // The intermediate point determines the curve and retains its supplied precision.
                                visit();
                                yield new Arc(arc.support(), coordinate(arc.end(), axes), arc.radius());
                            }
                        }).toList())).toList();
        if (line.lineAttributes().isEmpty()) {
            accept.accept(new Polyline(parts));
        } else {
            value(line.lineAttributes().orElseThrow(), null, 0, depth + 1, rounded ->
                    accept.accept(new Polyline(parts, Optional.of((TransferValue.Structured) rounded))));
        }
    }

    private <T> void mapChildren(List<T> source, BiConsumer<T, Consumer<T>> transform, Consumer<List<T>> accept) {
        var values = new ArrayList<T>();
        children(source.iterator(), (original, next) -> transform.accept(original, item -> {
            values.add(item);
            next.run();
        }), () -> accept.accept(values));
    }

    private Coordinate coordinate(Coordinate coordinate, CoordinateType axes) {
        visit();
        if (axes == null) {
            return coordinate;
        }
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
