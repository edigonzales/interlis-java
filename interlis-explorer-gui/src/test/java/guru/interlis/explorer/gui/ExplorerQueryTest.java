package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.explorer.ExplorerQueries;
import guru.interlis.explorer.ExplorerQueries.Mode;
import guru.interlis.explorer.Iql;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            var cancelled = new ExplorerQueries.Cancellation();
            cancelled.cancel();
            assertThrows(CancellationException.class, () -> queries.execute(Mode.SQL, "SELECT 1", cancelled));
        }
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
