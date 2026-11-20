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
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.GeometryKind;
import guru.interlis.model.GeometryType;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceType;
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
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.db.Ili2dbLayout.LV95;
import static guru.interlis.db.Ili2dbLayout.TECHNICAL_COLUMNS;
import static guru.interlis.db.Ili2dbLayout.reservedTable;

/** The SQL names and storage bindings for a checked transfer plan. */
final class DbMapping {
    enum Kind {
        TEXT, BOOLEAN, INTEGER, ENUMERATION, POINT, POLYLINE, SURFACE, REFERENCE;

        boolean geometry() { return this == POINT || this == POLYLINE || this == SURFACE; }

        String geometryType() {
            return switch (this) {
                case POINT -> "POINT";
                case POLYLINE -> "COMPOUNDCURVE";
                case SURFACE -> "CURVEPOLYGON";
                default -> throw new IllegalStateException("Expected a geometry kind: " + this);
            };
        }
    }

    record Column(AttributeSlot slot, String name, Kind kind, TypeDescriptor effectiveType,
                  Optional<SymbolRef> target, int srsId) {
        Column withSrsId(int id) {
            return new Column(slot, name, kind, effectiveType, target, id);
        }
    }
    record Table(ObjectDescriptor object, String name, List<Column> columns) {
        Table {
            columns = List.copyOf(columns);
            if (columns.stream().filter(column -> column.kind().geometry()).count() > 1) {
                throw unsupported("multiple geometries in " + object.name().declared());
            }
        }
    }

    private final TransferPlan plan;
    private final LanguageVersion version;
    private final List<Table> tables;
    private final Map<SymbolRef, Table> bySymbol;

    DbMapping(TransferPlan plan, LanguageVersion version, List<Table> tables) {
        this.plan = plan;
        this.version = version;
        this.tables = List.copyOf(tables);
        bySymbol = this.tables.stream().collect(Collectors.toUnmodifiableMap(
                table -> table.object().symbol(), Function.identity()));
    }

    TransferPlan plan() { return plan; }
    LanguageVersion version() { return version; }
    List<Table> tables() { return tables; }

    Table table(SymbolRef ref) {
        Table table = bySymbol.get(ref);
        if (table == null) throw new DbException("No table for transfer class " + ref);
        return table;
    }

    static DbMapping planNew(String modelName, SourceText source, boolean gpkg, CancellationToken token) {
        TransferPlan plan = compile(modelName, source, "model file", token);
        var allocator = new SqlNames();
        var tables = new ArrayList<Table>();
        for (var object : plan.objects()) {
            token.throwIfCancellationRequested();
            var topic = plan.topic(object.topic().orElseThrow()).orElseThrow();
            String name = allocator.table(modelName, topic.name().declared().last(), object.name().declared().last());
            if (reservedTable(name, gpkg)) {
                throw unsupported("class " + object.name().declared() + " maps to technical table " + name);
            }
            var columns = new ArrayList<Column>();
            for (var attribute : attributes(object)) {
                String column = allocator.column(name, attribute.name().declared().last());
                if (TECHNICAL_COLUMNS.contains(column)) {
                    throw unsupported("attribute " + attribute.name().declared() + " maps to technical column " + column);
                }
                if (!gpkg && column.length() > 63) {
                    throw unsupported("SQL name " + column + " exceeds the PostgreSQL limit at "
                            + attribute.name().declared());
                }
                var binding = describeColumn(plan, object, attribute, column);
                columns.add(binding.kind().geometry() ? binding.withSrsId(LV95) : binding);
            }
            tables.add(new Table(object, name, columns));
        }
        return new DbMapping(plan, plan.model(plan.rootModels().getFirst()).orElseThrow().languageVersion(), tables);
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
        if (plan.models().size() != 1 || plan.topics().isEmpty() || plan.objects().isEmpty()) {
            throw unsupported("expected one standalone model containing classes in topics");
        }
        for (var topic : plan.topics()) {
            if (topic.abstractTopic() || topic.baseTopic().isPresent() || !topic.oidRules().isEmpty()
                    || !topic.genericBindings().isEmpty() || !topic.dependencies().isEmpty()) {
                throw unsupported("topic " + topic.name().declared());
            }
        }
        for (var object : plan.objects()) {
            if (object.kind() != SymbolKind.CLASS || !object.transferable() || object.baseType().isPresent()
                    || !object.oidRules().isEmpty()) {
                throw unsupported("class layout or identity rules for " + object.name().declared());
            }
        }
    }

    static List<AttributeSlot> attributes(ObjectDescriptor object) {
        var attributes = new ArrayList<AttributeSlot>();
        for (var slot : object.slots()) {
            if (!(slot instanceof AttributeSlot attribute)) throw unsupported(slot.name().declared().toString());
            if (!attribute.attribute().transientAttribute()) attributes.add(attribute);
        }
        return attributes;
    }

    static Column describeColumn(TransferPlan plan, ObjectDescriptor object, AttributeSlot attr, String name) {
        String path = attr.name().declared().toString();
        if (!CardinalityUpper.finite(1).equals(attr.cardinality().upper())) throw unsupported(path);
        var type = plan.type(attr.effectiveType()).known().orElseThrow(() -> unsupported(path));
        Kind kind = switch (type) {
            case TextType text when text.textKind() == TextKind.TEXT || text.textKind() == TextKind.MTEXT -> Kind.TEXT;
            case NumericType number when int32(number) -> Kind.INTEGER;
            case EnumerationType enumeration -> Kind.ENUMERATION;
            case CoordinateType point when xy(point) && optional(attr) -> Kind.POINT;
            case GeometryType geometry when optional(attr) && supportedCurveGeometry(plan, geometry, path) ->
                    geometry.geometryKind() == GeometryKind.POLYLINE ? Kind.POLYLINE : Kind.SURFACE;
            case ReferenceType reference when !reference.external() && reference.target().isPresent()
                    && reference.restrictions().isEmpty() && optional(attr) -> Kind.REFERENCE;
            default -> {
                if (type.kind() == TypeKind.BOOLEAN) yield Kind.BOOLEAN;
                throw unsupported("attribute " + path + " (" + type.kind() + ")");
            }
        };
        Optional<SymbolRef> target = Optional.empty();
        if (type instanceof ReferenceType ref) {
            var symbol = new SymbolRef(plan.snapshotId(), ref.target().orElseThrow());
            if (plan.object(symbol).filter(obj -> obj.topic().equals(object.topic())).isEmpty()) {
                throw unsupported("reference to another topic or an unmapped class at " + path);
            }
            target = Optional.of(symbol);
        }
        return new Column(attr, name, kind, type, target, 0);
    }

    private static boolean xy(CoordinateType coordinate) {
        return coordinate.dimensions() == 2 && !coordinate.multiCoordinate();
    }

    private static boolean supportedCurveGeometry(TransferPlan plan, GeometryType geometry, String path) {
        var kind = geometry.geometryKind();
        if ((kind != GeometryKind.POLYLINE && kind != GeometryKind.SURFACE && kind != GeometryKind.AREA)
                || geometry.lineAttributes().isPresent() || geometry.lineForms().isEmpty()
                || geometry.vertexDomain().isEmpty()) {
            return false;
        }
        for (var id : geometry.lineForms()) {
            var form = plan.symbol(new SymbolRef(plan.snapshotId(), id)).known().orElseThrow();
            if (!(form.origin() instanceof Origin.PredefinedOrigin predefined)
                    || !(predefined.key().equals("INTERLIS.STRAIGHTS") || predefined.key().equals("INTERLIS.ARCS"))) {
                return false;
            }
        }
        var domain = vertexDomain(plan, geometry);
        var type = plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        if (domain.generic() || !(type instanceof CoordinateType coordinates) || !xy(coordinates)) {
            return false;
        }
        validateGeometryCrs(plan, coordinates, domain, kind.name().toLowerCase(Locale.ROOT), path);
        return true;
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
