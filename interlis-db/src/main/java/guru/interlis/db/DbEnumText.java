package guru.interlis.db;

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
        var labels = new HashMap<String, String>();
        if (column.kind() == DbMapping.Kind.ENUMERATION) {
            if (column.enumeration().filter(value -> !value.labels().isEmpty()).isPresent()) {
                return new DbEnumText(column.name() + "_txt", column.enumeration().orElseThrow().labels(), beautify);
            }
            var definition = DbEnumeration.definition(plan, (AttributeSlot) column.slot());
            String language = language(plan, definition);
            DbEnumeration.elements(plan, definition).forEach((path, element) -> {
                String label = displayName(element, language);
                if (label != null && !label.isEmpty()) labels.put(path, label);
            });
        }
        return new DbEnumText(column.name() + "_txt", labels, beautify);
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
        return switch (value) {
            case BooleanValue flag -> Boolean.toString(flag.value());
            case Enumeration enumeration -> {
                String path = String.join(".", enumeration.path());
                yield labels.getOrDefault(path, fallback(path, beautify));
            }
            case null, default -> null;
        };
    }

    static String fallback(String path, boolean beautify) {
        return beautify ? path.replace('_', ' ') : path;
    }
}
