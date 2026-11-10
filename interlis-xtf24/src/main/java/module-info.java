/** Streaming XML adapter for INTERLIS 2.4 transfers. */
module guru.interlis.xtf24 {
    requires java.xml;
    requires guru.interlis.model;
    requires transitive guru.interlis.transfer;

    exports guru.interlis.xtf24;
}
