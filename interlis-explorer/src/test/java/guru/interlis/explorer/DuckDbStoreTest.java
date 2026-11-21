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
            var items = store.objects(ITEM);
            assertEquals(1, items.size());
            assertEquals(items, store.objects(ITEM, 1));
            assertEquals(List.of(), store.objects(ITEM, 0));
            assertThrows(IllegalArgumentException.class, () -> store.objects(ITEM, -1));
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
        for (var table : List.of("transfer", "basket", "object", "reference")) {
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
        var source = new SourceText(new SourceId("fixture:/Reference24.ili"), new SourceRevision(1), model);
        var root = new ModelSelector.ByName("Reference24", Optional.empty(), Optional.empty());
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
