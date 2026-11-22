package guru.interlis.db;

import guru.interlis.db.DbMapping.Column;
import guru.interlis.db.DbMapping.Composition;
import guru.interlis.db.DbMapping.EmbeddedAssociation;
import guru.interlis.db.DbMapping.SecondaryGeometry;
import guru.interlis.db.DbMapping.Table;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolKind;
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
import guru.interlis.transfer.TransferIdentity.ObjectOid;
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
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.db.Database.quote;
import static guru.interlis.db.Ili2dbLayout.BASKET;
import static guru.interlis.db.Ili2dbLayout.DATASET;
import static guru.interlis.db.Ili2dbLayout.T_BASKET;
import static guru.interlis.db.Ili2dbLayout.T_ID;
import static guru.interlis.db.Ili2dbLayout.T_ILI_TID;
import static guru.interlis.db.Ili2dbLayout.T_SEQ;
import static guru.interlis.db.Ili2dbLayout.T_TYPE;

final class DbTransfer {
    // The object tag is at depth four. Each structure adds an attribute tag and its value tag.
    private static final int MAX_STRUCTURE_DEPTH = (128 - 4) / 2;
    private static final GeometrySelection ALL_GEOMETRIES = new GeometrySelection(List.of(), Map.of());

    private DbTransfer() { }

    static void exportData(Database db, DbMapping mapping, String dataset, Optional<String> outputModel,
                           TransferWriter writer, CancellationToken token) throws SQLException {
        token.throwIfCancellationRequested();
        long datasetId = DbDatasetWrite.findDataset(db, dataset)
                .orElseThrow(() -> new DbException("Dataset does not exist: " + dataset));
        checkStructureOwners(db, mapping, datasetId, token);
        checkLinkOwners(db, mapping, datasetId, token);
        checkHierarchyTopics(db, mapping, datasetId, token);
        checkCrossDatasetReferences(db, mapping, datasetId, token);
        var plan = mapping.plan();
        var output = outputModel.map(name -> plan.models().stream().filter(model -> model.name().declared().last().equals(name))
                .findFirst().orElseThrow().symbol());
        var topics = plan.topics().stream().filter(topic -> mapping.models().contains(topic.model()))
                .collect(Collectors.toMap(topic -> topic.name().declared().toString(), Function.identity()));
        var exported = new HashSet<SymbolRef>();
        String modelQuery = "SELECT DISTINCT topic FROM " + db.technicalTable(BASKET) + " WHERE dataset = ?";
        try (var statement = db.connection().prepareStatement(modelQuery)) {
            statement.setLong(1, datasetId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    var stored = selectedTopic(topics, rows.getString(1));
                    exported.add(output.map(model -> mapping.translations().outputTopic(stored, model)).orElse(stored).model());
                }
            }
        }
        if (exported.isEmpty()) throw new DbException("Dataset has no baskets: " + dataset);
        boolean ili23 = mapping.version() == LanguageVersion.ILI23;
        var declarations = (output.isPresent() ? output.stream() : mapping.models().stream()).filter(exported::contains).map(ref -> {
            var model = plan.model(ref).orElseThrow();
            var symbol = (ModelSymbol) plan.symbol(ref).known().orElseThrow();
            return new TransferEvent.ModelDeclaration(ref, mapping.version(),
                    ili23 ? Optional.of(model.modelVersion()) : Optional.empty(),
                    ili23 ? Optional.of(symbol.issuer()) : Optional.empty());
        }).toList();
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
                        Optional.empty(), declarations), token);
                do {
                    token.throwIfCancellationRequested();
                    var topicName = rows.getString(2);
                    var stored = selectedTopic(topics, topicName);
                    var topic = output.map(model -> mapping.translations().outputTopic(stored, model)).orElse(stored);
                    String domains = rows.getString(4);
                    var bid = new BasketId(identifier(rows.getString(3), "BID", topicName), IdOrigin.SOURCE);
                    var bindings = DbDomains.read(plan, stored, domains, bid.value());
                    writeBasket(db, mapping, topic, rows.getLong(1), bid, bindings, writer, token);
                } while (rows.next());
            }
        }
        writer.write(new TransferEvent.EndTransfer(), token);
    }

    private static TopicDescriptor selectedTopic(Map<String, TopicDescriptor> topics, String name) {
        var topic = topics.get(name);
        if (topic == null) throw new DbException("Basket topic is outside the selected model: " + name);
        if (topic.abstractTopic()) throw new DbException("Basket uses an abstract topic: " + name);
        return topic;
    }

    private static void writeBasket(Database db, DbMapping mapping, TopicDescriptor topic, long basketId,
                                    BasketId bid, List<TransferEvent.DomainBinding> bindings, TransferWriter writer,
                                    CancellationToken token) throws SQLException {
        var stableOid = basketOid(mapping, topic, bid);
        writer.write(new TransferEvent.StartBasket(bid, topic.symbol(), List.of(), BasketKind.FULL,
                Consistency.COMPLETE, Optional.empty(), Optional.empty(), stableOid, bindings), token);
        var selection = new GeometrySelection(bindings,
                mapping.crsPair().map(pair -> pair.epsgCodes(topic.symbol())).orElse(Map.of()));
        for (var table : mapping.tables()) {
            if (table.hasDiscriminator() && table.object().kind() == SymbolKind.STRUCTURE) {
                checkStructureHierarchy(db, mapping, table, basketId, token);
            }
            if (table.subtype() || table.object().topic().filter(declared -> mapping.includesTopic(topic.symbol(), declared)).isEmpty()) {
                continue;
            }
            if (table.hasDiscriminator() && table.object().kind() != SymbolKind.STRUCTURE && !mapping.isEmbeddedLink(table)) {
                writeHierarchyObjects(db, mapping, table, basketId, bid, topic.symbol(), writer, token);
            } else if (table.object().transferable() && !mapping.isEmbeddedLink(table)) {
                writeObjects(db, mapping, table, basketId, bid, topic.symbol(), selection, writer, token);
            }
        }
        writer.write(new TransferEvent.EndBasket(bid), token);
    }

    private record GeometrySelection(List<TransferEvent.DomainBinding> domains, Map<SymbolRef, Integer> epsgCodes) {
        boolean active(Column column) {
            return (domains.isEmpty() || DbDomains.active(column, domains))
                    && epsgCodes.getOrDefault(column.slot().symbol(), column.epsgCode()) == column.epsgCode();
        }
    }

    private static void writeObjects(Database db, DbMapping mapping, Table table, long basketId, BasketId bid,
                                     SymbolRef topic, GeometrySelection selection, TransferWriter writer,
                                     CancellationToken token) throws SQLException {
        var alternate = mapping.crsPair().flatMap(pair -> pair.alternateClass(topic, table.object().symbol()));
        var projections = new StringJoiner(", ").add("obj." + T_ID);
        if (table.hasTidColumn()) {
            projections.add("obj." + T_ILI_TID);
        }
        var from = new StringBuilder(db.table(table.name())).append(" obj");
        String basketField = "obj." + T_BASKET;
        appendColumns(db, mapping, table, "obj", projections, from);
        var links = mapping.linkFields(table.object().symbol());
        appendEmbeddedLinks(db, mapping, links, "obj", projections, from);
        String sql = "SELECT " + projections + " FROM " + from
                + " WHERE " + basketField + " = ? ORDER BY obj." + T_ID;
        try (var structures = new StructureRows(db, mapping, basketId, token);
             var statement = db.connection().prepareStatement(sql)) {
            for (var composition : mapping.compositionsForParent(table.object().symbol())) {
                structures.open(composition);
            }
            fetchInPages(db, statement);
            statement.setLong(1, basketId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    String ownerTid = table.hasTidColumn() ? identifier(rows.getString(2), "TID", table.name()) : null;
                    int start = table.hasTidColumn() ? 3 : 2;
                    var values = readFields(db, mapping, rows, start, table, ownerTid, selection);
                    readEmbeddedLinks(db, mapping, rows, start + projectionSize(mapping, table), links, ownerTid, values);
                    structures.readFor(rows.getLong(1), ownerTid, values, table.object().symbol());
                    var identity = objectIdentity(mapping, table, bid, ownerTid);
                    var data = alternate.isPresent()
                            ? new Structured(alternate.orElseThrow().symbol(),
                                    mapping.crsPair().orElseThrow().alternateValues(table.object().symbol(), values))
                            : new Structured(table.object().symbol(), values);
                    var object = new TransferObject(identity, data);
                    object = mapping.translations().inTopic(object, topic);
                    writer.write(new TransferEvent.ObjectEvent(object, Optional.empty(), Consistency.COMPLETE), token);
                }
                structures.requireExhausted();
            }
        }
    }

    private static void appendEmbeddedLinks(Database db, DbMapping mapping, List<EmbeddedAssociation> links,
                                            String owner, StringJoiner projections, StringBuilder from) {
        for (int index = 0; index < links.size(); index++) {
            var association = links.get(index);
            var link = mapping.table(association.object().symbol());
            String alias = "link" + index;
            from.append(" LEFT JOIN ").append(db.table(link.name())).append(' ').append(alias)
                    .append(" ON ").append(alias).append('.').append(linkOwnerColumn(link, association))
                    .append(" = ").append(owner).append('.').append(T_ID)
                    .append(" AND ").append(alias).append('.').append(T_BASKET)
                    .append(" = ").append(owner).append('.').append(T_BASKET);
            projections.add(alias + "." + T_ID);
            appendColumns(db, mapping, link, alias, projections, from);
        }
    }

    private static void readEmbeddedLinks(Database db, DbMapping mapping, ResultSet rows, int next,
                                          List<EmbeddedAssociation> links, String ownerTid,
                                          Map<SymbolRef, TransferValue> values) throws SQLException {
        for (var association : links) {
            var link = mapping.table(association.object().symbol());
            if (rows.getObject(next) != null) {
                var fields = readFields(db, mapping, rows, next + 1, link, ownerTid);
                var owner = (Reference) fields.remove(association.ownerRole().slot().symbol());
                var role = association.role().symbol();
                if (owner.orderPosition().isPresent()) {
                    fields.put(role, withOrderPosition((Reference) fields.get(role), owner.orderPosition()));
                }
                values.put(role, fields.size() == 1 ? fields.get(role) : new Structured(link.object().symbol(), fields));
            }
            next += 1 + projectionSize(mapping, link);
        }
    }

    private static Reference withOrderPosition(Reference reference, Optional<BigInteger> position) {
        return new Reference(reference.target(), reference.targetBasket(), reference.external(), position);
    }

    private static void writeHierarchyObjects(Database db, DbMapping mapping, Table root, long basketId,
                                               BasketId bid, SymbolRef topic, TransferWriter writer, CancellationToken token)
            throws SQLException {
        boolean hasTid = root.hasTidColumn();
        var fields = new StringJoiner(", ").add("obj." + T_ID);
        if (hasTid) fields.add("obj." + T_ILI_TID);
        fields.add("obj." + T_TYPE);
        var from = new StringBuilder(db.table(root.name())).append(" obj");
        appendColumns(db, mapping, root, "obj", fields, from);
        String sql = "SELECT " + fields + " FROM " + from + " WHERE obj." + T_BASKET
                + " = ? ORDER BY obj." + T_ID;
        try (var fragments = new HierarchyRows(db, mapping, basketId, token);
             var structures = new StructureRows(db, mapping, basketId, token);
             var statement = db.connection().prepareStatement(sql)) {
            fragments.open(root, true);
            for (var part : mapping.hierarchy(root)) {
                for (var composition : mapping.compositionsForParent(part.object().symbol())) {
                    structures.open(composition);
                }
            }
            fetchInPages(db, statement);
            statement.setLong(1, basketId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    long id = rows.getLong(1);
                    int field = 2;
                    String tid = hasTid ? identifier(rows.getString(field++), "TID", root.name()) : null;
                    var actual = mapping.concreteType(root, rows.getString(field++), id, topic);
                    var values = readFields(db, mapping, rows, field, root, tid, ALL_GEOMETRIES, actual.object().symbol());
                    fragments.readFor(actual, id, tid, values);
                    structures.readFor(id, tid, values, actual.object().symbol());
                    mapping.extendedFields(actual.object().symbol()).forEach((base, effective) -> {
                        var value = values.remove(base);
                        if (value != null) values.put(effective, value);
                    });
                    var identity = objectIdentity(mapping, root, bid, tid);
                    var object = new TransferObject(identity, new Structured(actual.object().symbol(), values));
                    object = mapping.translations().inTopic(object, topic);
                    writer.write(new TransferEvent.ObjectEvent(object, Optional.empty(), Consistency.COMPLETE), token);
                }
                fragments.requireExhausted();
                structures.requireExhausted();
            }
        }
    }

    private static void checkStructureHierarchy(Database db, DbMapping mapping, Table root, long basketId,
                                                CancellationToken token) throws SQLException {
        // Composition cursors visit structures in owner and sequence order. Check the complete
        // hierarchy in ID order first, including subtype fragments without reachable owners.
        String sql = "SELECT " + T_ID + ", " + T_TYPE + " FROM " + db.table(root.name())
                + " WHERE " + T_BASKET + " = ? ORDER BY " + T_ID;
        try (var fragments = new HierarchyRows(db, mapping, basketId, token);
             var statement = db.connection().prepareStatement(sql)) {
            fragments.open(root, false);
            fetchInPages(db, statement);
            statement.setLong(1, basketId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    long id = rows.getLong(1);
                    fragments.verifyFor(mapping.concreteType(root, rows.getString(2), id), id);
                }
                fragments.requireExhausted();
            }
        }
    }

    /** Moves each fragment cursor forward in the root's T_Id order and validates its membership. */
    private static final class HierarchyRows implements AutoCloseable {
        private final Database db;
        private final DbMapping mapping;
        private final long basketId;
        private final CancellationToken token;
        private final List<PreparedStatement> statements = new ArrayList<>();
        private final Map<Table, Fragment> fragments = new LinkedHashMap<>();

        HierarchyRows(Database db, DbMapping mapping, long basketId, CancellationToken token) {
            this.db = db;
            this.mapping = mapping;
            this.basketId = basketId;
            this.token = token;
        }

        void open(Table root, boolean includeFields) throws SQLException {
            // A separate ordered query per subtype avoids a join of unbounded width for deep hierarchies.
            for (var part : mapping.hierarchy(root)) {
                if (!part.subtype()) continue;
                token.throwIfCancellationRequested();
                var fields = new StringJoiner(", ").add("part." + T_ID).add("part." + T_BASKET);
                var from = new StringBuilder(db.table(part.name())).append(" part LEFT JOIN ")
                        .append(db.table(root.name())).append(" root ON root.").append(T_ID)
                        .append(" = part.").append(T_ID);
                var links = includeFields ? mapping.linkFields(part.object().symbol()) : List.<EmbeddedAssociation>of();
                if (includeFields) {
                    appendColumns(db, mapping, part, "part", fields, from);
                    appendEmbeddedLinks(db, mapping, links, "part", fields, from);
                }
                String sql = "SELECT " + fields + " FROM " + from + " WHERE part." + T_BASKET
                        + " = ? OR root." + T_BASKET + " = ? ORDER BY part." + T_ID;
                var statement = db.connection().prepareStatement(sql);
                statements.add(statement);
                fetchInPages(db, statement);
                statement.setLong(1, basketId);
                statement.setLong(2, basketId);
                fragments.put(part, new Fragment(part, links, statement.executeQuery()));
            }
        }

        void readFor(DbMapping.ObjectStorage actual, long id, String tid, Map<SymbolRef, TransferValue> values) throws SQLException {
            for (var part : actual.rows()) {
                if (part.subtype()) fragments.get(part).readFor(id, tid, values, actual.object().symbol());
            }
        }

        void verifyFor(DbMapping.ObjectStorage actual, long id) throws SQLException {
            for (var part : actual.rows()) {
                if (!part.subtype()) continue;
                var fragment = fragments.get(part);
                fragment.requireRow(id);
                fragment.advance();
            }
        }

        void requireExhausted() {
            for (var fragment : fragments.values()) {
                if (fragment.available) throw fragment.unexpected();
            }
        }

        @Override
        public void close() throws SQLException {
            closeStatements(statements);
        }

        private final class Fragment {
            private final Table table;
            private final List<EmbeddedAssociation> links;
            private final ResultSet rows;
            private boolean available;
            private long id;

            Fragment(Table table, List<EmbeddedAssociation> links, ResultSet rows) throws SQLException {
                this.table = table;
                this.links = links;
                this.rows = rows;
                advance();
            }

            void readFor(long rootId, String tid, Map<SymbolRef, TransferValue> values, SymbolRef actual) throws SQLException {
                requireRow(rootId);
                values.putAll(readFields(db, mapping, rows, 3, table, tid, ALL_GEOMETRIES, actual));
                if (!links.isEmpty()) {
                    readEmbeddedLinks(db, mapping, rows, 3 + projectionSize(mapping, table), links, tid, values);
                }
                advance();
            }

            private void requireRow(long rootId) throws SQLException {
                if (available && id < rootId) throw unexpected();
                if (!available || id != rootId) {
                    throw new DbException("Missing inheritance fragment in " + table.name() + " for T_Id " + rootId);
                }
                if (rows.getLong(2) != basketId) {
                    throw new DbException("Inheritance fragment in " + table.name() + " belongs to a different basket"
                            + " for T_Id " + rootId);
                }
            }

            private void advance() throws SQLException {
                token.throwIfCancellationRequested();
                available = rows.next();
                if (available) id = rows.getLong(1);
            }

            private DbException unexpected() {
                return new DbException("Unexpected or orphan inheritance fragment in " + table.name() + " for T_Id " + id);
            }
        }
    }

    private static void checkHierarchyTopics(Database db, DbMapping mapping, long datasetId, CancellationToken token)
            throws SQLException {
        for (var table : mapping.tables()) {
            if (table.object().kind() == SymbolKind.STRUCTURE || (!table.hasDiscriminator() && !table.subtype())) continue;
            token.throwIfCancellationRequested();
            String sql = "SELECT obj." + T_ID + " FROM " + db.table(table.name()) + " obj JOIN "
                    + db.technicalTable(BASKET) + " basket ON basket." + T_ID + " = obj." + T_BASKET
                    + " WHERE basket.dataset = ? AND basket.topic NOT IN " + topicNames(mapping, table) + " LIMIT 1";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, datasetId);
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        throw new DbException("Inheritance row in " + table.name()
                                + " belongs to a different topic for T_Id " + rows.getLong(1));
                    }
                }
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
                        projections.add(db.geometryProjection(field, column));
                case REFERENCE -> {
                    String ref = alias + "ref" + refIndex++;
                    SymbolRef declared = column.target().orElseThrow();
                    var target = mapping.table(column.targetRelation());
                    var root = mapping.referenceRoot(column);
                    from.append(" LEFT JOIN ").append(db.table(root.name())).append(' ').append(ref)
                            .append(" ON ").append(ref).append('.').append(T_ID).append(" = ").append(field);
                    String targetBasket = ref + "." + T_BASKET;
                    String sourceBasket = alias + "." + T_BASKET;
                    String baskets = db.technicalTable(BASKET);
                    if (column.external()) {
                        from.append(" AND EXISTS (SELECT 1 FROM ").append(baskets).append(" dst WHERE dst.")
                                .append(T_ID).append(" = ").append(targetBasket);
                        from.append(" AND dst.topic IN ").append(topicNames(mapping, declared)).append(')');
                    } else {
                        from.append(" AND ").append(targetBasket).append(" = ").append(sourceBasket);
                    }
                    if (!target.equals(root)) {
                        // Keep one outer join per reference within SQLite's join limit.
                        String fragment = ref + "part";
                        from.append(" AND EXISTS (SELECT 1 FROM ").append(db.table(target.name())).append(' ').append(fragment)
                                .append(" WHERE ").append(fragment).append('.').append(T_ID).append(" = ")
                                .append(ref).append('.').append(T_ID)
                                .append(" AND ").append(fragment).append('.').append(T_BASKET).append(" = ")
                                .append(ref).append('.').append(T_BASKET).append(')');
                    }
                    projections.add(field).add(ref + "." + T_ILI_TID);
                    if (root.hasDiscriminator()) projections.add(ref + "." + T_TYPE);
                    if (projectsTargetBid(mapping, column)) {
                        projections.add("CASE WHEN " + targetBasket + " <> " + sourceBasket + " THEN (SELECT basket."
                                + T_ILI_TID + " FROM " + baskets + " basket WHERE basket." + T_ID + " = "
                                + targetBasket + ") END");
                    }
                    column.positionColumn().ifPresent(position -> projections.add(alias + "." + quote(position)));
                }
                default -> {
                    if (column.jsonStructure().isPresent() || (db.gpkg() && column.array().isPresent())) {
                        projections.add(db.jsonProjection(field));
                    } else {
                        column.physicalNames().forEach(name -> projections.add(alias + "." + quote(name)));
                    }
                    if (column.array().flatMap(DbArray::target).isPresent()) projections.add(alias + "." + T_BASKET);
                }
            }
        }
        for (var geometry : table.secondaryGeometries()) {
            String field = "fragment." + quote(geometry.column().name());
            String lookup = " FROM " + db.table(geometry.table()) + " fragment WHERE fragment." + T_ID
                    + " = " + alias + "." + T_ID;
            projections.add("(SELECT fragment." + T_BASKET + " = " + alias + "." + T_BASKET + lookup + ")");
            projections.add("(SELECT " + db.geometryProjection(field, geometry.column()) + lookup + ")");
        }
    }

    private static void checkCrossDatasetReferences(Database db, DbMapping mapping, long datasetId, CancellationToken token)
            throws SQLException {
        String baskets = db.technicalTable(BASKET);
        for (var table : mapping.tables()) {
            for (var column : table.columns()) {
                if (!column.external()) continue;
                token.throwIfCancellationRequested();
                var root = mapping.referenceRoot(column);
                String sql = "SELECT DISTINCT ref." + T_ID + ", ref." + T_ILI_TID + ", dst." + T_ILI_TID
                        + " FROM " + db.table(table.name()) + " obj JOIN " + baskets
                        + " src ON src." + T_ID + " = obj." + T_BASKET + " JOIN " + db.table(root.name())
                        + " ref ON ref." + T_ID + " = obj." + quote(column.name()) + " JOIN " + baskets
                        + " dst ON dst." + T_ID + " = ref." + T_BASKET + " WHERE src.dataset = ? AND (dst.dataset IS NULL OR dst.dataset <> ?)";
                try (var statement = db.connection().prepareStatement(sql)) {
                    fetchInPages(db, statement);
                    statement.setLong(1, datasetId);
                    statement.setLong(2, datasetId);
                    try (var rows = statement.executeQuery()) {
                        while (rows.next()) {
                            token.throwIfCancellationRequested();
                            String context = column.slot().name().declared().toString();
                            String tid = storedTid(root, identifier(rows.getString(2), "reference target TID", context));
                            var bid = mapping.version() == LanguageVersion.ILI23
                                    ? Optional.of(identifier(rows.getString(3), "reference target BID", context))
                                    : Optional.<String>empty();
                            var resolved = findReference(db, mapping, column, tid, bid);
                            if (resolved == null || resolved.id() != rows.getLong(1)
                                    || hasExportedTid(db, mapping, datasetId, tid, token)) {
                                throw new DbException("Cannot export cross-dataset reference to " + tid + " at " + context);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean hasExportedTid(Database db, DbMapping mapping, long dataset, String tid, CancellationToken token)
            throws SQLException {
        for (var table : mapping.tables()) {
            if (!table.hasTidColumn() || table.object().kind() == SymbolKind.STRUCTURE) continue;
            token.throwIfCancellationRequested();
            String sql = "SELECT 1 FROM " + db.table(table.name()) + " obj JOIN " + db.technicalTable(BASKET)
                    + " basket ON basket." + T_ID + " = obj." + T_BASKET
                    + " WHERE basket.dataset = ? AND basket.topic IN " + topicNames(mapping, table)
                    + " AND " + tidExpression(table, "obj") + " = ? LIMIT 1";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, dataset);
                statement.setString(2, tid);
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) return true;
                }
            }
        }
        return false;
    }

    private static StoredObject findReference(Database db, DbMapping mapping, Column column,
                                              String tid, Optional<String> bid) throws SQLException {
        var declared = column.target().orElseThrow();
        StoredObject result = null;
        for (var target : mapping.referenceTables(declared)) {
            var match = findReference(db, mapping, declared, target, tid, bid, column.slot().name().declared().toString());
            if (match == null) continue;
            if (result != null) throw ambiguousTarget(tid, column.slot().name().declared().toString());
            result = match;
        }
        return result;
    }

    private static StoredObject findReference(Database db, DbMapping mapping, SymbolRef declared, Table target,
                                              String tid, Optional<String> bid, String context) throws SQLException {
        var root = mapping.rootTable(target.object().symbol());
        String discriminator = root.hasDiscriminator() ? ", ref." + T_TYPE : "";
        String fragment = target.equals(root) ? "" : " AND EXISTS (SELECT 1 FROM " + db.table(target.name())
                + " leaf WHERE leaf." + T_ID + " = ref." + T_ID + " AND leaf." + T_BASKET + " = ref." + T_BASKET + ")";
        String sql = "SELECT ref." + T_ID + ", basket." + T_ILI_TID + discriminator
                + " FROM " + db.table(root.name()) + " ref JOIN " + db.technicalTable(BASKET)
                + " basket ON basket." + T_ID + " = ref." + T_BASKET + " WHERE basket.topic IN " + topicNames(mapping, declared)
                + " AND " + tidExpression(root, "ref") + " = ?" + (bid.isPresent() ? " AND basket." + T_ILI_TID + " = ?" : "") + fragment;
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, tid);
            if (bid.isPresent()) statement.setString(2, bid.orElseThrow());
            try (var rows = statement.executeQuery()) {
                StoredObject result = null;
                while (rows.next()) {
                    long id = rows.getLong(1);
                    var type = root.hasDiscriminator() ? mapping.concreteType(root, rows.getString(3), id).object().symbol()
                            : root.object().symbol();
                    if (!mapping.isAssignableTo(type, declared)) continue;
                    if (result != null) throw ambiguousTarget(tid, context);
                    var basket = new BasketId(identifier(rows.getString(2), "reference target BID", context), IdOrigin.SOURCE);
                    result = new StoredObject(id, type, basket);
                }
                return result;
            }
        }
    }

    private static DbException ambiguousTarget(String tid, String context) {
        return new DbException("Ambiguous stored reference target " + tid + " at " + context);
    }

    private static String topicNames(DbMapping mapping, Table table) {
        return topicNames(mapping, table.object().symbol());
    }

    private static String topicNames(DbMapping mapping, SymbolRef type) {
        var object = mapping.plan().object(type).orElseThrow();
        return mapping.storedTopicNames(object.topic().orElseThrow()).stream()
                .map(name -> "'" + name.replace("'", "''") + "'").collect(Collectors.joining(", ", "(", ")"));
    }

    static String tidExpression(Table table, String alias) {
        String column = alias + "." + T_ILI_TID;
        return table.uuidIdentity() ? "lower(CAST(" + column + " AS text))" : column;
    }

    private static int projectionSize(DbMapping mapping, Table table) {
        return table.columns().stream().mapToInt(column -> projectionSize(mapping, column)).sum()
                + 2 * table.secondaryGeometries().size();
    }

    private static int projectionSize(DbMapping mapping, Column column) {
        if (column.array().flatMap(DbArray::target).isPresent()) return 2;
        if (column.kind() != DbMapping.Kind.REFERENCE) return column.physicalWidth();
        int targetFields = mapping.referenceRoot(column).hasDiscriminator() ? 3 : 2;
        return targetFields + (projectsTargetBid(mapping, column) ? 1 : 0)
                + (column.positionColumn().isPresent() ? 1 : 0);
    }

    private static boolean projectsTargetBid(DbMapping mapping, Column column) {
        return mapping.version() == LanguageVersion.ILI23 && column.external();
    }

    private static String linkOwnerColumn(Table table, EmbeddedAssociation association) {
        return quote(table.column(association.ownerRole().slot().symbol()).name());
    }

    private static Map<SymbolRef, TransferValue> readFields(Database db, DbMapping mapping, ResultSet rows,
                                                            int index, Table table, String ownerTid) throws SQLException {
        return readFields(db, mapping, rows, index, table, ownerTid, ALL_GEOMETRIES);
    }

    private static Map<SymbolRef, TransferValue> readFields(Database db, DbMapping mapping, ResultSet rows,
                                                            int index, Table table, String ownerTid,
                                                            GeometrySelection selection) throws SQLException {
        return readFields(db, mapping, rows, index, table, ownerTid, selection, table.object().symbol());
    }

    private static Map<SymbolRef, TransferValue> readFields(Database db, DbMapping mapping, ResultSet rows,
                                                            int index, Table table, String ownerTid,
                                                            GeometrySelection selection, SymbolRef actual) throws SQLException {
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        for (var column : table.columns()) {
            if (!mapping.applies(column, actual)) {
                int width = column.physicalWidth();
                for (int offset = 0; offset < width; offset++) {
                    if (rows.getObject(index + offset) != null) {
                        throw new DbException("Column " + column.name() + " does not apply to object " + ownerTid);
                    }
                }
                index += projectionSize(mapping, column);
                continue;
            }
            var value = checkGeometrySelection(rows, index, column, ownerTid, selection)
                    ? readValue(db, mapping, rows, index, column, ownerTid) : null;
            if (value != null && values.putIfAbsent(column.slot().symbol(), value) != null) {
                throw new DbException("Multiple stored values for " + column.slot().name().declared() + " of object " + ownerTid);
            }
            index += projectionSize(mapping, column);
        }
        if (table.object().kind() == SymbolKind.ASSOCIATION) {
            for (var column : table.columns()) {
                if (column.slot() instanceof RoleSlot && !values.containsKey(column.slot().symbol())) {
                    throw new DbException("Missing link endpoint at " + column.slot().name().declared());
                }
            }
        }
        for (var geometry : table.secondaryGeometries()) {
            boolean sameBasket = rows.getBoolean(index++);
            var column = geometry.column();
            if (!mapping.applies(column, actual)) {
                if (!rows.wasNull()) {
                    throw new DbException("Unexpected secondary geometry row for " + column.slot().name().declared()
                            + " of object " + ownerTid);
                }
                index++;
                continue;
            }
            if (!sameBasket) {
                String problem = rows.wasNull() ? "Missing secondary geometry row" : "Secondary geometry belongs to a different basket";
                throw new DbException(problem + " for " + geometry.column().slot().name().declared()
                        + " of object " + ownerTid);
            }
            if (checkGeometrySelection(rows, index, column, ownerTid, selection)) {
                var value = readGeometry(db, rows, index, column);
                if (value != null) values.put(column.slot().symbol(), value);
            }
            index++;
        }
        return values;
    }

    private static boolean checkGeometrySelection(ResultSet rows, int index, Column column, String ownerTid,
                                                  GeometrySelection selection) throws SQLException {
        if (selection.active(column)) return true;
        if (rows.getBytes(index) != null) {
            throw new DbException("Value in inactive EPSG:" + column.epsgCode() + " column at "
                    + column.slot().name().declared() + " of object " + ownerTid);
        }
        return false;
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
                    + " OR lb.topic NOT IN " + topicNames(mapping, parent) + " THEN 1 ELSE 0 END) > 0 LIMIT 1";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, datasetId);
                statement.setLong(2, datasetId);
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
            if (!table.hasSequenceColumn()) continue;
            token.throwIfCancellationRequested();
            var ownerCount = new StringJoiner(" + ").setEmptyValue("0");
            for (var composition : mapping.compositionsForChild(table.object().symbol())) {
                ownerCount.add("CASE WHEN child." + quote(composition.ownerColumn()) + " IS NULL THEN 0 ELSE 1 END");
            }
            String wrongTopic = table.object().topic().isPresent() ? " OR basket.topic NOT IN " + topicNames(mapping, table) : "";
            String sql = "SELECT child." + T_ID + " FROM " + db.table(table.name()) + " child JOIN "
                    + db.technicalTable(BASKET) + " basket ON basket." + T_ID + " = child." + T_BASKET
                    + " WHERE basket.dataset = ? AND ((" + ownerCount + ") <> 1" + wrongTopic + ") LIMIT 1";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, datasetId);
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        throw new DbException("Expected exactly one structure owner in the correct topic for "
                                + table.name() + " T_Id " + rows.getLong(1));
                    }
                }
            }
        }
        // Nested lookups cannot reach orphans. Check the actual SQL owner, including inherited fields.
        for (var composition : mapping.compositions()) {
            var parent = mapping.table(composition.parent());
            if (mapping.profile() == DbInheritance.Profile.NEW_CLASS && parent.object().kind() != SymbolKind.STRUCTURE
                    && !parent.hasDiscriminator() && !parent.subtype()) continue;
            token.throwIfCancellationRequested();
            var child = mapping.table(composition.childRoot());
            String owner = "child." + quote(composition.ownerColumn());
            String basket = db.technicalTable(BASKET);
            String sql = "SELECT parent." + T_ID + " FROM " + db.table(child.name()) + " child LEFT JOIN "
                    + db.table(parent.name()) + " parent ON parent." + T_ID + " = " + owner
                    + " LEFT JOIN " + basket + " cb ON cb." + T_ID + " = child." + T_BASKET
                    + " LEFT JOIN " + basket + " pb ON pb." + T_ID + " = parent." + T_BASKET
                    + " WHERE " + owner + " IS NOT NULL AND (cb.dataset = ? OR pb.dataset = ?)"
                    + " AND (parent." + T_ID + " IS NULL OR pb." + T_ID + " IS NULL OR cb." + T_ID
                    + " IS NULL OR parent." + T_BASKET + " <> child." + T_BASKET + ") LIMIT 1";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, datasetId);
                statement.setLong(2, datasetId);
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        String problem = rows.getObject(1) == null ? "Orphan structure row"
                                : "Structure row and owner are in different baskets";
                        throw new DbException(problem + " at " + composition.slot().name().declared());
                    }
                }
            }
        }
    }

    private static final class StructureRows implements AutoCloseable {
        private final Database db;
        private final DbMapping mapping;
        private final long basketId;
        private final CancellationToken token;
        private final List<PreparedStatement> statements = new ArrayList<>();
        private final List<StructureCursor> cursors = new ArrayList<>();
        private final Map<Composition, PreparedStatement> nestedStatements = new HashMap<>();
        private final Map<Table, PreparedStatement> subtypeStatements = new HashMap<>();

        StructureRows(Database db, DbMapping mapping, long basketId, CancellationToken token) {
            this.db = db;
            this.mapping = mapping;
            this.basketId = basketId;
            this.token = token;
        }

        void open(Composition composition) throws SQLException {
            token.throwIfCancellationRequested();
            var statement = prepare(composition, true);
            statement.setLong(1, basketId);
            statement.setLong(2, basketId);
            cursors.add(new StructureCursor(composition, statement.executeQuery()));
        }

        private PreparedStatement prepare(Composition composition, boolean streaming) throws SQLException {
            var child = mapping.table(composition.childRoot());
            String owner = "child." + quote(composition.ownerColumn());
            String sequence = "child." + T_SEQ;
            String basket = "child." + T_BASKET;
            String id = "child." + T_ID;
            var fields = new StringJoiner(", ").add(owner).add(sequence).add(basket).add(id);
            if (child.hasDiscriminator()) fields.add("child." + T_TYPE);
            var from = new StringBuilder(db.table(child.name())).append(" child");
            String where;
            if (streaming) {
                var parent = mapping.table(composition.parent());
                from.append(" LEFT JOIN ").append(db.table(parent.name())).append(" parent ON parent.")
                        .append(T_ID).append(" = ").append(owner);
                where = owner + " IS NOT NULL AND (" + basket + " = ? OR parent." + T_BASKET + " = ?)";
            } else {
                where = owner + " = ? AND " + basket + " = ?";
            }
            appendColumns(db, mapping, child, "child", fields, from);
            String sql = "SELECT " + fields + " FROM " + from + " WHERE " + where
                    + " ORDER BY " + owner + ", " + sequence + ", " + id;
            var statement = db.connection().prepareStatement(sql);
            statements.add(statement);
            fetchInPages(db, statement);
            return statement;
        }

        void readFor(long parentId, String parentTid, Map<SymbolRef, TransferValue> values, SymbolRef actual) throws SQLException {
            for (var cursor : cursors) {
                if (cursor.available && cursor.ownerId == parentId && !mapping.applies(cursor.composition, actual)) {
                    throw cursor.problem("Structure field does not apply to object " + parentTid);
                }
                read(cursor, parentId, parentTid, 1, values);
            }
        }

        private void readNested(SymbolRef parent, long parentId, String parentTid, int depth,
                                Map<SymbolRef, TransferValue> values) throws SQLException {
            for (var composition : mapping.compositionsForObject(parent)) {
                token.throwIfCancellationRequested();
                var statement = nestedStatements.get(composition);
                if (statement == null) {
                    statement = prepare(composition, false);
                    nestedStatements.put(composition, statement);
                }
                statement.setLong(1, parentId);
                statement.setLong(2, basketId);
                try (var rows = statement.executeQuery()) {
                    read(new StructureCursor(composition, rows), parentId, parentTid, depth, values);
                }
            }
        }

        private void read(StructureCursor cursor, long parentId, String parentTid, int depth,
                          Map<SymbolRef, TransferValue> values) throws SQLException {
            var value = cursor.readFor(parentId, parentTid, depth);
            if (value != null && values.putIfAbsent(cursor.composition.slot().symbol(), value) != null) {
                throw cursor.problem("Multiple stored structure values for object " + parentTid);
            }
        }

        private void readSubtype(Table table, long id, String parentTid, Map<SymbolRef, TransferValue> values, SymbolRef actual)
                throws SQLException {
            token.throwIfCancellationRequested();
            var statement = subtypeStatements.get(table);
            if (statement == null) {
                var fields = new StringJoiner(", ").add("part." + T_ID);
                var from = new StringBuilder(db.table(table.name())).append(" part");
                appendColumns(db, mapping, table, "part", fields, from);
                statement = db.connection().prepareStatement("SELECT " + fields + " FROM " + from
                        + " WHERE part." + T_ID + " = ? AND part." + T_BASKET + " = ?");
                statements.add(statement);
                subtypeStatements.put(table, statement);
            }
            statement.setLong(1, id);
            statement.setLong(2, basketId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new DbException("Missing inheritance fragment in " + table.name() + " for T_Id " + id);
                }
                values.putAll(readFields(db, mapping, rows, 2, table, parentTid, ALL_GEOMETRIES, actual));
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
            private final ResultSet rows;
            private boolean available;
            private long ownerId;

            StructureCursor(Composition composition, ResultSet rows) throws SQLException {
                this.child = mapping.table(composition.childRoot());
                this.composition = composition;
                this.rows = rows;
                advance();
            }

            TransferValue readFor(long parentId, String parentTid, int depth) throws SQLException {
                if (!available || ownerId > parentId) {
                    return null;
                }
                if (ownerId < parentId) {
                    throw problem("Orphan structure row");
                }
                if (depth > MAX_STRUCTURE_DEPTH) {
                    throw problem("Structure exceeds the supported XTF depth");
                }
                var collectionKind = composition.collectionKind();
                if (collectionKind.isEmpty()) {
                    var value = readChild(parentTid, depth);
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
                    items.add(readChild(parentTid, depth));
                    previous = sequence;
                } while (available && ownerId == parentId);
                return new Repeated(collectionKind.orElseThrow(), items);
            }

            private Structured readChild(String parentTid, int depth) throws SQLException {
                long id = integer(4, T_ID);
                var actual = child.hasDiscriminator() ? mapping.concreteType(child, rows.getString(5), id)
                        : mapping.storage(child.object().symbol());
                if (!mapping.isAssignableTo(actual.object().symbol(), composition.child())) {
                    throw problem("Unexpected structure type " + actual.discriminator());
                }
                var values = readFields(db, mapping, rows, child.hasDiscriminator() ? 6 : 5, child, parentTid,
                        ALL_GEOMETRIES, actual.object().symbol());
                for (var part : actual.rows()) {
                    if (part.subtype()) readSubtype(part, id, parentTid, values, actual.object().symbol());
                }
                readNested(actual.object().symbol(), id, parentTid, depth + 1, values);
                advance();
                return new Structured(actual.object().symbol(), values);
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

    private static TransferValue readValue(Database db, DbMapping mapping, ResultSet rows, int index,
                                           Column column, String ownerTid) throws SQLException {
        if (column.expandedStructure().isPresent()) {
            return readExpanded(db, rows, index, column.expandedStructure().orElseThrow(), ownerTid);
        }
        if (column.jsonStructure().isPresent()) {
            return column.jsonStructure().orElseThrow().decode(rows.getString(index), column.slot().name().declared() + " of object " + ownerTid);
        }
        if (column.array().isPresent()) {
            var array = column.array().orElseThrow();
            String context = column.slot().name().declared() + " of object " + ownerTid;
            var values = db.readArray(rows, index, array, context);
            if (array.target().isPresent()) {
                values = readArrayReferences(db, mapping, mapping.table(array.target().orElseThrow()),
                        rows.getLong(index + 1), values, context);
            }
            return array.assemble(values, context);
        }
        return switch (column.kind()) {
            case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE -> readGeometry(db, rows, index, column);
            case BOOLEAN, INTEGER, BIGINT, DECIMAL, DATE, TIME, DATETIME, FORMATTED, ENUMERATION -> readScalar(db, rows, index, column);
            case TEXT -> {
                if (column.textExpansion().isPresent()) {
                    var expansion = column.textExpansion().orElseThrow();
                    var values = new String[expansion.width()];
                    for (int offset = 0; offset < values.length; offset++) values[offset] = rows.getString(index + offset);
                    yield expansion.assemble(values);
                }
                yield readScalar(db, rows, index, column);
            }
            case REFERENCE -> {
                String path = column.slot().name().declared().toString();
                String context = ownerTid == null ? path : path + " of object " + ownerTid;
                boolean hasTarget = rows.getObject(index) != null;
                var position = column.positionColumn().isPresent()
                        ? readPosition(rows.getObject(index + projectionSize(mapping, column) - 1), hasTarget, context)
                        : Optional.<BigInteger>empty();
                if (!hasTarget) yield null;
                String tid = identifier(rows.getString(index + 1), column.external()
                        ? "same-dataset reference target TID" : "same-basket reference target TID", context);
                SymbolRef declared = column.target().orElseThrow();
                Table root = mapping.referenceRoot(column);
                int next = index + 2;
                if (root.hasDiscriminator()) {
                    var actual = mapping.concreteType(root, rows.getString(next++), rows.getLong(index));
                    if (!mapping.isAssignableTo(actual.object().symbol(), declared)) {
                        throw new DbException("Unexpected reference type " + actual.discriminator() + " at " + context);
                    }
                }
                var bid = projectsTargetBid(mapping, column) ? Optional.ofNullable(rows.getString(next))
                        .map(value -> new BasketId(identifier(value, "reference target BID", context), IdOrigin.SOURCE))
                        : Optional.<BasketId>empty();
                String value = storedTid(root, tid);
                TransferIdentity.ObjectKey key = hasWireOid(mapping, root) ? new ObjectOid(value) : new TransferId(value, IdOrigin.SOURCE);
                var reference = new Reference(key, bid, column.external(), position);
                var catalogue = column.catalogueReference();
                yield catalogue.<TransferValue>map(binding ->
                        new Structured(binding.structure(), Map.of(binding.field().symbol(), reference))).orElse(reference);
            }
        };
    }

    private static TransferValue readExpanded(Database db, ResultSet rows, int index, DbExpandedStructure expanded,
                                               String ownerTid) throws SQLException {
        var items = new ArrayList<TransferValue>();
        boolean gap = false;
        for (var group : expanded.groups()) {
            String type = rows.getString(index++);
            String context = group.path() + " of object " + ownerTid;
            if (type != null && !expanded.typeName().equals(type)) {
                throw new DbException("Unexpected expanded structure type " + type + " at " + context);
            }
            var values = new LinkedHashMap<SymbolRef, TransferValue>();
            for (var field : group.fields()) {
                var value = field.expandedStructure().isPresent()
                        ? readExpanded(db, rows, index, field.expandedStructure().orElseThrow(), ownerTid)
                        : readScalar(db, rows, index, field);
                index += field.physicalWidth();
                if (value != null) values.put(field.slot().symbol(), value);
            }
            if (type == null) {
                if (!values.isEmpty()) throw new DbException("Missing expanded structure type at " + context);
                gap = true;
            } else {
                if (gap) throw new DbException("Missing earlier expanded structure position at " + context);
                items.add(new Structured(expanded.structure(), values));
            }
        }
        if (items.isEmpty()) return null;
        return expanded.collection().<TransferValue>map(kind -> new Repeated(kind, items)).orElseGet(items::getFirst);
    }

    private static TransferValue readScalar(Database db, ResultSet rows, int index, Column column) throws SQLException {
        return switch (column.kind()) {
            case BOOLEAN -> readBoolean(db, rows, index, column);
            case INTEGER -> readInteger(rows.getString(index), column);
            case BIGINT -> readBigInt(rows.getString(index), column.slot().name().declared().toString());
            case DECIMAL -> db.readDecimal(rows, index, column.slot().name().declared().toString());
            case DATE -> db.readDate(rows, index, column.slot().name().declared().toString());
            case TIME -> db.readTime(rows, index, column.slot().name().declared().toString());
            case DATETIME -> db.readTimestamp(rows, index, column.slot().name().declared().toString());
            case ENUMERATION -> {
                String value = rows.getString(index);
                yield column.enumeration().isPresent()
                        ? column.enumeration().orElseThrow().read(value, column.slot().name().declared().toString())
                        : readEnumeration(value, column);
            }
            case TEXT, FORMATTED -> {
                String value = rows.getString(index);
                yield value == null ? null : new Text(value);
            }
            default -> throw new IllegalArgumentException("Expected a scalar column");
        };
    }

    private static TransferValue readGeometry(Database db, ResultSet rows, int index, Column column) throws SQLException {
        var stored = db.readGeometry(rows, index, column);
        return column.wrappedGeometry().isPresent() ? column.wrappedGeometry().orElseThrow().wrap(stored) : stored;
    }

    private static TransferGeometry storedGeometry(Column column, TransferValue value, String context) {
        if (column.wrappedGeometry().isPresent()) {
            return column.wrappedGeometry().orElseThrow().unwrap(value, column.slot().name().declared() + " of object " + context);
        }
        return (TransferGeometry) value;
    }

    private static Optional<BigInteger> readPosition(Object value, boolean hasTarget, String context) {
        if (value == null) return Optional.empty();
        if (!hasTarget) throw new DbException("Role position without a reference at " + context);
        if (value instanceof Long || value instanceof Integer || value instanceof Short) {
            long position = ((Number) value).longValue();
            if (position > 0) return Optional.of(BigInteger.valueOf(position));
        }
        throw new DbException("Expected a positive integer role position at " + context);
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

    static NumberValue readInteger(String value, Column column) {
        return readInteger(value, column.slot().name().declared().toString());
    }

    static NumberValue readInteger(String value, String context) {
        if (value == null) {
            return null;
        }
        try {
            int integer = new BigDecimal(value).intValueExact();
            return new NumberValue(Integer.toString(integer), BigDecimal.valueOf(integer));
        } catch (NumberFormatException | ArithmeticException e) {
            throw new DbException("Invalid 32-bit integer in " + context, e);
        }
    }

    private static NumberValue readBigInt(String value, String context) {
        if (value == null) return null;
        try {
            long integer = new BigDecimal(value).longValueExact();
            return new NumberValue(Long.toString(integer), BigDecimal.valueOf(integer));
        } catch (NumberFormatException | ArithmeticException failure) {
            throw new DbException("Invalid 64-bit integer in " + context, failure);
        }
    }

    private static long bigIntValue(NumberValue value, String context) {
        try {
            return value.exactValue().longValueExact();
        } catch (ArithmeticException failure) {
            throw new DbException("Value exceeds the 64-bit integer encoding of " + context, failure);
        }
    }

    static int integerValue(NumberValue value, String context) {
        try {
            return value.exactValue().intValueExact();
        } catch (ArithmeticException failure) {
            throw new DbException("Value exceeds the 32-bit integer encoding of " + context, failure);
        }
    }

    static Enumeration readEnumeration(String value, Column column) {
        return readEnumeration(value, column.slot().name().declared().toString());
    }

    static Enumeration readEnumeration(String value, String context) {
        if (value == null) {
            return null;
        }
        try {
            return new Enumeration(List.of(value.split("\\.", -1)));
        } catch (IllegalArgumentException e) {
            throw new DbException("Invalid enumeration encoding in " + context, e);
        }
    }

    enum WriteMode { IMPORT, REPLACE, UPDATE }

    static void importData(Database db, DbMapping mapping, String dataset,
                           TransferReader reader, WriteMode mode, CancellationToken token) throws SQLException {
        token.throwIfCancellationRequested();
        if (mode != WriteMode.IMPORT) DbDatasetWrite.lock(db, mapping);
        var existing = DbDatasetWrite.findDataset(db, dataset);
        if (mode == WriteMode.IMPORT && existing.isPresent()) {
            throw new DbException("Dataset already exists: " + dataset);
        }
        try (var importer = new Import(db, mapping, mode == WriteMode.UPDATE, token)) {
            long datasetId = 0;
            boolean sawBasket = false;
            for (var next = reader.readNext(token); next.isPresent(); next = reader.readNext(token)) {
                switch (next.orElseThrow()) {
                    case TransferEvent.StartTransfer start -> {
                        var models = mapping.plan().modelImportClosure(Set.copyOf(mapping.models()));
                        if (start.models().isEmpty() || start.models().stream().anyMatch(model -> !models.contains(model.model()))
                                || !reader.stableIdentityNamespaces().isEmpty()) {
                            throw new DbException("Expected the selected model and transfer-scoped identities in XTF");
                        }
                        if (existing.isPresent()) {
                            datasetId = existing.orElseThrow();
                            if (mode == WriteMode.REPLACE) DbDatasetWrite.clearDataset(db, mapping, datasetId, "replace", token);
                        } else {
                            datasetId = importer.insertDataset(dataset);
                        }
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
            importer.finishTransfer();
        }
    }

    private static List<Reference> readArrayReferences(Database db, DbMapping mapping, Table target, long basket,
                                                       List<?> values, String context) throws SQLException {
        var distinct = new HashSet<Long>();
        for (var value : values) {
            if (!(value instanceof Long id)) throw new DbException("Missing or invalid array reference ID at " + context);
            distinct.add(id);
        }
        var ids = new ArrayList<>(distinct);
        var references = new HashMap<Long, Reference>();
        final int batchSize = 256;
        for (int offset = 0; offset < ids.size(); offset += batchSize) {
            int size = Math.min(batchSize, ids.size() - offset);
            String sql = "SELECT " + T_ID + ", " + T_ILI_TID + " FROM " + db.table(target.name())
                    + " WHERE " + T_BASKET + " = ? AND " + T_ID + " IN ("
                    + String.join(", ", Collections.nCopies(size, "?")) + ")";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, basket);
                for (int i = 0; i < size; i++) statement.setLong(i + 2, ids.get(offset + i));
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String tid = storedTid(target, identifier(rows.getString(2), "array reference target TID", context));
                        TransferIdentity.ObjectKey key = hasWireOid(mapping, target)
                                ? new ObjectOid(tid) : new TransferId(tid, IdOrigin.SOURCE);
                        references.put(rows.getLong(1), new Reference(key, Optional.empty(), false, Optional.empty()));
                    }
                }
            }
        }
        var result = new ArrayList<Reference>(values.size());
        for (var id : values) {
            var reference = references.get(id);
            if (reference == null) throw new DbException("Missing same-basket array target T_Id " + id + " at " + context);
            result.add(reference);
        }
        return result;
    }

    private static TransferIdentity objectIdentity(DbMapping mapping, Table root, BasketId bid, String tid) {
        var value = Optional.ofNullable(tid).map(id -> storedTid(root, id));
        return new TransferIdentity(bid, Optional.empty(), value.map(id -> new TransferId(id, IdOrigin.SOURCE)),
                hasWireOid(mapping, root) ? value.map(ObjectOid::new) : Optional.empty(), Optional.empty());
    }

    private static boolean hasWireOid(DbMapping mapping, Table root) {
        return mapping.version() == LanguageVersion.ILI24 && root.oidDomain().isPresent();
    }

    private static Optional<String> basketOid(DbMapping mapping, TopicDescriptor topic, BasketId bid) {
        if (topic.oidRules().stream().noneMatch(rule -> rule.basket())) return Optional.empty();
        canonicalUuid(bid.value(), topic.name().declared().toString());
        return mapping.version() == LanguageVersion.ILI24 ? Optional.of(bid.value()) : Optional.empty();
    }

    static String storedTid(Table table, String tid) {
        return table.uuidIdentity() ? canonicalUuid(tid, table.object().name().declared().toString()) : tid;
    }

    private static String canonicalUuid(String tid, String context) {
        try {
            String canonical = UUID.fromString(tid).toString();
            if (canonical.equalsIgnoreCase(tid)) return canonical;
        } catch (IllegalArgumentException ignored) {
            // UUID.fromString also accepts abbreviated segments, which are not the ili2db wire form.
        }
        throw new DbException("Invalid UUID " + tid + " for " + context);
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
        private final boolean updating;
        private final Map<String, PreparedStatement> inserts = new HashMap<>();
        private final Map<String, PreparedStatement> objectUpdates = new HashMap<>();
        private final Map<Composition, PreparedStatement> structureInserts = new HashMap<>();
        private final Map<ReferenceColumn, PreparedStatement> updates = new HashMap<>();
        private final Set<SymbolRef> referenceTargets;
        private final Set<SymbolRef> externalTargets;
        private final Map<String, StoredObject> objects = new HashMap<>();
        private final Optional<Set<String>> storedTids;
        private final Map<String, StoredObject> externalObjects = new HashMap<>();
        private final List<Pending> pending = new ArrayList<>();
        private final List<PendingArray> pendingArrays = new ArrayList<>();
        private final List<Pending> externalPending = new ArrayList<>();
        private final List<DbDatasetWrite.ExistingObject> removedObjects = new ArrayList<>();
        private Map<DbDatasetWrite.ObjectKey, DbDatasetWrite.ExistingObject> existingObjects = Map.of();
        private long datasetId;
        private long basketId;
        private GeometrySelection basketGeometry = new GeometrySelection(List.of(), Map.of());

        Import(Database db, DbMapping mapping, boolean updating, CancellationToken token) {
            this.db = db;
            this.mapping = mapping;
            this.token = token;
            this.updating = updating;
            referenceTargets = referenceTargets(false);
            externalTargets = referenceTargets(true);
            storedTids = mapping.tables().stream().anyMatch(Table::uuidIdentity)
                    ? Optional.of(new HashSet<>()) : Optional.empty();
        }

        private Set<SymbolRef> referenceTargets(boolean external) {
            var declaredTargets = mapping.tables().stream().flatMap(table -> table.columns().stream())
                    .filter(column -> column.external() == external)
                    .flatMap(column -> column.target().or(() -> column.array().flatMap(DbArray::target)).stream())
                    .collect(Collectors.toSet());
            return mapping.objectStorage().stream().filter(storage -> storage.object().transferable())
                    .map(storage -> storage.object().symbol())
                    .filter(type -> declaredTargets.stream().anyMatch(declared -> mapping.isAssignableTo(type, declared)))
                    .collect(Collectors.toSet());
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
            if (!mapping.models().contains(topic.model())
                    || start.kind() != BasketKind.FULL || start.consistency() != Consistency.COMPLETE
                    || !start.additionalTopics().isEmpty()
                    || start.startState().isPresent() || start.endState().isPresent()
                    || start.id().origin() != IdOrigin.SOURCE
                    || !start.stableOid().equals(basketOid(mapping, topic, start.id()))) {
                throw new DbException("Unsupported basket metadata for " + start.id().value());
            }
            var domainsForBasket = DbDomains.resolve(mapping.plan(), topic, start.domainBindings(), start.id().value());
            basketGeometry = new GeometrySelection(domainsForBasket,
                    mapping.crsPair().map(pair -> pair.epsgCodes(topic.symbol())).orElse(Map.of()));
            String domains = DbDomains.format(mapping.plan(), start.domainBindings(), start.id().value());
            this.datasetId = datasetId;
            if (updating) {
                var stored = DbDatasetWrite.findBasket(db, mapping, datasetId, topic, start.id().value(), domainsForBasket);
                if (stored.isPresent()) {
                    basketId = stored.getAsLong();
                    existingObjects = DbDatasetWrite.readObjects(db, mapping, topic, basketId, token);
                    DbDatasetWrite.clearBasketPayload(db, mapping, basketId, token);
                    return;
                }
                existingObjects = new HashMap<>();
            }
            basketId = db.nextId();
            String sql = "INSERT INTO " + db.technicalTable(BASKET)
                    + " (" + T_ID + ", topic, " + T_ILI_TID
                    + ", attachmentkey, dataset, domains) VALUES (?, ?, ?, ?, ?, ?)";
            try (var statement = db.connection().prepareStatement(sql)) {
                statement.setLong(1, basketId);
                statement.setString(2, topic.name().declared().toString());
                statement.setString(3, start.id().value());
                statement.setString(4, Long.toString(basketId));
                statement.setLong(5, datasetId);
                statement.setString(6, domains);
                statement.executeUpdate();
            }
        }

        void insertObject(TransferEvent.ObjectEvent event) throws SQLException {
            var object = mapping.crsPair().map(pair -> pair.originalObject(event.object())).orElse(event.object());
            object = mapping.translations().toOriginal(object);
            var identity = object.identity();
            if (event.operation().isPresent() || event.consistency() != Consistency.COMPLETE
                    || identity.originalBasket().isPresent()) {
                throw new DbException("Unsupported object metadata for " + object.type());
            }
            var storage = mapping.storage(object.type());
            var table = storage.rows().getLast();
            if (mapping.isEmbeddedLink(table)) {
                throw new DbException("Expected an embedded association at " + table.object().name().declared());
            }
            var chain = storage.rows();
            var root = storage.root();
            boolean hasTid = root.hasTidColumn();
            var sourceTid = identity.tid().filter(value -> value.origin() == IdOrigin.SOURCE).map(TransferId::value);
            var expectedOid = hasWireOid(mapping, root)
                    ? sourceTid : Optional.<String>empty();
            if (!identity.oid().map(ObjectOid::value).equals(expectedOid)) {
                throw new DbException("Unsupported object identity for " + table.object().name().declared());
            }
            var tid = sourceTid.map(value -> storedTid(root, value));
            tid.ifPresent(value -> storedTids.ifPresent(seen -> {
                if (!seen.add(value)) throw new DbException("Duplicate stored object TID " + value);
            }));
            if (hasTid && tid.isEmpty()) {
                throw new DbException("Missing source TID");
            }
            if (!hasTid && identity.tid().isPresent()) {
                throw new DbException("Unexpected TID for link " + table.object().name().declared());
            }
            DbDatasetWrite.ExistingObject previous = updating && tid.isPresent()
                    ? existingObjects.remove(new DbDatasetWrite.ObjectKey(mapping.identityFamily(object.type()), tid.orElseThrow()))
                    : null;
            if (previous != null && !previous.type().equals(storage)) {
                throw new DbException("Cannot change concrete class for TID " + tid.orElseThrow() + " from "
                        + previous.type().object().name().declared() + " to " + storage.object().name().declared());
            }
            if (updating && previous == null && root.oidDomain().isPresent()) {
                DbDatasetWrite.checkStableObjectBasket(db, root, datasetId, basketId, tid.orElseThrow());
            }
            long id = previous == null ? db.nextId() : previous.id();
            String context = tid.orElseGet(() -> table.object().name().declared() + " T_Id " + id);
            int found = 0;
            for (var part : chain) {
                token.throwIfCancellationRequested();
                found += writeObjectRow(part, storage.discriminator(), object, id, context, tid, previous != null);
            }
            var compositions = mapping.compositionsForObject(object.type());
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
            insertCompositions(object.data(), compositions, id, context, identity.currentBasket());
            for (var association : links) {
                var value = object.value(association.role().symbol()).orElse(null);
                if (value != null && value != Undefined.INSTANCE) {
                    insertLink(association, id, context, identity.currentBasket(), value);
                }
            }
            if (referenceTargets.contains(object.type())) {
                objects.put(tid.orElseThrow(), new StoredObject(id, object.type(), identity.currentBasket()));
            }
            if (externalTargets.contains(object.type()) || (!externalTargets.isEmpty() && tid.isPresent())) {
                externalObjects.put(tid.orElseThrow(), new StoredObject(id, object.type(), identity.currentBasket()));
            }
        }

        private int writeObjectRow(Table table, String concreteName, TransferObject object, long id, String context,
                                   Optional<String> tid, boolean update) throws SQLException {
            boolean link = table.object().kind() == SymbolKind.ASSOCIATION;
            var statement = update ? objectUpdateStatement(table) : insertStatement(table);
            int index = 1;
            if (!update) statement.setLong(index++, id);
            statement.setLong(index++, basketId);
            if (table.hasDiscriminator()) {
                statement.setString(index++, concreteName);
            }
            if (table.hasTidColumn()) {
                if (table.uuidIdentity() && !db.gpkg()) {
                    statement.setObject(index++, UUID.fromString(tid.orElseThrow()));
                } else {
                    statement.setString(index++, tid.orElseThrow());
                }
            }
            var found = new HashSet<SymbolRef>();
            var extendedFields = mapping.extendedFields(object.type());
            for (var column : table.columns()) {
                var slot = column.slot().symbol();
                var value = basketGeometry.active(column) && mapping.applies(column, object.type())
                        ? object.value(extendedFields.getOrDefault(slot, slot))
                        : Optional.<TransferValue>empty();
                if (value.isPresent()) found.add(slot);
                var payload = value.filter(val -> val != Undefined.INSTANCE).orElse(null);
                if (link && column.slot() instanceof RoleSlot && !(payload instanceof Reference)) {
                    throw new DbException("Missing link endpoint at " + column.slot().name().declared());
                }
                if (!link && column.slot() instanceof RoleSlot role && payload instanceof Structured embedded) {
                    var reference = embedded.value(role.symbol()).orElse(null);
                    if (embedded.values().size() != 1 || !(reference instanceof Reference)
                            || role.role().owner().filter(embedded.type().symbol()::equals).isEmpty()) {
                        throw new DbException("Unsupported embedded association payload at " + role.name().declared());
                    }
                    payload = reference;
                }
                index = bindColumn(statement, index, table, column, id, context, object.identity().currentBasket(), payload);
            }
            if (update) {
                statement.setLong(index++, id);
                statement.setLong(index, basketId);
            }
            if (statement.executeUpdate() != 1) throw new DbException("Could not write object " + context + " in " + table.name());
            for (var geometry : table.secondaryGeometries()) {
                token.throwIfCancellationRequested();
                if (!mapping.applies(geometry.column(), object.type())) continue;
                var value = basketGeometry.active(geometry.column()) ? object.value(geometry.column().slot().symbol())
                        : Optional.<TransferValue>empty();
                if (value.isPresent()) found.add(geometry.column().slot().symbol());
                var stored = storedGeometry(geometry.column(), value.filter(item -> item != Undefined.INSTANCE).orElse(null), context);
                writeSecondaryGeometry(geometry, stored, id, context, update);
            }
            return found.size();
        }

        private void writeSecondaryGeometry(SecondaryGeometry geometry, TransferGeometry value, long id,
                                            String context, boolean update) throws SQLException {
            var statements = update ? objectUpdates : inserts;
            var statement = statements.get(geometry.table());
            if (statement == null) {
                String table = db.table(geometry.table());
                String field = quote(geometry.column().name());
                String parameter = db.geometryParameter();
                String sql = update
                        ? "UPDATE " + table + " SET " + field + " = " + parameter + " WHERE " + T_ID + " = ? AND " + T_BASKET + " = ?"
                        : "INSERT INTO " + table + " (" + field + ", " + T_ID + ", " + T_BASKET + ") VALUES (" + parameter + ", ?, ?)";
                statement = db.connection().prepareStatement(sql);
                statements.put(geometry.table(), statement);
            }
            int index = db.bindGeometry(statement, 1, geometry.table(), geometry.column(), value);
            statement.setLong(index++, id);
            statement.setLong(index, basketId);
            if (statement.executeUpdate() != 1) {
                throw new DbException("Missing or basket-mismatched secondary geometry row for "
                        + geometry.column().slot().name().declared() + " of object " + context);
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
            if (!(fields.get(role) instanceof Reference target)) {
                throw new DbException("Missing link endpoint at " + context);
            }
            long id = db.nextId();
            var statement = insertStatement(table);
            statement.setLong(1, id);
            statement.setLong(2, basketId);
            int index = 3;
            var found = new HashSet<SymbolRef>();
            for (var column : table.columns()) {
                var field = column.slot().symbol();
                if (field.equals(association.ownerRole().slot().symbol())) {
                    statement.setLong(index++, ownerId);
                    if (column.positionColumn().isPresent()) {
                        bindPosition(statement, index++, target.orderPosition(), column);
                    }
                } else {
                    var payload = fields.get(field);
                    if (payload != null) found.add(field);
                    if (field.equals(role) && association.ordered() && target.orderPosition().isPresent()) {
                        payload = withOrderPosition(target, Optional.empty());
                    }
                    index = bindColumn(statement, index, table, column, id, context, basket,
                            payload == Undefined.INSTANCE ? null : payload);
                }
            }
            if (found.size() != fields.size()) {
                throw new DbException("Embedded association contains a field without a stored mapping at " + context);
            }
            statement.executeUpdate();
        }

        private void insertCompositions(Structured parent, List<Composition> compositions, long ownerId,
                                        String ownerTid, BasketId basket) throws SQLException {
            for (var composition : compositions) {
                var value = parent.value(composition.slot().symbol()).orElse(null);
                if (value != null && value != Undefined.INSTANCE) {
                    insertStructures(composition, ownerId, ownerTid, basket, value);
                }
            }
        }

        private void insertStructures(Composition composition, long ownerId, String ownerTid, BasketId basket,
                                      TransferValue value) throws SQLException {
            var collectionKind = composition.collectionKind();
            if (collectionKind.isPresent()) {
                var kind = collectionKind.orElseThrow();
                if (!(value instanceof Repeated collection) || collection.kind() != kind) {
                    throw new DbException("Expected a structure " + kind + " at " + composition.slot().name().declared());
                }
                int sequence = 0;
                for (var child : collection.values()) {
                    insertStructure(composition, ownerId, ownerTid, basket, sequence++, child);
                }
            } else {
                insertStructure(composition, ownerId, ownerTid, basket, 0, value);
            }
        }

        private void insertStructure(Composition composition, long ownerId, String ownerTid, BasketId basket,
                                     int sequence, TransferValue value) throws SQLException {
            token.throwIfCancellationRequested();
            if (!(value instanceof Structured structure)) {
                throw new DbException("Cannot encode structure type at " + composition.slot().name().declared());
            }
            var actual = mapping.storage(structure.type());
            var chain = actual.rows();
            if (actual.object().modifiers().contains(Modifier.ABSTRACT)
                    || !mapping.isAssignableTo(actual.object().symbol(), composition.child())) {
                throw new DbException("Cannot encode structure type " + actual.discriminator() + " at "
                        + composition.slot().name().declared());
            }
            var table = actual.root();
            var statement = structureInserts.get(composition);
            if (statement == null) {
                statement = prepareWrite(table, composition, false);
                structureInserts.put(composition, statement);
            }
            long id = db.nextId();
            statement.setLong(1, id);
            statement.setLong(2, basketId);
            int index = 3;
            if (table.hasDiscriminator()) statement.setString(index++, actual.discriminator());
            statement.setNull(index++, Types.VARCHAR);
            statement.setInt(index++, sequence);
            statement.setLong(index++, ownerId);
            int found = bindStructureFields(statement, index, table, structure, id, ownerTid, basket);
            statement.executeUpdate();
            for (var part : chain) {
                if (!part.subtype()) continue;
                token.throwIfCancellationRequested();
                var fragment = insertStatement(part);
                fragment.setLong(1, id);
                fragment.setLong(2, basketId);
                found += bindStructureFields(fragment, 3, part, structure, id, ownerTid, basket);
                fragment.executeUpdate();
            }
            var compositions = mapping.compositionsForObject(structure.type());
            for (var nested : compositions) {
                if (structure.value(nested.slot().symbol()).isPresent()) found++;
            }
            if (found != structure.values().size()) {
                throw new DbException("Structure contains a field without a stored mapping at "
                        + composition.slot().name().declared());
            }
            insertCompositions(structure, compositions, id, ownerTid, basket);
        }

        private int bindStructureFields(PreparedStatement statement, int index, Table table, Structured structure,
                                        long id, String ownerTid, BasketId basket) throws SQLException {
            var found = new HashSet<SymbolRef>();
            for (var column : table.columns()) {
                var field = mapping.applies(column, structure.type())
                        ? structure.value(column.slot().symbol()) : Optional.<TransferValue>empty();
                if (field.isPresent()) found.add(column.slot().symbol());
                var payload = field.filter(item -> item != Undefined.INSTANCE).orElse(null);
                index = bindColumn(statement, index, table, column, id, ownerTid, basket, payload);
            }
            return found.size();
        }

        private PreparedStatement insertStatement(Table table) throws SQLException {
            var statement = inserts.get(table.name());
            if (statement == null) {
                statement = prepareWrite(table, null, false);
                inserts.put(table.name(), statement);
            }
            return statement;
        }

        private PreparedStatement objectUpdateStatement(Table table) throws SQLException {
            var statement = objectUpdates.get(table.name());
            if (statement == null) {
                statement = prepareWrite(table, null, true);
                objectUpdates.put(table.name(), statement);
            }
            return statement;
        }

        private PreparedStatement prepareWrite(Table table, Composition composition, boolean update) throws SQLException {
            var names = new ArrayList<String>();
            var values = new ArrayList<String>();
            if (!update) {
                names.add(T_ID);
                values.add("?");
            }
            names.add(T_BASKET);
            values.add("?");
            if (table.hasDiscriminator()) {
                names.add(T_TYPE);
                values.add("?");
            }
            if (table.hasTidColumn()) {
                names.add(T_ILI_TID);
                values.add("?");
            }
            if (composition != null) {
                names.add(T_SEQ);
                names.add(quote(composition.ownerColumn()));
                values.add("?");
                values.add("?");
            }
            for (var column : table.columns()) {
                for (var name : column.physicalNames()) {
                    names.add(quote(name));
                    values.add(column.kind().geometry() ? db.geometryParameter()
                            : column.jsonStructure().isPresent() ? db.jsonParameter() : "?");
                }
                column.positionColumn().ifPresent(position -> {
                    names.add(quote(position));
                    values.add("?");
                });
            }
            if (update) {
                var assignments = new StringJoiner(", ");
                for (int i = 0; i < names.size(); i++) assignments.add(names.get(i) + " = " + values.get(i));
                return db.connection().prepareStatement("UPDATE " + db.table(table.name()) + " SET " + assignments
                        + " WHERE " + T_ID + " = ? AND " + T_BASKET + " = ?");
            }
            return db.connection().prepareStatement("INSERT INTO " + db.table(table.name())
                    + " (" + String.join(", ", names) + ") VALUES (" + String.join(", ", values) + ")");
        }

        private int bindValue(PreparedStatement statement, int index, Table table, Column column, TransferValue value)
                throws SQLException {
            if (column.kind().geometry()) {
                return db.bindGeometry(statement, index, table.name(), column, (TransferGeometry) value);
            }
            return bindScalar(statement, index, column, value);
        }

        private int bindScalar(PreparedStatement statement, int index, Column column, TransferValue value)
                throws SQLException {
            int next = index + 1;
            if (column.enumText().isPresent()) statement.setString(next++, column.enumText().orElseThrow().label(value));
            if (value == null || value == Undefined.INSTANCE) {
                statement.setNull(index, Types.NULL);
                return next;
            }
            switch (column.kind()) {
                case TEXT, FORMATTED -> statement.setString(index, ((Text) value).value());
                case BOOLEAN -> statement.setBoolean(index, ((BooleanValue) value).value());
                case ENUMERATION -> {
                    var enumeration = (Enumeration) value;
                    if (column.enumeration().isPresent()) {
                        statement.setLong(index, column.enumeration().orElseThrow().number(enumeration,
                                column.slot().name().declared().toString()));
                    } else {
                        statement.setString(index, String.join(".", enumeration.path()));
                    }
                }
                case INTEGER -> statement.setInt(index, integerValue((NumberValue) value, column.slot().name().declared().toString()));
                case BIGINT -> statement.setLong(index, bigIntValue((NumberValue) value, column.slot().name().declared().toString()));
                case DECIMAL -> db.bindDecimal(statement, index, (NumberValue) value, column.slot().name().declared().toString());
                case DATE -> db.bindDate(statement, index, (Text) value, column.slot().name().declared().toString());
                case TIME -> db.bindTime(statement, index, (Text) value, column.slot().name().declared().toString());
                case DATETIME -> db.bindTimestamp(statement, index, (Text) value, column.slot().name().declared().toString());
                case POINT, POLYLINE, SURFACE, MULTIPOINT, MULTICURVE, MULTISURFACE, REFERENCE ->
                        throw new DbException("Unsupported value for " + column.slot().name().declared());
            }
            return next;
        }

        private int bindColumn(PreparedStatement statement, int index, Table table, Column column, long id,
                               String context, BasketId basket, TransferValue value) throws SQLException {
            if (column.kind().geometry()) value = storedGeometry(column, value, context);
            if (column.expandedStructure().isPresent()) {
                return bindExpanded(statement, index, column.expandedStructure().orElseThrow(), value,
                        column.slot().name().declared() + " of object " + context);
            }
            if (column.jsonStructure().isPresent()) {
                statement.setString(index, column.jsonStructure().orElseThrow().encode(value,
                        column.slot().name().declared() + " of object " + context));
                return index + 1;
            }
            if (column.array().isPresent()) {
                var array = column.array().orElseThrow();
                String path = column.slot().name().declared() + " of object " + context;
                var values = array.disassemble(value, path);
                if (array.target().isPresent() && !values.isEmpty()) {
                    var target = mapping.table(array.target().orElseThrow());
                    var tids = new ArrayList<String>(values.size());
                    for (var item : values) {
                        token.throwIfCancellationRequested();
                        var reference = (Reference) item;
                        if (reference.external() || reference.orderPosition().isPresent()
                                || reference.targetBasket().filter(bid -> !bid.value().equals(basket.value())).isPresent()
                                || (reference.target() instanceof ObjectOid) != hasWireOid(mapping, target)) {
                            throw new DbException("Unsupported array reference at " + path);
                        }
                        tids.add(storedTid(target, reference.target().value()));
                    }
                    pendingArrays.add(new PendingArray(new ReferenceColumn(table, column), id, path, tids));
                    db.bindArray(statement, index, array, List.of(), path);
                } else {
                    db.bindArray(statement, index, array, values, path);
                }
                return index + 1;
            }
            if (column.textExpansion().isPresent()) {
                for (var text : column.textExpansion().orElseThrow().disassemble(value, column.slot().name().declared()
                        + " of object " + context)) {
                    statement.setString(index++, text);
                }
                return index;
            }
            if (column.catalogueReference().isPresent() && value != null && value != Undefined.INSTANCE) {
                var binding = column.catalogueReference().orElseThrow();
                if (!(value instanceof Structured structure) || !binding.structure().equals(structure.type())
                        || structure.values().keySet().stream().anyMatch(field -> !binding.field().symbol().equals(field))) {
                    throw new DbException("Unsupported catalogue reference in " + context + " at " + column.slot().name().declared());
                }
                value = structure.values().get(binding.field().symbol());
            }
            Optional<BigInteger> position = Optional.empty();
            if (column.kind() == DbMapping.Kind.REFERENCE && value instanceof Reference reference) {
                var target = bindReference(table, column, id, context, basket, reference);
                if (target == null) {
                    statement.setNull(index++, Types.BIGINT);
                } else {
                    statement.setLong(index++, target.id());
                }
                position = reference.orderPosition();
            } else {
                index = bindValue(statement, index, table, column, value);
            }
            if (column.positionColumn().isPresent()) {
                bindPosition(statement, index++, position, column);
            } else if (position.isPresent()) {
                throw new DbException("Unexpected role position at " + column.slot().name().declared());
            }
            return index;
        }

        private int bindExpanded(PreparedStatement statement, int index, DbExpandedStructure expanded,
                                        TransferValue value, String context) throws SQLException {
            var items = expanded.items(value, context);
            for (int position = 0; position < expanded.groups().size(); position++) {
                var group = expanded.groups().get(position);
                var item = position < items.size() ? expanded.checked(items.get(position), context) : null;
                statement.setString(index++, item == null ? null : expanded.typeName());
                for (var field : group.fields()) {
                    var fieldValue = item == null ? null : item.values().get(field.slot().symbol());
                    index = field.expandedStructure().isPresent()
                            ? bindExpanded(statement, index, field.expandedStructure().orElseThrow(), fieldValue,
                                    group.path() + "." + field.slot().name().declared().last())
                            : bindScalar(statement, index, field, fieldValue);
                }
            }
            return index;
        }

        private static void bindPosition(PreparedStatement statement, int index, Optional<BigInteger> position,
                                         Column column) throws SQLException {
            if (position.isEmpty()) {
                statement.setNull(index, Types.BIGINT);
                return;
            }
            try {
                statement.setLong(index, position.orElseThrow().longValueExact());
            } catch (ArithmeticException e) {
                throw new DbException("Role position exceeds the 64-bit integer encoding of "
                        + column.slot().name().declared(), e);
            }
        }

        private StoredObject bindReference(Table table, Column column, long id, String tid, BasketId basket,
                                           Reference reference) {
            boolean external = column.external();
            var root = mapping.referenceRoot(column);
            if (reference.external() != external
                    || (!external && reference.targetBasket().filter(target -> !target.value().equals(basket.value())).isPresent())
                    || (reference.target() instanceof ObjectOid) != hasWireOid(mapping, root)) {
                throw new DbException("Unsupported reference in " + tid + " at " + column.slot().name().declared());
            }
            String targetTid = storedTid(root, reference.target().value());
            var patch = new Pending(table, column, id, tid, targetTid, reference.targetBasket());
            var target = (external ? externalObjects : objects).get(patch.targetTid());
            if (target == null) {
                (external ? externalPending : pending).add(patch);
            } else {
                checkReference(patch, target);
            }
            return target != null && mappedInColumn(column, target) ? target : null;
        }

        private boolean mappedInColumn(Column column, StoredObject target) {
            return column.referenceTable().isEmpty() || mapping.occupies(target.type(), column.referenceTable().orElseThrow());
        }

        void finishBasket() throws SQLException {
            patchReferences(pending, objects);
            pending.clear();
            patchArrayReferences();
            pendingArrays.clear();
            objects.clear();
            removedObjects.addAll(existingObjects.values());
            existingObjects = Map.of();
        }

        void finishTransfer() throws SQLException {
            DbDatasetWrite.deleteRemovedObjects(db, mapping, removedObjects, token);
            patchReferences(externalPending, externalObjects);
        }

        private void patchReferences(List<Pending> references, Map<String, StoredObject> targets) throws SQLException {
            for (var patch : references) {
                token.throwIfCancellationRequested();
                var target = targets.get(patch.targetTid());
                if (target == null && patch.column().external()) {
                    target = findReference(db, mapping, patch.column(), patch.targetTid(),
                            patch.targetBasket().map(BasketId::value));
                }
                checkReference(patch, target);
                if (!mappedInColumn(patch.column(), target)) continue;
                var key = new ReferenceColumn(patch.table(), patch.column());
                var statement = referenceUpdate(key);
                statement.setLong(1, target.id());
                statement.setLong(2, patch.objectId());
                if (statement.executeUpdate() != 1) {
                    throw new DbException("Could not patch reference on object " + patch.objectTid());
                }
            }
        }

        private void patchArrayReferences() throws SQLException {
            for (var patch : pendingArrays) {
                var array = patch.column().column().array().orElseThrow();
                var targetType = array.target().orElseThrow();
                var ids = new ArrayList<Object>(patch.targetTids().size());
                for (var tid : patch.targetTids()) {
                    token.throwIfCancellationRequested();
                    var target = objects.get(tid);
                    if (target == null || !targetType.equals(target.type())) {
                        throw new DbException("Cannot encode array reference to " + tid + " in this basket at " + patch.path());
                    }
                    ids.add(target.id());
                }
                var statement = referenceUpdate(patch.column());
                db.bindArray(statement, 1, array, ids, patch.path());
                statement.setLong(2, patch.objectId());
                if (statement.executeUpdate() != 1) throw new DbException("Could not patch array at " + patch.path());
            }
        }

        private PreparedStatement referenceUpdate(ReferenceColumn key) throws SQLException {
            var statement = updates.get(key);
            if (statement == null) {
                statement = db.connection().prepareStatement("UPDATE " + db.table(key.table().name())
                        + " SET " + quote(key.column().name()) + " = ? WHERE " + T_ID + " = ?");
                updates.put(key, statement);
            }
            return statement;
        }

        private void checkReference(Pending patch, StoredObject target) {
            if (target == null || !mapping.isAssignableTo(target.type(), patch.column().target().orElseThrow())) {
                throw new DbException("Cannot encode reference from " + patch.objectTid() + " at "
                        + patch.column().slot().name().declared() + " to " + patch.targetTid()
                        + " as an ID in the mapped target table in this " + (patch.column().external() ? "dataset" : "basket"));
            }
            if (patch.targetBasket().filter(bid -> !bid.value().equals(target.basket().value())).isPresent()) {
                throw new DbException("Wrong target BID for reference from " + patch.objectTid() + " at "
                        + patch.column().slot().name().declared() + " to " + patch.targetTid()
                        + ": expected " + target.basket().value() + ", got " + patch.targetBasket().orElseThrow().value());
            }
        }

        @Override
        public void close() throws SQLException {
            var statements = new ArrayList<>(inserts.values());
            statements.addAll(structureInserts.values());
            statements.addAll(objectUpdates.values());
            statements.addAll(updates.values());
            closeStatements(statements);
        }
    }

    private record StoredObject(long id, SymbolRef type, BasketId basket) { }

    private record ReferenceColumn(Table table, Column column) { }

    private record PendingArray(ReferenceColumn column, long objectId, String path, List<String> targetTids) { }

    private record Pending(Table table, Column column, long objectId, String objectTid, String targetTid,
                           Optional<BasketId> targetBasket) { }
}
