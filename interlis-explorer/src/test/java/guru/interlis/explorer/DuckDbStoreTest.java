package guru.interlis.explorer;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferGeometry.Arc;
import guru.interlis.transfer.TransferGeometry.Coordinate;
import guru.interlis.transfer.TransferGeometry.CurvePart;
import guru.interlis.transfer.TransferGeometry.Polyline;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.IdOrigin;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.transfer.TransferValue.*;
import guru.interlis.xtf24.Xtf24Exception;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DuckDbStoreTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final QualifiedName ITEM = QualifiedName.of("Reference24", "Data", "Item");
    private static final QualifiedName PARENT_ROLE = QualifiedName.of("Reference24", "Data", "Link", "parent");
    private static final BasketId BASKET = new BasketId("b1", IdOrigin.SOURCE);
    private static final SourceId SOURCE = new SourceId("fixture:/Reference24.xtf");

    @TempDir Path temp;

    @Test
    void rebindsObjectsAndNavigatesEmbeddedAssociation() throws Exception {
        var file = temp.resolve("objects.duckdb");
        var inputPlan = compile();
        try (var store = DuckDbStore.open(file, inputPlan);
             var input = Files.newInputStream(fixture("structures-and-link.xtf"));
             var reader = new Xtf24Reader(inputPlan, input, Optional.of(SOURCE))) {
            store.importTransfer(reader, RUN);
        }

        var plan = compile("!! New source revision with the same declarations.\n" + model());
        assertNotEquals(inputPlan.snapshotId(), plan.snapshotId());
        try (var store = DuckDbStore.open(file, plan)) {
            var basketTopics = store.basketTopics();
            assertEquals(Set.of(symbol(plan, QualifiedName.of("Reference24", "Data"))), basketTopics);
            assertThrows(UnsupportedOperationException.class, basketTopics::clear);
            var items = store.objects(ITEM);
            assertEquals(1, items.size());
            var item = store.find(BASKET, new TransferId("i1", IdOrigin.SOURCE)).orElseThrow();
            assertEquals(items.getFirst(), item);
            assertEquals(plan.snapshotId(), item.object().type().snapshot());
            assertEquals(Optional.of(SOURCE), item.object().identity().source());

            var details = assertInstanceOf(Repeated.class, item.object().value(symbol(plan, ITEM.child("details"))).orElseThrow());
            assertEquals(CollectionKind.LIST, details.kind());
            var detail = QualifiedName.of("Reference24", "Detail");
            assertEquals(List.of(new Text("second"), new Text("first")), details.values().stream()
                    .map(Structured.class::cast)
                    .peek(value -> assertEquals(symbol(plan, detail), value.type()))
                    .map(value -> value.value(symbol(plan, detail.child("label"))).orElseThrow()).toList());

            var relation = assertInstanceOf(Structured.class,
                    item.object().value(symbol(plan, PARENT_ROLE)).orElseThrow());
            assertEquals(new Text("linked"), relation.value(symbol(plan, QualifiedName.of("Reference24", "Data", "Link", "note")))
                    .orElseThrow());
            var parent = store.find(BASKET, new TransferId("p1", IdOrigin.SOURCE)).orElseThrow();
            var outgoing = store.outgoing(item.position());
            assertEquals(1, outgoing.size());
            var link = outgoing.getFirst();
            assertEquals(PARENT_ROLE, link.slot());
            assertEquals(relation.value(symbol(plan, PARENT_ROLE)).orElseThrow(), link.reference());
            assertEquals(Optional.of(parent), link.target());
            assertEquals(List.of(item), store.incoming(parent.position()));

            var output = new ByteArrayOutputStream();
            store.exportTransfer(output, RUN);
            try (var original = Files.newInputStream(fixture("structures-and-link.xtf"))) {
                assertEquals(read(plan, original), read(plan, new ByteArrayInputStream(output.toByteArray())));
            }
        }

        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + file);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT tid FROM object
                     WHERE type_name = 'Reference24.Data.Item'
                       AND CAST(json_extract_string(fields, '$."Reference24.Data.Item.count"[2]') AS INTEGER) = 5
                     """)) {
            assertTrue(rows.next());
            assertEquals("i1", rows.getString("tid"));
            assertFalse(rows.next());
        }
        var incompatible = compile(model().replace("count : ", "quantity : "));
        try (var store = DuckDbStore.open(file, incompatible)) {
            var failure = assertThrows(IllegalArgumentException.class, () -> store.objects(ITEM));
            assertTrue(failure.getMessage().contains("Reference24.Data.Item.count"));
        }
    }

    @Test
    void committedTransferWithoutBasketsHasNoBasketTopics() throws Exception {
        var plan = compile();
        var transfer = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS">
                  <i:headersection>
                    <i:models><i:model>Reference24</i:model></i:models>
                    <i:sender>test</i:sender>
                  </i:headersection>
                  <i:datasection/>
                </i:transfer>
                """;
        try (var store = DuckDbStore.open(temp.resolve("no-baskets.duckdb"), plan);
             var reader = new Xtf24Reader(plan, new ByteArrayInputStream(transfer.getBytes(StandardCharsets.UTF_8)))) {
            store.importTransfer(reader, RUN);
            assertTrue(store.hasTransfer());
            assertEquals(Set.of(), store.basketTopics());
        }
    }

    @Test
    void limitsExactTypeInTransferOrder() throws Exception {
        var plan = compile();
        try (var store = DuckDbStore.open(temp.resolve("limited.duckdb"), plan);
             var input = Files.newInputStream(fixture("objects-in-transfer-order.xtf"));
             var reader = new Xtf24Reader(plan, input)) {
            store.importTransfer(reader, RUN);

            var items = store.objects(ITEM, 2);
            assertEquals(List.of("i2", "i1"), items.stream()
                    .map(item -> item.object().identity().tid().orElseThrow().value())
                    .toList());
            assertEquals(List.of(items.getFirst()), store.objects(ITEM, 1));
            assertEquals(List.of(), store.objects(ITEM, 0));
            assertThrows(IllegalArgumentException.class, () -> store.objects(ITEM, -1));
        }
    }

    @Test
    void readsIndexedPositionsAndOnlyIncomingAssociationSources() throws Exception {
        var plan = compile(Files.readString(fixture("RelationsDemo.ili")), "RelationsDemo");
        var xml = Files.readString(fixture("relations-demo.xtf")).replace("</Data>", """
                <Item i:tid="only-attribute">
                  <details><Detail><owner i:ref="p1"/></Detail></details>
                </Item>
                <Item i:tid="plain-embedded"><parent i:ref="p1"/></Item>
                </Data>
                """);
        try (var store = DuckDbStore.open(temp.resolve("relations.duckdb"), plan);
             var input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
             var reader = new Xtf24Reader(plan, input)) {
            store.importTransfer(reader, RUN);
            var parent = store.find(BASKET, new TransferId("p1", IdOrigin.SOURCE)).orElseThrow();
            var item = store.find(BASKET, new TransferId("i1", IdOrigin.SOURCE)).orElseThrow();
            assertEquals(Optional.of(parent), store.objectAt(parent.position()));
            assertTrue(store.objectAt(999).isEmpty());
            assertEquals(List.of(OptionalLong.of(parent.position()), OptionalLong.of(parent.position())),
                    store.referencePositions(item.position()).stream()
                            .map(DuckDbStore.ReferencePosition::targetPosition).toList());
            var missing = store.find(BASKET, new TransferId("i2", IdOrigin.SOURCE)).orElseThrow();
            var unresolved = store.referencePositions(missing.position()).getFirst();
            assertEquals("p404", unresolved.reference().target().value());
            assertTrue(unresolved.targetPosition().isEmpty());

            assertEquals(List.of("i1", "r1", "plain-embedded"),
                    store.incomingRoleSources(parent.position(), 10).stream()
                            .map(source -> source.object().identity().tid().orElseThrow().value()).toList());
            assertEquals(List.of(item), store.incomingRoleSources(parent.position(), 1));
            assertEquals(List.of(), store.incomingRoleSources(parent.position(), 0));
            assertThrows(IllegalArgumentException.class, () -> store.incomingRoleSources(parent.position(), -1));
        }
    }

    @Test
    void exportsExactGeometryAfterReopening() throws Exception {
        var file = temp.resolve("geometry.duckdb");
        var plan = compile();
        try (var store = DuckDbStore.open(file, plan);
             var input = Files.newInputStream(fixture("geometry.xtf"));
             var reader = new Xtf24Reader(plan, input, Optional.of(SOURCE))) {
            store.importTransfer(reader, RUN);
        }

        var rebound = compile("!! New source revision with the same declarations.\n" + model());
        var output = new ByteArrayOutputStream();
        try (var store = DuckDbStore.open(file, rebound)) {
            var object = store.find(BASKET, new TransferId("i1", IdOrigin.SOURCE)).orElseThrow().object();
            var line = assertInstanceOf(Polyline.class, object.value(symbol(rebound, ITEM.child("trace"))).orElseThrow());
            var arc = assertInstanceOf(Arc.class, line.parts().getFirst().segments().getFirst());
            var one = new NumberValue("1.00", new BigDecimal("1.00"));
            assertEquals(List.of(one, one), arc.support().ordinates());
            assertEquals(one, arc.radius().orElseThrow().sourceValue());
            assertEquals("2.00", arc.end().ordinates().getFirst().lexeme());
            store.exportTransfer(output, RUN);
        }
        try (var original = Files.newInputStream(fixture("geometry.xtf"))) {
            assertEquals(read(rebound, original), read(rebound, new ByteArrayInputStream(output.toByteArray())));
        }

        try (var store = DuckDbStore.openReadOnly(file, rebound);
             var connection = store.duplicateForQuery();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT value_path, CAST(geom AS VARCHAR), projection_error
                     FROM geometry ORDER BY value_path
                     """)) {
            assertTrue(rows.next());
            assertEquals("/Reference24.Data.Item.points", rows.getString(1));
            assertTrue(rows.getString(2).startsWith("MULTIPOINT"));
            assertNull(rows.getString(3));
            assertTrue(rows.next());
            assertTrue(rows.getString(2).startsWith("POLYGON"));
            assertTrue(rows.next());
            assertTrue(rows.getString(2).startsWith("MULTIPOLYGON"));
            assertTrue(rows.next());
            assertEquals("/Reference24.Data.Item.trace", rows.getString(1));
            assertNull(rows.getString(2));
            assertTrue(rows.getString(3).contains("explicit radius"));
            assertTrue(rows.next());
            assertTrue(rows.getString(2).startsWith("MULTILINESTRING"));
            assertFalse(rows.next());
        }
    }

    @Test
    void derivesGeometryAtTheExactCollectionOccurrenceAndUsesModelCrs() throws Exception {
        var file = temp.resolve("nested-geometry.duckdb");
        var plan = compile(model().replace("    P = ", "    !!@CRS=EPSG:2056\n    P = ")
                .replace("    label : TEXT*20;", "    label : TEXT*20;\n    point : P;"));
        var xml = Files.readString(fixture("structures-and-link.xtf"))
                .replace("<label>second</label>",
                        "<label>second</label><point><g:coord><g:c1>1.00</g:c1><g:c2>2.00</g:c2></g:coord></point>")
                .replace("<label>first</label>",
                        "<label>first</label><point><g:coord><g:c1>3.00</g:c1><g:c2>4.00</g:c2></g:coord></point>")
                .replace("xmlns=\"urn:reference24\"", "xmlns=\"urn:reference24\" xmlns:g=\"http://www.interlis.ch/geometry/1.0\"");
        try (var store = DuckDbStore.open(file, plan);
             var reader = new Xtf24Reader(plan, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            store.importTransfer(reader, RUN);
        }
        try (var store = DuckDbStore.openReadOnly(file, plan);
             var connection = store.duplicateForQuery();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT s.text_value, g.crs, CAST(g.geom AS VARCHAR)
                     FROM scalar s JOIN geometry g ON g.source_position = s.source_position
                       AND replace(g.value_path, '/Reference24.Detail.point', '/Reference24.Detail.label') = s.value_path
                     ORDER BY g.value_path
                     """)) {
            assertTrue(rows.next());
            assertEquals("second", rows.getString(1));
            assertEquals("EPSG:2056", rows.getString(2));
            assertEquals("POINT (1 2)", rows.getString(3));
            assertTrue(rows.next());
            assertEquals("first", rows.getString(1));
            assertEquals("POINT (3 4)", rows.getString(3));
            assertFalse(rows.next());
        }
    }

    @Test
    void segmentsSharedArcsIdenticallyInEitherDirection() {
        var start = coordinate(0, 0);
        var support = coordinate(1, 1);
        var end = coordinate(2, 0);
        var forward = new Polyline(List.of(new CurvePart(start, List.of(new Arc(support, end, Optional.empty())))));
        var reverse = new Polyline(List.of(new CurvePart(end, List.of(new Arc(support, start, Optional.empty())))));
        double tolerance = 0.001;
        var points = linePoints(GeometryProjection.encode(forward, tolerance));
        assertEquals(points.reversed(), linePoints(GeometryProjection.encode(reverse, tolerance)));
        assertEquals(List.of(0.0, 0.0), points.getFirst());
        assertTrue(points.contains(List.of(1.0, 1.0)));
        assertEquals(List.of(2.0, 0.0), points.getLast());
        for (int index = 1; index < points.size(); index++) {
            var a = points.get(index - 1);
            var b = points.get(index);
            double radiusAtMidpoint = Math.hypot((a.get(0) + b.get(0)) / 2 - 1, (a.get(1) + b.get(1)) / 2);
            assertTrue(1 - radiusAtMidpoint <= tolerance);
        }
    }

    private static Coordinate coordinate(int x, int y) {
        return new Coordinate(List.of(new NumberValue(Integer.toString(x), BigDecimal.valueOf(x)),
                new NumberValue(Integer.toString(y), BigDecimal.valueOf(y))));
    }

    private static List<List<Double>> linePoints(byte[] wkb) {
        var data = ByteBuffer.wrap(wkb).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, data.get());
        assertEquals(2, data.getInt());
        int size = data.getInt();
        var points = new ArrayList<List<Double>>(size);
        for (int index = 0; index < size; index++) {
            points.add(List.of(data.getDouble(), data.getDouble()));
        }
        assertFalse(data.hasRemaining());
        return points;
    }

    @Test
    void backfillsReferencePathsAndTypedValuesBeforeReadOnlyQueries() throws Exception {
        var file = temp.resolve("old-store.duckdb");
        var plan = compile();
        try (var store = DuckDbStore.open(file, plan);
             var input = Files.newInputStream(fixture("structures-and-link.xtf"));
             var reader = new Xtf24Reader(plan, input)) {
            store.importTransfer(reader, RUN);
        }
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + file);
             var statement = connection.createStatement()) {
            statement.execute("DROP INDEX reference_target");
            statement.execute("ALTER TABLE reference DROP COLUMN value_path");
            for (var table : List.of("projection_version", "association_role", "association", "geometry", "scalar")) {
                statement.execute("DROP TABLE " + table);
            }
        }
        try (var store = DuckDbStore.open(file, plan)) {
            assertTrue(store.hasTransfer());
        }
        try (var store = DuckDbStore.openReadOnly(file, plan);
             var connection = store.duplicateForQuery();
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("""
                    SELECT value_path, decimal_value, integer_value, number_text FROM scalar
                    WHERE value_path = '/Reference24.Data.Item.count'
                    """)) {
                assertTrue(rows.next());
                assertEquals(new BigDecimal("5.000000000000000000"), rows.getBigDecimal("decimal_value"));
                assertEquals(5, rows.getLong("integer_value"));
                assertEquals("5", rows.getString("number_text"));
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("""
                    SELECT a.value_path, r.slot, o.tid
                    FROM association a JOIN association_role r ON a.id = r.association_id
                    JOIN object o ON r.target_position = o.position ORDER BY r.slot
                    """)) {
                assertTrue(rows.next());
                assertEquals("/Reference24.Data.Link.parent", rows.getString(1));
                assertEquals("Reference24.Data.Link.items", rows.getString(2));
                assertEquals("i1", rows.getString(3));
                assertTrue(rows.next());
                assertEquals("Reference24.Data.Link.parent", rows.getString(2));
                assertEquals("p1", rows.getString(3));
                assertFalse(rows.next());
            }
            assertThrows(SQLException.class, () -> statement.execute("DELETE FROM object"));
            try (var configuration = connection.createStatement()) {
                assertThrows(SQLException.class, () -> configuration.execute("SET enable_external_access = true"));
            }
        }
    }

    @Test
    void rollsBackLateSourceFailureAndAllowsOneCompleteImport() throws Exception {
        var file = temp.resolve("rollback.duckdb");
        var plan = compile();
        var xml = Files.readString(fixture("structures-and-link.xtf"));
        byte[] truncated = xml.substring(0, xml.indexOf("</i:transfer>")).getBytes(StandardCharsets.UTF_8);
        try (var store = DuckDbStore.open(file, plan);
             var reader = new Xtf24Reader(plan, new ByteArrayInputStream(truncated))) {
            assertThrows(Xtf24Exception.class, () -> store.importTransfer(reader, RUN));
        }

        try (var store = DuckDbStore.open(file, plan);
             var connection = DriverManager.getConnection("jdbc:duckdb:" + file);
             var statement = connection.createStatement()) {
            assertEmptyStore(store, statement);
            var missing = xml.replace("i:ref=\"p1\"", "i:ref=\"missing\"").getBytes(StandardCharsets.UTF_8);
            try (var reader = new Xtf24Reader(plan, new ByteArrayInputStream(missing))) {
                store.importTransfer(reader, RUN);
            }
            assertTrue(store.hasTransfer());
            var item = store.find(BASKET, new TransferId("i1", IdOrigin.SOURCE)).orElseThrow();
            var unresolved = store.outgoing(item.position());
            assertEquals(1, unresolved.size());
            assertEquals(new TransferId("missing", IdOrigin.SOURCE), unresolved.getFirst().reference().target());
            assertTrue(unresolved.getFirst().target().isEmpty());

            try (var input = Files.newInputStream(fixture("geometry.xtf"));
                 var reader = new Xtf24Reader(plan, input)) {
                assertThrows(IllegalStateException.class, () -> store.importTransfer(reader, RUN));
            }
            assertEquals(unresolved, store.outgoing(item.position()));
        }
    }

    @Test
    void rollsBackWhenFlushingReferencesFails() throws Exception {
        var file = temp.resolve("flush-failure.duckdb");
        var plan = compile();
        var xml = Files.readString(fixture("structures-and-link.xtf")).replace("</Data>", """
                <Item i:tid="i2">
                  <required>yes</required>
                  <parent i:ref="p1"><Link><note>linked</note></Link></parent>
                </Item>
                </Data>
                """);
        try (var store = DuckDbStore.open(file, plan);
             var connection = DriverManager.getConnection("jdbc:duckdb:" + file);
             var statement = connection.createStatement();
             var reader = new Xtf24Reader(plan, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            statement.execute("CREATE UNIQUE INDEX one_reference_per_slot ON reference (slot)");
            var failure = assertThrows(SQLException.class, () -> store.importTransfer(reader, RUN));
            assertTrue(failure.getMessage().contains(PARENT_ROLE.toString()), failure::getMessage);
            assertEmptyStore(store, statement);
        }
    }

    private static void assertEmptyStore(DuckDbStore store, Statement statement) throws SQLException {
        assertFalse(store.hasTransfer());
        for (var table : List.of("transfer", "basket", "object", "reference", "geometry", "scalar",
                "association", "association_role", "projection_version")) {
            try (var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
                assertTrue(rows.next());
                assertEquals(0L, rows.getLong(1), table);
            }
        }
    }

    private static TransferPlan compile() throws Exception {
        return compile(model());
    }

    private static String model() throws Exception {
        return Files.readString(fixture("Reference24.ili"));
    }

    private static TransferPlan compile(String model) {
        return compile(model, "Reference24");
    }

    private static TransferPlan compile(String model, String modelName) {
        var source = new SourceText(new SourceId("fixture:/" + modelName + ".ili"), new SourceRevision(1), model);
        var root = new ModelSelector.ByName(modelName, Optional.empty(), Optional.empty());
        var request = new CompilationRequest(List.of(root), SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, RUN);
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, outcome::toString).schema();
        return assertInstanceOf(TransferPlanningResult.Ready.class,
                TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED)).plan();
    }

    private static SymbolRef symbol(TransferPlan plan, QualifiedName name) {
        var matches = plan.schema().queries().findQualifiedName(name);
        assertEquals(1, matches.size(), name.toString());
        return matches.getFirst();
    }

    private static List<TransferEvent> read(TransferPlan plan, InputStream input) {
        var result = new ArrayList<TransferEvent>();
        try (var reader = new Xtf24Reader(plan, input, Optional.of(SOURCE))) {
            for (var next = reader.readNext(RUN); next.isPresent(); next = reader.readNext(RUN)) {
                result.add(next.orElseThrow());
            }
        }
        return result;
    }

    private static Path fixture(String name) {
        return Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24", name);
    }
}
