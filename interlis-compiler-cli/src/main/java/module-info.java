/** CLI entry and typed in-process diagnostic/termination contracts. */
module guru.interlis.compiler.cli {
    requires transitive guru.interlis.compiler;
    requires transitive guru.interlis.model;
    requires transitive guru.interlis.repository;
    requires guru.interlis.workspace;
    requires guru.interlis.projection;
    exports guru.interlis.cli;
}
