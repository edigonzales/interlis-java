/** Explicit filesystem, HTTP and repository host adapters. */
module guru.interlis.repository {
    requires transitive guru.interlis.workspace;
    requires transitive guru.interlis.model;
    requires java.net.http;
    requires java.xml;
    exports guru.interlis.repository;
}
