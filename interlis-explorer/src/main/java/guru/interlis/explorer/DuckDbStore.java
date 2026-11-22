package guru.interlis.explorer;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    /** A stored occurrence and its database-local position, including occurrences without a TID or OID. */
    public record StoredObject(long position, TransferObject object) {}

    /** A reference at its declaring slot. The target is empty when there is no unique local match. */
    public record ReferenceLink(QualifiedName slot, Reference reference, Optional<StoredObject> target) {}

    private DuckDbStore(String url, Connection connection, TransferPlan plan) {
        this.url = url;
        this.connection = connection;
        this.plan = plan;
        codec = new StoreCodec(plan);
    }

    /** Opens or creates a local DuckDB file. Both arguments are nonnull. */
    public static DuckDbStore open(Path file, TransferPlan plan) throws SQLException {
        Objects.requireNonNull(plan, "plan");
        String url = "jdbc:duckdb:" + file.toAbsolutePath();
        var connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
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
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                connection.close();
            } catch (SQLException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        return new DuckDbStore(url, connection, plan);
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
        // Own this transaction separately so a failed import never affects later reads.
        try (var transaction = DriverManager.getConnection(url)) {
            transaction.setAutoCommit(false);
            try {
                if (header(transaction).isPresent()) {
                    throw new IllegalStateException("The store already contains a transfer");
                }
                ingest(transaction, reader, cancellation);
                try (var statement = transaction.createStatement()) {
                    statement.executeUpdate(RESOLVE_REFERENCES);
                }
                cancellation.throwIfCancellationRequested();
                transaction.commit();
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    transaction.rollback();
                } catch (SQLException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
    }

    private void ingest(Connection transaction, TransferReader reader, CancellationToken cancellation) throws SQLException {
        var duckdb = transaction.unwrap(DuckDBConnection.class);
        try (var transfer = transaction.prepareStatement("INSERT INTO transfer VALUES (true, ?)");
             var basket = transaction.prepareStatement("INSERT INTO basket VALUES (?, ?, ?)");
             var object = duckdb.createAppender("object");
             var reference = duckdb.createAppender("reference")) {
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
                    }
                    case ObjectEvent occurrence -> insertObject(object, reference, ++objectPosition, basketPosition,
                            occurrence, cancellation);
                    case EndBasket ignored -> {}
                    case EndTransfer ignored -> {}
                }
            }
            if (!started) {
                throw new IllegalArgumentException("Import requires a reader positioned before StartTransfer");
            }
            // DuckDB JDBC 2.0.1.0 suppresses flush failures in Appender.close().
            object.flush();
            reference.flush();
        }
    }

    private void insertObject(DuckDBAppender object, DuckDBAppender reference, long position, long basketPosition,
                              ObjectEvent occurrence, CancellationToken cancellation) throws SQLException {
        if (occurrence.operation().isPresent()) {
            throw new IllegalArgumentException("The store does not support incremental object operations");
        }
        var value = occurrence.object();
        var identity = value.identity();
        var links = new ArrayList<Map.Entry<String, Reference>>();
        var fields = codec.fields(value.data(), (slot, target) -> links.add(Map.entry(slot, target)));
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

        int index = 0;
        for (var link : links) {
            cancellation.throwIfCancellationRequested();
            var target = link.getValue();
            reference.beginRow()
                    .append(position)
                    .append(++index)
                    .append(link.getKey())
                    .append(StoreCodec.reference(target).toString())
                    .append(target.target().value())
                    .append(!(target.target() instanceof TransferId))
                    .append(target.targetBasket().map(BasketId::value).orElse(null))
                    .append(target.external())
                    .appendNull() // target_position is set by reference resolution.
                    .endRow();
        }
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
