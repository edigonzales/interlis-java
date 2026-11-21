package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.Modifier;
import guru.interlis.model.TextType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.Database.quote;
import static guru.interlis.db.Ili2dbLayout.ATTRNAME;
import static guru.interlis.db.Ili2dbLayout.ATTRNAME_KEY;
import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.BASKET_COLUMN;
import static guru.interlis.db.Ili2dbLayout.CLASSNAME;
import static guru.interlis.db.Ili2dbLayout.COLUMN_PROP;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.DATASET_KEY;
import static guru.interlis.db.Ili2dbLayout.EMBEDDED;
import static guru.interlis.db.Ili2dbLayout.ID_COLUMN;
import static guru.interlis.db.Ili2dbLayout.INHERITANCE;
import static guru.interlis.db.Ili2dbLayout.INHERITANCE_TAG;
import static guru.interlis.db.Ili2dbLayout.KEY_OBJECT;
import static guru.interlis.db.Ili2dbLayout.LV95;
import static guru.interlis.db.Ili2dbLayout.MODEL;
import static guru.interlis.db.Ili2dbLayout.MODEL_KEY;
import static guru.interlis.db.Ili2dbLayout.NEW_CLASS;
import static guru.interlis.db.Ili2dbLayout.PREFIX;
import static guru.interlis.db.Ili2dbLayout.SCHEMA_SENDER;
import static guru.interlis.db.Ili2dbLayout.SEQUENCE;
import static guru.interlis.db.Ili2dbLayout.SEQ_COLUMN;
import static guru.interlis.db.Ili2dbLayout.SETTINGS;
import static guru.interlis.db.Ili2dbLayout.TABLE_PROP;
import static guru.interlis.db.Ili2dbLayout.TID_COLUMN;
import static guru.interlis.db.Ili2dbLayout.TRAFO;
import static guru.interlis.db.Ili2dbLayout.TYPE_COLUMN;

/** DDL and parameterized ili2db metadata for the 5.5.2 profile. */
final class SchemaSql {
    private static final String UUID_DEFAULT = "uuid_generate_v4()";

    private SchemaSql() { }

    static void createPostgres(Database db, SchemaPlan plan, boolean createGeometryIndexes, CancellationToken token) throws SQLException {
        String schema = db.schema();
        checkPostgresSchemaName(db.connection(), schema);
        if (!plan.geometryColumns().isEmpty()) {
            checkPostgresSrs(db);
        }
        if (plan.mapping().tables().stream().anyMatch(DbMapping.Table::uuidIdentity)) {
            requireUuidDefault(db.connection());
        }
        var dialect = new Dialect(false, schema, db.postgisObject("geometry"));
        var ddl = ddl(plan, dialect, createGeometryIndexes);
        try (var statement = db.connection().createStatement()) {
            statement.execute("CREATE SCHEMA " + quote(schema));
        } catch (SQLException failure) {
            if ("42P06".equals(failure.getSQLState()) || "23505".equals(failure.getSQLState())) {
                throw new DbException("PostgreSQL schema already exists: " + schema, failure);
            }
            throw failure;
        }
        apply(db.connection(), plan, dialect, ddl, createGeometryIndexes, token);
    }

    private static void checkPostgresSchemaName(Connection connection, String schema) throws SQLException {
        // ili2pg addresses the target schema using an unquoted identifier.
        if (schema.length() > 63 || !schema.matches("[a-z_][a-z0-9_$]*")) {
            throw new DbException("Expected a lowercase unquoted PostgreSQL schema name of at most 63 characters");
        }
        try (var statement = connection.prepareStatement(
                "SELECT word FROM pg_catalog.pg_get_keywords() WHERE word = ? AND catcode IN ('R', 'T')")) {
            statement.setString(1, schema);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) throw new DbException("PostgreSQL schema name is a reserved word: " + schema);
            }
        }
    }

    private static void requireUuidDefault(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT pg_catalog.to_regprocedure(?)")) {
            statement.setString(1, UUID_DEFAULT);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getString(1) == null) {
                    throw new DbException("UUID identity columns require uuid_generate_v4() from the PostgreSQL uuid-ossp extension");
                }
            }
        }
    }

    private static void checkPostgresSrs(Database db) throws SQLException {
        String sql = "SELECT srid FROM " + db.postgisObject("spatial_ref_sys")
                + " WHERE srid = ? AND auth_name = 'EPSG' AND auth_srid = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setInt(1, LV95);
            statement.setInt(2, LV95);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new DbException("PostGIS is missing the EPSG:2056 SRS");
            }
        }
    }

    static void createGeoPackageTables(Connection connection, SchemaPlan plan, boolean createGeometryIndexes,
                                       CancellationToken token)
            throws SQLException {
        var dialect = new Dialect(true, "main", "");
        apply(connection, plan, dialect, ddl(plan, dialect, false), createGeometryIndexes, token);
    }

    private static void apply(Connection connection, SchemaPlan plan, Dialect dialect, List<String> ddl,
                              boolean createGeometryIndexes, CancellationToken token) throws SQLException {
        try (var statement = connection.createStatement()) {
            for (String sql : ddl) {
                token.throwIfCancellationRequested();
                statement.execute(sql);
            }
        }
        token.throwIfCancellationRequested();
        writeMetadata(connection, plan, dialect, createGeometryIndexes, token);
    }

    private static List<String> ddl(SchemaPlan plan, Dialect dialect, boolean createGeometryIndexes) {
        var commands = new ArrayList<String>();
        if (!dialect.gpkg()) commands.add("CREATE SEQUENCE " + dialect.table(SEQUENCE));
        for (var table : plan.mapping().tables()) {
            var columns = new ArrayList<String>();
            columns.add(dialect.primaryId() + (table.subtype() ? "" : dialect.sequenceDefault()));
            columns.add(BASKET_COLUMN + " " + dialect.idType() + " NOT NULL");
            if (table.hasDiscriminator()) {
                columns.add(TYPE_COLUMN + " " + dialect.text(60) + " NOT NULL");
            }
            if (table.hasTidColumn()) {
                columns.add(TID_COLUMN + " " + dialect.tidType(table.uuidIdentity()));
            }
            if (table.hasSequenceColumn()) {
                columns.add(SEQ_COLUMN + " " + dialect.idType());
            }
            for (var column : table.columns()) {
                columns.add(quote(column.name()) + " " + dialect.columnType(column));
                column.positionColumn().ifPresent(position -> columns.add(quote(position) + " " + dialect.idType()));
            }
            for (var composition : plan.mapping().compositionsForChild(table.object().symbol())) {
                columns.add(quote(composition.ownerColumn()) + " " + dialect.idType());
            }
            commands.add(dialect.create(table.name(), String.join(", ", columns)));
            var geometry = plan.geometryColumns().get(table.name());
            if (createGeometryIndexes && geometry != null) {
                commands.add("CREATE INDEX ON " + dialect.table(table.name())
                        + " USING gist (" + quote(geometry.column().name()) + ")");
            }
        }
        commands.addAll(metadataDdl(dialect));
        return List.copyOf(commands);
    }

    private static List<String> metadataDdl(Dialect d) {
        String t200 = d.text(200);
        String t255 = d.text(255);
        String t1024 = d.text(1024);
        String t8000 = d.text(8000);
        var ddl = new ArrayList<>(List.of(
                d.create(BASKET, d.primaryId() + ", dataset " + d.idType() + ", topic " + t200
                        + " NOT NULL, " + TID_COLUMN + " " + t200 + ", attachmentKey " + t200 + " NOT NULL, domains " + t1024),
                d.create(DATASET, d.primaryId() + ", datasetName " + t200
                        + d.uniqueConstraint(DATASET_KEY, "datasetName")),
                d.create(INHERITANCE, "thisClass " + t1024 + d.primary() + ", baseClass " + t1024),
                d.create(SETTINGS, "tag " + d.text(60) + d.primary() + ", setting " + t8000),
                d.create(TRAFO, "iliname " + t1024 + " NOT NULL, tag " + t1024
                        + " NOT NULL, setting " + t1024 + " NOT NULL"),
                d.create(MODEL, "filename " + d.text(250) + " NOT NULL, iliversion " + d.text(3)
                        + " NOT NULL, modelName TEXT NOT NULL, content TEXT NOT NULL, importDate " + d.timestamp()
                        + " NOT NULL" + d.uniqueConstraint(MODEL_KEY, "modelName, iliversion")
                        + ", PRIMARY KEY (modelName, iliversion)"),
                d.create(CLASSNAME, "IliName " + t1024 + d.primary() + ", SqlName " + t1024 + " NOT NULL"),
                d.create(ATTRNAME, "IliName " + t1024 + " NOT NULL, SqlName " + t1024 + " NOT NULL, ColOwner " + t1024
                        + " NOT NULL, Target " + t1024 + d.uniqueConstraint(ATTRNAME_KEY, "ColOwner, SqlName")
                        + ", PRIMARY KEY (ColOwner, SqlName)"),
                d.create(COLUMN_PROP, "tablename " + t255 + " NOT NULL, subtype " + t255 + ", columnname " + t255
                        + " NOT NULL, tag " + t1024 + " NOT NULL, setting " + t8000 + " NOT NULL"),
                d.create(TABLE_PROP, "tablename " + t255 + " NOT NULL, tag " + t1024 + " NOT NULL, setting "
                        + t8000 + " NOT NULL")
        ));
        if (d.gpkg()) {
            ddl.add(d.create(KEY_OBJECT, "T_Key " + d.text(30) + d.primary() + ", T_LastUniqueId INTEGER NOT NULL"
                    + ", T_LastChange DATETIME NOT NULL, T_CreateDate DATETIME NOT NULL, T_User " + d.text(40) + " NOT NULL"));
        } else {
            ddl.add(d.uniqueIndex(DATASET_KEY, DATASET, "datasetName"));
            ddl.add(d.uniqueIndex(MODEL_KEY, MODEL, "modelName, iliversion"));
            ddl.add(d.uniqueIndex(ATTRNAME_KEY, ATTRNAME, "ColOwner, SqlName"));
        }
        return ddl;
    }

    private static void writeMetadata(Connection connection, SchemaPlan plan, Dialect d, boolean createGeometryIndexes,
                                      CancellationToken token)
            throws SQLException {
        try (var models = insert(connection, d, MODEL, "filename, iliversion, modelName, content, importDate", 5)) {
            models.setString(1, plan.filename());
            models.setString(2, plan.mapping().version().literal());
            models.setString(3, plan.modelNames());
            models.setString(4, plan.content());
            models.setTimestamp(5, Timestamp.from(Instant.now()));
            models.executeUpdate();
        }
        try (var settings = insert(connection, d, SETTINGS, "tag, setting", 2);
             var classes = insert(connection, d, CLASSNAME, "IliName, SqlName", 2);
             var attributes = insert(connection, d, ATTRNAME, "IliName, SqlName, ColOwner, Target", 4);
             var inheritance = insert(connection, d, INHERITANCE, "thisClass, baseClass", 2);
             var transformations = insert(connection, d, TRAFO, "iliname, tag, setting", 3);
             var tableProps = insert(connection, d, TABLE_PROP, "tablename, tag, setting", 3);
             var colProps = insert(connection, d, COLUMN_PROP, "tablename, subtype, columnname, tag, setting", 5)) {
            writeSettings(settings, d.gpkg());
            if (createGeometryIndexes) row(settings, "ch.ehi.sqlgen.createGeomIndex", "True");
            for (var table : plan.mapping().tables()) {
                token.throwIfCancellationRequested();
                String name = table.object().name().declared().toString();
                row(classes, name, table.name());
                var base = table.object().baseType().map(plan.mapping()::table);
                row(inheritance, name, base.map(value -> value.object().name().declared().toString()).orElse(null));
                row(transformations, name, INHERITANCE_TAG, NEW_CLASS);
                if (base.isPresent()) {
                    row(colProps, table.name(), null, ID_COLUMN, PREFIX + "foreignKey",
                            base.orElseThrow().name());
                } else {
                    row(tableProps, table.name(), PREFIX + "tableKind", table.object().kind().name());
                }
                if (table.hasDiscriminator()) {
                    String types = plan.mapping().hierarchy(table).stream()
                            .filter(type -> !type.object().modifiers().contains(Modifier.ABSTRACT))
                            .map(DbMapping.Table::name).sorted().collect(Collectors.joining("\",\"", "[\"", "\"]"));
                    row(colProps, table.name(), null, TYPE_COLUMN, PREFIX + "types", types);
                }
                String displayName = plan.displayName(table);
                if (displayName != null) row(tableProps, table.name(), PREFIX + "dispName", displayName);
                for (var column : table.columns()) {
                    token.throwIfCancellationRequested();
                    String target = column.target().map(plan.mapping()::table).map(DbMapping.Table::name).orElse(null);
                    row(attributes, column.slot().name().declared().toString(), column.name(), table.name(), target);
                }
                for (var composition : plan.mapping().compositionsForChild(table.object().symbol())) {
                    token.throwIfCancellationRequested();
                    String parent = plan.mapping().table(composition.parent()).name();
                    row(attributes, composition.slot().name().declared().toString(), composition.ownerColumn(),
                            table.name(), parent);
                }
            }
            for (var association : plan.mapping().associations()) {
                token.throwIfCancellationRequested();
                if (association.linkTable()) continue;
                String name = association.object().name().declared().toString();
                row(classes, name, association.name());
                row(inheritance, name, null);
                row(transformations, name, INHERITANCE_TAG, EMBEDDED);
            }
            for (var property : plan.columnProperties()) {
                token.throwIfCancellationRequested();
                row(colProps, property.table(), property.subtype(), property.column(), property.key(), property.value());
            }
        }
    }

    private static void writeSettings(PreparedStatement statement, boolean gpkg) throws SQLException {
        row(statement, PREFIX + "sender", SCHEMA_SENDER);
        row(statement, PREFIX + "BasketHandling", "readWrite");
        row(statement, PREFIX + "TidHandling", "property");
        row(statement, PREFIX + "SqlNull", "enable");
        row(statement, PREFIX + "defaultSrsAuthority", "EPSG");
        row(statement, PREFIX + "defaultSrsCode", Integer.toString(LV95));
        row(statement, PREFIX + "maxSqlNameLength", "60");
        row(statement, "ch.interlis.ili2c.ilidirs", "%ILI_FROM_DB");
        if (gpkg) {
            row(statement, PREFIX + "geomAttrPerTable", "oneGeomAttrPerTable");
        } else {
            row(statement, PREFIX + "uuidDefaultValue", UUID_DEFAULT);
        }
    }

    private static PreparedStatement insert(Connection connection, Dialect dialect, String table, String columns,
                                            int parameters) throws SQLException {
        String values = "?, ".repeat(parameters - 1) + "?";
        return connection.prepareStatement("INSERT INTO " + dialect.table(table) + " (" + columns + ") VALUES (" + values + ")");
    }

    private static void row(PreparedStatement statement, String... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setString(index + 1, values[index]);
        }
        statement.executeUpdate();
    }

    private record Dialect(boolean gpkg, String schema, String postgisGeometryType) {
        String tidType(boolean uuid) {
            if (!uuid) return text(200);
            return gpkg ? text(36) : "uuid DEFAULT " + UUID_DEFAULT;
        }

        String table(String name) {
            return quote(schema) + "." + quote(Ili2dbLayout.tableName(name, gpkg));
        }

        String create(String table, String columns) {
            return "CREATE TABLE " + table(table) + " (" + columns + ")";
        }

        String primaryId() {
            return ID_COLUMN + " " + idType() + primary();
        }

        String primary() {
            return gpkg ? " NOT NULL PRIMARY KEY" : " PRIMARY KEY";
        }

        String idType() {
            return gpkg ? "INTEGER" : "bigint";
        }

        String text(int length) {
            return (gpkg ? "TEXT" : "varchar") + "(" + length + ")";
        }

        String timestamp() {
            return gpkg ? "DATETIME" : "timestamp";
        }

        String sequenceDefault() {
            if (gpkg) return "";
            String name = table(SEQUENCE).replace("\\", "\\\\").replace("'", "''");
            return " DEFAULT pg_catalog.nextval(E'" + name + "'::pg_catalog.regclass)";
        }

        String uniqueConstraint(String name, String columns) {
            return gpkg ? ", CONSTRAINT " + quote(name) + " UNIQUE (" + columns + ")" : "";
        }

        String uniqueIndex(String name, String table, String columns) {
            return "CREATE UNIQUE INDEX " + quote(Ili2dbLayout.foldedName(name)) + " ON " + table(table)
                    + " (" + columns + ")";
        }

        String columnType(DbMapping.Column column) {
            return switch (column.kind()) {
                case BOOLEAN -> "BOOLEAN";
                case INTEGER -> "INTEGER";
                case ENUMERATION -> text(255);
                case REFERENCE -> idType();
                case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE -> {
                    String type = column.kind().geometryType();
                    yield gpkg ? type : postgisGeometryType + "(" + type + ", " + column.srsId() + ")";
                }
                case TEXT -> {
                    var type = (TextType) column.effectiveType().orElseThrow();
                    int length = type.maxLength().orElse(0);
                    yield length > 0 ? text(length) : "TEXT";
                }
            };
        }
    }
}
