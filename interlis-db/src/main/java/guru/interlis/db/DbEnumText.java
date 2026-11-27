package guru.interlis.db;

import guru.interlis.model.AlignmentType;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.BooleanValue;
import guru.interlis.transfer.TransferValue.Enumeration;

import java.util.HashMap;
import java.util.Map;

/** Companion label column for a scalar enumeration or Boolean. */
record DbEnumText(String column, Map<String, String> labels, boolean beautify) {
    DbEnumText {
        labels = Map.copyOf(labels);
    }

    static DbEnumText describe(TransferPlan plan, DbMapping.Column column, boolean beautify) {
        if (column.kind() == DbMapping.Kind.ENUMERATION) {
            if (column.enumeration().filter(value -> !value.labels().isEmpty()).isPresent()) {
                return new DbEnumText(column.name() + "_txt", column.enumeration().orElseThrow().labels(), beautify);
            }
            if (!(column.effectiveType().orElse(null) instanceof AlignmentType)) {
                return new DbEnumText(column.name() + "_txt", modelLabels(plan, (AttributeSlot) column.slot()), beautify);
            }
        }
        return new DbEnumText(column.name() + "_txt", Map.of(), beautify);
    }

    static Map<String, String> modelLabels(TransferPlan plan, AttributeSlot slot) {
        var labels = new HashMap<String, String>();
        var definition = DbEnumeration.definition(plan, slot);
        String language = language(plan, definition);
        DbEnumeration.elements(plan, definition).forEach((path, element) -> {
            String label = displayName(element, language);
            if (label != null && !label.isEmpty()) labels.put(path, label);
        });
        return Map.copyOf(labels);
    }

    static String language(TransferPlan plan, Symbol symbol) {
        while (!(symbol instanceof ModelSymbol)) {
            symbol = plan.symbol(new SymbolRef(plan.snapshotId(), symbol.owner().orElseThrow())).known().orElseThrow();
        }
        return ((ModelSymbol) symbol).options().language().map(option -> option.value()).orElse(null);
    }

    static String displayName(Symbol symbol, String language) {
        String label = language == null ? null : DbMapping.metaText(symbol, "ili2db.dispName_" + language);
        return label == null ? DbMapping.metaText(symbol, "ili2db.dispName") : label;
    }

    String label(TransferValue value) {
        return label(value, labels);
    }

    String label(TransferValue value, Map<String, String> effectiveLabels) {
        return switch (value) {
            case BooleanValue flag -> Boolean.toString(flag.value());
            case Enumeration enumeration -> {
                String path = String.join(".", enumeration.path());
                yield effectiveLabels.getOrDefault(path, fallback(path, beautify));
            }
            case null, default -> null;
        };
    }

    static String fallback(String path, boolean beautify) {
        return beautify ? path.replace('_', ' ') : path;
    }
}
