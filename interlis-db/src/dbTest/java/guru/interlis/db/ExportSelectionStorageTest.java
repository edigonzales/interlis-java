package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExportSelectionStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void basketExportBoundsOwnerChecksAndRequiresUniqueBids(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("basket-checks.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        var input = fixture.delivery("primary",
                fixture.basket("Data", "keep", fixture.asset("keep-asset", "keep", false)),
                fixture.basket("Data", "other", fixture.asset("other-asset", "other", true)));
        assertCliSuccess(db, "import", fixture.model, "primary", input);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("note") + " SET asset_detail=NULL");
        }
        var output = work.resolve("selected.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", "keep");
        assertEquals(List.of("Data:keep:keep-asset:keep"), fixture.exportedAssets(output));

        assertRejected(db, fixture.model, output, "Expected exactly one structure owner", "--basket", "other");
        assertRejected(db, fixture.model, output, "Basket does not exist: missing",
                "--basket", "keep", "--basket", "missing");
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("note") + " SET asset_detail=(SELECT t_id FROM " + db.table("asset")
                    + " WHERE t_ili_tid='other-asset'), t_basket=(SELECT t_id FROM " + db.table("t_ili2db_basket")
                    + " WHERE t_ili_tid='keep')");
        }
        assertRejected(db, fixture.model, output, "owner are in different baskets", "--basket", "other");

        var copy = fixture.delivery("repeat-bid", fixture.basket("Data", "keep", fixture.asset("copy-asset", "copy", false)));
        assertCliSuccess(db, "import", fixture.model, "duplicate", copy);
        assertRejected(db, fixture.model, output, "Ambiguous basket BID: keep", "--basket", "keep");
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void omittedTargetsUseExternalResolutionAcrossTheActualSelection(Backend backend, int version) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var db = POSTGIS.target(work.resolve("basket-references.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.model, fixture.source);
        var input = fixture.delivery("references",
                fixture.basket("Data", "source", fixture.survey("survey", "destination", "target")),
                fixture.basket("Data", "target", fixture.place("destination")));
        assertCliSuccess(db, "import", fixture.model, "references", input);
        var collision = fixture.delivery("collision", fixture.basket("Data", "collision", fixture.asset("destination", "other", false)));
        assertCliSuccess(db, "import", fixture.model, "collision", collision);

        var output = work.resolve("selected.xtf");
        assertCliSuccess(db, "export", fixture.model, null, output, "--basket", "source");
        var baskets = fixture.baskets(output);
        assertEquals(List.of("source"), baskets.stream().map(basket -> id(basket, version, "bid")).toList());
        var survey = children(baskets.getFirst()).getFirst();
        assertEquals("survey", id(survey, version, "tid"));
        var reference = child(children(child(survey, "PointerValue")).getFirst(), "Destination");
        assertEquals("destination", id(reference, version, "ref"));
        if (version == 23) assertEquals("target", id(reference, version, "bid"));
        assertRejected(db, fixture.model, output, "outside the selected baskets",
                "--basket", "source", "--basket", "collision");

        var duplicate = fixture.delivery("duplicate", fixture.basket("Data", "target", fixture.place("destination")));
        assertCliSuccess(db, "import", fixture.model, "other-target", duplicate);
        assertRejected(db, fixture.model, output, "Ambiguous stored reference target destination", "--basket", "source");
    }

    private static void assertRejected(Target db, String model, Path output, String diagnostic, String... options) throws Exception {
        var original = Files.readAllBytes(output);
        var result = cli(db, "export", model, null, output, CancellationToken.none(), options);
        assertEquals(1, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains(diagnostic), result::diagnostics);
        assertArrayEquals(original, Files.readAllBytes(output));
    }
}
