package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.BlackboxKind;
import guru.interlis.model.BlackboxType;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.FormattedType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Modifier;
import guru.interlis.model.NumericType;
import guru.interlis.model.OidKind;
import guru.interlis.model.OidRule;
import guru.interlis.model.OidType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeKind;
import guru.interlis.model.TypeRef;
import guru.interlis.model.TopicSymbol;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferEvent.DomainBinding;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;
import guru.interlis.db.Ili2dbLayout.AttributeTransformation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.db.Ili2dbLayout.SEQ_COLUMN;
import static guru.interlis.db.Ili2dbLayout.TECHNICAL_COLUMNS;
import static guru.interlis.db.Ili2dbLayout.T_SEQ;
import static guru.interlis.db.Ili2dbLayout.T_TYPE;
import static guru.interlis.db.Ili2dbLayout.foldedName;
import static guru.interlis.db.Ili2dbLayout.reservedTable;

/** The SQL names and storage bindings for a checked transfer plan. */
final class DbMapping {
    enum Kind {
        TEXT, BOOLEAN, INTEGER, BIGINT, DECIMAL, DATE, TIME, DATETIME, FORMATTED, UUID, OID_TEXT, BINARY, XML, ENUMERATION,
        POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE, REFERENCE, REFERENCE_TEXT;

        boolean geometry() { return this == POINT || this == POLYLINE || this == SURFACE || collection(); }

        boolean collection() { return this == MULTIPOINT || this == MULTICURVE || this == MULTISURFACE; }

        String geometryType() {
            return switch (this) {
                case POINT -> "POINT";
                case POLYLINE -> "COMPOUNDCURVE";
                case SURFACE -> "CURVEPOLYGON";
                case MULTIPOINT -> "MULTIPOINT";
                case MULTICURVE -> "MULTICURVE";
                case MULTISURFACE -> "MULTISURFACE";
                default -> throw new IllegalStateException("Expected a geometry kind: " + this);
            };
        }
    }

    enum GeometryEncoding {
        STANDARD, LINEAR;

        String sqlType(Kind kind) {
            if (this == STANDARD) return kind.geometryType();
            return switch (kind) {
                case POLYLINE -> "LINESTRING";
                case SURFACE -> "POLYGON";
                case MULTICURVE -> "MULTILINESTRING";
                case MULTISURFACE -> "MULTIPOLYGON";
                default -> throw new IllegalArgumentException("No linear encoding for " + kind);
            };
        }
    }

    /** A zero code means the corresponding fallback was absent. */
    record CrsDefaults(int schemaEpsg, int modelEpsg) { }

    record Column(TransferSlot slot, String name, Kind kind, Optional<TypeDescriptor> effectiveType,
                  Optional<SymbolRef> target, Optional<SymbolRef> referenceTable, int epsgCode, int srsId, boolean legacyEpsgHeader,
                  Optional<String> positionColumn, int dimensions, Optional<DomainBinding> domainBinding,
                  Optional<DbCatalogueReference> catalogueReference, Optional<DbTextExpansion> textExpansion,
                  Optional<DbWrappedGeometry> wrappedGeometry, double strokeTolerance,
                  Optional<DbExpandedStructure> expandedStructure, Optional<DbJsonStructure> jsonStructure,
                  Optional<DbArray> array, Optional<DbEnumeration> enumeration, Optional<DbEnumText> enumText) {
        Column {
            boolean role = slot instanceof RoleSlot;
            if (effectiveType.isEmpty() != role || (role && kind != Kind.REFERENCE && kind != Kind.REFERENCE_TEXT)) {
                throw new IllegalArgumentException("Only a role reference can omit the effective attribute type");
            }
        }

        Column(TransferSlot slot, String name, Kind kind, Optional<TypeDescriptor> effectiveType,
               Optional<SymbolRef> target, Optional<SymbolRef> referenceTable, int epsgCode, int srsId,
               boolean legacyEpsgHeader, Optional<String> positionColumn, int dimensions, Optional<DomainBinding> domainBinding,
               Optional<DbCatalogueReference> catalogueReference, Optional<DbTextExpansion> textExpansion,
               Optional<DbWrappedGeometry> wrappedGeometry) {
            this(slot, name, kind, effectiveType, target, referenceTable, epsgCode, srsId, legacyEpsgHeader,
                    positionColumn, dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry, 0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        Column(TransferSlot slot, String name, Kind kind, Optional<TypeDescriptor> effectiveType,
               Optional<SymbolRef> target, Optional<SymbolRef> referenceTable, int epsgCode, int srsId,
               boolean legacyEpsgHeader, Optional<String> positionColumn, int dimensions,
               Optional<DomainBinding> domainBinding) {
            this(slot, name, kind, effectiveType, target, referenceTable, epsgCode, srsId, legacyEpsgHeader,
                    positionColumn, dimensions, domainBinding, Optional.empty(), Optional.empty(), Optional.empty());
        }

        Column withName(String sqlName) {
            return new Column(slot, sqlName, kind, effectiveType, target, referenceTable, epsgCode, srsId, legacyEpsgHeader,
                    positionColumn, dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry, strokeTolerance, expandedStructure, jsonStructure, array, enumeration, enumText);
        }

        Column withItfCodes() {
            return withEnumeration(DbEnumeration.itfCodes((EnumerationType) effectiveType.orElseThrow(), slot.name().declared().toString()));
        }

        Column withEnumeration(DbEnumeration binding) {
            return new Column(slot, name, kind, effectiveType, target, referenceTable, epsgCode, srsId, legacyEpsgHeader,
                    positionColumn, dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry,
                    strokeTolerance, expandedStructure, jsonStructure, array, Optional.of(binding), enumText);
        }

        Column withEnumText(DbEnumText binding) {
            return new Column(slot, name, kind, effectiveType, target, referenceTable, epsgCode, srsId, legacyEpsgHeader,
                    positionColumn, dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry,
                    strokeTolerance, expandedStructure, jsonStructure, array, enumeration, Optional.of(binding));
        }

        Column withEpsg(int epsg) {
            return new Column(slot, name, kind, effectiveType, target, referenceTable, epsg, epsg, false,
                    positionColumn, dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry, strokeTolerance, expandedStructure, jsonStructure, array, enumeration, enumText);
        }

        Column withSrsId(int id, boolean acceptsEpsgHeader) {
            return new Column(slot, name, kind, effectiveType, target, referenceTable, epsgCode, id, acceptsEpsgHeader,
                    positionColumn, dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry, strokeTolerance, expandedStructure, jsonStructure, array, enumeration, enumText);
        }

        Column withPositionColumn(boolean gpkg) {
            String position = name + "_" + (gpkg ? SEQ_COLUMN : T_SEQ);
            checkColumnName(slot, position, false, false, gpkg);
            return new Column(slot, name, kind, effectiveType, target, referenceTable, epsgCode, srsId, legacyEpsgHeader,
                    Optional.of(position), dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry, strokeTolerance, expandedStructure, jsonStructure, array, enumeration, enumText);
        }

        Column withReferenceTable(SymbolRef table) {
            return new Column(slot, name, kind, effectiveType, target, Optional.of(table), epsgCode, srsId,
                    legacyEpsgHeader, positionColumn, dimensions, domainBinding, catalogueReference, textExpansion,
                    wrappedGeometry, strokeTolerance, expandedStructure, jsonStructure, array, enumeration, enumText);
        }

        Column asTextReference() {
            return new Column(slot, name, Kind.REFERENCE_TEXT, effectiveType, target, Optional.empty(), epsgCode, srsId,
                    legacyEpsgHeader, positionColumn, dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry,
                    strokeTolerance, expandedStructure, jsonStructure, array, enumeration, enumText);
        }

        Column withStrokeArcs(TransferPlan plan, boolean strokeArcs) {
            if (!strokeArcs || !kind.geometry() || kind == Kind.POINT || kind == Kind.MULTIPOINT) return this;
            if (dimensions != 2) {
                throw unsupported("strokeArcs requires XY at " + slot.name().declared());
            }
            int digits = vertexCoordinates(plan).axes().getFirst().maximum()
                    .map(limit -> limit.precision().fractionalDigits()).orElse(0);
            double tolerance = digits == 0 ? 0.5 : Math.pow(10, -digits);
            if (tolerance == 0) throw unsupported("arc precision at " + slot.name().declared());
            return new Column(slot, name, kind, effectiveType, target, referenceTable, epsgCode, srsId, legacyEpsgHeader,
                    positionColumn, dimensions, domainBinding, catalogueReference, textExpansion, wrappedGeometry, tolerance, expandedStructure, jsonStructure, array, enumeration, enumText);
        }

        CoordinateType vertexCoordinates(TransferPlan plan) {
            var effective = effectiveType.orElseThrow();
            if (effective instanceof CoordinateType coordinates) return coordinates;
            var domain = domainBinding.map(binding -> DbDomains.domain(plan, binding.concrete().symbol()))
                    .orElseGet(() -> vertexDomain(plan, (GeometryType) effective));
            return (CoordinateType) plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        }

        GeometryEncoding geometryEncoding() { return strokeTolerance > 0 ? GeometryEncoding.LINEAR : GeometryEncoding.STANDARD; }

        String geometryType() { return geometryEncoding().sqlType(kind); }

        boolean scalarText(Set<Ili2dbLayout.SchemaOption> options) {
            return options.contains(Ili2dbLayout.SchemaOption.SCALAR_TEXT) && simpleScalar();
        }

        boolean simpleScalar() {
            return !kind.geometry() && kind != Kind.REFERENCE && kind != Kind.REFERENCE_TEXT
                    && textExpansion.isEmpty() && expandedStructure.isEmpty() && jsonStructure.isEmpty() && array.isEmpty();
        }

        List<String> physicalNames() {
            if (expandedStructure.isPresent()) return expandedStructure.orElseThrow().physicalNames();
            if (enumText.isPresent()) return List.of(name, enumText.orElseThrow().column());
            return textExpansion.map(expansion -> expansion.columns(name)).orElseGet(() -> List.of(name));
        }

        int physicalWidth() {
            if (expandedStructure.isPresent()) return expandedStructure.orElseThrow().width();
            if (enumText.isPresent()) return 2;
            return textExpansion.map(DbTextExpansion::width).orElse(1);
        }

        AttributeTransformation transformation() {
            if (expandedStructure.isPresent()) return AttributeTransformation.EXPANDED_STRUCTURE;
            if (jsonStructure.isPresent()) return AttributeTransformation.JSON_STRUCTURE;
            if (array.isPresent()) return AttributeTransformation.ARRAY;
            if (catalogueReference.isPresent()) return AttributeTransformation.CATALOGUE_REFERENCE;
            if (wrappedGeometry.isPresent()) return wrappedGeometry.orElseThrow().transformation();
            return textExpansion.map(DbTextExpansion::transformation).orElse(null);
        }

        SymbolRef targetRelation() {
            return referenceTable.orElseGet(target::orElseThrow);
        }

        boolean external() {
            if (slot instanceof RoleSlot role) return role.role().external();
            return effectiveType.orElseThrow() instanceof ReferenceType reference && reference.external();
        }
    }

    /** A structure-valued field whose declared child type is stored in a physical hierarchy root.
     * An empty collection kind denotes a single structure value.
     */
    record Composition(AttributeSlot slot, SymbolRef parent, SymbolRef child, SymbolRef childRoot,
                       String ownerColumn, Optional<CollectionKind> collectionKind) { }

    /** An association transferred as a field on an object at the owner end. */
    record EmbeddedAssociation(ObjectDescriptor object, String name, AssociationRole ownerRole,
                               AssociationRole targetRole, boolean linkTable) {
        SymbolRef owner() { return ownerRole.target(); }
        SymbolRef target() { return targetRole.target(); }
        RoleSlot role() { return targetRole.slot(); }
        boolean ordered() { return ownerRole.slot().role().ordered(); }
    }

    record AssociationRole(RoleSlot slot, List<SymbolRef> targets) {
        AssociationRole {
            targets = List.copyOf(targets);
        }

        // Embedded associations have one declared target per role.
        SymbolRef target() { return targets.getFirst(); }

        List<Column> columns() {
            return targets.stream().map(target -> roleColumn(slot, target, slot.name().declared().last())).toList();
        }

        boolean single() {
            return CardinalityUpper.finite(1).equals(slot.role().cardinality().upper());
        }

        boolean external() {
            return slot.role().external();
        }
    }

    /** A geometry stored in a table that shares its owner's identity and basket. */
    record SecondaryGeometry(String table, Column column) { }

    /** The declaration that names the table and its physical parent, when the rows share an ID. */
    record Table(ObjectDescriptor object, String name, Optional<SymbolRef> baseTable, List<Column> columns, boolean hasDiscriminator,
                 Optional<String> oidDomain, boolean storesTransferTid, List<SecondaryGeometry> secondaryGeometries) {
        Table {
            columns = List.copyOf(columns);
            secondaryGeometries = List.copyOf(secondaryGeometries);
            var names = new HashSet<String>();
            for (var column : columns) {
                for (var field : column.physicalNames()) {
                    if (!names.add(foldedName(field))) throw unsupported("column " + field + " collides in " + name);
                }
            }
            for (var column : columns) {
                column.positionColumn().ifPresent(position -> {
                    if (!names.add(foldedName(position))) {
                        throw unsupported("role position column " + position + " collides in " + name);
                    }
                });
            }
        }

        boolean subtype() { return baseTable.isPresent(); }
        boolean hasTidColumn() {
            return carriesObjectId() && (oidDomain.isPresent() || storesTransferTid)
                    || !subtype() && storesTransferTid && object.kind() == SymbolKind.STRUCTURE;
        }
        boolean carriesObjectId() {
            return !subtype() && (object.kind() == SymbolKind.CLASS
                    || object.kind() == SymbolKind.ASSOCIATION && oidDomain.isPresent());
        }
        boolean uuidIdentity() { return oidDomain.filter(Ili2dbLayout.UUID_OID::equals).isPresent(); }
        boolean hasSequenceColumn() { return !subtype() && object.kind() == SymbolKind.STRUCTURE; }

        Column column(SymbolRef field) {
            return columns.stream().filter(column -> column.slot().symbol().equals(field)).findFirst().orElseThrow();
        }
    }

    record ObjectStorage(ObjectDescriptor object, String discriminator, List<Table> rows) {
        ObjectStorage {
            rows = List.copyOf(rows);
        }
        Table root() { return rows.getFirst(); }
    }

    record StorageAlias(ObjectDescriptor object, String discriminator, SymbolRef owner) { }

    private final TransferPlan plan;
    private final DbInheritance inheritance;
    private final Set<Ili2dbLayout.SchemaOption> schemaOptions;
    private final LanguageVersion version;
    private final Optional<DbCrsPair> crsPair;
    private final DbTranslations translations;
    private final List<SymbolRef> models;
    private final List<Table> tables;
    private final Map<SymbolRef, Table> bySymbol;
    private final Map<String, ObjectStorage> byDiscriminator;
    private final Map<SymbolRef, ObjectStorage> storage;
    private final Map<SymbolRef, List<Table>> referenceTables;
    private final Map<Table, List<Table>> hierarchies;
    private final Map<SymbolRef, Map<SymbolRef, SymbolRef>> extendedFields;
    private final Map<SymbolRef, Map<String, String>> extendedEnumLabels;
    private final List<Composition> compositions;
    private final Map<SymbolRef, List<Composition>> byParent;
    private final Map<SymbolRef, List<Composition>> byChild;
    private final List<EmbeddedAssociation> associations;
    private final Map<SymbolRef, SymbolRef> roleOwners;
    private final Set<SymbolRef> requiredEmbeddedRoles;

    DbMapping(TransferPlan plan, LanguageVersion version, List<Table> tables, List<Composition> compositions,
              List<EmbeddedAssociation> associations, Optional<DbCrsPair> crsPair, List<SymbolRef> selectedModels,
              DbInheritance inheritance, Set<Ili2dbLayout.SchemaOption> schemaOptions, Set<SymbolRef> notNullRoles) {
        this.plan = plan;
        this.schemaOptions = Set.copyOf(schemaOptions);
        this.inheritance = inheritance;
        this.crsPair = crsPair;
        translations = new DbTranslations(plan);
        this.version = version;
        models = crsPair.map(DbCrsPair::models).orElseGet(() -> version == LanguageVersion.ILI24
                ? selectedModels.stream().map(ref -> plan.model(ref).orElseThrow().name().canonical()).distinct().toList()
                : List.copyOf(selectedModels));
        this.tables = List.copyOf(tables);
        bySymbol = this.tables.stream().collect(Collectors.toUnmodifiableMap(
                table -> table.object().symbol(), Function.identity()));
        var paths = new HashMap<SymbolRef, ObjectStorage>();
        var families = new HashMap<Table, List<Table>>();
        for (var table : this.tables) {
            var path = new ArrayDeque<Table>();
            for (var part = table; part != null; part = part.baseTable().map(bySymbol::get).orElse(null)) {
                path.addFirst(part);
            }
            var binding = new ObjectStorage(table.object(), table.name(), List.copyOf(path));
            paths.put(table.object().symbol(), binding);
            if (binding.root().hasDiscriminator()) {
                families.computeIfAbsent(binding.root(), unused -> new ArrayList<>()).add(table);
            }
        }
        for (var alias : inheritance.aliases()) {
            var owner = paths.get(alias.owner());
            paths.put(alias.object().symbol(), new ObjectStorage(alias.object(), alias.discriminator(), owner.rows()));
        }
        storage = Map.copyOf(paths);
        referenceTables = this.tables.stream().flatMap(table -> table.columns().stream())
                .filter(column -> column.kind() == Kind.REFERENCE)
                .collect(Collectors.groupingBy(column -> column.target().orElseThrow(),
                        Collectors.mapping(column -> table(column.targetRelation()), Collectors.collectingAndThen(
                                Collectors.toCollection(java.util.LinkedHashSet::new), List::copyOf))));
        byDiscriminator = storage.values().stream().collect(Collectors.toUnmodifiableMap(
                ObjectStorage::discriminator, Function.identity()));
        hierarchies = families.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        var extensions = new HashMap<SymbolRef, Map<SymbolRef, SymbolRef>>();
        var labels = new HashMap<SymbolRef, Map<String, String>>();
        boolean hasEnumText = tables.stream().flatMap(table -> table.columns().stream())
                .anyMatch(column -> column.kind() == Kind.ENUMERATION && column.enumText().isPresent());
        for (var binding : storage.values()) {
            var fields = new HashMap<SymbolRef, SymbolRef>();
            for (var slot : binding.object().slots()) {
                if (slot instanceof AttributeSlot attribute && !attribute.attribute().transientAttribute()) {
                    if (hasEnumText && attribute.attribute().overrides().isPresent() && inlineEnumeration(plan, attribute)) {
                        labels.computeIfAbsent(slot.symbol(), unused -> DbEnumText.modelLabels(plan, attribute));
                    }
                    for (var base = attribute.attribute(); base.overrides().isPresent();) {
                        var ref = new SymbolRef(plan.snapshotId(), base.overrides().orElseThrow());
                        fields.put(ref, slot.symbol());
                        base = (AttributeSymbol) plan.symbol(ref).known().orElseThrow();
                    }
                }
            }
            if (!fields.isEmpty()) extensions.put(binding.object().symbol(), Map.copyOf(fields));
        }
        extendedFields = Map.copyOf(extensions);
        extendedEnumLabels = Map.copyOf(labels);
        this.compositions = List.copyOf(compositions);
        byParent = indexCompositions(this.compositions, Composition::parent);
        byChild = indexCompositions(this.compositions, Composition::childRoot);
        checkCompositionCycles();
        this.associations = List.copyOf(associations);
        roleOwners = associations.stream().filter(association -> !association.linkTable())
                .collect(Collectors.toUnmodifiableMap(association -> association.role().symbol(), EmbeddedAssociation::owner));
        requiredEmbeddedRoles = tables.stream().flatMap(table -> table.columns().stream()
                .filter(column -> notNullRoles.contains(column.slot().symbol()) && simpleEmbeddedRole(table, column)))
                .map(column -> column.slot().symbol()).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<SymbolRef, List<Composition>> indexCompositions(List<Composition> compositions,
                                                                       Function<Composition, SymbolRef> key) {
        return Map.copyOf(compositions.stream().collect(Collectors.groupingBy(key,
                Collectors.collectingAndThen(Collectors.toList(), List::copyOf))));
    }

    private void checkCompositionCycles() {
        var remainingParents = new HashMap<SymbolRef, Integer>();
        for (var composition : compositions) {
            remainingParents.putIfAbsent(composition.parent(), 0);
            remainingParents.merge(composition.child(), 1, Integer::sum);
        }
        var ready = new ArrayDeque<SymbolRef>();
        remainingParents.forEach((type, count) -> {
            if (count == 0) ready.add(type);
        });
        while (!ready.isEmpty()) {
            for (var composition : compositionsForParent(ready.remove())) {
                if (remainingParents.merge(composition.child(), -1, Integer::sum) == 0) {
                    ready.add(composition.child());
                }
            }
        }
        for (var composition : compositions) {
            if (remainingParents.get(composition.parent()) > 0 && remainingParents.get(composition.child()) > 0) {
                throw unsupported("cyclic structure composition at " + composition.slot().name().declared());
            }
        }
    }

    Optional<DbCrsPair> crsPair() { return crsPair; }

    List<SymbolRef> models() { return models; }

    SymbolRef originalTopic(SymbolRef topic) {
        return crsPair.map(pair -> pair.originalTopic(topic)).orElseGet(() -> plan.topic(topic).orElseThrow().name().canonical());
    }

    List<String> storedTopicNames(SymbolRef topic) {
        return plan.topics().stream().filter(candidate -> includesTopic(candidate.symbol(), topic))
                .map(candidate -> candidate.name().declared().toString()).toList();
    }

    boolean includesTopic(SymbolRef basket, SymbolRef declared) {
        return includesTopic(plan, originalTopic(basket), declared);
    }

    private static boolean includesTopic(TransferPlan plan, SymbolRef basket, SymbolRef declared) {
        for (var topic = basket; topic != null; topic = plan.topic(topic).orElseThrow().baseTopic().orElse(null)) {
            if (topic.equals(declared)) return true;
        }
        return false;
    }

    DbTranslations translations() { return translations; }

    TransferPlan plan() { return plan; }
    DbInheritance.Profile profile() { return inheritance.profile(); }
    DbInheritance inheritance() { return inheritance; }
    LanguageVersion version() { return version; }
    List<Table> tables() { return tables; }
    Collection<ObjectStorage> objectStorage() { return storage.values(); }

    Set<Ili2dbLayout.SchemaOption> schemaOptions() { return schemaOptions; }

    boolean requiredEmbeddedRole(Column column) { return requiredEmbeddedRoles.contains(column.slot().symbol()); }

    boolean simpleEmbeddedRole(Table table, Column column) {
        if (profile() != DbInheritance.Profile.NEW_CLASS || column.kind() != Kind.REFERENCE
                || !(column.slot() instanceof RoleSlot) || column.external() || column.positionColumn().isPresent()
                || column.referenceTable().isPresent() || !table.object().symbol().equals(roleOwners.get(column.slot().symbol()))) {
            return false;
        }
        var target = table(column.targetRelation());
        return flatClass(table) && flatClass(target)
                && associationsForOwner(associations, table.object().symbol()).stream()
                .anyMatch(association -> association.role().symbol().equals(column.slot().symbol()) && !association.ownerRole().single());
    }

    private boolean flatClass(Table table) {
        return table.object().kind() == SymbolKind.CLASS && !table.subtype()
                && !table.object().modifiers().contains(Modifier.ABSTRACT) && !hasSubtypes(plan, table.object().symbol());
    }
    boolean hasBasketColumn() { return schemaOptions.contains(Ili2dbLayout.SchemaOption.BASKET_COLUMN); }
    boolean storesTransferTids() { return schemaOptions.contains(Ili2dbLayout.SchemaOption.TID_COLUMN); }

    List<String> storageTables() { return storageTables(tables); }

    static List<String> storageTables(List<Table> tables) {
        var names = new ArrayList<String>();
        for (var table : tables) {
            names.add(table.name());
            for (var geometry : table.secondaryGeometries()) names.add(geometry.table());
        }
        return names;
    }
    List<Composition> compositions() { return compositions; }
    List<Composition> compositionsForParent(SymbolRef parent) { return byParent.getOrDefault(parent, List.of()); }
    List<Composition> compositionsForObject(SymbolRef type) {
        return tableChain(type).stream().flatMap(table -> compositionsForParent(table.object().symbol()).stream())
                .filter(composition -> applies(composition, type)).toList();
    }
    List<Composition> compositionsForChild(SymbolRef child) { return byChild.getOrDefault(child, List.of()); }
    List<EmbeddedAssociation> associations() { return associations; }
    ObjectStorage storage(SymbolRef type) {
        var binding = storage.get(type);
        if (binding == null) throw new DbException("No table for transfer class " + type);
        return binding;
    }
    List<Table> tableChain(SymbolRef type) { return storage(type).rows(); }
    Table rootTable(SymbolRef type) { return storage(type).root(); }

    Table referenceRoot(Column column) { return rootTable(column.targetRelation()); }

    List<Table> referenceTables(SymbolRef target) { return referenceTables.getOrDefault(target, List.of()); }

    boolean occupies(SymbolRef actual, SymbolRef relation) {
        return storage(actual).rows().stream().anyMatch(row -> row.object().symbol().equals(relation));
    }

    SymbolRef identityFamily(SymbolRef type) {
        if (profile() == DbInheritance.Profile.NEW_CLASS) return rootTable(type).object().symbol();
        var object = plan.object(type).orElseThrow();
        while (object.baseType().isPresent()) object = plan.object(object.baseType().orElseThrow()).orElseThrow();
        return object.symbol();
    }

    boolean isAssignableTo(SymbolRef actual, SymbolRef declared) {
        return isAssignableTo(plan, actual, declared);
    }

    private static boolean isAssignableTo(TransferPlan plan, SymbolRef actual, SymbolRef declared) {
        for (var type = actual; type != null; type = plan.object(type).orElseThrow().baseType().orElse(null)) {
            if (type.equals(declared)) return true;
        }
        return false;
    }

    boolean applies(Column column, SymbolRef actual) {
        if (profile() == DbInheritance.Profile.NEW_CLASS) return true;
        var owner = column.slot() instanceof AttributeSlot attribute
                ? attributeOwner(attribute)
                : roleOwners.get(column.slot().symbol());
        return owner == null || isAssignableTo(actual, owner);
    }

    boolean applies(Composition composition, SymbolRef actual) {
        return profile() == DbInheritance.Profile.NEW_CLASS || isAssignableTo(actual, attributeOwner(composition.slot()));
    }

    private SymbolRef attributeOwner(AttributeSlot attribute) {
        return new SymbolRef(plan.snapshotId(), attribute.attribute().owner().orElseThrow());
    }
    List<Table> hierarchy(Table root) { return hierarchies.getOrDefault(root, List.of()); }

    Map<SymbolRef, SymbolRef> extendedFields(SymbolRef type) {
        return extendedFields.getOrDefault(type, Map.of());
    }

    Map<String, String> extendedEnumLabels(SymbolRef slot) {
        return extendedEnumLabels.getOrDefault(slot, Map.of());
    }

    void checkEnumLookupIds(DbEnumOptions options) {
        if (options.lookup() != DbEnumOptions.Lookup.WITH_ID) return;
        for (var binding : storage.values()) {
            var object = binding.object();
            if (!object.transferable()) continue;
            var fields = extendedFields(object.symbol());
            if (fields.isEmpty()) continue;
            for (var table : binding.rows()) {
                for (var column : table.columns()) {
                    if (column.kind() == Kind.ENUMERATION && fields.containsKey(column.slot().symbol())) {
                        throw unsupported("enumeration lookup IDs for a shared column " + table.name() + "." + column.name()
                                + " at " + object.name().declared());
                    }
                }
            }
        }
    }

    ObjectStorage concreteType(Table root, String discriminator, long id) {
        var actual = discriminator == null ? null : byDiscriminator.get(discriminator);
        if (actual == null || actual.object().modifiers().contains(Modifier.ABSTRACT)
                || !actual.root().equals(root)) {
            throw new DbException("Unknown or abstract T_Type " + discriminator + " in " + root.name()
                    + " for T_Id " + id);
        }
        return actual;
    }

    ObjectStorage concreteType(Table root, String discriminator, long id, SymbolRef topic) {
        var actual = concreteType(root, discriminator, id);
        if (!plan.topic(originalTopic(topic)).orElseThrow().objectTypes().contains(actual.object().symbol())) {
            throw new DbException("T_Type " + discriminator + " in " + root.name() + " does not belong to basket topic "
                    + plan.topic(topic).orElseThrow().name().declared() + " for T_Id " + id);
        }
        return actual;
    }

    static void requireSingleGeometry(ObjectDescriptor object, List<Column> columns) {
        if (columns.stream().filter(column -> column.kind().geometry()).count() > 1) {
            throw unsupported("multiple geometries in a single-geometry table for " + object.name().declared());
        }
    }

    static Set<SymbolRef> baseTables(List<ObjectDescriptor> objects) {
        return objects.stream().flatMap(object -> object.baseType().stream()).collect(Collectors.toSet());
    }

    List<EmbeddedAssociation> linkFields(SymbolRef owner) {
        return associationsForOwner(associations, owner).stream().filter(EmbeddedAssociation::linkTable).toList();
    }

    boolean isEmbeddedLink(Table table) {
        return table.object().kind() == SymbolKind.ASSOCIATION && associations.stream().anyMatch(association -> association.linkTable()
                && association.object().symbol().equals(table.object().symbol()));
    }

    Table table(SymbolRef ref) {
        return storage(ref).rows().getLast();
    }

    static List<ObjectDescriptor> storageObjects(TransferPlan plan, List<SymbolRef> roots, CancellationToken token) {
        var topics = new HashSet<SymbolRef>();
        var dependencies = new ArrayDeque<SymbolRef>();
        for (var topic : plan.topics()) {
            if (roots.contains(topic.model())) dependencies.add(topic.symbol());
        }
        while (!dependencies.isEmpty()) {
            token.throwIfCancellationRequested();
            var topic = dependencies.removeFirst();
            if (topics.add(topic)) {
                var declaration = plan.topic(topic).orElseThrow();
                dependencies.addAll(declaration.dependencies());
                declaration.baseTopic().ifPresent(dependencies::addLast);
            }
        }
        var selected = new HashSet<SymbolRef>();
        var pending = new ArrayDeque<ObjectDescriptor>();
        for (var object : plan.objects()) {
            if (roots.contains(object.model()) || object.topic().filter(topics::contains).isPresent()) {
                include(object, selected, pending);
            }
        }
        var extensions = plan.objects().stream().filter(object -> object.baseType().isPresent())
                .collect(Collectors.groupingBy(object -> object.baseType().orElseThrow()));
        while (!pending.isEmpty()) {
            token.throwIfCancellationRequested();
            var object = pending.removeFirst();
            if (object.kind() == SymbolKind.STRUCTURE) {
                object.baseType().flatMap(plan::object).ifPresent(base -> include(base, selected, pending));
                for (var extension : extensions.getOrDefault(object.symbol(), List.of())) {
                    include(extension, selected, pending);
                }
            }
            for (var slot : attributes(object)) {
                var type = plan.type(slot.effectiveType()).known().orElseThrow();
                if (type instanceof CollectionType collection) {
                    type = plan.type(new TypeRef(plan.snapshotId(), collection.elementType())).known().orElseThrow();
                }
                if (type instanceof StructureValueType structure) {
                    structure.target().flatMap(id -> plan.object(new SymbolRef(plan.snapshotId(), id)))
                            .ifPresent(child -> include(child, selected, pending));
                }
            }
        }
        return plan.objects().stream().filter(object -> selected.contains(object.symbol())).toList();
    }

    static List<SymbolRef> storageRoots(TransferPlan plan, Optional<DbCrsPair> crsPair) {
        return crsPair.map(pair -> List.of(pair.models().getFirst())).orElseGet(() -> plan.rootModels().stream()
                .map(ref -> plan.model(ref).orElseThrow().name().canonical()).distinct().toList());
    }

    private static void include(ObjectDescriptor object, Set<SymbolRef> selected, ArrayDeque<ObjectDescriptor> pending) {
        if (selected.add(object.symbol())) pending.addLast(object);
    }

    static DbMapping planNew(String modelName, SourceText source, boolean gpkg, boolean splitGeometries,
                             int defaultEpsg, CancellationToken token) {
        var plan = DbModelSources.compile(List.of(modelName), List.of(source), "model files", token).plan();
        return planNew(plan, gpkg, splitGeometries, defaultEpsg, false, Optional.empty(),
                new SqlNames(), DbInheritance.Profile.NEW_CLASS, Set.of(), false, DbEnumOptions.DEFAULT, Set.of(), token);
    }

    static DbMapping planNew(TransferPlan plan, boolean gpkg, boolean splitGeometries,
                             int defaultEpsg, boolean multiSrs, Optional<DbCrsPair.Models> srsModels,
                             SqlNames allocator, DbInheritance.Profile profile, Set<AttributeTransformation> transformations,
                             boolean strokeArcs, DbEnumOptions enumOptions, Set<Ili2dbLayout.SchemaOption> schemaOptions,
                             CancellationToken token) {
        var crs = new CrsDefaults(defaultEpsg, 0);
        var crsPair = srsModels.map(pair -> new DbCrsPair(plan, pair, crs, crs));
        var roots = storageRoots(plan, crsPair);
        var objects = storageObjects(plan, roots, token);
        checkBasketLayout(plan, objects, schemaOptions);
        checkLayout(plan, objects, enumOptions);
        var wrappedGeometries = DbWrappedGeometry.plan(plan, objects, transformations);
        var geometryComponents = wrappedGeometries.values().stream().map(binding -> binding.component().symbol())
                .collect(Collectors.toSet());
        if (!multiSrs && plan.topics().stream()
                .anyMatch(topic -> plan.rootModels().contains(topic.model()) && DbDomains.deferred(topic))) {
            throw unsupported("deferred generic domains require --multi-srs");
        }
        var names = new HashMap<SymbolRef, String>();
        var associations = new ArrayList<EmbeddedAssociation>();
        var linkRoles = new HashMap<SymbolRef, List<AssociationRole>>();
        for (var object : objects) {
            token.throwIfCancellationRequested();
            String topic = object.topic().flatMap(plan::topic).map(value -> value.name().declared().last()).orElse(null);
            String declaredModel = plan.model(object.model()).orElseThrow().name().declared().last();
            String name = allocator.mappedTable(object.name().declared().toString(), declaredModel, topic,
                    object.name().declared().last());
            if (reservedTable(name, gpkg)) {
                throw unsupported("type " + object.name().declared() + " maps to technical table " + name);
            }
            if (object.kind() == SymbolKind.ASSOCIATION) {
                var roles = describeRoles(plan, object);
                var embedded = embeddedAssociation(plan, object, name, roles);
                if (embedded.isPresent()) {
                    associations.add(embedded.orElseThrow());
                    if (!embedded.orElseThrow().linkTable()) continue;
                }
                if (object.baseType().isEmpty()) linkRoles.put(object.symbol(), roles);
            }
            names.put(object.symbol(), name);
        }
        var referenced = new HashSet<SymbolRef>();
        linkRoles.values().forEach(roles -> roles.forEach(role -> referenced.addAll(role.targets())));
        associations.forEach(association -> referenced.add(association.target()));
        var inheritance = DbInheritance.plan(plan, objects, names, profile, referenced,
                schemaOptions.contains(Ili2dbLayout.SchemaOption.TYPE_DISCRIMINATOR));
        var discriminated = inheritance.relations().stream().filter(DbInheritance.Relation::discriminator)
                .map(relation -> relation.object().symbol()).collect(Collectors.toSet());
        var tables = new ArrayList<Table>();
        var compositions = new ArrayList<Composition>();
        for (var relation : inheritance.relations()) {
            token.throwIfCancellationRequested();
            var object = relation.object();
            String name = relation.declaration().sqlName();
            boolean discriminator = relation.discriminator();
            var columns = new ArrayList<Column>();
            var secondary = new ArrayList<SecondaryGeometry>();
            boolean geometrySeen = false;
            var roles = linkRoles.getOrDefault(object.symbol(), List.of());
            for (var role : roles) {
                columns.addAll(planReferences(allocator, names, inheritance, name, discriminator, gpkg,
                        role.columns(), role.slot().role().ordered(), schemaOptions));
            }
            for (var contributor : relation.contributors()) {
                for (var attribute : storageAttributes(plan, profile, relation, contributor)) {
                    String attributeName = attribute.name().declared().last();
                    var catalogue = transformations.contains(AttributeTransformation.CATALOGUE_REFERENCE)
                            ? DbCatalogueReference.describe(plan, attribute)
                            : Optional.<DbCatalogueReference>empty();
                    if (catalogue.isPresent()) {
                        var binding = catalogueColumn(plan, contributor, attribute, catalogue.orElseThrow());
                        columns.addAll(planReferences(allocator, names, inheritance, name, discriminator, gpkg,
                                binding, false, schemaOptions));
                        continue;
                    }
                    var text = DbTextExpansion.plan(plan, attribute, transformations);
                    if (text.isPresent()) {
                        var column = textColumn(attribute, allocator.column(name, attributeName), text.orElseThrow());
                        for (String field : column.physicalNames()) {
                            checkColumnName(attribute, field, object.kind() == SymbolKind.STRUCTURE, discriminator, gpkg);
                        }
                        columns.add(column);
                        continue;
                    }
                    if (transformations.contains(AttributeTransformation.EXPANDED_STRUCTURE) && DbExpandedStructure.planned(attribute)) {
                        var binding = DbExpandedStructure.bind(plan, attribute, names, DbExpandedStructure::planned, (path, kind) -> {
                            enumOptions.checkExpandedField(kind, path);
                            String field = allocator.expandedColumn(name, path);
                            checkColumnName(attribute, field, object.kind() == SymbolKind.STRUCTURE, discriminator, gpkg);
                            return field;
                        });
                        columns.add(expandedColumn(plan, attribute, binding));
                        continue;
                    }
                    if (transformations.contains(AttributeTransformation.JSON_STRUCTURE)
                            && "JSON".equals(metaText(attribute.attribute(), "ili2db.mapping"))) {
                        String field = allocator.column(name, attributeName);
                        checkColumnName(attribute, field, object.kind() == SymbolKind.STRUCTURE, discriminator, gpkg);
                        columns.add(jsonColumn(plan, attribute, field, DbJsonStructure.describe(plan, attribute)));
                        continue;
                    }
                    if (transformations.contains(AttributeTransformation.ARRAY)
                            && "ARRAY".equals(metaText(attribute.attribute(), "ili2db.mapping"))) {
                        String field = allocator.column(name, attributeName);
                        checkColumnName(attribute, field, object.kind() == SymbolKind.STRUCTURE, discriminator, gpkg);
                        var array = DbArray.describe(plan, inheritance, object, attribute);
                        if (enumOptions.itfCodes() && array.kind() == Kind.ENUMERATION) {
                            array = array.withEnumeration(DbEnumeration.itfCodes((EnumerationType) array.type(),
                                    attribute.name().declared().toString()));
                        }
                        columns.add(arrayColumn(plan, attribute, field, array));
                        continue;
                    }
                    var wrapped = wrappedGeometries.get(attribute.symbol());
                    var composition = wrapped == null ? describeComposition(plan, object, attribute, child -> {
                        String childName = names.get(child);
                        if (childName == null) throw unsupported("structure storage at " + attribute.name().declared());
                        return allocator.reverseColumn(childName, name, attributeName);
                    }) : Optional.<Composition>empty();
                    if (composition.isPresent()) {
                        var edge = composition.orElseThrow();
                        checkColumnName(attribute, edge.ownerColumn(), true, discriminated.contains(edge.childRoot()), gpkg);
                        compositions.add(edge);
                        continue;
                    }
                    var declaration = attribute.member().overridden() ? object : contributor;
                    for (var binding : storageColumns(plan, declaration, attribute, crs, crsPair, wrapped,
                            geometryComponents.contains(attribute.symbol()))) {
                        if (binding.kind() == Kind.REFERENCE) {
                            columns.addAll(planReferences(allocator, names, inheritance, name, discriminator, gpkg,
                                binding, false, schemaOptions));
                            continue;
                        }
                        String storageName = multiSrs && binding.kind().geometry() ? attributeName + "_" + binding.epsgCode() : attributeName;
                        boolean split = splitGeometries && geometrySeen && binding.kind().geometry();
                        String owner = split ? allocator.geometryTable(plan.model(object.model()).orElseThrow().name().declared().last(),
                                object.topic().flatMap(plan::topic).orElseThrow().name().declared().last(),
                                object.name().declared().last(), storageName) : name;
                        if (split && reservedTable(owner, gpkg)) {
                            throw unsupported("geometry " + attribute.name().declared() + " maps to technical table " + owner);
                        }
                        String column = allocator.column(owner, storageName);
                        checkColumnName(attribute, column, object.kind() == SymbolKind.STRUCTURE, !split && discriminator, gpkg);
                        var named = binding.withName(column).withStrokeArcs(plan, strokeArcs);
                        if (enumOptions.itfCodes() && named.kind() == Kind.ENUMERATION) named = named.withItfCodes();
                        if (enumOptions.textColumns() && (named.kind() == Kind.ENUMERATION || named.kind() == Kind.BOOLEAN)) {
                            var textLabel = DbEnumText.describe(plan, named, enumOptions.beautify());
                            checkColumnName(attribute, textLabel.column(), object.kind() == SymbolKind.STRUCTURE, discriminator, gpkg);
                            allocator.reserveColumn(owner, textLabel.column());
                            named = named.withEnumText(textLabel);
                        }
                        if (binding.kind().geometry()) geometrySeen = true;
                        if (split) secondary.add(new SecondaryGeometry(owner, named));
                        else columns.add(named);
                    }
                }
                for (var association : associationsForOwner(associations, contributor.symbol())) {
                    if (association.linkTable()) continue;
                    var role = association.role();
                    columns.addAll(planReferences(allocator, names, inheritance, name, discriminator, gpkg,
                            roleColumn(role, association.target(), role.name().declared().last()), association.ordered(), schemaOptions));
                }
            }
            tables.add(new Table(object, name, relation.base(), columns, discriminator, relation.oidDomain(),
                    schemaOptions.contains(Ili2dbLayout.SchemaOption.TID_COLUMN), secondary));
        }
        var version = plan.model(plan.rootModels().getFirst()).orElseThrow().languageVersion();
        var mapping = new DbMapping(plan, version, tables, compositions, associations, crsPair, plan.rootModels(),
                inheritance, schemaOptions, Set.of());
        mapping.checkEnumLookupIds(enumOptions);
        return mapping;
    }

    private static List<Column> planReferences(SqlNames allocator, Map<SymbolRef, String> names, DbInheritance inheritance,
                                                String owner, boolean discriminator, boolean gpkg, Column reference,
                                                boolean ordered, Set<Ili2dbLayout.SchemaOption> options) {
        return planReferences(allocator, names, inheritance, owner, discriminator, gpkg, List.of(reference), ordered, options);
    }

    private static List<Column> planReferences(SqlNames allocator, Map<SymbolRef, String> names, DbInheritance inheritance,
                                               String owner, boolean discriminator, boolean gpkg, List<Column> references,
                                               boolean ordered, Set<Ili2dbLayout.SchemaOption> options) {
        var alternatives = referenceAlternatives(inheritance, references, ordered, options);
        var result = new ArrayList<Column>();
        for (var reference : alternatives) {
            String field = reference.slot().name().declared().last();
            String name = alternatives.size() > 1 ? allocator.polymorphicColumn(owner, field, names.get(reference.targetRelation()))
                    : reference.slot() instanceof RoleSlot ? allocator.roleColumn(owner, field) : allocator.column(owner, field);
            checkColumnName(reference.slot(), name, false, discriminator, gpkg);
            var column = reference.withName(name);
            result.add(ordered ? column.withPositionColumn(gpkg) : column);
        }
        return result;
    }

    static List<Column> referenceAlternatives(DbInheritance inheritance, List<Column> references, boolean ordered,
                                               Set<Ili2dbLayout.SchemaOption> options) {
        var alternatives = new ArrayList<Column>();
        var relations = new HashSet<SymbolRef>();
        for (var reference : references) {
            if (reference.external() && options.contains(Ili2dbLayout.SchemaOption.EXTERNAL_REFERENCE_TEXT)) {
                if (inheritance.profile() != DbInheritance.Profile.NEW_CLASS || ordered || references.size() != 1
                        || reference.catalogueReference().isPresent()) {
                    throw unsupported("external reference as text at " + reference.slot().name().declared());
                }
                return List.of(reference.asTextReference());
            }
            var declared = reference.target().orElseThrow();
            var tables = inheritance.referenceTables(declared);
            if (tables.isEmpty() || (ordered && tables.size() > 1)) {
                throw unsupported("reference storage at " + reference.slot().name().declared());
            }
            for (var table : tables) {
                if (!relations.add(table)) {
                    throw unsupported("overlapping reference storage at " + reference.slot().name().declared());
                }
                alternatives.add(!table.equals(declared) || tables.size() > 1 || references.size() > 1
                        ? reference.withReferenceTable(table) : reference);
            }
        }
        return alternatives;
    }

    private static void checkColumnName(TransferSlot slot, String column, boolean structure, boolean discriminator,
                                        boolean gpkg) {
        if (TECHNICAL_COLUMNS.contains(column) || (structure && T_SEQ.equals(column))
                || (discriminator && T_TYPE.equals(column))) {
            String kind = slot instanceof RoleSlot ? "role " : "attribute ";
            throw unsupported(kind + slot.name().declared() + " maps to technical column " + column);
        }
        if (!gpkg && column.length() > 63) {
            throw unsupported("SQL name " + column + " exceeds the PostgreSQL limit at " + slot.name().declared());
        }
    }

    static void checkLayout(TransferPlan plan, List<ObjectDescriptor> objects, DbEnumOptions enumOptions) {
        if (objects.stream().noneMatch(object -> object.kind() == SymbolKind.CLASS && object.topic().isPresent())) {
            throw unsupported("expected a selected model containing classes in topics");
        }
        var topics = objects.stream().flatMap(object -> object.topic().stream()).collect(Collectors.toSet());
        for (var topic : plan.topics()) {
            if (!topics.contains(topic.symbol()) && !plan.rootModels().contains(topic.model())) continue;
            if (topic.oidRules().stream().anyMatch(rule -> !Ili2dbLayout.UUID_OID.equals(oidDomain(plan, rule))
                    && !isTextOid(plan, rule))
                    || topic.genericBindings().stream().anyMatch(binding -> binding.state() == TopicSymbol.BindingState.ABSTRACT)) {
                throw unsupported("topic " + topic.name().declared());
            }
        }
        var bases = baseTables(objects);
        var hierarchy = new HashSet<>(bases);
        for (var object : objects) {
            if (object.baseType().isPresent()) hierarchy.add(object.symbol());
            boolean supported = switch (object.kind()) {
                case CLASS -> object.topic().isPresent() && (object.transferable() || bases.contains(object.symbol()));
                case ASSOCIATION -> supportsAssociation(plan, object, bases);
                case STRUCTURE -> !object.modifiers().contains(Modifier.ABSTRACT) || bases.contains(object.symbol());
                default -> false;
            };
            var domain = oidDomain(plan, object);
            boolean uuid = domain.filter(Ili2dbLayout.UUID_OID::equals).isPresent();
            boolean flat = object.baseType().isEmpty() && !bases.contains(object.symbol());
            boolean standard = domain.filter(Ili2dbLayout.STANDARD_OID::equals).isPresent();
            boolean text = !standard && objectOidRule(object).filter(rule -> isTextOid(plan, rule)).isPresent();
            boolean supportedIdentity = object.oidRules().isEmpty() || switch (object.kind()) {
                case CLASS -> domain.isEmpty() || abstractAnyOid(plan, object) || (standard && flat)
                        || ((uuid || text || standard) && object.baseType().flatMap(plan::object)
                        .map(base -> oidDomain(plan, base).equals(domain) || abstractAnyOid(plan, base)).orElse(!standard));
                case ASSOCIATION -> (uuid || text) && flat;
                default -> false;
            };
            if (!flat && object.topic().flatMap(plan::topic).filter(DbDomains::deferred).isPresent()) {
                throw unsupported("deferred generic domains in a class hierarchy at " + object.name().declared());
            }
            if (!supported || !supportedIdentity) {
                throw unsupported("class layout or identity rules for " + object.name().declared());
            }
        }
        if (!hierarchy.isEmpty()) checkInheritance(plan, objects, hierarchy, enumOptions);
    }

    static void checkBasketLayout(TransferPlan plan, List<ObjectDescriptor> objects,
                                  Set<Ili2dbLayout.SchemaOption> options) {
        if (options.contains(Ili2dbLayout.SchemaOption.BASKET_COLUMN)) return;
        if (options.contains(Ili2dbLayout.SchemaOption.DATASET_COLUMN)) {
            throw new DbException("--create-dataset-col requires --create-basket-col");
        }
        var topics = objects.stream().flatMap(object -> object.topic().stream()).collect(Collectors.toSet());
        for (var topic : plan.topics()) {
            if (!topics.contains(topic.symbol()) && !plan.rootModels().contains(topic.model())) continue;
            if (topic.baseTopic().isPresent() || DbDomains.deferred(topic) || topic.oidRules().stream().anyMatch(OidRule::basket)) {
                throw new DbException(topic.name().declared() + " requires --create-basket-col");
            }
        }
    }

    static Optional<String> concreteOidDomain(TransferPlan plan, ObjectDescriptor object) {
        return abstractAnyOid(plan, object) ? Optional.empty() : oidDomain(plan, object);
    }

    private static boolean abstractAnyOid(TransferPlan plan, ObjectDescriptor object) {
        return object.modifiers().contains(Modifier.ABSTRACT)
                && objectOidRule(object).filter(rule -> oidKind(plan, rule).filter(OidKind.ANY::equals).isPresent()).isPresent();
    }

    static Optional<String> oidDomain(TransferPlan plan, ObjectDescriptor object) {
        return objectOidRule(object).map(rule -> oidDomain(plan, rule));
    }

    private static Optional<OidRule> objectOidRule(ObjectDescriptor object) {
        return object.oidRules().stream().filter(rule -> !rule.basket() && rule.domain().isPresent()).findFirst();
    }

    static String oidDomain(TransferPlan plan, OidRule rule) {
        var ref = new SymbolRef(plan.snapshotId(), rule.domain().orElseThrow());
        var domain = plan.symbol(ref).known().orElseThrow();
        if (domain.header().origin() instanceof Origin.PredefinedOrigin predefined) return predefined.key();
        return DbDomains.name(plan, ref);
    }

    private static boolean isTextOid(TransferPlan plan, OidRule rule) {
        return oidKind(plan, rule).filter(OidKind.TEXT::equals).isPresent();
    }

    private static Optional<OidKind> oidKind(TransferPlan plan, OidRule rule) {
        return rule.domain().map(id -> new SymbolRef(plan.snapshotId(), id))
                .flatMap(ref -> plan.schema().queries().effectiveType(ref).known())
                .flatMap(type -> plan.type(new TypeRef(plan.snapshotId(), type.effectiveType())).known())
                .filter(OidType.class::isInstance).map(OidType.class::cast).map(OidType::oidKind);
    }

    private static boolean supportsAssociation(TransferPlan plan, ObjectDescriptor object, Set<SymbolRef> bases) {
        if (object.baseType().isEmpty() && !bases.contains(object.symbol())) return object.transferable();

        var root = object.baseType().flatMap(plan::object).orElse(object);
        if (root.kind() != SymbolKind.ASSOCIATION || root.baseType().isPresent()
                || !root.modifiers().contains(Modifier.ABSTRACT)
                || (object.baseType().isPresent() && !object.transferable())
                || plan.objects().stream().filter(other -> other.baseType().filter(root.symbol()::equals).isPresent())
                .count() != 1) return false;

        var roles = roleSlots(object);
        return roles.size() == 2 && roles.stream().map(RoleSlot::symbol).toList()
                .equals(roleSlots(root).stream().map(RoleSlot::symbol).toList())
                && roles.stream().allMatch(role -> role.role().aggregation() == RoleAggregation.NONE
                        && !CardinalityUpper.finite(0).equals(role.role().cardinality().upper())
                        && !CardinalityUpper.finite(1).equals(role.role().cardinality().upper()));
    }

    private static List<RoleSlot> roleSlots(ObjectDescriptor association) {
        return association.slots().stream().filter(RoleSlot.class::isInstance).map(RoleSlot.class::cast).toList();
    }

    private static void checkInheritance(TransferPlan plan, List<ObjectDescriptor> objects, Set<SymbolRef> hierarchy,
                                         DbEnumOptions enumOptions) {
        for (var object : objects) {
            if (!hierarchy.contains(object.symbol())) continue;
            boolean catalogueWrapper = object.kind() == SymbolKind.STRUCTURE && DbCatalogueReference.isWrapper(plan, object);
            if (object.baseType().isPresent()
                    && plan.object(object.baseType().orElseThrow()).filter(base -> base.kind() == object.kind()
                    && (catalogueWrapper || base.topic().equals(object.topic()) || (object.kind() == SymbolKind.CLASS
                    && localTopic(plan, object, base)))).isEmpty()) {
                throw unsupported("base type outside the current topic or of a different kind at " + object.name().declared());
            }
            for (var slot : object.slots()) {
                if (!(slot instanceof AttributeSlot attribute)) continue;
                boolean multilingual = DbTextExpansion.isInheritedCollection(plan, object, attribute);
                if (attribute.member().overridden() && !multilingual
                        && !(catalogueWrapper && DbCatalogueReference.isReferenceField(attribute))
                    && !supportsExtendedClassAttribute(plan, object, attribute, enumOptions)) {
                    throw unsupported("extended attribute in inheritance at " + attribute.name().declared());
                }
                if (object.kind() != SymbolKind.STRUCTURE || attribute.attribute().transientAttribute()) continue;
                var type = plan.type(attribute.effectiveType()).known().orElseThrow();
                if (!multilingual && (type instanceof StructureValueType || type instanceof CollectionType)) {
                    throw unsupported("composition in structure inheritance at " + attribute.name().declared());
                }
            }
        }
    }

    private static boolean supportsExtendedClassAttribute(TransferPlan plan, ObjectDescriptor object,
                                                          AttributeSlot attribute, DbEnumOptions enumOptions) {
        if (object.kind() != SymbolKind.CLASS || attribute.attribute().transientAttribute()) return false;
        var type = plan.type(attribute.effectiveType()).known().orElseThrow();
        if (type instanceof TextType text) return text.textKind() == TextKind.TEXT;
        Kind kind = scalarKind(type);
        if (kind == Kind.ENUMERATION) {
            if (enumOptions.itfCodes() || !inlineEnumeration(plan, attribute)) return false;
        } else if (kind != Kind.INTEGER && kind != Kind.BIGINT && kind != Kind.DECIMAL) {
            return false;
        }

        var base = baseAttribute(plan, attribute.attribute());
        var owner = plan.object(new SymbolRef(plan.snapshotId(), base.header().owner().orElseThrow())).orElseThrow();
        return !base.transientAttribute() && owner.topic().equals(object.topic()) && owner.slots().stream()
                .anyMatch(slot -> {
                    if (!(slot instanceof AttributeSlot original) || !original.attribute().id().equals(base.id())) return false;
                    if (kind == Kind.ENUMERATION) return inlineEnumeration(plan, original);
                    var originalType = plan.type(original.effectiveType()).known().orElseThrow();
                    return scalarKind(originalType) == kind && (kind != Kind.DECIMAL
                            || DbDecimal.describe((NumericType) originalType).scale() == DbDecimal.describe((NumericType) type).scale());
                });
    }

    static boolean inlineEnumeration(TransferPlan plan, AttributeSlot slot) {
        return plan.type(slot.declaredType()).known().orElseThrow() instanceof EnumerationType enumeration
                && !enumeration.treeValues();
    }

    private static AttributeSymbol baseAttribute(TransferPlan plan, AttributeSymbol attribute) {
        while (attribute.overrides().isPresent()) {
            var ref = new SymbolRef(plan.snapshotId(), attribute.overrides().orElseThrow());
            attribute = (AttributeSymbol) plan.symbol(ref).known().orElseThrow();
        }
        return attribute;
    }

    static List<AttributeSlot> attributes(ObjectDescriptor object) {
        var attributes = new ArrayList<AttributeSlot>();
        for (var slot : object.slots()) {
            if (object.kind() == SymbolKind.ASSOCIATION && slot instanceof RoleSlot) continue;
            if (!(slot instanceof AttributeSlot attribute)) throw unsupported(slot.name().declared().toString());
            if (!attribute.attribute().transientAttribute() && !attribute.member().overridden()
                    && attribute.member().inheritancePath().getFirst().equals(object.symbol().symbol())) {
                attributes.add(attribute);
            }
        }
        return attributes;
    }

    static List<AttributeSlot> storageAttributes(TransferPlan plan, DbInheritance.Profile profile,
                                                DbInheritance.Relation relation, ObjectDescriptor contributor) {
        var table = relation.object();
        var attributes = attributes(contributor);
        if (profile != DbInheritance.Profile.NEW_CLASS && table.kind() == SymbolKind.CLASS) {
            return copiedClassAttributes(plan, relation, contributor, attributes);
        }
        if (table.equals(contributor) || !DbCatalogueReference.isBase(contributor)) return attributes;
        // When a Smart layout copies a wrapper's base field into the concrete table,
        // ili2db stores the concrete declaration and its narrower target.
        var effective = table.slots().stream().filter(AttributeSlot.class::isInstance)
                .map(AttributeSlot.class::cast).filter(DbCatalogueReference::isReferenceField).findFirst();
        return attributes.stream().map(attribute -> DbCatalogueReference.isReferenceField(attribute)
                ? effective.orElse(attribute) : attribute).toList();
    }

    private static List<AttributeSlot> copiedClassAttributes(TransferPlan plan, DbInheritance.Relation relation,
                                                           ObjectDescriptor contributor, List<AttributeSlot> attributes) {
        var fields = new HashMap<SymbolId, AttributeSlot>();
        for (var attribute : attributes) fields.put(attribute.attribute().id(), attribute);
        boolean changed = false;
        for (var slot : relation.object().slots()) {
            if (!(slot instanceof AttributeSlot override) || !override.member().overridden()
                    || override.attribute().transientAttribute()
                    || !copiedOverride(plan, override)) continue;
            var base = baseAttribute(plan, override.attribute());
            if (relation.contributors().stream().noneMatch(owner ->
                    base.header().owner().filter(owner.symbol().symbol()::equals).isPresent())) continue;
            if (fields.remove(base.id()) != null) changed = true;
            if (override.member().inheritancePath().getFirst().equals(contributor.symbol().symbol())) {
                fields.put(override.attribute().id(), override);
                changed = true;
            }
        }
        if (!changed) return attributes;
        // A copied override keeps its position in the class that declares it.
        var declaration = (ClassSymbol) plan.symbol(contributor.symbol()).known().orElseThrow();
        return declaration.members().stream().map(fields::get).filter(attribute -> attribute != null).toList();
    }

    private static boolean copiedOverride(TransferPlan plan, AttributeSlot override) {
        var type = plan.type(override.effectiveType()).known().orElseThrow();
        return type instanceof NumericType || type instanceof EnumerationType;
    }

    static List<AssociationRole> describeRoles(TransferPlan plan, ObjectDescriptor association) {
        String feature = "association " + association.name().declared();
        var roles = roleSlots(association);
        var domain = oidDomain(plan, association);
        boolean uuid = domain.filter(Ili2dbLayout.UUID_OID::equals).isPresent();
        boolean identified = uuid || (domain.filter(Ili2dbLayout.STANDARD_OID::equals).isEmpty()
                && objectOidRule(association).filter(rule -> isTextOid(plan, rule)).isPresent());
        if (association.kind() != SymbolKind.ASSOCIATION || (!identified && (!association.oidRules().isEmpty()
                || association.modifiers().contains(Modifier.OID))) || (roles.size() != 2 && roles.size() != 3)) {
            throw unsupported(feature);
        }
        if (identified && (roles.size() != 2 || roles.stream().anyMatch(role ->
                CardinalityUpper.finite(1).equals(role.role().cardinality().upper()) || role.role().external()
                        || role.role().ordered() || role.role().aggregation() != RoleAggregation.NONE))) {
            throw unsupported((uuid ? "UUID" : "OID TEXT") + " identity on " + feature);
        }
        if (roles.stream().anyMatch(role -> role.role().cardinality().upper().equals(CardinalityUpper.finite(0)))) {
            throw unsupported(feature + " requires every role to admit a target");
        }
        if (roles.size() == 3 && roles.stream().anyMatch(role ->
                role.role().aggregation() != RoleAggregation.NONE
                        || role.role().cardinality().upper().equals(CardinalityUpper.finite(1)))) {
            throw unsupported(feature + " requires three unordered -- roles with upper bounds greater than one");
        }
        if (association.slots().stream().anyMatch(AttributeSlot.class::isInstance)
                && roles.stream().anyMatch(role -> role.role().aggregation() != RoleAggregation.NONE)) {
            throw unsupported("attributes on an aggregation or composition at " + association.name().declared());
        }
        long external = roles.stream().filter(role -> role.role().external()).count();
        if (external > 0 && (external != 1 || roles.size() != 2
                || association.baseType().isPresent() || !association.transferable()
                || roles.stream().anyMatch(role -> role.role().aggregation() != RoleAggregation.NONE))) {
            throw unsupported("external roles at " + association.name().declared());
        }
        if (roles.stream().anyMatch(role -> role.role().ordered()) && (roles.size() != 2
                || association.baseType().isPresent() || hasSubtypes(plan, association.symbol())
                || roles.stream().anyMatch(role -> role.role().external()
                        || role.role().aggregation() != RoleAggregation.NONE))) {
            throw unsupported("ORDERED roles in " + feature);
        }
        long alternatives = roles.stream().filter(role -> role.role().targets().size() > 1).count();
        if (alternatives > 0 && (alternatives != 1 || roles.size() != 2 || identified || external > 0
                || association.baseType().isPresent() || hasSubtypes(plan, association.symbol())
                || roles.stream().anyMatch(role -> role.role().targets().size() > 2 || role.role().ordered()
                || role.role().aggregation() != RoleAggregation.NONE
                || CardinalityUpper.finite(1).equals(role.role().cardinality().upper())))) {
            throw unsupported("OR role at " + association.name().declared());
        }
        return roles.stream().map(role -> new AssociationRole(role, roleTargets(plan, association, role))).toList();
    }

    private static boolean hasSubtypes(TransferPlan plan, SymbolRef type) {
        return plan.objects().stream().anyMatch(object -> object.baseType().filter(type::equals).isPresent());
    }

    private static boolean inDependentTopic(TransferPlan plan, ObjectDescriptor source, ObjectDescriptor target) {
        if (target.topic().isEmpty()) return false;
        var destination = target.topic().orElseThrow();
        for (var current = source.topic(); current.isPresent(); ) {
            var topic = plan.topic(current.orElseThrow()).orElseThrow();
            if (topic.dependencies().contains(destination)) return true;
            current = topic.baseTopic();
        }
        return false;
    }

    private static boolean localTopic(TransferPlan plan, ObjectDescriptor source, ObjectDescriptor target) {
        return source.topic().isPresent() && target.topic().isPresent()
                && includesTopic(plan, source.topic().orElseThrow(), target.topic().orElseThrow());
    }

    static Optional<EmbeddedAssociation> embeddedAssociation(TransferPlan plan, ObjectDescriptor association, String name,
                                                              List<AssociationRole> roles) {
        if (roles.size() != 2) return Optional.empty();
        var first = roles.getFirst();
        var second = roles.getLast();
        if (!first.single() && !second.single()) {
            return Optional.empty();
        }
        boolean linkTable = association.slots().stream().anyMatch(AttributeSlot.class::isInstance);
        // For 1:1, the second declared role's class owns the column for the first role.
        var target = first.single() ? first : second;
        var owner = first.single() ? second : first;
        if (!owner.slot().role().targets().getFirst().restrictions().isEmpty()) {
            throw unsupported("restriction on the owner of an embedded association at " + association.name().declared());
        }
        boolean crossesTopic = roles.stream().anyMatch(role ->
                !plan.object(role.target()).orElseThrow().topic().equals(association.topic()));
        if (crossesTopic && owner.single()) {
            throw unsupported("one-to-one associations between topics at " + association.name().declared());
        }
        if (crossesTopic && owner.external()) return Optional.empty();
        if (!plan.object(owner.target()).orElseThrow().topic().equals(association.topic())) {
            throw unsupported("embedded association on an inherited owner at " + association.name().declared());
        }
        if (owner.external() || (target.external() && linkTable && owner.single())) {
            throw unsupported("external roles on this embedded association at " + association.name().declared());
        }
        if (target.slot().role().ordered() || (owner.slot().role().ordered() && owner.single())) {
            throw unsupported("ORDERED embedded association " + association.name().declared());
        }
        if (linkTable && hasSubtypes(plan, owner.target())) {
            throw unsupported("embedded association with attributes on an owner with subtypes at "
                    + association.name().declared());
        }
        return Optional.of(new EmbeddedAssociation(association, name, owner, target, linkTable));
    }

    private static List<SymbolRef> roleTargets(TransferPlan plan, ObjectDescriptor association, RoleSlot slot) {
        var role = slot.role();
        String feature = "role " + slot.name().declared();
        if (role.targets().isEmpty() || role.hiding() || role.derivation().isPresent()) {
            throw unsupported(feature);
        }
        var targets = new ArrayList<SymbolRef>();
        for (var alternative : role.targets()) {
            var target = new SymbolRef(plan.snapshotId(), alternative.base());
            if (plan.object(target).filter(object -> object.kind() == SymbolKind.CLASS
                    && (localTopic(plan, association, object) || (role.external() && inDependentTopic(plan, association, object))))
                    .isEmpty()) {
                throw unsupported(feature + " requires a class in an allowed topic");
            }
            if (targets.stream().anyMatch(other -> isAssignableTo(plan, target, other) || isAssignableTo(plan, other, target))) {
                throw unsupported("overlapping targets of " + feature);
            }
            targets.add(target);
        }
        return targets;
    }

    static List<EmbeddedAssociation> associationsForOwner(List<EmbeddedAssociation> associations, SymbolRef owner) {
        return associations.stream().filter(association -> association.owner().equals(owner))
                .sorted(Comparator.comparing(association -> association.role().name().declared().last())).toList();
    }

    static Column roleColumn(RoleSlot role, SymbolRef target, String name) {
        return new Column(role, name, Kind.REFERENCE, Optional.empty(), Optional.of(target), Optional.empty(), 0, 0, false,
                Optional.empty(), 0, Optional.empty());
    }

    static Optional<Composition> describeComposition(TransferPlan plan, ObjectDescriptor parent, AttributeSlot slot,
                                                     String ownerColumn) {
        return describeComposition(plan, parent, slot, child -> ownerColumn);
    }

    static Column catalogueColumn(TransferPlan plan, ObjectDescriptor object, AttributeSlot outer,
                                  DbCatalogueReference binding) {
        var target = referenceTarget(plan, object, outer, binding.reference());
        return new Column(outer, outer.name().declared().last(), Kind.REFERENCE, Optional.of(binding.reference()),
                Optional.of(target), Optional.empty(), 0, 0, false, Optional.empty(), 0, Optional.empty(),
                Optional.of(binding), Optional.empty(), Optional.empty());
    }

    static Column textColumn(AttributeSlot outer, String name, DbTextExpansion binding) {
        return new Column(outer, name, Kind.TEXT, Optional.of(binding.textType()), Optional.empty(), Optional.empty(),
                0, 0, false, Optional.empty(), 0, Optional.empty(), Optional.empty(), Optional.of(binding), Optional.empty());
    }

    static Column expandedColumn(TransferPlan plan, AttributeSlot outer, DbExpandedStructure binding) {
        return new Column(outer, binding.groups().getFirst().typeColumn(), Kind.TEXT, Optional.of(DbStructureShape.type(plan, outer)),
                Optional.empty(), Optional.empty(), 0, 0, false, Optional.empty(), 0, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.of(binding), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    static Column jsonColumn(TransferPlan plan, AttributeSlot outer, String name, DbJsonStructure binding) {
        return new Column(outer, name, Kind.TEXT, Optional.of(DbStructureShape.type(plan, outer)),
                Optional.empty(), Optional.empty(), 0, 0, false, Optional.empty(), 0, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(), Optional.of(binding), Optional.empty(), Optional.empty(), Optional.empty());
    }

    static Column arrayColumn(TransferPlan plan, AttributeSlot outer, String name, DbArray binding) {
        return new Column(outer, name, Kind.TEXT, Optional.of(DbStructureShape.type(plan, outer)),
                Optional.empty(), Optional.empty(), 0, 0, false, Optional.empty(), 0, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(), Optional.empty(), Optional.of(binding), Optional.empty(), Optional.empty());
    }

    static Kind scalarKind(TypeDescriptor type) {
        return switch (type) {
            case TextType ignored -> Kind.TEXT;
            case NumericType number when integerRange(number, Integer.MIN_VALUE, Integer.MAX_VALUE) -> Kind.INTEGER;
            case NumericType number when integerRange(number, Long.MIN_VALUE, Long.MAX_VALUE) -> Kind.BIGINT;
            case NumericType number when DbDecimal.fixedScale(number) -> Kind.DECIMAL;
            case EnumerationType ignored -> Kind.ENUMERATION;
            case FormattedType format when format.origin() instanceof Origin.PredefinedOrigin -> switch (format.predefined().orElse(null)) {
                case DATE -> Kind.DATE;
                case TIMEOFDAY -> Kind.TIME;
                case DATETIME -> Kind.DATETIME;
                case null -> Kind.FORMATTED;
            };
            case FormattedType ignored -> Kind.FORMATTED;
            case OidType oid when oid.oidKind() == OidKind.UUID -> Kind.UUID;
            case OidType oid when oid.oidKind() == OidKind.TEXT -> Kind.OID_TEXT;
            case BlackboxType box when box.blackboxKind() == BlackboxKind.BINARY -> Kind.BINARY;
            case BlackboxType box when box.blackboxKind() == BlackboxKind.XML -> Kind.XML;
            default -> type.kind() == TypeKind.BOOLEAN ? Kind.BOOLEAN : null;
        };
    }

    static Column structureScalar(TransferPlan plan, ObjectDescriptor structure, AttributeSlot field) {
        var column = describeColumn(plan, structure, field, new CrsDefaults(0, 0), Optional.empty(), false);
        if (column.kind() == Kind.REFERENCE || column.kind().geometry()) {
            throw unsupported("structure scalar field " + field.name().declared());
        }
        return column;
    }

    private static Column wrappedGeometryColumn(TransferPlan plan, AttributeSlot outer, DbWrappedGeometry binding,
                                                 CrsDefaults defaults) {
        var component = describeColumn(plan, plan.object(binding.element()).orElseThrow(), binding.component(), defaults,
                Optional.empty(), true);
        if (component.dimensions() != 2) throw unsupported("coalesced geometry dimensions at " + outer.name().declared());
        return new Column(outer, outer.name().declared().last(), binding.kind(), component.effectiveType(),
                Optional.empty(), Optional.empty(), component.epsgCode(), component.srsId(), false, Optional.empty(),
                component.dimensions(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(binding));
    }

    static List<Column> storageColumns(TransferPlan plan, ObjectDescriptor object, AttributeSlot field, CrsDefaults defaults,
                                       Optional<DbCrsPair> crsPair, DbWrappedGeometry wrapped, boolean component) {
        if (wrapped != null) return List.of(wrappedGeometryColumn(plan, field, wrapped, defaults));
        if (component) return List.of(describeColumn(plan, object, field, defaults, Optional.empty(), true));
        return describeColumns(plan, object, field, defaults, crsPair);
    }

    private static Optional<Composition> describeComposition(TransferPlan plan, ObjectDescriptor parent,
                                                             AttributeSlot slot, Function<SymbolRef, String> ownerColumn) {
        String path = slot.name().declared().toString();
        var type = plan.type(slot.effectiveType()).known().orElseThrow(() -> unsupported(path));
        if (!(type instanceof StructureValueType) && !(type instanceof CollectionType)) {
            return Optional.empty();
        }
        String feature = "attribute " + path + " (" + type.kind() + ")";
        if (parent.kind() != SymbolKind.CLASS && parent.kind() != SymbolKind.STRUCTURE) {
            throw unsupported(feature);
        }
        Optional<CollectionKind> collectionKind = Optional.empty();
        StructureValueType structure;
        if (type instanceof CollectionType collection) {
            if (!(plan.type(new TypeRef(plan.snapshotId(), collection.elementType())).known().orElseThrow()
                    instanceof StructureValueType element)) {
                throw unsupported(feature);
            }
            collectionKind = Optional.of(collection.collectionKind());
            structure = element;
        } else {
            if (!optional(slot)) throw unsupported(feature);
            structure = (StructureValueType) type;
        }
        if (structure.target().isEmpty()) throw unsupported(feature);
        var child = new SymbolRef(plan.snapshotId(), structure.target().orElseThrow());
        if (plan.object(child).filter(object -> object.kind() == SymbolKind.STRUCTURE
                && (object.topic().isEmpty() || object.topic().equals(parent.topic()))).isEmpty()) {
            throw unsupported("structure outside the supported topic at " + path);
        }
        var root = plan.object(child).orElseThrow();
        while (root.baseType().isPresent()) root = plan.object(root.baseType().orElseThrow()).orElseThrow();
        var childRoot = root.symbol();
        return Optional.of(new Composition(slot, parent.symbol(), child, childRoot, ownerColumn.apply(childRoot),
                collectionKind));
    }

    static List<Column> describeColumns(TransferPlan plan, ObjectDescriptor object, AttributeSlot attr,
                                        CrsDefaults defaults, Optional<DbCrsPair> crsPair) {
        return crsPair.map(pair -> pair.columns(attr.symbol())).orElseGet(() -> describeColumns(plan, object, attr, defaults));
    }

    static List<Column> describeColumns(TransferPlan plan, ObjectDescriptor object, AttributeSlot attr, CrsDefaults defaults) {
        var generic = DbDomains.geometryBinding(plan, object, attr);
        if (generic.isEmpty()) return List.of(describeColumn(plan, object, attr, defaults, Optional.empty(), false));
        var binding = generic.orElseThrow();
        var columns = new ArrayList<Column>();
        var codes = new HashSet<Integer>();
        for (var id : binding.alternatives()) {
            var column = describeColumn(plan, object, attr, defaults, Optional.of(DbDomains.choice(plan, binding, id)), false);
            if (!codes.add(column.epsgCode())) {
                throw unsupported("multiple concrete domains with EPSG:" + column.epsgCode() + " at " + attr.name().declared());
            }
            if (!columns.isEmpty() && (column.kind() != columns.getFirst().kind()
                    || column.dimensions() != columns.getFirst().dimensions())) {
                throw unsupported("inconsistent geometry kind or dimension at " + attr.name().declared());
            }
            columns.add(column);
        }
        columns.sort(Comparator.comparingInt(Column::epsgCode));
        return List.copyOf(columns);
    }

    private static Column describeColumn(TransferPlan plan, ObjectDescriptor object, AttributeSlot attr,
                                         CrsDefaults defaults, Optional<DomainBinding> selection, boolean geometryComponent) {
        String path = attr.name().declared().toString();
        String name = attr.name().declared().last();
        if (!CardinalityUpper.finite(1).equals(attr.cardinality().upper())) throw unsupported(path);
        var concrete = selection.map(binding -> DbDomains.domain(plan, binding.concrete().symbol()));
        var type = plan.type(attr.effectiveType()).known().orElseThrow(() -> unsupported(path));
        if (!(type instanceof GeometryType) && concrete.isPresent()) {
            type = plan.type(new TypeRef(plan.snapshotId(), concrete.orElseThrow().typeId())).known().orElseThrow();
        }
        boolean geometrySupported = optional(attr) || geometryComponent;
        var curve = type instanceof GeometryType geometry && geometrySupported
                ? describeCurve(plan, geometry, path, defaults, concrete) : null;
        Kind scalar = scalarKind(type);
        Kind kind = scalar != null ? scalar : switch (type) {
            case CoordinateType coordinates when geometrySupported && storageDimensions(coordinates) ->
                    coordinates.multiCoordinate() ? Kind.MULTIPOINT : Kind.POINT;
            case GeometryType ignored when curve != null -> curve.kind();
            case ReferenceType reference when reference.target().isPresent()
                    && (optional(attr)
                    || (DbCatalogueReference.isReferenceField(attr) && DbCatalogueReference.isWrapper(plan, object))) ->
                    Kind.REFERENCE;
            default -> throw unsupported("attribute " + path + " (" + type.kind() + ")");
        };
        if ((object.kind() == SymbolKind.STRUCTURE && kind.geometry() && !geometryComponent)
                || (object.kind() == SymbolKind.ASSOCIATION && (kind.geometry() || kind == Kind.REFERENCE))) {
            throw unsupported("attribute " + path + " (" + type.kind() + ")");
        }
        Optional<SymbolRef> target = Optional.empty();
        if (type instanceof ReferenceType ref) {
            target = Optional.of(referenceTarget(plan, object, attr, ref));
        }
        int dimensions = curve != null ? curve.dimensions()
                : kind.geometry() ? ((CoordinateType) type).dimensions() : 0;
        int epsg = 0;
        if (curve != null) {
            epsg = curve.epsg();
        } else if (kind.geometry()) {
            var coordinates = (CoordinateType) type;
            epsg = concrete.isPresent() ? explicitEpsg(coordinates, concrete.orElseThrow(), path)
                    : pointEpsg(plan, attr, coordinates, defaults, path);
        }
        return new Column(attr, name, kind, Optional.of(type), target, Optional.empty(), epsg, epsg, false, Optional.empty(), dimensions, selection);
    }

    private static SymbolRef referenceTarget(TransferPlan plan, ObjectDescriptor object, AttributeSlot attr, ReferenceType ref) {
        var symbol = new SymbolRef(plan.snapshotId(), ref.target().orElseThrow());
        if (plan.object(symbol).filter(obj -> obj.kind() == SymbolKind.CLASS
                && (localTopic(plan, object, obj) || (ref.external() && inDependentTopic(plan, object, obj)))).isEmpty()) {
            throw unsupported("reference outside the supported topic or class at " + attr.name().declared());
        }
        return symbol;
    }

    private record Curve(Kind kind, int dimensions, int epsg) { }

    private static Curve describeCurve(TransferPlan plan, GeometryType geometry, String path, CrsDefaults defaults,
                                       Optional<DomainSymbol> concrete) {
        String feature = "attribute " + path + " (" + geometry.kind() + ")";
        var geometryKind = geometry.geometryKind();
        var kind = switch (geometryKind) {
            case POLYLINE -> Kind.POLYLINE;
            case MULTIPOLYLINE -> Kind.MULTICURVE;
            case SURFACE, AREA -> Kind.SURFACE;
            case MULTISURFACE, MULTIAREA -> Kind.MULTISURFACE;
        };
        if (geometry.lineAttributes().isPresent() || geometry.lineForms().isEmpty() || geometry.vertexDomain().isEmpty()) {
            throw unsupported(feature);
        }
        for (var id : geometry.lineForms()) {
            var form = plan.symbol(new SymbolRef(plan.snapshotId(), id)).known().orElseThrow();
            if (!(form.origin() instanceof Origin.PredefinedOrigin predefined)
                    || !(predefined.key().equals("INTERLIS.STRAIGHTS") || predefined.key().equals("INTERLIS.ARCS"))) {
                throw unsupported(feature);
            }
        }
        var domain = concrete.orElseGet(() -> vertexDomain(plan, geometry));
        var type = plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        if (domain.generic() || !(type instanceof CoordinateType coordinates)
                || coordinates.multiCoordinate() || !storageDimensions(coordinates)) {
            throw unsupported(feature);
        }
        int epsg = concrete.isPresent() ? explicitEpsg(coordinates, domain, path)
                : geometryEpsg(plan, coordinates, domain, defaults, path);
        return new Curve(kind, coordinates.dimensions(), epsg);
    }

    private static boolean storageDimensions(CoordinateType coordinates) {
        return coordinates.dimensions() == 2 || coordinates.dimensions() == 3;
    }

    static DomainSymbol vertexDomain(TransferPlan plan, GeometryType geometry) {
        return (DomainSymbol) plan.symbol(new SymbolRef(plan.snapshotId(), geometry.vertexDomain().orElseThrow()))
                .known().orElseThrow();
    }

    private static int pointEpsg(TransferPlan plan, AttributeSlot attr, CoordinateType coordinates,
                                 CrsDefaults defaults, String path) {
        Symbol declaration = attr.attribute();
        if (plan.type(attr.declaredType()).known().orElseThrow() instanceof DomainReferenceType alias) {
            var domain = (DomainSymbol) plan.symbol(new SymbolRef(plan.snapshotId(), alias.targetDomain()))
                    .known().orElseThrow();
            // ili2db uses the fallback when the immediate domain itself is an alias.
            if (plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow()
                    instanceof DomainReferenceType) {
                return requireDefaultEpsg(defaults.schemaEpsg(), path);
            }
            declaration = domain;
        }
        return geometryEpsg(plan, coordinates, declaration, defaults, path);
    }

    private static int explicitEpsg(CoordinateType coordinates, DomainSymbol domain, String path) {
        String crs = coordinates.referenceSystem().orElseGet(() -> metaText(domain, "CRS"));
        if (crs == null || !crs.startsWith("EPSG:")) throw new DbException("Expected an EPSG CRS for " + domain.name().value() + " at " + path);
        return Ili2dbLayout.parseEpsgCode(crs.substring(5), path);
    }

    private static int geometryEpsg(TransferPlan plan, CoordinateType coordinates, Symbol declaration,
                                    CrsDefaults defaults, String path) {
        String crs = coordinates.referenceSystem().orElse(null);
        if (crs == null) crs = metaText(declaration, "CRS");
        if (crs == null && defaults.modelEpsg() != 0) return defaults.modelEpsg();
        if (crs == null) {
            Symbol model = declaration;
            while (model.kind() != SymbolKind.MODEL && model.owner().isPresent()) {
                model = plan.symbol(new SymbolRef(plan.snapshotId(), model.owner().orElseThrow())).known().orElseThrow();
            }
            crs = metaText(model, "CRS");
        }
        if (crs == null) return requireDefaultEpsg(defaults.schemaEpsg(), path);
        if (!crs.startsWith("EPSG:")) throw new DbException("Expected an EPSG CRS at " + path + ": " + crs);
        return Ili2dbLayout.parseEpsgCode(crs.substring(5), path);
    }

    private static int requireDefaultEpsg(int defaultEpsg, String path) {
        if (defaultEpsg == 0) throw new DbException("No EPSG CRS declared or configured for " + path);
        return defaultEpsg;
    }

    static String metaText(Symbol symbol, String key) {
        var value = symbol.header().metaAttributes().values().get(key);
        return value == null ? null : value.text();
    }

    private static boolean optional(AttributeSlot attr) {
        return attr.cardinality().lower().equals(BigInteger.ZERO);
    }

    private static boolean integerRange(NumericType type, long min, long max) {
        return type.range().minimum().filter(bound -> bound.precision().fractionalDigits() == 0
                && bound.exactValue().scale() <= 0
                && bound.exactValue().compareTo(BigDecimal.valueOf(min)) >= 0).isPresent()
                && type.range().maximum().filter(bound -> bound.precision().fractionalDigits() == 0
                && bound.exactValue().scale() <= 0
                && bound.exactValue().compareTo(BigDecimal.valueOf(max)) <= 0).isPresent();
    }

    static DbException unsupported(String feature) {
        return new DbException("Unsupported ili2db mapping (" + feature + ")");
    }
}
