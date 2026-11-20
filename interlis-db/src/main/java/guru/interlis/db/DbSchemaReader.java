package guru.interlis.db;

import guru.interlis.db.DbMapping.Column;
import guru.interlis.db.DbMapping.Kind;
import guru.interlis.db.DbMapping.Table;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static guru.interlis.db.DbMapping.unsupported;
import static guru.interlis.db.Ili2dbLayout.ATTRNAME;
import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.CLASSNAME;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.LV95;
import static guru.interlis.db.Ili2dbLayout.MAX_MODEL_CHARACTERS;
import static guru.interlis.db.Ili2dbLayout.MODEL;
import static guru.interlis.db.Ili2dbLayout.PREFIX;
import static guru.interlis.db.Ili2dbLayout.SCHEMA_SENDER;
import static guru.interlis.db.Ili2dbLayout.SETTINGS;
import static guru.interlis.db.Ili2dbLayout.TECHNICAL_COLUMNS;
import static guru.interlis.db.Ili2dbLayout.TRAFO;
import static guru.interlis.db.Ili2dbLayout.T_BASKET;
import static guru.interlis.db.Ili2dbLayout.T_ID;
import static guru.interlis.db.Ili2dbLayout.T_ILI_TID;
import static guru.interlis.db.Ili2dbLayout.foldedName;

/** Binds a checked model to the mappings and physical catalog of an existing ili2db schema. */
final class DbSchemaReader {
    private record StoredModel(LanguageVersion version, String content) { }
    private record AttributeName(String name, String owner, String target) { }

    private DbSchemaReader() { }

    static DbMapping read(Database db, String modelName, CancellationToken token) throws SQLException {
        token.throwIfCancellationRequested();
        checkSettings(db);
        StoredModel source = readModel(db, modelName);
        TransferPlan plan = DbMapping.compile(modelName, new SourceText(new SourceId("database:/model.ili"),
                new SourceRevision(1), source.content()), "MODEL.content", token);
        if (!plan.schema().languageVersions().equals(Set.of(source.version()))) {
            throw new DbException("MODEL.iliversion disagrees with MODEL.content for " + modelName);
        }

        String prefix = modelName + ".";
        var classNames = readClassNames(db, prefix);
        var attrNames = readAttributeNames(db, prefix);
        var newClasses = readInheritance(db, prefix);
        var names = new HashMap<SymbolRef, String>();
        var physicalNames = new HashSet<String>();
        for (ObjectDescriptor object : plan.objects()) {
            String path = object.name().declared().toString();
            String name = classNames.remove(path);
            if (name == null || !newClasses.remove(path) || !physicalNames.add(key(db, name))) {
                throw new DbException("Missing or inconsistent CLASSNAME/TRAFO mapping for " + path);
            }
            names.put(object.symbol(), name);
        }
        if (!classNames.isEmpty() || !newClasses.isEmpty()) {
            throw new DbException("Extra CLASSNAME/TRAFO mappings for " + modelName);
        }
        checkSystemTables(db);
        var tables = new ArrayList<Table>();
        for (ObjectDescriptor object : plan.objects()) {
            token.throwIfCancellationRequested();
            String name = names.get(object.symbol());
            var physical = physicalColumns(db, name);
            requireColumns(db, name, physical, Map.of(
                    T_ID, Kind.REFERENCE, T_BASKET, Kind.REFERENCE, T_ILI_TID, Kind.TEXT));
            requireIdPrimaryKey(db, name);
            var used = new HashSet<>(TECHNICAL_COLUMNS);
            var columns = new ArrayList<Column>();
            for (var attr : DbMapping.attributes(object)) {
                columns.add(bindColumn(db, plan, object, name, names, attr, attrNames, physical, used));
            }
            if (!physical.keySet().equals(used)) {
                throw new DbException("Unexpected physical columns in " + name);
            }
            tables.add(new Table(object, name, columns));
        }
        if (!attrNames.isEmpty()) throw new DbException("Extra ATTRNAME mappings for " + modelName);
        return new DbMapping(plan, source.version(), tables);
    }

    private static void checkSettings(Database db) throws SQLException {
        // Both backends accept the folded technical names. Keep the same names in catalog diagnostics.
        String table = foldedName(SETTINGS);
        requireMetadataColumns(db, table, "tag", "setting");
        var settings = new HashMap<String, String>();
        try (var stmt = db.connection().createStatement();
             var rows = stmt.executeQuery("SELECT tag, setting FROM " + db.table(table))) {
            while (rows.next()) {
                String tag = required(rows, 1, "SETTINGS.tag");
                if (settings.containsKey(tag)) throw new DbException("Duplicate SETTINGS entry for " + tag);
                settings.put(tag, rows.getString(2));
            }
        }
        String sender = settings.get(PREFIX + "sender");
        String tool = db.gpkg() ? "ili2gpkg" : "ili2pg";
        if (!SCHEMA_SENDER.equals(sender) && !isSender(sender, tool + "-5.5.1") && !isSender(sender, tool + "-5.5.2")) {
            throw unsupported("expected a supported schema created by interlis-db or " + tool + " 5.5.1 or 5.5.2");
        }
        requireSetting(settings, "BasketHandling", "readWrite");
        requireSetting(settings, "TidHandling", "property");
        requireSetting(settings, "SqlNull", "enable");
        requireSetting(settings, "defaultSrsAuthority", "EPSG");
        requireSetting(settings, "defaultSrsCode", Integer.toString(LV95));
        for (String tag : List.of("inheritanceTrafo", "createEnumColAsItfCode", "createEnumCols",
                "createDatasetCols", "typeDiscriminator", "createStdCols", "SqlColsAsText", "SqlExtRefCols")) {
            if (settings.containsKey(PREFIX + tag)) throw unsupported("SETTINGS." + tag);
        }
        if ("multiTableWithId".equals(settings.get(PREFIX + "createEnumDefs"))) {
            throw unsupported("enumerations stored as SQL IDs");
        }
    }

    private static boolean isSender(String sender, String version) {
        return sender != null && (sender.equals(version) || sender.startsWith(version + "-"));
    }

    private static void requireSetting(Map<String, String> settings, String tag, String value) {
        if (!value.equals(settings.get(PREFIX + tag))) {
            throw unsupported("SETTINGS must contain " + PREFIX + tag + "=" + value);
        }
    }

    private static StoredModel readModel(Database db, String modelName) throws SQLException {
        String table = foldedName(MODEL);
        requireMetadataColumns(db, table, "modelname", "iliversion", "content");
        // modelname lists definitions followed by optional brace-delimited imports for each file.
        String contains = db.gpkg() ? "instr" : "strpos";
        String sql = "SELECT modelname, iliversion, CASE WHEN length(content) <= ? THEN content END FROM "
                + db.table(table) + " WHERE " + contains + "(modelname, ?) > 0";
        StoredModel match = null;
        try (var stmt = db.connection().prepareStatement(sql)) {
            stmt.setInt(1, MAX_MODEL_CHARACTERS);
            stmt.setString(2, modelName);
            try (var rows = stmt.executeQuery()) {
                while (rows.next()) {
                    String names = required(rows, 1, "MODEL.modelname");
                    if (!List.of(names.replaceAll("\\{[^{}]*}", "").strip().split("\\s+")).contains(modelName)) {
                        continue;
                    }
                    if (match != null) throw new DbException("Multiple MODEL sources for " + modelName);
                    var version = LanguageVersion.fromLiteral(rows.getString(2))
                            .orElseThrow(() -> unsupported("MODEL.iliversion for " + modelName));
                    String content = rows.getString(3);
                    if (content == null || content.isBlank()) {
                        throw new DbException("MODEL.content is missing or exceeds the size limit for " + modelName);
                    }
                    match = new StoredModel(version, content);
                }
            }
        }
        if (match == null) throw new DbException("No MODEL source for " + modelName);
        return match;
    }

    private static Map<String, String> readClassNames(Database db, String prefix) throws SQLException {
        String table = foldedName(CLASSNAME);
        requireMetadataColumns(db, table, "iliname", "sqlname");
        var result = new LinkedHashMap<String, String>();
        try (var stmt = scopedMetadata(db, table, "iliname, sqlname", prefix);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "CLASSNAME.iliname");
                if (result.putIfAbsent(path, required(rows, 2, "CLASSNAME.sqlname")) != null) {
                    throw new DbException("Duplicate CLASSNAME mapping for " + path);
                }
            }
        }
        return result;
    }

    private static Map<String, AttributeName> readAttributeNames(Database db, String prefix) throws SQLException {
        String table = foldedName(ATTRNAME);
        requireMetadataColumns(db, table, "iliname", "sqlname", "colowner", "target");
        var result = new LinkedHashMap<String, AttributeName>();
        try (var stmt = scopedMetadata(db, table, "iliname, sqlname, colowner, target", prefix);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "ATTRNAME.iliname");
                var name = new AttributeName(required(rows, 2, "ATTRNAME.sqlname"),
                        required(rows, 3, "ATTRNAME.colowner"), rows.getString(4));
                if (result.putIfAbsent(path, name) != null) {
                    throw new DbException("Ambiguous ATTRNAME mapping for " + path);
                }
            }
        }
        return result;
    }

    private static Set<String> readInheritance(Database db, String prefix) throws SQLException {
        String table = foldedName(TRAFO);
        requireMetadataColumns(db, table, "iliname", "tag", "setting");
        var result = new HashSet<String>();
        try (var stmt = scopedMetadata(db, table, "iliname, tag, setting", prefix);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "TRAFO.iliname");
                if (!(PREFIX + "inheritance").equals(rows.getString(2)) || !"newClass".equals(rows.getString(3))
                        || !result.add(path)) {
                    throw unsupported("TRAFO entries for " + path);
                }
            }
        }
        return result;
    }

    private static PreparedStatement scopedMetadata(Database db, String table, String columns, String prefix)
            throws SQLException {
        var stmt = db.connection().prepareStatement("SELECT " + columns + " FROM " + db.table(table)
                + " WHERE substr(iliname, 1, ?) = ?");
        try {
            stmt.setInt(1, prefix.codePointCount(0, prefix.length()));
            stmt.setString(2, prefix);
            return stmt;
        } catch (SQLException failure) {
            try {
                stmt.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static Column bindColumn(Database db, TransferPlan plan, ObjectDescriptor object, String table,
                                     Map<SymbolRef, String> tables, AttributeSlot attr,
                                     Map<String, AttributeName> names, Map<String, String> physical, Set<String> used)
            throws SQLException {
        String path = attr.name().declared().toString();
        AttributeName mapping = names.remove(path);
        if (mapping == null || !key(db, table).equals(key(db, mapping.owner()))
                || !used.add(key(db, mapping.name()))) {
            throw new DbException("Missing or inconsistent ATTRNAME mapping for " + path);
        }
        var column = DbMapping.describeColumn(plan, object, attr, mapping.name());
        if (column.target().isPresent()) {
            if (mapping.target() == null
                    || !key(db, tables.get(column.target().orElseThrow())).equals(key(db, mapping.target()))) {
                throw new DbException("Inconsistent ATTRNAME.target for " + path);
            }
        } else if (mapping.target() != null) {
            throw new DbException("Unexpected ATTRNAME.target for " + path);
        }
        requireColumns(db, table, physical, Map.of(column.name(), column.kind()));
        return column.kind() == Kind.POINT ? column.withSrsId(db.pointSrs(table, column.name())) : column;
    }

    private static void checkSystemTables(Database db) throws SQLException {
        String baskets = foldedName(BASKET);
        requireColumns(db, baskets, physicalColumns(db, baskets), Map.of(T_ID, Kind.REFERENCE,
                "dataset", Kind.REFERENCE, "topic", Kind.TEXT, T_ILI_TID, Kind.TEXT,
                "attachmentkey", Kind.TEXT, "domains", Kind.TEXT));
        requireIdPrimaryKey(db, baskets);
        String datasets = foldedName(DATASET);
        requireColumns(db, datasets, physicalColumns(db, datasets),
                Map.of(T_ID, Kind.REFERENCE, "datasetname", Kind.TEXT));
        requireIdPrimaryKey(db, datasets);
    }

    private static Map<String, String> physicalColumns(Database db, String name) throws SQLException {
        var metadata = db.connection().getMetaData();
        String catalog = db.connection().getCatalog();
        String escape = metadata.getSearchStringEscape();
        String pattern = searchPattern(name, escape);
        String schemaPattern = searchPattern(db.schema(), escape);
        try (var tables = metadata.getTables(catalog, schemaPattern, pattern, new String[]{"TABLE"})) {
            if (!tables.next() || !key(db, name).equals(key(db, tables.getString("TABLE_NAME"))) || tables.next()) {
                throw new DbException("Missing or ambiguous physical table " + name);
            }
        }
        var columns = new HashMap<String, String>();
        try (var rows = metadata.getColumns(catalog, schemaPattern, pattern, null)) {
            while (rows.next()) {
                columns.put(key(db, rows.getString("COLUMN_NAME")), rows.getString("TYPE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private static String searchPattern(String name, String escape) {
        return name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    private static void requireIdPrimaryKey(Database db, String table) throws SQLException {
        var metadata = db.connection().getMetaData();
        try (var keys = metadata.getPrimaryKeys(db.connection().getCatalog(), db.schema(), table)) {
            if (!keys.next() || !T_ID.equals(key(db, keys.getString("COLUMN_NAME"))) || keys.next()) {
                throw new DbException("Expected T_Id primary key in " + table);
            }
        }
    }

    private static void requireColumns(Database db, String table, Map<String, String> physical,
                                       Map<String, Kind> expected) {
        expected.forEach((name, kind) -> {
            String type = physical.get(key(db, name));
            if (type == null) throw new DbException("Missing physical column " + table + "." + name);
            boolean supported = switch (kind) {
                case TEXT, ENUMERATION -> type.equals("text") || type.equals("varchar");
                case BOOLEAN -> type.equals("boolean") || type.equals("bool");
                case INTEGER -> type.equals("integer") || type.equals("int4") || type.equals("int2")
                        || type.equals("smallint");
                case REFERENCE -> type.equals("integer") || type.equals("int4") || type.equals("int8")
                        || type.equals("bigint") || type.equals("bigserial") || type.equals("serial");
                case POINT -> type.equals(db.gpkg() ? "point" : "geometry");
            };
            if (!supported) throw new DbException("Unsupported SQL type for " + table + "." + name + " (" + type + ")");
        });
    }

    private static void requireMetadataColumns(Database db, String table, String... names) throws SQLException {
        var physical = physicalColumns(db, table);
        for (String name : names) requireColumns(db, table, physical, Map.of(name, Kind.TEXT));
    }

    private static String key(Database db, String name) {
        return db.gpkg() ? name.toLowerCase(Locale.ROOT) : name;
    }

    private static String required(ResultSet rows, int index, String field) throws SQLException {
        String value = rows.getString(index);
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw new DbException("Missing or invalid " + field);
        }
        return value;
    }
}
