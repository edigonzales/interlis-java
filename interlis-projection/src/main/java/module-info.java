/** Pure diagram projection from accepted compiler/model snapshots. */
module guru.interlis.projection {
    requires transitive guru.interlis.compiler;
    requires transitive guru.interlis.model;
    exports guru.interlis.projection;
}
