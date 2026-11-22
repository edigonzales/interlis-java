package guru.interlis.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compare the stored profile and database shape while ignoring record order and timestamps. */
record DbSchemaCatalog(Map<String, Set<List<String>>> relations) {
    private static final Pattern MODEL_IMPORTS = Pattern.compile("\\{([^{}]*)\\}");

    static DbSchemaCatalog read(Connection sql, boolean postgis, String schema) throws SQLException {
        var catalog = new LinkedHashMap<String, Set<List<String>>>();
        if (postgis) {
            catalog.put("columns", rows(sql, """
                    select table_name, ordinal_position, column_name, data_type, character_maximum_length,
                           is_nullable, replace(column_default, '%1$s.', '<schema>.')
                    from information_schema.columns where table_schema = '%1$s'
                    """.formatted(schema)));
            catalog.put("PostGIS geometry_columns", rows(sql, """
                    select f_table_name, f_geometry_column, type, coord_dimension, srid
                    from geometry_columns where f_table_schema = '%s'
                    """.formatted(schema)));
            catalog.put("PostgreSQL sequences", rows(sql, """
                    select sequencename, data_type, start_value, min_value, max_value, increment_by, cycle, cache_size
                    from pg_sequences where schemaname = '%s'
                    """.formatted(schema)));
            catalog.put("unique keys", uniqueKeys(rows(sql, """
                    select t.relname, i.relname, a.attname from pg_index x
                    join pg_class t on t.oid = x.indrelid
                    join pg_class i on i.oid = x.indexrelid
                    join pg_namespace n on n.oid = t.relnamespace
                    join pg_attribute a on a.attrelid = t.oid and a.attnum = any(x.indkey)
                    where x.indisunique and n.nspname = '%s'
                    """.formatted(schema))));
            catalog.put("primary keys", rows(sql, """
                    select t.relname, a.attname from pg_index x
                    join pg_class t on t.oid = x.indrelid
                    join pg_namespace n on n.oid = t.relnamespace
                    join pg_attribute a on a.attrelid = t.oid and a.attnum = any(x.indkey)
                    where x.indisprimary and n.nspname = '%s'
                    """.formatted(schema)));
        } else {
            catalog.put("columns", rows(sql, """
                    select lower(s.name), t.cid, lower(t.name), upper(t.type), t."notnull", t.dflt_value
                    from sqlite_schema s join pragma_table_info(s.name) t
                    where s.type = 'table' and substr(s.name, 1, 7) <> 'sqlite_'
                    """));
            catalog.put("unique keys", uniqueKeys(rows(sql, """
                    select lower(s.name), i.name, lower(c.name)
                    from sqlite_schema s join pragma_index_list(s.name) i join pragma_index_info(i.name) c
                    where s.type = 'table' and i."unique" = 1 and substr(s.name, 1, 7) <> 'sqlite_'
                    """)));
            catalog.put("primary keys", rows(sql, """
                    select lower(s.name), lower(t.name) from sqlite_schema s join pragma_table_info(s.name) t
                    where s.type = 'table' and t.pk > 0 and substr(s.name, 1, 7) <> 'sqlite_'
                    """));
            catalog.put("GeoPackage application ID", rows(sql, "pragma application_id"));
            catalog.put("GeoPackage user version", rows(sql, "pragma user_version"));
            catalog.put("gpkg_contents", rows(sql, """
                    select lower(table_name), data_type, lower(identifier), coalesce(description, ''),
                           min_x, min_y, max_x, max_y, srs_id
                    from gpkg_contents
                    """));
            catalog.put("gpkg_geometry_columns", rows(sql, """
                    select table_name, column_name, geometry_type_name, srs_id, z, m from gpkg_geometry_columns
                    """));
            catalog.put("gpkg_spatial_ref_sys", rows(sql, """
                    select srs_name, srs_id, organization, organization_coordsys_id, definition, description
                    from gpkg_spatial_ref_sys
                    """));
            catalog.put("gpkg_extensions", rows(sql, """
                    select table_name, coalesce(column_name, ''), extension_name, definition, scope from gpkg_extensions
                    """));
        }

        String prefix = postgis ? schema + "." : "";
        for (String table : List.of("attrname", "classname", "column_prop", "inheritance", "table_prop", "trafo")) {
            catalog.put(table, rows(sql, "select * from " + prefix + "t_ili2db_" + table));
        }
        var models = new HashSet<List<String>>();
        for (var row : rows(sql, "select filename, iliversion, modelname, content from "
                + prefix + "t_ili2db_model where importdate is not null")) {
            // ili2db's dependency sets may enumerate the same imports in different orders.
            String names = MODEL_IMPORTS.matcher(row.get(2)).replaceAll(match -> Matcher.quoteReplacement(
                    "{ " + String.join(" ", java.util.Arrays.stream(match.group(1).strip().split("\\s+")).sorted().toList()) + "}"));
            models.add(List.of(row.get(0), row.get(1), names, row.get(3)));
        }
        catalog.put("model", models);
        catalog.put("settings", rows(sql, "select tag, setting from " + prefix + """
                t_ili2db_settings where tag not in ('ch.ehi.ili2db.sender', 'ch.interlis.ili2c.ilidirs')
                """));
        return new DbSchemaCatalog(catalog);
    }

    DbSchemaCatalog expectGeoPackageGeometryExtension(String table, String column, String geometryType) {
        // ili2gpkg 5.5.2 omits the extension rows required by GeoPackage for non-linear types.
        var expected = new LinkedHashMap<>(relations);
        var extensions = new HashSet<>(expected.get("gpkg_extensions"));
        assertTrue(extensions.add(List.of(table, column, "gpkg_geom_" + geometryType,
                "https://www.geopackage.org/spec131/#extension_geometry_types", "read-write")));
        expected.put("gpkg_extensions", extensions);
        return new DbSchemaCatalog(expected);
    }

    void assertSameAs(DbSchemaCatalog actual) {
        assertEquals(relations.keySet(), actual.relations.keySet());
        relations.forEach((relation, expected) -> assertEquals(expected, actual.relations.get(relation), relation));
    }

    DbSchemaCatalog canonicalReferenceAlternatives() {
        // ili2db enumerates some target classes from a set. Only the order within each
        // alternative group may vary. Keep all column metadata and the group's positions.
        var tables = new HashMap<List<String>, Set<String>>();
        for (var attribute : relations.get("attrname")) {
            if (attribute.get(3) != null) {
                tables.computeIfAbsent(List.of(attribute.get(2), attribute.get(0)), key -> new HashSet<>())
                        .add(attribute.get(1));
            }
        }
        var columns = new HashSet<>(relations.get("columns"));
        for (var group : tables.entrySet()) {
            if (group.getValue().size() < 2) continue;
            var alternatives = columns.stream().filter(row -> row.getFirst().equals(group.getKey().getFirst())
                            && group.getValue().contains(row.get(2)))
                    .sorted(Comparator.comparing(row -> row.get(2))).toList();
            var positions = alternatives.stream().mapToInt(row -> Integer.parseInt(row.get(1))).sorted().toArray();
            columns.removeAll(alternatives);
            for (int i = 0; i < positions.length; i++) {
                var column = new ArrayList<>(alternatives.get(i));
                column.set(1, Integer.toString(positions[i]));
                columns.add(column);
            }
        }
        var result = new LinkedHashMap<>(relations);
        result.put("columns", columns);
        return new DbSchemaCatalog(result);
    }

    private static Set<List<String>> uniqueKeys(Set<List<String>> indexColumns) {
        // The reference does not consistently order composite keys with the same columns.
        var byIndex = new HashMap<List<String>, Set<String>>();
        for (var indexColumn : indexColumns) {
            byIndex.computeIfAbsent(indexColumn.subList(0, 2), key -> new HashSet<>()).add(indexColumn.get(2));
        }
        var keys = new HashSet<List<String>>();
        byIndex.forEach((name, columns) -> {
            var key = new ArrayList<String>();
            key.add(name.getFirst());
            key.addAll(columns.stream().sorted().toList());
            keys.add(key);
        });
        return keys;
    }

    private static Set<List<String>> rows(Connection sql, String query) throws SQLException {
        try (var statement = sql.createStatement(); var result = statement.executeQuery(query)) {
            int count = result.getMetaData().getColumnCount();
            var rows = new HashSet<List<String>>();
            while (result.next()) {
                var row = new ArrayList<String>(count);
                for (int column = 1; column <= count; column++) row.add(result.getString(column));
                rows.add(row);
            }
            return rows;
        }
    }
}
