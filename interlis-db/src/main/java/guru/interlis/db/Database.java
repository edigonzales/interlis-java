package guru.interlis.db;

import guru.interlis.db.DbMapping.Column;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.MultiCoordinate;
import guru.interlis.transfer.TransferGeometry.MultiPolyline;
import guru.interlis.transfer.TransferGeometry.MultiSurface;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.ID_COLUMN;
import static guru.interlis.db.Ili2dbLayout.KEY_OBJECT;
import static guru.interlis.db.Ili2dbLayout.SEQUENCE;

/** One JDBC connection and transaction against an existing ili2db schema. */
final class Database implements AutoCloseable {
    private final Connection connection;
    private final String schema;
    private final String postgisSchema;
    private final boolean importing;
    private final Set<SymbolRef> indexedGeometry = new HashSet<>();
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
                "main", true, importing, Connection.TRANSACTION_READ_COMMITTED);
    }

    static Database openPostgres(String url, String schema, String user, String password, boolean importing)
            throws SQLException {
        return openPostgres(url, schema, user, password, importing, Connection.TRANSACTION_REPEATABLE_READ);
    }

    static Database openPostgres(String url, String schema, String user, String password, boolean importing,
                                 int isolation) throws SQLException {
        var properties = new Properties();
        properties.setProperty("user", user);
        if (password != null) {
            properties.setProperty("password", password);
        }
        properties.setProperty("connectTimeout", "10");
        return open(DriverManager.getConnection(url, properties), schema, false, importing, isolation);
    }

    private static Database open(Connection connection, String schema, boolean gpkg, boolean importing, int isolation)
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
            connection.setTransactionIsolation(isolation);
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

    int geometrySrs(String table, Column column) throws SQLException {
        if (gpkg()) {
            return geoPackageSrs(table, column);
        }
        String sql = """
                SELECT g.srid FROM %s g JOIN %s s ON s.srid = g.srid
                WHERE g.f_table_schema = ? AND g.f_table_name = ? AND g.f_geometry_column = ?
                AND g.type = ? AND g.coord_dimension = ? AND g.srid = ?
                AND s.auth_name = 'EPSG' AND s.auth_srid = ?
                """.formatted(postgisObject("geometry_columns"), postgisObject("spatial_ref_sys"));
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column.name());
            statement.setString(4, column.kind().geometryType());
            statement.setInt(5, column.dimensions());
            statement.setInt(6, column.epsgCode());
            statement.setInt(7, column.epsgCode());
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    return rows.getInt(1);
                }
            }
        }
        throw unsupportedGeometry(table, column);
    }

    private int geoPackageSrs(String table, Column column) throws SQLException {
        String sql = """
                SELECT g.srs_id FROM main.gpkg_geometry_columns g
                JOIN main.gpkg_spatial_ref_sys s ON s.srs_id = g.srs_id
                JOIN main.gpkg_contents c ON c.table_name = g.table_name
                WHERE g.table_name = ? AND g.column_name = ? AND c.data_type = 'features'
                AND g.geometry_type_name = ? AND g.z = ? AND g.m = 0
                AND s.organization = 'EPSG' AND s.organization_coordsys_id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, column.name());
            statement.setString(3, column.kind().geometryType());
            statement.setInt(4, column.dimensions() - 2);
            statement.setInt(5, column.epsgCode());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw unsupportedGeometry(table, column);
                }
                return rows.getInt(1);
            }
        }
    }

    void checkGeometryTable(String table, List<Column> columns) throws SQLException {
        if (!gpkg() || columns.isEmpty()) return;
        // In the ili2gpkg multi-column layout, the contents row describes the first physical geometry column.
        String sql = """
                SELECT c.srs_id = g.srs_id FROM main.gpkg_contents c
                JOIN main.gpkg_geometry_columns g ON g.table_name = c.table_name
                JOIN pragma_table_info(?) p ON p.name = g.column_name
                WHERE c.table_name = ? ORDER BY p.cid LIMIT 1
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, table);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean(1)) {
                    throw new DbException("GeoPackage contents SRS differs from the first geometry column in " + table);
                }
            }
        }
        if (!importing) return;
        var indexed = GeoPackageRtree.indexedColumns(connection, table, columns.stream().map(Column::name).toList());
        if (!indexed.isEmpty() && indexedGeometry.isEmpty()) GeoPackageRtree.register(connection);
        for (var column : columns) {
            if (indexed.contains(column.name())) indexedGeometry.add(column.slot().symbol());
        }
    }

    private static DbException unsupportedGeometry(String table, Column column) {
        return new DbException("Expected an EPSG:" + column.epsgCode() + " " + column.dimensions() + "D " + column.kind().geometryType()
                + " column at " + table + "." + column.name());
    }

    String geometryProjection(String field, Column column) {
        if (gpkg()) {
            // Bound the bytes sent to JDBC even when a database contains a malformed geometry.
            return "CASE WHEN length(" + field + ") > " + GeometryBinary.maxGeoPackageSize(column.kind(), column.dimensions())
                    + " OR typeof(" + field + ") NOT IN ('null', 'blob') THEN X'' ELSE " + field + " END";
        }
        // Read one byte past the limit so the codec can reject oversized values.
        return "pg_catalog.substr(" + postgisObject("st_asbinary") + "(" + field + "), 1, "
                + (GeometryBinary.maxWkbSize(column.kind(), column.dimensions()) + 1) + ")";
    }

    String geometryParameter() {
        return gpkg() ? "?" : postgisObject("st_geomfromwkb") + "(?, ?)";
    }

    TransferGeometry readGeometry(ResultSet rows, int index, Column column) throws SQLException {
        byte[] bytes = rows.getBytes(index);
        try {
            if (bytes == null) {
                return null;
            }
            var geometry = GeometryBinary.decode(bytes, gpkg(), column.srsId(), column.kind(), column.dimensions());
            // The reference exporter omits attributes stored as empty collections.
            boolean empty = switch (geometry) {
                case MultiCoordinate multi -> multi.coordinates().isEmpty();
                case MultiPolyline multi -> multi.lines().isEmpty();
                case MultiSurface multi -> multi.surfaces().isEmpty();
                default -> false;
            };
            return empty ? null : geometry;
        } catch (DbException failure) {
            throw invalidGeometry(column, failure);
        }
    }

    int bindGeometry(PreparedStatement statement, int index, Column column, TransferGeometry value) throws SQLException {
        byte[] bytes;
        try {
            bytes = value == null ? null : indexedGeometry.contains(column.slot().symbol())
                    ? GeometryBinary.encodeIndexedGeoPackage(value, column.srsId(), column.kind(), column.dimensions())
                    : GeometryBinary.encode(value, gpkg(), column.srsId(), column.kind(), column.dimensions());
        } catch (DbException failure) {
            throw invalidGeometry(column, failure);
        }
        statement.setBytes(index++, bytes);
        if (!gpkg()) {
            statement.setInt(index++, column.srsId());
        }
        return index;
    }

    private static DbException invalidGeometry(Column column, DbException failure) {
        return new DbException("Invalid geometry at " + column.slot().name().declared() + ": " + failure.getMessage(),
                failure);
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
