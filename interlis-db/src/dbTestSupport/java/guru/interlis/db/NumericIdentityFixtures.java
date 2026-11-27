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
    private static final String SITE_UUID = "7216a4c0-42af-4475-b96b-b156ae8872ec";
    private static final String INSPECTION_UUID = "b33dcbde-765e-4611-91e4-1620803a0516";

    private final Path work;
    private final int version;
    private final String siteDomain;
    private final String inspectionDomain;
    private final String inspectionOid;
    final String model;
    final Path ili;
    final String siteBid;
    final String inspectionBid;
    final String siteOid;

    NumericIdentityFixtures(Path work, int version) throws Exception {
        this(work, version, false);
    }

    static NumericIdentityFixtures forBasketOids(Path work, int version) throws Exception {
        return new NumericIdentityFixtures(work, version, true);
    }

    private NumericIdentityFixtures(Path work, int version, boolean numericBasket) throws Exception {
        this.work = work;
        this.version = version;
        model = (numericBasket ? "NumericBasketOid" : "NumericObjectOid") + version;
        String numericDomain = model + ".NumericKey";
        String basketDomain = numericBasket ? (version == 23 ? "INTERLIS.I32OID" : numericDomain) : "INTERLIS.UUIDOID";
        siteDomain = numericBasket ? "INTERLIS.UUIDOID" : numericDomain;
        inspectionDomain = numericBasket ? "INTERLIS.UUIDOID" : "INTERLIS.I32OID";
        siteBid = numericBasket ? "0010" : SITE_UUID;
        inspectionBid = numericBasket ? "0020" : INSPECTION_UUID;
        siteOid = numericBasket ? SITE_UUID : "0011";
        inspectionOid = numericBasket ? INSPECTION_UUID : "0021";
        ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                %sMODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  DOMAIN NumericKey = OID 0 .. 999999;
                  TOPIC Data =
                    BASKET OID AS %s;
                    CLASS Site = OID AS %s; Label : TEXT*30; END Site;
                    STRUCTURE Pointer = Destination : REFERENCE TO (EXTERNAL) Site; END Pointer;
                    CLASS Inspection =
                      OID AS %s;
                      SiteLink : Pointer;
                    END Inspection;
                  END Data;
                END %s.
                """.formatted(version - 20, version == 23 ? "CONTRACTED " : "", model, basketDomain, siteDomain, inspectionDomain, model));
    }

    Path site(String name, String bid, String label) throws Exception {
        return site(name, bid, siteOid, label);
    }

    Path site(String name, String bid, String oid, String label) throws Exception {
        return transfer(name, bid, object("Site", oid, "<Label>" + label + "</Label>"));
    }

    Path inspection() throws Exception {
        String pointer = version == 23 ? model + ".Data.Pointer" : "Pointer";
        String target = version == 23 ? "<Destination REF=\"" + siteOid + "\" BID=\"" + siteBid + "\"/>"
                : "<Destination i:ref=\"" + siteOid + "\"/>";
        String fields = "<SiteLink><" + pointer + ">" + target + "</" + pointer + "></SiteLink>";
        return transfer("inspection", inspectionBid, object("Inspection", inspectionOid, fields));
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
        assertSite(output, siteBid, siteOid, label);
    }

    void assertSite(Path output, String bid, String oid, String label) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(bid, id(basket, version, "bid"));
        var site = children(basket).getFirst();
        assertEquals("Site", site.getLocalName().substring(site.getLocalName().lastIndexOf('.') + 1));
        assertEquals(oid, id(site, version, "tid"));
        assertEquals(label, child(site, "Label").getTextContent());
    }

    void assertInspection(Path output) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(inspectionBid, id(basket, version, "bid"));
        var inspection = children(basket).getFirst();
        assertEquals(inspectionOid, id(inspection, version, "tid"));
        var pointer = children(child(inspection, "SiteLink")).getFirst();
        assertEquals(siteOid, id(child(pointer, "Destination"), version, "ref"));
    }

    void assertObjectOidMetadata(Target db, Connection sql) throws Exception {
        String columnProps = db.table("t_ili2db_column_prop");
        assertEquals(siteDomain, scalar(sql, "SELECT setting FROM " + columnProps
                + " WHERE tablename='site' AND lower(columnname)='t_ili_tid' AND tag='ch.ehi.ili2db.oidDomain'"));
        assertEquals(inspectionDomain, scalar(sql, "SELECT setting FROM " + columnProps
                + " WHERE tablename='inspection' AND lower(columnname)='t_ili_tid' AND tag='ch.ehi.ili2db.oidDomain'"));
    }

    String siteIds(Target db, Connection sql) throws Exception {
        return scalar(sql, "SELECT basket.dataset || ':' || basket.t_id || ':' || site.t_id FROM " + db.table("site")
                + " site JOIN " + db.table("t_ili2db_basket") + " basket ON site.t_basket=basket.t_id"
                + " WHERE CAST(site.t_ili_tid AS TEXT)='" + siteOid + "'");
    }

    void assertStoredSiteAndReference(Target db, Connection sql, String label) throws Exception {
        assertEquals(label, scalar(sql, "SELECT label FROM " + db.table("site") + " WHERE t_ili_tid='" + siteOid + "'"));
        assertEquals(inspectionOid, scalar(sql, "SELECT t_ili_tid FROM " + db.table("inspection")));
        assertEquals(siteOid, scalar(sql, "SELECT site.t_ili_tid FROM " + db.table("pointer") + " link JOIN "
                + db.table("site") + " site ON link.destination=site.t_id"));
    }
}
