package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class NumericIdentityFixtures {
    static final String SITE_BID = "7216a4c0-42af-4475-b96b-b156ae8872ec";
    static final String INSPECTION_BID = "b33dcbde-765e-4611-91e4-1620803a0516";
    static final String SITE_OID = "0011";
    static final String INSPECTION_OID = "0021";

    private final Path work;
    private final int version;
    final String model;
    final Path ili;

    NumericIdentityFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        model = "NumericObjectOid" + version;
        ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                %sMODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN SiteKey = OID 0 .. 999999;
                  TOPIC Data =
                    BASKET OID AS INTERLIS.UUIDOID;
                    CLASS Site = OID AS %s.SiteKey; Label : TEXT*30; END Site;
                    STRUCTURE Pointer = Destination : REFERENCE TO (EXTERNAL) Site; END Pointer;
                    CLASS Inspection =
                      OID AS INTERLIS.I32OID;
                      SiteLink : Pointer;
                    END Inspection;
                  END Data;
                END %s.
                """.formatted(version - 20, version == 23 ? "CONTRACTED " : "", model, model, model));
    }

    Path site(String name, String bid, String label) throws Exception {
        return transfer(name, bid, object("Site", SITE_OID, "<Label>" + label + "</Label>"));
    }

    Path inspection() throws Exception {
        String pointer = version == 23 ? model + ".Data.Pointer" : "Pointer";
        String target = version == 23 ? "<Destination REF=\"" + SITE_OID + "\" BID=\"" + SITE_BID + "\"/>"
                : "<Destination i:ref=\"" + SITE_OID + "\"/>";
        String fields = "<SiteLink><" + pointer + ">" + target + "</" + pointer + "></SiteLink>";
        return transfer("inspection", INSPECTION_BID, object("Inspection", INSPECTION_OID, fields));
    }

    private String object(String name, String oid, String fields) {
        String tag = version == 23 ? model + ".Data." + name : name;
        String attribute = version == 23 ? "TID" : "i:tid";
        return "<" + tag + " " + attribute + "=\"" + oid + "\">" + fields + "</" + tag + ">";
    }

    private Path transfer(String name, String bid, String object) throws Exception {
        return Files.writeString(work.resolve(name + ".xtf"), referenceDatasetFixture(model, version, bid, object));
    }

    void assertSite(Path output, String label) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(SITE_BID, id(basket, version, "bid"));
        var site = children(basket).getFirst();
        assertEquals("Site", site.getLocalName().substring(site.getLocalName().lastIndexOf('.') + 1));
        assertEquals(SITE_OID, id(site, version, "tid"));
        assertEquals(label, child(site, "Label").getTextContent());
    }

    void assertInspection(Path output) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(INSPECTION_BID, id(basket, version, "bid"));
        var inspection = children(basket).getFirst();
        assertEquals(INSPECTION_OID, id(inspection, version, "tid"));
        var pointer = children(child(inspection, "SiteLink")).getFirst();
        assertEquals(SITE_OID, id(child(pointer, "Destination"), version, "ref"));
    }

    void assertObjectOidMetadata(Target db, Connection sql) throws Exception {
        String columnProps = db.table("t_ili2db_column_prop");
        assertEquals(model + ".SiteKey", scalar(sql, "SELECT setting FROM " + columnProps
                + " WHERE tablename='site' AND lower(columnname)='t_ili_tid' AND tag='ch.ehi.ili2db.oidDomain'"));
        assertEquals("INTERLIS.I32OID", scalar(sql, "SELECT setting FROM " + columnProps
                + " WHERE tablename='inspection' AND lower(columnname)='t_ili_tid' AND tag='ch.ehi.ili2db.oidDomain'"));
    }

    static String siteIds(Target db, Connection sql) throws Exception {
        return scalar(sql, "SELECT basket.dataset || ':' || basket.t_id || ':' || site.t_id FROM " + db.table("site")
                + " site JOIN " + db.table("t_ili2db_basket") + " basket ON site.t_basket=basket.t_id");
    }

    void assertStoredSiteAndReference(Target db, Connection sql, String label) throws Exception {
        assertEquals(label, scalar(sql, "SELECT label FROM " + db.table("site") + " WHERE t_ili_tid='" + SITE_OID + "'"));
        assertEquals(INSPECTION_OID, scalar(sql, "SELECT t_ili_tid FROM " + db.table("inspection")));
        assertEquals(SITE_OID, scalar(sql, "SELECT site.t_ili_tid FROM " + db.table("pointer") + " link JOIN "
                + db.table("site") + " site ON link.destination=site.t_id"));
    }
}
