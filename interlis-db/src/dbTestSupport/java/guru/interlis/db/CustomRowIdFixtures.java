package guru.interlis.db;

import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CustomRowIdFixtures {
    static final String SETTING = "ch.ehi.ili2db.colName_T_ID";
    static final String KEY = "Row_Custom";

    private final Path work;
    private final int version;
    private final ProfileFixtures profile;
    final String model;

    CustomRowIdFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
        profile = new ProfileFixtures(work, version);
        model = "DbCustomRowId" + version;
    }

    Path modelFile() throws Exception {
        return profile.source(model, """
                DOMAIN Position = COORD 2460000.000..2870000.000, 1045000.000..1310000.000;
                STRUCTURE Memo = Comment : TEXT*24; END Memo;
                CLASS Site = Label : TEXT*24; END Site;
                CLASS Asset =
                  Center : Position;
                  Checkpoint : Position;
                  Detail : Memo;
                END Asset;
                ASSOCIATION Location =
                  AssignedSite -- {0..1} Site;
                  Assets -- {0..*} Asset;
                END Location;
                """);
    }

    Path delivery(String file, String prefix, String x, String comment) throws Exception {
        String structureTag = version == 23 ? model + ".Data.Memo" : "Memo";
        String fields = "<Center>" + profile.point(x, "1201000.250") + "</Center>"
                + "<Checkpoint>" + profile.point("2602000.375", "1202000.500") + "</Checkpoint>"
                + "<Detail><" + structureTag + "><Comment>" + comment + "</Comment></" + structureTag + "></Detail>"
                + profile.reference("AssignedSite", prefix + "-site");
        String objects = profile.object(model, "Asset", prefix + "-asset", fields)
                + profile.object(model, "Site", prefix + "-site", "<Label>destination</Label>");
        return Files.writeString(work.resolve(file + ".xtf"),
                referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    void assertDelivery(Path output, String prefix, String x, String comment) throws Exception {
        var rows = profile.rows(output, prefix, 2);
        var asset = rows.getFirst();
        assertEquals(prefix + "-asset", id(asset, version, "tid"));
        assertEquals(prefix + "-site", id(rows.getLast(), version, "tid"));
        assertEquals(prefix + "-site", id(child(asset, "AssignedSite"), version, "ref"));
        assertEquals(comment, child(children(child(asset, "Detail")).getFirst(), "Comment").getTextContent());
        assertPoint(asset, "Center", x, "1201000.250");
        assertPoint(asset, "Checkpoint", "2602000.375", "1202000.500");
    }

    private static void assertPoint(Element asset, String field, String x, String y) {
        var axes = children(children(child(asset, field)).getFirst());
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(axes.get(0).getTextContent())), field);
        assertEquals(0, new BigDecimal(y).compareTo(new BigDecimal(axes.get(1).getTextContent())), field);
    }

    static String physicalKey(DbTestSupport.Target db) {
        return db.postgis() ? "row_custom" : KEY;
    }

    static String key(DbTestSupport.Target db) {
        return Database.quote(physicalKey(db));
    }

    static String assetId(DbTestSupport.Target db, Connection sql, String prefix) throws Exception {
        return scalar(sql, "SELECT " + key(db) + " FROM " + db.table("asset") + " WHERE t_ili_tid='" + prefix + "-asset'");
    }

    static void assertRelation(DbTestSupport.Target db, Connection sql, String prefix) throws Exception {
        String key = key(db);
        String asset = db.table("asset");
        String site = db.table("site");
        String memo = db.table("memo");
        String baskets = db.table("t_ili2db_basket");
        String datasets = db.table("t_ili2db_dataset");
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + asset + " a JOIN " + site + " s ON s." + key
                + " = a.assignedsite JOIN " + memo + " m ON m.asset_detail = a." + key + " JOIN " + baskets
                + " b ON b." + key + " = a.t_basket JOIN " + datasets + " d ON d." + key + " = b.dataset"
                + " WHERE a.t_ili_tid='" + prefix + "-asset' AND s.t_ili_tid='" + prefix + "-site'"
                + " AND s.t_basket=b." + key + " AND b.t_ili_tid='" + prefix + "-basket' AND d.datasetname='" + prefix + "'"));
    }

    static void assertSpatialIndex(DbTestSupport.Target db, Connection sql, String expectedId, String x) throws Exception {
        String query = db.postgis()
                ? "SELECT " + key(db) + " FROM " + db.table("asset")
                  + " WHERE ST_Intersects(center,ST_MakeEnvelope(" + x + "-1,1200999," + x + "+1,1201001,2056))"
                : "SELECT id FROM rtree_asset_center WHERE minx <= " + x + "+1 AND maxx >= " + x + "-1";
        assertEquals(expectedId, scalar(sql, query));
    }
}
