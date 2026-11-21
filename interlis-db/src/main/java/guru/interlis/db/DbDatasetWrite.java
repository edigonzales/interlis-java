package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolKind;

import java.sql.SQLException;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.COLUMN_PROP;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.PREFIX;
import static guru.interlis.db.Ili2dbLayout.T_BASKET;
import static guru.interlis.db.Ili2dbLayout.T_ID;

/** Operations on rows of existing datasets. All writes belong to the caller's transaction. */
final class DbDatasetWrite {
    private DbDatasetWrite() { }

    static void lock(Database db, DbMapping mapping) throws SQLException {
        if (db.gpkg()) return; // BEGIN IMMEDIATE already excludes other writers.
        var tables = new TreeSet<String>();
        tables.add(db.technicalTable(DATASET));
        tables.add(db.technicalTable(BASKET));
        for (var table : mapping.tables()) tables.add(db.table(table.name()));
        try (var statement = db.connection().createStatement()) {
            statement.execute("LOCK TABLE " + String.join(", ", tables) + " IN SHARE ROW EXCLUSIVE MODE NOWAIT");
        } catch (SQLException failure) {
            if ("55P03".equals(failure.getSQLState())) {
                throw new DbException("Another writer is using the dataset tables", failure);
            }
            throw failure;
        }
    }

    static void checkReplaceProfile(Database db, DbMapping mapping) throws SQLException {
        for (var table : mapping.tables()) {
            if (table.object().kind() != SymbolKind.CLASS || table.subtype() || table.hasDiscriminator()
                    || table.columns().stream().anyMatch(column -> column.kind().geometry()
                    || column.kind() == DbMapping.Kind.REFERENCE)) {
                throw new DbException("Unsupported mapping for replace at " + table.object().name().declared());
            }
        }
        checkUnmappedReferences(db, mapping);
    }

    private static void checkUnmappedReferences(Database db, DbMapping mapping) throws SQLException {
        var tables = mapping.tables().stream().map(DbMapping.Table::name).collect(Collectors.toSet());
        String sql = "SELECT tablename, columnname, setting FROM " + db.technicalTable(COLUMN_PROP) + " WHERE tag = ?";
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, PREFIX + "foreignKey");
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (tables.contains(rows.getString(3)) && !tables.contains(rows.getString(1))) {
                        throw new DbException("Unmapped incoming reference at " + rows.getString(1) + "." + rows.getString(2));
                    }
                }
            }
        }
    }

    static void clearDataset(Database db, DbMapping mapping, long dataset, CancellationToken token) throws SQLException {
        var topics = mapping.plan().topics().stream()
                .map(topic -> topic.name().declared().toString()).collect(Collectors.toSet());
        String baskets = db.technicalTable(BASKET);
        try (var statement = db.connection().prepareStatement("SELECT DISTINCT topic FROM " + baskets + " WHERE dataset = ?")) {
            statement.setLong(1, dataset);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (!topics.contains(rows.getString(1))) {
                        throw new DbException("Cannot replace a dataset with a basket outside the selected model: "
                                + rows.getString(1));
                    }
                }
            }
        }
        for (var table : mapping.tables().reversed()) {
            token.throwIfCancellationRequested();
            delete(db, "DELETE FROM " + db.table(table.name()) + " WHERE " + T_BASKET
                    + " IN (SELECT " + T_ID + " FROM " + baskets + " WHERE dataset = ?)", dataset);
        }
        token.throwIfCancellationRequested();
        delete(db, "DELETE FROM " + baskets + " WHERE dataset = ?", dataset);
    }

    private static void delete(Database db, String sql, long id) throws SQLException {
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
