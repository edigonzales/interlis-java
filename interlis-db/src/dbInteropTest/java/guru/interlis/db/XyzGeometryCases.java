package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XyzGeometryCases {
    private final Path work;
    private final int version;
    private final String model;
    private final ReferenceTool reference;

    XyzGeometryCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        model = "DbXyz" + version;
        this.reference = reference;
    }

    void exchange(TestDatabase old, TestDatabase own) throws Exception {
        var ili = work.resolve(model + ".ili");
        try (var fixture = getClass().getResourceAsStream("/" + ili.getFileName())) {
            Files.copy(fixture, ili);
        }
        reference.run(old, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--createGeomIdx", ili.toString());
        var create = cli(own, "schemaimport", model, null, ili, CancellationToken.none(), "--create-geom-index");
        assertEquals(0, create.code(), create::diagnostics);
        try (var oldSql = old.connect(); var ownSql = own.connect()) {
            var expected = DbSchemaCatalog.read(oldSql, old.postgis(), old.schema());
            if (!old.postgis()) {
                expected = expected.expectGeoPackageGeometryExtension("track", "trace", "COMPOUNDCURVE");
            }
            expected.assertSameAs(DbSchemaCatalog.read(ownSql, own.postgis(), own.schema()));
        }

        String markers = object("Marker", "above", "<Position>" + position("2600000.125", "1200000.375", "42.875") + "</Position>")
                + object("Marker", "below", "<Position>" + position("2600010.250", "1200020.500", "-31.125") + "</Position>")
                + object("Marker", "absent", "");
        String track = object("Track", "track-1", "<Trace>" + line(
                position("2600300.125", "1200300.375", "7.500")
                + position("2600310.250", "1200320.500", "-11.250")
                + position("2600320.375", "1200340.750", "12.875")) + "</Trace>");
        Path input = delivery("xyz", markers + track);
        reference.run(old, work.toString(), "--import", "--dataset", "xyz", "--importTid", "--importBid", input.toString());
        Path ownExport = work.resolve("xyz-native.xtf");
        assertCliSuccess(old, "export", model, "xyz", ownExport);
        assertTransfer(ownExport);

        assertCliSuccess(own, "import", model, "xyz", input);
        Path referenceExport = work.resolve("xyz-reference.xtf");
        reference.run(own, work.toString(), "--export", "--dataset", "xyz", "--exportTid", referenceExport.toString());
        assertTransfer(referenceExport);

        try (var sql = own.connect()) {
            assertStorage(own, sql);
            var broken = delivery("xyz-broken", markers + track + object("Track", "broken", "<Trace>" + line(
                    position("2600300.125", "1200300.375", "7.500") + position("2600320.375", "1200340.750"))
                    + "</Trace>"));
            var failure = cli(own, "replace", model, "xyz", broken, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertStorage(own, sql);
        }
    }

    private void assertStorage(TestDatabase db, Connection sql) throws SQLException {
        String table = db.table("marker");
        assertEquals("3", scalar(sql, "SELECT count(*) FROM " + table));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + table + " WHERE aposition IS NULL"));
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("track") + " WHERE t_ili_tid='track-1'"));
        if (db.postgis()) {
            assertEquals("42.875", scalar(sql, "SELECT ST_Z(aposition) FROM " + table + " WHERE t_ili_tid='above'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + table
                    + " WHERE aposition && ST_MakeEnvelope(2600000,1200000,2600001,1200001,2056)"));
        } else {
            assertEquals("1", scalar(sql, "SELECT count(*) FROM rtree_marker_aposition"
                    + " WHERE minx<=2600000.125 AND maxx>=2600000.125 AND miny<=1200000.375 AND maxy>=1200000.375"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM rtree_marker_aposition"));
        }
    }

    private void assertTransfer(Path file) throws Exception {
        var basket = children(readDataSection(file, version)).getFirst();
        assertEquals("xyz-basket", id(basket, version, "bid"));
        var markers = children(basket);
        assertEquals(4, markers.size());
        var heights = new ArrayList<BigDecimal>();
        for (var marker : markers) {
            String tid = id(marker, version, "tid");
            if (tid.equals("track-1")) {
                assertHeights(marker, "Trace", "7.5", "-11.25", "12.875");
                continue;
            }
            var field = child(marker, "Position");
            if (tid.equals("absent")) {
                assertNull(field);
            } else {
                var coords = children(children(field).getFirst());
                assertEquals(3, coords.size());
                heights.add(new BigDecimal(coords.get(2).getTextContent()).stripTrailingZeros());
            }
        }
        assertEquals(List.of(new BigDecimal("-31.125"), new BigDecimal("42.875")), heights.stream().sorted().toList());
    }

    private void assertHeights(org.w3c.dom.Element object, String field, String... expected) {
        var elements = child(object, field).getElementsByTagNameNS("*", version == 23 ? "C3" : "c3");
        assertEquals(expected.length, elements.getLength(), field);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(0, new BigDecimal(expected[i]).compareTo(new BigDecimal(elements.item(i).getTextContent())), field);
        }
    }

    private String line(String positions) {
        String tag = version == 23 ? "POLYLINE" : "g:polyline";
        return "<" + tag + ">" + positions + "</" + tag + ">";
    }

    private Path delivery(String name, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, "xyz-basket", objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(name + ".xtf"), text);
    }

    private String object(String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    private String position(String... ordinates) {
        String tag = version == 23 ? "COORD" : "g:coord";
        var xml = new StringBuilder("<").append(tag).append('>');
        for (int index = 0; index < ordinates.length; index++) {
            String axis = (version == 23 ? "C" : "g:c") + (index + 1);
            xml.append('<').append(axis).append('>').append(ordinates[index]).append("</").append(axis).append('>');
        }
        return xml.append("</").append(tag).append('>').toString();
    }
}
