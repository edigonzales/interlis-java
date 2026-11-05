/** Workspace snapshots, updates and prepared resolution contracts. */
module guru.interlis.workspace {
    requires transitive guru.interlis.compiler;
    requires transitive guru.interlis.model;
    exports guru.interlis.workspace;
    exports guru.interlis.workspace.resolution;
}
