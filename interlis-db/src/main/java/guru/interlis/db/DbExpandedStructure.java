package guru.interlis.db;

import guru.interlis.db.DbMapping.Column;
import guru.interlis.db.DbMapping.Kind;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Undefined;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** The ordered columns of an ili2db expanded structure value. */
record DbExpandedStructure(SymbolRef structure, String typeName, String typeColumn, List<Column> fields) {
    DbExpandedStructure {
        fields = List.copyOf(fields);
    }

    static DbExpandedStructure bind(TransferPlan plan, AttributeSlot outer, Map<SymbolRef, String> tableNames,
                                    BiFunction<String, Kind, String> bindColumn) {
        String path = outer.name().declared().toString();
        var object = DbStructureShape.target(plan, DbStructureShape.type(plan, outer))
                .filter(target -> target.kind() == SymbolKind.STRUCTURE && !target.modifiers().contains(Modifier.ABSTRACT))
                .orElseThrow(() -> DbMapping.unsupported("expanded structure at " + path));
        String typeName = tableNames.get(object.symbol());
        if (typeName == null) throw DbMapping.unsupported("expanded structure type at " + path);
        String prefix = path + "[0].";
        String marker = bindColumn.apply(prefix + "_type", Kind.TEXT);
        var fields = new ArrayList<Column>();
        for (var slot : object.slots()) {
            if (!(slot instanceof AttributeSlot field) || field.attribute().transientAttribute()) continue;
            var column = DbMapping.structureScalar(plan, object, field);
            String name = bindColumn.apply(prefix + field.name().declared().last(), column.kind());
            fields.add(column.withName(name));
        }
        return new DbExpandedStructure(object.symbol(), typeName, marker, fields);
    }

    List<String> physicalNames() {
        var names = new ArrayList<String>(1 + fields.size());
        names.add(typeColumn);
        for (var field : fields) names.add(field.name());
        return names;
    }

    int width() { return 1 + fields.size(); }

    Structured checked(TransferValue value, String context) {
        if (value == null || value == Undefined.INSTANCE) return null;
        if (!(value instanceof Structured item) || !structure.equals(item.type())
                || item.values().keySet().stream().anyMatch(key -> fields.stream().noneMatch(field -> field.slot().symbol().equals(key)))) {
            throw new DbException("Unsupported expanded structure at " + context);
        }
        return item;
    }
}
