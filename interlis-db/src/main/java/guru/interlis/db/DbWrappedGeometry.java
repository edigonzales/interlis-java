package guru.interlis.db;

import guru.interlis.db.DbMapping.Kind;
import guru.interlis.db.Ili2dbLayout.AttributeTransformation;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.MultiCoordinate;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Undefined;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static guru.interlis.db.DbStructureShape.bagElement;
import static guru.interlis.db.DbStructureShape.target;
import static guru.interlis.db.DbStructureShape.type;

/** A geometry collection stored as one SQL geometry and transferred in structure values. */
record DbWrappedGeometry(AttributeTransformation transformation, SymbolRef structure, AttributeSlot bag,
                         SymbolRef element, AttributeSlot component) {
    static Map<SymbolRef, DbWrappedGeometry> discover(TransferPlan plan, List<ObjectDescriptor> objects,
                                                     Function<AttributeSlot, AttributeTransformation> transformation) {
        var result = new HashMap<SymbolRef, DbWrappedGeometry>();
        for (var object : objects) {
            for (var field : DbMapping.attributes(object)) {
                var recipe = transformation.apply(field);
                if (recipe != AttributeTransformation.MULTIPOINT) continue;
                var binding = describe(plan, field, recipe)
                        .orElseThrow(() -> DbMapping.unsupported("coalesced geometry at " + field.name().declared()));
                result.put(field.symbol(), binding);
            }
        }
        return result;
    }

    private static Optional<DbWrappedGeometry> describe(TransferPlan plan, AttributeSlot outer,
                                                        AttributeTransformation transformation) {
        if (!CardinalityUpper.finite(1).equals(outer.cardinality().upper())) return Optional.empty();
        var structure = target(plan, type(plan, outer)).orElse(null);
        if (structure == null || !"MultiPoint".equals(DbMapping.metaText(
                plan.symbol(structure.symbol()).known().orElseThrow(), "ili2db.mapping"))) return Optional.empty();
        var bag = singleField(structure);
        var element = bag == null ? null : bagElement(plan, bag).orElse(null);
        var component = element == null ? null : singleField(element);
        if (component == null || !(type(plan, component) instanceof CoordinateType coordinate)
                || coordinate.multiCoordinate() || coordinate.dimensions() != 2) return Optional.empty();
        return Optional.of(new DbWrappedGeometry(transformation, structure.symbol(), bag, element.symbol(), component));
    }

    private static AttributeSlot singleField(ObjectDescriptor structure) {
        return !structure.modifiers().contains(Modifier.ABSTRACT) && structure.slots().size() == 1
                && structure.slots().getFirst() instanceof AttributeSlot field && !field.attribute().transientAttribute()
                ? field : null;
    }

    Kind kind() { return Kind.MULTIPOINT; }

    TransferValue wrap(TransferGeometry stored) {
        if (stored == null) return null;
        List<TransferValue> values = ((MultiCoordinate) stored).coordinates().stream()
                .<TransferValue>map(part -> new Structured(element, Map.of(component.symbol(), part))).toList();
        return new Structured(structure, values.isEmpty() ? Map.of()
                : Map.of(bag.symbol(), new Repeated(CollectionKind.BAG, values)));
    }

    TransferGeometry unwrap(TransferValue value, String context) {
        if (value == null || value == Undefined.INSTANCE) return null;
        var contents = field(value, structure, bag, context);
        if (contents == null || contents == Undefined.INSTANCE) return null;
        if (!(contents instanceof Repeated parts) || parts.kind() != CollectionKind.BAG) throw invalid(context);
        var points = new ArrayList<Coordinate>();
        for (var item : parts.values()) {
            if (!(field(item, element, component, context) instanceof Coordinate point)) throw invalid(context);
            points.add(point);
        }
        return points.isEmpty() ? null : new MultiCoordinate(points);
    }

    private static TransferValue field(TransferValue value, SymbolRef type, AttributeSlot slot, String context) {
        if (!(value instanceof Structured structure) || !structure.type().equals(type)
                || structure.values().keySet().stream().anyMatch(field -> !slot.symbol().equals(field))) {
            throw invalid(context);
        }
        return structure.values().get(slot.symbol());
    }

    private static DbException invalid(String context) {
        return new DbException("Cannot encode coalesced geometry at " + context);
    }
}
