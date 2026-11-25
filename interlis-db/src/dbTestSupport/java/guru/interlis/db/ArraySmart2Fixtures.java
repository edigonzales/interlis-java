package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.ArrayFixtures.objects;
import static guru.interlis.db.ArrayFixtures.storedItems;
import static guru.interlis.db.ArrayFixtures.wrappedItems;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ArraySmart2Fixtures {
    static final List<String> INITIAL = List.of("first", "second", "second");
    static final List<String> SHORTENED = List.of("shortened");

    record Ids(String dataset, String basket, String firstObject, String otherObject) { }

    private final Path work;
    private final Path source;
    private final int version;
    final String model;

    ArraySmart2Fixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        model = "ArraySmart" + version;
        source = fixture(work, model + ".ili");
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, model, source, "--inheritance", "smart2", "--coalesce-array");
    }

    Path delivery(String file, String prefix, List<String> values, boolean other) throws Exception {
        String qualifier = version == 23 ? model + ".Data." : "";
        String tid = version == 23 ? "TID" : "i:tid";
        String entries = values.stream().map(value -> entry(qualifier, value)).collect(Collectors.joining());
        String rows = "<%1$sFirstAsset %2$s=\"%3$s-first\">%4$s<Marker>primary</Marker></%1$sFirstAsset>"
                .formatted(qualifier, tid, prefix, entries.isEmpty() ? "" : "<Items>" + entries + "</Items>")
                + "<%1$sOtherAsset %2$s=\"%3$s-other\">%4$s<Caption>secondary</Caption></%1$sOtherAsset>"
                .formatted(qualifier, tid, prefix, other ? "<Items>" + entry(qualifier, "other") + "</Items>" : "");
        return Files.writeString(work.resolve(file + ".xtf"), referenceDatasetFixture(model, version, prefix + "-b", rows));
    }

    private static String entry(String qualifier, String value) {
        return "<%1$sElem><Entry>%2$s</Entry></%1$sElem>".formatted(qualifier, value);
    }

    void assertDelivery(Path output, String prefix, List<String> values, boolean other) throws Exception {
        var rows = objects(output, version, prefix);
        assertEquals(2, rows.size());
        var firstRow = rows.get(prefix + "-first");
        var otherRow = rows.get(prefix + "-other");
        assertName(firstRow, version, model + ".Data.FirstAsset");
        assertName(otherRow, version, model + ".Data.OtherAsset");
        assertEquals(values, wrappedItems(firstRow, "Items"));
        assertEquals(other ? List.of("other") : List.of(), wrappedItems(otherRow, "Items"));
        if (values.isEmpty()) assertNull(child(firstRow, "Items"));
        if (!other) assertNull(child(otherRow, "Items"));
        assertEquals("primary", child(firstRow, "Marker").getTextContent());
        assertEquals("secondary", child(otherRow, "Caption").getTextContent());
    }

    Ids assertStoredDelivery(Target db, Connection sql, String dataset, String prefix, List<String> values, boolean other)
            throws Exception {
        String datasetId = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='" + dataset + "'");
        assertNotNull(datasetId);
        String basketId = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket")
                + " WHERE dataset=" + datasetId + " AND topic='" + model + ".Data' AND t_ili_tid='" + prefix + "-b'");
        assertNotNull(basketId);
        String first = " FROM " + db.table("firstasset") + " WHERE t_ili_tid='" + prefix + "-first'";
        String second = " FROM " + db.table("otherasset") + " WHERE t_ili_tid='" + prefix + "-other'";
        String firstId = scalar(sql, "SELECT t_id" + first);
        String otherId = scalar(sql, "SELECT t_id" + second);
        assertNotNull(firstId);
        assertNotNull(otherId);
        assertEquals(basketId, scalar(sql, "SELECT t_basket" + first));
        assertEquals(basketId, scalar(sql, "SELECT t_basket" + second));
        assertEquals("primary", scalar(sql, "SELECT marker" + first));
        assertEquals("secondary", scalar(sql, "SELECT caption" + second));
        if (values.isEmpty()) {
            assertNull(scalar(sql, "SELECT items" + first));
        } else {
            assertEquals(values, storedItems(db, sql, "firstasset", "items", prefix + "-first", values.size()));
        }
        if (other) {
            assertEquals(List.of("other"), storedItems(db, sql, "otherasset", "items", prefix + "-other", 1));
        } else {
            assertNull(scalar(sql, "SELECT items" + second));
        }
        return new Ids(datasetId, basketId, firstId, otherId);
    }
}
