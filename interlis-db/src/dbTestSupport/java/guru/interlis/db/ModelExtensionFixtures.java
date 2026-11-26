package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.execution.CancellationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.ModelFixtures.assertStructures;
import static guru.interlis.db.ModelFixtures.fixture;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelExtensionFixtures {
    record OriginalIds(String report, String basket, String dataset, Set<String> details) { }

    private final Path work;
    private final int version;
    private final boolean strict;
    final String original;
    final String added;
    final String library;
    final Path sharedSource;
    final Path addedSource;
    final Path originalInput;
    private final Path originalSource;

    ModelExtensionFixtures(Path work, int version, boolean strict) throws Exception {
        this.work = work;
        this.version = version;
        this.strict = strict;
        original = "DbImportedStruct" + version;
        added = "DbLaterStruct" + version;
        library = "DbSharedDetail" + version;
        originalSource = fixture(work, original + ".ili");
        sharedSource = fixture(work, library + ".ili");
        originalInput = fixture(work, original + ".xtf");
        Files.writeString(originalSource, Files.readString(originalSource).replace("Summary : ", "Summary : MANDATORY "));
        if (strict) {
            Files.writeString(sharedSource, Files.readString(sharedSource).replaceFirst("Label : TEXT", "Label : MANDATORY TEXT"));
        }
        addedSource = Files.writeString(work.resolve(added + ".ili"), Files.readString(originalSource).replace(original, added)
                .replace("END Report;", "  RankNo : MANDATORY 0..100;\n      CommentText : TEXT*30;\n    END Report;"));
    }

    void initialSchema(Target db) {
        if (strict) assertSchemaWithIdentityColumns(db, original, originalSource, "--model-file", sharedSource.toString(), "--sql-not-null");
        else assertSchemaWithIdentityColumns(db, original, originalSource, "--model-file", sharedSource.toString());
    }

    void extend(Target db) {
        var result = schemaWithIdentityColumns(db, added, addedSource, CancellationToken.none(),
                "--add-models", "--model-file", sharedSource.toString());
        assertEquals(0, result.code(), result::diagnostics);
    }

    void removeSources() throws Exception {
        for (Path file : List.of(originalSource, sharedSource, addedSource)) Files.delete(file);
    }

    Path addedDelivery(String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + "-input.xtf"), Files.readString(originalInput)
                .replace(original, added).replace("fixture-", prefix + "-")
                .replace("</Observations>", "</Observations><RankNo>7</RankNo>"));
    }

    void assertOriginalDelivery(Path output) throws Exception {
        assertStructures(output, version, original, library, "fixture");
    }

    void assertAddedDelivery(Path output, String prefix) throws Exception {
        assertStructures(output, version, added, library, prefix);
        var report = children(children(readDataSection(output, version)).getFirst()).getFirst();
        assertEquals("7", child(report, "RankNo").getTextContent());
        assertNull(child(report, "CommentText"));
    }

    OriginalIds originalIds(Target db, Connection sql) throws Exception {
        String report = scalar(sql, "SELECT t_id FROM " + db.table("report") + " WHERE t_ili_tid='fixture-report'");
        String basket = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='fixture-basket'");
        String dataset = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='original'");
        assertNotNull(report);
        assertNotNull(basket);
        assertNotNull(dataset);
        var details = catalogKeys(sql, "SELECT t_id FROM " + db.table("detail") + " WHERE t_basket=" + basket);
        assertEquals(5, details.size());
        return new OriginalIds(report, basket, dataset, details);
    }

    String assertExtension(Target db, Connection sql, DbSchemaCatalog before, OriginalIds ids) throws Exception {
        assertEquals(before.relations().get("settings"), DbSchemaCatalog.read(sql, db.postgis(), db.schema())
                .relations().get("settings"));
        assertEquals(ids, originalIds(db, sql));
        assertEquals("report", tableFor(db, original + ".Data.Report"));
        String table = tableFor(db, added + ".Data.Report");
        assertEquals("dblaterstrct" + version + "data_report", table);
        assertEquals("3", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_model")));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_model") + " WHERE modelname='" + library + "'"));
        assertEquals("detail", tableFor(db, library + ".Detail"));
        assertNullability(db, sql, table, "rankno", !strict);
        assertNullability(db, sql, table, "commenttext", true);
        assertNullability(db, sql, "detail", "label", !strict);
        return table;
    }

    private static void assertNullability(Target db, Connection sql, String table, String column, boolean nullable)
            throws Exception {
        var metadata = sql.getMetaData();
        String escape = metadata.getSearchStringEscape();
        try (var rows = metadata.getColumns(sql.getCatalog(), db.postgis() ? db.schema().replace("_", escape + "_") : null,
                table.replace("_", escape + "_"), column.replace("_", escape + "_"))) {
            assertTrue(rows.next(), table + "." + column);
            assertEquals(nullable ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls,
                    rows.getInt("NULLABLE"), table + "." + column);
        }
    }

    void assertOwnedDetails(Target db, Connection sql, String model, String table, String prefix) throws Exception {
        for (String field : List.of("Summary", "Entries", "Observations")) {
            String ownerColumn = scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_attrname")
                    + " WHERE colowner='detail' AND target='" + table + "' AND iliname='" + model + ".Data.Report." + field + "'");
            assertNotNull(ownerColumn, field);
            assertNullability(db, sql, "detail", ownerColumn, true);
            assertEquals(table, scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                    + " WHERE tablename='detail' AND columnname='" + ownerColumn + "' AND tag='ch.ehi.ili2db.foreignKey'"));
            String rows = " FROM " + db.table("detail") + " d JOIN " + db.table(table)
                    + " r ON d." + ownerColumn + "=r.t_id AND d.t_basket=r.t_basket WHERE r.t_ili_tid='" + prefix + "-report'";
            assertEquals(field.equals("Summary") ? "1" : "2", scalar(sql, "SELECT count(*)" + rows), field);
            assertEquals("1", scalar(sql, "SELECT count(*)" + rows + " AND d.t_seq=0"), field);
        }
    }
}
