package guru.interlis.validation;

import guru.interlis.model.SymbolId;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
interface RelationFindingSink {
    void report(ObjectHandle source, List<ObjectHandle> counterparts, SymbolId role, Optional<ValidationCode> violation, boolean complete,
                List<String> arguments);
}
