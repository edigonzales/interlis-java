/** Portable JSON session boundary; compiler/workspace types remain implementation details. */
module guru.interlis.wasm.bridge {
    requires guru.interlis.workspace;
    requires guru.interlis.compiler;
    requires guru.interlis.projection;
    requires guru.interlis.model;
    exports guru.interlis.bridge;
}
