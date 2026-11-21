package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.assertMissingTarget;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AlternateCrsCases {
    private final Path work;
    private final ReferenceTool reference;

    AlternateCrsCases(Path work, ReferenceTool reference) {
        this.work = work;
        this.reference = reference;
    }

    void export(TestDatabase db) throws Exception {
        createReference(db, modelFile());
        Path input = delivery("legacy");
        reference.run(db, work.toString(), "--import", "--dataset", "legacy", "--importBid", "--importTid", input.toString());
        Path baseline = work.resolve("alt-ref.xtf");
        reference.run(db, work.toString(), "--export", "--dataset", "legacy", "--exportTid", baseline.toString());
        assertDelivery(baseline, "legacy");
        Path output = work.resolve("alt-own.xtf");
        assertCliSuccess(db, "export", "Current23", "legacy", output);
        assertDelivery(output, "legacy");
        String saved = Files.readString(output);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_basket")
                    + " SET topic='Unknown23.Data' WHERE t_ili_tid='basket-old-legacy'");
        }
        var result = cli(db, "export", "Current23", "legacy", output, CancellationToken.none());
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("outside the selected model"), result::diagnostics);
        assertEquals(saved, Files.readString(output));
    }

    void exchange(TestDatabase legacy, TestDatabase own, TestDatabase rejected) throws Exception {
        Path model = modelFile();
        createReference(legacy, model);
        createOwn(own, model);
        try (var oldSql = legacy.connect(); var ownSql = own.connect()) {
            DbSchemaCatalog.read(oldSql, legacy.postgis(), legacy.schema())
                    .assertSameAs(DbSchemaCatalog.read(ownSql, own.postgis(), own.schema()));
        }
        for (var db : List.of(legacy, own)) {
            reference.run(db, work.toString(), "--import", "--dataset", "legacy", "--importBid", "--importTid",
                    delivery("legacy").toString());
            var nativeExport = work.resolve(db.schema() + "-native.xtf");
            assertCliSuccess(db, "export", "Current23", "legacy", nativeExport);
            assertDelivery(nativeExport, "legacy");

            assertCliSuccess(db, "import", "Current23", "native", delivery("native"));
            var referenceExport = work.resolve(db.schema() + "-reference.xtf");
            reference.run(db, work.toString(), "--export", "--dataset", "native", "--exportTid", referenceExport.toString());
            assertDelivery(referenceExport, "native");
            try (var sql = db.connect()) {
                String basket = db.table("t_ili2db_basket");
                String station = db.table("station");
                assertEquals("Old23.Data", scalar(sql, "SELECT topic FROM " + basket + " WHERE t_ili_tid='basket-old-native'"));
                assertEquals("", scalar(sql, "SELECT domains FROM " + basket + " WHERE t_ili_tid='basket-old-native'"));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + station
                        + " WHERE t_ili_tid='old-native' AND location_2056 IS NULL"));
                String current = db.postgis() ? "ST_SRID(location_2056)=2056" : "hex(substr(location_2056,5,4))='00000808'";
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + station + " WHERE t_ili_tid='current-native' AND " + current));
                String old = db.postgis() ? station : db.table("station_location_21781");
                String hasOld = db.postgis() ? "ST_SRID(location_21781)=21781" : "hex(substr(location_21781,5,4))='00005515'";
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + old + " WHERE t_id=(SELECT t_id FROM " + station
                        + " WHERE t_ili_tid='old-native') AND " + hasOld));
            }
        }

        String content = Files.readString(model);
        int alternate = content.indexOf("MODEL Old23");
        Path invalidModel = Files.writeString(work.resolve("Incompatible.ili"), content.substring(0, alternate)
                + content.substring(alternate).replace("TEXT*32", "TEXT*16"));
        var result = cli(rejected, "schemaimport", "Current23", null, invalidModel, CancellationToken.none(),
                "--multi-srs", "--alt-srs-model", "Current23=Old23");
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("structurally incompatible"), result::diagnostics);
        assertMissingTarget(rejected);
    }

    private static void createOwn(TestDatabase db, Path model) {
        var result = cli(db, "schemaimport", "Current23", null, model, CancellationToken.none(),
                "--multi-srs", "--alt-srs-model", "Current23=Old23");
        assertEquals(0, result.code(), result::diagnostics);
    }

    private Path modelFile() throws Exception {
        Path path = work.resolve("AlternateCrs23.ili");
        try (var resource = getClass().getResourceAsStream("/AlternateCrs23.ili")) {
            Files.copy(resource, path);
        }
        return path;
    }

    private void createReference(TestDatabase db, Path model) throws Exception {
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--models", "Current23", "--multiSrs", "--altSrsModel", "Current23=Old23", model.toString());
    }

    private Path delivery(String suffix) throws Exception {
        return Files.writeString(work.resolve("alternate-" + suffix + ".xtf"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="interop"><MODELS>
                    <MODEL NAME="Current23" VERSION="1" URI="https://example.org/interlis/db"/>
                    <MODEL NAME="Old23" VERSION="1" URI="https://example.org/interlis/db"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION>
                    <Current23.Data BID="basket-current-%1$s"><Current23.Data.Station TID="current-%1$s">
                      <Name>Current</Name><Location><COORD><C1>2600001.625</C1><C2>1200002.125</C2></COORD></Location>
                    </Current23.Data.Station></Current23.Data>
                    <Old23.Data BID="basket-old-%1$s"><Old23.Data.Station TID="old-%1$s">
                      <Name>Old</Name><Location><COORD><C1>600100.125</C1><C2>200200.375</C2></COORD></Location>
                    </Old23.Data.Station></Old23.Data>
                  </DATASECTION>
                </TRANSFER>
                """.formatted(suffix));
    }

    private static void assertDelivery(Path output, String suffix) throws Exception {
        var section = readDataSection(output, 23);
        var header = child((org.w3c.dom.Element) section.getParentNode(), "HEADERSECTION");
        var names = children(child(header, "MODELS")).stream().map(element -> element.getAttribute("NAME")).toList();
        assertTrue(names.containsAll(List.of("Current23", "Old23")), names.toString());
        var baskets = children(section);
        assertEquals(2, baskets.size());
        for (String model : List.of("Current23", "Old23")) {
            boolean current = model.equals("Current23");
            String tid = (current ? "current-" : "old-") + suffix;
            var basket = baskets.stream().filter(b -> ("basket-" + tid).equals(id(b, 23, "bid"))).findFirst().orElseThrow();
            assertEquals(model + ".Data", basket.getLocalName());
            var object = children(basket).getFirst();
            assertEquals(model + ".Data.Station", object.getLocalName());
            assertEquals(tid, id(object, 23, "tid"));
            assertEquals(current ? "Current" : "Old", child(object, "Name").getTextContent());
            var coord = child(child(object, "Location"), "COORD");
            assertEquals(0, new BigDecimal(current ? "2600001.625" : "600100.125")
                    .compareTo(new BigDecimal(child(coord, "C1").getTextContent())));
            assertEquals(0, new BigDecimal(current ? "1200002.125" : "200200.375")
                    .compareTo(new BigDecimal(child(coord, "C2").getTextContent())));
        }
    }
}
