package guru.interlis.explorer.gui;

import guru.interlis.execution.CancellationToken;
import guru.interlis.explorer.ExplorerQueries;
import guru.interlis.explorer.ExplorerQueries.Mode;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryResultPresentationTest {
    private static final Path FIXTURES = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");

    @Test
    void keepsNullCellIdentityInBothQueryModesAndRendersLiteralHeadings() throws Exception {
        try (var dataset = ExplorerDataset.open(List.of(FIXTURES.resolve("RelationsDemo.ili")),
                FIXTURES.resolve("relations-demo.xtf"), CancellationToken.none())) {
            var queries = new ExplorerQueries(dataset.store());
            var sql = queries.execute(Mode.SQL, """
                    SELECT CAST(NULL AS VARCHAR) AS "<html><b>Heading</b>", 'NULL' AS literal,
                           '' AS empty_text, CAST(NULL AS BLOB) AS absent_bytes
                    """, new ExplorerQueries.Cancellation());
            assertEquals(List.of(List.of("NULL", "NULL", "", "NULL")), sql.rows());
            assertEquals(List.of(Set.of(0, 3)), sql.nullColumnsByRow());
            var iql = queries.execute(Mode.IQL, """
                    SELECT i.details.owner.title FROM RelationsDemo.Data.Item i
                    ORDER BY i.title DESC
                    """, new ExplorerQueries.Cancellation());
            assertEquals(List.of(List.of("Parent"), List.of("NULL")), iql.rows());
            assertEquals(List.of(Set.of(), Set.of(0)), iql.nullColumnsByRow());

            SwingUtilities.invokeAndWait(() -> {
                var table = new QueryPanel.ResultTable();
                table.showResult(sql);
                var header = table.getTableHeader();
                Object title = table.getColumnModel().getColumn(0).getHeaderValue();
                var heading = assertInstanceOf(JLabel.class, header.getDefaultRenderer()
                        .getTableCellRendererComponent(table, title, false, false, -1, 0));
                assertEquals("<html><b>Heading</b>", heading.getText());
                assertNull(heading.getClientProperty(BasicHTML.propertyKey));

                var absent = assertInstanceOf(JLabel.class, table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0));
                assertEquals("NULL", absent.getText());
                assertTrue(absent.getFont().isItalic());
                assertEquals("SQL NULL", absent.getToolTipText());
                assertEquals("SQL NULL", absent.getAccessibleContext().getAccessibleName());

                var literal = assertInstanceOf(JLabel.class, table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1));
                assertEquals("NULL", literal.getText());
                assertEquals(table.getFont(), literal.getFont());
                assertNull(literal.getToolTipText());
                assertEquals("NULL", literal.getAccessibleContext().getAccessibleName());
                assertFalse(sql.isNull(0, 2));
            });
        }
    }
}
