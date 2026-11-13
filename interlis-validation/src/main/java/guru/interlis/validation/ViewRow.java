package guru.interlis.validation;

import guru.interlis.model.SymbolId;
import guru.interlis.transfer.TransferValue;

import java.util.List;
import java.util.Map;

/** Virtual row identity comes from its view and ordered real occurrences. */
record ViewRow(SymbolId view, Map<SymbolId, TransferValue.Structured> bases,
               List<DatasetRuleContext.Occurrence> origins) {
    ViewRow {
        bases = Map.copyOf(bases);
        origins = List.copyOf(origins);
    }
}
