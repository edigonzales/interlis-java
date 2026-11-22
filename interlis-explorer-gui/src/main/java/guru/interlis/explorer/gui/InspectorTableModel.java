package guru.interlis.explorer.gui;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.explorer.gui.InspectorRelationships.Association;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Reference;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Supplier;

final class InspectorTableModel extends AbstractTableModel {
    private record Row(String label, String summary, int depth, Long target, Supplier<List<Row>> children) {}

    private final List<Row> rows = new ArrayList<>();
    private TransferPlan plan;
    private TypeRelations types;

    void show(TransferPlan plan, TransferValue.Structured object, InspectorRelationships relationships) {
        this.plan = plan;
        types = new TypeRelations(plan.schema().semanticGraph());
        rows.clear();
        rows.addAll(attributes(object, 0, relationships.targets()));
        var associations = relationships.associations();
        if (!associations.isEmpty()) {
            rows.add(new Row("Beziehungen", associations.size() + (relationships.limited() ? " shown (limit)" : ""),
                    0, null, null));
            for (var association : associations) {
                rows.add(new Row(association.type().name().declared().last(), "", 0, null,
                        () -> associationMembers(association)));
            }
        }
        fireTableDataChanged();
    }

    void clear() {
        plan = null;
        types = null;
        rows.clear();
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int column) {
        return column == 0 ? "Attribute" : "Value";
    }

    @Override
    public String getValueAt(int row, int column) {
        var entry = rows.get(row);
        return column == 0 ? entry.label() : entry.summary();
    }

    int depth(int row) {
        return rows.get(row).depth();
    }

    boolean expandable(int row) {
        return rows.get(row).children() != null;
    }

    OptionalLong target(int row) {
        if (row < 0 || row >= rows.size()) {
            return OptionalLong.empty();
        }
        var target = rows.get(row).target();
        return target == null ? OptionalLong.empty() : OptionalLong.of(target);
    }

    List<Integer> pathTo(int row) {
        var path = new ArrayList<Integer>();
        for (int index = 0; index <= row; index++) {
            int depth = depth(index);
            if (path.size() > depth + 1) {
                path.subList(depth + 1, path.size()).clear();
            }
            if (path.size() == depth) {
                path.add(0);
            } else {
                path.set(depth, path.get(depth) + 1);
            }
        }
        return List.copyOf(path);
    }

    int reveal(List<Integer> path) {
        int row = 0;
        for (int depth = 0; depth < path.size(); depth++) {
            for (int sibling = 0; sibling < path.get(depth); sibling++) {
                row++;
                while (row < rows.size() && depth(row) > depth) {
                    row++;
                }
            }
            if (row >= rows.size() || depth(row) != depth) {
                return -1;
            }
            if (depth + 1 == path.size()) {
                return row;
            }
            if (!expanded(row)) {
                toggle(row);
            }
            row++;
        }
        return -1;
    }

    boolean expanded(int row) {
        return row + 1 < rows.size() && depth(row + 1) > depth(row);
    }

    void toggle(int row) {
        if (row < 0 || row >= rows.size() || !expandable(row)) {
            return;
        }

        int firstChild = row + 1;
        int parentDepth = depth(row);
        if (expanded(row)) {
            int end = firstChild + 1;
            while (end < rows.size() && depth(end) > parentDepth) {
                end++;
            }
            rows.subList(firstChild, end).clear();
            fireTableRowsDeleted(firstChild, end - 1);
        } else {
            var children = rows.get(row).children().get();
            rows.addAll(firstChild, children);
            fireTableRowsInserted(firstChild, firstChild + children.size() - 1);
        }
        fireTableRowsUpdated(row, row);
    }

    private List<Row> attributes(TransferValue.Structured structure, int depth, Map<Reference, Long> targets) {
        var result = new ArrayList<Row>();
        for (var slot : plan.object(structure.type()).orElseThrow().slots()) {
            if (slot instanceof AttributeSlot attribute) {
                result.add(row(attribute.name().declared().last(), attribute.effectiveType(),
                        structure.value(attribute.symbol()).orElse(null), depth, targets));
            }
        }
        return result;
    }

    private List<Row> associationMembers(Association association) {
        var result = new ArrayList<Row>();
        for (var slot : association.type().slots()) {
            var name = slot.name().declared().last();
            var value = association.data().value(slot.symbol()).orElse(null);
            switch (slot) {
                case AttributeSlot attribute -> result.add(row(name, attribute.effectiveType(),
                        value, 1, association.targets()));
                case RoleSlot role -> {
                    if (role.equals(association.embeddedOwner())) {
                        result.add(new Row(name, ExplorerFrame.objectLabel(association.source()), 1,
                                association.source().position(), null));
                    } else {
                        result.add(row(name, null, value, 1, association.targets()));
                    }
                }
            }
        }
        return result;
    }

    private List<Row> entries(TransferValue.Repeated repeated, TypeRef elementType, int depth,
                              Map<Reference, Long> targets) {
        var result = new ArrayList<Row>(repeated.values().size());
        for (int index = 0; index < repeated.values().size(); index++) {
            String label = repeated.kind() == CollectionKind.LIST ? Integer.toString(index + 1) : "";
            result.add(row(label, elementType, repeated.values().get(index), depth, targets));
        }
        return result;
    }

    private Row row(String label, TypeRef type, TransferValue value, int depth, Map<Reference, Long> targets) {
        Supplier<List<Row>> children = switch (value) {
            case TransferValue.Structured structure -> plan.object(structure.type()).orElseThrow().slots()
                    .stream().anyMatch(AttributeSlot.class::isInstance)
                    ? () -> attributes(structure, depth + 1, targets) : null;
            case TransferValue.Repeated repeated -> repeated.values().isEmpty() ? null
                    : () -> entries(repeated, collectionElement(type), depth + 1, targets);
            case null, default -> null;
        };
        var target = value instanceof Reference reference ? targets.get(reference) : null;
        return new Row(label, displayValue(type, value), depth, target, children);
    }

    private TypeRef collectionElement(TypeRef type) {
        var collection = (CollectionType) plan.type(type).known().orElseThrow();
        var element = new TypeRef(plan.snapshotId(), collection.elementType());
        return types.resolveAliases(element).known()
                .map(resolved -> new TypeRef(plan.snapshotId(), resolved.effectiveType())).orElse(element);
    }

    private String displayValue(TypeRef type, TransferValue value) {
        return switch (value) {
            case null -> "Absent";
            case TransferValue.Undefined ignored -> "Undefined";
            case TransferValue.Text text -> text.value();
            case TransferValue.BooleanValue flag -> flag.value() ? "TRUE" : "FALSE";
            case TransferValue.Enumeration enumeration -> String.join(".", enumeration.path());
            case TransferValue.NumberValue number -> number.lexeme();
            case TransferValue.Oid oid -> oid.lexeme();
            case TransferValue.ClassName name -> name.name().toString();
            case TransferValue.AttributePath path -> path.name().toString();
            case TransferValue.Binary binary -> "BINARY (" + binary.size() + " bytes)";
            case TransferValue.XmlFragment ignored -> "XML";
            case TransferValue.Structured structure -> plan.object(structure.type()).orElseThrow().name().declared().toString();
            case TransferValue.Repeated repeated -> repeated.kind() + " OF "
                    + typeName(plan.type(collectionElement(type)).known().orElseThrow())
                    + " (" + repeated.values().size() + ")";
            case TransferValue.Reference reference -> {
                var target = reference.target();
                String label = (target instanceof TransferIdentity.TransferId ? "TID " : "OID ") + target.value();
                yield reference.targetBasket().map(basket -> label + " (BID " + basket.value() + ")").orElse(label);
            }
            case TransferGeometry.Coordinate ignored -> "COORD";
            case TransferGeometry.MultiCoordinate ignored -> "MULTICOORD";
            case TransferGeometry ignored -> typeName(plan.type(type).known().orElseThrow());
        };
    }

    private String typeName(TypeDescriptor type) {
        return switch (type) {
            case CoordinateType coordinate -> coordinate.multiCoordinate() ? "MULTICOORD" : "COORD";
            case GeometryType geometry -> geometry.geometryKind().name();
            case StructureValueType structure -> structure.target()
                    .map(id -> plan.object(new SymbolRef(plan.snapshotId(), id)).orElseThrow())
                    .map(descriptor -> descriptor.name().declared().toString()).orElse("ANYSTRUCTURE");
            default -> type.kind().name();
        };
    }
}
