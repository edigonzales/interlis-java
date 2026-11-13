/** Optional JVM implementation of INTERLIS geometry operations. */
module guru.interlis.validation.jts {
    requires transitive guru.interlis.validation;
    requires org.locationtech.jts;
    exports guru.interlis.validation.jts;
}
