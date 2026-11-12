/** Optional JVM storage for one dataset-validation run. */
module guru.interlis.validation.mvstore {
    requires transitive guru.interlis.validation;
    requires com.h2database.mvstore;
    exports guru.interlis.validation.mvstore;
}
