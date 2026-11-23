package guru.interlis.explorer;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;
import guru.interlis.transfer.RoleSlot;
import guru.interlis.transfer.TransferEvent.*;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.ObjectKey;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.xtf24.Xtf24Writer;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.io.OutputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;

/**
 * A single XTF 2.4 FULL transfer in a local DuckDB file. Use one caller at a time.
 * Returned objects and lists are immutable and bound to the supplied transfer plan.
 * The caller supplies a compatible plan each time the file is opened. Reading a
 * declaration that the plan cannot bind uniquely fails with IllegalArgumentException.
 */
public final class DuckDbStore implements AutoCloseable {
    private static final String RESOLVE_REFERENCES = """
            UPDATE reference SET target_position = resolved.target_position
            FROM (
                SELECT r.source_position, r.position, min(t.position) AS target_position
                FROM reference r
                JOIN object s ON s.position = r.source_position
                JOIN object t ON r.target_key = CASE WHEN r.stable THEN t.oid ELSE t.tid END
                JOIN basket b ON b.position = t.basket_position
                WHERE (r.external OR t.basket_position = s.basket_position)
                  AND (r.target_basket IS NULL OR r.target_basket = b.bid)
                GROUP BY r.source_position, r.position
                HAVING count(*) = 1
            ) resolved
            WHERE reference.source_position = resolved.source_position AND reference.position = resolved.position
            """;

    private final String url;
    private final Connection connection;
    private final TransferPlan plan;
    private final StoreCodec codec;
    private final List<String> roleNames;
    private final Spatial spatial;

    /** A stored occurrence and its database-local position, including occurrences without a TID or OID. */
    public record StoredObject(long position, TransferObject object) {}

    /** A reference at its declaring slot. The target is empty when there is no unique local match. */
    public record ReferenceLink(QualifiedName slot, Reference reference, Optional<StoredObject> target) {}

    /** An indexed reference and its database-local target position, if uniquely resolved. */
    public record ReferencePosition(Reference reference, OptionalLong targetPosition) {}

    private DuckDbStore(String url, Connection connection, TransferPlan plan, Spatial spatial) {
        this.url = url;
        this.connection = connection;
        this.plan = plan;
        this.spatial = spatial;
        codec = new StoreCodec(plan);
        roleNames = plan.objects().stream().flatMap(type -> type.slots().stream())
                .filter(RoleSlot.class::isInstance).map(slot -> slot.name().declared().toString())
                .distinct().toList();
    }

    /** Opens or creates a local DuckDB file. Both arguments are nonnull. */
    public static DuckDbStore open(Path file, TransferPlan plan) throws SQLException {
        return open(file, plan, false);
    }

    /**
     * Opens or creates a local DuckDB file, attempting to provision the signed
     * Spatial extension if needed. A file with no persisted spatial index can
     * still be used when the extension is unavailable.
     */
    public static DuckDbStore openWithSpatial(Path file, TransferPlan plan) throws SQLException {
        return open(file, plan, true);
    }

    private static DuckDbStore open(Path file, TransferPlan plan, boolean provisionSpatial) throws SQLException {
        Objects.requireNonNull(plan, "plan");
        String url = "jdbc:duckdb:" + file.toAbsolutePath();
        var connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
            Spatial spatial = loadSpatial(connection, provisionSpatial);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS transfer (
                        singleton BOOLEAN PRIMARY KEY CHECK (singleton), metadata JSON NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS basket (
                        position BIGINT PRIMARY KEY, bid VARCHAR NOT NULL UNIQUE, metadata JSON NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS object (
                        position BIGINT PRIMARY KEY, basket_position BIGINT NOT NULL,
                        type_name VARCHAR NOT NULL, tid VARCHAR, oid VARCHAR, identity JSON NOT NULL,
                        consistency VARCHAR NOT NULL, fields JSON NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS reference (
                        source_position BIGINT NOT NULL, position INTEGER NOT NULL,
                        slot VARCHAR NOT NULL, value JSON NOT NULL, target_key VARCHAR NOT NULL,
                        stable BOOLEAN NOT NULL, target_basket VARCHAR, external BOOLEAN NOT NULL,
                        target_position BIGINT, PRIMARY KEY (source_position, position)
                    )
                    """);
            var store = new DuckDbStore(url, connection, plan, spatial);
            store.prepareProjections();
            return store;
        } catch (SQLException | RuntimeException | Error failure) {
            closeFailedOpen(connection, failure);
            throw failure;
        }
    }

    /**
     * Opens a previously prepared local store for inspection and streaming
     * queries. Close all writable connections to the file before calling this.
     * External access and extension installation are disabled for this session.
     */
    public static DuckDbStore openReadOnly(Path file, TransferPlan plan) throws SQLException {
        Objects.requireNonNull(plan, "plan");
        String url = "jdbc:duckdb:" + file.toAbsolutePath();
        var properties = new Properties();
        properties.setProperty("access_mode", "READ_ONLY");
        properties.setProperty("jdbc_stream_results", "true");
        var connection = DriverManager.getConnection(url, properties);
        try (var statement = connection.createStatement()) {
            Spatial spatial = loadSpatial(connection, false);
            if (!StoreProjection.prepared(connection)) {
                throw new SQLException("The explorer store has no committed query projections");
            }
            statement.execute("SET autoinstall_known_extensions = false");
            statement.execute("SET autoload_known_extensions = false");
            statement.execute("SET enable_external_access = false");
            statement.execute("SET lock_configuration = true");
            return new DuckDbStore(url, connection, plan, spatial);
        } catch (SQLException | RuntimeException | Error failure) {
            closeFailedOpen(connection, failure);
            throw failure;
        }
    }

    private record Spatial(boolean available, String diagnostic) {}

    private static Spatial loadSpatial(Connection connection, boolean provision) {
        try {
            execute(connection, "LOAD spatial");
            return new Spatial(true, null);
        } catch (SQLException unavailable) {
            if (!provision) {
                return new Spatial(false, unavailable.getMessage());
            }
            try {
                // A failed execution closes its DuckDB JDBC statement.
                execute(connection, "INSTALL spatial FROM 'https://extensions.duckdb.org'");
                execute(connection, "LOAD spatial");
                return new Spatial(true, null);
            } catch (SQLException installFailure) {
                return new Spatial(false, installFailure.getMessage());
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void closeFailedOpen(Connection connection, Throwable failure) {
        try {
            connection.close();
        } catch (SQLException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    /** Returns whether this store can use DuckDB Spatial functions. */
    public boolean spatialAvailable() {
        return spatial.available();
    }

    /** Returns the engine diagnostic when DuckDB Spatial is unavailable. */
    public Optional<String> spatialDiagnostic() {
        return Optional.ofNullable(spatial.diagnostic());
    }

    Connection duplicateForQuery() throws SQLException {
        requireOpen();
        if (!connection.isReadOnly()) {
            throw new SQLException("A query requires a read-only explorer store");
        }
        return connection.unwrap(DuckDBConnection.class).duplicate();
    }

    TransferPlan queryPlan() {
        return plan;
    }

    private void prepareProjections() throws SQLException {
        connection.setAutoCommit(false);
        try {
            StoreProjection.createSchema(connection);
            if (header(connection).isPresent()) {
                if (!StoreProjection.prepared(connection)) {
                    backfillProjections();
                } else if (spatial.available()) {
                    execute(connection, "CREATE INDEX IF NOT EXISTS geometry_rtree ON geometry USING RTREE (geom)");
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException | Error failure) {
            rollback(connection, failure);
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void backfillProjections() throws SQLException {
        var cancellation = CancellationToken.none();
        try (var statement = connection.createStatement()) {
            statement.execute("DELETE FROM reference");
        }
        try (var projection = new StoreProjection(connection, plan, codec, cancellation);
             var query = connection.prepareStatement("""
                     SELECT o.position, o.basket_position, o.type_name, o.fields, b.metadata
                     FROM object o JOIN basket b ON b.position = o.basket_position
                     WHERE o.position > ? ORDER BY o.position LIMIT 64
                     """)) {
            long position = 0;
            long basket = 0;
            boolean finished = false;
            while (!finished) {
                query.setLong(1, position);
                long pageStart = position;
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        long basketPosition = rows.getLong("basket_position");
                        if (basket != basketPosition) {
                            projection.startBasket(codec.basket(rows.getString("metadata")));
                            basket = basketPosition;
                        }
                        position = rows.getLong("position");
                        projection.project(position, codec.structure(rows.getString("type_name"), rows.getString("fields")));
                    }
                }
                projection.flush();
                finished = position == pageStart;
            }
        }
        resolveProjections(connection, cancellation);
    }

    /**
     * Imports one complete FULL transfer from a fresh reader bound to this plan.
     * The caller owns the reader and supplies nonnull arguments. Reader and cancellation
     * failures propagate after rollback. A filled store rejects another import.
     */
    public void importTransfer(TransferReader reader, CancellationToken cancellation) throws SQLException {
        if (reader.transferPlan() != plan) {
            throw new IllegalArgumentException("The transfer reader must use the store's transfer plan");
        }
        cancellation.throwIfCancellationRequested();
        requireOpen();
        if (connection.isReadOnly()) {
            throw new SQLException("Cannot import into a read-only explorer store");
        }
        // Own this transaction separately so a failed import never affects later reads.
        try (var transaction = DriverManager.getConnection(url)) {
            transaction.setAutoCommit(false);
            try {
                if (header(transaction).isPresent()) {
                    throw new IllegalStateException("The store already contains a transfer");
                }
                ingest(transaction, reader, cancellation);
                resolveProjections(transaction, cancellation);
                cancellation.throwIfCancellationRequested();
                transaction.commit();
            } catch (SQLException | RuntimeException | Error failure) {
                rollback(transaction, failure);
                throw failure;
            }
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private void resolveProjections(Connection transaction, CancellationToken cancellation) throws SQLException {
        try (var statement = transaction.createStatement()) {
            statement.executeUpdate(RESOLVE_REFERENCES);
        }
        StoreProjection.finish(transaction, spatial.available(), cancellation);
    }

    private void ingest(Connection transaction, TransferReader reader, CancellationToken cancellation) throws SQLException {
        var duckdb = transaction.unwrap(DuckDBConnection.class);
        try (var transfer = transaction.prepareStatement("INSERT INTO transfer VALUES (true, ?)");
             var basket = transaction.prepareStatement("INSERT INTO basket VALUES (?, ?, ?)");
             var object = duckdb.createAppender("object");
             var projection = new StoreProjection(transaction, plan, codec, cancellation)) {
            long basketPosition = 0;
            long objectPosition = 0;
            boolean started = false;
            // TransferReader checks the sequence and only reports EOF after EndTransfer.
            for (var next = reader.readNext(cancellation); next.isPresent(); next = reader.readNext(cancellation)) {
                var event = next.orElseThrow();
                if (!started && !(event instanceof StartTransfer)) {
                    throw new IllegalArgumentException("Import requires a reader positioned before StartTransfer");
                }
                switch (event) {
                    case StartTransfer start -> {
                        if (!reader.stableIdentityNamespaces().isEmpty()) {
                            throw new IllegalArgumentException("The store profile does not support stable identity aliases");
                        }
                        transfer.setString(1, codec.header(start).toString());
                        transfer.executeUpdate();
                        started = true;
                    }
                    case StartBasket start -> {
                        if (start.kind() != BasketKind.FULL || start.startState().isPresent() || start.endState().isPresent()) {
                            throw new IllegalArgumentException("The store only supports FULL baskets without incremental state");
                        }
                        basket.setLong(1, ++basketPosition);
                        basket.setString(2, start.id().value());
                        basket.setString(3, codec.basket(start).toString());
                        basket.executeUpdate();
                        projection.startBasket(start);
                    }
                    case ObjectEvent occurrence -> {
                        insertObject(object, ++objectPosition, basketPosition, occurrence);
                        projection.project(objectPosition, occurrence.object().data());
                        if (objectPosition % 256 == 0) {
                            object.flush();
                            projection.flush();
                        }
                    }
                    case EndBasket ignored -> {}
                    case EndTransfer ignored -> {}
                }
            }
            if (!started) {
                throw new IllegalArgumentException("Import requires a reader positioned before StartTransfer");
            }
            // DuckDB JDBC 2.0.1.0 suppresses flush failures in Appender.close().
            object.flush();
            projection.flush();
        }
    }

    private void insertObject(DuckDBAppender object, long position, long basketPosition, ObjectEvent occurrence)
            throws SQLException {
        if (occurrence.operation().isPresent()) {
            throw new IllegalArgumentException("The store does not support incremental object operations");
        }
        var value = occurrence.object();
        var identity = value.identity();
        var fields = codec.fields(value.data());
        object.beginRow()
                .append(position)
                .append(basketPosition)
                .append(codec.objectName(value.type()))
                .append(identity.tid().map(ObjectKey::value).orElse(null))
                .append(identity.oid().map(ObjectKey::value).orElse(null))
                .append(codec.identity(identity).toString())
                .append(occurrence.consistency().name())
                .append(fields.toString())
                .endRow();

    }

    /** Returns whether the file contains a committed transfer, including one with no baskets. */
    public boolean hasTransfer() throws SQLException {
        return header(connection).isPresent();
    }

    /** Returns all occurrences of the exact declared type in transfer order. */
    public List<StoredObject> objects(QualifiedName type) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM object WHERE type_name = ? ORDER BY position")) {
            query.setString(1, type.toString());
            return readObjects(query);
        }
    }

    /**
     * Returns an immutable list of up to {@code limit} occurrences of the nonnull exact
     * declared type in transfer order. A zero limit returns an empty list.
     * @throws IllegalArgumentException if the limit is negative
     * @throws SQLException if the query fails
     */
    public List<StoredObject> objects(QualifiedName type, int limit) throws SQLException {
        if (limit < 0) {
            throw new IllegalArgumentException("The object limit must be nonnegative");
        }
        try (var query = connection.prepareStatement(
                "SELECT * FROM object WHERE type_name = ? ORDER BY position LIMIT ?")) {
            query.setString(1, type.toString());
            query.setInt(2, limit);
            return readObjects(query);
        }
    }

    /**
     * Reads the occurrence at a database-local position, or returns empty if it is absent.
     * @throws SQLException if the query fails
     */
    public Optional<StoredObject> objectAt(long position) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM object WHERE position = ?")) {
            query.setLong(1, position);
            try (var rows = query.executeQuery()) {
                return rows.next() ? Optional.of(storedObject(rows)) : Optional.empty();
            }
        }
    }

    /**
     * Returns the source's indexed references in stored order, including duplicates and
     * unresolved references, without loading the target objects. The list is immutable.
     * @throws SQLException if the query fails
     */
    public List<ReferencePosition> referencePositions(long sourcePosition) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT value, target_position FROM reference
                WHERE source_position = ? ORDER BY position
                """)) {
            query.setLong(1, sourcePosition);
            var result = new ArrayList<ReferencePosition>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    long target = rows.getLong("target_position");
                    var position = rows.wasNull() ? OptionalLong.empty() : OptionalLong.of(target);
                    result.add(new ReferencePosition(StoreCodec.reference(rows.getString("value")), position));
                }
            }
            return List.copyOf(result);
        }
    }

    /**
     * Returns up to the given number of distinct occurrences with association roles
     * referring to the target. The list is immutable and in transfer order. Positions
     * are local to this file. A zero limit returns an empty list.
     * @throws IllegalArgumentException if the limit is negative
     * @throws SQLException if the query fails
     */
    public List<StoredObject> incomingRoleSources(long targetPosition, int limit) throws SQLException {
        if (limit < 0) {
            throw new IllegalArgumentException("The source limit must be nonnegative");
        }
        if (roleNames.isEmpty() || limit == 0) {
            return List.of();
        }
        var parameters = String.join(", ", Collections.nCopies(roleNames.size(), "?"));
        try (var query = connection.prepareStatement("""
                SELECT * FROM object WHERE position IN (
                    SELECT source_position FROM reference WHERE target_position = ? AND slot IN (%s)
                ) ORDER BY position LIMIT ?
                """.formatted(parameters))) {
            query.setLong(1, targetPosition);
            for (int index = 0; index < roleNames.size(); index++) {
                query.setString(index + 2, roleNames.get(index));
            }
            query.setInt(roleNames.size() + 2, limit);
            return readObjects(query);
        }
    }

    /**
     * Looks up a TID or OID in the supplied basket by identifier spelling.
     * Returns empty for an absent object and throws IllegalStateException for an ambiguous identity.
     */
    public Optional<StoredObject> find(BasketId basket, ObjectKey key) throws SQLException {
        String column = Objects.requireNonNull(key) instanceof TransferId ? "o.tid" : "o.oid";
        try (var query = connection.prepareStatement("""
                SELECT o.* FROM object o JOIN basket b ON b.position = o.basket_position
                WHERE b.bid = ? AND %s = ? LIMIT 2
                """.formatted(column))) {
            query.setString(1, basket.value());
            query.setString(2, key.value());
            var found = readObjects(query);
            if (found.size() > 1) {
                throw new IllegalStateException("Ambiguous object identity " + key.value() + " in basket " + basket.value());
            }
            return found.stream().findFirst();
        }
    }

    /**
     * Returns references in stored value order, including duplicates and unresolved references.
     * The position is local to this file. An unknown position returns an empty list.
     */
    public List<ReferenceLink> outgoing(long position) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT o.*, r.slot, r.value FROM reference r LEFT JOIN object o ON o.position = r.target_position
                WHERE r.source_position = ? ORDER BY r.position
                """)) {
            query.setLong(1, position);
            try (var rows = query.executeQuery()) {
                var result = new ArrayList<ReferenceLink>();
                while (rows.next()) {
                    var target = rows.getObject("position") == null ? Optional.<StoredObject>empty()
                            : Optional.of(storedObject(rows));
                    result.add(new ReferenceLink(codec.referenceSlot(rows.getString("slot")),
                            StoreCodec.reference(rows.getString("value")), target));
                }
                return List.copyOf(result);
            }
        }
    }

    /**
     * Returns distinct source occurrences referring to the target position, in transfer order.
     * The position is local to this file. An unknown position returns an empty list.
     */
    public List<StoredObject> incoming(long position) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT * FROM object WHERE position IN (
                    SELECT source_position FROM reference WHERE target_position = ?
                ) ORDER BY position
                """)) {
            query.setLong(1, position);
            return readObjects(query);
        }
    }

    /**
     * Exports a committed transfer through Xtf24Writer. The caller owns the output
     * stream. A writer or cancellation failure can leave partial output.
     * @throws IllegalStateException if the store is empty
     */
    public void exportTransfer(OutputStream output, CancellationToken cancellation) throws SQLException {
        cancellation.throwIfCancellationRequested();
        var start = codec.header(header(connection).orElseThrow(() -> new IllegalStateException("The store is empty")));
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT b.position AS basket_order, b.metadata, o.*
                     FROM basket b LEFT JOIN object o ON b.position = o.basket_position
                     ORDER BY b.position, o.position
                     """);
             var writer = new Xtf24Writer(plan, output)) {
            writer.write(start, cancellation);
            long basketPosition = 0;
            BasketId active = null;
            while (rows.next()) {
                long nextBasket = rows.getLong("basket_order");
                if (basketPosition != nextBasket) {
                    if (active != null) {
                        writer.write(new EndBasket(active), cancellation);
                    }
                    var basket = codec.basket(rows.getString("metadata"));
                    writer.write(basket, cancellation);
                    active = basket.id();
                    basketPosition = nextBasket;
                }
                if (rows.getObject("position") != null) {
                    writer.write(new ObjectEvent(storedObject(rows).object(), Optional.empty(),
                            Consistency.valueOf(rows.getString("consistency"))), cancellation);
                }
            }
            if (active != null) {
                writer.write(new EndBasket(active), cancellation);
            }
            writer.write(new EndTransfer(), cancellation);
        }
    }

    private static Optional<String> header(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT metadata FROM transfer")) {
            return rows.next() ? Optional.of(rows.getString(1)) : Optional.empty();
        }
    }

    private List<StoredObject> readObjects(PreparedStatement query) throws SQLException {
        try (var rows = query.executeQuery()) {
            var result = new ArrayList<StoredObject>();
            while (rows.next()) {
                result.add(storedObject(rows));
            }
            return List.copyOf(result);
        }
    }

    private StoredObject storedObject(ResultSet rows) throws SQLException {
        return new StoredObject(rows.getLong("position"), new TransferObject(codec.identity(rows.getString("identity")),
                codec.structure(rows.getString("type_name"), rows.getString("fields"))));
    }

    private void requireOpen() throws SQLException {
        if (connection.isClosed()) {
            throw new SQLException("The store is closed");
        }
    }

    /** Closes the store's connection. */
    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
