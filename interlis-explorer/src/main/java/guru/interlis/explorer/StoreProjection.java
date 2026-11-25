package guru.interlis.explorer;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.FormattedType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TemporalKind;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.*;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Derives query rows from the same occurrences the canonical store encodes. */
final class StoreProjection implements AutoCloseable {
    private final TransferPlan plan;
    private final StoreCodec codec;
    private final CancellationToken cancellation;
    private final DuckDBAppender references;
    private final DuckDBAppender geometries;
    private final DuckDBAppender scalars;
    private final DuckDBAppender associations;
    private final DuckDBAppender roles;
    private final Map<SymbolRef, AttributeSlot> attributes = new HashMap<>();
    private final Map<SymbolId, SymbolId> domains = new HashMap<>();
    private long associationId;
    private int referencePosition;

    static void createSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE reference ADD COLUMN IF NOT EXISTS value_path VARCHAR");
            statement.execute("CREATE TABLE IF NOT EXISTS projection_version (version INTEGER NOT NULL)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS geometry (
                        source_position BIGINT NOT NULL, value_path VARCHAR NOT NULL,
                        crs VARCHAR, geom GEOMETRY, projection_error VARCHAR,
                        CHECK ((geom IS NOT NULL) = (projection_error IS NULL))
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS scalar (
                        source_position BIGINT NOT NULL, value_path VARCHAR NOT NULL, text_value VARCHAR,
                        integer_value BIGINT, number_text VARCHAR, boolean_value BOOLEAN, date_value DATE,
                        decimal_value DECIMAL(38, 18)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS association (
                        id BIGINT PRIMARY KEY, type_name VARCHAR NOT NULL, source_position BIGINT NOT NULL,
                        value_path VARCHAR NOT NULL, fields JSON NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS association_role (
                        association_id BIGINT NOT NULL, slot VARCHAR NOT NULL, target_position BIGINT,
                        reference_path VARCHAR
                    )
                    """);
        }
    }

    static boolean prepared(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT version FROM projection_version WHERE version = 1")) {
            return result.next();
        }
    }

    StoreProjection(Connection connection, TransferPlan plan, StoreCodec codec, CancellationToken cancellation)
            throws SQLException {
        this.plan = plan;
        this.codec = codec;
        this.cancellation = cancellation;
        for (var object : plan.objects()) {
            for (var slot : object.slots()) {
                if (slot instanceof AttributeSlot attribute) {
                    attributes.put(attribute.symbol(), attribute);
                }
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TEMP TABLE geometry_stage (
                        source_position BIGINT, value_path VARCHAR, crs VARCHAR, wkb BLOB, projection_error VARCHAR
                    )
                    """);
        }
        var duckdb = connection.unwrap(DuckDBConnection.class);
        references = duckdb.createAppender("reference");
        geometries = duckdb.createAppender("temp", "main", "geometry_stage");
        scalars = duckdb.createAppender("scalar");
        associations = duckdb.createAppender("association");
        roles = duckdb.createAppender("association_role");
    }

    void startBasket(StartBasket basket) {
        domains.clear();
        var topics = new ArrayList<>(basket.additionalTopics());
        topics.addFirst(basket.topic());
        for (var topic : topics) {
            for (var binding : plan.topic(topic).orElseThrow().genericBindings()) {
                if (binding.alternatives().size() == 1) {
                    domains.put(binding.genericDomain(), binding.alternatives().getFirst());
                }
            }
        }
        for (var binding : basket.domainBindings()) {
            domains.put(binding.generic().symbol(), binding.concrete().symbol());
        }
    }

    void project(long objectPosition, Structured data) throws SQLException {
        referencePosition = 0;
        var type = plan.object(data.type()).orElseThrow();
        if (type.kind() == SymbolKind.ASSOCIATION) {
            association(objectPosition, "", type, data, null, null);
        }
        structure(objectPosition, "", type, data);
    }

    private void structure(long object, String path, ObjectDescriptor type, Structured value) throws SQLException {
        for (var field : value.values().entrySet()) {
            String slot = codec.slotName(field.getKey());
            String fieldPath = path + '/' + slot;
            if (type.kind() != SymbolKind.ASSOCIATION) {
                var symbol = plan.symbol(field.getKey()).known().orElseThrow();
                if (symbol.kind() == SymbolKind.ROLE) {
                    embeddedAssociation(object, fieldPath, field.getKey(), field.getValue());
                }
            }
            var attribute = attributes.get(field.getKey());
            visit(object, fieldPath, slot, attribute == null ? null : attribute.declaredType(), field.getValue());
        }
    }

    private void visit(long object, String path, String slot, TypeRef type, TransferValue value) throws SQLException {
        cancellation.throwIfCancellationRequested();
        switch (value) {
            case Structured data -> structure(object, path, plan.object(data.type()).orElseThrow(), data);
            case Repeated list -> {
                TypeRef element = elementType(type);
                for (int index = 0; index < list.values().size(); index++) {
                    visit(object, path + '/' + index, slot, element, list.values().get(index));
                }
            }
            case Reference reference -> references.beginRow().append(object).append(++referencePosition)
                    .append(slot).append(StoreCodec.reference(reference).toString()).append(reference.target().value())
                    .append(!(reference.target() instanceof TransferId))
                    .append(reference.targetBasket().map(BasketId::value).orElse(null)).append(reference.external())
                    .appendNull().append(path).endRow();
            case TransferGeometry geometry -> geometry(object, path, type, geometry);
            case Text text -> scalar(object, path, text.value(), null, null, null, date(type, text.value()), null);
            case Enumeration enumeration -> scalar(object, path, String.join(".", enumeration.path()),
                    null, null, null, null, null);
            case Oid oid -> scalar(object, path, oid.lexeme(), null, null, null, null, null);
            case NumberValue number -> scalar(object, path, null, integral(number), number.exactValue().toString(),
                    null, null, decimal(number.exactValue()));
            case BooleanValue bool -> scalar(object, path, null, null, null, bool.value(), null, null);
            default -> {}
        }
    }

    private void embeddedAssociation(long object, String path, SymbolRef slot, TransferValue value) throws SQLException {
        var declaration = plan.symbol(slot).known().orElseThrow();
        var declared = plan.object(new SymbolRef(plan.snapshotId(), declaration.owner().orElseThrow())).orElseThrow();
        var wireRole = roles(declared).stream().filter(role -> role.symbol().equals(slot)).findFirst().orElseThrow();
        if (value instanceof Structured data) {
            var actual = plan.object(data.type()).orElseThrow();
            if (actual.kind() == SymbolKind.ASSOCIATION) {
                association(object, path, actual, data, wireRole, null);
            }
        } else if (value instanceof Reference reference) {
            association(object, path, declared, null, wireRole, reference);
        } else if (value instanceof Repeated repeated) {
            for (int index = 0; index < repeated.values().size(); index++) {
                embeddedAssociation(object, path + '/' + index, slot, repeated.values().get(index));
            }
        }
    }

    private void association(long object, String path, ObjectDescriptor type, Structured data,
                             RoleSlot embeddedWireRole, Reference bareReference) throws SQLException {
        long id = ++associationId;
        String fields = data == null ? "{}" : codec.fields(data).toString();
        associations.beginRow().append(id).append(type.name().declared().toString()).append(object)
                .append(path).append(fields).endRow();
        var ends = roles(type);
        for (var end : ends) {
            String slot = codec.slotName(end.symbol());
            if (embeddedWireRole != null && ends.size() == 2
                    && end.member().position() != embeddedWireRole.member().position()) {
                role(id, slot, object, null);
            } else if (bareReference != null) {
                role(id, slot, null, path);
            } else if (data != null) {
                String fieldPath = path + '/' + slot;
                addExplicitRoles(id, slot, fieldPath, data.value(end.symbol()).orElse(null));
            }
        }
    }

    private void addExplicitRoles(long association, String slot, String path, TransferValue value) throws SQLException {
        if (value instanceof Reference) {
            role(association, slot, null, path);
        } else if (value instanceof Repeated repeated) {
            for (int index = 0; index < repeated.values().size(); index++) {
                addExplicitRoles(association, slot, path + '/' + index, repeated.values().get(index));
            }
        }
    }

    private static List<RoleSlot> roles(ObjectDescriptor association) {
        return association.slots().stream().filter(RoleSlot.class::isInstance).map(RoleSlot.class::cast).toList();
    }

    private void role(long id, String slot, Long implicitTarget, String referencePath) throws SQLException {
        roles.beginRow().append(id).append(slot).append(implicitTarget).append(referencePath).endRow();
    }

    private void scalar(long object, String path, String text, Long integer, String number, Boolean bool,
                        LocalDate date, BigDecimal decimal) throws SQLException {
        scalars.beginRow().append(object).append(path).append(text).append(integer).append(number)
                .append(bool).append(date).append(decimal).endRow();
    }

    private static BigDecimal decimal(BigDecimal exact) {
        var normalized = exact.stripTrailingZeros();
        if (normalized.scale() > 18 || (long) normalized.precision() - normalized.scale() > 20) {
            return null;
        }
        return normalized.setScale(18);
    }

    private static Long integral(NumberValue number) {
        try {
            return number.exactValue().longValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private LocalDate date(TypeRef type, String value) {
        var descriptor = type == null ? null : plan.type(type).known().orElse(null);
        if (descriptor instanceof DomainReferenceType reference) {
            var domain = domain(reference.targetDomain());
            return domain == null ? null : date(ref(domain.typeId()), value);
        }
        if (descriptor instanceof FormattedType formatted && formatted.predefined().orElse(null) == TemporalKind.DATE) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private TypeRef elementType(TypeRef type) {
        var descriptor = type == null ? null : plan.type(type).known().orElse(null);
        if (descriptor instanceof DomainReferenceType reference) {
            var domain = domain(reference.targetDomain());
            return domain == null ? null : elementType(ref(domain.typeId()));
        }
        return descriptor instanceof CollectionType collection ? ref(collection.elementType()) : type;
    }

    private void geometry(long object, String path, TypeRef type, TransferGeometry value) throws SQLException {
        var coordinates = coordinates(type, null);
        byte[] wkb = null;
        String failure = null;
        try {
            wkb = GeometryProjection.encode(value, coordinates.tolerance());
        } catch (IllegalArgumentException exception) {
            failure = java.util.Objects.requireNonNullElse(exception.getMessage(), "Geometry projection failed");
        }
        geometries.beginRow().append(object).append(path).append(coordinates.crs())
                .append(wkb).append(failure).endRow();
    }

    private record Coordinates(String crs, double tolerance) {}

    private Coordinates coordinates(TypeRef type, String crs) {
        var descriptor = type == null ? null : plan.type(type).known().orElse(null);
        if (descriptor instanceof DomainReferenceType reference) {
            return coordinates(domain(reference.targetDomain()), crs);
        }
        if (descriptor instanceof GeometryType geometry && geometry.vertexDomain().isPresent()) {
            return coordinates(domain(geometry.vertexDomain().orElseThrow()), crs);
        }
        if (descriptor instanceof CoordinateType coordinate) {
            String resolved = crs == null ? normalizedCrs(coordinate.referenceSystem().orElse(null)) : crs;
            double tolerance = coordinate.axes().getFirst().minimum().map(bound -> {
                var precision = bound.precision();
                int places = precision.fractionalDigits() - precision.exponent();
                return places <= 0 ? 0.5 : Math.pow(10, -places);
            }).orElse(0.001);
            return new Coordinates(resolved, tolerance);
        }
        return new Coordinates(crs, 0.001);
    }

    private Coordinates coordinates(DomainSymbol domain, String crs) {
        if (domain == null) {
            return new Coordinates(crs, 0.001);
        }
        String effectiveCrs = crs;
        if (effectiveCrs == null) {
            for (var candidate = domain; candidate != null && effectiveCrs == null;
                 candidate = candidate.baseDomain().map(this::domain).orElse(null)) {
                var meta = candidate.header().metaAttributes().values().get("CRS");
                if (meta != null) {
                    effectiveCrs = normalizedCrs(meta.text());
                }
            }
        }
        return coordinates(ref(domain.typeId()), effectiveCrs);
    }

    private static String normalizedCrs(String value) {
        if (value == null) {
            return null;
        }
        String crs = value.strip().toUpperCase(java.util.Locale.ROOT);
        return crs.matches("EPSG:[0-9]+") ? crs : null;
    }

    private DomainSymbol domain(SymbolId id) {
        return plan.symbol(new SymbolRef(plan.snapshotId(), domains.getOrDefault(id, id)))
                .known().orElse(null) instanceof DomainSymbol domain ? domain : null;
    }

    private TypeRef ref(guru.interlis.model.TypeId id) {
        return new TypeRef(plan.snapshotId(), id);
    }

    static void finish(Connection connection, boolean spatial, CancellationToken cancellation) throws SQLException {
        cancellation.throwIfCancellationRequested();
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO geometry
                    SELECT source_position, value_path, crs, ST_GeomFromWKB(wkb), projection_error FROM geometry_stage
                    """);
            statement.execute("DROP TABLE geometry_stage");
            statement.execute("""
                    UPDATE association_role AS ar SET target_position = r.target_position
                    FROM association a, reference r
                    WHERE a.id = ar.association_id AND a.source_position = r.source_position
                      AND ar.reference_path = r.value_path
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS reference_target ON reference(target_position)");
            statement.execute("CREATE INDEX IF NOT EXISTS association_role_target ON association_role(target_position)");
            if (spatial) {
                statement.execute("CREATE INDEX IF NOT EXISTS geometry_rtree ON geometry USING RTREE (geom)");
            }
            statement.execute("INSERT INTO projection_version VALUES (1)");
        }
        cancellation.throwIfCancellationRequested();
    }

    void flush() throws SQLException {
        references.flush();
        geometries.flush();
        scalars.flush();
        associations.flush();
        roles.flush();
    }

    @Override
    public void close() throws SQLException {
        try (references; geometries; scalars; associations; roles) {
            // Release in reverse creation order and retain a suppressed close failure.
        }
    }
}
