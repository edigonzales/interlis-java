/** Streaming XML adapter for INTERLIS 2.3 and 2.4 transfers. */
module guru.interlis.xtf {
    requires java.xml;
    requires guru.interlis.model;
    requires transitive guru.interlis.transfer;

    exports guru.interlis.xtf23;
    exports guru.interlis.xtf24;
}
