package guru.interlis.db;

import guru.interlis.db.DbMapping.Column;
import guru.interlis.db.DbMapping.Kind;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Undefined;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/** The ordered columns of an ili2db expanded structure attribute. */
record DbExpandedStructure(SymbolRef structure, String typeName, Optional<CollectionKind> collection, List<Group> groups) {
    private static final BigInteger MAX_GROUPS = BigInteger.valueOf(5);

    record Group(String path, String typeColumn, List<Column> fields) {
        Group {
            fields = List.copyOf(fields);
        }

        int width() { return 1 + fields.size(); }
    }

    DbExpandedStructure {
        groups = List.copyOf(groups);
    }

    static DbExpandedStructure bind(TransferPlan plan, AttributeSlot outer, Map<SymbolRef, String> tableNames,
                                    BiFunction<String, Kind, String> bindColumn) {
        String path = outer.name().declared().toString();
        var type = DbStructureShape.type(plan, outer);
        Optional<CollectionKind> collection = Optional.empty();
        int count = 1;
        if (type instanceof CollectionType repeated) {
            if (!(outer.cardinality().upper() instanceof CardinalityUpper.Finite upper)
                    || upper.value().signum() <= 0 || upper.value().compareTo(MAX_GROUPS) > 0) {
                throw DbMapping.unsupported("expanded collection at " + path);
            }
            count = upper.value().intValueExact();
            collection = Optional.of(repeated.collectionKind());
            type = plan.type(new TypeRef(plan.snapshotId(), repeated.elementType())).known().orElseThrow();
        }
        var object = DbStructureShape.target(plan, type)
                .filter(target -> target.kind() == SymbolKind.STRUCTURE && !target.modifiers().contains(Modifier.ABSTRACT))
                .orElseThrow(() -> DbMapping.unsupported("expanded structure at " + path));
        String typeName = tableNames.get(object.symbol());
        if (typeName == null) throw DbMapping.unsupported("expanded structure type at " + path);
        var leaves = new ArrayList<Column>();
        for (var slot : object.slots()) {
            if (!(slot instanceof AttributeSlot field) || field.attribute().transientAttribute()) continue;
            leaves.add(DbMapping.structureScalar(plan, object, field));
        }
        var groups = new ArrayList<Group>(count);
        for (int index = 0; index < count; index++) {
            String position = path + "[" + index + "]";
            String marker = bindColumn.apply(position + "._type", Kind.TEXT);
            var fields = new ArrayList<Column>();
            for (var column : leaves) {
                String name = bindColumn.apply(position + "." + column.slot().name().declared().last(), column.kind());
                fields.add(column.withName(name));
            }
            groups.add(new Group(position, marker, fields));
        }
        return new DbExpandedStructure(object.symbol(), typeName, collection, groups);
    }

    List<String> physicalNames() {
        var names = new ArrayList<String>(width());
        for (var group : groups) {
            names.add(group.typeColumn());
            for (var field : group.fields()) names.add(field.name());
        }
        return names;
    }

    int width() { return groups.size() * groups.getFirst().width(); }

    List<TransferValue> items(TransferValue value, String context) {
        if (value == null || value == Undefined.INSTANCE) return List.of();
        if (collection.isEmpty()) return List.of(value);
        if (!(value instanceof Repeated repeated) || repeated.kind() != collection.orElseThrow()) {
            throw new DbException("Unexpected expanded collection at " + context);
        }
        if (repeated.values().size() > groups.size()) {
            throw new DbException("Expanded collection exceeds " + groups.size() + " stored positions at " + context);
        }
        return repeated.values();
    }

    Structured checked(TransferValue value, String context) {
        if (!(value instanceof Structured item) || !structure.equals(item.type())
                || item.values().keySet().stream().anyMatch(key -> groups.getFirst().fields().stream()
                        .noneMatch(field -> field.slot().symbol().equals(key)))) {
            throw new DbException("Unsupported expanded structure at " + context);
        }
        return item;
    }
}
