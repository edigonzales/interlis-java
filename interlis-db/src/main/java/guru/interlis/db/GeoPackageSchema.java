package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** Builds the ili2gpkg container and publishes the completed database to a new path. */
final class GeoPackageSchema {
    private GeoPackageSchema() { }

    static void create(Path target, SchemaPlan plan, CancellationToken token) throws IOException, SQLException {
        token.throwIfCancellationRequested();
        Path destination = target.toAbsolutePath().normalize();
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new DbException("GeoPackage already exists: " + destination);
        }
        Path temporary = Files.createTempFile(destination.getParent(), ".interlis-db-", ".gpkg");
        try {
            build(temporary, plan, token);
            token.throwIfCancellationRequested();
            try {
                // Linking publishes the completed file without overwriting a concurrent target.
                Files.createLink(destination, temporary);
            } catch (UnsupportedOperationException failure) {
                throw new IOException("File system does not support publishing a new GeoPackage without replacing existing files",
                        failure);
            }
        } catch (IOException | SQLException | RuntimeException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        try {
            Files.delete(temporary);
        } catch (IOException failure) {
            throw new IOException("GeoPackage was created but temporary file cleanup failed: " + temporary, failure);
        }
    }

    private static void build(Path file, SchemaPlan plan, CancellationToken token) throws IOException, SQLException {
        var config = new SQLiteConfig();
        config.resetOpenMode(SQLiteOpenMode.CREATE);
        config.enforceForeignKeys(true);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.toUri(), config.toProperties());
             var transaction = connection.createStatement()) {
            transaction.execute("BEGIN IMMEDIATE");
            try {
                bootstrap(transaction, token);
                SchemaSql.createGeoPackageTables(connection, plan, token);
                registerContents(connection, plan, token);
                token.throwIfCancellationRequested();
                transaction.execute("COMMIT");
            } catch (IOException | SQLException | RuntimeException failure) {
                try {
                    transaction.execute("ROLLBACK");
                } catch (SQLException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
    }

    private static void bootstrap(Statement statement, CancellationToken token) throws IOException, SQLException {
        try (var resource = GeoPackageSchema.class.getResourceAsStream("bootstrap-gpkg.sql")) {
            if (resource == null) throw new IOException("Missing GeoPackage bootstrap resource");
            String script = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            for (String sql : script.split(";\\R")) {
                token.throwIfCancellationRequested();
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
    }

    private static void registerContents(Connection connection, SchemaPlan plan, CancellationToken token)
            throws SQLException {
        try (var attributes = connection.prepareStatement("""
                 INSERT INTO gpkg_contents (table_name, data_type, identifier, description)
                 VALUES (?, 'attributes', ?, NULL)
                 """);
             var feature = connection.prepareStatement("""
                 INSERT INTO gpkg_contents
                     (table_name, data_type, identifier, description, min_x, min_y, max_x, max_y, srs_id)
                 VALUES (?, 'features', ?, NULL, ?, ?, ?, ?, ?)
                 """);
             var geometry = connection.prepareStatement("""
                 INSERT INTO gpkg_geometry_columns (table_name, column_name, geometry_type_name, srs_id, z, m)
                 VALUES (?, ?, ?, ?, 0, 0)
                 """);
             var extension = connection.prepareStatement("""
                 INSERT INTO gpkg_extensions (table_name, column_name, extension_name, definition, scope)
                 VALUES (?, ?, ?, 'https://www.geopackage.org/spec131/#extension_geometry_types',
                         'read-write')
                 """)) {
            for (String table : Ili2dbLayout.METADATA_TABLES) {
                token.throwIfCancellationRequested();
                registerAttributes(attributes, table);
            }
            registerAttributes(attributes, Ili2dbLayout.KEY_OBJECT);
            for (var table : plan.mapping().tables()) {
                token.throwIfCancellationRequested();
                var spatial = plan.geometryColumns().get(table.name());
                if (spatial == null) {
                    registerAttributes(attributes, table.name());
                } else {
                    registerFeature(feature, geometry, table.name(), spatial);
                    var kind = spatial.column().kind();
                    if (kind == DbMapping.Kind.POLYLINE || kind == DbMapping.Kind.SURFACE) {
                        extension.setString(1, table.name());
                        extension.setString(2, spatial.column().name());
                        extension.setString(3, "gpkg_geom_" + kind.geometryType());
                        extension.executeUpdate();
                    }
                }
            }
        }
    }

    private static void registerAttributes(PreparedStatement statement, String table) throws SQLException {
        statement.setString(1, table);
        statement.setString(2, table);
        statement.executeUpdate();
    }

    private static void registerFeature(PreparedStatement contents, PreparedStatement geometry, String table,
                                        SchemaPlan.GeometryColumn spatial) throws SQLException {
        var column = spatial.column();
        contents.setString(1, table);
        contents.setString(2, table);
        contents.setObject(3, spatial.minX());
        contents.setObject(4, spatial.minY());
        contents.setObject(5, spatial.maxX());
        contents.setObject(6, spatial.maxY());
        contents.setInt(7, column.srsId());
        contents.executeUpdate();

        geometry.setString(1, table);
        geometry.setString(2, column.name());
        geometry.setString(3, column.kind().geometryType());
        geometry.setInt(4, column.srsId());
        geometry.executeUpdate();
    }
}
