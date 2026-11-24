package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.fixture;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ImportedTopicFixtures {
    private final int version;
    final String model;
    final String library;
    private final Path source;
    private final Path librarySource;
    final Path survey;
    final Path catalog;

    ImportedTopicFixtures(Path work, int version) throws Exception {
        this.version = version;
        model = "DbReachTopic" + version;
        library = "DbImportedTarget" + version;
        source = fixture(work, model + ".ili");
        librarySource = fixture(work, library + ".ili");
        survey = fixture(work, model + ".xtf");
        catalog = fixture(work, library + ".xtf");
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, model, source, "--model-file", librarySource.toString());
    }

    void removeSources() throws Exception {
        Files.delete(source);
        Files.delete(librarySource);
    }

    void assertDelivery(Path output, String prefix) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(model), headerModels(data, version));
        assertEquals(1, children(data).size());
        var basket = basket(data, version, model + ".Survey", prefix + "-basket");
        assertEquals(1, children(basket).size());
        var report = children(basket).getFirst();
        assertName(report, version, model + ".Survey.Report");
        assertEquals(prefix + "-report", id(report, version, "tid"));
        assertEquals("target-entry", id(child(report, "Site"), version, "ref"));
        var note = children(child(report, "Details")).getFirst();
        assertEquals("target-entry", id(child(note, "Destination"), version, "ref"));
    }

    void assertStoredReferences(Target db, int count) throws Exception {
        try (var sql = db.connect()) {
            String metadata = db.table("t_ili2db_classname");
            assertEquals(library + ".Catalog.Marker", scalar(sql, "SELECT iliname FROM " + metadata + " WHERE sqlname='marker'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + metadata + " WHERE iliname LIKE '" + library + ".Unused.%'"));
            String baskets = db.table("t_ili2db_basket");
            String datasets = db.table("t_ili2db_dataset");
            String target = " JOIN " + baskets + " b ON b.t_id=e.t_basket JOIN " + datasets + " d ON d.t_id=b.dataset"
                    + " WHERE b.t_ili_tid='target-basket' AND d.datasetname='catalog'";
            assertEquals(Integer.toString(count), scalar(sql, "SELECT count(*) FROM " + db.table("report") + " r"
                    + " JOIN " + db.table("note") + " n ON n.report_details=r.t_id AND n.t_basket=r.t_basket"
                    + " JOIN " + db.table("entry") + " e ON e.t_id=r.site AND e.t_id=n.destination"
                    + target + " AND e.t_ili_tid='target-entry'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("marker") + " e" + target
                    + " AND e.t_ili_tid='target-marker'"));
        }
    }
}
