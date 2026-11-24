package guru.interlis.explorer;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IqlTest {
    private static final CancellationToken RUN = CancellationToken.none();

    @TempDir Path directory;

    @Test
    void resolvesDirectEmbeddedAndStandaloneRelationships() throws Exception {
        var plan = plan("RelationsDemo");
        Path database = load(plan, "relations-demo.xtf");
        assertEquals(List.of("Parent"), values(database, plan, """
                SELECT i.details.owner.title FROM RelationsDemo.Data.Item i WHERE i.parent.title = 'Parent'
                """));
        assertEquals(List.of("Parent"), values(database, plan, """
                SELECT p.title FROM RelationsDemo.Data.Parent p
                WHERE EXISTS(p.items AS i WHERE i.title = 'Item with relationships')
                """));
        assertEquals(List.of("linked"), values(database, plan, """
                SELECT l.note FROM RelationsDemo.Data.Link l WHERE l.parent.title = 'Parent'
                """));
        assertEquals(List.of("reviewed"), values(database, plan, """
                SELECT r.remark FROM RelationsDemo.Data.Review r
                WHERE EXISTS(r.reviewer AS p WHERE p.title = 'Parent')
                """));
        assertEquals(List.of("Item with an unresolved reference"), values(database, plan, """
                SELECT title FROM RelationsDemo.Data.Item WHERE details.owner IS UNRESOLVED
                """));
    }

    @Test
    void comparesExactNumbersAndBindsCollectionFiltersToTheirElement() throws Exception {
        var plan = plan("Reference24");
        Path database = load(plan, "structures-and-link.xtf");
        assertEquals(List.of("1"), values(database, plan, """
                SELECT COUNT(*) FROM Reference24.Data.Item i
                WHERE i.count > 4.99 AND EXISTS(i.details AS d WHERE d.label = 'second')
                """));
        assertEquals(List.of("0"), values(database, plan, """
                SELECT COUNT(*) FROM Reference24.Data.Item i
                WHERE EXISTS(i.details AS d WHERE d.label = 'second' AND d.label = 'first')
                """));
        assertEquals(List.of("0"), values(database, plan, """
                SELECT COUNT(*) FROM Reference24.Data.Item i WHERE i.count = 5.0000000000000000001
                """));
        assertEquals(List.of("5"), values(database, plan, """
                SELECT count FROM Reference24.Data.Item WHERE count = 50e-1 ORDER BY count DESC
                """));
    }

    @Test
    void keepsValuesAsParametersAndPointsAtInvalidPaths() throws Exception {
        var plan = plan("RelationsDemo");
        var query = Iql.compile(plan, "SELECT title FROM RelationsDemo.Data.Item WHERE title = 'O''Neil'", 20);
        assertEquals(List.of("O'Neil"), query.parameters());
        assertFalse(query.sql().contains("Neil"));
        assertTrue(query.sql().endsWith(" LIMIT 21"));
        String invalid = "SELECT i.nonexistent FROM RelationsDemo.Data.Item AS i";
        var failure = assertThrows(Iql.QueryException.class, () -> Iql.compile(plan, invalid, 20));
        assertEquals("nonexistent", invalid.substring(failure.start(), failure.end()));
        assertThrows(Iql.QueryException.class, () -> Iql.compile(plan, "SELECT title FROM RelationsDemo.Data.Item; SELECT title", 20));
    }

    @Test
    void completesQualifiedSourcesAliasesAndStructuresAtTheCaret() throws Exception {
        var plan = plan("RelationsDemo");
        String source = "SELECT * FROM RelationsDemo.Data.I";
        assertTrue(Iql.complete(plan, source, source.length()).stream().anyMatch(match -> match.text().equals("Item")));
        String nested = "SELECT i.details.ow FROM RelationsDemo.Data.Item AS i";
        var matches = Iql.complete(plan, nested, nested.indexOf(" FROM"));
        assertEquals(List.of("owner"), matches.stream().map(Iql.Completion::text).toList());
        var match = matches.getFirst();
        assertEquals("ow", nested.substring(match.start(), match.end()));
        String inner = "SELECT p.title FROM RelationsDemo.Data.Parent p WHERE EXISTS(p.items AS i WHERE i.det";
        assertEquals(List.of("details"), Iql.complete(plan, inner, inner.length()).stream().map(Iql.Completion::text).toList());
    }

    @Test
    void spatialPreflightChecksOnlyTheGeometryNamedByTheQuery() throws Exception {
        var plan = plan("Reference24");
        Path database = load(plan, "geometry.xtf");
        var query = Iql.compile(plan, """
                SELECT count FROM Reference24.Data.Item i WHERE INTERSECTS(i.shape, BOX(0, 0, 1, 1)) ORDER BY count
                """, 100);
        assertEquals(List.of(0.0, 0.0, 1.0, 1.0), query.parameters());
        var check = query.checks().getFirst();
        assertFalse(check.sql().contains("JOIN scalar"));
        var failedGeometry = Iql.compile(plan, """
                SELECT COUNT(*) FROM Reference24.Data.Item i WHERE INTERSECTS(i.trace, BOX(0, 0, 1, 1))
                """, 100).checks().getFirst();
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + database);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(check.sql())) {
                assertTrue(result.next());
                assertTrue(result.getString(1).contains("coordinate reference system is unknown"));
            }
            try (var result = statement.executeQuery(failedGeometry.sql())) {
                assertTrue(result.next());
                assertTrue(result.getString(1).contains("no native projection"));
            }
        }
    }

    @Test
    void spatialPreflightKeepsTheCollectionOccurrence() throws Exception {
        var plan = plan("Reference24", Files.readString(fixture("Reference24.ili"))
                .replace("label : TEXT*20;", "label : TEXT*20; marker : P;"));
        var input = Files.readString(fixture("structures-and-link.xtf")).replace("<label>first</label>", """
                <label>first</label><marker xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <g:coord><g:c1>1.00</g:c1><g:c2>2.00</g:c2></g:coord>
                </marker>
                """);
        Path database = directory.resolve("nested.duckdb");
        try (var store = DuckDbStore.open(database, plan);
             var reader = new Xtf24Reader(plan, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))) {
            store.importTransfer(reader, RUN);
        }
        String query = """
                SELECT COUNT(*) FROM Reference24.Data.Item i
                WHERE EXISTS(i.details AS d WHERE INTERSECTS(d.marker, BOX(0, 0, 3, 3)))
                """;
        var check = Iql.compile(plan, query, 100).checks().getFirst();
        assertEquals("d.marker", query.substring(check.start(), check.end()));
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + database);
             var statement = connection.createStatement();
             var result = statement.executeQuery(check.sql())) {
            assertTrue(result.next());
            assertTrue(result.getString(1).contains("coordinate reference system is unknown"));
        }
    }

    @Test
    void spatialPreflightRespectsBooleanBranches() throws Exception {
        var plan = plan("Reference24", Files.readString(fixture("Reference24.ili"))
                .replace("    P = ", "    !!@CRS=EPSG:2056\n    P = "));
        var input = Files.readString(fixture("geometry.xtf")).replace("    </Data>", """
                    <Item i:tid="i2">
                      <required>geo</required>
                      <count>5</count>
                      <trace><g:polyline>
                        <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                        <g:coord><g:c1>1</g:c1><g:c2>1</g:c2></g:coord>
                      </g:polyline></trace>
                    </Item>
                  </Data>
                """);
        var database = loadText(plan, input);
        String select = "SELECT COUNT(*) FROM Reference24.Data.Item i WHERE ";
        String spatial = "INTERSECTS(i.trace, BOX(0, 0, 1, 1))";
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + database)) {
            var filtered = Iql.compile(plan, select + "i.count = 5 AND " + spatial, 100);
            assertEquals(List.of("5", 0.0, 0.0, 1.0, 1.0), filtered.parameters());
            assertEquals(List.of("5"), filtered.checks().getFirst().parameters());
            assertNull(runCheck(connection, filtered));
            assertNull(runCheck(connection, Iql.compile(plan, select + "FALSE AND " + spatial, 100)));
            assertNull(runCheck(connection, Iql.compile(plan, select + spatial + " OR TRUE", 100)));
            assertTrue(runCheck(connection, Iql.compile(plan, select + "FALSE OR " + spatial, 100))
                    .contains("no native projection"));
            assertTrue(runCheck(connection, Iql.compile(plan, select + "NOT (i.count = 5 AND " + spatial + ")", 100))
                    .contains("no native projection"));
        }
    }

    @Test
    void spatialPreflightCombinesOuterAndSameOccurrenceFilters() throws Exception {
        var plan = plan("Reference24", Files.readString(fixture("Reference24.ili"))
                .replace("    P = ", "    !!@CRS=EPSG:2056\n    P = ")
                .replace("label : TEXT*20;", "label : TEXT*20; marker : POLYLINE WITH (STRAIGHTS, ARCS) VERTEX P;"));
        var input = Files.readString(fixture("structures-and-link.xtf")).replace("<label>second</label>", """
                <label>second</label>
                <marker xmlns:g="http://www.interlis.ch/geometry/1.0"><g:polyline>
                  <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                  <g:arc><g:c1>2</g:c1><g:c2>0</g:c2><g:a1>1</g:a1><g:a2>1</g:a2><g:r>1</g:r></g:arc>
                </g:polyline></marker>
                """).replace("<label>first</label>", """
                <label>first</label>
                <marker xmlns:g="http://www.interlis.ch/geometry/1.0"><g:polyline>
                  <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                  <g:coord><g:c1>1</g:c1><g:c2>1</g:c2></g:coord>
                </g:polyline></marker>
                """);
        var database = loadText(plan, input);
        String source = """
                SELECT COUNT(*) FROM Reference24.Data.Item i WHERE i.count = %d
                  AND EXISTS(i.details AS d WHERE INTERSECTS(d.marker, BOX(0, 0, 1, 1)) AND d.label = '%s')
                """;
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + database)) {
            var valid = Iql.compile(plan, source.formatted(5, "first"), 100);
            assertEquals(List.of("5", 0.0, 0.0, 1.0, 1.0, "first"), valid.parameters());
            assertEquals(List.of("first", "5"), valid.checks().getFirst().parameters());
            assertNull(runCheck(connection, valid));
            assertNull(runCheck(connection, Iql.compile(plan, source.formatted(4, "second"), 100)));
            assertTrue(runCheck(connection, Iql.compile(plan, source.formatted(5, "second"), 100))
                    .contains("no native projection"));
        }
    }

    private static String runCheck(Connection connection, Iql.Compiled compiled) throws Exception {
        var check = compiled.checks().getFirst();
        try (var statement = connection.prepareStatement(check.sql())) {
            for (int i = 0; i < check.parameters().size(); i++) statement.setObject(i + 1, check.parameters().get(i));
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private Path loadText(TransferPlan plan, String text) throws Exception {
        var database = directory.resolve("spatial-scope.duckdb");
        try (var store = DuckDbStore.open(database, plan);
             var reader = new Xtf24Reader(plan, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))) {
            store.importTransfer(reader, RUN);
        }
        return database;
    }

    private Path load(TransferPlan plan, String file) throws Exception {
        Path database = directory.resolve("test.duckdb");
        try (var store = DuckDbStore.open(database, plan);
             var input = Files.newInputStream(fixture(file));
             var reader = new Xtf24Reader(plan, input)) {
            store.importTransfer(reader, RUN);
        }
        return database;
    }

    private static List<String> values(Path database, TransferPlan plan, String text) throws Exception {
        var query = Iql.compile(plan, text, 100);
        var values = new ArrayList<String>();
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + database);
             var statement = connection.prepareStatement(query.sql())) {
            for (int i = 0; i < query.parameters().size(); i++) statement.setObject(i + 1, query.parameters().get(i));
            try (var result = statement.executeQuery()) {
                while (result.next()) values.add(result.getString(1));
            }
        }
        return values;
    }

    private static TransferPlan plan(String name) throws Exception {
        return plan(name, Files.readString(fixture(name + ".ili")));
    }

    private static TransferPlan plan(String name, String text) {
        var source = new SourceText(new SourceId("fixture:/" + name + ".ili"), new SourceRevision(1),
                text);
        var request = new CompilationRequest(List.of(new ModelSelector.ByName(name, Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        var compiled = assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create().compile(request, RUN));
        return assertInstanceOf(TransferPlanningResult.Ready.class,
                TransferPlanner.plan(compiled.schema(), TransferPlanner.Names.DECLARED)).plan();
    }

    private static Path fixture(String name) {
        return Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24", name);
    }
}
