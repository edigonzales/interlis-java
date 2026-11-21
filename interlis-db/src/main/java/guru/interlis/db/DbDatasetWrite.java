package guru.interlis.db;

import guru.interlis.db.DbMapping.Table;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolKind;
import guru.interlis.transfer.TopicDescriptor;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.COLUMN_PROP;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.PREFIX;
import static guru.interlis.db.Ili2dbLayout.T_BASKET;
import static guru.interlis.db.Ili2dbLayout.T_ID;
import static guru.interlis.db.Ili2dbLayout.T_ILI_TID;
import static guru.interlis.db.Ili2dbLayout.T_TYPE;

/** Operations on rows of existing datasets. All writes belong to the caller's transaction. */
final class DbDatasetWrite {
    private DbDatasetWrite() { }

    static void lock(Database db, DbMapping mapping) throws SQLException {
        if (db.gpkg()) return; // BEGIN IMMEDIATE already excludes other writers.
        var tables = new TreeSet<String>();
        tables.add(db.technicalTable(DATASET));
        tables.add(db.technicalTable(BASKET));
        for (var table : mapping.storageTables()) tables.add(db.table(table));
        try (var statement = db.connection().createStatement()) {
            statement.execute("LOCK TABLE " + String.join(", ", tables) + " IN SHARE ROW EXCLUSIVE MODE NOWAIT");
        } catch (SQLException failure) {
            if ("55P03".equals(failure.getSQLState())) {
                throw new DbException("Another writer is using the dataset tables", failure);
            }
            throw failure;
        }
    }

    static OptionalLong findBasket(Database db, long dataset, TopicDescriptor topic, String bid) throws SQLException {
        String baskets = db.technicalTable(BASKET);
        String sql = "SELECT " + T_ID + ", dataset, topic, domains FROM " + baskets + " WHERE " + T_ILI_TID + " = ?";
        String topicName = topic.name().declared().toString();
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, bid);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    long id = rows.getLong(1);
                    boolean belongs = rows.getLong(2) == dataset && topicName.equals(rows.getString(3));
                    String domains = rows.getString(4);
                    if (rows.next()) throw new DbException("Ambiguous stored BID " + bid);
                    if (!belongs) throw new DbException("BID " + bid + " belongs to another dataset or topic");
                    if (domains != null && !domains.isBlank()) {
                        throw new DbException("Unsupported domain assignments for stored basket " + bid);
                    }
                    return OptionalLong.of(id);
                }
            }
        }
        String missing = "SELECT 1 FROM " + baskets + " WHERE dataset = ? AND topic = ? AND ("
                + T_ILI_TID + " IS NULL OR " + T_ILI_TID + " = '') LIMIT 1";
        try (var statement = db.connection().prepareStatement(missing)) {
            statement.setLong(1, dataset);
            statement.setString(2, topicName);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) throw new DbException("Missing stored BID in the dataset for topic " + topicName);
            }
        }
        return OptionalLong.empty();
    }

    record ObjectKey(Table root, String tid) { }
    record ExistingObject(Table type, long basket, long id) { }

    static Map<ObjectKey, ExistingObject> readObjects(Database db, DbMapping mapping, TopicDescriptor topic, long basket,
                                                       CancellationToken token) throws SQLException {
        var objects = new HashMap<ObjectKey, ExistingObject>();
        for (var table : mapping.tables()) {
            if (!table.hasTidColumn() || table.object().kind() == SymbolKind.STRUCTURE
                    || table.object().topic().filter(topic.symbol()::equals).isEmpty()) continue;
            token.throwIfCancellationRequested();
            String discriminator = table.hasDiscriminator() ? ", " + T_TYPE : "";
            String sql = "SELECT " + T_ID + ", " + T_ILI_TID + discriminator + " FROM " + db.table(table.name())
                    + " WHERE " + T_BASKET + " = ?";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, basket);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        token.throwIfCancellationRequested();
                        long id = rows.getLong(1);
                        String raw = rows.getString(2);
                        if (raw == null || raw.isEmpty()) {
                            throw new DbException("Missing stored TID at " + table.name() + " T_Id " + id);
                        }
                        String tid = DbTransfer.storedTid(table, raw);
                        var concrete = table.hasDiscriminator() ? mapping.concreteType(table, rows.getString(3), id) : table;
                        if (objects.putIfAbsent(new ObjectKey(table, tid), new ExistingObject(concrete, basket, id)) != null) {
                            throw new DbException("Ambiguous stored object TID " + tid + " at " + table.name());
                        }
                    }
                }
            }
        }
        return objects;
    }

    static void checkStableObjectBasket(Database db, Table table, long dataset, long basket, String tid) throws SQLException {
        String sql = """
                SELECT 1 FROM %s obj JOIN %s b ON b.t_id = obj.t_basket
                WHERE b.dataset = ? AND b.t_id <> ? AND %s = ? LIMIT 1
                """.formatted(db.table(table.name()), db.technicalTable(BASKET), DbTransfer.tidExpression(table, "obj"));
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setLong(1, dataset);
            statement.setLong(2, basket);
            statement.setString(3, tid);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) throw new DbException("Object OID " + tid + " is already stored in another basket");
            }
        }
    }

    static void clearBasketPayload(Database db, DbMapping mapping, long basket,
                                   CancellationToken token) throws SQLException {
        for (var table : mapping.tables().reversed()) {
            boolean anonymousLink = table.object().kind() == SymbolKind.ASSOCIATION
                    && !mapping.rootTable(table.object().symbol()).hasTidColumn();
            if (table.object().kind() != SymbolKind.STRUCTURE && !anonymousLink) continue;
            token.throwIfCancellationRequested();
            delete(db, "DELETE FROM " + db.table(table.name()) + " WHERE " + T_BASKET + " = ?", basket);
        }
    }

    static void deleteRemovedObjects(Database db, DbMapping mapping, List<ExistingObject> removed,
                                     CancellationToken token) throws SQLException {
        if (removed.isEmpty()) return;
        checkIncomingMappings(db, mapping);
        var byRoot = new HashMap<Table, Set<Long>>();
        for (var object : removed) {
            var root = mapping.rootTable(object.type().object().symbol());
            byRoot.computeIfAbsent(root, ignored -> new HashSet<>()).add(object.id());
        }
        for (var table : mapping.tables()) {
            var sourceRoot = mapping.rootTable(table.object().symbol());
            var removedSources = byRoot.getOrDefault(sourceRoot, Set.of());
            for (var column : table.columns()) {
                if (column.kind() != DbMapping.Kind.REFERENCE) continue;
                var targetRoot = mapping.rootTable(column.target().orElseThrow());
                var targets = byRoot.get(targetRoot);
                if (targets != null) checkUpdateReferences(db, table, column, List.copyOf(targets), removedSources, token);
            }
        }
        for (var removedObject : removed) {
            token.throwIfCancellationRequested();
            deleteObject(db, mapping, removedObject);
        }
    }

    private static void checkUpdateReferences(Database db, Table table, DbMapping.Column column, List<Long> targetIds,
                                              Set<Long> removedSources, CancellationToken token) throws SQLException {
        // Bound parameter counts on both databases when many objects disappear from a basket.
        final int batchSize = 256;
        String field = Database.quote(column.name());
        for (int offset = 0; offset < targetIds.size(); offset += batchSize) {
            token.throwIfCancellationRequested();
            int batch = Math.min(batchSize, targetIds.size() - offset);
            String sql = "SELECT " + T_ID + ", " + field + " FROM " + db.table(table.name()) + " WHERE " + field
                    + " IN (" + String.join(", ", Collections.nCopies(batch, "?")) + ")";
            try (var statement = db.connection().prepareStatement(sql)) {
                for (int i = 0; i < batch; i++) statement.setLong(i + 1, targetIds.get(offset + i));
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        token.throwIfCancellationRequested();
                        if (!removedSources.contains(rows.getLong(1))) {
                            throw referencedTarget("delete", column, rows.getLong(2));
                        }
                    }
                }
            }
        }
    }

    private static void deleteObject(Database db, DbMapping mapping, ExistingObject object) throws SQLException {
        for (var table : mapping.tableChain(object.type().object().symbol()).reversed()) {
            for (var secondary : table.secondaryGeometries()) deleteObjectRow(db, secondary.table(), object);
            deleteObjectRow(db, table.name(), object);
        }
    }

    private static void deleteObjectRow(Database db, String table, ExistingObject object) throws SQLException {
        try (var statement = db.connection().prepareStatement("DELETE FROM " + db.table(table)
                + " WHERE " + T_ID + " = ? AND " + T_BASKET + " = ?")) {
            statement.setLong(1, object.id());
            statement.setLong(2, object.basket());
            if (statement.executeUpdate() != 1) {
                throw new DbException("Missing stored row for T_Id " + object.id() + " in " + table);
            }
        }
    }

    private static void checkIncomingMappings(Database db, DbMapping mapping) throws SQLException {
        var tables = mapping.storageTables().stream().map(table -> catalogName(db, table)).collect(Collectors.toSet());
        String sql = "SELECT tablename, columnname, setting FROM " + db.technicalTable(COLUMN_PROP) + " WHERE tag = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, PREFIX + "foreignKey");
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (tables.contains(catalogName(db, rows.getString(3)))
                            && !tables.contains(catalogName(db, rows.getString(1)))) {
                        throw new DbException("Unmapped incoming reference at " + rows.getString(1) + "." + rows.getString(2));
                    }
                }
            }
        }
    }

    private static String catalogName(Database db, String name) {
        return db.gpkg() ? name.toLowerCase(Locale.ROOT) : name;
    }

    static Optional<Long> findDataset(Database db, String dataset) throws SQLException {
        String sql = "SELECT " + T_ID + " FROM " + db.technicalTable(DATASET)
                + " WHERE datasetname = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, dataset);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                long id = rows.getLong(1);
                if (rows.next()) {
                    throw new DbException("Dataset name is ambiguous: " + dataset);
                }
                return Optional.of(id);
            }
        }
    }

    static void deleteDataset(Database db, DbMapping mapping, String dataset, CancellationToken token)
            throws SQLException {
        token.throwIfCancellationRequested();
        lock(db, mapping);
        long id = findDataset(db, dataset).orElseThrow(() -> new DbException("Dataset does not exist: " + dataset));
        clearDataset(db, mapping, id, "delete", token);
        token.throwIfCancellationRequested();
        delete(db, "DELETE FROM " + db.technicalTable(DATASET) + " WHERE " + T_ID + " = ?", id);
    }

    static void clearDataset(Database db, DbMapping mapping, long dataset, String operation, CancellationToken token) throws SQLException {
        var topics = mapping.plan().topics().stream()
                .map(topic -> topic.name().declared().toString()).collect(Collectors.toSet());
        String baskets = db.technicalTable(BASKET);
        try (var statement = db.connection().prepareStatement("SELECT DISTINCT topic FROM " + baskets + " WHERE dataset = ?")) {
            statement.setLong(1, dataset);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (!topics.contains(rows.getString(1))) {
                        throw new DbException("Cannot " + operation + " a dataset with a basket outside the selected model: "
                                + rows.getString(1));
                    }
                }
            }
        }
        checkIncomingMappings(db, mapping);
        checkDatasetReferences(db, mapping, dataset, operation, token);
        checkDatasetGeometryFragments(db, mapping, dataset, token);
        for (var table : mapping.storageTables().reversed()) {
            token.throwIfCancellationRequested();
            delete(db, "DELETE FROM " + db.table(table) + " WHERE " + T_BASKET
                    + " IN (SELECT " + T_ID + " FROM " + baskets + " WHERE dataset = ?)", dataset);
        }
        token.throwIfCancellationRequested();
        delete(db, "DELETE FROM " + baskets + " WHERE dataset = ?", dataset);
    }

    private static void checkDatasetGeometryFragments(Database db, DbMapping mapping, long dataset,
                                                       CancellationToken token) throws SQLException {
        String datasetBaskets = "(SELECT " + T_ID + " FROM " + db.technicalTable(BASKET) + " WHERE dataset = ?)";
        for (var table : mapping.tables()) {
            for (var geometry : table.secondaryGeometries()) {
                token.throwIfCancellationRequested();
                // Check both sides of the basket boundary. An orphan left by ili2db UPDATE has no owner
                // row and can be removed by its own basket.
                String sql = """
                        SELECT obj.t_id FROM %s obj LEFT JOIN %s geom ON obj.t_id = geom.t_id
                        WHERE (obj.t_basket IN %s AND (geom.t_basket IS NULL OR geom.t_basket <> obj.t_basket))
                           OR (geom.t_basket IN %s AND geom.t_basket <> obj.t_basket)
                        LIMIT 1
                        """.formatted(db.table(table.name()), db.table(geometry.table()), datasetBaskets, datasetBaskets);
                try (var statement = db.connection().prepareStatement(sql)) {
                    statement.setLong(1, dataset);
                    statement.setLong(2, dataset);
                    try (var rows = statement.executeQuery()) {
                        if (rows.next()) {
                            throw new DbException("Missing or basket-mismatched secondary geometry row for "
                                    + geometry.column().slot().name().declared() + " at T_Id " + rows.getLong(1));
                        }
                    }
                }
            }
        }
    }

    private static void checkDatasetReferences(Database db, DbMapping mapping, long dataset, String operation,
                                               CancellationToken token) throws SQLException {
        String baskets = db.technicalTable(BASKET);
        for (var table : mapping.tables()) {
            for (var column : table.columns()) {
                if (column.kind() != DbMapping.Kind.REFERENCE) continue;
                token.throwIfCancellationRequested();
                var root = mapping.rootTable(column.target().orElseThrow());
                String sql = """
                        SELECT ref.t_id FROM %s src JOIN %s ref ON ref.t_id = src.%s
                        JOIN %s dst ON dst.t_id = ref.t_basket
                        WHERE dst.dataset = ? AND NOT EXISTS
                           (SELECT 1 FROM %s own WHERE own.t_id = src.t_basket AND own.dataset = ?)
                        LIMIT 1
                        """.formatted(db.table(table.name()), db.table(root.name()), Database.quote(column.name()), baskets, baskets);
                try (var statement = db.connection().prepareStatement(sql)) {
                    statement.setLong(1, dataset);
                    statement.setLong(2, dataset);
                    try (var rows = statement.executeQuery()) {
                        if (rows.next()) throw referencedTarget(operation, column, rows.getLong(1));
                    }
                }
            }
        }
    }

    private static DbException referencedTarget(String operation, DbMapping.Column column, long id) {
        return new DbException("Cannot " + operation + " referenced object T_Id " + id + " at "
                + column.slot().name().declared());
    }

    private static void delete(Database db, String sql, long id) throws SQLException {
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
