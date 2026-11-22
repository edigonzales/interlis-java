package guru.interlis.db;

import guru.interlis.compiler.AnalysisBudget;
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
import guru.interlis.model.Origin;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static final Pattern MODEL_DECLARATION = Pattern.compile("([^\\s{}]+)(?:\\s*\\{([^{}]*)\\})?\\s*");

    private record StoredModel(String names, LanguageVersion version, String content, List<String> imports) { }
    private record AttributeName(String name, String owner, String target) { }
    record Settings(boolean splitGeometries, int defaultEpsg, String modelEpsgAssignments, boolean referenceEpsgHeader,
                    boolean multiSrs, Optional<DbCrsPair.Models> srsModels, boolean createGeometryIndexes,
                    DbInheritance.Profile inheritance) {
        CrsDefaults crs(String model) {
            return new CrsDefaults(defaultEpsg, modelEpsg(modelEpsgAssignments, model));
        }
    }

    private record Transformations(Map<String, String> classes, Map<String, String> secondary) { }

    private record CatalogTable(String name, Map<String, String> columns, Set<String> used) { }

    private DbSchemaReader() { }

    static DbMapping read(Database db, List<String> selectedModels, CancellationToken token) throws SQLException {
        return read(db, selectedModels, Optional.empty(), token);
    }

    static DbMapping read(Database db, List<String> selectedModels, Optional<String> outputModel, CancellationToken token)
            throws SQLException {
        token.throwIfCancellationRequested();
        String modelName = String.join(", ", selectedModels);
        var settings = readSettings(db, selectedModels);
        var models = new LinkedHashSet<>(settings.srsModels().map(DbCrsPair.Models::names).orElse(selectedModels));
        outputModel.ifPresent(models::add);
        TransferPlan plan = readModelPlan(db, List.copyOf(models), token);
        var version = plan.model(plan.rootModels().getFirst()).orElseThrow().languageVersion();
        if (plan.rootModels().stream().map(ref -> plan.model(ref).orElseThrow().languageVersion())
                .anyMatch(language -> language != version)) {
            throw new DbException("Selected transfer models must use the same INTERLIS language version: " + modelName);
        }

        var crsPair = settings.srsModels().map(pair -> new DbCrsPair(plan, pair, settings.crs(pair.original()),
                settings.crs(pair.alternate())));
        var requestedRoots = plan.rootModels().stream().filter(ref ->
                selectedModels.contains(plan.model(ref).orElseThrow().name().declared().last())).toList();
        if (outputModel.isPresent()) {
            String output = outputModel.orElseThrow();
            var target = plan.models().stream().filter(model -> model.name().declared().last().equals(output))
                    .findFirst().orElseThrow();
            if (version == LanguageVersion.ILI24 && !target.symbol().equals(target.name().canonical())) {
                throw unsupported("XTF 2.4 exports require the original model instead of " + output);
            }
            if (requestedRoots.stream().map(ref -> plan.model(ref).orElseThrow().name().canonical())
                    .noneMatch(target.name().canonical()::equals)) {
                throw unsupported("export model " + output + " is outside the selected translation families");
            }
        }
        var roots = DbMapping.storageRoots(plan, crsPair);
        var selected = DbMapping.storageObjects(plan, roots, token);
        DbMapping.checkLayout(plan, selected);
        if (!settings.multiSrs() && plan.topics().stream()
                .anyMatch(topic -> plan.rootModels().contains(topic.model()) && DbDomains.deferred(topic))) {
            throw unsupported("deferred generic domains require SETTINGS.useEpsgInNames");
        }
        var mappedModels = new HashSet<>(selectedModels);
        roots.stream().map(ref -> plan.model(ref).orElseThrow().name().declared().last()).forEach(mappedModels::add);
        var scope = new MetadataScope(mappedModels, selected.stream()
                .map(object -> object.name().declared().toString()).collect(Collectors.toSet()));
        var classNames = readClassNames(db, scope);
        var attrNames = readAttributeNames(db, scope);
        var transformations = readTransformations(db, scope);
        var inheritance = readInheritance(db, scope);
        var names = new HashMap<SymbolRef, String>();
        var usedNames = new HashSet<String>();
        var declarations = new ArrayList<DbInheritance.Declaration>();
        var associations = new ArrayList<EmbeddedAssociation>();
        var linkRoles = new HashMap<SymbolRef, List<AssociationRole>>();
        for (ObjectDescriptor object : selected) {
            String path = object.name().declared().toString();
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
            if (settings.inheritance() == DbInheritance.Profile.NEW_CLASS && !NEW_CLASS.equals(transformation)) {
                throw new DbException("Missing or inconsistent CLASSNAME/TRAFO mapping for " + path);
            }
            var strategy = switch (transformation) {
                case NEW_CLASS -> DbInheritance.Strategy.NEW_CLASS;
                case "superClass" -> DbInheritance.Strategy.SUPER_CLASS;
                case "subClass" -> DbInheritance.Strategy.SUB_CLASS;
                case "newAndSubClass" -> DbInheritance.Strategy.NEW_AND_SUB_CLASS;
                case null, default -> throw DbInheritance.invalid(object);
            };
            declarations.add(new DbInheritance.Declaration(object, name, strategy));
            names.put(object.symbol(), name);
        }
        if (!transformations.classes().isEmpty() || !inheritance.isEmpty()) {
            throw new DbException("Extra CLASSNAME/TRAFO/INHERITANCE mappings for " + modelName);
        }
        checkSystemTables(db);
        var layout = new DbInheritance(plan, declarations, settings.inheritance());
        for (var alias : layout.aliases()) {
            names.put(alias.object().symbol(), names.get(alias.owner()));
        }
        for (var association : associations) {
            if (association.linkTable() && layout.referenceTables(association.owner()).size() != 1) {
                throw unsupported("embedded link table with multiple owner tables at " + association.object().name().declared());
            }
        }
        var catalog = readCatalog(db, plan, layout.relations(), token);
        var tables = new ArrayList<Table>();
        var compositions = new ArrayList<Composition>();
        for (var relation : layout.relations()) {
            token.throwIfCancellationRequested();
            var object = relation.object();
            var parent = catalog.get(object.symbol());
            var columns = new ArrayList<Column>();
            var secondary = new ArrayList<SecondaryGeometry>();
            var roles = linkRoles.get(object.symbol());
            if (roles != null) {
                for (var role : roles) {
                    columns.addAll(bindReferences(db, parent, attrNames, layout, names,
                            DbMapping.roleColumn(role.slot(), role.target(), role.slot().name().declared().last()),
                            role.slot().role().ordered(), settings));
                }
            }
            for (var contributor : relation.contributors()) {
                var defaults = settings.crs(plan.model(contributor.model()).orElseThrow().name().declared().last());
                for (var attr : DbMapping.attributes(contributor)) {
                    String path = attr.name().declared().toString();
                    var composition = DbMapping.describeComposition(plan, object, attr, path);
                    if (composition.isPresent()) {
                        var binding = composition.orElseThrow();
                        var child = catalog.get(binding.childRoot());
                        if (child == null) throw unsupported("structure storage at " + path);
                        var mapping = attrNames.take(path, child.name(), parent.name());
                        useColumn(db, child, mapping, path);
                        requireColumns(db, child.name(), child.columns(), Map.of(mapping.name(), Kind.REFERENCE));
                        compositions.add(new Composition(attr, binding.parent(), binding.child(), binding.childRoot(),
                                mapping.name(), binding.collectionKind()));
                        continue;
                    }
                    for (var described : DbMapping.describeColumns(plan, contributor, attr, defaults, crsPair)) {
                        if (described.kind() == Kind.REFERENCE) {
                            columns.addAll(bindReferences(db, parent, attrNames, layout, names, described, false, settings));
                            continue;
                        }
                        String attribute = Ili2dbLayout.geometryAttribute(path, described.epsgCode(), settings.multiSrs());
                        String owner = object.name().declared().toString();
                        String secondaryKey = Ili2dbLayout.secondaryClass(attribute, owner);
                        String table = settings.splitGeometries() && described.kind().geometry() ? classNames.remove(secondaryKey) : null;
                        var mapping = attrNames.take(attribute, table == null ? parent.name() : table, null);
                        var column = described.withName(mapping.name());
                        if (table != null) {
                            var transformation = transformations.secondary()
                                    .remove(Ili2dbLayout.secondaryTransformation(path, owner, column.epsgCode()));
                            if (transformation == null || !key(db, table).equals(key(db, transformation))
                                    || !usedNames.add(key(db, table)) || Ili2dbLayout.reservedTable(foldedName(table), db.gpkg())) {
                                throw new DbException("Inconsistent secondary geometry CLASSNAME/TRAFO/ATTRNAME mapping for " + path);
                            }
                            secondary.add(bindSecondaryGeometry(db, column, mapping, parent.name(), table, path, settings));
                        } else {
                            columns.add(bindColumn(db, parent, names, column, mapping, path, settings));
                        }
                    }
                }
                for (var association : DbMapping.associationsForOwner(associations, contributor.symbol())) {
                    if (association.linkTable()) continue;
                    columns.addAll(bindReferences(db, parent, attrNames, layout, names,
                            DbMapping.roleColumn(association.role(), association.target(), association.role().name().declared().last()),
                            association.ordered(), settings));
                }
            }
            if (settings.splitGeometries()) DbMapping.requireSingleGeometry(object, columns);
            db.checkGeometryTable(parent.name(), columns.stream().filter(column -> column.kind().geometry()).toList());
            for (var geometry : secondary) db.checkGeometryTable(geometry.table(), List.of(geometry.column()));
            tables.add(new Table(object, parent.name(), relation.base(), columns, relation.discriminator(),
                    DbMapping.oidDomain(plan, object), secondary));
        }
        for (var table : tables) {
            var physical = catalog.get(table.object().symbol());
            if (table.object().kind() == SymbolKind.STRUCTURE && !table.subtype()
                    && !physical.columns().keySet().equals(physical.used())) {
                bindOtherStructureOwners(db, scope, physical);
            }
            if (!physical.columns().keySet().equals(physical.used())) {
                throw new DbException("Unexpected physical columns in " + physical.name());
            }
        }
        if (!classNames.isEmpty() || !transformations.secondary().isEmpty()) {
            throw new DbException("Extra CLASSNAME/TRAFO mappings for " + modelName);
        }
        if (!attrNames.isEmpty()) throw new DbException("Extra ATTRNAME mappings for " + modelName);
        return new DbMapping(plan, version, tables, compositions, associations, crsPair, requestedRoots,
                settings.inheritance(), layout.aliases());
    }

    private static void bindOtherStructureOwners(Database db, MetadataScope scope, CatalogTable table) throws SQLException {
        String sql = """
                SELECT a.iliname, a.sqlname FROM %s a JOIN %s p
                  ON p.tablename = a.colowner AND lower(p.columnname) = lower(a.sqlname)
                   AND p.setting = a.target AND p.subtype IS NULL
                WHERE a.colowner = ? AND p.tag = ?
                """.formatted(db.technicalTable(ATTRNAME), db.technicalTable(Ili2dbLayout.COLUMN_PROP));
        try (var stmt = db.connection().prepareStatement(sql)) {
            stmt.setString(1, table.name());
            stmt.setString(2, PREFIX + "foreignKey");
            try (var rows = stmt.executeQuery()) {
                while (rows.next()) {
                    String path = required(rows, 1, "ATTRNAME.iliname");
                    if (scope.includes(path)) continue;
                    String column = key(db, required(rows, 2, "ATTRNAME.sqlname"));
                    requireColumns(db, table.name(), table.columns(), Map.of(column, Kind.REFERENCE));
                    table.used().add(column);
                }
            }
        }
    }

    private static Map<SymbolRef, CatalogTable> readCatalog(Database db, TransferPlan plan,
                                                           List<DbInheritance.Relation> relations, CancellationToken token)
            throws SQLException {
        var result = new HashMap<SymbolRef, CatalogTable>();
        for (var relation : relations) {
            token.throwIfCancellationRequested();
            var object = relation.object();
            String name = relation.declaration().sqlName();
            var physical = physicalColumns(db, name);
            requireColumns(db, name, physical, Map.of(T_ID, Kind.REFERENCE, T_BASKET, Kind.REFERENCE));
            requireIdPrimaryKey(db, name);
            var used = new HashSet<>(Set.of(T_ID, T_BASKET));
            boolean subtype = relation.base().isPresent();
            var domain = DbMapping.oidDomain(plan, object);
            if (!subtype && (object.kind() != SymbolKind.ASSOCIATION || domain.isPresent())) {
                if (domain.isPresent()) {
                    requireIdentityColumn(db, name, physical, domain.orElseThrow());
                } else {
                    requireColumns(db, name, physical, Map.of(T_ILI_TID, Kind.TEXT));
                }
                used.add(T_ILI_TID);
            }
            if (relation.discriminator()) {
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

    static Settings readSettings(Database db, List<String> models) throws SQLException {
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
        String modelEpsg = settings.get(PREFIX + "modelSrsCode");
        var inheritance = switch (settings.get(PREFIX + "inheritanceTrafo")) {
            case null -> DbInheritance.Profile.NEW_CLASS;
            case "smart1" -> DbInheritance.Profile.SMART1;
            case "smart2" -> DbInheritance.Profile.SMART2;
            default -> throw unsupported("SETTINGS.inheritanceTrafo");
        };
        for (String tag : List.of("createEnumColAsItfCode", "createEnumCols",
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
        var srsModels = Optional.ofNullable(settings.get(Ili2dbLayout.SRS_MODEL_ASSIGNMENT)).map(assignment ->
                DbCrsPair.Models.parse(assignment, assignment.split("=", 2)[0]));
        if (srsModels.isPresent() && !multiSrs) throw unsupported("SETTINGS.srsModelAssignment requires --multiSrs");
        srsModels = srsModels.filter(pair -> !Collections.disjoint(models, pair.names()));
        if (srsModels.isPresent() && !models.equals(List.of(srsModels.orElseThrow().original()))) {
            throw unsupported("select the original CRS model by itself: " + srsModels.orElseThrow().original());
        }
        String geometryLayout = settings.get(Ili2dbLayout.GEOMETRY_LAYOUT);
        boolean referenceEpsgHeader = db.gpkg() && isSender(sender, "ili2gpkg-5.5.2");
        boolean indexes = "True".equals(settings.get(Ili2dbLayout.CREATE_GEOMETRY_INDEX));
        if (Ili2dbLayout.MULTIPLE_GEOMETRIES.equals(geometryLayout)) {
            return new Settings(false, defaultEpsg, modelEpsg, referenceEpsgHeader, multiSrs, srsModels, indexes, inheritance);
        }
        if (geometryLayout != null && !Ili2dbLayout.SINGLE_GEOMETRY.equals(geometryLayout)) {
            throw unsupported("SETTINGS.geomAttrPerTable=" + geometryLayout);
        }
        return new Settings(db.gpkg() || Ili2dbLayout.SINGLE_GEOMETRY.equals(geometryLayout), defaultEpsg, modelEpsg,
                referenceEpsgHeader, multiSrs, srsModels, indexes, inheritance);
    }

    static SqlNames namesForExtension(Database db, List<String> models) throws SQLException {
        checkSystemTables(db);
        var relations = new HashSet<String>();
        var metadata = db.connection().getMetaData();
        try (var rows = metadata.getTables(db.connection().getCatalog(),
                searchPattern(db.schema(), metadata.getSearchStringEscape()), "%", null)) {
            while (rows.next()) relations.add(foldedName(rows.getString("TABLE_NAME")));
        }
        String ids = db.gpkg() ? foldedName(Ili2dbLayout.KEY_OBJECT) : Ili2dbLayout.SEQUENCE;
        if (!relations.contains(ids)) throw new DbException("Missing ili2db ID allocator " + ids);
        var classes = new HashMap<String, String>();
        String classTable = foldedName(CLASSNAME);
        requireMetadataColumns(db, classTable, "iliname", "sqlname");
        try (var stmt = db.connection().createStatement();
             var rows = stmt.executeQuery("SELECT iliname, sqlname FROM " + db.table(classTable))) {
            var roots = Set.copyOf(models);
            while (rows.next()) {
                String path = required(rows, 1, "CLASSNAME.iliname");
                if (roots.contains(MetadataScope.modelName(path))) {
                    throw new DbException("Model already has a SQL mapping: " + MetadataScope.modelName(path));
                }
                if (classes.putIfAbsent(path, required(rows, 2, "CLASSNAME.sqlname")) != null) {
                    throw new DbException("Duplicate CLASSNAME mapping for " + path);
                }
            }
        }
        var attributes = new HashSet<String>();
        var columns = new HashMap<String, Set<String>>();
        String attributeTable = foldedName(ATTRNAME);
        requireMetadataColumns(db, attributeTable, "iliname", "colowner", "sqlname");
        try (var stmt = db.connection().createStatement();
             var rows = stmt.executeQuery("SELECT iliname, colowner, sqlname FROM " + db.table(attributeTable))) {
            while (rows.next()) {
                attributes.add(required(rows, 1, "ATTRNAME.iliname"));
                String owner = required(rows, 2, "ATTRNAME.colowner");
                columns.computeIfAbsent(owner, unused -> new HashSet<>()).add(foldedName(required(rows, 3, "ATTRNAME.sqlname")));
            }
        }
        return new SqlNames(classes, relations, columns, attributes);
    }

    static List<SchemaPlan.ModelFile> newModelFiles(Database db, List<SchemaPlan.ModelFile> files, CancellationToken token)
            throws SQLException {
        String table = foldedName(MODEL);
        requireMetadataColumns(db, table, "modelname", "iliversion", "content");
        var fresh = new ArrayList<SchemaPlan.ModelFile>();
        for (var file : files) {
            var declarations = storedDeclarations(file.modelNames());
            int stored = 0;
            for (var entry : declarations.entrySet()) {
                token.throwIfCancellationRequested();
                var prior = lookupModel(db, table, entry.getKey());
                if (prior == null) continue;
                stored++;
                if (prior.version() != file.version() || !prior.content().equals(file.content())
                        || !Set.copyOf(prior.imports()).equals(Set.copyOf(entry.getValue()))) {
                    throw new DbException("Stored MODEL source differs for " + entry.getKey());
                }
            }
            if (stored == 0) fresh.add(file);
            else if (stored != declarations.size()) {
                throw new DbException("Stored MODEL source only covers part of " + file.filename());
            }
        }
        return fresh;
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

    private static TransferPlan readModelPlan(Database db, List<String> models, CancellationToken token) throws SQLException {
        String table = foldedName(MODEL);
        requireMetadataColumns(db, table, "modelname", "iliversion", "content");
        var pending = new ArrayDeque<>(models);
        var versions = new HashMap<String, LanguageVersion>();
        var files = new LinkedHashMap<String, SourceText>();
        var sourceIds = new HashMap<String, SourceId>();
        long totalLength = 0;
        while (!pending.isEmpty()) {
            token.throwIfCancellationRequested();
            String model = pending.removeFirst();
            if ("INTERLIS".equals(model) || versions.containsKey(model)) continue;
            var stored = lookupModel(db, table, model);
            if (stored == null) throw new DbException("No MODEL source for " + model);
            versions.put(model, stored.version());
            var source = files.get(stored.names());
            if (source == null) {
                totalLength += stored.content().length();
                if (totalLength > AnalysisBudget.defaults().maxTotalSourceUtf16Length()) {
                    throw new DbException("MODEL.content closure exceeds the size limit at " + model);
                }
                source = new SourceText(new SourceId("database:/" + files.size() + ".ili"),
                        new SourceRevision(1), stored.content());
                files.put(stored.names(), source);
            }
            sourceIds.put(model, source.id());
            pending.addAll(stored.imports());
        }
        var plan = DbModelSources.compile(models, files.values(), "MODEL.content", true, token);
        for (var model : plan.models()) {
            String name = model.name().declared().last();
            var symbol = plan.symbol(model.symbol()).known().orElseThrow();
            if (!(symbol.header().origin() instanceof Origin.DeclaredOrigin origin)
                    || !origin.range().source().equals(sourceIds.get(name))) {
                throw new DbException("MODEL.modelname disagrees with MODEL.content for " + name);
            }
            if (model.languageVersion() != versions.get(name)) {
                throw new DbException("MODEL.iliversion disagrees with MODEL.content for " + name);
            }
        }
        return plan;
    }

    private static StoredModel lookupModel(Database db, String table, String modelName) throws SQLException {
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
                    var imports = storedDeclarations(names).get(modelName);
                    if (imports == null) continue;
                    if (match != null) throw new DbException("Multiple MODEL sources for " + modelName);
                    var version = LanguageVersion.fromLiteral(rows.getString(2))
                            .orElseThrow(() -> unsupported("MODEL.iliversion for " + modelName));
                    String content = rows.getString(3);
                    if (content == null || content.isBlank()) {
                        throw new DbException("MODEL.content is missing or exceeds the size limit for " + modelName);
                    }
                    match = new StoredModel(names, version, content, imports);
                }
            }
        }
        return match;
    }

    private static Map<String, List<String>> storedDeclarations(String names) {
        String declarations = names.strip();
        var matcher = MODEL_DECLARATION.matcher(declarations);
        var imports = new LinkedHashMap<String, List<String>>();
        int offset = 0;
        while (matcher.find() && matcher.start() == offset) {
            offset = matcher.end();
            String model = matcher.group(1);
            String dependencies = matcher.group(2);
            List<String> namesForModel = dependencies == null || dependencies.isBlank() ? List.of()
                    : List.of(dependencies.strip().split("\\s+"));
            if (imports.putIfAbsent(model, namesForModel) != null) {
                throw new DbException("Duplicate MODEL.modelname declaration for " + model);
            }
        }
        if (offset != declarations.length()) throw new DbException("Invalid MODEL.modelname: " + names);
        return imports;
    }

    private static Map<String, String> readClassNames(Database db, MetadataScope scope) throws SQLException {
        String table = foldedName(CLASSNAME);
        requireMetadataColumns(db, table, "iliname", "sqlname");
        var result = new LinkedHashMap<String, String>();
        try (var stmt = scopedMetadata(db, table, "iliname, sqlname", "iliname", scope);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "CLASSNAME.iliname");
                if (!scope.includes(path)) continue;
                if (result.putIfAbsent(path, required(rows, 2, "CLASSNAME.sqlname")) != null) {
                    throw new DbException("Duplicate CLASSNAME mapping for " + path);
                }
            }
        }
        return result;
    }

    private static final class AttributeNames {
        private final Database db;
        private final Map<String, List<AttributeName>> mappings = new LinkedHashMap<>();

        AttributeNames(Database db) { this.db = db; }

        void add(String path, AttributeName name) {
            var alternatives = mappings.computeIfAbsent(path, unused -> new ArrayList<>());
            if (alternatives.stream().anyMatch(existing -> key(db, existing.owner()).equals(key(db, name.owner()))
                    && Objects.equals(existing.target(), name.target()))) {
                throw new DbException("Ambiguous ATTRNAME mapping for " + path);
            }
            alternatives.add(name);
        }

        AttributeName take(String path, String owner, String target) {
            var alternatives = mappings.getOrDefault(path, List.of());
            var matches = alternatives.stream().filter(value -> key(db, owner).equals(key(db, value.owner()))
                    && (target == null || value.target() != null && key(db, target).equals(key(db, value.target())))).toList();
            if (matches.size() != 1) throw new DbException("Missing or inconsistent ATTRNAME mapping for " + path);
            var selected = matches.getFirst();
            alternatives.remove(selected);
            if (alternatives.isEmpty()) mappings.remove(path);
            return selected;
        }

        boolean isEmpty() { return mappings.isEmpty(); }
    }

    private static AttributeNames readAttributeNames(Database db, MetadataScope scope) throws SQLException {
        String table = foldedName(ATTRNAME);
        requireMetadataColumns(db, table, "iliname", "sqlname", "colowner", "target");
        var result = new AttributeNames(db);
        try (var stmt = scopedMetadata(db, table, "iliname, sqlname, colowner, target", "iliname", scope);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "ATTRNAME.iliname");
                if (!scope.includes(path)) continue;
                var name = new AttributeName(required(rows, 2, "ATTRNAME.sqlname"),
                        required(rows, 3, "ATTRNAME.colowner"), rows.getString(4));
                result.add(path, name);
            }
        }
        return result;
    }

    private static Transformations readTransformations(Database db, MetadataScope scope) throws SQLException {
        String table = foldedName(TRAFO);
        requireMetadataColumns(db, table, "iliname", "tag", "setting");
        var result = new HashMap<String, String>();
        var secondary = new HashMap<String, String>();
        try (var stmt = scopedMetadata(db, table, "iliname, tag, setting", "iliname", scope);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "TRAFO.iliname");
                if (!scope.includes(path)) continue;
                String setting = required(rows, 3, "TRAFO.setting");
                String tag = rows.getString(2);
                if (Ili2dbLayout.SECONDARY_TABLE_TAG.equals(tag)) {
                    if (secondary.putIfAbsent(path, setting) != null) throw unsupported("TRAFO entries for " + path);
                } else if (!INHERITANCE_TAG.equals(tag) || result.putIfAbsent(path, setting) != null) {
                    throw unsupported("TRAFO entries for " + path);
                }
            }
        }
        return new Transformations(result, secondary);
    }

    private static Map<String, String> readInheritance(Database db, MetadataScope scope) throws SQLException {
        String table = foldedName(INHERITANCE);
        requireMetadataColumns(db, table, "thisclass", "baseclass");
        var result = new HashMap<String, String>();
        try (var stmt = scopedMetadata(db, table, "thisclass, baseclass", "thisclass", scope);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "INHERITANCE.thisClass");
                if (!scope.includes(path)) continue;
                if (result.containsKey(path)) throw new DbException("Duplicate INHERITANCE mapping for " + path);
                result.put(path, rows.getString(2));
            }
        }
        return result;
    }

    private record MetadataScope(Set<String> roots, Set<String> objects) {
        List<String> models() {
            var models = new HashSet<>(roots);
            for (var object : objects) models.add(modelName(object));
            return models.stream().sorted().toList();
        }

        boolean includes(String path) {
            if (roots.contains(modelName(path)) || objects.contains(path)) return true;
            // Secondary names append the owning class in parentheses after the attribute path.
            int owner = path.indexOf('(');
            int member = path.lastIndexOf('.', owner < 0 ? path.length() : owner);
            return member >= 0 && objects.contains(path.substring(0, member));
        }

        private static String modelName(String path) {
            int separator = path.indexOf('.');
            return separator < 0 ? path : path.substring(0, separator);
        }
    }

    private static PreparedStatement scopedMetadata(Database db, String table, String columns, String field,
                                                     MetadataScope scope) throws SQLException {
        var models = scope.models();
        String separator = (db.gpkg() ? "instr" : "strpos") + "(" + field + ", '.')";
        String placeholders = String.join(", ", Collections.nCopies(models.size(), "?"));
        var stmt = db.connection().prepareStatement("SELECT " + columns + " FROM " + db.table(table)
                + " WHERE substr(" + field + ", 1, " + separator + " - 1) IN (" + placeholders + ")");
        try {
            for (int i = 0; i < models.size(); i++) stmt.setString(i + 1, models.get(i));
            return stmt;
        } catch (SQLException failure) {
            stmt.close();
            throw failure;
        }
    }

    private static Column bindColumn(Database db, CatalogTable table,
                                     Map<SymbolRef, String> tables, Column column, AttributeName mapping,
                                     String path, Settings settings)
            throws SQLException {
        useColumn(db, table, mapping, path);
        if (column.target().isPresent()) {
            requireTarget(db, tables.get(column.targetRelation()), mapping, path);
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

    private static List<Column> bindReferences(Database db, CatalogTable table, AttributeNames names,
                                                DbInheritance layout, Map<SymbolRef, String> tableNames,
                                                Column reference, boolean ordered, Settings settings) throws SQLException {
        String path = reference.slot().name().declared().toString();
        var declared = reference.target().orElseThrow();
        var targets = layout.referenceTables(declared);
        if (targets.isEmpty() || (ordered && targets.size() > 1)) throw unsupported("reference storage at " + path);
        var columns = new ArrayList<Column>();
        for (var target : targets) {
            String targetName = tableNames.get(target);
            var binding = names.take(path, table.name(), targetName);
            var described = reference.withName(binding.name());
            if (!target.equals(declared) || targets.size() > 1) described = described.withReferenceTable(target);
            var column = bindColumn(db, table, tableNames, described, binding, path, settings);
            if (settings.inheritance() == DbInheritance.Profile.SMART2) {
                requireForeignKey(db, table.name(), binding.name(), targetName, path);
            }
            columns.add(ordered ? bindPosition(db, table, column) : column);
        }
        return columns;
    }

    private static void requireForeignKey(Database db, String owner, String column, String target, String path)
            throws SQLException {
        String fields = db.gpkg() ? "lower(tablename) = ? AND lower(columnname) = ?" : "tablename = ? AND columnname = ?";
        String sql = "SELECT setting FROM " + db.technicalTable(Ili2dbLayout.COLUMN_PROP) + " WHERE "
                + fields + " AND subtype IS NULL AND tag = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, key(db, owner));
            statement.setString(2, key(db, column));
            statement.setString(3, PREFIX + "foreignKey");
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !key(db, target).equals(key(db, required(rows, 1, "COLUMN_PROP.setting"))) || rows.next()) {
                    throw new DbException("Inconsistent COLUMN_PROP.foreignKey for " + path + " in " + owner + "." + column);
                }
            }
        }
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

    private static void requireTarget(Database db, String expected, AttributeName mapping, String path) {
        if (expected == null || mapping.target() == null || !key(db, expected).equals(key(db, mapping.target()))) {
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
