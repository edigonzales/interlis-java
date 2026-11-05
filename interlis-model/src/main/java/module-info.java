/** Immutable public source, model, diagnostics and execution contracts. */
module guru.interlis.model {
    exports guru.interlis.model;
    exports guru.interlis.model.query;
    exports guru.interlis.source;
    exports guru.interlis.diagnostics;
    exports guru.interlis.execution;
    // Shared implementation rules are consumed by the compiler, not SDK clients.
    exports guru.interlis.model.internal to guru.interlis.compiler;
}
