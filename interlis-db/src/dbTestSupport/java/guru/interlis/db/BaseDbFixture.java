package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.text;
import static guru.interlis.db.DbTestSupport.transferredStructures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BaseDbFixture {
    private final Path work;
    private final int version;
    private final String model;
    private final String transfer;

    BaseDbFixture(Path work, int version) throws IOException {
        this.work = work;
        this.version = version;
        model = "DbInterop" + version;
        transfer = new String(resource(model + ".xtf"), StandardCharsets.UTF_8);
    }

    String model() { return model; }

    String transfer() { return transfer; }

    Path source() throws IOException {
        return Files.write(work.resolve(model + ".ili"), resource(model + ".ili"));
    }

    void seed(Target db) throws IOException {
        assertSchemaWithIdentityColumns(db, model, source());
        for (String dataset : List.of("legacy", "native", "tail")) {
            importDataset(db, dataset);
        }
    }

    void importDataset(Target db, String dataset) throws IOException {
        Path input = Files.writeString(work.resolve(dataset + ".xtf"), transfer.replace("legacy-", dataset + "-"));
        assertCliSuccess(db, "import", model, dataset, input);
    }

    private static byte[] resource(String name) throws IOException {
        try (var input = BaseDbFixture.class.getResourceAsStream("/" + name)) {
            return Objects.requireNonNull(input, name).readAllBytes();
        }
    }

    static void assertRows(Target db, Connection sql, int version, int datasets) throws SQLException {
        assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("t_ili2db_dataset")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("t_ili2db_basket")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("place")));
        assertEquals(Integer.toString(datasets * 4), scalar(sql, "select count(*) from " + db.table("route")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("plot")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("parcel")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("report")));
        for (var count : Map.of("nestedrecord", 2, "nestedouter", 2, "nestedinner", 3, "nestedleaf", 10).entrySet()) {
            assertEquals(Integer.toString(datasets * count.getValue()),
                    scalar(sql, "select count(*) from " + db.table(count.getKey())));
        }
        assertEquals(Integer.toString(datasets * 8), scalar(sql, "select count(*) from " + db.table("detail")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("linkdetail")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("marker")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("hub")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("markerlink")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("review")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("annotatedlink")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("pairleft")));
        assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("pairright")));
        var tables = new ArrayList<>(List.of("t_ili2db_dataset", "t_ili2db_basket", "place", "route", "plot", "parcel",
                "report", "detail", "marker", "hub", "markerlink", "review", "annotatedlink", "pairleft", "pairright", "linkdetail",
                "nestedrecord", "nestedouter", "nestedinner", "nestedleaf"));
        var hierarchyCounts = Map.of("mast", 4, "tower", 2, "arelay", 1, "beacon", 1, "servicebase", 3, "serviceunit", 3);
        for (var entry : hierarchyCounts.entrySet()) {
            assertEquals(Integer.toString(datasets * entry.getValue()),
                    scalar(sql, "select count(*) from " + db.table(entry.getKey())), entry.getKey());
        }
        tables.addAll(hierarchyCounts.keySet());
        var structureCounts = Map.of("typedrecord", 2, "notebase", 4, "countnote", 2,
                "flagnote", 1, "notekind", 2, "namednote", 2);
        for (var entry : structureCounts.entrySet()) {
            assertEquals(Integer.toString(datasets * entry.getValue()),
                    scalar(sql, "select count(*) from " + db.table(entry.getKey())), entry.getKey());
        }
        tables.addAll(structureCounts.keySet());
        for (var entry : Map.of("refnotebook", 1, "refnotebase", 2, "refnotebranch", 2, "refnoteleaf", 2).entrySet()) {
            assertEquals(Integer.toString(datasets * entry.getValue()),
                    scalar(sql, "select count(*) from " + db.table(entry.getKey())));
            tables.add(entry.getKey());
        }
        if (version == 24) {
            assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("link")));
            for (String table : List.of("refassetbase", "refassetbranch", "refassetleaf")) {
                assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table(table)));
                tables.add(table);
            }
            var collections = List.of("park", "district", "routegroup", "survey");
            for (String table : collections) {
                assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table(table)));
            }
            tables.add("link");
            tables.addAll(collections);
        }
        assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("refcarrier")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("targetpair")));
        tables.addAll(List.of("refcarrier", "targetpair"));
        for (String table : List.of("hierarchylink", "hierarchynote", "dispatchrecord")) {
            assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table(table)));
            tables.add(table);
        }
        assertTableRows(db, sql, "ratingform", datasets * 3);
        assertTableRows(db, sql, "rating", datasets * 2);
        assertTableRows(db, sql, "ratingtag", datasets * 2);
        tables.add("ratingtag");
        assertTableRows(db, sql, "relayreview", datasets);
        tables.add("relayreview");
        tables.addAll(List.of("ratingform", "rating"));
        assertTableRows(db, sql, "serviceaggregation", datasets);
        tables.add("serviceaggregation");
        for (String table : List.of("allocationbase", "taggedallocation", "contractsite", "contractsigner")) {
            assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table(table)));
            tables.add(table);
        }
        var ids = tables.stream().map(table -> "select t_id from " + db.table(table)).toList();
        assertEquals(Integer.toString(datasets * (version == 24 ? 107 : 96)),
                scalar(sql, "select count(distinct t_id) from (" + String.join(" union all ", ids) + ") ids"));
    }

    private static void assertTableRows(Target db, Connection sql, String table, int count) throws SQLException {
        assertEquals(Integer.toString(count), scalar(sql, "select count(*) from " + db.table(table)), table);
    }

    static void assertTransferredNestedTree(Element record, int version, List<String> values) {
        String prefix = version == 23 ? "DbInterop23.Data." : "";
        assertEquals(prefix + "NestedRecord", record.getLocalName());
        var tree = transferredStructures(record, "Tree");
        assertEquals(List.of(prefix + "NestedOuter"), tree.stream().map(Element::getLocalName).toList());
        var items = transferredStructures(tree.getFirst(), "Items");
        assertEquals(List.of(prefix + "NestedInner", prefix + "NestedInner"),
                items.stream().map(Element::getLocalName).toList());
        assertEquals("populated", text(items.getFirst(), "Headline"));
        assertEquals("empty", text(items.getLast(), "Headline"));
        for (var entry : List.of(Map.entry("Main", List.of("1")), Map.entry("Values", values),
                Map.entry("Extras", List.of("4", "4")))) {
            assertTransferredAmounts(items.getFirst(), entry.getKey(), version, prefix, entry.getValue());
            assertNull(child(items.getLast(), entry.getKey()));
        }
    }

    static void assertTransferredAmounts(Element owner, String attribute, int version, String prefix,
                                                  List<String> amounts) {
        var leaves = transferredStructures(owner, attribute);
        assertEquals(amounts, leaves.stream().map(leaf -> text(leaf, "Amount")).toList());
        assertTrue(leaves.stream().allMatch(leaf -> leaf.getLocalName().equals(prefix + "NestedLeaf")
                && id(leaf, version, "tid").isEmpty()));
    }
}
