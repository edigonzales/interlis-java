/** Internal host-only verification; never part of SDK, LSP or portable delivery. */
module guru.interlis.verification {
    requires guru.interlis.compiler;
    requires guru.interlis.model;
    requires guru.interlis.workspace;
    requires guru.interlis.repository;
    requires guru.interlis.wasm.bridge;
    requires com.google.gson;
    requires java.xml;
    requires java.management;
    // Controlled local HTTP fixtures in the internal CLI process verifier.
    requires jdk.httpserver;
    requires java.compiler;
    requires jdk.compiler;
    // Host process records are serialized/deserialized by the existing Gson writer.
    opens guru.interlis.verification to com.google.gson;
}
