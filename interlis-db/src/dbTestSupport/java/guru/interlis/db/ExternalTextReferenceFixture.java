package guru.interlis.db;

import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

record ExternalTextReferenceFixture(Path work, int version) {
    static final String REMOTE = "0007";

    String model() { return "ExternalText" + version; }

    Path source(String model) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Catalog =
                    CLASS Site = END Site;
                  END Catalog;
                  TOPIC Data =
                    DEPENDS ON %s.Catalog;
                    CLASS Observation = END Observation;
                    STRUCTURE Annotation =
                      SiteRef : REFERENCE TO (EXTERNAL) %s.Catalog.Site;
                      ObservationRef : REFERENCE TO Observation;
                    END Annotation;
                    CLASS Report =
                      Note : Annotation;
                      %s
                    END Report;
                    ASSOCIATION Marking =
                      Reports -- {0..*} Report;
                      SiteMark (EXTERNAL) -- {0..1} %s.Catalog.Site;
                    END Marking;
                  END Data;
                END %s.
                """.formatted(version - 20, model, model, model, version == 24
                ? "DirectSite : REFERENCE TO (EXTERNAL) " + model + ".Catalog.Site;" : "", model, model));
    }

    Path transfer(String model, String dataset) throws Exception {
        String local = dataset + "-local";
        String annotation = object(model, "Annotation", null, reference("SiteRef", REMOTE, true)
                + reference("ObservationRef", local, false));
        String report = "<Note>" + annotation + "</Note>";
        if (version == 24) report += reference("DirectSite", REMOTE, true);
        report += reference("SiteMark", REMOTE, true);
        String objects = object(model, "Report", dataset + "-report", report)
                + object(model, "Report", dataset + "-empty", "<Note>" + object(model, "Annotation", null, "") + "</Note>")
                + object(model, "Observation", local, "");
        return Files.writeString(work.resolve(dataset + ".xtf"), referenceDatasetFixture(model, version, dataset + "-basket", objects));
    }

    private String object(String model, String name, String tid, String fields) {
        String tag = version == 23 ? model + ".Data." + name : name;
        String identity = tid == null ? "" : (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\"";
        return "<" + tag + identity + ">" + fields + "</" + tag + ">";
    }

    private String reference(String field, String target, boolean external) {
        return "<" + field + (version == 23 ? " REF=\"" : " i:ref=\"") + target + "\""
                + (version == 23 && external ? " BID=\"unloaded-basket\"" : "") + "/>";
    }

    void assertTransfer(Path path, String dataset) throws Exception {
        var basket = children(readDataSection(path, version)).getFirst();
        assertEquals(dataset + "-basket", id(basket, version, "bid"));
        var objects = children(basket).stream().collect(Collectors.toMap(row -> id(row, version, "tid"), row -> row));
        var report = objects.get(dataset + "-report");
        var note = children(child(report, "Note")).getFirst();
        assertTextReference(child(note, "SiteRef"), REMOTE);
        assertEquals(dataset + "-local", id(child(note, "ObservationRef"), version, "ref"));
        var empty = objects.get(dataset + "-empty");
        assertNull(child(children(child(empty, "Note")).getFirst(), "SiteRef"));
        assertTextReference(child(report, "SiteMark"), REMOTE);
        assertNull(child(empty, "SiteMark"));
        if (version == 24) {
            assertTextReference(child(report, "DirectSite"), REMOTE);
            assertNull(child(empty, "DirectSite"));
        }
    }

    void assertTextReference(Element element, String expected) {
        assertEquals(expected, id(element, version, "ref"));
        assertEquals("", id(element, version, "bid"));
    }

    void assertStored(DbTestSupport.Target db, Connection sql, String dataset) throws Exception {
        assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("site")));
        assertEquals("enable", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_settings")
                + " WHERE tag='ch.ehi.ili2db.SqlExtRefCols'"));
        String report = db.table("report");
        assertEquals("embedded", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_trafo")
                + " WHERE iliname='" + model() + ".Data.Marking' AND tag='ch.ehi.ili2db.inheritance'"));
        assertEquals(REMOTE, scalar(sql, "SELECT sitemark FROM " + report
                + " WHERE t_ili_tid='" + dataset + "-report'"));
        assertNull(scalar(sql, "SELECT sitemark FROM " + report + " WHERE t_ili_tid='" + dataset + "-empty'"));
        String joinedNote = " FROM " + db.table("annotation") + " n JOIN " + report
                + " r ON r.t_id=n.report_note WHERE r.t_ili_tid='" + dataset;
        assertEquals(REMOTE, scalar(sql, "SELECT n.siteref" + joinedNote + "-report'"));
        assertNull(scalar(sql, "SELECT n.siteref" + joinedNote + "-empty'"));
        assertEquals(dataset + "-local", scalar(sql, "SELECT o.t_ili_tid FROM " + db.table("observation") + " o WHERE o.t_id=("
                + "SELECT n.observationref" + joinedNote + "-report')"));
        var keys = foreignKeyColumns(db, sql);
        assertTrue(keys.contains("annotation:observationref:observation"), keys::toString);
        assertFalse(keys.stream().anyMatch(key -> key.startsWith("annotation:siteref:") || key.startsWith("report:directsite:")
                || key.startsWith("report:sitemark:")));
        if (version == 24) assertEquals(REMOTE, scalar(sql, "SELECT directsite FROM " + report
                + " WHERE t_ili_tid='" + dataset + "-report'"));
    }
}
