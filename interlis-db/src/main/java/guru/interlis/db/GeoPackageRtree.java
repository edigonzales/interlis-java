package guru.interlis.db;

import org.sqlite.Function;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static guru.interlis.db.Database.quote;
import static guru.interlis.db.Ili2dbLayout.ID_COLUMN;

/** The GeoPackage RTree extension on a SQLite connection. */
final class GeoPackageRtree {
    private static final String EXTENSION = "gpkg_rtree_index";
    private static final List<String> FUNCTIONS = List.of("ST_IsEmpty", "ST_MinX", "ST_MaxX", "ST_MinY", "ST_MaxY");

    private GeoPackageRtree() { }

    static boolean check(Connection connection, String table, String column) throws SQLException {
        String index = indexName(table, column);
        String virtual = sql(connection, "table", index);
        int registration = registration(connection, table, column);
        var triggers = triggers(table, column);
        int matches = 0;
        try (var statement = connection.prepareStatement("""
                SELECT name, sql FROM main.sqlite_schema WHERE type = 'trigger' AND tbl_name = ? COLLATE NOCASE
                AND (substr(lower(name), 1, 6) = 'rtree_' OR instr(lower(sql), 'st_isempty(') > 0)
                """)) {
            statement.setString(1, table);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString(1).toLowerCase(Locale.ROOT);
                    String expected = triggers.get(name);
                    if (expected == null || !normalized(expected).equals(normalized(rows.getString(2)))) {
                        throw invalid(table, column, "unexpected trigger " + name);
                    }
                    matches++;
                }
            }
        }
        if (virtual == null && registration == 0 && matches == 0) return false;
        if (registration != 1) throw invalid(table, column, "expected one write-only extension registration");
        if (virtual == null || !normalized(createIndex(index)).equals(normalized(virtual))) {
            throw invalid(table, column, "expected the RTree virtual table " + index);
        }
        if (matches != triggers.size()) throw invalid(table, column, "missing index maintenance trigger");
        return true;
    }

    private static int registration(Connection connection, String table, String column) throws SQLException {
        if (sql(connection, "table", "gpkg_extensions") == null) return 0;
        try (var statement = connection.prepareStatement("""
                SELECT scope FROM main.gpkg_extensions
                WHERE table_name = ? COLLATE NOCASE AND column_name = ? COLLATE NOCASE AND extension_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            statement.setString(3, EXTENSION);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return 0;
                return "write-only".equals(rows.getString(1)) && !rows.next() ? 1 : -1;
            }
        }
    }

    private static String sql(Connection connection, String type, String name) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT sql FROM main.sqlite_schema WHERE type = ? AND name = ? COLLATE NOCASE")) {
            statement.setString(1, type);
            statement.setString(2, name);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static String indexName(String table, String column) {
        return "rtree_" + table + "_" + column;
    }

    private static String createIndex(String name) {
        return "CREATE VIRTUAL TABLE " + quote(name) + " USING rtree(id,minx,maxx,miny,maxy)";
    }

    private static Map<String, String> triggers(String table, String column) {
        String name = indexName(table, column);
        String key = name.toLowerCase(Locale.ROOT);
        String field = "NEW." + quote(column);
        String oldId = "OLD." + quote(ID_COLUMN);
        String newId = "NEW." + quote(ID_COLUMN);
        String present = "(" + field + " NOT NULL AND NOT ST_IsEmpty(" + field + "))";
        String empty = "(" + field + " ISNULL OR ST_IsEmpty(" + field + "))";
        String unchanged = oldId + " = " + newId + " AND ";
        String changed = oldId + " != " + newId + " AND ";
        String insert = "INSERT OR REPLACE INTO " + quote(name) + " VALUES (" + newId
                + ",ST_MinX(" + field + "),ST_MaxX(" + field + "),ST_MinY(" + field + "),ST_MaxY(" + field + "));";
        String delete = "DELETE FROM " + quote(name) + " WHERE id = " + oldId + ";";
        String deleteBoth = "DELETE FROM " + quote(name) + " WHERE id IN (" + oldId + "," + newId + ");";
        String updateGeometry = "UPDATE OF " + quote(column);
        return Map.of(
                key + "_insert", trigger(name + "_insert", table, "INSERT", present, insert),
                key + "_update1", trigger(name + "_update1", table, updateGeometry, unchanged + present, insert),
                key + "_update2", trigger(name + "_update2", table, updateGeometry, unchanged + empty, delete),
                key + "_update3", trigger(name + "_update3", table, "UPDATE", changed + present, delete + " " + insert),
                key + "_update4", trigger(name + "_update4", table, "UPDATE", changed + empty, deleteBoth),
                key + "_delete", trigger(name + "_delete", table, "DELETE", "OLD." + quote(column) + " NOT NULL", delete));
    }

    private static String trigger(String name, String table, String event, String condition, String body) {
        return "CREATE TRIGGER " + quote(name) + " AFTER " + event + " ON " + quote(table) + " WHEN " + condition
                + " BEGIN " + body + " END";
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT).strip().replaceAll(";$", "")
                .replaceAll("[\"`\\[]([a-z_][a-z_0-9$]*)[\"`\\]]", "$1")
                .replaceAll("\\bnot\\s+null\\b", "notnull")
                .replaceAll("\\bis\\s+null\\b", "isnull")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*([.,();=])\\s*", "$1");
    }

    static void register(Connection connection) throws SQLException {
        for (String name : FUNCTIONS) {
            Function.create(connection, name, new Function() {
                @Override protected void xFunc() throws SQLException {
                    // SQLite value types 4 and 5 are BLOB and NULL.
                    if (value_type(0) == 5) {
                        result();
                        return;
                    }
                    if (value_type(0) != 4) {
                        error("Expected a GeoPackage geometry BLOB");
                        return;
                    }
                    try {
                        var bounds = GeometryBinary.indexEnvelope(value_blob(0));
                        if (name.equals("ST_IsEmpty")) result(bounds == null ? 1 : 0);
                        else if (bounds == null) result();
                        else result(switch (name) {
                            case "ST_MinX" -> bounds.minX();
                            case "ST_MaxX" -> bounds.maxX();
                            case "ST_MinY" -> bounds.minY();
                            case "ST_MaxY" -> bounds.maxY();
                            default -> throw new AssertionError(name);
                        });
                    } catch (DbException failure) {
                        error(failure.getMessage());
                    }
                }
            }, 1, Function.FLAG_DETERMINISTIC);
        }
    }

    private static DbException invalid(String table, String column, String reason) {
        return new DbException("Invalid GeoPackage RTree at " + table + "." + column + ": " + reason);
    }
}
