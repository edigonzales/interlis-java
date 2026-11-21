package guru.interlis.db;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Modifier;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeKind;
import guru.interlis.model.TypeRef;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.transfer.TransferSlot;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

import static guru.interlis.db.Ili2dbLayout.LV95;
import static guru.interlis.db.Ili2dbLayout.SEQ_COLUMN;
import static guru.interlis.db.Ili2dbLayout.TECHNICAL_COLUMNS;
import static guru.interlis.db.Ili2dbLayout.T_SEQ;
import static guru.interlis.db.Ili2dbLayout.T_TYPE;
import static guru.interlis.db.Ili2dbLayout.foldedName;
import static guru.interlis.db.Ili2dbLayout.reservedTable;

/** The SQL names and storage bindings for a checked transfer plan. */
final class DbMapping {
    enum Kind {
        TEXT, BOOLEAN, INTEGER, ENUMERATION, POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE, REFERENCE;

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

    record Column(TransferSlot slot, String name, Kind kind, Optional<TypeDescriptor> effectiveType,
                  Optional<SymbolRef> target, int epsgCode, int srsId, Optional<String> positionColumn, int dimensions) {
        Column {
            boolean role = slot instanceof RoleSlot;
            if (effectiveType.isEmpty() != role || (role && kind != Kind.REFERENCE)) {
                throw new IllegalArgumentException("Only a role reference can omit the effective attribute type");
            }
        }

        Column withName(String sqlName) {
            return new Column(slot, sqlName, kind, effectiveType, target, epsgCode, srsId, positionColumn, dimensions);
        }

        Column withEpsgCode(int code) {
            return new Column(slot, name, kind, effectiveType, target, code, code, positionColumn, dimensions);
        }

        Column withSrsId(int id) {
            return new Column(slot, name, kind, effectiveType, target, epsgCode, id, positionColumn, dimensions);
        }

        Column withPositionColumn(boolean gpkg) {
            String position = name + "_" + (gpkg ? SEQ_COLUMN : T_SEQ);
            checkColumnName(slot, position, false, false, gpkg);
            return new Column(slot, name, kind, effectiveType, target, epsgCode, srsId, Optional.of(position), dimensions);
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

    record AssociationRole(RoleSlot slot, SymbolRef target) {
        boolean single() {
            return CardinalityUpper.finite(1).equals(slot.role().cardinality().upper());
        }

        boolean external() {
            return slot.role().external();
        }
    }

    /** A geometry stored in a table that shares its owner's identity and basket. */
    record SecondaryGeometry(String table, Column column) { }

    record Table(ObjectDescriptor object, String name, List<Column> columns, boolean hasDiscriminator,
                 Optional<String> oidDomain, List<SecondaryGeometry> secondaryGeometries) {
        Table {
            columns = List.copyOf(columns);
            secondaryGeometries = List.copyOf(secondaryGeometries);
            var names = columns.stream().map(column -> foldedName(column.name()))
                    .collect(Collectors.toCollection(HashSet::new));
            for (var column : columns) {
                column.positionColumn().ifPresent(position -> {
                    if (!names.add(foldedName(position))) {
                        throw unsupported("role position column " + position + " collides in " + name);
                    }
                });
            }
        }

        boolean subtype() { return object.baseType().isPresent(); }
        boolean hasTidColumn() { return !subtype() && (object.kind() != SymbolKind.ASSOCIATION || oidDomain.isPresent()); }
        boolean uuidIdentity() { return oidDomain.filter(Ili2dbLayout.UUID_OID::equals).isPresent(); }
        boolean hasSequenceColumn() { return !subtype() && object.kind() == SymbolKind.STRUCTURE; }

        Column column(SymbolRef field) {
            return columns.stream().filter(column -> column.slot().symbol().equals(field)).findFirst().orElseThrow();
        }
    }

    private final TransferPlan plan;
    private final LanguageVersion version;
    private final List<Table> tables;
    private final Map<SymbolRef, Table> bySymbol;
    private final Map<String, Table> byName;
    private final Map<SymbolRef, List<Table>> chains;
    private final Map<Table, List<Table>> hierarchies;
    private final List<Composition> compositions;
    private final Map<SymbolRef, List<Composition>> byParent;
    private final Map<SymbolRef, List<Composition>> byChild;
    private final List<EmbeddedAssociation> associations;

    DbMapping(TransferPlan plan, LanguageVersion version, List<Table> tables, List<Composition> compositions,
              List<EmbeddedAssociation> associations) {
        this.plan = plan;
        this.version = version;
        this.tables = List.copyOf(tables);
        bySymbol = this.tables.stream().collect(Collectors.toUnmodifiableMap(
                table -> table.object().symbol(), Function.identity()));
        byName = this.tables.stream().collect(Collectors.toUnmodifiableMap(Table::name, Function.identity()));
        var paths = new HashMap<SymbolRef, List<Table>>();
        var families = new HashMap<Table, List<Table>>();
        for (var table : this.tables) {
            if (!table.subtype() && !table.hasDiscriminator()) continue;
            var path = new ArrayDeque<Table>();
            for (var part = table; part != null; part = part.object().baseType().map(this::table).orElse(null)) {
                path.addFirst(part);
            }
            var chain = List.copyOf(path);
            paths.put(table.object().symbol(), chain);
            families.computeIfAbsent(chain.getFirst(), unused -> new ArrayList<>()).add(table);
        }
        chains = Map.copyOf(paths);
        hierarchies = families.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        this.compositions = List.copyOf(compositions);
        byParent = indexCompositions(this.compositions, Composition::parent);
        byChild = indexCompositions(this.compositions, Composition::childRoot);
        checkCompositionCycles();
        this.associations = List.copyOf(associations);
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

    TransferPlan plan() { return plan; }
    LanguageVersion version() { return version; }
    List<Table> tables() { return tables; }

    List<String> storageTables() {
        var names = new ArrayList<String>();
        for (var table : tables) {
            names.add(table.name());
            for (var geometry : table.secondaryGeometries()) names.add(geometry.table());
        }
        return names;
    }
    List<Composition> compositions() { return compositions; }
    List<Composition> compositionsForParent(SymbolRef parent) { return byParent.getOrDefault(parent, List.of()); }
    List<Composition> compositionsForChild(SymbolRef child) { return byChild.getOrDefault(child, List.of()); }
    List<EmbeddedAssociation> associations() { return associations; }
    List<Table> tableChain(SymbolRef type) { return chains.getOrDefault(type, List.of(table(type))); }
    Table rootTable(SymbolRef type) { return tableChain(type).getFirst(); }

    boolean isAssignableTo(SymbolRef actual, SymbolRef declared) {
        return tableChain(actual).stream().anyMatch(part -> part.object().symbol().equals(declared));
    }
    List<Table> hierarchy(Table root) { return hierarchies.getOrDefault(root, List.of()); }

    Table concreteType(Table root, String discriminator, long id) {
        var actual = discriminator == null ? null : byName.get(discriminator);
        if (actual == null || actual.object().modifiers().contains(Modifier.ABSTRACT)
                || !rootTable(actual.object().symbol()).equals(root)) {
            throw new DbException("Unknown or abstract T_Type " + discriminator + " in " + root.name()
                    + " for T_Id " + id);
        }
        return actual;
    }

    static void requireSingleGeometry(ObjectDescriptor object, List<Column> columns) {
        if (columns.stream().filter(column -> column.kind().geometry()).count() > 1) {
            throw unsupported("multiple geometries in a single-geometry table for " + object.name().declared());
        }
    }

    static Set<SymbolRef> baseTables(TransferPlan plan) {
        return plan.objects().stream().flatMap(object -> object.baseType().stream()).collect(Collectors.toSet());
    }

    List<EmbeddedAssociation> linkFields(SymbolRef owner) {
        return associationsForOwner(associations, owner).stream().filter(EmbeddedAssociation::linkTable).toList();
    }

    boolean isEmbeddedLink(Table table) {
        return table.object().kind() == SymbolKind.ASSOCIATION && associations.stream().anyMatch(association -> association.linkTable()
                && association.object().symbol().equals(table.object().symbol()));
    }

    Table table(SymbolRef ref) {
        Table table = bySymbol.get(ref);
        if (table == null) throw new DbException("No table for transfer class " + ref);
        return table;
    }

    static DbMapping planNew(String modelName, SourceText source, boolean gpkg, boolean splitGeometries,
                             CancellationToken token) {
        TransferPlan plan = compile(modelName, source, "model file", token);
        var allocator = new SqlNames();
        var names = new HashMap<SymbolRef, String>();
        var associations = new ArrayList<EmbeddedAssociation>();
        var linkRoles = new HashMap<SymbolRef, List<AssociationRole>>();
        for (var object : plan.objects()) {
            token.throwIfCancellationRequested();
            String topic = object.topic().flatMap(plan::topic).map(value -> value.name().declared().last()).orElse(null);
            String name = allocator.table(modelName, topic, object.name().declared().last());
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
        var bases = baseTables(plan);
        var tables = new ArrayList<Table>();
        var compositions = new ArrayList<Composition>();
        for (var object : plan.objects()) {
            token.throwIfCancellationRequested();
            String name = names.get(object.symbol());
            if (name == null) continue;
            boolean discriminator = object.baseType().isEmpty() && bases.contains(object.symbol());
            var columns = new ArrayList<Column>();
            var secondary = new ArrayList<SecondaryGeometry>();
            boolean geometrySeen = false;
            var roles = linkRoles.get(object.symbol());
            if (roles != null) {
                for (var role : roles) {
                    String column = allocator.roleColumn(name, role.slot().name().declared().last());
                    checkColumnName(role.slot(), column, false, discriminator, gpkg);
                    var binding = roleColumn(role.slot(), role.target(), column);
                    columns.add(role.slot().role().ordered() ? binding.withPositionColumn(gpkg) : binding);
                }
            }
            for (var attribute : attributes(object)) {
                String attributeName = attribute.name().declared().last();
                var composition = describeComposition(plan, object, attribute, child ->
                        allocator.reverseColumn(names.get(child), name, attributeName));
                if (composition.isPresent()) {
                    var edge = composition.get();
                    checkColumnName(attribute, edge.ownerColumn(), true, bases.contains(edge.childRoot()), gpkg);
                    compositions.add(edge);
                    continue;
                }
                var binding = describeColumn(plan, object, attribute, attributeName);
                boolean split = splitGeometries && geometrySeen && binding.kind().geometry();
                String owner = split ? allocator.geometryTable(modelName,
                        object.topic().flatMap(plan::topic).orElseThrow().name().declared().last(),
                        object.name().declared().last(), attributeName) : name;
                if (split && reservedTable(owner, gpkg)) {
                    throw unsupported("geometry " + attribute.name().declared() + " maps to technical table " + owner);
                }
                String column = allocator.column(owner, attributeName);
                checkColumnName(attribute, column, object.kind() == SymbolKind.STRUCTURE, !split && discriminator, gpkg);
                binding = binding.withName(column);
                if (binding.kind().geometry()) {
                    binding = binding.withEpsgCode(LV95);
                    geometrySeen = true;
                }
                if (split) secondary.add(new SecondaryGeometry(owner, binding));
                else columns.add(binding);
            }
            for (var association : associationsForOwner(associations, object.symbol())) {
                if (association.linkTable()) continue;
                var role = association.role();
                String column = allocator.roleColumn(name, role.name().declared().last());
                checkColumnName(role, column, false, discriminator, gpkg);
                var binding = roleColumn(role, association.target(), column);
                columns.add(association.ordered() ? binding.withPositionColumn(gpkg) : binding);
            }
            tables.add(new Table(object, name, columns, discriminator, oidDomain(plan, object), secondary));
        }
        var version = plan.model(plan.rootModels().getFirst()).orElseThrow().languageVersion();
        return new DbMapping(plan, version, tables, compositions, associations);
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

    static TransferPlan compile(String modelName, SourceText source, String description, CancellationToken token) {
        var selector = new ModelSelector.ByName(modelName, Optional.empty(), Optional.of(source.id()));
        var request = new CompilationRequest(List.of(selector), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, token);
        var schema = switch (outcome) {
            case CompilationOutcome.Succeeded succeeded -> succeeded.schema();
            case CompilationOutcome.Rejected rejected -> throw new DbException("Cannot compile " + description + " for "
                    + modelName + " (" + rejected.analysis().diagnostics().stream()
                    .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                    .map(diagnostic -> diagnostic.code() + " " + diagnostic.messageKey())
                    .distinct().limit(5).collect(Collectors.joining(", ")) + ")");
            case CompilationOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
            case CompilationOutcome.Failed failed -> throw new DbException(
                    "Compiler failed for " + description + " of " + modelName + " (" + failed.failureId() + ")");
        };
        var plan = switch (TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED)) {
            case TransferPlanningResult.Ready ready -> ready.plan();
            case TransferPlanningResult.Unsupported rejected -> throw unsupported(rejected.issues().getFirst().toString());
        };
        checkModel(plan);
        return plan;
    }

    private static void checkModel(TransferPlan plan) {
        if (plan.models().size() != 1 || plan.topics().isEmpty()
                || plan.objects().stream().noneMatch(object -> object.kind() == SymbolKind.CLASS)) {
            throw unsupported("expected one standalone model containing classes in topics");
        }
        for (var topic : plan.topics()) {
            if (topic.abstractTopic() || topic.baseTopic().isPresent()
                    || topic.oidRules().stream().anyMatch(rule -> !Ili2dbLayout.UUID_OID.equals(oidDomain(plan, rule)))
                    || !topic.genericBindings().isEmpty()) {
                throw unsupported("topic " + topic.name().declared());
            }
        }
        var bases = baseTables(plan);
        var hierarchy = new HashSet<>(bases);
        for (var object : plan.objects()) {
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
            boolean supportedIdentity = object.oidRules().isEmpty() || switch (object.kind()) {
                case CLASS -> (standard && flat) || (uuid && object.baseType().flatMap(plan::object)
                        .map(base -> oidDomain(plan, base).equals(domain)).orElse(true));
                case ASSOCIATION -> uuid && flat;
                default -> false;
            };
            if (!supported || !supportedIdentity) {
                throw unsupported("class layout or identity rules for " + object.name().declared());
            }
        }
        if (!hierarchy.isEmpty()) checkInheritance(plan, hierarchy);
    }

    static Optional<String> oidDomain(TransferPlan plan, ObjectDescriptor object) {
        return object.oidRules().stream().filter(rule -> !rule.basket()).findFirst()
                .filter(rule -> rule.domain().isPresent()).map(rule -> oidDomain(plan, rule));
    }

    private static String oidDomain(TransferPlan plan, guru.interlis.model.OidRule rule) {
        var domain = plan.symbol(new SymbolRef(plan.snapshotId(), rule.domain().orElseThrow())).known().orElseThrow();
        if (domain.header().origin() instanceof Origin.PredefinedOrigin predefined) return predefined.key();
        var owner = plan.symbol(new SymbolRef(plan.snapshotId(), domain.owner().orElseThrow())).known().orElseThrow();
        return owner.name().value() + "." + domain.name().value();
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

    private static void checkInheritance(TransferPlan plan, Set<SymbolRef> hierarchy) {
        for (var object : plan.objects()) {
            boolean inheritedLayout = hierarchy.contains(object.symbol());
            if (inheritedLayout && object.baseType().isPresent()
                    && plan.object(object.baseType().orElseThrow()).filter(base -> base.kind() == object.kind()
                    && base.topic().equals(object.topic())).isEmpty()) {
                throw unsupported("base type outside the current topic or of a different kind at " + object.name().declared());
            }
            for (var slot : object.slots()) {
                if (!(slot instanceof AttributeSlot attribute) || !inheritedLayout) continue;
                if (attribute.member().overridden()) {
                    throw unsupported("extended attribute in inheritance at " + attribute.name().declared());
                }
                if (object.kind() != SymbolKind.STRUCTURE || attribute.attribute().transientAttribute()) continue;
                var type = plan.type(attribute.effectiveType()).known().orElseThrow();
                if (type instanceof StructureValueType || type instanceof CollectionType) {
                    throw unsupported("composition in structure inheritance at " + attribute.name().declared());
                }
            }
        }
    }

    static List<AttributeSlot> attributes(ObjectDescriptor object) {
        var attributes = new ArrayList<AttributeSlot>();
        for (var slot : object.slots()) {
            if (object.kind() == SymbolKind.ASSOCIATION && slot instanceof RoleSlot) continue;
            if (!(slot instanceof AttributeSlot attribute)) throw unsupported(slot.name().declared().toString());
            if (!attribute.attribute().transientAttribute()
                    && attribute.member().inheritancePath().getFirst().equals(object.symbol().symbol())) {
                attributes.add(attribute);
            }
        }
        return attributes;
    }

    static List<AssociationRole> describeRoles(TransferPlan plan, ObjectDescriptor association) {
        String feature = "association " + association.name().declared();
        var roles = roleSlots(association);
        boolean identified = oidDomain(plan, association).filter(Ili2dbLayout.UUID_OID::equals).isPresent();
        if (association.kind() != SymbolKind.ASSOCIATION || (!identified && (!association.oidRules().isEmpty()
                || association.modifiers().contains(Modifier.OID))) || (roles.size() != 2 && roles.size() != 3)) {
            throw unsupported(feature);
        }
        if (identified && (roles.size() != 2 || roles.stream().anyMatch(role ->
                CardinalityUpper.finite(1).equals(role.role().cardinality().upper()) || role.role().external()
                        || role.role().ordered() || role.role().aggregation() != RoleAggregation.NONE))) {
            throw unsupported("UUID identity on " + feature);
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
        return roles.stream().map(role -> new AssociationRole(role, roleTarget(plan, association, role))).toList();
    }

    private static boolean hasSubtypes(TransferPlan plan, SymbolRef type) {
        return plan.objects().stream().anyMatch(object -> object.baseType().filter(type::equals).isPresent());
    }

    private static boolean inDependentTopic(TransferPlan plan, ObjectDescriptor source, ObjectDescriptor target) {
        return source.topic().flatMap(plan::topic).filter(topic ->
                target.topic().filter(topic.dependencies()::contains).isPresent()).isPresent();
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
        boolean crossesTopic = roles.stream().anyMatch(role ->
                !plan.object(role.target()).orElseThrow().topic().equals(association.topic()));
        if (crossesTopic && owner.single()) {
            throw unsupported("one-to-one associations between topics at " + association.name().declared());
        }
        if (crossesTopic && owner.external()) return Optional.empty();
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

    private static SymbolRef roleTarget(TransferPlan plan, ObjectDescriptor association, RoleSlot slot) {
        var role = slot.role();
        String feature = "role " + slot.name().declared();
        if (role.targets().size() != 1 || !role.targets().getFirst().restrictions().isEmpty()
                || role.hiding() || role.derivation().isPresent()) {
            throw unsupported(feature);
        }
        var target = new SymbolRef(plan.snapshotId(), role.targets().getFirst().base());
        if (plan.object(target).filter(object -> object.kind() == SymbolKind.CLASS
                && (object.topic().equals(association.topic()) || (role.external() && inDependentTopic(plan, association, object))))
                .isEmpty()) {
            throw unsupported(feature + " requires a class in an allowed topic");
        }
        return target;
    }

    static List<EmbeddedAssociation> associationsForOwner(List<EmbeddedAssociation> associations, SymbolRef owner) {
        return associations.stream().filter(association -> association.owner().equals(owner))
                .sorted(Comparator.comparing(association -> association.role().name().declared().last())).toList();
    }

    static Column roleColumn(RoleSlot role, SymbolRef target, String name) {
        return new Column(role, name, Kind.REFERENCE, Optional.empty(), Optional.of(target), 0, 0, Optional.empty(), 0);
    }

    static Optional<Composition> describeComposition(TransferPlan plan, ObjectDescriptor parent, AttributeSlot slot,
                                                     String ownerColumn) {
        return describeComposition(plan, parent, slot, child -> ownerColumn);
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
        if (structure.target().isEmpty() || !structure.restrictions().isEmpty()) throw unsupported(feature);
        var child = new SymbolRef(plan.snapshotId(), structure.target().orElseThrow());
        if (plan.object(child).filter(object -> object.kind() == SymbolKind.STRUCTURE
                && (object.topic().isEmpty() || object.topic().equals(parent.topic()))).isEmpty()) {
            throw unsupported("structure outside the parent model or topic at " + path);
        }
        var root = plan.object(child).orElseThrow();
        while (root.baseType().isPresent()) root = plan.object(root.baseType().orElseThrow()).orElseThrow();
        var childRoot = root.symbol();
        return Optional.of(new Composition(slot, parent.symbol(), child, childRoot, ownerColumn.apply(childRoot),
                collectionKind));
    }

    static Column describeColumn(TransferPlan plan, ObjectDescriptor object, AttributeSlot attr, String name) {
        String path = attr.name().declared().toString();
        if (!CardinalityUpper.finite(1).equals(attr.cardinality().upper())) throw unsupported(path);
        var type = plan.type(attr.effectiveType()).known().orElseThrow(() -> unsupported(path));
        var curve = type instanceof GeometryType geometry && optional(attr) ? describeCurve(plan, geometry, path) : null;
        Kind kind = switch (type) {
            case TextType text when text.textKind() == TextKind.TEXT || text.textKind() == TextKind.MTEXT -> Kind.TEXT;
            case NumericType number when int32(number) -> Kind.INTEGER;
            case EnumerationType enumeration -> Kind.ENUMERATION;
            case CoordinateType coordinates when optional(attr) && storageDimensions(coordinates) ->
                    coordinates.multiCoordinate() ? Kind.MULTIPOINT : Kind.POINT;
            case GeometryType ignored when curve != null -> curve.kind();
            case ReferenceType reference when reference.target().isPresent()
                    && reference.restrictions().isEmpty() && optional(attr) -> Kind.REFERENCE;
            default -> {
                if (type.kind() == TypeKind.BOOLEAN) yield Kind.BOOLEAN;
                throw unsupported("attribute " + path + " (" + type.kind() + ")");
            }
        };
        if ((object.kind() == SymbolKind.STRUCTURE && kind.geometry())
                || (object.kind() == SymbolKind.ASSOCIATION && (kind.geometry() || kind == Kind.REFERENCE))) {
            throw unsupported("attribute " + path + " (" + type.kind() + ")");
        }
        Optional<SymbolRef> target = Optional.empty();
        if (type instanceof ReferenceType ref) {
            var symbol = new SymbolRef(plan.snapshotId(), ref.target().orElseThrow());
            if (plan.object(symbol).filter(obj -> obj.kind() == SymbolKind.CLASS
                    && (obj.topic().equals(object.topic()) || (ref.external() && inDependentTopic(plan, object, obj))))
                    .isEmpty()) {
                throw unsupported("reference outside the supported topic or class at " + path);
            }
            target = Optional.of(symbol);
        }
        int dimensions = curve != null ? curve.dimensions()
                : kind.geometry() ? ((CoordinateType) type).dimensions() : 0;
        return new Column(attr, name, kind, Optional.of(type), target, 0, 0, Optional.empty(), dimensions);
    }

    private record Curve(Kind kind, int dimensions) { }

    private static Curve describeCurve(TransferPlan plan, GeometryType geometry, String path) {
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
        var domain = vertexDomain(plan, geometry);
        var type = plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        if (domain.generic() || !(type instanceof CoordinateType coordinates)
                || coordinates.multiCoordinate() || !storageDimensions(coordinates)) {
            throw unsupported(feature);
        }
        validateGeometryCrs(plan, coordinates, domain, geometryKind.name().toLowerCase(Locale.ROOT), path);
        return new Curve(kind, coordinates.dimensions());
    }

    private static boolean storageDimensions(CoordinateType coordinates) {
        return coordinates.dimensions() == 2 || coordinates.dimensions() == 3;
    }

    static DomainSymbol vertexDomain(TransferPlan plan, GeometryType geometry) {
        return (DomainSymbol) plan.symbol(new SymbolRef(plan.snapshotId(), geometry.vertexDomain().orElseThrow()))
                .known().orElseThrow();
    }

    static void validateGeometryCrs(TransferPlan plan, CoordinateType coordinates, Symbol declaration,
                                    String geometry, String path) {
        String crs = coordinates.referenceSystem().orElse(null);
        if (crs == null) crs = metaText(declaration, "CRS");
        if (crs == null) crs = metaText(plan.symbol(plan.rootModels().getFirst()).known().orElseThrow(), "CRS");
        if (crs != null && !isLv95(crs)) {
            throw new DbException("Expected an EPSG:2056 " + geometry + " at " + path);
        }
    }

    static String metaText(Symbol symbol, String key) {
        var value = symbol.header().metaAttributes().values().get(key);
        return value == null ? null : value.text();
    }

    private static boolean isLv95(String crs) {
        if (!crs.startsWith("EPSG:")) return false;
        try {
            return Integer.parseInt(crs.substring(5)) == LV95;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean optional(AttributeSlot attr) {
        return attr.cardinality().lower().equals(BigInteger.ZERO);
    }

    private static boolean int32(NumericType type) {
        return type.range().minimum().filter(bound -> bound.precision().fractionalDigits() == 0
                && bound.exactValue().scale() <= 0
                && bound.exactValue().compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0).isPresent()
                && type.range().maximum().filter(bound -> bound.precision().fractionalDigits() == 0
                && bound.exactValue().scale() <= 0
                && bound.exactValue().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0).isPresent();
    }

    static DbException unsupported(String feature) {
        return new DbException("Unsupported ili2db mapping (" + feature + ")");
    }
}
