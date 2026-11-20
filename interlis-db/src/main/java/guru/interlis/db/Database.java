package guru.interlis.db;

import guru.interlis.transfer.TransferGeometry.Coordinate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Properties;

import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.ID_COLUMN;
import static guru.interlis.db.Ili2dbLayout.KEY_OBJECT;
import static guru.interlis.db.Ili2dbLayout.LV95;
import static guru.interlis.db.Ili2dbLayout.SEQUENCE;

/** One JDBC connection and transaction against an existing ili2db schema. */
final class Database implements AutoCloseable {
    private final Connection connection;
    private final String schema;
    private final String postgisSchema;
    private final boolean importing;
    private PreparedStatement allocateId;
    private boolean active = true;

    private Database(Connection connection, String schema, String postgisSchema, boolean importing) {
        this.connection = connection;
        this.schema = schema;
        this.postgisSchema = postgisSchema;
        this.importing = importing;
    }

    static Database openGeoPackage(Path path, boolean importing) throws SQLException {
        var config = new SQLiteConfig();
        config.setReadOnly(!importing);
        config.resetOpenMode(SQLiteOpenMode.CREATE);
        config.setBusyTimeout(5000);
        config.enforceForeignKeys(true);
        return open(DriverManager.getConnection("jdbc:sqlite:" + path.toUri(), config.toProperties()),
                "main", true, importing);
    }

    static Database openPostgres(String url, String schema, String user, String password, boolean importing)
            throws SQLException {
        var properties = new Properties();
        properties.setProperty("user", user);
        if (password != null) {
            properties.setProperty("password", password);
        }
        properties.setProperty("connectTimeout", "10");
        return open(DriverManager.getConnection(url, properties), schema, false, importing);
    }

    private static Database open(Connection connection, String schema, boolean gpkg, boolean importing)
            throws SQLException {
        try {
            if (gpkg) {
                try (var statement = connection.createStatement()) {
                    try (var rows = statement.executeQuery("PRAGMA application_id")) {
                        if (!rows.next() || switch (rows.getInt(1)) {
                            case 0x47503130, 0x47503131, 0x47504B47 -> false; // GP10, GP11, GPKG
                            default -> true;
                        }) {
                            throw new DbException("Expected a GeoPackage database");
                        }
                    }
                    statement.execute(importing ? "BEGIN IMMEDIATE" : "BEGIN");
                }
                return new Database(connection, schema, null, importing);
            }
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(!importing);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("""
                         SELECT n.nspname FROM pg_catalog.pg_extension e
                         JOIN pg_catalog.pg_namespace n ON n.oid = e.extnamespace
                         WHERE e.extname = 'postgis'
                         """)) {
                if (!rows.next()) {
                    throw new DbException("PostGIS extension is required");
                }
                return new Database(connection, schema, rows.getString(1), importing);
            }
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    Connection connection() {
        return connection;
    }

    String schema() {
        return schema;
    }

    boolean gpkg() {
        return postgisSchema == null;
    }

    boolean importing() {
        return importing;
    }

    String postgisObject(String name) {
        return qualified(postgisSchema, name);
    }

    String table(String name) {
        return qualified(schema, name);
    }

    String technicalTable(String name) {
        return table(Ili2dbLayout.tableName(name, gpkg()));
    }

    static String quote(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private static String qualified(String schema, String name) {
        return quote(schema) + "." + quote(name);
    }

    int pointSrs(String table, String column) throws SQLException {
        if (gpkg()) {
            return geoPackageSrs(table, column);
        }
        String sql = """
                SELECT srid FROM %s WHERE f_table_schema = ? AND f_table_name = ?
                AND f_geometry_column = ? AND type = 'POINT' AND coord_dimension = 2 AND srid = ?
                """.formatted(qualified(postgisSchema, "geometry_columns"));
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            statement.setInt(4, LV95);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    return LV95;
                }
            }
        }
        throw unsupportedPoint(table, column);
    }

    private int geoPackageSrs(String table, String column) throws SQLException {
        String sql = """
                SELECT g.srs_id FROM main.gpkg_geometry_columns g
                JOIN main.gpkg_spatial_ref_sys s ON s.srs_id = g.srs_id
                JOIN main.gpkg_contents c ON c.table_name = g.table_name AND c.srs_id = g.srs_id
                WHERE g.table_name = ? AND g.column_name = ? AND c.data_type = 'features'
                AND g.geometry_type_name = 'POINT' AND g.z = 0 AND g.m = 0
                AND s.organization = 'EPSG' AND s.organization_coordsys_id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            statement.setInt(3, LV95);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw unsupportedPoint(table, column);
                }
                if (importing) {
                    checkGeoPackageIndex(table, column);
                }
                return rows.getInt(1);
            }
        }
    }

    private void checkGeoPackageIndex(String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT name FROM main.sqlite_schema
                WHERE (type = 'table' AND name = ? COLLATE NOCASE)
                    OR (type = 'trigger' AND tbl_name = ? COLLATE NOCASE
                        AND (substr(lower(name), 1, 6) = 'rtree_' OR instr(lower(sql), 'st_isempty(') > 0))
                """)) {
            statement.setString(1, "rtree_" + table + "_" + column);
            statement.setString(2, table);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    throw new DbException("GeoPackage RTree writes are unsupported for " + table);
                }
            }
        }
    }

    private static DbException unsupportedPoint(String table, String column) {
        return new DbException("Expected an EPSG:2056 2D POINT column at " + table + "." + column);
    }

    String pointProjection(String column) {
        if (gpkg()) {
            // Bound the bytes sent to JDBC even when a database contains a malformed geometry.
            return "CASE WHEN length(" + column + ") > " + PointBinary.MAX_GPKG_SIZE
                    + " OR typeof(" + column + ") NOT IN ('null', 'blob') THEN X'' ELSE " + column + " END";
        }
        return qualified(postgisSchema, "st_asbinary") + "(" + column + ")";
    }

    String pointParameter() {
        if (gpkg()) {
            return "?";
        }
        return qualified(postgisSchema, "st_setsrid") + "(" + qualified(postgisSchema, "st_makepoint") + "(?, ?), ?)";
    }

    Coordinate readPoint(ResultSet rows, int index, int srsId) throws SQLException {
        byte[] bytes = rows.getBytes(index);
        return bytes == null ? null : PointBinary.decode(bytes, gpkg(), srsId);
    }

    int bindPoint(PreparedStatement statement, int index, Coordinate point, int srsId) throws SQLException {
        if (gpkg()) {
            statement.setBytes(index, point == null ? null : PointBinary.encode(point, srsId));
            return index + 1;
        }
        if (point == null) {
            statement.setNull(index, Types.DOUBLE);
            statement.setNull(index + 1, Types.DOUBLE);
        } else {
            var ordinates = point.ordinates();
            if (ordinates.size() != 2) {
                throw new DbException("Expected a 2D point");
            }
            statement.setDouble(index, PointBinary.finite(ordinates.get(0).exactValue().doubleValue()));
            statement.setDouble(index + 1, PointBinary.finite(ordinates.get(1).exactValue().doubleValue()));
        }
        statement.setInt(index + 2, srsId);
        return index + 3;
    }

    long nextId() throws SQLException {
        if (allocateId == null) {
            allocateId = prepareAllocator();
        }
        try (var rows = allocateId.executeQuery()) {
            if (rows.next()) {
                return rows.getLong(1);
            }
        }
        throw new DbException("GeoPackage T_KEY_OBJECT has an invalid or exhausted T_Id counter");
    }

    private PreparedStatement prepareAllocator() throws SQLException {
        if (!gpkg()) {
            var statement = connection.prepareStatement("SELECT pg_catalog.nextval(CAST(? AS pg_catalog.regclass))");
            statement.setString(1, technicalTable(SEQUENCE));
            return statement;
        }
        String counter = technicalTable(KEY_OBJECT);
        try (var statement = connection.createStatement()) {
            // ili2gpkg creates the counter row on the first import into a fresh schema.
            statement.executeUpdate("""
                    INSERT INTO %1$s
                        (T_Key, T_LastUniqueId, T_CreateDate, T_LastChange, T_User)
                    SELECT '%2$s', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'interlis-db'
                    WHERE NOT EXISTS (SELECT 1 FROM %1$s WHERE T_Key = '%2$s')
                        AND NOT EXISTS (SELECT 1 FROM %3$s)
                        AND NOT EXISTS (SELECT 1 FROM %4$s)
                    """.formatted(counter, ID_COLUMN, technicalTable(DATASET), technicalTable(BASKET)));
        }
        return connection.prepareStatement("""
                UPDATE %s SET T_LastUniqueId = T_LastUniqueId + 1,
                    T_LastChange = CURRENT_TIMESTAMP, T_User = 'interlis-db'
                WHERE T_Key = '%s' AND typeof(T_LastUniqueId) = 'integer'
                    AND T_LastUniqueId >= 0 AND T_LastUniqueId < 9223372036854775807
                RETURNING T_LastUniqueId
                """.formatted(counter, ID_COLUMN));
    }

    void commit() throws SQLException {
        if (gpkg()) {
            try (var statement = connection.createStatement()) {
                statement.execute("COMMIT");
            }
        } else {
            connection.commit();
        }
        active = false;
    }

    @Override
    public void close() throws SQLException {
        try (connection) {
            if (active) {
                if (gpkg()) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("ROLLBACK");
                    }
                } else {
                    connection.rollback();
                }
            }
            if (allocateId != null) {
                allocateId.close();
            }
        }
    }
}
