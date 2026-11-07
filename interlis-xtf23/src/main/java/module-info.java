/** Streaming XML adapter for INTERLIS 2.3 transfers. */
module guru.interlis.xtf23 {
    requires java.xml;
    requires guru.interlis.model;
    requires transitive guru.interlis.transfer;

    exports guru.interlis.xtf23;
}
