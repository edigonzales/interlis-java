package guru.interlis.db;

import com.google.gson.Gson;

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
import guru.interlis.model.EnumerationType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Modifier;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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
import static guru.interlis.db.Ili2dbLayout.T_DATASET_NAME;
import static guru.interlis.db.Ili2dbLayout.T_CREATE_DATE;
import static guru.interlis.db.Ili2dbLayout.T_ID;
import static guru.interlis.db.Ili2dbLayout.T_ILI_TID;
import static guru.interlis.db.Ili2dbLayout.T_LAST_CHANGE;
import static guru.interlis.db.Ili2dbLayout.T_SEQ;
import static guru.interlis.db.Ili2dbLayout.T_TYPE;
import static guru.interlis.db.Ili2dbLayout.T_USER;
import static guru.interlis.db.Ili2dbLayout.foldedName;

/** Binds a checked model to the mappings and physical catalog of an existing ili2db schema. */
final class DbSchemaReader {
    private static final Pattern MODEL_DECLARATION = Pattern.compile("([^\\s{}]+)(?:\\s*\\{([^{}]*)\\})?\\s*");

    private record StoredModel(String names, LanguageVersion version, String content, List<String> imports) { }
    private record AttributeName(String name, String owner, String target) { }
    record Settings(boolean splitGeometries, int defaultEpsg, String modelEpsgAssignments, boolean referenceEpsgHeader,
                    boolean multiSrs, Optional<DbCrsPair.Models> srsModels, boolean createGeometryIndexes, boolean sqlEnableNull,
                    DbInheritance.Profile inheritance, boolean strokeArcs,
                    Set<Ili2dbLayout.AttributeTransformation> structureTransformations, DbEnumOptions enumOptions,
                    Set<Ili2dbLayout.SchemaOption> schemaOptions) {
        CrsDefaults crs(String model) {
            return new CrsDefaults(defaultEpsg, modelEpsg(modelEpsgAssignments, model));
        }
    }

    private record Transformations(Map<String, String> classes, Map<String, String> secondary,
                                   Map<String, Ili2dbLayout.AttributeTransformation> attributes) { }

    private record CatalogTable(String name, Map<String, String> columns, Set<String> notNull, Set<String> used) { }
    private record PhysicalColumns(Map<String, String> types, Set<String> notNull) { }

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
        var initialPlan = readModelPlan(db, List.copyOf(models), token);
        var additionalTypes = switch (settings.inheritance()) {
            case SMART1 -> sharedSubtypes(db, initialPlan, token);
            case SMART2 -> storedReferenceSubtypes(db, initialPlan, token);
            case NEW_CLASS -> Set.<String>of();
        };
        var storageModels = new LinkedHashSet<>(models);
        additionalTypes.stream().sorted().map(MetadataScope::modelName).forEach(storageModels::add);
        TransferPlan plan = storageModels.equals(models) ? initialPlan : readModelPlan(db, List.copyOf(storageModels), token);
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
        var roots = DbMapping.storageRoots(plan, crsPair).stream().filter(ref -> plan.models().stream().anyMatch(model ->
                model.name().canonical().equals(ref) && models.contains(model.name().declared().last()))).toList();
        var selected = DbMapping.storageObjects(plan, roots, additionalTypes, token);
        DbMapping.checkBasketLayout(plan, selected, settings.schemaOptions());
        DbMapping.checkLayout(plan, selected, settings.enumOptions());
        if (!settings.multiSrs() && plan.topics().stream()
                .anyMatch(topic -> plan.rootModels().contains(topic.model()) && DbDomains.deferred(topic))) {
            throw unsupported("deferred generic domains require SETTINGS.useEpsgInNames");
        }
        var mappedModels = new HashSet<>(selectedModels);
        roots.stream().map(ref -> plan.model(ref).orElseThrow().name().declared().last()).forEach(mappedModels::add);
        var enumDefinitions = new LinkedHashMap<String, String>();
        var enumLookup = settings.enumOptions().lookup();
        List<DbEnumLookup.Definition> definitions = List.of();
        if (enumLookup == DbEnumOptions.Lookup.PER_DEFINITION || enumLookup == DbEnumOptions.Lookup.WITH_ID) {
            definitions = DbEnumLookup.definitions(plan, roots, selected, token);
            for (var definition : definitions) {
                var declaration = definition.declaration();
                var storedDefinition = enumLookup == DbEnumOptions.Lookup.WITH_ID
                        ? DbEnumLookup.root(plan, declaration) : declaration;
                enumDefinitions.put(DbEnumLookup.name(plan, declaration), DbEnumLookup.name(plan, storedDefinition));
            }
            if (enumLookup == DbEnumOptions.Lookup.PER_DEFINITION && DbEnumLookup.hasBoolean(plan, selected)) {
                enumDefinitions.put(DbEnumLookup.BOOLEAN_DEFINITION, DbEnumLookup.BOOLEAN_DEFINITION);
            }
        }
        var enumTableNames = new HashSet<>(enumDefinitions.values());
        var scope = new MetadataScope(mappedModels, selected.stream()
                .map(object -> object.name().declared().toString()).collect(Collectors.toSet()), enumTableNames);
        var classNames = readClassNames(db, scope);
        var unselectedOwners = new HashSet<String>();
        classNames.entrySet().removeIf(entry -> {
            if (scope.includes(entry.getKey())) return false;
            unselectedOwners.add(key(db, entry.getValue()));
            return true;
        });
        var attrNames = readAttributeNames(db, scope);
        var transformations = readTransformations(db, scope);
        var usedAttributeTransformations = new HashSet<String>();
        var wrappedGeometries = DbWrappedGeometry.discover(plan, selected,
                field -> transformations.attributes().get(field.name().declared().toString()));
        var geometryComponents = wrappedGeometries.values().stream().map(binding -> binding.component().symbol())
                .collect(Collectors.toSet());
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
            var strategy = DbInheritance.storedStrategy(object, transformation);
            declarations.add(new DbInheritance.Declaration(object, name, strategy));
            names.put(object.symbol(), name);
        }
        if (!transformations.classes().isEmpty() || !inheritance.isEmpty()) {
            throw new DbException("Extra CLASSNAME/TRAFO/INHERITANCE mappings for " + modelName);
        }
        checkSystemTables(db);
        var layout = new DbInheritance(plan, declarations, settings.inheritance(),
                settings.schemaOptions().contains(Ili2dbLayout.SchemaOption.TYPE_DISCRIMINATOR));
        if (enumLookup == DbEnumOptions.Lookup.WITH_ID) {
            var stored = DbEnumLookup.storedDefinitions(plan, definitions, layout).stream()
                    .map(definition -> DbEnumLookup.name(plan, definition.declaration())).collect(Collectors.toSet());
            enumDefinitions.keySet().retainAll(stored);
        }
        var enumerations = readEnumTables(db, classNames, usedNames, enumDefinitions, enumLookup, token);
        for (var alias : layout.aliases()) {
            names.put(alias.object().symbol(), names.get(alias.owner()));
        }
        for (var association : associations) {
            if (association.linkTable() && layout.referenceTables(association.owner()).size() != 1) {
                throw unsupported("embedded link table with multiple owner tables at " + association.object().name().declared());
            }
        }
        var catalog = readCatalog(db, plan, layout.relations(), settings, token);
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
                            role.columns(), role.slot().role().ordered(), settings));
                }
            }
            for (var contributor : relation.contributors()) {
                var defaults = settings.crs(plan.model(contributor.model()).orElseThrow().name().declared().last());
                for (var attr : DbMapping.storageAttributes(plan, settings.inheritance(), relation, contributor)) {
                    String path = attr.name().declared().toString();
                    var transformation = transformations.attributes().get(path);
                    if (transformation == Ili2dbLayout.AttributeTransformation.CATALOGUE_REFERENCE) {
                        var binding = DbCatalogueReference.describe(plan, attr)
                                .orElseThrow(() -> unsupported("catalogue reference at " + path));
                        if (settings.inheritance() == DbInheritance.Profile.SMART1) {
                            throw unsupported("catalogue reference inheritance at " + path);
                        }
                        columns.addAll(bindReferences(db, parent, attrNames, layout, names,
                                DbMapping.catalogueColumn(plan, contributor, attr, binding), false, settings));
                        usedAttributeTransformations.add(path);
                        continue;
                    }
                    if (transformation == Ili2dbLayout.AttributeTransformation.MULTILINGUAL
                            || transformation == Ili2dbLayout.AttributeTransformation.LOCALISED) {
                        var binding = DbTextExpansion.describe(plan, attr, transformation)
                                .orElseThrow(() -> unsupported("text expansion at " + path));
                        var mapping = attrNames.take(path, parent.name(), null);
                        var column = DbMapping.textColumn(attr, mapping.name(), binding);
                        columns.add(bindColumn(db, parent, names, column, mapping, path, settings));
                        usedAttributeTransformations.add(path);
                        continue;
                    }
                    if (transformation == Ili2dbLayout.AttributeTransformation.EXPANDED_STRUCTURE) {
                        var binding = DbExpandedStructure.bind(plan, attr, names,
                                field -> transformations.attributes().get(field.name().declared().toString())
                                        == Ili2dbLayout.AttributeTransformation.EXPANDED_STRUCTURE, (field, kind) -> {
                                    settings.enumOptions().checkExpandedField(kind, field);
                                    var stored = attrNames.take(field, parent.name(), null);
                                    useColumn(db, parent, stored, field);
                                    if (stored.target() != null) throw new DbException("Unexpected ATTRNAME.target for " + field);
                                    requireColumns(db, parent.name(), parent.columns(), Map.of(stored.name(), kind));
                                    return stored.name();
                                });
                        columns.add(DbMapping.expandedColumn(plan, attr, binding));
                        usedAttributeTransformations.add(path);
                        continue;
                    }
                    if (transformation == Ili2dbLayout.AttributeTransformation.JSON_STRUCTURE) {
                        var stored = attrNames.take(path, parent.name(), null);
                        useColumn(db, parent, stored, path);
                        if (stored.target() != null) throw new DbException("Unexpected ATTRNAME.target for " + path);
                        String expected = db.gpkg() ? "text" : "jsonb";
                        if (!expected.equals(parent.columns().get(key(db, stored.name())))) {
                            throw new DbException("Expected " + expected + " for JSON column " + parent.name() + "." + stored.name());
                        }
                        columns.add(DbMapping.jsonColumn(plan, attr, stored.name(), DbJsonStructure.describe(plan, attr)));
                        usedAttributeTransformations.add(path);
                        continue;
                    }
                    if (transformation == Ili2dbLayout.AttributeTransformation.ARRAY) {
                        var binding = DbArray.describe(plan, layout, object, attr);
                        if (binding.kind() == Kind.ENUMERATION && settings.enumOptions().numeric()) {
                            var encoding = settings.enumOptions().itfCodes()
                                    ? DbEnumeration.itfCodes((EnumerationType) binding.type(), path)
                                    : enumeration(enumerations, DbEnumLookup.name(plan, binding.enumDefinition(plan, attr)));
                            binding = binding.withEnumeration(encoding);
                        }
                        var stored = attrNames.take(path, parent.name(), null);
                        useColumn(db, parent, stored, path);
                        if (binding.target().isPresent()) {
                            String target = names.get(binding.target().orElseThrow());
                            requireTarget(db, target, stored, path);
                            requireForeignKey(db, parent.name(), stored.name(), target, path);
                        } else if (stored.target() != null) {
                            throw new DbException("Unexpected ATTRNAME.target for " + path);
                        }
                        String expected = db.gpkg() ? "text" : "_" + binding.pgElementType();
                        if (!expected.equals(parent.columns().get(key(db, stored.name())))) {
                            throw new DbException("Expected " + expected + " for array column " + parent.name() + "." + stored.name());
                        }
                        columns.add(DbMapping.arrayColumn(plan, attr, stored.name(), binding));
                        usedAttributeTransformations.add(path);
                        continue;
                    }
                    var wrapped = wrappedGeometries.get(attr.symbol());
                    var composition = wrapped == null ? DbMapping.describeComposition(plan, object, attr, path)
                            : Optional.<Composition>empty();
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
                    var declaration = attr.member().overridden() ? object : contributor;
                    var columnCrs = defaults;
                    if (wrapped != null) {
                        var componentModel = plan.object(wrapped.element()).orElseThrow().model();
                        columnCrs = settings.crs(plan.model(componentModel).orElseThrow().name().declared().last());
                        usedAttributeTransformations.add(path);
                    }
                    for (var described : DbMapping.storageColumns(plan, declaration, attr, columnCrs, crsPair, wrapped,
                            geometryComponents.contains(attr.symbol()))) {
                        if (described.kind() == Kind.REFERENCE) {
                            columns.addAll(bindReferences(db, parent, attrNames, layout, names, described, false, settings));
                            continue;
                        }
                        String attribute = Ili2dbLayout.geometryAttribute(path, described.epsgCode(), settings.multiSrs());
                        String owner = object.name().declared().toString();
                        String secondaryKey = Ili2dbLayout.secondaryClass(attribute, owner);
                        String table = settings.splitGeometries() && described.kind().geometry() ? classNames.remove(secondaryKey) : null;
                        var mapping = attrNames.take(attribute, table == null ? parent.name() : table, null);
                        var column = described.withName(mapping.name()).withStrokeArcs(plan, settings.strokeArcs());
                        if (column.kind() == Kind.ENUMERATION) {
                            if (settings.enumOptions().itfCodes()) {
                                column = column.withItfCodes();
                            } else if (enumLookup == DbEnumOptions.Lookup.WITH_ID) {
                                String definition = DbEnumLookup.name(plan, DbEnumeration.definition(plan, attr));
                                column = column.withEnumeration(enumeration(enumerations, definition));
                            }
                        }
                        if (settings.enumOptions().textColumns() && (column.kind() == Kind.ENUMERATION || column.kind() == Kind.BOOLEAN)) {
                            column = column.withEnumText(DbEnumText.describe(plan, column, settings.enumOptions().beautify()));
                        }
                        if (table != null) {
                            var secondaryTransformation = transformations.secondary()
                                    .remove(Ili2dbLayout.secondaryTransformation(path, owner, column.epsgCode()));
                            if (secondaryTransformation == null || !key(db, table).equals(key(db, secondaryTransformation))
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
                    relation.oidDomain(), settings.schemaOptions().contains(Ili2dbLayout.SchemaOption.TID_COLUMN), secondary));
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
        for (var path : transformations.attributes().keySet()) {
            if (!usedAttributeTransformations.contains(path)) throw unsupported("attribute TRAFO entry for " + path);
        }
        if (settings.inheritance() != DbInheritance.Profile.NEW_CLASS) {
            unselectedOwners.removeAll(usedNames);
            attrNames.ignoreOwners(unselectedOwners);
            if (!attrNames.isEmpty()) {
                var roleOwners = associations.stream().filter(association -> !association.linkTable())
                        .collect(Collectors.toMap(association -> association.role().name().declared().toString(),
                                association -> plan.object(association.owner()).orElseThrow().name().declared().toString()));
                ignoreOtherModelCopies(db, scope, usedNames, attrNames, roleOwners);
            }
        }
        if (!attrNames.isEmpty()) throw new DbException("Extra ATTRNAME mappings for " + modelName);
        var notNullRoles = tables.stream().flatMap(table -> {
            var notNull = catalog.get(table.object().symbol()).notNull();
            return table.columns().stream().filter(column -> column.slot() instanceof RoleSlot
                    && notNull.contains(key(db, column.name())));
        }).map(column -> column.slot().symbol()).collect(Collectors.toUnmodifiableSet());
        var mapping = new DbMapping(plan, version, tables, compositions, associations, crsPair, requestedRoots,
                layout, settings.schemaOptions(), notNullRoles);
        mapping.checkEnumLookupIds(settings.enumOptions());
        return mapping;
    }

    private static Set<String> sharedSubtypes(Database db, TransferPlan plan, CancellationToken token) throws SQLException {
        var selected = DbMapping.storageObjects(plan, DbMapping.storageRoots(plan, Optional.empty()), token).stream()
                .map(object -> object.name().declared().toString()).collect(Collectors.toSet());
        return DbStoredClasses.read(db, token).sharedSubtypes(selected);
    }

    private static Set<String> storedReferenceSubtypes(Database db, TransferPlan plan, CancellationToken token) throws SQLException {
        var selected = DbMapping.storageObjects(plan, DbMapping.storageRoots(plan, Optional.empty()), token);
        var references = new LinkedHashMap<String, Set<String>>();
        for (var object : selected) {
            for (var slot : object.slots()) {
                if (slot instanceof RoleSlot || slot instanceof AttributeSlot attribute
                        && DbStructureShape.type(plan, attribute) instanceof ReferenceType) {
                    references.put(slot.name().declared().toString(), qualifiedReferenceRoots(plan, slot));
                }
            }
        }
        if (references.isEmpty()) return Set.of();
        var known = selected.stream().map(object -> object.name().declared().toString()).collect(Collectors.toSet());
        var stored = DbStoredClasses.read(db, token);
        var additional = new HashSet<String>();
        for (var entry : readReferenceNames(db, token).entrySet()) {
            var roots = references.get(entry.getKey());
            if (roots == null) continue;
            for (var binding : entry.getValue()) {
                var owner = stored.physicalType(binding.owner(), db.gpkg());
                if (owner == null || !known.contains(owner.path())) continue;
                var target = stored.physicalType(binding.target(), db.gpkg());
                if (target == null || known.contains(target.path())) continue;
                var ancestry = stored.ancestry(target);
                if (!Collections.disjoint(ancestry, roots)) additional.addAll(ancestry);
            }
        }
        additional.removeAll(known);
        return additional;
    }

    private static void ignoreOtherModelCopies(Database db, MetadataScope scope, Set<String> selectedOwners,
                                               AttributeNames attributes, Map<String, String> roleOwners) throws SQLException {
        var bases = new HashMap<String, String>();
        var owners = new HashMap<String, String>();
        String sql = """
                SELECT i.thisclass, i.baseclass, c.sqlname, t.setting
                FROM %s i JOIN %s c ON c.iliname = i.thisclass
                JOIN %s t ON t.iliname = i.thisclass AND t.tag = ?
                WHERE i.baseclass IS NOT NULL
                """.formatted(db.technicalTable(INHERITANCE), db.technicalTable(CLASSNAME), db.technicalTable(TRAFO));
        try (var stmt = db.connection().prepareStatement(sql)) {
            stmt.setString(1, INHERITANCE_TAG);
            try (var rows = stmt.executeQuery()) {
                while (rows.next()) {
                    String type = required(rows, 1, "INHERITANCE.thisClass");
                    bases.put(type, required(rows, 2, "INHERITANCE.baseClass"));
                    String owner = key(db, required(rows, 3, "CLASSNAME.sqlname"));
                    String strategy = required(rows, 4, "TRAFO.setting");
                    if ((NEW_CLASS.equals(strategy) || "newAndSubClass".equals(strategy))
                            && !scope.includes(type) && !selectedOwners.contains(owner)) {
                        owners.put(owner, type);
                    }
                }
            }
        }
        attributes.ignoreInheritedCopies(owners, bases, roleOwners);
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
                                                           List<DbInheritance.Relation> relations, Settings settings,
                                                           CancellationToken token)
            throws SQLException {
        var result = new HashMap<SymbolRef, CatalogTable>();
        for (var relation : relations) {
            token.throwIfCancellationRequested();
            var object = relation.object();
            String name = relation.declaration().sqlName();
            var physicalColumns = readPhysicalColumns(db, name);
            var physical = physicalColumns.types();
            requireIdPrimaryKey(db, name);
            var used = bindRowIdentity(db, name, physical, settings);
            bindAdditionalColumns(db, name, physical, used, settings);
            boolean subtype = relation.base().isPresent();
            var domain = relation.oidDomain();
            if (!subtype && (domain.isPresent() || object.kind() != SymbolKind.ASSOCIATION
                    && settings.schemaOptions().contains(Ili2dbLayout.SchemaOption.TID_COLUMN))) {
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
            result.put(object.symbol(), new CatalogTable(name, physical, physicalColumns.notNull(), used));
        }
        return result;
    }

    private static void bindAdditionalColumns(Database db, String table, Map<String, String> physical, Set<String> used,
                                              Settings settings) {
        if (settings.schemaOptions().contains(Ili2dbLayout.SchemaOption.DATASET_COLUMN)) {
            requireColumns(db, table, physical, Map.of(T_DATASET_NAME, Kind.TEXT));
            used.add(T_DATASET_NAME);
        }
        if (settings.schemaOptions().contains(Ili2dbLayout.SchemaOption.STANDARD_COLUMNS)) {
            var columns = Map.of(T_LAST_CHANGE, Kind.DATETIME, T_CREATE_DATE, Kind.DATETIME, T_USER, Kind.TEXT);
            requireColumns(db, table, physical, columns);
            used.addAll(columns.keySet());
        }
    }

    private static Set<String> bindRowIdentity(Database db, String table, Map<String, String> physical, Settings settings) {
        requireColumns(db, table, physical, Map.of(T_ID, Kind.REFERENCE));
        var used = new HashSet<>(Set.of(T_ID));
        if (settings.schemaOptions().contains(Ili2dbLayout.SchemaOption.BASKET_COLUMN)) {
            requireColumns(db, table, physical, Map.of(T_BASKET, Kind.REFERENCE));
            used.add(T_BASKET);
        }
        return used;
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
        boolean sqlEnableNull = optionalSetting(settings, PREFIX + "SqlNull", "enable");
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
        var enumOptions = new DbEnumOptions(optionalSetting(settings, Ili2dbLayout.ENUM_ITF_CODE, "yes"),
                optionalSetting(settings, Ili2dbLayout.ENUM_TEXT_COLUMN, "addTxtCol"),
                optionalSetting(settings, Ili2dbLayout.ENUM_BEAUTIFY, "underscore"),
                switch (settings.get(Ili2dbLayout.ENUM_DEFINITIONS)) {
                    case "singleTable" -> DbEnumOptions.Lookup.SINGLE;
                    case "multiTable" -> DbEnumOptions.Lookup.PER_DEFINITION;
                    case "multiTableWithId" -> DbEnumOptions.Lookup.WITH_ID;
                    case null, default -> DbEnumOptions.Lookup.NONE;
                });
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
        boolean strokeArcs = settings.containsKey(Ili2dbLayout.STROKE_ARCS);
        if (strokeArcs && !Ili2dbLayout.STROKE_ARCS_ENABLED.equals(settings.get(Ili2dbLayout.STROKE_ARCS))) {
            throw unsupported("SETTINGS.StrokeArcs");
        }
        var transformations = EnumSet.noneOf(Ili2dbLayout.AttributeTransformation.class);
        for (var transformation : List.of(Ili2dbLayout.AttributeTransformation.EXPANDED_STRUCTURE,
                Ili2dbLayout.AttributeTransformation.JSON_STRUCTURE, Ili2dbLayout.AttributeTransformation.ARRAY)) {
            if (!settings.containsKey(transformation.tag)) continue;
            if (!transformation.setting.equals(settings.get(transformation.tag))) {
                throw unsupported("SETTINGS." + transformation.tag.substring(PREFIX.length()));
            }
            transformations.add(transformation);
        }
        var structureTransformations = Set.copyOf(transformations);
        var schemaOptions = EnumSet.noneOf(Ili2dbLayout.SchemaOption.class);
        for (var option : Ili2dbLayout.SchemaOption.values()) {
            if ("True".equals(option.setting) && "False".equals(settings.get(option.tag))) continue;
            if (optionalSetting(settings, option.tag, option.setting)) schemaOptions.add(option);
        }
        var enabled = Set.copyOf(schemaOptions);
        DbScalarText.checkProfile(enabled, enumOptions, structureTransformations);
        if (enabled.contains(Ili2dbLayout.SchemaOption.META_INFO)) {
            requireMetadataColumns(db, foldedName(Ili2dbLayout.META_ATTRS), "ilielement", "attr_name", "attr_value");
        }
        if (Ili2dbLayout.MULTIPLE_GEOMETRIES.equals(geometryLayout)) {
            return new Settings(false, defaultEpsg, modelEpsg, referenceEpsgHeader, multiSrs, srsModels, indexes,
                    sqlEnableNull, inheritance, strokeArcs, structureTransformations, enumOptions, enabled);
        }
        if (geometryLayout != null && !Ili2dbLayout.SINGLE_GEOMETRY.equals(geometryLayout)) {
            throw unsupported("SETTINGS.geomAttrPerTable=" + geometryLayout);
        }
        return new Settings(db.gpkg() || Ili2dbLayout.SINGLE_GEOMETRY.equals(geometryLayout), defaultEpsg, modelEpsg,
                referenceEpsgHeader, multiSrs, srsModels, indexes, sqlEnableNull, inheritance, strokeArcs,
                structureTransformations, enumOptions, enabled);
    }

    private static boolean optionalSetting(Map<String, String> settings, String key, String supported) {
        String value = settings.get(key);
        if (value == null) return false;
        if (!supported.equals(value)) throw unsupported("SETTINGS." + key.substring(PREFIX.length()));
        return true;
    }

    static SqlNames namesForExtension(Database db, List<String> models, DbInheritance.Profile profile) throws SQLException {
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
        var strategies = new HashMap<String, String>();
        if (profile != DbInheritance.Profile.NEW_CLASS) {
            String table = foldedName(TRAFO);
            requireMetadataColumns(db, table, "iliname", "tag", "setting");
            try (var stmt = db.connection().prepareStatement("SELECT iliname, setting FROM " + db.table(table) + " WHERE tag = ?")) {
                stmt.setString(1, INHERITANCE_TAG);
                try (var rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        String path = required(rows, 1, "TRAFO.iliname");
                        if (strategies.putIfAbsent(path, required(rows, 2, "TRAFO.setting")) != null) {
                            throw new DbException("Duplicate TRAFO inheritance mapping for " + path);
                        }
                    }
                }
            }
        }
        return new SqlNames(classes, relations, columns, attributes, strategies);
    }

    static SchemaPlan planSmartExtension(Database db, SchemaPlan extension, List<SchemaPlan.ModelFile> newFiles,
                                          SqlNames names, CancellationToken token) throws SQLException {
        var mapping = extension.mapping();
        var newModels = newFiles.stream().flatMap(file -> storedDeclarations(file.modelNames()).keySet().stream())
                .collect(Collectors.toSet());
        for (var declaration : mapping.inheritance().declarations()) {
            token.throwIfCancellationRequested();
            var object = declaration.object();
            String path = object.name().declared().toString();
            if (object.kind() == SymbolKind.CLASS && !newModels.contains(MetadataScope.modelName(path)) && !names.hasClass(path)) {
                throw new DbException("Missing stored CLASSNAME mapping for " + path);
            }
        }
        var checkedTables = new HashSet<String>();
        for (var table : extension.addedTables()) {
            for (var column : table.columns()) {
                token.throwIfCancellationRequested();
                if (column.kind() != Kind.REFERENCE || !(column.slot() instanceof RoleSlot role) || !role.role().external()) continue;
                var target = mapping.table(column.targetRelation());
                if (names.hasClass(target.object().name().declared().toString()) && checkedTables.add(target.name())) {
                    requirePhysicalTable(db, target.name());
                    requireIdPrimaryKey(db, target.name());
                }
            }
        }
        var references = planStoredReferenceExpansion(db, extension, names, token);
        var columns = new ArrayList<SchemaPlan.AddedColumn>();
        var discriminators = new ArrayList<SchemaPlan.DiscriminatorChange>();
        var aliases = mapping.inheritance().aliases().stream()
                .filter(alias -> !names.hasClass(alias.object().name().declared().toString())
                        && names.hasClass(mapping.table(alias.owner()).object().name().declared().toString()))
                .collect(Collectors.groupingBy(DbMapping.StorageAlias::owner, LinkedHashMap::new, Collectors.toList()));
        for (var entry : aliases.entrySet()) {
            token.throwIfCancellationRequested();
            var owner = mapping.table(entry.getKey());
            String ownerPath = owner.object().name().declared().toString();
            var stored = read(db, List.of(MetadataScope.modelName(ownerPath)), token);
            var oldTable = stored.tables().stream().filter(table -> table.object().name().declared().toString().equals(ownerPath))
                    .findFirst().orElseThrow(() -> new DbException("Missing stored Smart1 table for " + ownerPath));
            if (oldTable.subtype() || !owner.hasDiscriminator()) {
                throw unsupported("Smart1 --add-models requires a shared root table in " + owner.name());
            }
            var oldTypes = stored.objectStorage().stream().filter(type -> type.root().equals(oldTable)
                    && !type.object().modifiers().contains(Modifier.ABSTRACT)).map(DbMapping.ObjectStorage::discriminator).sorted().toList();
            Optional<String> initialType = Optional.empty();
            Optional<String> existingCheck = Optional.empty();
            if (oldTable.hasDiscriminator()) {
                requireStoredDiscriminatorTypes(db, owner.name(), oldTypes);
                if (extension.schemaOptions().contains(Ili2dbLayout.SchemaOption.TYPE_CONSTRAINT)) {
                    existingCheck = Optional.of(storedDiscriminatorCheck(db, owner.name(), oldTypes));
                }
            } else {
                if (oldTypes.size() != 1) throw new DbException("Cannot determine initial T_Type for " + owner.name());
                requireStoredDiscriminatorTypes(db, owner.name(), List.of());
                initialType = Optional.of(oldTypes.getFirst());
            }
            var types = new java.util.TreeSet<>(oldTypes);
            var physical = readPhysicalColumns(db, owner.name()).types();
            for (var alias : entry.getValue()) {
                var object = alias.object();
                if (!object.modifiers().contains(Modifier.ABSTRACT)) types.add(alias.discriminator());
                if (mapping.associations().stream().anyMatch(association -> association.owner().equals(object.symbol()))) {
                    throw unsupported("new Smart1 roles in shared table " + owner.name());
                }
                for (var attribute : DbMapping.attributes(object)) {
                    var fields = owner.columns().stream().filter(column -> column.slot().symbol().equals(attribute.symbol())).toList();
                    if (fields.size() != 1 || fields.getFirst().kind().geometry() || fields.getFirst().target().isPresent()
                            || fields.getFirst().transformation() != null || fields.getFirst().enumText().isPresent()
                            || fields.getFirst().kind() == Kind.ENUMERATION && extension.enumOptions().lookup() == DbEnumOptions.Lookup.WITH_ID) {
                        throw unsupported("Smart1 attribute in shared table at " + attribute.name().declared());
                    }
                    var field = fields.getFirst();
                    if (physical.containsKey(key(db, field.name()))) {
                        throw new DbException("Existing column conflicts with " + owner.name() + "." + field.name());
                    }
                    columns.add(new SchemaPlan.AddedColumn(owner.name(), field,
                            SchemaPlan.columnChecks(List.of(field), db.gpkg(), extension.schemaOptions())));
                }
            }
            discriminators.add(new SchemaPlan.DiscriminatorChange(owner.name(), List.copyOf(types), initialType, existingCheck));
        }
        return extension.withExtension(newFiles, columns, discriminators, references);
    }

    private static String storedDiscriminatorCheck(Database db, String table, List<String> types) throws SQLException {
        var names = new ArrayList<String>();
        if (db.gpkg()) {
            String values = types.stream().map(SchemaSql::literal).map(Pattern::quote).collect(Collectors.joining("\\s*,\\s*"));
            var namedCheck = Pattern.compile("(?i:CONSTRAINT\\s+)(?:\"([^\"]+)\"|([a-zA-Z0-9_]+))"
                    + "(?i:\\s+CHECK\\s*\\(\\s*\"?t_type\"?\\s+IN\\s*\\(\\s*)" + values + "\\s*\\)\\s*\\)");
            try (var stmt = db.connection().prepareStatement("SELECT sql FROM sqlite_schema WHERE type = 'table' AND name = ?")) {
                stmt.setString(1, table);
                try (var rows = stmt.executeQuery()) {
                    if (rows.next()) {
                        var matches = namedCheck.matcher(rows.getString(1));
                        while (matches.find()) names.add(matches.group(1) == null ? matches.group(2) : matches.group(1));
                    }
                }
            }
        } else {
            String sql = """
                    SELECT c.conname, pg_get_expr(c.conbin, c.conrelid)
                    FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
                    JOIN pg_namespace n ON n.oid = t.relnamespace
                    WHERE n.nspname = ? AND t.relname = ? AND c.contype = 'c'
                    """;
            String values = types.stream().map(SchemaSql::literal).collect(Collectors.joining(","));
            try (var stmt = db.connection().prepareStatement(sql)) {
                stmt.setString(1, db.schema());
                stmt.setString(2, table);
                try (var rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        String expression = rows.getString(2).replaceAll("::character varying(?:\\[\\])?|::text(?:\\[\\])?|[()\\s]", "");
                        if (expression.equals("t_type=ANYARRAY[" + values + "]")
                                || types.size() == 1 && expression.equals("t_type=" + values)) {
                            names.add(rows.getString(1));
                        }
                    }
                }
            }
        }
        if (names.size() != 1) throw new DbException("Expected one named T_Type check for " + table);
        return names.getFirst();
    }

    private static void requireStoredDiscriminatorTypes(Database db, String table, List<String> types) throws SQLException {
        String sql = "SELECT setting FROM " + db.technicalTable(Ili2dbLayout.COLUMN_PROP)
                + " WHERE tablename = ? AND lower(columnname) = ? AND tag = ? AND subtype IS NULL";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, T_TYPE);
            statement.setString(3, PREFIX + "types");
            try (var rows = statement.executeQuery()) {
                boolean found = rows.next();
                if (types.isEmpty() ? found : !found || !new Gson().toJson(types).equals(rows.getString(1)) || rows.next()) {
                    throw new DbException("Inconsistent COLUMN_PROP.types for " + table + "." + T_TYPE);
                }
            }
        }
    }

    private static List<SchemaPlan.AddedReference> planStoredReferenceExpansion(Database db, SchemaPlan extension,
                                                                                SqlNames names, CancellationToken token)
            throws SQLException {
        var mapping = extension.mapping();
        var plan = mapping.plan();
        var affectedTargets = new LinkedHashMap<String, Set<String>>();
        for (var table : extension.addedTables()) {
            if (table.object().kind() != SymbolKind.CLASS) continue;
            for (var base = table.object().baseType().flatMap(plan::object); base.isPresent();
                 base = base.orElseThrow().baseType().flatMap(plan::object)) {
                var ancestor = base.orElseThrow();
                String path = ancestor.name().declared().toString();
                if (names.hasClass(path) && mapping.inheritance().referenceTables(ancestor.symbol()).contains(table.object().symbol())) {
                    affectedTargets.computeIfAbsent(path, unused -> new LinkedHashSet<>()).add(table.name());
                }
            }
        }
        if (affectedTargets.isEmpty()) return List.of();

        // A previously mapped reference can be declared in a model outside the new source closure.
        var storedNames = readReferenceNames(db, token);
        var slotsByModel = storedNames.keySet().stream().collect(Collectors.groupingBy(MetadataScope::modelName,
                LinkedHashMap::new, Collectors.toSet()));
        var storedClasses = DbStoredClasses.read(db, token);
        var additions = new ArrayList<SchemaPlan.AddedReference>();
        var processed = new HashSet<String>();
        var compiledModels = plan.models().stream().map(model -> model.name().declared().last()).collect(Collectors.toSet());
        for (var entry : slotsByModel.entrySet()) {
            token.throwIfCancellationRequested();
            var source = compiledModels.contains(entry.getKey()) ? plan : readModelPlan(db, List.of(entry.getKey()), token);
            for (var object : source.objects()) {
                for (var slot : object.slots()) {
                    String path = slot.name().declared().toString();
                    if (!entry.getValue().contains(path) || processed.contains(path)) continue;
                    var roots = qualifiedReferenceRoots(source, slot);
                    var targets = roots.stream().filter(affectedTargets::containsKey).flatMap(root -> affectedTargets.get(root).stream())
                            .collect(Collectors.toCollection(java.util.TreeSet::new));
                    if (targets.isEmpty()) continue;
                    if (mapping.profile() != DbInheritance.Profile.SMART2 || !expandableReference(source, object, slot)) {
                        throw new DbException("--add-models would change stored reference " + path);
                    }
                    processed.add(path);
                    var byOwner = storedNames.get(path).stream().collect(Collectors.groupingBy(AttributeName::owner,
                            LinkedHashMap::new, Collectors.toList()));
                    for (var owner : byOwner.entrySet()) {
                        String table = owner.getKey();
                        var ownerType = storedClasses.physicalType(table, db.gpkg());
                        if (ownerType == null) throw new DbException("Missing stored reference owner for " + path + " in " + table);
                        var columns = readPhysicalColumns(db, table).types();
                        for (var binding : owner.getValue()) {
                            requireColumns(db, table, columns, Map.of(binding.name(), Kind.REFERENCE));
                            requireForeignKey(db, table, binding.name(), binding.target(), path);
                        }
                        for (String target : targets) {
                            String column = names.polymorphicColumn(table, slot.name().declared().last(), target);
                            if (columns.containsKey(key(db, column))) {
                                throw new DbException("Existing column conflicts with " + table + "." + column);
                            }
                            additions.add(new SchemaPlan.AddedReference(MetadataScope.modelName(ownerType.path()), path, table, column, target));
                        }
                    }
                }
            }
        }
        return additions;
    }

    private static boolean expandableReference(TransferPlan plan, ObjectDescriptor object, TransferSlot slot) {
        return switch (slot) {
            case RoleSlot role -> object.kind() == SymbolKind.ASSOCIATION
                    && DbMapping.embeddedAssociation(plan, object, "", DbMapping.describeRoles(plan, object)).stream()
                    .anyMatch(association -> association.role().symbol().equals(role.symbol())
                            && !association.linkTable() && !association.ordered());
            case AttributeSlot attribute -> (object.kind() == SymbolKind.CLASS || object.kind() == SymbolKind.STRUCTURE)
                    && DbStructureShape.type(plan, attribute) instanceof ReferenceType reference && reference.restrictions().isEmpty();
        };
    }

    private static Map<String, List<AttributeName>> readReferenceNames(Database db, CancellationToken token) throws SQLException {
        var bindings = new LinkedHashMap<String, List<AttributeName>>();
        String sql = "SELECT iliname, sqlname, colowner, target FROM " + db.technicalTable(ATTRNAME) + " WHERE target IS NOT NULL";
        try (var stmt = db.connection().createStatement(); var rows = stmt.executeQuery(sql)) {
            while (rows.next()) {
                token.throwIfCancellationRequested();
                String path = required(rows, 1, "ATTRNAME.iliname");
                bindings.computeIfAbsent(path, unused -> new ArrayList<>()).add(new AttributeName(
                        required(rows, 2, "ATTRNAME.sqlname"), required(rows, 3, "ATTRNAME.colowner"), required(rows, 4, "ATTRNAME.target")));
            }
        }
        return bindings;
    }

    private static Set<String> qualifiedReferenceRoots(TransferPlan plan, TransferSlot slot) {
        return referenceRoots(plan, slot).stream().map(id -> new SymbolRef(plan.snapshotId(), id))
                .flatMap(ref -> plan.object(ref).stream()).map(target -> target.name().declared().toString()).collect(Collectors.toSet());
    }

    private static List<SymbolId> referenceRoots(TransferPlan plan, TransferSlot slot) {
        return switch (slot) {
            case RoleSlot role -> role.role().targets().stream().flatMap(target -> target.allowedRoots().stream()).toList();
            case AttributeSlot attribute -> DbStructureShape.type(plan, attribute) instanceof ReferenceType reference
                    ? reference.targetRoots() : DbCatalogueReference.describe(plan, attribute)
                    .map(binding -> binding.reference().targetRoots()).orElseGet(List::of);
        };
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
        var plan = DbModelSources.compile(models, files.values(), "MODEL.content", true, token).plan();
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

        void ignoreOwners(Set<String> owners) {
            mappings.values().forEach(names -> names.removeIf(name -> owners.contains(key(db, name.owner()))));
            mappings.values().removeIf(List::isEmpty);
        }

        void ignoreInheritedCopies(Map<String, String> owners, Map<String, String> bases, Map<String, String> roleOwners) {
            for (var entry : mappings.entrySet()) {
                int member = entry.getKey().lastIndexOf('.');
                if (member < 0) continue;
                String declaration = roleOwners.getOrDefault(entry.getKey(), entry.getKey().substring(0, member));
                entry.getValue().removeIf(name -> inheritsFrom(owners.get(key(db, name.owner())), declaration, bases));
            }
            mappings.values().removeIf(List::isEmpty);
        }

        private static boolean inheritsFrom(String type, String declaration, Map<String, String> bases) {
            var seen = new HashSet<String>();
            while ((type = bases.get(type)) != null && seen.add(type)) {
                if (type.equals(declaration)) return true;
            }
            return false;
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
        var attributes = new HashMap<String, Ili2dbLayout.AttributeTransformation>();
        try (var stmt = scopedMetadata(db, table, "iliname, tag, setting", "iliname", scope);
             var rows = stmt.executeQuery()) {
            while (rows.next()) {
                String path = required(rows, 1, "TRAFO.iliname");
                if (!scope.includes(path)) continue;
                String setting = required(rows, 3, "TRAFO.setting");
                String tag = rows.getString(2);
                var transformation = Ili2dbLayout.AttributeTransformation.fromTag(tag);
                if (Ili2dbLayout.SECONDARY_TABLE_TAG.equals(tag)) {
                    if (secondary.putIfAbsent(path, setting) != null) throw unsupported("TRAFO entries for " + path);
                } else if (transformation != null) {
                    if (!transformation.setting.equals(setting) || attributes.putIfAbsent(path, transformation) != null) {
                        throw unsupported("attribute TRAFO entry for " + path);
                    }
                } else if (!INHERITANCE_TAG.equals(tag) || result.putIfAbsent(path, setting) != null) {
                    throw unsupported("TRAFO entries for " + path);
                }
            }
        }
        return new Transformations(result, secondary, attributes);
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

    private record MetadataScope(Set<String> roots, Set<String> objects, Set<String> enums) {
        List<String> models() {
            var models = new HashSet<>(roots);
            for (var object : objects) models.add(modelName(object));
            for (var definition : enums) models.add(modelName(definition));
            return models.stream().sorted().toList();
        }

        boolean includes(String path) {
            int owner = path.indexOf('(');
            if (owner >= 0 && path.endsWith(")")) return includes(path.substring(owner + 1, path.length() - 1));
            if (roots.contains(modelName(path)) || objects.contains(path) || enums.contains(path)) return true;
            int position = path.indexOf('[');
            int member = position < 0 ? path.lastIndexOf('.') : path.lastIndexOf('.', position);
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
        if (column.kind().geometry()) {
            requireGeometryColumn(db, table.name(), table.columns(), column);
            return bindGeometry(db, table.name(), column, settings);
        }
        for (var name : column.physicalNames()) {
            boolean supplementary = !name.equals(column.name());
            if (supplementary && !table.used().add(key(db, name))) {
                throw new DbException("Inconsistent supplementary column " + name + " for " + path);
            }
            Kind physical = column.scalarText(settings.schemaOptions()) || (supplementary && column.enumText().isPresent()) ? Kind.TEXT
                    : column.enumeration().isPresent() ? Kind.REFERENCE : column.kind();
            requireColumns(db, table.name(), table.columns(), Map.of(name, physical));
        }
        return column;
    }

    private static SecondaryGeometry bindSecondaryGeometry(Database db, Column column, AttributeName mapping,
                                                           String parent, String table, String path, Settings settings)
            throws SQLException {
        if (!column.kind().geometry() || mapping.target() != null || Ili2dbLayout.TECHNICAL_COLUMNS.contains(key(db, column.name()))) {
            throw new DbException("Invalid secondary geometry column for " + path);
        }
        var physical = physicalColumns(db, table);
        var used = bindRowIdentity(db, table, physical, settings);
        requireGeometryColumn(db, table, physical, column);
        requireIdPrimaryKey(db, table);
        used.add(key(db, column.name()));
        bindAdditionalColumns(db, table, physical, used, settings);
        if (!physical.keySet().equals(used)) throw new DbException("Unexpected physical columns in " + table);

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
        String sql = "SELECT tag, setting FROM " + db.technicalTable(Ili2dbLayout.COLUMN_PROP)
                + " WHERE " + names + " AND subtype IS NULL AND tag IN (?, ?)";
        var seen = new HashSet<String>();
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, key(db, table));
            statement.setString(2, key(db, column.name()));
            statement.setString(3, PREFIX + Ili2dbLayout.SRID_PROPERTY);
            statement.setString(4, PREFIX + Ili2dbLayout.GEOMETRY_TYPE_PROPERTY);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String tag = rows.getString(1);
                    boolean srid = tag.equals(PREFIX + Ili2dbLayout.SRID_PROPERTY);
                    String property = "COLUMN_PROP." + tag.substring(PREFIX.length()) + " for " + table + "." + column.name();
                    String value = rows.getString(2);
                    boolean matches = srid ? Ili2dbLayout.parseEpsgCode(value, property) == column.epsgCode()
                            : column.geometryType().equals(value);
                    if (!seen.add(tag) || !matches) {
                        throw new DbException("Inconsistent " + property + ": expected one "
                                + (srid ? "EPSG:" + column.epsgCode() : column.geometryType()));
                    }
                }
            }
        }
        return stored;
    }

    private static void requireGeometryColumn(Database db, String table, Map<String, String> physical, Column column) {
        String name = table + "." + column.name();
        String actual = physical.get(key(db, column.name()));
        if (actual == null) throw new DbException("Missing physical column " + name);
        String expected = db.gpkg() ? column.geometryType() : "geometry";
        if (!expected.equalsIgnoreCase(actual)) {
            throw new DbException("Unsupported SQL type for " + name + " (" + actual + "), expected " + expected);
        }
    }

    private static void useColumn(Database db, CatalogTable table, AttributeName mapping, String path) {
        if (!key(db, table.name()).equals(key(db, mapping.owner())) || !table.used().add(key(db, mapping.name()))) {
            throw new DbException("Missing or inconsistent ATTRNAME mapping for " + path);
        }
    }

    private static List<Column> bindReferences(Database db, CatalogTable table, AttributeNames names,
                                                DbInheritance layout, Map<SymbolRef, String> tableNames,
                                                Column reference, boolean ordered, Settings settings) throws SQLException {
        return bindReferences(db, table, names, layout, tableNames, List.of(reference), ordered, settings);
    }

    private static List<Column> bindReferences(Database db, CatalogTable table, AttributeNames names,
                                                DbInheritance layout, Map<SymbolRef, String> tableNames,
                                                List<Column> references, boolean ordered, Settings settings) throws SQLException {
        var columns = new ArrayList<Column>();
        for (var reference : DbMapping.referenceAlternatives(layout, references, ordered, settings.schemaOptions())) {
            String path = reference.slot().name().declared().toString();
            String targetName = tableNames.get(reference.targetRelation());
            var binding = names.take(path, table.name(), targetName);
            var column = bindColumn(db, table, tableNames, reference.withName(binding.name()), binding, path, settings);
            if (column.kind() == Kind.REFERENCE && (settings.inheritance() == DbInheritance.Profile.SMART2
                    || reference.catalogueReference().isPresent() || references.size() > 1)) {
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

    private static Map<String, DbEnumeration> readEnumTables(Database db, Map<String, String> classNames, Set<String> usedNames,
                                       Map<String, String> definitions, DbEnumOptions.Lookup mode, CancellationToken token) throws SQLException {
        if (mode == DbEnumOptions.Lookup.SINGLE) {
            requireEnumTable(db, foldedName(Ili2dbLayout.ENUM), true);
        }
        var bindings = new HashMap<String, DbEnumeration>();
        var tables = new HashMap<String, String>();
        boolean ids = mode == DbEnumOptions.Lookup.WITH_ID;
        for (var entry : definitions.entrySet()) {
            token.throwIfCancellationRequested();
            String definition = entry.getKey();
            String root = entry.getValue();
            String table = tables.get(root);
            if (table == null) {
                table = classNames.remove(root);
                if (table == null || !usedNames.add(key(db, table)) || Ili2dbLayout.reservedTable(foldedName(table), db.gpkg())) {
                    throw new DbException("Missing or inconsistent enumeration CLASSNAME mapping for " + root);
                }
                requireEnumTable(db, table, ids);
                if (ids) requireColumns(db, table, physicalColumns(db, table), Map.of(T_ID, Kind.REFERENCE));
                tables.put(root, table);
            }
            if (ids) bindings.put(definition, readEnumerationIds(db, table, definition, token));
        }
        return Map.copyOf(bindings);
    }

    private static DbEnumeration enumeration(Map<String, DbEnumeration> enumerations, String definition) {
        var encoding = enumerations.get(definition);
        if (encoding == null) throw new DbException("Missing enumeration lookup for " + definition);
        return encoding;
    }

    private static DbEnumeration readEnumerationIds(Database db, String table, String definition, CancellationToken token)
            throws SQLException {
        var numbers = new HashMap<String, Long>();
        var paths = new HashMap<Long, String>();
        var labels = new HashMap<String, String>();
        String sql = "SELECT " + T_ID + ", ilicode, dispname FROM " + db.table(table) + " WHERE thisclass = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, definition);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    String path = required(rows, 2, "enumeration iliCode");
                    long id = rows.getLong(1);
                    if (rows.wasNull() || numbers.putIfAbsent(path, id) != null || paths.putIfAbsent(id, path) != null) {
                        throw new DbException("Ambiguous enumeration lookup for " + definition + " at " + path);
                    }
                    String label = rows.getString(3);
                    if (label != null && !label.isEmpty()) labels.put(path, label);
                }
            }
        }
        return new DbEnumeration(numbers, paths, labels);
    }

    private static void requireEnumTable(Database db, String table, boolean shared) throws SQLException {
        var physical = physicalColumns(db, table);
        requireColumns(db, table, physical, Map.of("ilicode", Kind.TEXT, "itfcode", Kind.INTEGER, "seq", Kind.INTEGER,
                "dispname", Kind.TEXT, "description", Kind.TEXT, "inactive", Kind.BOOLEAN));
        if (shared) requireColumns(db, table, physical, Map.of("thisclass", Kind.TEXT, "baseclass", Kind.TEXT));
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
        return readPhysicalColumns(db, name).types();
    }

    private static PhysicalColumns readPhysicalColumns(Database db, String name) throws SQLException {
        requirePhysicalTable(db, name);
        var metadata = db.connection().getMetaData();
        String catalog = db.connection().getCatalog();
        String escape = metadata.getSearchStringEscape();
        String pattern = searchPattern(name, escape);
        String schemaPattern = searchPattern(db.schema(), escape);
        var columns = new HashMap<String, String>();
        var notNull = new HashSet<String>();
        try (var rows = metadata.getColumns(catalog, schemaPattern, pattern, null)) {
            while (rows.next()) {
                String column = key(db, rows.getString("COLUMN_NAME"));
                columns.put(column, rows.getString("TYPE_NAME").toLowerCase(Locale.ROOT));
                if (rows.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls && !rows.wasNull()) notNull.add(column);
            }
        }
        return new PhysicalColumns(columns, notNull);
    }

    private static void requirePhysicalTable(Database db, String name) throws SQLException {
        var metadata = db.connection().getMetaData();
        String escape = metadata.getSearchStringEscape();
        try (var tables = metadata.getTables(db.connection().getCatalog(), searchPattern(db.schema(), escape),
                searchPattern(name, escape), new String[]{"TABLE"})) {
            if (!tables.next() || !key(db, name).equals(key(db, tables.getString("TABLE_NAME"))) || tables.next()) {
                throw new DbException("Missing or ambiguous physical table " + name);
            }
        }
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
                case TEXT, OID_TEXT, FORMATTED, ENUMERATION, REFERENCE_TEXT -> type.equals("text") || type.equals("varchar");
                case BOOLEAN -> type.equals("boolean") || type.equals("bool");
                case INTEGER -> type.equals("integer") || type.equals("int4") || type.equals("int2")
                        || type.equals("smallint");
                case BINARY -> db.gpkg() ? type.equals("blob") : type.equals("bytea");
                case XML -> db.gpkg() ? type.equals("text") : type.equals("xml");
                case UUID -> db.gpkg() ? type.equals("text") || type.equals("varchar") : type.equals("uuid");
                case DATE -> type.equals("date");
                case TIME -> type.equals("time");
                case DATETIME -> db.gpkg() ? type.equals("datetime") : type.equals("timestamp") || type.equals("timestamp without time zone");
                case DECIMAL -> db.gpkg() ? type.equals("double") : type.equals("numeric") || type.equals("decimal");
                case BIGINT -> db.gpkg() ? type.equals("integer") : type.equals("int8") || type.equals("bigint");
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
