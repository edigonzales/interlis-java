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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import static guru.interlis.db.Ili2dbLayout.MODEL;
import static guru.interlis.db.Ili2dbLayout.MODEL_KEY;
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
        var dialect = postgresDialect(db, plan, token);
        try (var statement = db.connection().createStatement()) {
            statement.execute("CREATE SCHEMA " + quote(schema));
        } catch (SQLException failure) {
            if ("42P06".equals(failure.getSQLState()) || "23505".equals(failure.getSQLState())) {
                throw new DbException("PostgreSQL schema already exists: " + schema, failure);
            }
            throw failure;
        }
        apply(db.connection(), plan, dialect, true, createGeometryIndexes, token);
    }

    private static Dialect postgresDialect(Database db, SchemaPlan plan, CancellationToken token) throws SQLException {
        for (int epsg : plan.geometryEpsgCodes()) {
            token.throwIfCancellationRequested();
            checkPostgresSrs(db, epsg);
        }
        if (plan.addedTables().stream().anyMatch(DbMapping.Table::uuidIdentity)) requireUuidDefault(db.connection());
        return new Dialect(false, db.schema(), db.postgisObject("geometry"), plan.enumOptions());
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

    private static void checkPostgresSrs(Database db, int epsg) throws SQLException {
        String sql = "SELECT srid FROM " + db.postgisObject("spatial_ref_sys")
                + " WHERE srid = ? AND auth_name = 'EPSG' AND auth_srid = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setInt(1, epsg);
            statement.setInt(2, epsg);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new DbException("PostGIS is missing the EPSG:" + epsg + " SRS");
            }
        }
    }

    static void createGeoPackageTables(Connection connection, SchemaPlan plan, boolean createGeometryIndexes,
                                       CancellationToken token)
            throws SQLException {
        var dialect = new Dialect(true, "main", "", plan.enumOptions());
        apply(connection, plan, dialect, true, createGeometryIndexes, token);
    }

    static void addTables(Database db, SchemaPlan plan, boolean createGeometryIndexes, CancellationToken token)
            throws SQLException {
        var dialect = db.gpkg() ? new Dialect(true, "main", "", plan.enumOptions()) : postgresDialect(db, plan, token);
        apply(db.connection(), plan, dialect, false, createGeometryIndexes, token);
    }

    private static void apply(Connection connection, SchemaPlan plan, Dialect dialect, boolean newSchema,
                              boolean createGeometryIndexes, CancellationToken token) throws SQLException {
        try (var statement = connection.createStatement()) {
            for (String sql : ddl(plan, dialect, newSchema, createGeometryIndexes && !dialect.gpkg())) {
                token.throwIfCancellationRequested();
                statement.execute(sql);
            }
        }
        token.throwIfCancellationRequested();
        writeMetadata(connection, plan, dialect, newSchema, createGeometryIndexes, token);
        writeEnumLookups(connection, plan, dialect, newSchema, token);
    }

    private static List<String> ddl(SchemaPlan plan, Dialect dialect, boolean newSchema, boolean createGeometryIndexes) {
        var commands = new ArrayList<String>();
        if (newSchema && !dialect.gpkg()) commands.add("CREATE SEQUENCE " + dialect.table(SEQUENCE));
        for (var lookup : plan.enumLookups()) {
            if (lookup.create()) commands.add(enumTable(lookup, dialect));
        }
        var storage = new java.util.HashSet<>(plan.addedStorageTables());
        for (var table : plan.addedTables()) {
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
                addColumnDefinitions(columns, column, dialect);
                column.positionColumn().ifPresent(position -> columns.add(quote(position) + " " + dialect.idType()));
            }
            for (var composition : plan.addedCompositions()) {
                if (composition.childRoot().equals(table.object().symbol())) {
                    columns.add(quote(composition.ownerColumn()) + " " + dialect.idType());
                }
            }
            commands.add(dialect.create(table.name(), String.join(", ", columns)));
            for (var geometry : table.secondaryGeometries()) {
                commands.add(dialect.create(geometry.table(), dialect.primaryId()
                        + ", " + BASKET_COLUMN + " " + dialect.idType() + " NOT NULL, "
                        + quote(geometry.column().name()) + " " + dialect.columnType(geometry.column(), 0)));
            }
        }
        for (var composition : plan.addedCompositions()) {
            String child = plan.mapping().table(composition.childRoot()).name();
            if (!storage.contains(child)) {
                commands.add("ALTER TABLE " + dialect.table(child) + " ADD COLUMN "
                        + quote(composition.ownerColumn()) + " " + dialect.idType());
            }
        }
        if (createGeometryIndexes) {
            for (String table : plan.addedStorageTables()) {
                for (var geometry : plan.geometryColumns().getOrDefault(table, List.of())) {
                    commands.add("CREATE INDEX ON " + dialect.table(table)
                            + " USING gist (" + quote(geometry.column().name()) + ")");
                }
            }
        }
        if (newSchema) commands.addAll(metadataDdl(dialect));
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

    private static void writeMetadata(Connection connection, SchemaPlan plan, Dialect d, boolean newSchema,
                                      boolean createGeometryIndexes, CancellationToken token)
            throws SQLException {
        try (var models = insert(connection, d, MODEL, "filename, iliversion, modelName, content, importDate", 5)) {
            var imported = Timestamp.from(Instant.now());
            for (var model : plan.modelFiles()) {
                token.throwIfCancellationRequested();
                models.setString(1, model.filename());
                models.setString(2, model.version().literal());
                models.setString(3, model.modelNames());
                models.setString(4, model.content());
                models.setTimestamp(5, imported);
                models.executeUpdate();
            }
        }
        try (var settings = insert(connection, d, SETTINGS, "tag, setting", 2);
             var classes = insert(connection, d, CLASSNAME, "IliName, SqlName", 2);
             var attributes = insert(connection, d, ATTRNAME, "IliName, SqlName, ColOwner, Target", 4);
             var inheritance = insert(connection, d, INHERITANCE, "thisClass, baseClass", 2);
             var transformations = insert(connection, d, TRAFO, "iliname, tag, setting", 3);
             var tableProps = insert(connection, d, TABLE_PROP, "tablename, tag, setting", 3);
             var colProps = insert(connection, d, COLUMN_PROP, "tablename, subtype, columnname, tag, setting", 5)) {
            if (newSchema) {
                writeSettings(settings, d.gpkg(), plan);
                if (createGeometryIndexes) row(settings, Ili2dbLayout.CREATE_GEOMETRY_INDEX, "True");
                for (var declaration : plan.mapping().inheritance().declarations()) {
                    if (declaration.strategy().createsTable()) continue;
                    token.throwIfCancellationRequested();
                    String name = declaration.object().name().declared().toString();
                    row(classes, name, declaration.sqlName());
                    row(inheritance, name, baseClass(plan, declaration.object()));
                    row(transformations, name, INHERITANCE_TAG, declaration.strategy().value);
                }
            }
            for (var lookup : plan.enumLookups()) {
                token.throwIfCancellationRequested();
                if (lookup.create() && lookup.mappedName().isPresent()) {
                    row(classes, lookup.mappedName().orElseThrow(), lookup.table());
                    row(tableProps, lookup.table(), PREFIX + "tableKind", "ENUM");
                }
            }
            var transformedAttributes = new HashSet<String>();
            for (var table : plan.addedTables()) {
                token.throwIfCancellationRequested();
                String name = table.object().name().declared().toString();
                row(classes, name, table.name());
                var base = table.baseTable().map(plan.mapping()::table);
                row(inheritance, name, baseClass(plan, table.object()));
                row(transformations, name, INHERITANCE_TAG, plan.mapping().inheritance().strategy(table.object().symbol()).value);
                if (base.isPresent()) {
                    row(colProps, table.name(), null, ID_COLUMN, PREFIX + "foreignKey",
                            base.orElseThrow().name());
                } else {
                    row(tableProps, table.name(), PREFIX + "tableKind", plan.tableKind(table));
                }
                if (table.hasDiscriminator()) {
                    String types = plan.mapping().objectStorage().stream()
                            .filter(type -> type.root().equals(table) && !type.object().modifiers().contains(Modifier.ABSTRACT))
                            .map(DbMapping.ObjectStorage::discriminator).sorted().collect(Collectors.joining("\",\"", "[\"", "\"]"));
                    row(colProps, table.name(), null, TYPE_COLUMN, PREFIX + "types", types);
                }
                String displayName = plan.displayName(table);
                if (displayName != null) row(tableProps, table.name(), PREFIX + "dispName", displayName);
                for (var column : table.columns()) {
                    token.throwIfCancellationRequested();
                    String target = column.target().map(ignored -> column.targetRelation())
                            .or(() -> column.array().flatMap(DbArray::target))
                            .map(relation -> plan.mapping().table(relation).name()).orElse(null);
                    String path = Ili2dbLayout.geometryAttribute(column.slot().name().declared().toString(), column.epsgCode(), plan.multiSrs());
                    if (column.expandedStructure().isPresent()) {
                        writeExpandedAttributes(attributes, table.name(), column.expandedStructure().orElseThrow());
                    } else {
                        row(attributes, path, column.name(), table.name(), target);
                    }
                    writeAttributeTransformation(transformations, transformedAttributes, column);
                }
                for (var secondary : table.secondaryGeometries()) {
                    var column = secondary.column();
                    String path = column.slot().name().declared().toString();
                    String attribute = Ili2dbLayout.geometryAttribute(path, column.epsgCode(), plan.multiSrs());
                    row(classes, Ili2dbLayout.secondaryClass(attribute, name), secondary.table());
                    row(transformations, Ili2dbLayout.secondaryTransformation(path, name, column.epsgCode()),
                            Ili2dbLayout.SECONDARY_TABLE_TAG, secondary.table());
                    row(attributes, attribute, column.name(), secondary.table(), null);
                    writeAttributeTransformation(transformations, transformedAttributes, column);
                }
            }
            for (var composition : plan.addedCompositions()) {
                token.throwIfCancellationRequested();
                String parent = plan.mapping().table(composition.parent()).name();
                String child = plan.mapping().table(composition.childRoot()).name();
                row(attributes, composition.slot().name().declared().toString(), composition.ownerColumn(), child, parent);
            }
            for (var association : plan.addedAssociations()) {
                token.throwIfCancellationRequested();
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

    private static String enumTable(DbEnumLookup lookup, Dialect dialect) {
        String code = "iliCode " + dialect.text(1024) + " NOT NULL";
        String sequence = "seq INTEGER, inactive BOOLEAN NOT NULL";
        String label = ", dispName " + dialect.text(250) + " NOT NULL, description TEXT";
        String definition = "thisClass " + dialect.text(1024) + " NOT NULL, baseClass " + dialect.text(1024);
        String columns = switch (dialect.enumOptions().lookup()) {
            case SINGLE -> definition + ", " + sequence + ", " + code + ", itfCode INTEGER NOT NULL";
            case PER_DEFINITION -> "itfCode INTEGER" + dialect.primary() + ", " + code + ", " + sequence;
            case WITH_ID -> dialect.primaryId() + dialect.sequenceDefault() + ", " + definition
                    + ", itfCode INTEGER NOT NULL, " + code + ", " + sequence;
            case NONE -> throw new IllegalStateException("No enumeration lookup table was planned");
        };
        return dialect.create(lookup.table(), columns + label);
    }

    private static void writeEnumLookups(Connection connection, SchemaPlan plan, Dialect dialect, boolean newSchema,
                                         CancellationToken token) throws SQLException {
        for (var lookup : plan.enumLookups()) {
            boolean shared = plan.enumOptions().lookup() != DbEnumOptions.Lookup.PER_DEFINITION;
            if (!lookup.create() && !shared) continue;
            String table = dialect.table(lookup.table());
            boolean add = shared && !newSchema && !lookup.create();
            String sql = "INSERT INTO " + table + " (iliCode, itfCode, seq, dispName, description, inactive"
                    + (shared ? ", thisClass, baseClass)" : ")")
                    + (add ? " SELECT ?, ?, ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM " + table
                            + " WHERE thisClass = ? AND iliCode = ?)"
                            : shared ? " VALUES (?, ?, ?, ?, ?, ?, ?, ?)" : " VALUES (?, ?, ?, ?, ?, ?)");
            try (var statement = connection.prepareStatement(sql)) {
                for (var row : lookup.rows()) {
                    token.throwIfCancellationRequested();
                    statement.setString(1, row.path());
                    statement.setInt(2, row.itfCode());
                    statement.setObject(3, row.sequence(), java.sql.Types.INTEGER);
                    statement.setString(4, row.label());
                    statement.setString(5, row.documentation());
                    statement.setBoolean(6, false);
                    if (shared) {
                        statement.setString(7, row.definition());
                        statement.setString(8, row.base());
                    }
                    if (add) {
                        statement.setString(9, row.definition());
                        statement.setString(10, row.path());
                    }
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException failure) {
                throw new DbException("Cannot write enumeration lookup rows for " + lookup.table(), failure);
            }
        }
    }

    private static void addColumnDefinitions(List<String> columns, DbMapping.Column column, Dialect dialect) {
        if (column.expandedStructure().isPresent()) {
            for (var group : column.expandedStructure().orElseThrow().groups()) {
                columns.add(quote(group.typeColumn()) + " " + dialect.text(60));
                for (var field : group.fields()) addColumnDefinitions(columns, field, dialect);
            }
        } else {
            var names = column.physicalNames();
            for (int i = 0; i < names.size(); i++) {
                columns.add(quote(names.get(i)) + " " + dialect.columnType(column, i));
            }
        }
    }

    private static void writeExpandedAttributes(PreparedStatement statement, String table, DbExpandedStructure expansion)
            throws SQLException {
        for (var group : expansion.groups()) {
            row(statement, group.path() + "._type", group.typeColumn(), table, null);
            for (var field : group.fields()) {
                if (field.expandedStructure().isPresent()) {
                    writeExpandedAttributes(statement, table, field.expandedStructure().orElseThrow());
                } else {
                    row(statement, group.path() + "." + field.slot().name().declared().last(), field.name(), table, null);
                }
            }
        }
    }

    private static void writeAttributeTransformation(PreparedStatement statement, Set<String> used,
                                                      DbMapping.Column column) throws SQLException {
        var transformation = column.transformation();
        String path = column.slot().name().declared().toString();
        if (transformation != null && used.add(path)) row(statement, path, transformation.tag, transformation.setting);
    }

    private static String baseClass(SchemaPlan plan, guru.interlis.transfer.ObjectDescriptor type) {
        return type.baseType().flatMap(plan.mapping().plan()::object)
                .map(value -> value.name().declared().toString()).orElse(null);
    }

    private static void writeSettings(PreparedStatement statement, boolean gpkg, SchemaPlan plan) throws SQLException {
        row(statement, PREFIX + "sender", SCHEMA_SENDER);
        row(statement, PREFIX + "BasketHandling", "readWrite");
        row(statement, PREFIX + "TidHandling", "property");
        row(statement, PREFIX + "SqlNull", "enable");
        row(statement, PREFIX + "defaultSrsAuthority", "EPSG");
        row(statement, PREFIX + "defaultSrsCode", Integer.toString(plan.defaultEpsg()));
        row(statement, PREFIX + "maxSqlNameLength", "60");
        if (plan.enumOptions().itfCodes()) row(statement, Ili2dbLayout.ENUM_ITF_CODE, "yes");
        if (plan.enumOptions().textColumns()) row(statement, Ili2dbLayout.ENUM_TEXT_COLUMN, "addTxtCol");
        if (plan.enumOptions().beautify()) row(statement, Ili2dbLayout.ENUM_BEAUTIFY, "underscore");
        if (plan.enumOptions().lookup() != DbEnumOptions.Lookup.NONE) {
            row(statement, Ili2dbLayout.ENUM_DEFINITIONS, plan.enumOptions().lookup().setting);
        }
        String inheritance = plan.mapping().profile().setting;
        if (inheritance != null) row(statement, PREFIX + "inheritanceTrafo", inheritance);
        for (var transformation : Ili2dbLayout.AttributeTransformation.values()) {
            if (plan.transformations().contains(transformation)) row(statement, transformation.tag, transformation.setting);
        }
        row(statement, "ch.interlis.ili2c.ilidirs", "%ILI_FROM_DB");
        if (plan.multiSrs()) row(statement, Ili2dbLayout.USE_EPSG_IN_NAMES, "True");
        if (plan.strokeArcs()) row(statement, Ili2dbLayout.STROKE_ARCS, Ili2dbLayout.STROKE_ARCS_ENABLED);
        if (plan.mapping().crsPair().isPresent()) {
            row(statement, Ili2dbLayout.SRS_MODEL_ASSIGNMENT, plan.mapping().crsPair().orElseThrow().assignment().setting());
        }
        if (plan.splitGeometries() || gpkg) {
            row(statement, Ili2dbLayout.GEOMETRY_LAYOUT, plan.splitGeometries()
                    ? Ili2dbLayout.SINGLE_GEOMETRY : Ili2dbLayout.MULTIPLE_GEOMETRIES);
        }
        if (!gpkg) row(statement, PREFIX + "uuidDefaultValue", UUID_DEFAULT);
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

    private record Dialect(boolean gpkg, String schema, String postgisGeometryType, DbEnumOptions enumOptions) {
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

        String columnType(DbMapping.Column column, int index) {
            if (index == 1 && column.enumText().isPresent()) return text(255);
            if (column.array().isPresent()) {
                if (gpkg) return "TEXT";
                var array = column.array().orElseThrow();
                String element = switch (array.kind()) {
                    case TEXT -> array.textLength() == 0 ? "TEXT" : text(array.textLength());
                    case ENUMERATION -> enumOptions.numeric() ? idType() : text(255);
                    case BOOLEAN -> "BOOLEAN";
                    case INTEGER -> "INTEGER";
                    case REFERENCE -> "BIGINT";
                    default -> throw new IllegalStateException("Unsupported SQL array element " + array.kind());
                };
                return element + "[]";
            }
            if (column.jsonStructure().isPresent()) return gpkg ? "TEXT" : "jsonb";
            if (column.textExpansion().isPresent()) {
                return column.textExpansion().orElseThrow().translations().isEmpty() && index == 1 ? text(2) : "TEXT";
            }
            return switch (column.kind()) {
                case BOOLEAN -> "BOOLEAN";
                case INTEGER -> "INTEGER";
                case BIGINT -> idType();
                case DATE -> "DATE";
                case TIME -> "TIME";
                case DATETIME -> timestamp();
                case FORMATTED -> text(255);
                case DECIMAL -> {
                    var decimal = DbDecimal.describe((guru.interlis.model.NumericType) column.effectiveType().orElseThrow());
                    yield gpkg ? "DOUBLE" : "numeric(" + decimal.precision() + "," + decimal.scale() + ")";
                }
                case ENUMERATION -> column.enumeration().isPresent() || enumOptions.lookup() == DbEnumOptions.Lookup.WITH_ID
                        ? idType() : text(255);
                case REFERENCE -> idType();
                case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE -> {
                    String type = column.geometryType();
                    yield gpkg ? type : postgisGeometryType + "(" + type
                            + (column.dimensions() == 3 ? "Z" : "") + ", " + column.srsId() + ")";
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
