package guru.interlis.validation;

import guru.interlis.model.ReferenceType;
import guru.interlis.transfer.TransferValue;

record DatasetPathContext(DatasetRuleContext dataset, DatasetRuleContext.Occurrence occurrence) {
    ReferenceResolver.Result resolve(TransferValue.Reference reference, ReferenceType type) {
        return dataset.resolve(occurrence, reference, type);
    }

    java.util.Map<guru.interlis.model.SymbolId, guru.interlis.model.SymbolId> domains() {
        return ValidationSession.BasketContext.of(occurrence.basket(), dataset.plan).domains();
    }

    DatasetPathContext target(ObjectHandle handle) {
        return new DatasetPathContext(dataset, dataset.occurrence(handle));
    }
}
