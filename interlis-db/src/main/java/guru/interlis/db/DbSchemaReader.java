package guru.interlis.db;

import guru.interlis.db.DbMapping.AssociationRole;
import guru.interlis.db.DbMapping.Column;
import guru.interlis.db.DbMapping.Composition;
import guru.interlis.db.DbMapping.CrsDefaults;
import guru.interlis.db.DbMapping.EmbeddedAssociation;
import guru.interlis.db.DbMapping.Kind;
import guru.interlis.db.DbMapping.SecondaryGeometry;
import guru.interlis.db.DbMapping.Table;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferPlan;

import java.sql.DatabaseMetaData;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.db.DbMapping.unsupported;
import static guru.interlis.db.Ili2dbLayout.ATTRNAME;
import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.CLASSNAME;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.EMBEDDED;
import static guru.interlis.db.Ili2dbLayout.INHERITANCE;
import static guru.interlis.db.Ili2dbLayout.INHERITANCE_TAG;
import static guru.interlis.db.Ili2dbLayout.MAX_MODEL_CHARACTERS;
import static guru.interlis.db.Ili2dbLayout.MODEL;
import static guru.interlis.db.Ili2dbLayout.NEW_CLASS;
import static guru.interlis.db.Ili2dbLayout.PREFIX;
import static guru.interlis.db.Ili2dbLayout.SCHEMA_SENDER;
import static guru.interlis.db.Ili2dbLayout.SETTINGS;
import static guru.interlis.db.Ili2dbLayout.TRAFO;
import static guru.interlis.db.Ili2dbLayout.T_BASKET;
import static guru.interlis.db.Ili2dbLayout.T_ID;
import static guru.interlis.db.Ili2dbLayout.T_ILI_TID;
import static guru.interlis.db.Ili2dbLayout.T_SEQ;
import static guru.interlis.db.Ili2dbLayout.T_TYPE;
import static guru.interlis.db.Ili2dbLayout.foldedName;

/** Binds a checked model to the mappings and physical catalog of an existing ili2db schema. */
final class DbSchemaReader {
    private record StoredModel(LanguageVersion version, String content) { }
    private record AttributeName(String name, String owner, String target) { }
    private record Settings(boolean splitGeometries, CrsDefaults crs, boolean referenceEpsgHeader, boolean multiSrs,
                            Optional<DbCrsPair.Models> srsModels, CrsDefaults alternateCrs) { }

    private record Transformations(Map<String, String> classes, Map<String, String> secondary) { }

    private record CatalogTable(String name, Map<String, String> columns, Set<String> used) { }

    private DbSchemaReader() { }

    static DbMapping read(Database db, String modelName, CancellationToken token) throws SQLException {
        token.throwIfCancellationRequested();
        var settings = readSettings(db, modelName);
        StoredModel source = readModel(db, modelName);
        var models = settings.srsModels().map(DbCrsPair.Models::names).orElse(List.of(modelName));
        TransferPlan plan = DbMapping.compile(models, new SourceText(new SourceId("database:/model.ili"),
                new SourceRevision(1), source.content()), "MODEL.content", token);
        if (!plan.schema().languageVersions().equals(Set.of(source.version()))) {
            throw new DbException("MODEL.iliversion disagrees with MODEL.content for " + modelName);
        }

        if (!settings.multiSrs() && plan.topics().stream().anyMatch(DbDomains::deferred)) {
            throw unsupported("deferred generic domains require SETTINGS.useEpsgInNames");
        }
        var crsPair = settings.srsModels().map(pair -> new DbCrsPair(plan, pair, settings.crs(), settings.alternateCrs()));
        String prefix = modelName + ".";
        var classNames = readClassNames(db, prefix);
        var attrNames = readAttributeNames(db, prefix);
        var transformations = readTransformations(db, prefix);
        var inheritance = readInheritance(db, prefix);
        var names = new HashMap<SymbolRef, String>();
        var usedNames = new HashSet<String>();
        var objects = new ArrayList<ObjectDescriptor>();
        var associations = new ArrayList<EmbeddedAssociation>();
        var linkRoles = new HashMap<SymbolRef, List<AssociationRole>>();
        for (ObjectDescriptor object : plan.objects()) {
            String path = object.name().declared().toString();
            if (crsPair.isPresent() && !path.startsWith(prefix)) continue;
            String name = classNames.remove(path);
            if (name == null || !usedNames.add(key(db, name))) {
                throw new DbException("Missing or inconsistent CLASSNAME/TRAFO mapping for " + path);
            }
            String transformation = transformations.classes().remove(path);
            String expectedBase = object.baseType().flatMap(plan::object)
                    .map(base -> base.name().declared().toString()).orElse(null);
            if (!inheritance.containsKey(path) || !Objects.equals(expectedBase, inheritance.remove(path))) {
                throw new DbException("Missing or inconsistent INHERITANCE mapping for " + path);
            }
            if (object.kind() == SymbolKind.ASSOCIATION) {
                var roles = DbMapping.describeRoles(plan, object);
                var embedded = DbMapping.embeddedAssociation(plan, object, name, roles);
                if (embedded.isPresent()) {
                    var association = embedded.orElseThrow();
                    associations.add(association);
                    if (!association.linkTable()) {
                        if (!EMBEDDED.equals(transformation)) {
                            throw new DbException("Missing or inconsistent CLASSNAME/TRAFO mapping for " + path);
                        }
                        continue;
                    }
                }
                if (object.baseType().isEmpty()) linkRoles.put(object.symbol(), roles);
            }
            if (!NEW_CLASS.equals(transformation)) {
                throw new DbException("Missing or inconsistent CLASSNAME/TRAFO mapping for " + path);
            }
            objects.add(object);
            names.put(object.symbol(), name);
        }
        if (!transformations.classes().isEmpty() || !inheritance.isEmpty()) {
            throw new DbException("Extra CLASSNAME/TRAFO/INHERITANCE mappings for " + modelName);
        }
        checkSystemTables(db);
        var bases = DbMapping.baseTables(plan);
        var catalog = readCatalog(db, plan, objects, names, bases, token);
        var tables = new ArrayList<Table>();
        var compositions = new ArrayList<Composition>();
        for (ObjectDescriptor object : objects) {
            token.throwIfCancellationRequested();
            var parent = catalog.get(object.symbol());
            var columns = new ArrayList<Column>();
            var secondary = new ArrayList<SecondaryGeometry>();
            var roles = linkRoles.get(object.symbol());
            if (roles != null) {
                for (var role : roles) {
                    var column = bindRole(db, parent, attrNames, role.slot(), role.target(), names);
                    columns.add(role.slot().role().ordered() ? bindPosition(db, parent, column) : column);
                }
            }
            for (var attr : DbMapping.attributes(object)) {
                String path = attr.name().declared().toString();
                var ordinary = attrNames.get(path);
                var composition = DbMapping.describeComposition(plan, object, attr, ordinary == null ? path : ordinary.name());
                if (composition.isPresent()) {
                    var mapping = takeAttributeName(attrNames, path);
                    var binding = composition.orElseThrow();
                    var child = catalog.get(binding.childRoot());
                    useColumn(db, child, mapping, path);
                    requireTarget(db, parent.name(), mapping, path);
                    requireColumns(db, child.name(), child.columns(), Map.of(binding.ownerColumn(), Kind.REFERENCE));
                    compositions.add(binding);
                    continue;
                }
                for (var described : DbMapping.describeColumns(plan, object, attr, settings.crs(), crsPair)) {
                    String attribute = Ili2dbLayout.geometryAttribute(path, described.epsgCode(), settings.multiSrs());
                    var mapping = takeAttributeName(attrNames, attribute);
                    var column = described.withName(mapping.name());
                    if (settings.splitGeometries() && !key(db, parent.name()).equals(key(db, mapping.owner()))) {
                        String owner = object.name().declared().toString();
                        var table = classNames.remove(Ili2dbLayout.secondaryClass(attribute, owner));
                        var transformation = transformations.secondary()
                                .remove(Ili2dbLayout.secondaryTransformation(path, owner, column.epsgCode()));
                        if (table == null || transformation == null || !key(db, table).equals(key(db, transformation))
                                || !key(db, table).equals(key(db, mapping.owner())) || !usedNames.add(key(db, table))
                                || Ili2dbLayout.reservedTable(foldedName(table), db.gpkg())) {
                            throw new DbException("Inconsistent secondary geometry CLASSNAME/TRAFO/ATTRNAME mapping for " + path);
                        }
                        secondary.add(bindSecondaryGeometry(db, column, mapping, parent.name(), table, path, settings));
                    } else {
                        columns.add(bindColumn(db, parent, names, column, mapping, path, settings));
                    }
                }
            }
            for (var association : DbMapping.associationsForOwner(associations, object.symbol())) {
                if (association.linkTable()) continue;
                var column = bindRole(db, parent, attrNames, association.role(), association.target(), names);
                columns.add(association.ordered() ? bindPosition(db, parent, column) : column);
            }
            if (settings.splitGeometries()) DbMapping.requireSingleGeometry(object, columns);
            db.checkGeometryTable(parent.name(), columns.stream().filter(column -> column.kind().geometry()).toList());
            for (var geometry : secondary) db.checkGeometryTable(geometry.table(), List.of(geometry.column()));
            tables.add(new Table(object, parent.name(), columns, hasDiscriminator(object, bases),
                    DbMapping.oidDomain(plan, object), secondary));
        }
        for (var table : tables) {
            var physical = catalog.get(table.object().symbol());
            if (!physical.columns().keySet().equals(physical.used())) {
                throw new DbException("Unexpected physical columns in " + physical.name());
            }
        }
        if (!classNames.isEmpty() || !transformations.secondary().isEmpty()) {
            throw new DbException("Extra CLASSNAME/TRAFO mappings for " + modelName);
        }
        if (!attrNames.isEmpty()) throw new DbException("Extra ATTRNAME mappings for " + modelName);
        return new DbMapping(plan, source.version(), tables, compositions, associations, crsPair);
    }

    private static boolean hasDiscriminator(ObjectDescriptor object, Set<SymbolRef> bases) {
        return object.baseType().isEmpty() && bases.contains(object.symbol());
    }

    private static Map<SymbolRef, CatalogTable> readCatalog(Database db, TransferPlan plan, List<ObjectDescriptor> objects,
                                                           Map<SymbolRef, String> names, Set<SymbolRef> bases,
                                                           CancellationToken token)
            throws SQLException {
        var result = new HashMap<SymbolRef, CatalogTable>();
        for (var object : objects) {
            token.throwIfCancellationRequested();
            String name = names.get(object.symbol());
            var physical = physicalColumns(db, name);
            requireColumns(db, name, physical, Map.of(T_ID, Kind.REFERENCE, T_BASKET, Kind.REFERENCE));
            requireIdPrimaryKey(db, name);
            var used = new HashSet<>(Set.of(T_ID, T_BASKET));
            boolean subtype = object.baseType().isPresent();
            var domain = DbMapping.oidDomain(plan, object);
            if (!subtype && (object.kind() != SymbolKind.ASSOCIATION || domain.isPresent())) {
                if (domain.isPresent()) {
                    requireIdentityColumn(db, name, physical, domain.orElseThrow());
                } else {
                    requireColumns(db, name, physical, Map.of(T_ILI_TID, Kind.TEXT));
                }
                used.add(T_ILI_TID);
            }
            if (hasDiscriminator(object, bases)) {
                requireColumns(db, name, physical, Map.of(T_TYPE, Kind.TEXT));
                requireDiscriminator(db, name);
                used.add(T_TYPE);
            }
            if (!subtype && object.kind() == SymbolKind.STRUCTURE) {
                requireColumns(db, name, physical, Map.of(T_SEQ, Kind.REFERENCE));
                used.add(T_SEQ);
            }
            result.put(object.symbol(), new CatalogTable(name, physical, used));
        }
        return result;
    }

    private static void requireIdentityColumn(Database db, String table, Map<String, String> physical, String domain)
            throws SQLException {
        if (Ili2dbLayout.UUID_OID.equals(domain)) {
            if (!(db.gpkg() ? "text" : "uuid").equals(physical.get(T_ILI_TID))) {
                throw new DbException("Expected UUID identity column " + table + "." + T_ILI_TID);
            }
        } else {
            requireColumns(db, table, physical, Map.of(T_ILI_TID, Kind.TEXT));
        }
        String sql = "SELECT setting FROM " + db.technicalTable(Ili2dbLayout.COLUMN_PROP)
                + " WHERE tablename = ? AND subtype IS NULL AND lower(columnname) = ? AND tag = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, T_ILI_TID);
            statement.setString(3, Ili2dbLayout.OID_DOMAIN_PROPERTY);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !domain.equals(rows.getString(1)) || rows.next()) {
                    throw new DbException("Missing or inconsistent oidDomain for " + table + "." + T_ILI_TID);
                }
            }
        }
    }

    private static Settings readSettings(Database db, String modelName) throws SQLException {
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
        String value = settings.get(PREFIX + "defaultSrsCode");
        int defaultEpsg = value == null ? 0 : Ili2dbLayout.parseEpsgCode(value, "SETTINGS.defaultSrsCode");
        var crs = new CrsDefaults(defaultEpsg, modelEpsg(settings.get(PREFIX + "modelSrsCode"), modelName));
        for (String tag : List.of("inheritanceTrafo", "createEnumColAsItfCode", "createEnumCols",
                "createDatasetCols", "typeDiscriminator", "createStdCols", "SqlColsAsText", "SqlExtRefCols")) {
            if (settings.containsKey(PREFIX + tag)) throw unsupported("SETTINGS." + tag);
        }
        if ("multiTableWithId".equals(settings.get(PREFIX + "createEnumDefs"))) {
            throw unsupported("enumerations stored as SQL IDs");
        }
        String epsgNames = settings.get(Ili2dbLayout.USE_EPSG_IN_NAMES);
        if (epsgNames != null && !List.of("True", "False").contains(epsgNames)) {
            throw unsupported("SETTINGS.useEpsgInNames=" + epsgNames);
        }
        boolean multiSrs = "True".equals(epsgNames);
        var srsModels = Optional.ofNullable(settings.get(Ili2dbLayout.SRS_MODEL_ASSIGNMENT))
                .map(assignment -> DbCrsPair.Models.parse(assignment, modelName));
        if (srsModels.isPresent() && !multiSrs) throw unsupported("SETTINGS.srsModelAssignment requires --multiSrs");
        int alternateEpsg = srsModels.map(pair -> modelEpsg(settings.get(PREFIX + "modelSrsCode"), pair.alternate())).orElse(0);
        var alternateCrs = new CrsDefaults(defaultEpsg, alternateEpsg);
        String geometryLayout = settings.get(Ili2dbLayout.GEOMETRY_LAYOUT);
        boolean referenceEpsgHeader = db.gpkg() && isSender(sender, "ili2gpkg-5.5.2");
        if (Ili2dbLayout.MULTIPLE_GEOMETRIES.equals(geometryLayout)) {
            return new Settings(false, crs, referenceEpsgHeader, multiSrs, srsModels, alternateCrs);
        }
        if (geometryLayout != null && !Ili2dbLayout.SINGLE_GEOMETRY.equals(geometryLayout)) {
            throw unsupported("SETTINGS.geomAttrPerTable=" + geometryLayout);
        }
        return new Settings(db.gpkg() || Ili2dbLayout.SINGLE_GEOMETRY.equals(geometryLayout), crs, referenceEpsgHeader, multiSrs, srsModels, alternateCrs);
    }

    private static int modelEpsg(String assignments, String modelName) {
        if (assignments == null || assignments.isBlank()) return 0;
        int epsg = 0;
        for (String assignment : assignments.split(";")) {
            int separator = assignment.indexOf('=');
            if (separator <= 0) throw unsupported("invalid SETTINGS.modelSrsCode assignment");
            if (!modelName.equals(assignment.substring(0, separator))) continue;
            if (epsg != 0) throw unsupported("multiple SETTINGS.modelSrsCode assignments for " + modelName);
            epsg = Ili2dbLayout.parseEpsgCode(assignment.substring(separator + 1), "SETTINGS.modelSrsCode for " + modelName);
        }
        return epsg;
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

    private static Transformations readTransformations(Database db, String prefix) throws SQLException {
        String table = foldedName(TRAFO);
        requireMetadataColumns(db, table, "iliname", "tag", "setting");
        var result = new HashMap<String, String>();
        var secondary = new HashMap<String, String>();
        try (var stmt = scopedMetadata(db, table, "iliname, tag, setting", prefix);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "TRAFO.iliname");
                String setting = required(rows, 3, "TRAFO.setting");
                String tag = rows.getString(2);
                if (Ili2dbLayout.SECONDARY_TABLE_TAG.equals(tag)) {
                    if (secondary.putIfAbsent(path, setting) != null) throw unsupported("TRAFO entries for " + path);
                } else if (!INHERITANCE_TAG.equals(tag) || !(NEW_CLASS.equals(setting) || EMBEDDED.equals(setting))
                        || result.putIfAbsent(path, setting) != null) {
                    throw unsupported("TRAFO entries for " + path);
                }
            }
        }
        return new Transformations(result, secondary);
    }

    private static Map<String, String> readInheritance(Database db, String prefix) throws SQLException {
        String table = foldedName(INHERITANCE);
        requireMetadataColumns(db, table, "thisclass", "baseclass");
        var result = new HashMap<String, String>();
        try (var stmt = scopedMetadata(db, table, "thisclass, baseclass", "thisclass", prefix);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "INHERITANCE.thisClass");
                if (result.containsKey(path)) throw new DbException("Duplicate INHERITANCE mapping for " + path);
                result.put(path, rows.getString(2));
            }
        }
        return result;
    }

    private static PreparedStatement scopedMetadata(Database db, String table, String columns, String prefix)
            throws SQLException {
        return scopedMetadata(db, table, columns, "iliname", prefix);
    }

    private static PreparedStatement scopedMetadata(Database db, String table, String columns, String field,
                                                     String prefix) throws SQLException {
        var stmt = db.connection().prepareStatement("SELECT " + columns + " FROM " + db.table(table)
                + " WHERE substr(" + field + ", 1, ?) = ?");
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

    private static Column bindColumn(Database db, CatalogTable table,
                                     Map<SymbolRef, String> tables, Column column, AttributeName mapping,
                                     String path, Settings settings)
            throws SQLException {
        useColumn(db, table, mapping, path);
        if (column.target().isPresent()) {
            requireTarget(db, tables.get(column.target().orElseThrow()), mapping, path);
        } else if (mapping.target() != null) {
            throw new DbException("Unexpected ATTRNAME.target for " + path);
        }
        requireColumns(db, table.name(), table.columns(), Map.of(column.name(), column.kind()));
        return column.kind().geometry() ? bindGeometry(db, table.name(), column, settings) : column;
    }

    private static SecondaryGeometry bindSecondaryGeometry(Database db, Column column, AttributeName mapping,
                                                           String parent, String table, String path, Settings settings)
            throws SQLException {
        if (!column.kind().geometry() || mapping.target() != null || Ili2dbLayout.TECHNICAL_COLUMNS.contains(key(db, column.name()))) {
            throw new DbException("Invalid secondary geometry column for " + path);
        }
        var physical = physicalColumns(db, table);
        requireColumns(db, table, physical, Map.of(T_ID, Kind.REFERENCE, T_BASKET, Kind.REFERENCE, column.name(), column.kind()));
        requireIdPrimaryKey(db, table);
        if (physical.size() != 3) throw new DbException("Unexpected physical columns in " + table);

        String query = "SELECT setting FROM " + db.technicalTable(Ili2dbLayout.COLUMN_PROP)
                + " WHERE tablename = ? AND subtype IS NULL AND lower(columnname) = ? AND tag = ?";
        try (var stmt = db.connection().prepareStatement(query)) {
            stmt.setString(1, table);
            stmt.setString(2, T_ID);
            stmt.setString(3, PREFIX + "foreignKey");
            try (var rows = stmt.executeQuery()) {
                if (!rows.next() || !parent.equals(rows.getString(1)) || rows.next()) {
                    throw new DbException("Invalid secondary geometry owner for " + path);
                }
            }
        }
        return new SecondaryGeometry(table, bindGeometry(db, table, column, settings));
    }

    private static Column bindGeometry(Database db, String table, Column column, Settings settings) throws SQLException {
        var stored = db.geometryStorage(table, column, settings.referenceEpsgHeader());
        String names = db.gpkg() ? "lower(tablename) = ? AND lower(columnname) = ?" : "tablename = ? AND columnname = ?";
        String sql = "SELECT setting FROM " + db.technicalTable(Ili2dbLayout.COLUMN_PROP)
                + " WHERE " + names + " AND subtype IS NULL AND tag = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, key(db, table));
            statement.setString(2, key(db, column.name()));
            statement.setString(3, PREFIX + Ili2dbLayout.SRID_PROPERTY);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    String property = "COLUMN_PROP.srid for " + table + "." + column.name();
                    int epsg = Ili2dbLayout.parseEpsgCode(rows.getString(1), property);
                    if (epsg != column.epsgCode() || rows.next()) {
                        throw new DbException("Inconsistent " + property + ": expected one EPSG:" + column.epsgCode());
                    }
                }
            }
        }
        return stored;
    }

    private static void useColumn(Database db, CatalogTable table, AttributeName mapping, String path) {
        if (!key(db, table.name()).equals(key(db, mapping.owner())) || !table.used().add(key(db, mapping.name()))) {
            throw new DbException("Missing or inconsistent ATTRNAME mapping for " + path);
        }
    }

    private static Column bindRole(Database db, CatalogTable table, Map<String, AttributeName> names,
                                   RoleSlot role, SymbolRef target, Map<SymbolRef, String> tables) {
        String path = role.name().declared().toString();
        var mapping = takeAttributeName(names, path);
        useColumn(db, table, mapping, path);
        requireTarget(db, tables.get(target), mapping, path);
        requireColumns(db, table.name(), table.columns(), Map.of(mapping.name(), Kind.REFERENCE));
        return DbMapping.roleColumn(role, target, mapping.name());
    }

    private static Column bindPosition(Database db, CatalogTable table, Column role) throws SQLException {
        var column = role.withPositionColumn(db.gpkg());
        String position = column.positionColumn().orElseThrow();
        String columnKey = key(db, position);
        requireColumns(db, table.name(), table.columns(), Map.of(position, Kind.REFERENCE));
        String type = table.columns().get(columnKey);
        var metadata = db.connection().getMetaData();
        String escape = metadata.getSearchStringEscape();
        try (var columns = metadata.getColumns(db.connection().getCatalog(), searchPattern(db.schema(), escape),
                searchPattern(table.name(), escape), searchPattern(position, escape))) {
            while (columns.next()) {
                if (!columnKey.equals(key(db, columns.getString("COLUMN_NAME")))) continue;
                boolean idType = db.gpkg() ? type.equals("integer") : type.equals("int8") || type.equals("bigint");
                if (idType && columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable
                        && table.used().add(columnKey)) return column;
                break;
            }
        }
        throw new DbException("Expected distinct nullable ID column " + table.name() + "." + position
                + " for role position at " + role.slot().name().declared());
    }

    private static AttributeName takeAttributeName(Map<String, AttributeName> names, String path) {
        var name = names.remove(path);
        if (name == null) throw new DbException("Missing or inconsistent ATTRNAME mapping for " + path);
        return name;
    }

    private static void requireTarget(Database db, String expected, AttributeName mapping, String path) {
        if (mapping.target() == null || !key(db, expected).equals(key(db, mapping.target()))) {
            throw new DbException("Inconsistent ATTRNAME.target for " + path);
        }
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

    private static void requireDiscriminator(Database db, String name) throws SQLException {
        var metadata = db.connection().getMetaData();
        String escape = metadata.getSearchStringEscape();
        try (var columns = metadata.getColumns(db.connection().getCatalog(), searchPattern(db.schema(), escape),
                searchPattern(name, escape), null)) {
            while (columns.next()) {
                if (!T_TYPE.equals(key(db, columns.getString("COLUMN_NAME")))) continue;
                if (columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls) break;
                return;
            }
        }
        throw new DbException("Expected non-null T_Type discriminator in " + name);
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
                case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE ->
                        db.gpkg() ? type.equalsIgnoreCase(kind.geometryType()) : type.equals("geometry");
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
