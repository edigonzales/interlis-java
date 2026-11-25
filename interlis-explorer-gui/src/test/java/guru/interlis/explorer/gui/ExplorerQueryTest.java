package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.explorer.ExplorerQueries;
import guru.interlis.explorer.ExplorerQueries.Mode;
import guru.interlis.explorer.ExplorerQueries.Occurrence;
import guru.interlis.explorer.Iql;
import guru.interlis.model.QualifiedName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorerQueryTest {
    private static final Path FIXTURES = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");

    @Test
    void runsNativeReadOnlySqlAndCompletesCatalogNames() throws Exception {
        try (var dataset = relations()) {
            var queries = new ExplorerQueries(dataset.store());
            var result = queries.execute(Mode.SQL, """
                    WITH items AS (SELECT tid FROM object WHERE type_name = 'RelationsDemo.Data.Item')
                    SELECT tid, ';' AS punctuation FROM items ORDER BY tid
                    """, new ExplorerQueries.Cancellation());
            assertEquals(List.of("tid", "punctuation"), result.columns());
            assertEquals(List.of(List.of("i1", ";"), List.of("i2", ";")), result.rows());
            assertEquals(List.of(Optional.empty(), Optional.empty()), result.occurrencesByRow());
            assertFalse(result.truncated());
            assertThrows(SQLException.class, () -> queries.execute(Mode.SQL, "SELECT 1; SELECT 2",
                    new ExplorerQueries.Cancellation()));
            assertThrows(SQLException.class, () -> queries.execute(Mode.SQL, "CREATE TABLE prohibited (id BIGINT)",
                    new ExplorerQueries.Cancellation()));
            assertThrows(SQLException.class, () -> queries.execute(Mode.SQL, "SELECT * FROM read_text('local.txt')",
                    new ExplorerQueries.Cancellation()));

            var catalog = queries.sqlCatalog();
            var text = "SELECT o.ti FROM object AS o";
            var proposals = catalog.complete(text, text.indexOf(" FROM"));
            assertTrue(proposals.stream().anyMatch(proposal -> proposal.text().equals("tid")));
            assertTrue(catalog.functions().contains("count"));

            var capped = queries.execute(Mode.SQL, "SELECT i FROM range(2000) r(i)",
                    new ExplorerQueries.Cancellation());
            assertEquals(1_000, capped.rows().size());
            assertTrue(capped.truncated());

            var ordered = queries.execute(Mode.SQL, """
                    SELECT i AS value, i + 1 AS value FROM range(20) r(i)
                    ORDER BY i DESC LIMIT 2 OFFSET 1; -- trailing comment
                    """, new ExplorerQueries.Cancellation());
            assertEquals(List.of("value", "value"), ordered.columns());
            assertEquals(List.of(List.of("18", "19"), List.of("17", "18")), ordered.rows());
            assertFalse(ordered.truncated());

            var cancelled = new ExplorerQueries.Cancellation();
            cancelled.cancel();
            assertThrows(CancellationException.class, () -> queries.execute(Mode.SQL, "SELECT 1", cancelled));
        }
    }

    @Test
    void cancelsAnExecutingSqlQueryAndLeavesItsDatasetUsable() throws Exception {
        try (var dataset = relations()) {
            var queries = new ExplorerQueries(dataset.store());
            var cancellation = new ExplorerQueries.Cancellation();
            var pending = new FutureTask<>(() -> queries.execute(Mode.SQL, """
                    SELECT count(*) FROM range(200000000) r(i) WHERE md5(i::VARCHAR) = 'no match'
                    """, cancellation));
            var worker = Thread.ofPlatform().daemon().start(pending);
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!pending.isDone() && !executingPreview(worker) && System.nanoTime() < deadline) {
                    Thread.sleep(2);
                }
                assertTrue(executingPreview(worker), "The preview must be executing in DuckDB before cancellation");
                cancellation.cancel();
                var failure = assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
                assertInstanceOf(CancellationException.class, failure.getCause());

                var result = queries.execute(Mode.SQL,
                        "SELECT count(*) FROM object WHERE type_name = 'RelationsDemo.Data.Item'",
                        new ExplorerQueries.Cancellation());
                assertEquals(List.of(List.of("2")), result.rows());
            } finally {
                cancellation.cancel();
                worker.join(Duration.ofSeconds(5));
            }
        }
    }

    private static boolean executingPreview(Thread worker) {
        var stack = Arrays.asList(worker.getStackTrace());
        return stack.stream().anyMatch(frame -> frame.isNativeMethod()
                    && frame.getClassName().equals("org.duckdb.DuckDBNative"))
                && stack.stream().anyMatch(frame -> frame.getClassName().equals(ExplorerQueries.class.getName())
                    && frame.getMethodName().equals("preview"));
    }

    @Test
    void bindsReferencePathsAndReportsUnknownAttributes() throws Exception {
        try (var dataset = relations()) {
            var queries = new ExplorerQueries(dataset.store());
            var result = queries.execute(Mode.IQL, """
                    SELECT i.details.owner.title
                    FROM RelationsDemo.Data.Item AS i
                    WHERE i.title = 'Item with relationships'
                    """, new ExplorerQueries.Cancellation());
            assertEquals(List.of(List.of("Parent")), result.rows());
            long itemPosition = dataset.store().objects(QualifiedName.of("RelationsDemo", "Data", "Item"), 1)
                    .getFirst().position();
            assertEquals(Optional.of(new Occurrence(itemPosition, "")), result.occurrence(0));

            var aggregate = queries.execute(Mode.IQL, "SELECT COUNT(*) FROM RelationsDemo.Data.Item",
                    new ExplorerQueries.Cancellation());
            assertEquals(List.of(List.of("2")), aggregate.rows());
            assertTrue(aggregate.occurrence(0).isEmpty());
            var association = queries.execute(Mode.IQL, "SELECT remark FROM RelationsDemo.Data.Review",
                    new ExplorerQueries.Cancellation());
            assertEquals(List.of(List.of("reviewed")), association.rows());
            assertTrue(association.occurrence(0).isEmpty());

            var text = "SELECT i.details. FROM RelationsDemo.Data.Item AS i";
            assertTrue(queries.completeIql(text, text.indexOf(" FROM")).stream()
                    .anyMatch(proposal -> proposal.text().equals("owner")));

            var invalid = "SELECT i.missingField FROM RelationsDemo.Data.Item AS i";
            var error = assertThrows(Iql.QueryException.class, () -> queries.execute(Mode.IQL, invalid,
                    new ExplorerQueries.Cancellation()));
            assertEquals("missingField", invalid.substring(error.start(), error.end()));
        }
    }

    private static ExplorerDataset relations() throws Exception {
        return ExplorerDataset.open(List.of(FIXTURES.resolve("RelationsDemo.ili")),
                FIXTURES.resolve("relations-demo.xtf"), CancellationToken.none());
    }
}
