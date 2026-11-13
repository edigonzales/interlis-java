package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferValue;

import java.util.Optional;

/** Read-only access to frozen host parameters and the current work budget. */
public final class FunctionContext {
    private final RuntimeBindings bindings;
    private final Runnable visit;

    FunctionContext(RuntimeBindings bindings, Runnable visit) {
        this.bindings = bindings;
        this.visit = visit;
    }

    public Optional<TransferValue> parameter(SymbolRef symbol) {
        consumeWork();
        return Optional.ofNullable(bindings.parameters().get(symbol));
    }

    public void consumeWork() {
        visit.run();
    }
}
