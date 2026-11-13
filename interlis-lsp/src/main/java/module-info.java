/** Stdio LSP host and versioned editor DTO/API contracts. */
module guru.interlis.lsp {
    requires transitive guru.interlis.workspace;
    requires transitive guru.interlis.compiler;
    requires transitive guru.interlis.projection;
    requires transitive guru.interlis.model;
    requires guru.interlis.repository;
    requires transitive org.eclipse.lsp4j;
    requires org.eclipse.lsp4j.jsonrpc;
    requires transitive com.google.gson;
    // Gson must instantiate LSP4J Either3 while copying formatting parameters.
    requires jdk.unsupported;
    exports guru.interlis.lsp;
    // DTO records and the package-private authoritative diagram TypeAdapter.
    opens guru.interlis.lsp to com.google.gson;
}
