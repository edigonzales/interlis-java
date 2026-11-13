package guru.interlis.validation;

import guru.interlis.model.SymbolId;
import guru.interlis.transfer.TransferValue;

import java.util.List;
import java.util.Map;

/** Virtual row identity comes from its view and ordered real occurrences. */
record ViewRow(SymbolId view, Map<SymbolId, Base> bases,
               List<DatasetRuleContext.Occurrence> origins, List<TransferValue.Structured> parents,
               java.util.OptionalLong groupSize, java.util.Set<SymbolId> nullBases) {
    ViewRow(SymbolId view, Map<SymbolId, Base> bases, List<DatasetRuleContext.Occurrence> origins) {
        this(view, bases, origins, List.of(), java.util.OptionalLong.empty());
    }

    ViewRow(SymbolId view, Map<SymbolId, Base> bases, List<DatasetRuleContext.Occurrence> origins,
            List<TransferValue.Structured> parents, java.util.OptionalLong groupSize) {
        this(view, bases, origins, parents, groupSize, java.util.Set.of());
    }

    record Base(TransferValue.Structured value, DatasetRuleContext.Occurrence origin,
                List<TransferValue.Structured> parents, java.util.Optional<ViewRow> row) {
        Base(TransferValue.Structured value, DatasetRuleContext.Occurrence origin) {
            this(value, origin, List.of(), java.util.Optional.empty());
        }
    }

    ViewRow {
        bases = Map.copyOf(bases);
        origins = List.copyOf(origins);
        parents = List.copyOf(parents);
        nullBases = java.util.Set.copyOf(nullBases);
    }
}
