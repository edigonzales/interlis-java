package guru.interlis.validation;

import guru.interlis.model.ReferenceType;
import guru.interlis.transfer.TransferValue;

record DatasetPathContext(DatasetRuleContext dataset, DatasetRuleContext.Occurrence occurrence,
                          TransferValue.Structured payload, RelationProjector.Link embeddedLink) {
    DatasetPathContext(DatasetRuleContext dataset, DatasetRuleContext.Occurrence occurrence) {
        this(dataset, occurrence, null, null);
    }

    java.util.Optional<ObjectHandle> implicitRole(TransferValue.Structured value, guru.interlis.model.SymbolRef role) {
        if (value != payload || embeddedLink == null) {
            return java.util.Optional.empty();
        }
        return embeddedLink.endpoints().stream().filter(endpoint -> endpoint.role().equals(role))
                .flatMap(endpoint -> endpoint.implicitTarget().stream()).findFirst();
    }

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
