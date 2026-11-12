/** Immutable public source, model, diagnostics and execution contracts. */
module guru.interlis.model {
    exports guru.interlis.model;
    exports guru.interlis.model.query;
    exports guru.interlis.source;
    exports guru.interlis.diagnostics;
    exports guru.interlis.execution;
    // Shared implementation rules for the compiler and validator.
    exports guru.interlis.model.internal to guru.interlis.compiler, guru.interlis.validation;
}
