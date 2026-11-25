package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;

import static guru.interlis.db.BaseDbFixture.MARKER_LABELS;
import static guru.interlis.db.BaseDbFixture.expectedMarkerLinks;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BaseAssociationRows {
    private BaseAssociationRows() { }

    static void assertStored(Target db, Connection sql, int version, String dataset) throws SQLException {
        assertStoredHierarchyRoles(db, sql, version, dataset);
        assertStoredAggregationAndComposition(db, sql, dataset);
        assertStoredRoles(db, sql, dataset);
        assertStoredPairing(db, sql, version, dataset);
        assertStoredStandaloneLinks(db, sql, dataset);
        assertStoredHierarchyLinks(db, sql, dataset);
        assertStoredRatingTags(db, sql, version, dataset);
        assertStoredEmbeddedLink(db, sql, "relayreview", "reviewingrelays", "mast", dataset + "-relay",
                "recommended", "servicebase", dataset + "-serviceunit", "accepted");
        assertStoredEmbeddedLink(db, sql, "rating", "ratingforms", "ratingform", dataset + "-ratingform-1",
                "rated", "mast", dataset + "-relay", "checked");
        assertStoredEmbeddedLink(db, sql, "rating", "ratingforms", "ratingform", dataset + "-ratingform-2",
                "rated", "mast", dataset + "-tower", null);
        assertStoredEmbeddedLink(db, sql, "rating", "ratingforms", "ratingform", dataset + "-ratingform-3",
                "rated", "mast", null, null);
        assertStoredTernaryLink(db, sql, dataset);
        assertStoredAssociationHierarchy(db, sql, dataset);
        assertStoredAttributedLinks(db, sql, dataset, "review", "reviewmarkers", "witness", "hub", "reviewed");
        assertStoredAttributedLinks(db, sql, dataset, "annotatedlink", "consultedmarker", "consultedreport", "report", "confirmed");
    }

    private static void assertStoredHierarchyRoles(Target db, Connection sql, int version, String dataset)
            throws SQLException {
        String tower = " from " + db.table("tower") + " t join " + db.table("mast")
                + " m on m.t_id=t.t_id and m.t_basket=t.t_basket where m.t_ili_tid='" + dataset;
        String relay = tower + "-relay'";
        String empty = tower + "-tower'";
        String service = " from " + db.table("servicebase") + " where t_ili_tid='" + dataset + "-serviceunit'";
        String relayId = scalar(sql, "select t.t_id" + relay);
        String serviceId = scalar(sql, "select t_id" + service);
        assertNotNull(relayId);
        assertNotNull(serviceId);
        assertEquals(relayId, scalar(sql, "select supervisor" + service));
        assertEquals(relayId, scalar(sql, "select t.mentor" + relay));
        assertNull(scalar(sql, "select t.mentor" + empty));
        if (version == 23) {
            assertEquals(serviceId, scalar(sql, "select t.pairedservice" + relay));
            assertNull(scalar(sql, "select t.pairedservice" + empty));
        } else {
            assertEquals(relayId, scalar(sql, "select pairedtower" + service));
        }
    }

    private static void assertStoredAggregationAndComposition(Target db, Connection sql, String dataset)
            throws SQLException {
        String roles = """
                from %s a
                join %s r on r.t_id=a.groupwhole and r.t_basket=a.t_basket
                join %s s on s.t_id=a.grouppart and s.t_basket=a.t_basket
                join %s b on b.t_id=a.t_basket
                where r.t_ili_tid='%s-refcarrier' and s.t_ili_tid='%s-serviceunit' and b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("serviceaggregation"), db.table("refcarrier"), db.table("servicebase"),
                db.table("t_ili2db_basket"), dataset, dataset, dataset);
        assertEquals("1", scalar(sql, "select count(*) " + roles));
        String composition = """
                select m.t_ili_tid from %s s
                join %s m on m.t_id=s.wholemast and m.t_basket=s.t_basket
                where s.t_ili_tid='%s-serviceunit'
                """.formatted(db.table("servicebase"), db.table("mast"), dataset);
        assertEquals(dataset + "-beacon", scalar(sql, composition));
    }

    private static void assertStoredRoles(Target db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select m.t_ili_tid, m.aselect, m.aselect1, b.t_ili_tid as bid, m.t_basket as marker_basket,
                       h.t_id as hub_id, h.t_ili_tid as hub_tid, h.t_basket as hub_basket
                from %s m left join %s h on h.t_id=m.aselect1
                join %s b on b.t_id=m.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' order by m.t_ili_tid
                """.formatted(db.table("marker"), db.table("hub"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            for (int index = 0; index < MARKER_LABELS.size(); index++) {
                assertTrue(result.next());
                assertEquals(dataset + "-marker-" + (index + 1), result.getString("t_ili_tid"));
                assertEquals(MARKER_LABELS.get(index), result.getString("aselect"));
                if (index < 2) {
                    assertEquals(dataset + "-basket-1", result.getString("bid"));
                    assertEquals(dataset + "-hub-1", result.getString("hub_tid"));
                    assertEquals(result.getString("hub_id"), result.getString("aselect1"));
                    assertEquals(result.getString("marker_basket"), result.getString("hub_basket"));
                } else {
                    assertEquals(dataset + "-basket-2", result.getString("bid"));
                    assertNull(result.getString("aselect1"));
                }
            }
            assertFalse(result.next());
        }
    }

    private static void assertStoredPairing(Target db, Connection sql, int version, String dataset) throws SQLException {
        String owner = version == 23 ? "pairright" : "pairleft";
        String target = version == 23 ? "pairleft" : "pairright";
        String column = version == 23 ? "leftlink" : "rightlink";
        String rows = """
                select o.t_ili_tid as owner_tid, o.%s as stored_id, t.t_id as target_id,
                       t.t_ili_tid as target_tid, o.t_basket as owner_basket, t.t_basket as target_basket, b.t_ili_tid as bid
                from %s o left join %s t on t.t_id=o.%s
                join %s b on b.t_id=o.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' order by o.t_ili_tid
                """.formatted(column, db.table(owner), db.table(target), column, db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals(dataset + "-" + owner + "-1", result.getString("owner_tid"));
            assertEquals(dataset + "-" + target + "-1", result.getString("target_tid"));
            assertEquals(result.getLong("target_id"), result.getLong("stored_id"));
            assertEquals(result.getLong("target_basket"), result.getLong("owner_basket"));
            assertEquals(dataset + "-basket-2", result.getString("bid"));
            if (version == 24) {
                assertTrue(result.next());
                assertEquals(dataset + "-pairleft-2", result.getString("owner_tid"));
                assertNull(result.getObject("stored_id"));
            }
            assertFalse(result.next());
        }
    }

    private static void assertStoredStandaloneLinks(Target db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select l.t_id, b.t_ili_tid, m.t_ili_tid, h.t_ili_tid, l.t_basket, m.t_basket, h.t_basket
                from %s l join %s m on m.t_id=l.linkedmarker join %s h on h.t_id=l.linkedhub
                join %s b on b.t_id=l.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s'
                """.formatted(db.table("markerlink"), db.table("marker"), db.table("hub"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        var pairs = new HashSet<List<String>>();
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            while (result.next()) {
                assertTrue(result.getLong(1) > 0);
                assertEquals(dataset + "-basket-1", result.getString(2));
                assertEquals(result.getLong(5), result.getLong(6));
                assertEquals(result.getLong(5), result.getLong(7));
                assertTrue(pairs.add(List.of(result.getString(3), result.getString(4))));
            }
        }
        assertEquals(expectedMarkerLinks(dataset), pairs);
    }

    private static void assertStoredHierarchyLinks(Target db, Connection sql, String dataset) throws SQLException {
        String mast = db.table("mast");
        String service = db.table("servicebase");
        String basket = db.table("t_ili2db_basket");
        String bare = """
                select m.t_ili_tid, s.t_ili_tid
                from %s l join %s m on m.t_id=l.workbranch and m.t_basket=l.t_basket
                join %s t on t.t_id=m.t_id and t.t_basket=l.t_basket
                join %s s on s.t_id=l.staffroot and s.t_basket=l.t_basket
                join %s b on b.t_id=l.t_basket where b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("hierarchylink"), mast, db.table("tower"), service, basket, dataset);
        String attributed = """
                select m.t_ili_tid, s.t_ili_tid, n.memo, n.severity
                from %s n join %s m on m.t_id=n.raisedat and m.t_basket=n.t_basket
                join %s s on s.t_id=n.sharedat and s.t_basket=n.t_basket
                join %s b on b.t_id=n.t_basket where b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("hierarchynote"), mast, service, basket, dataset);
        try (var statement = sql.createStatement()) {
            try (var row = statement.executeQuery(bare)) {
                assertTrue(row.next());
                assertEquals(dataset + "-relay", row.getString(1));
                assertEquals(dataset + "-serviceunit", row.getString(2));
                assertFalse(row.next());
            }
            try (var row = statement.executeQuery(attributed)) {
                assertTrue(row.next());
                assertEquals(dataset + "-beacon", row.getString(1));
                assertEquals(dataset + "-serviceunit", row.getString(2));
                assertEquals("reviewed", row.getString(3));
                assertEquals("major", row.getString(4));
                assertFalse(row.next());
            }
        }
    }

    private static void assertStoredRatingTags(Target db, Connection sql, int version, String dataset)
            throws SQLException {
        String owner = version == 23 ? "taggedform" : "taggedunit";
        String target = version == 23 ? "taggedunit" : "taggedform";
        String ownerRoot = version == 23 ? "ratingform" : "servicebase";
        String targetRoot = version == 23 ? "servicebase" : "ratingform";
        for (int index = 1; index <= 3; index++) {
            String form = dataset + "-ratingform-" + index;
            String unit = dataset + "-serviceunit" + (index == 1 ? "" : "-" + index);
            assertStoredEmbeddedLink(db, sql, "ratingtag", owner, ownerRoot, version == 23 ? form : unit,
                    target, targetRoot, index == 3 ? null : version == 23 ? unit : form, index == 1 ? "tagged" : null);
        }
    }

    private static void assertStoredEmbeddedLink(Target db, Connection sql, String table, String ownerColumn,
                                                 String ownerRoot, String ownerTid, String targetColumn,
                                                 String targetRoot, String targetTid, String memo) throws SQLException {
        String query = """
                select t.t_ili_tid, link.memo from %s link
                join %s o on o.t_id=link.%s and o.t_basket=link.t_basket
                left join %s t on t.t_id=link.%s and t.t_basket=link.t_basket
                where o.t_ili_tid='%s'
                """.formatted(db.table(table), db.table(ownerRoot), ownerColumn, db.table(targetRoot), targetColumn, ownerTid);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            if (targetTid != null) {
                assertTrue(rows.next(), ownerTid);
                assertEquals(targetTid, rows.getString(1));
                assertEquals(memo, rows.getString(2));
            }
            assertFalse(rows.next(), ownerTid);
        }
    }

    private static void assertStoredTernaryLink(Target db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select a.t_ili_tid, p.t_ili_tid, m.t_ili_tid, d.memo, d.severity
                from %s d join %s a on a.t_id=d.dispatchleft and a.t_basket=d.t_basket
                join %s p on p.t_id=d.dispatchlocation and p.t_basket=d.t_basket
                join %s t on t.t_id=d.dispatchmast and t.t_basket=d.t_basket
                join %s m on m.t_id=t.t_id and m.t_basket=d.t_basket
                join %s b on b.t_id=d.t_basket where b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("dispatchrecord"), db.table("pairleft"), db.table("place"), db.table("tower"),
                db.table("mast"), db.table("t_ili2db_basket"), dataset);
        try (var statement = sql.createStatement(); var row = statement.executeQuery(rows)) {
            assertTrue(row.next());
            assertEquals(dataset + "-pairleft-1", row.getString(1));
            assertEquals(dataset + "-place-3", row.getString(2));
            assertEquals(dataset + "-relay", row.getString(3));
            assertEquals("dispatched", row.getString(4));
            assertEquals("minor", row.getString(5));
            assertFalse(row.next());
        }
    }

    private static void assertStoredAssociationHierarchy(Target db, Connection sql, String dataset)
            throws SQLException {
        String rows = """
                select a.t_type, s.t_ili_tid, g.t_ili_tid, a.basenote, t.detailrank from %s a
                join %s t on t.t_id=a.t_id and t.t_basket=a.t_basket
                join %s s on s.t_id=a.listedsite and s.t_basket=a.t_basket
                join %s g on g.t_id=a.listedsigner and g.t_basket=a.t_basket
                join %s b on b.t_id=a.t_basket where b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("allocationbase"), db.table("taggedallocation"), db.table("contractsite"),
                db.table("contractsigner"), db.table("t_ili2db_basket"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals("taggedallocation", result.getString(1));
            assertEquals(dataset + "-contract-site", result.getString(2));
            assertEquals(dataset + "-contract-signer", result.getString(3));
            assertEquals("base allocation", result.getString(4));
            assertEquals("17", result.getString(5));
            assertFalse(result.next());
        }
    }

    private static void assertStoredAttributedLinks(Target db, Connection sql, String dataset, String association,
                                                   String markerColumn, String targetColumn, String targetTable,
                                                   String memo) throws SQLException {
        boolean annotated = association.equals("annotatedlink");
        String rows = """
                select r.t_id, b.t_ili_tid, m.t_ili_tid, h.t_ili_tid, r.t_basket, m.t_basket, h.t_basket, r.memo, r.grade, %s
                from %s r join %s m on m.t_id=r.%s join %s h on h.t_id=r.%s
                join %s b on b.t_id=r.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' order by m.t_ili_tid
                """.formatted(annotated ? "r.localdecision" : "null", db.table(association), db.table("marker"),
                markerColumn, db.table(targetTable), targetColumn, db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"),
                dataset);
        String emptyMemo = annotated ? "" : null;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            for (int index = 1; index <= 2; index++) {
                assertTrue(result.next());
                assertTrue(result.getLong(1) > 0);
                assertEquals(dataset + "-basket-1", result.getString(2));
                assertEquals(dataset + "-marker-" + index, result.getString(3));
                assertEquals(dataset + "-" + targetTable + "-1", result.getString(4));
                assertEquals(result.getLong(5), result.getLong(6));
                assertEquals(result.getLong(5), result.getLong(7));
                assertEquals(index == 1 ? memo : emptyMemo, result.getString(8));
                assertEquals(index == 1 ? "-3" : null, result.getString(9));
                assertEquals(index == 1 && annotated ? "approved" : null, result.getString(10));
            }
            assertFalse(result.next());
        }
    }
}
