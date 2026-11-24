package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.fixture;
import static guru.interlis.db.ModelFixtures.headerModels;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MultiModelFixtures {
    record Tables(String warehouse, String workshop) { }

    record Ids(String dataset, String warehouseObject, String warehouseBasket, String workshopObject, String workshopBasket) { }

    private final Path work;
    private final int version;
    final String warehouse;
    final String workshop;
    final Path warehouseSource;
    final Path workshopSource;
    final Path mixed;
    final Path singleWarehouse;

    MultiModelFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        warehouse = "DbWarehouse" + version;
        workshop = "DbWorkshop" + version;
        warehouseSource = fixture(work, warehouse + ".ili");
        workshopSource = fixture(work, workshop + ".ili");
        mixed = fixture(work, "DbMixedModels" + version + ".xtf");
        singleWarehouse = fixture(work, warehouse + ".xtf");
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, warehouse, warehouseSource, "--model", workshop,
                "--model-file", workshopSource.toString());
    }

    Path mixedWithPrefix(String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(mixed).replace("mixed-", prefix + "-"));
    }

    Path warehouseUpdate(String prefix) throws Exception {
        return Files.writeString(work.resolve("update-" + prefix + ".xtf"), Files.readString(singleWarehouse)
                .replace("fixture-", prefix + "-").replace(">warehouse<", ">updated<"));
    }

    void run(Target db, String operation, String dataset, Path transfer) {
        var result = cli(db, operation, warehouse, dataset, transfer, CancellationToken.none(), "--model", workshop);
        assertEquals(0, result.code(), result::diagnostics);
    }

    void assertDelivery(Path output, String prefix, String memo) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(Set.of(warehouse, workshop), Set.copyOf(headerModels(data, version)));
        assertEquals(2, children(data).size());
        assertEntry(basket(data, version, warehouse + ".Data", prefix + "-warehouse-basket"),
                version, warehouse, prefix + "-warehouse-entry", memo);
        assertEntry(basket(data, version, workshop + ".Data", prefix + "-workshop-basket"),
                version, workshop, prefix + "-workshop-entry", "workshop");
    }

    static void assertSingleDelivery(Path output, int version, String model) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(model), headerModels(data, version));
        assertEquals(1, children(data).size());
        assertEntry(basket(data, version, model + ".Data", "fixture-warehouse-basket"),
                version, model, "fixture-warehouse-entry", "warehouse");
    }

    private static void assertEntry(Element basket, int version, String model, String tid, String memo) {
        assertEquals(1, children(basket).size());
        var entry = children(basket).getFirst();
        assertName(entry, version, model + ".Data.Entry");
        assertEquals(tid, id(entry, version, "tid"));
        assertEquals(memo, child(entry, "Memo").getTextContent());
    }

    Tables tables(Target db) throws Exception {
        String a = tableFor(db, warehouse + ".Data.Entry");
        String b = tableFor(db, workshop + ".Data.Entry");
        assertTrue("entry".equals(a) || a != null && a.startsWith("dbwarehouse" + version), () -> "Warehouse table: " + a);
        assertTrue("entry".equals(b) || b != null && b.startsWith("dbworkshop" + version), () -> "Workshop table: " + b);
        assertTrue("entry".equals(a) ^ "entry".equals(b), () -> "Expected one unqualified table: " + a + ", " + b);
        return new Tables(a, b);
    }

    Ids storedIds(Target db, Tables tables, String dataset, String prefix, String memo) throws Exception {
        String datasets = db.table("t_ili2db_dataset");
        String baskets = db.table("t_ili2db_basket");
        try (var sql = db.connect()) {
            String datasetId = scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='" + dataset + "'");
            String warehouseRow = " FROM " + db.table(tables.warehouse()) + " e JOIN " + baskets
                    + " b ON b.t_id=e.t_basket WHERE e.t_ili_tid='" + prefix + "-warehouse-entry'"
                    + " AND b.t_ili_tid='" + prefix + "-warehouse-basket' AND b.topic='" + warehouse + ".Data' AND b.dataset=" + datasetId;
            String workshopRow = " FROM " + db.table(tables.workshop()) + " e JOIN " + baskets
                    + " b ON b.t_id=e.t_basket WHERE e.t_ili_tid='" + prefix + "-workshop-entry'"
                    + " AND b.t_ili_tid='" + prefix + "-workshop-basket' AND b.topic='" + workshop + ".Data' AND b.dataset=" + datasetId;
            assertEquals(memo, scalar(sql, "SELECT e.memo" + warehouseRow));
            assertEquals("workshop", scalar(sql, "SELECT e.memo" + workshopRow));
            var ids = new Ids(datasetId, scalar(sql, "SELECT e.t_id" + warehouseRow), scalar(sql, "SELECT b.t_id" + warehouseRow),
                    scalar(sql, "SELECT e.t_id" + workshopRow), scalar(sql, "SELECT b.t_id" + workshopRow));
            assertEquals(5, new HashSet<>(List.of(ids.dataset(), ids.warehouseObject(), ids.warehouseBasket(),
                    ids.workshopObject(), ids.workshopBasket())).size());
            return ids;
        }
    }
}
