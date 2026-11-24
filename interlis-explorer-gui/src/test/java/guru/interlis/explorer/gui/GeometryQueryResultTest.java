package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.explorer.ExplorerQueries;
import guru.interlis.explorer.ExplorerQueries.Mode;
import guru.interlis.explorer.Iql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryQueryResultTest {
    private static final Path FIXTURES = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");

    @TempDir Path directory;

    @Test
    void previewsNativeGeometryAtTheSelectedListOccurrence() throws Exception {
        Path model = directory.resolve("Reference24.ili");
        Files.writeString(model, Files.readString(FIXTURES.resolve("Reference24.ili"))
                .replace("    P = ", "    !!@CRS=EPSG:2056\n    P = ")
                .replace("LIST {0..2}", "LIST {0..3}")
                .replace("label : TEXT*20;", "label : TEXT*20; marker : POLYLINE WITH (STRAIGHTS, ARCS) VERTEX P;"));
        Path transfer = directory.resolve("geometry-in-list.xtf");
        Files.writeString(transfer, Files.readString(FIXTURES.resolve("structures-and-link.xtf"))
                .replace("<label>second</label>", """
                        <label>second</label>
                        <marker xmlns:g="http://www.interlis.ch/geometry/1.0"><g:polyline>
                          <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                          <g:coord><g:c1>1</g:c1><g:c2>1</g:c2></g:coord>
                        </g:polyline></marker>
                        """)
                .replace("<label>first</label>", """
                        <label>first</label>
                        <marker xmlns:g="http://www.interlis.ch/geometry/1.0"><g:polyline>
                          <g:coord><g:c1>0</g:c1><g:c2>0</g:c2></g:coord>
                          <g:arc><g:c1>2</g:c1><g:c2>0</g:c2><g:a1>1</g:a1><g:a2>1</g:a2><g:r>1</g:r></g:arc>
                        </g:polyline></marker>
                        """)
                .replace("<count>5</count>", """
                        <details><Detail><label>missing</label></Detail></details>
                        <count>5</count>
                        <trace xmlns:g="http://www.interlis.ch/geometry/1.0"><g:polyline>
                          <g:coord><g:c1>2</g:c1><g:c2>2</g:c2></g:coord>
                          <g:coord><g:c1>3</g:c1><g:c2>3</g:c2></g:coord>
                        </g:polyline></trace>
                        """));

        try (var dataset = ExplorerDataset.open(List.of(model), transfer, CancellationToken.none())) {
            var queries = new ExplorerQueries(dataset.store());
            String text = """
                    SELECT GEOMETRY(i.trace) AS trace, d.label, GEOMETRY(d.marker) AS marker
                    FROM Reference24.Data.Item i EXPAND i.details AS d
                    """;
            var result = queries.execute(Mode.IQL, text, new ExplorerQueries.Cancellation());
            assertEquals(List.of("trace", "label", "marker"), result.columns());
            assertEquals(List.of(
                    List.of("LINESTRING · EPSG:2056", "second", "LINESTRING · EPSG:2056"),
                    List.of("LINESTRING · EPSG:2056", "first", "Geometry unavailable"),
                    List.of("LINESTRING · EPSG:2056", "missing", "NULL")
            ), result.rows());

            var root = result.geometry(0, 0).orElseThrow();
            var line = result.geometry(0, 2).orElseThrow();
            assertEquals(root.sourcePosition(), line.sourcePosition());
            assertEquals("/Reference24.Data.Item.trace", root.valuePath());
            assertEquals("/Reference24.Data.Item.details/0/Reference24.Detail.marker", line.valuePath());
            assertEquals(Optional.of("EPSG:2056"), line.crs());
            assertEquals(Optional.of("LINESTRING"), line.geometryType());
            assertTrue(line.projectionError().isEmpty());
            var failed = result.geometry(1, 2).orElseThrow();
            assertEquals("/Reference24.Data.Item.details/1/Reference24.Detail.marker", failed.valuePath());
            assertTrue(failed.projectionError().orElseThrow().contains("explicit radius"));
            assertFalse(result.isNull(1, 2));
            assertTrue(result.isNull(2, 2));
            assertTrue(result.geometry(2, 2).isEmpty());

            var nativeQuery = Iql.compile(dataset.plan(), text, 100);
            var nativeResult = queries.execute(Mode.SQL, nativeQuery.sql(), new ExplorerQueries.Cancellation());
            assertEquals(result.columns(), nativeResult.columns());
            assertEquals(List.of("GEOMETRY", "second", "GEOMETRY"), nativeResult.rows().getFirst());

            SwingUtilities.invokeAndWait(() -> {
                var table = new QueryPanel.ResultTable();
                table.showResult(result);
                var renderer = table.getCellRenderer(1, 2);
                var unavailable = assertInstanceOf(JLabel.class, table.prepareRenderer(renderer, 1, 2));
                assertTrue(unavailable.getToolTipText().contains("explicit radius"));
                var missing = assertInstanceOf(JLabel.class, table.prepareRenderer(renderer, 2, 2));
                assertEquals("SQL NULL", missing.getToolTipText());
                assertTrue(missing.getFont().isItalic());
            });
        }
    }
}
