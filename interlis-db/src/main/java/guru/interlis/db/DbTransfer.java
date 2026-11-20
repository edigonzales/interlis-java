package guru.interlis.db;

import guru.interlis.db.DbMapping.Column;
import guru.interlis.db.DbMapping.Table;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.BasketKind;
import guru.interlis.transfer.TransferEvent.Consistency;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.IdOrigin;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.BooleanValue;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.NumberValue;
import guru.interlis.transfer.TransferValue.Reference;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import guru.interlis.transfer.TransferValue.Undefined;
import guru.interlis.transfer.TransferWriter;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.db.Database.quote;
import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.T_BASKET;
import static guru.interlis.db.Ili2dbLayout.T_ID;
import static guru.interlis.db.Ili2dbLayout.T_ILI_TID;

final class DbTransfer {
    private DbTransfer() { }

    static void exportData(Database db, DbMapping mapping, String dataset,
                           TransferWriter writer, CancellationToken token) throws SQLException {
        token.throwIfCancellationRequested();
        long datasetId = findDataset(db, dataset)
                .orElseThrow(() -> new DbException("Dataset does not exist: " + dataset));
        var plan = mapping.plan();
        var modelRef = plan.rootModels().getFirst();
        var model = plan.model(modelRef).orElseThrow();
        var symbol = (ModelSymbol) plan.symbol(modelRef).known().orElseThrow();
        boolean ili23 = mapping.version() == LanguageVersion.ILI23;
        var declaration = new TransferEvent.ModelDeclaration(modelRef, mapping.version(),
                ili23 ? Optional.of(model.modelVersion()) : Optional.empty(),
                ili23 ? Optional.of(symbol.issuer()) : Optional.empty());
        var topics = plan.topics().stream().filter(topic -> topic.model().equals(modelRef))
                .collect(Collectors.toMap(topic -> topic.name().declared().toString(), Function.identity()));
        String sql = "SELECT " + T_ID + ", topic, " + T_ILI_TID + ", domains FROM "
                + db.technicalTable(BASKET) + " WHERE dataset = ? ORDER BY " + T_ID;
        try (var statement = db.connection().prepareStatement(sql)) {
            fetchInPages(db, statement);
            statement.setLong(1, datasetId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new DbException("Dataset has no baskets: " + dataset);
                }
                writer.write(new TransferEvent.StartTransfer(Optional.empty(), Optional.of("interlis-db"),
                        Optional.empty(), List.of(declaration)), token);
                do {
                    token.throwIfCancellationRequested();
                    var topicName = rows.getString(2);
                    var topic = topics.get(topicName);
                    if (topic == null) {
                        throw new DbException("Basket topic is outside the selected model: " + topicName);
                    }
                    String domains = rows.getString(4);
                    if (domains != null && !domains.isBlank()) {
                        throw new DbException("Basket domain assignments are outside the supported profile: " + topicName);
                    }
                    var bid = new BasketId(identifier(rows.getString(3), "BID", topicName), IdOrigin.SOURCE);
                    writeBasket(db, mapping, topic, rows.getLong(1), bid, writer, token);
                } while (rows.next());
            }
        }
        writer.write(new TransferEvent.EndTransfer(), token);
    }

    private static void writeBasket(Database db, DbMapping mapping, TopicDescriptor topic, long basketId,
                                    BasketId bid, TransferWriter writer, CancellationToken token) throws SQLException {
        writer.write(new TransferEvent.StartBasket(bid, topic.symbol(), List.of(), BasketKind.FULL,
                Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of()), token);
        for (var table : mapping.tables()) {
            if (table.object().topic().orElseThrow().equals(topic.symbol())) {
                writeObjects(db, mapping, table, basketId, bid, writer, token);
            }
        }
        writer.write(new TransferEvent.EndBasket(bid), token);
    }

    private static void writeObjects(Database db, DbMapping mapping, Table table, long basketId, BasketId bid,
                                     TransferWriter writer, CancellationToken token) throws SQLException {
        var projections = new StringJoiner(", ").add("obj." + T_ILI_TID);
        var from = new StringBuilder(db.table(table.name())).append(" obj");
        String basketField = "obj." + T_BASKET;
        int refIndex = 0;
        for (var column : table.columns()) {
            String field = "obj." + quote(column.name());
            switch (column.kind()) {
                case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE ->
                        projections.add(db.geometryProjection(field, column.kind()));
                case REFERENCE -> {
                    String alias = "ref" + refIndex++;
                    var target = mapping.table(column.target().orElseThrow());
                    from.append(" LEFT JOIN ").append(db.table(target.name())).append(' ').append(alias)
                            .append(" ON ").append(alias).append('.').append(T_ID).append(" = ").append(field)
                            .append(" AND ").append(alias).append('.').append(T_BASKET).append(" = ").append(basketField);
                    projections.add(field).add(alias + "." + T_ILI_TID);
                }
                default -> projections.add(field);
            }
        }
        String sql = "SELECT " + projections + " FROM " + from
                + " WHERE " + basketField + " = ? ORDER BY obj." + T_ID;
        try (var statement = db.connection().prepareStatement(sql)) {
            fetchInPages(db, statement);
            statement.setLong(1, basketId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    String tid = identifier(rows.getString(1), "TID", table.name());
                    var values = new LinkedHashMap<SymbolRef, TransferValue>();
                    int index = 2;
                    for (var column : table.columns()) {
                        var value = readValue(db, rows, index, column);
                        if (value != null) {
                            values.put(column.slot().symbol(), value);
                        }
                        index += column.kind() == DbMapping.Kind.REFERENCE ? 2 : 1;
                    }
                    var identity = new TransferIdentity(bid, Optional.empty(),
                            Optional.of(new TransferId(tid, IdOrigin.SOURCE)), Optional.empty(), Optional.empty());
                    var object = new TransferObject(identity, new Structured(table.object().symbol(), values));
                    writer.write(new TransferEvent.ObjectEvent(object, Optional.empty(), Consistency.COMPLETE), token);
                }
            }
        }
    }

    private static TransferValue readValue(Database db, ResultSet rows, int index, Column column) throws SQLException {
        return switch (column.kind()) {
            case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE -> db.readGeometry(rows, index, column);
            case BOOLEAN -> readBoolean(db, rows, index, column);
            case INTEGER -> readInteger(rows.getString(index), column);
            case ENUMERATION -> readEnumeration(rows.getString(index), column);
            case TEXT -> {
                String value = rows.getString(index);
                yield value == null ? null : new Text(value);
            }
            case REFERENCE -> {
                if (rows.getObject(index) == null) {
                    yield null;
                }
                String tid = identifier(rows.getString(index + 1), "same-basket reference target TID",
                        column.slot().name().declared().toString());
                yield new Reference(new TransferId(tid, IdOrigin.SOURCE), Optional.empty(), false, Optional.empty());
            }
        };
    }

    private static BooleanValue readBoolean(Database db, ResultSet rows, int index, Column column) throws SQLException {
        if (!db.gpkg()) {
            boolean value = rows.getBoolean(index);
            return rows.wasNull() ? null : new BooleanValue(value);
        }
        var value = rows.getObject(index);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer number && (number == 0 || number == 1)) {
            return new BooleanValue(number != 0);
        }
        throw new DbException("Invalid SQLite boolean in " + column.slot().name().declared());
    }

    private static NumberValue readInteger(String value, Column column) {
        if (value == null) {
            return null;
        }
        try {
            int integer = new BigDecimal(value).intValueExact();
            return new NumberValue(Integer.toString(integer), BigDecimal.valueOf(integer));
        } catch (NumberFormatException | ArithmeticException e) {
            throw new DbException("Invalid 32-bit integer in " + column.slot().name().declared(), e);
        }
    }

    private static Enumeration readEnumeration(String value, Column column) {
        if (value == null) {
            return null;
        }
        try {
            return new Enumeration(List.of(value.split("\\.", -1)));
        } catch (IllegalArgumentException e) {
            throw new DbException("Invalid enumeration encoding in " + column.slot().name().declared(), e);
        }
    }

    static void importData(Database db, DbMapping mapping, String dataset,
                           TransferReader reader, CancellationToken token) throws SQLException {
        token.throwIfCancellationRequested();
        if (findDataset(db, dataset).isPresent()) {
            throw new DbException("Dataset already exists: " + dataset);
        }
        try (var importer = new Import(db, mapping, token)) {
            long datasetId = 0;
            boolean sawBasket = false;
            for (var next = reader.readNext(token); next.isPresent(); next = reader.readNext(token)) {
                switch (next.orElseThrow()) {
                    case TransferEvent.StartTransfer start -> {
                        var model = mapping.plan().rootModels().getFirst();
                        if (start.models().size() != 1 || !start.models().getFirst().model().equals(model)
                                || !reader.stableIdentityNamespaces().isEmpty()) {
                            throw new DbException("Expected the selected model and transfer-scoped identities in XTF");
                        }
                        datasetId = importer.insertDataset(dataset);
                    }
                    case TransferEvent.StartBasket start -> {
                        importer.startBasket(datasetId, start);
                        sawBasket = true;
                    }
                    case TransferEvent.ObjectEvent event -> importer.insertObject(event);
                    case TransferEvent.EndBasket ignored -> importer.finishBasket();
                    case TransferEvent.EndTransfer ignored -> { }
                }
            }
            token.throwIfCancellationRequested();
            if (!sawBasket) {
                throw new DbException("A dataset must contain at least one basket");
            }
        }
    }

    private static Optional<Long> findDataset(Database db, String dataset) throws SQLException {
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

    private static String identifier(String value, String kind, String context) {
        if (value == null || value.isEmpty()) {
            throw new DbException("Missing " + kind + " in " + context);
        }
        return value;
    }

    private static void fetchInPages(Database db, PreparedStatement statement) throws SQLException {
        if (!db.gpkg()) {
            statement.setFetchSize(256);
        }
    }

    private static final class Import implements AutoCloseable {
        private final Database db;
        private final DbMapping mapping;
        private final CancellationToken token;
        private final Map<String, PreparedStatement> inserts = new HashMap<>();
        private final Map<Column, PreparedStatement> updates = new HashMap<>();
        private final Set<SymbolRef> referenceTargets;
        private final Map<String, StoredObject> objects = new HashMap<>();
        private final List<Pending> pending = new ArrayList<>();
        private long basketId;

        Import(Database db, DbMapping mapping, CancellationToken token) {
            this.db = db;
            this.mapping = mapping;
            this.token = token;
            referenceTargets = mapping.tables().stream().flatMap(table -> table.columns().stream())
                    .flatMap(column -> column.target().stream()).collect(Collectors.toSet());
        }

        long insertDataset(String dataset) throws SQLException {
            long id = db.nextId();
            String sql = "INSERT INTO " + db.technicalTable(DATASET)
                    + " (" + T_ID + ", datasetname) VALUES (?, ?)";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.setString(2, dataset);
                statement.executeUpdate();
            }
            return id;
        }

        void startBasket(long datasetId, TransferEvent.StartBasket start) throws SQLException {
            var topic = mapping.plan().topic(start.topic()).orElseThrow();
            if (!topic.model().equals(mapping.plan().rootModels().getFirst())
                    || start.kind() != BasketKind.FULL || start.consistency() != Consistency.COMPLETE
                    || !start.additionalTopics().isEmpty() || !start.domainBindings().isEmpty()
                    || start.startState().isPresent() || start.endState().isPresent()
                    || start.stableOid().isPresent() || start.id().origin() != IdOrigin.SOURCE) {
                throw new DbException("Unsupported basket metadata for " + start.id().value());
            }
            basketId = db.nextId();
            String sql = "INSERT INTO " + db.technicalTable(BASKET)
                    + " (" + T_ID + ", topic, " + T_ILI_TID
                    + ", attachmentkey, dataset, domains) VALUES (?, ?, ?, ?, ?, '')";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, basketId);
                statement.setString(2, topic.name().declared().toString());
                statement.setString(3, start.id().value());
                statement.setString(4, Long.toString(basketId));
                statement.setLong(5, datasetId);
                statement.executeUpdate();
            }
        }

        void insertObject(TransferEvent.ObjectEvent event) throws SQLException {
            var object = event.object();
            var identity = object.identity();
            if (event.operation().isPresent() || event.consistency() != Consistency.COMPLETE
                    || identity.oid().isPresent() || identity.originalBasket().isPresent()) {
                throw new DbException("Unsupported object metadata for " + object.type());
            }
            String tid = identity.tid().filter(id -> id.origin() == IdOrigin.SOURCE).map(TransferId::value)
                    .orElseThrow(() -> new DbException("Missing source TID"));
            var table = mapping.table(object.type());
            long id = db.nextId();
            var statement = insertStatement(table);
            statement.setLong(1, id);
            statement.setLong(2, basketId);
            statement.setString(3, tid);
            int index = 4;
            int found = 0;
            for (var column : table.columns()) {
                var value = object.value(column.slot().symbol());
                if (value.isPresent()) {
                    found++;
                }
                var payload = value.filter(val -> val != Undefined.INSTANCE).orElse(null);
                if (column.kind() == DbMapping.Kind.REFERENCE && payload instanceof Reference reference) {
                    var target = bindReference(table, column, id, tid, identity.currentBasket(), reference);
                    if (target == null) {
                        statement.setNull(index++, Types.BIGINT);
                    } else {
                        statement.setLong(index++, target.id());
                    }
                } else {
                    index = bindValue(statement, index, column, payload);
                }
            }
            if (found != object.data().values().size()) {
                throw new DbException("Object " + tid + " contains a field without a stored mapping");
            }
            statement.executeUpdate();
            if (referenceTargets.contains(object.type())) {
                objects.put(tid, new StoredObject(id, object.type()));
            }
        }

        private PreparedStatement insertStatement(Table table) throws SQLException {
            var statement = inserts.get(table.name());
            if (statement == null) {
                var names = new StringJoiner(", ").add(T_ID).add(T_BASKET).add(T_ILI_TID);
                var values = new StringJoiner(", ").add("?").add("?").add("?");
                for (var column : table.columns()) {
                    names.add(quote(column.name()));
                    values.add(column.kind().geometry() ? db.geometryParameter() : "?");
                }
                statement = db.connection().prepareStatement("INSERT INTO " + db.table(table.name())
                        + " (" + names + ") VALUES (" + values + ")");
                inserts.put(table.name(), statement);
            }
            return statement;
        }

        private int bindValue(PreparedStatement statement, int index, Column column, TransferValue value)
                throws SQLException {
            if (column.kind().geometry()) {
                return db.bindGeometry(statement, index, column, (TransferGeometry) value);
            }
            if (value == null) {
                statement.setNull(index, Types.NULL);
                return index + 1;
            }
            switch (column.kind()) {
                case TEXT -> statement.setString(index, ((Text) value).value());
                case BOOLEAN -> statement.setBoolean(index, ((BooleanValue) value).value());
                case ENUMERATION -> statement.setString(index, String.join(".", ((Enumeration) value).path()));
                case INTEGER -> {
                    try {
                        statement.setInt(index, ((NumberValue) value).exactValue().intValueExact());
                    } catch (ArithmeticException e) {
                        throw new DbException("Value exceeds the 32-bit integer encoding of "
                                + column.slot().name().declared(), e);
                    }
                }
                case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE, REFERENCE ->
                        throw new DbException("Unsupported value for " + column.slot().name().declared());
            }
            return index + 1;
        }

        private StoredObject bindReference(Table table, Column column, long id, String tid, BasketId basket,
                                           Reference reference) {
            if (reference.external() || reference.orderPosition().isPresent()
                    || reference.targetBasket().filter(target -> !target.value().equals(basket.value())).isPresent()
                    || !(reference.target() instanceof TransferId)) {
                throw new DbException("Unsupported reference in " + tid + "." + column.slot().name().declared().last());
            }
            var patch = new Pending(table, column, id, tid, reference.target().value());
            var target = objects.get(patch.targetTid());
            if (target == null) {
                pending.add(patch);
            } else {
                checkReference(patch, target);
            }
            return target;
        }

        void finishBasket() throws SQLException {
            for (var patch : pending) {
                token.throwIfCancellationRequested();
                var target = objects.get(patch.targetTid());
                checkReference(patch, target);
                var statement = updates.get(patch.column());
                if (statement == null) {
                    statement = db.connection().prepareStatement("UPDATE " + db.table(patch.table().name())
                            + " SET " + quote(patch.column().name()) + " = ? WHERE " + T_ID + " = ?");
                    updates.put(patch.column(), statement);
                }
                statement.setLong(1, target.id());
                statement.setLong(2, patch.objectId());
                if (statement.executeUpdate() != 1) {
                    throw new DbException("Could not patch reference on object " + patch.objectTid());
                }
            }
            pending.clear();
            objects.clear();
        }

        private static void checkReference(Pending patch, StoredObject target) {
            if (target == null || !target.type().equals(patch.column().target().orElseThrow())) {
                throw new DbException("Cannot encode reference from " + patch.objectTid() + " to "
                        + patch.targetTid() + " as an ID in the mapped target table in this basket");
            }
        }

        @Override
        public void close() throws SQLException {
            var statements = new ArrayList<>(inserts.values());
            statements.addAll(updates.values());
            SQLException failure = null;
            for (var statement : statements) {
                try {
                    statement.close();
                } catch (SQLException error) {
                    if (failure == null) {
                        failure = error;
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record StoredObject(long id, SymbolRef type) { }

    private record Pending(Table table, Column column, long objectId, String objectTid, String targetTid) { }
}
