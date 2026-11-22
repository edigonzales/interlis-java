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
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class InspectorTableModel extends AbstractTableModel {
    private record Row(String label, String summary, TypeRef type, TransferValue value, int depth) {}

    private final List<Row> rows = new ArrayList<>();
    private TransferPlan plan;
    private TypeRelations types;

    void show(TransferPlan plan, TransferValue.Structured object) {
        this.plan = plan;
        types = new TypeRelations(plan.schema().semanticGraph());
        rows.clear();
        rows.addAll(attributes(object, 0));
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
        return switch (rows.get(row).value()) {
            case TransferValue.Structured structure -> plan.object(structure.type()).orElseThrow().slots()
                    .stream().anyMatch(AttributeSlot.class::isInstance);
            case TransferValue.Repeated repeated -> !repeated.values().isEmpty();
            case null, default -> false;
        };
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
            var children = children(rows.get(row));
            rows.addAll(firstChild, children);
            fireTableRowsInserted(firstChild, firstChild + children.size() - 1);
        }
        fireTableRowsUpdated(row, row);
    }

    private List<Row> attributes(TransferValue.Structured structure, int depth) {
        var result = new ArrayList<Row>();
        for (var slot : plan.object(structure.type()).orElseThrow().slots()) {
            if (slot instanceof AttributeSlot attribute) {
                result.add(row(attribute.name().declared().last(), attribute.effectiveType(),
                        structure.value(attribute.symbol()).orElse(null), depth));
            }
        }
        return result;
    }

    private List<Row> children(Row parent) {
        int depth = parent.depth() + 1;
        if (parent.value() instanceof TransferValue.Structured structure) {
            return attributes(structure, depth);
        }
        var repeated = (TransferValue.Repeated) parent.value();
        var elementType = collectionElement(parent.type());
        var result = new ArrayList<Row>(repeated.values().size());
        for (int index = 0; index < repeated.values().size(); index++) {
            String label = repeated.kind() == CollectionKind.LIST ? Integer.toString(index + 1) : "";
            result.add(row(label, elementType, repeated.values().get(index), depth));
        }
        return result;
    }

    private Row row(String label, TypeRef type, TransferValue value, int depth) {
        return new Row(label, displayValue(type, value), type, value, depth);
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
