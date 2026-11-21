/** Local DuckDB host for INTERLIS transfer objects. */
module guru.interlis.explorer {
    requires guru.interlis.model;
    requires transitive guru.interlis.transfer;
    requires transitive java.sql;
    requires guru.interlis.xtf;
    requires com.google.gson;
    requires duckdb.jdbc;

    exports guru.interlis.explorer;
}
