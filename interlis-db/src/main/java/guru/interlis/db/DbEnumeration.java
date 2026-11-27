package guru.interlis.db;

import guru.interlis.model.AlignmentKind;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumElementSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue.Enumeration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Bidirectional mapping between an enumeration path and its numeric database value. */
record DbEnumeration(Map<String, Long> numbers, Map<Long, String> paths, Map<String, String> labels) {
    DbEnumeration {
        numbers = Map.copyOf(numbers);
        paths = Map.copyOf(paths);
        labels = Map.copyOf(labels);
    }

    static String alignmentName(AlignmentKind kind) {
        return switch (kind) {
            case HORIZONTAL -> "INTERLIS.HALIGNMENT";
            case VERTICAL -> "INTERLIS.VALIGNMENT";
        };
    }

    static List<String> alignmentCodes(AlignmentKind kind) {
        return switch (kind) {
            case HORIZONTAL -> List.of("Left", "Center", "Right");
            case VERTICAL -> List.of("Top", "Cap", "Half", "Base", "Bottom");
        };
    }

    static List<EnumerationValue> values(EnumerationType type) {
        if (type.treeValues()) return type.values();
        var parents = new HashSet<String>();
        for (var value : type.values()) {
            int separator = value.path().lastIndexOf('.');
            if (separator >= 0) parents.add(value.path().substring(0, separator));
        }
        return type.values().stream().filter(value -> !parents.contains(value.path())).toList();
    }

    static Symbol definition(TransferPlan plan, AttributeSlot slot) {
        return definition(plan, slot, plan.type(slot.declaredType()).known().orElseThrow());
    }

    static Symbol definition(TransferPlan plan, AttributeSlot slot, TypeDescriptor declaredType) {
        Symbol symbol = slot.attribute();
        var type = declaredType;
        while (type instanceof DomainReferenceType alias) {
            var domain = DbDomains.domain(plan, alias.targetDomain());
            symbol = domain;
            type = plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        }
        return symbol;
    }

    static Map<String, EnumElementSymbol> elements(TransferPlan plan, Symbol definition) {
        var elements = new HashMap<String, EnumElementSymbol>();
        var queries = plan.schema().queries();
        for (Symbol declaration = definition; declaration != null; declaration = base(plan, declaration)) {
            var ref = new SymbolRef(plan.snapshotId(), declaration.id());
            for (var member : queries.membersOf(ref).known().orElseThrow()) {
                if (plan.symbol(member).known().orElseThrow() instanceof EnumElementSymbol element) {
                    elements.putIfAbsent(String.join(".", element.path()), element);
                }
            }
        }
        return elements;
    }

    static Symbol base(TransferPlan plan, Symbol definition) {
        var base = switch (definition) {
            case AttributeSymbol attribute -> attribute.overrides();
            case DomainSymbol domain -> domain.baseDomain();
            default -> throw new IllegalArgumentException("Expected an enumeration declaration");
        };
        return base.map(id -> plan.symbol(new SymbolRef(plan.snapshotId(), id)).known().orElseThrow()).orElse(null);
    }

    static DbEnumeration itfCodes(EnumerationType type, String path) {
        if (type.treeValues()) throw DbMapping.unsupported("ITF codes for ALL OF at " + path);
        return orderedItfCodes(values(type).stream().map(EnumerationValue::path).toList());
    }

    static DbEnumeration itfCodes(AlignmentKind kind) {
        return orderedItfCodes(alignmentCodes(kind));
    }

    private static DbEnumeration orderedItfCodes(List<String> values) {
        var numbers = new HashMap<String, Long>();
        var paths = new HashMap<Long, String>();
        long code = 0;
        for (var value : values) {
            numbers.put(value, code);
            paths.put(code++, value);
        }
        return new DbEnumeration(numbers, paths, Map.of());
    }

    Enumeration read(String value, String context) {
        if (value == null) return null;
        try {
            return read(Long.parseLong(value), context);
        } catch (NumberFormatException failure) {
            throw new DbException("Invalid numeric enumeration value " + value + " at " + context, failure);
        }
    }

    Enumeration read(long value, String context) {
        String mapped = paths.get(value);
        if (mapped == null) throw new DbException("Unknown numeric enumeration value " + value + " at " + context);
        return DbTransfer.readEnumeration(mapped, context);
    }

    long number(Enumeration value, String context) {
        String path = String.join(".", value.path());
        var code = numbers.get(path);
        if (code == null) throw new DbException("Cannot encode enumeration value " + path + " at " + context);
        return code;
    }
}
