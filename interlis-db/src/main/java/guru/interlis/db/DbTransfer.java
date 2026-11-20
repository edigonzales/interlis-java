package guru.interlis.db;

import guru.interlis.db.DbMapping.Column;
import guru.interlis.db.DbMapping.Composition;
import guru.interlis.db.DbMapping.EmbeddedAssociation;
import guru.interlis.db.DbMapping.Table;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.RoleSlot;
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
import guru.interlis.transfer.TransferValue.Repeated;
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
import static guru.interlis.db.Ili2dbLayout.T_SEQ;

final class DbTransfer {
    private DbTransfer() { }

    static void exportData(Database db, DbMapping mapping, String dataset,
                           TransferWriter writer, CancellationToken token) throws SQLException {
        token.throwIfCancellationRequested();
        long datasetId = findDataset(db, dataset)
                .orElseThrow(() -> new DbException("Dataset does not exist: " + dataset));
        checkStructureOwners(db, mapping, datasetId, token);
        checkLinkOwners(db, mapping, datasetId, token);
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
            if (table.object().transferable() && table.object().topic().orElseThrow().equals(topic.symbol())
                    && !mapping.isEmbeddedLink(table)) {
                writeObjects(db, mapping, table, basketId, bid, writer, token);
            }
        }
        writer.write(new TransferEvent.EndBasket(bid), token);
    }

    private static void writeObjects(Database db, DbMapping mapping, Table table, long basketId, BasketId bid,
                                     TransferWriter writer, CancellationToken token) throws SQLException {
        var projections = new StringJoiner(", ").add("obj." + T_ID);
        if (table.hasTidColumn()) {
            projections.add("obj." + T_ILI_TID);
        }
        var from = new StringBuilder(db.table(table.name())).append(" obj");
        String basketField = "obj." + T_BASKET;
        appendColumns(db, mapping, table, "obj", projections, from);
        var links = mapping.linkFields(table.object().symbol());
        for (int index = 0; index < links.size(); index++) {
            var association = links.get(index);
            var link = mapping.table(association.object().symbol());
            String alias = "link" + index;
            from.append(" LEFT JOIN ").append(db.table(link.name())).append(' ').append(alias)
                    .append(" ON ").append(alias).append('.').append(linkOwnerColumn(link, association))
                    .append(" = obj.").append(T_ID)
                    .append(" AND ").append(alias).append('.').append(T_BASKET).append(" = ").append(basketField);
            projections.add(alias + "." + T_ID);
            appendColumns(db, mapping, link, alias, projections, from);
        }
        String sql = "SELECT " + projections + " FROM " + from
                + " WHERE " + basketField + " = ? ORDER BY obj." + T_ID;
        try (var structures = new StructureRows(db, token);
             var statement = db.connection().prepareStatement(sql)) {
            for (var composition : mapping.compositionsForParent(table.object().symbol())) {
                structures.open(table, mapping.table(composition.child()), composition, basketId);
            }
            fetchInPages(db, statement);
            statement.setLong(1, basketId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    Optional<TransferId> tid = table.hasTidColumn()
                            ? Optional.of(new TransferId(identifier(rows.getString(2), "TID", table.name()), IdOrigin.SOURCE))
                            : Optional.empty();
                    int start = table.hasTidColumn() ? 3 : 2;
                    var values = readFields(db, rows, start, table);
                    int next = start + projectionSize(table);
                    for (var association : links) {
                        var link = mapping.table(association.object().symbol());
                        if (rows.getObject(next) != null) {
                            var fields = readFields(db, rows, next + 1, link);
                            fields.remove(association.ownerRole().slot().symbol());
                            var role = association.role().symbol();
                            values.put(role, fields.size() == 1 ? fields.get(role) : new Structured(link.object().symbol(), fields));
                        }
                        next += 1 + projectionSize(link);
                    }
                    structures.readFor(rows.getLong(1), values);
                    var identity = new TransferIdentity(bid, Optional.empty(), tid, Optional.empty(), Optional.empty());
                    var object = new TransferObject(identity, new Structured(table.object().symbol(), values));
                    writer.write(new TransferEvent.ObjectEvent(object, Optional.empty(), Consistency.COMPLETE), token);
                }
                structures.requireExhausted();
            }
        }
    }

    private static void appendColumns(Database db, DbMapping mapping, Table table, String alias,
                                      StringJoiner projections, StringBuilder from) {
        int refIndex = 0;
        for (var column : table.columns()) {
            String field = alias + "." + quote(column.name());
            switch (column.kind()) {
                case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE ->
                        projections.add(db.geometryProjection(field, column.kind()));
                case REFERENCE -> {
                    String ref = alias + "ref" + refIndex++;
                    var target = mapping.table(column.target().orElseThrow());
                    from.append(" LEFT JOIN ").append(db.table(target.name())).append(' ').append(ref)
                            .append(" ON ").append(ref).append('.').append(T_ID).append(" = ").append(field)
                            .append(" AND ").append(ref).append('.').append(T_BASKET).append(" = ")
                            .append(alias).append('.').append(T_BASKET);
                    projections.add(field).add(ref + "." + T_ILI_TID);
                }
                default -> projections.add(field);
            }
        }
    }

    private static int projectionSize(Table table) {
        return table.columns().stream().mapToInt(DbTransfer::projectionSize).sum();
    }

    private static int projectionSize(Column column) {
        return column.kind() == DbMapping.Kind.REFERENCE ? 2 : 1;
    }

    private static String linkOwnerColumn(Table table, EmbeddedAssociation association) {
        return quote(table.column(association.ownerRole().slot().symbol()).name());
    }

    private static Map<SymbolRef, TransferValue> readFields(Database db, ResultSet rows, int index, Table table)
            throws SQLException {
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        for (var column : table.columns()) {
            var value = readValue(db, rows, index, column);
            if (value != null) {
                values.put(column.slot().symbol(), value);
            } else if (!table.hasTidColumn() && column.slot() instanceof RoleSlot) {
                throw new DbException("Missing link endpoint at " + column.slot().name().declared());
            }
            index += projectionSize(column);
        }
        return values;
    }

    private static void checkLinkOwners(Database db, DbMapping mapping, long datasetId,
                                        CancellationToken token) throws SQLException {
        for (var association : mapping.associations()) {
            if (!association.linkTable()) continue;
            token.throwIfCancellationRequested();
            var link = mapping.table(association.object().symbol());
            var parent = mapping.table(association.owner());
            String owner = "link." + linkOwnerColumn(link, association);
            String basket = db.technicalTable(BASKET);
            String sql = "SELECT COUNT(*) FROM " + db.table(link.name()) + " link LEFT JOIN "
                    + db.table(parent.name()) + " parent ON parent." + T_ID + " = " + owner
                    + " LEFT JOIN " + basket + " lb ON lb." + T_ID + " = link." + T_BASKET
                    + " LEFT JOIN " + basket + " pb ON pb." + T_ID + " = parent." + T_BASKET
                    + " WHERE lb.dataset = ? OR pb.dataset = ? GROUP BY " + owner
                    + " HAVING COUNT(*) > 1 OR MAX(CASE WHEN parent." + T_ID + " IS NULL OR lb." + T_ID
                    + " IS NULL OR parent." + T_BASKET + " <> link." + T_BASKET
                    + " OR lb.topic <> ? THEN 1 ELSE 0 END) > 0 LIMIT 1";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, datasetId);
                statement.setLong(2, datasetId);
                statement.setString(3, parent.object().topic().flatMap(mapping.plan()::topic).orElseThrow()
                        .name().declared().toString());
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        String problem = rows.getLong(1) > 1 ? "Multiple link rows" : "Missing or inconsistent link owner";
                        throw new DbException(problem + " at " + association.role().name().declared());
                    }
                }
            }
        }
    }

    private static void checkStructureOwners(Database db, DbMapping mapping, long datasetId,
                                             CancellationToken token) throws SQLException {
        for (var table : mapping.tables()) {
            if (table.object().transferable()) {
                continue;
            }
            token.throwIfCancellationRequested();
            var ownerCount = new StringJoiner(" + ").setEmptyValue("0");
            for (var composition : mapping.compositionsForChild(table.object().symbol())) {
                ownerCount.add("CASE WHEN child." + quote(composition.ownerColumn()) + " IS NULL THEN 0 ELSE 1 END");
            }
            var topic = table.object().topic().flatMap(mapping.plan()::topic);
            String wrongTopic = topic.isPresent() ? " OR basket.topic <> ?" : "";
            String sql = "SELECT child." + T_ID + " FROM " + db.table(table.name()) + " child JOIN "
                    + db.technicalTable(BASKET) + " basket ON basket." + T_ID + " = child." + T_BASKET
                    + " WHERE basket.dataset = ? AND ((" + ownerCount + ") <> 1" + wrongTopic + ") LIMIT 1";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, datasetId);
                if (topic.isPresent()) {
                    statement.setString(2, topic.orElseThrow().name().declared().toString());
                }
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        throw new DbException("Expected exactly one structure owner in the correct topic for "
                                + table.name() + " T_Id " + rows.getLong(1));
                    }
                }
            }
        }
    }

    private static final class StructureRows implements AutoCloseable {
        private final Database db;
        private final CancellationToken token;
        private final List<PreparedStatement> statements = new ArrayList<>();
        private final List<StructureCursor> cursors = new ArrayList<>();

        StructureRows(Database db, CancellationToken token) {
            this.db = db;
            this.token = token;
        }

        void open(Table parent, Table child, Composition composition, long basketId) throws SQLException {
            token.throwIfCancellationRequested();
            String owner = "child." + quote(composition.ownerColumn());
            String sequence = "child." + T_SEQ;
            String basket = "child." + T_BASKET;
            var fields = new StringJoiner(", ").add(owner).add(sequence).add(basket);
            for (var column : child.columns()) {
                fields.add("child." + quote(column.name()));
            }
            String sql = "SELECT " + fields + " FROM " + db.table(child.name()) + " child LEFT JOIN "
                    + db.table(parent.name()) + " parent ON parent." + T_ID + " = " + owner
                    + " WHERE " + owner + " IS NOT NULL AND (" + basket + " = ? OR parent." + T_BASKET
                    + " = ?) ORDER BY " + owner + ", " + sequence + ", child." + T_ID;
            var statement = db.connection().prepareStatement(sql);
            statements.add(statement);
            fetchInPages(db, statement);
            statement.setLong(1, basketId);
            statement.setLong(2, basketId);
            cursors.add(new StructureCursor(child, composition, basketId, statement.executeQuery()));
        }

        void readFor(long parentId, Map<SymbolRef, TransferValue> values) throws SQLException {
            for (var cursor : cursors) {
                var value = cursor.readFor(parentId);
                if (value != null) {
                    values.put(cursor.composition.slot().symbol(), value);
                }
            }
        }

        void requireExhausted() {
            for (var cursor : cursors) {
                if (cursor.available) {
                    throw cursor.problem("Orphan structure row");
                }
            }
        }

        @Override
        public void close() throws SQLException {
            closeStatements(statements);
        }

        private final class StructureCursor {
            private final Table child;
            private final Composition composition;
            private final long basketId;
            private final ResultSet rows;
            private boolean available;
            private long ownerId;

            StructureCursor(Table child, Composition composition, long basketId, ResultSet rows) throws SQLException {
                this.child = child;
                this.composition = composition;
                this.basketId = basketId;
                this.rows = rows;
                advance();
            }

            TransferValue readFor(long parentId) throws SQLException {
                if (!available || ownerId > parentId) {
                    return null;
                }
                if (ownerId < parentId) {
                    throw problem("Orphan structure row");
                }
                if (!composition.list()) {
                    var value = readChild();
                    if (available && ownerId == parentId) {
                        throw problem("Multiple rows for a single structure value");
                    }
                    return value;
                }
                var items = new ArrayList<TransferValue>();
                Long previous = null;
                do {
                    long sequence = integer(2, "T_Seq");
                    if (previous != null && sequence <= previous) {
                        throw problem("Duplicate or out-of-order T_Seq");
                    }
                    items.add(readChild());
                    previous = sequence;
                } while (available && ownerId == parentId);
                return new Repeated(CollectionKind.LIST, items);
            }

            private Structured readChild() throws SQLException {
                var value = new Structured(child.object().symbol(), readFields(db, rows, 4, child));
                advance();
                return value;
            }

            private void advance() throws SQLException {
                token.throwIfCancellationRequested();
                available = rows.next();
                if (available) {
                    ownerId = integer(1, "structure owner ID");
                    if (integer(3, "T_Basket") != basketId) {
                        throw problem("Structure row and owner are in different baskets");
                    }
                }
            }

            private long integer(int index, String column) throws SQLException {
                return switch (rows.getObject(index)) {
                    case Long value -> value;
                    case Integer value -> value;
                    case Short value -> value;
                    case null, default -> throw problem("Expected an integer " + column);
                };
            }

            private DbException problem(String message) {
                return new DbException(message + " at " + composition.slot().name().declared());
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

    private static void closeStatements(Iterable<PreparedStatement> statements) throws SQLException {
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

    private static final class Import implements AutoCloseable {
        private final Database db;
        private final DbMapping mapping;
        private final CancellationToken token;
        private final Map<String, PreparedStatement> inserts = new HashMap<>();
        private final Map<Composition, PreparedStatement> structureInserts = new HashMap<>();
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
            var table = mapping.table(object.type());
            if (mapping.isEmbeddedLink(table)) {
                throw new DbException("Expected an embedded association at " + table.object().name().declared());
            }
            boolean hasTid = table.hasTidColumn();
            var tid = identity.tid().filter(value -> value.origin() == IdOrigin.SOURCE).map(TransferId::value);
            if (hasTid && tid.isEmpty()) {
                throw new DbException("Missing source TID");
            }
            if (!hasTid && identity.tid().isPresent()) {
                throw new DbException("Unexpected TID for link " + table.object().name().declared());
            }
            long id = db.nextId();
            String context = tid.orElseGet(() -> table.object().name().declared() + " T_Id " + id);
            var statement = insertStatement(table);
            statement.setLong(1, id);
            statement.setLong(2, basketId);
            int index = 3;
            if (hasTid) {
                statement.setString(index++, tid.orElseThrow());
            }
            int found = 0;
            for (var column : table.columns()) {
                var value = object.value(column.slot().symbol());
                if (value.isPresent()) {
                    found++;
                }
                var payload = value.filter(val -> val != Undefined.INSTANCE).orElse(null);
                if (!hasTid && column.slot() instanceof RoleSlot && !(payload instanceof Reference)) {
                    throw new DbException("Missing link endpoint at " + column.slot().name().declared());
                }
                if (hasTid && column.slot() instanceof RoleSlot role && payload instanceof Structured link) {
                    var reference = link.value(role.symbol()).orElse(null);
                    if (link.values().size() != 1 || !(reference instanceof Reference)
                            || role.role().owner().filter(link.type().symbol()::equals).isEmpty()) {
                        throw new DbException("Unsupported embedded association payload at " + role.name().declared());
                    }
                    payload = reference;
                }
                index = bindColumn(statement, index, table, column, id, context, identity.currentBasket(), payload);
            }
            var compositions = mapping.compositionsForParent(object.type());
            for (var composition : compositions) {
                if (object.value(composition.slot().symbol()).isPresent()) {
                    found++;
                }
            }
            var links = mapping.linkFields(object.type());
            for (var association : links) {
                if (object.value(association.role().symbol()).isPresent()) found++;
            }
            if (found != object.data().values().size()) {
                throw new DbException("Object " + context + " contains a field without a stored mapping");
            }
            statement.executeUpdate();
            for (var composition : compositions) {
                var value = object.value(composition.slot().symbol()).orElse(null);
                if (value != null && value != Undefined.INSTANCE) {
                    insertStructures(composition, id, value);
                }
            }
            for (var association : links) {
                var value = object.value(association.role().symbol()).orElse(null);
                if (value != null && value != Undefined.INSTANCE) {
                    insertLink(association, id, context, identity.currentBasket(), value);
                }
            }
            if (referenceTargets.contains(object.type())) {
                objects.put(tid.orElseThrow(), new StoredObject(id, object.type()));
            }
        }

        private void insertLink(EmbeddedAssociation association, long ownerId, String ownerTid,
                                BasketId basket, TransferValue value) throws SQLException {
            var table = mapping.table(association.object().symbol());
            String context = ownerTid + "." + association.role().name().declared();
            var role = association.role().symbol();
            var fields = switch (value) {
                case Reference reference -> Map.<SymbolRef, TransferValue>of(role, reference);
                case Structured link when link.type().equals(association.object().symbol()) -> link.values();
                default -> throw new DbException("Unsupported embedded association payload at " + context);
            };
            if (!(fields.get(role) instanceof Reference)) {
                throw new DbException("Missing link endpoint at " + context);
            }
            long id = db.nextId();
            var statement = insertStatement(table);
            statement.setLong(1, id);
            statement.setLong(2, basketId);
            int index = 3;
            int found = 0;
            for (var column : table.columns()) {
                var field = column.slot().symbol();
                if (field.equals(association.ownerRole().slot().symbol())) {
                    statement.setLong(index++, ownerId);
                } else {
                    var payload = fields.get(field);
                    if (payload != null) found++;
                    index = bindColumn(statement, index, table, column, id, context, basket,
                            payload == Undefined.INSTANCE ? null : payload);
                }
            }
            if (found != fields.size()) {
                throw new DbException("Embedded association contains a field without a stored mapping at " + context);
            }
            statement.executeUpdate();
        }

        private void insertStructures(Composition composition, long ownerId, TransferValue value) throws SQLException {
            if (composition.list()) {
                if (!(value instanceof Repeated list) || list.kind() != CollectionKind.LIST) {
                    throw new DbException("Expected a structure list at " + composition.slot().name().declared());
                }
                int sequence = 0;
                for (var child : list.values()) {
                    insertStructure(composition, ownerId, sequence++, child);
                }
            } else {
                insertStructure(composition, ownerId, 0, value);
            }
        }

        private void insertStructure(Composition composition, long ownerId, int sequence, TransferValue value)
                throws SQLException {
            token.throwIfCancellationRequested();
            if (!(value instanceof Structured structure) || !structure.type().equals(composition.child())) {
                throw new DbException("Cannot encode structure type at " + composition.slot().name().declared());
            }
            var table = mapping.table(composition.child());
            var statement = structureInserts.get(composition);
            if (statement == null) {
                statement = prepareInsert(table, composition);
                structureInserts.put(composition, statement);
            }
            statement.setLong(1, db.nextId());
            statement.setLong(2, basketId);
            statement.setNull(3, Types.VARCHAR);
            statement.setInt(4, sequence);
            statement.setLong(5, ownerId);
            int index = 6;
            int found = 0;
            for (var column : table.columns()) {
                var field = structure.value(column.slot().symbol());
                if (field.isPresent()) {
                    found++;
                }
                index = bindValue(statement, index, column, field.filter(item -> item != Undefined.INSTANCE).orElse(null));
            }
            if (found != structure.values().size()) {
                throw new DbException("Structure contains a field without a stored mapping at "
                        + composition.slot().name().declared());
            }
            statement.executeUpdate();
        }

        private PreparedStatement insertStatement(Table table) throws SQLException {
            var statement = inserts.get(table.name());
            if (statement == null) {
                statement = prepareInsert(table, null);
                inserts.put(table.name(), statement);
            }
            return statement;
        }

        private PreparedStatement prepareInsert(Table table, Composition composition) throws SQLException {
            var names = new StringJoiner(", ").add(T_ID).add(T_BASKET);
            var values = new StringJoiner(", ").add("?").add("?");
            if (table.hasTidColumn()) {
                names.add(T_ILI_TID);
                values.add("?");
            }
            if (composition != null) {
                names.add(T_SEQ).add(quote(composition.ownerColumn()));
                values.add("?").add("?");
            }
            for (var column : table.columns()) {
                names.add(quote(column.name()));
                values.add(column.kind().geometry() ? db.geometryParameter() : "?");
            }
            return db.connection().prepareStatement("INSERT INTO " + db.table(table.name())
                    + " (" + names + ") VALUES (" + values + ")");
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

        private int bindColumn(PreparedStatement statement, int index, Table table, Column column, long id,
                               String context, BasketId basket, TransferValue value) throws SQLException {
            if (column.kind() == DbMapping.Kind.REFERENCE && value instanceof Reference reference) {
                var target = bindReference(table, column, id, context, basket, reference);
                if (target == null) {
                    statement.setNull(index, Types.BIGINT);
                } else {
                    statement.setLong(index, target.id());
                }
                return index + 1;
            }
            return bindValue(statement, index, column, value);
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
            statements.addAll(structureInserts.values());
            statements.addAll(updates.values());
            closeStatements(statements);
        }
    }

    private record StoredObject(long id, SymbolRef type) { }

    private record Pending(Table table, Column column, long objectId, String objectTid, String targetTid) { }
}
