package guru.interlis.db;

import guru.interlis.db.BaseDbFixture.DetailValue;
import guru.interlis.db.DbTestSupport.Target;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static guru.interlis.db.BaseDbFixture.ENTRIES;
import static guru.interlis.db.BaseDbFixture.LATER_ENTRY;
import static guru.interlis.db.BaseDbFixture.OBSERVATIONS;
import static guru.interlis.db.BaseDbFixture.SUMMARY;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BaseStructureRows {
    private BaseStructureRows() { }

    static void assertStored(Target db, Connection sql, int version, String dataset) throws SQLException {
        assertStoredHierarchyReferences(db, sql, version, dataset);
        assertStoredInheritedStructures(db, sql, dataset);
        assertStoredStructures(db, sql, dataset);
        assertStoredNestedStructures(db, sql, dataset);
        assertStoredStructureHierarchy(db, sql, dataset);
        assertStoredPolymorphicTargets(db, sql, dataset, version);
        assertStoredStructureReferences(db, sql, dataset);
    }

    private static void assertStoredHierarchyReferences(Target db, Connection sql, int version, String dataset)
            throws SQLException {
        String notes = """
                join %s book on book.t_id=src.refnotebook_entries and book.t_basket=src.t_basket
                where book.t_ili_tid='%s-ref-notebook' order by src.t_seq
                """.formatted(db.table("refnotebook"), dataset);
        assertReferenceFragmentRows(db, sql, "refnote", dataset, notes);
        if (version == 24) {
            assertReferenceFragmentRows(db, sql, "refasset", dataset,
                    " where src.t_ili_tid in ('" + dataset + "-ref-asset-1','" + dataset
                            + "-ref-asset-2') order by src.t_ili_tid");
        }
    }

    private static void assertReferenceFragmentRows(Target db, Connection sql, String prefix, String dataset,
                                                    String selection) throws SQLException {
        String mast = db.table("mast");
        String query = """
                select src.label, src.roottarget, branch.branchtarget, leaf.owntarget,
                       root_target.t_ili_tid, branch_target.t_ili_tid, own_target.t_ili_tid
                from %s src
                join %s branch on branch.t_id=src.t_id and branch.t_basket=src.t_basket
                join %s leaf on leaf.t_id=src.t_id and leaf.t_basket=src.t_basket
                left join %s root_target on root_target.t_id=src.roottarget and root_target.t_basket=src.t_basket
                left join %s branch_target on branch_target.t_id=branch.branchtarget and branch_target.t_basket=src.t_basket
                left join %s own_target on own_target.t_id=leaf.owntarget and own_target.t_basket=src.t_basket
                """.formatted(db.table(prefix + "base"), db.table(prefix + "branch"), db.table(prefix + "leaf"),
                mast, mast, mast) + selection;
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertEquals("populated", rows.getString(1));
            assertEquals(dataset + "-beacon", rows.getString(5));
            assertEquals(dataset + "-relay", rows.getString(6));
            assertEquals(dataset + "-mast", rows.getString(7));
            assertTrue(rows.next());
            assertEquals("empty", rows.getString(1));
            assertNull(rows.getObject(2));
            assertNull(rows.getObject(3));
            assertNull(rows.getObject(4));
            assertFalse(rows.next());
        }
    }

    private static void assertStoredInheritedStructures(Target db, Connection sql, String dataset)
            throws SQLException {
        String rootObjects = "('" + dataset + "-mast','" + dataset + "-relay','"
                + dataset + "-tower','" + dataset + "-beacon')";
        String notes = """
                select m.t_ili_tid, m.t_basket, l.t_basket, l.t_seq, l.amount, l.t_ili_tid,
                       l.nestedinner_main, l.nestedinner_values, l.nestedinner_extras
                from %s l join %s m on m.t_id=l.mast_notes
                where m.t_ili_tid in %s
                order by m.t_ili_tid, l.t_seq
                """.formatted(db.table("nestedleaf"), db.table("mast"), rootObjects);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(notes)) {
            for (var expected : List.of(List.of("mast", "0", "5"), List.of("mast", "1", "6"),
                    List.of("relay", "0", "7"))) {
                assertTrue(rows.next());
                assertEquals(dataset + "-" + expected.get(0), rows.getString(1));
                assertEquals(rows.getString(2), rows.getString(3));
                assertEquals(expected.get(1), rows.getString(4));
                assertEquals(expected.get(2), rows.getString(5));
                for (int column = 6; column <= 9; column++) assertNull(rows.getString(column));
            }
            assertFalse(rows.next());
        }

        String supplement = """
                select m.t_ili_tid, m.t_basket, t.t_basket, o.t_basket, i.t_basket, l.t_basket,
                       o.t_seq, i.t_seq, l.t_seq, i.headline, l.amount,
                       o.nestedrecord_tree, l.mast_notes, l.nestedinner_values, l.nestedinner_extras
                from %s m join %s t on t.t_id=m.t_id
                join %s o on o.tower_supplement=t.t_id
                join %s i on i.nestedouter_items=o.t_id
                join %s l on l.nestedinner_main=i.t_id
                where m.t_ili_tid in %s
                """.formatted(db.table("mast"), db.table("tower"), db.table("nestedouter"),
                db.table("nestedinner"), db.table("nestedleaf"), rootObjects);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(supplement)) {
            assertTrue(rows.next());
            assertEquals(dataset + "-relay", rows.getString(1));
            for (int column = 3; column <= 6; column++) assertEquals(rows.getString(2), rows.getString(column));
            for (int column = 7; column <= 9; column++) assertEquals("0", rows.getString(column));
            assertEquals("inherited", rows.getString(10));
            assertEquals("8", rows.getString(11));
            for (int column = 12; column <= 15; column++) assertNull(rows.getString(column));
            assertFalse(rows.next());
        }
    }

    private static void assertStoredStructures(Target db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select d.t_id as detail_id, d.t_basket as detail_basket, d.t_ili_tid as detail_tid, d.t_seq,
                       d.report_summary, d.report_entries, d.report_observations, d.label, d.enabled, d.quantity, d.decision,
                       r.t_id as report_id, r.t_basket as report_basket, r.t_ili_tid as report_tid, b.t_ili_tid as bid
                from %s d join %s r on r.t_id=coalesce(d.report_summary, d.report_entries, d.report_observations)
                join %s b on b.t_id=r.t_basket join %s s on s.t_id=b.dataset
                where s.datasetname='%s'
                order by r.t_ili_tid,
                         case when d.report_summary is not null then 0 when d.report_entries is not null then 1 else 2 end,
                         d.t_seq
                """.formatted(db.table("detail"), db.table("report"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            String firstReport = dataset + "-report-1";
            String firstBasket = dataset + "-basket-1";
            assertStoredDetail(result, firstReport, firstBasket, "report_summary", 0, SUMMARY);
            for (int index = 0; index < ENTRIES.size(); index++) {
                assertStoredDetail(result, firstReport, firstBasket, "report_entries", index, ENTRIES.get(index));
            }
            for (int index = 0; index < OBSERVATIONS.size(); index++) {
                assertStoredDetail(result, firstReport, firstBasket, "report_observations", index, OBSERVATIONS.get(index));
            }
            assertStoredDetail(result, dataset + "-report-2", dataset + "-basket-2", "report_entries", 0, LATER_ENTRY);
            assertFalse(result.next());
        }
    }

    private static void assertStoredDetail(ResultSet result, String tid, String bid, String owner, int sequence,
                                           DetailValue expected) throws SQLException {
        assertTrue(result.next());
        assertTrue(result.getLong("detail_id") > 0);
        assertEquals(result.getString("report_basket"), result.getString("detail_basket"));
        assertNull(result.getString("detail_tid"));
        assertEquals(tid, result.getString("report_tid"));
        assertEquals(bid, result.getString("bid"));
        assertEquals(result.getString("report_id"), result.getString(owner));
        for (String column : List.of("report_summary", "report_entries", "report_observations")) {
            if (!owner.equals(column)) assertNull(result.getString(column), column);
        }
        assertEquals(Integer.toString(sequence), result.getString("t_seq"));
        boolean enabled = result.getBoolean("enabled");
        Boolean optionalEnabled = result.wasNull() ? null : enabled;
        int quantity = result.getInt("quantity");
        Integer optionalQuantity = result.wasNull() ? null : quantity;
        assertEquals(expected, new DetailValue(result.getString("label"), optionalEnabled, optionalQuantity,
                result.getString("decision")));
    }

    private static void assertStoredNestedStructures(Target db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select r.t_ili_tid as root_tid, b.t_ili_tid as bid, r.t_basket as root_basket,
                       o.t_basket as outer_basket, o.t_ili_tid as outer_tid, o.t_seq as outer_seq,
                       i.t_id as inner_id, i.t_basket as inner_basket, i.t_ili_tid as inner_tid, i.t_seq as inner_seq,
                       i.headline, l.t_basket as leaf_basket, l.t_ili_tid as leaf_tid, l.t_seq as leaf_seq, l.amount,
                       l.nestedinner_main, l.nestedinner_values, l.nestedinner_extras
                from %s r join %s b on b.t_id=r.t_basket
                left join %s o on o.nestedrecord_tree=r.t_id
                left join %s i on i.nestedouter_items=o.t_id
                left join %s l on coalesce(l.nestedinner_main, l.nestedinner_values, l.nestedinner_extras)=i.t_id
                where r.t_ili_tid in ('%s-nested-1', '%s-nested-2')
                order by r.t_ili_tid, i.t_seq,
                         case when l.nestedinner_main is not null then 0
                              when l.nestedinner_values is not null then 1 else 2 end, l.t_seq
                """.formatted(db.table("nestedrecord"), db.table("t_ili2db_basket"), db.table("nestedouter"),
                db.table("nestedinner"), db.table("nestedleaf"), dataset, dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            for (var values : List.of(Map.entry("main", List.of("1")), Map.entry("values", List.of("2", "3", "2")),
                    Map.entry("extras", List.of("4", "4")))) {
                for (int sequence = 0; sequence < values.getValue().size(); sequence++) {
                    assertTrue(result.next());
                    assertEquals(dataset + "-nested-1", result.getString("root_tid"));
                    assertEquals(dataset + "-basket-2", result.getString("bid"));
                    assertEquals(result.getString("root_basket"), result.getString("outer_basket"));
                    assertEquals(result.getString("outer_basket"), result.getString("inner_basket"));
                    assertEquals(result.getString("inner_basket"), result.getString("leaf_basket"));
                    assertNull(result.getString("outer_tid"));
                    assertNull(result.getString("inner_tid"));
                    assertNull(result.getString("leaf_tid"));
                    assertEquals("0", result.getString("outer_seq"));
                    assertEquals("0", result.getString("inner_seq"));
                    assertEquals("populated", result.getString("headline"));
                    for (String field : List.of("main", "values", "extras")) {
                        assertEquals(field.equals(values.getKey()) ? result.getString("inner_id") : null,
                                result.getString("nestedinner_" + field));
                    }
                    assertEquals(Integer.toString(sequence), result.getString("leaf_seq"));
                    assertEquals(values.getValue().get(sequence), result.getString("amount"));
                }
            }
            assertTrue(result.next());
            assertEquals(dataset + "-nested-1", result.getString("root_tid"));
            assertEquals("empty", result.getString("headline"));
            assertEquals("1", result.getString("inner_seq"));
            assertNull(result.getString("leaf_basket"));
            assertTrue(result.next());
            assertEquals(dataset + "-nested-2", result.getString("root_tid"));
            assertEquals(dataset + "-basket-2", result.getString("bid"));
            assertNull(result.getString("outer_basket"));
            assertFalse(result.next());
        }
    }

    private static void assertStoredStructureHierarchy(Target db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select n.t_id, n.t_type, n.t_seq, n.label, n.t_ili_tid, n.typedrecord_main, c.t_id as count_id,
                       f.t_id as flag_id, c.amount, f.enabled, b.t_ili_tid as bid
                from %s r join %s b on b.t_id=r.t_basket
                join %s n on coalesce(n.typedrecord_main, n.typedrecord_values)=r.t_id and n.t_basket=r.t_basket
                left join %s c on c.t_id=n.t_id and c.t_basket=n.t_basket
                left join %s f on f.t_id=n.t_id and f.t_basket=n.t_basket
                where r.t_ili_tid='%s-typed-1'
                order by case when n.typedrecord_main is not null then 0 else 1 end, n.t_seq
                """.formatted(db.table("typedrecord"), db.table("t_ili2db_basket"), db.table("notebase"),
                db.table("countnote"), db.table("flagnote"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            var types = List.of("countnote", "notebase", "countnote", "flagnote");
            var labels = List.of("main", "root", "count", "flag");
            for (int index = 0; index < types.size(); index++) {
                assertTrue(result.next());
                String type = types.get(index);
                String id = result.getString("t_id");
                assertEquals(dataset + "-basket-2", result.getString("bid"));
                assertEquals(type, result.getString("t_type"));
                assertEquals(labels.get(index), result.getString("label"));
                assertNull(result.getString("t_ili_tid"));
                assertEquals(Math.max(0, index - 1), result.getInt("t_seq"));
                assertEquals(index == 0, result.getObject("typedrecord_main") != null);
                assertEquals(type.equals("countnote") ? id : null, result.getString("count_id"));
                assertEquals(type.equals("flagnote") ? id : null, result.getString("flag_id"));
                assertEquals(index == 0 ? "5" : index == 2 ? "7" : null, result.getString("amount"));
                if (index == 3) {
                    assertTrue(result.getBoolean("enabled"));
                    assertFalse(result.wasNull());
                } else {
                    assertNull(result.getObject("enabled"));
                }
            }
            assertFalse(result.next());
        }

        String restrictedRows = """
                select n.t_type,n.t_seq,n.label
                from %s n join %s r on n.typedrecord_selected=r.t_id and n.t_basket=r.t_basket
                where r.t_ili_tid='%s-typed-1' order by n.t_seq
                """.formatted(db.table("notebase"), db.table("typedrecord"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(restrictedRows)) {
            assertTrue(result.next());
            assertEquals("flagnote", result.getString(1));
            assertEquals(0, result.getInt(2));
            assertEquals("first", result.getString(3));
            assertTrue(result.next());
            assertEquals("countnote", result.getString(1));
            assertEquals(1, result.getInt(2));
            assertEquals("second", result.getString(3));
            assertFalse(result.next());
        }

        String abstractRows = """
                select k.t_type, k.t_seq, k.label, k.t_ili_tid, n.acode
                from %s k join %s n on n.t_id=k.t_id and n.t_basket=k.t_basket
                join %s r on r.t_id=k.typedrecord_extras and r.t_basket=k.t_basket
                where r.t_ili_tid='%s-typed-1' order by k.t_seq
                """.formatted(db.table("notekind"), db.table("namednote"), db.table("typedrecord"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(abstractRows)) {
            for (int sequence = 0; sequence < 2; sequence++) {
                assertTrue(result.next());
                assertEquals("namednote", result.getString("t_type"));
                assertEquals(sequence, result.getInt("t_seq"));
                assertEquals("duplicate", result.getString("label"));
                assertNull(result.getString("t_ili_tid"));
                assertEquals("ABC", result.getString("acode"));
            }
            assertFalse(result.next());
        }
    }

    private static void assertStoredPolymorphicTargets(Target db, Connection sql, String dataset, int version)
            throws SQLException {
        String root = scalar(sql, "select t_id from " + db.table("servicebase") + " where t_ili_tid='" + dataset + "-serviceunit'");
        String branch = scalar(sql, "select t.t_id from " + db.table("tower") + " t join " + db.table("mast")
                + " m on m.t_id=t.t_id and m.t_basket=t.t_basket where m.t_ili_tid='" + dataset + "-relay'");
        assertNotNull(root);
        assertNotNull(branch);
        String carrier = " where t_ili_tid='" + dataset + "-refcarrier'";
        String rows = """
                select p.label, p.roottarget, p.branchtarget, p.t_seq from %s p
                join %s c on c.t_id=p.refcarrier_pairs and c.t_basket=p.t_basket
                where c.t_ili_tid='%s-refcarrier' order by p.t_seq
                """.formatted(db.table("targetpair"), db.table("refcarrier"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals("forward", result.getString(1));
            assertEquals(root, result.getString(2));
            assertEquals(branch, result.getString(3));
            assertEquals("0", result.getString(4));
            assertTrue(result.next());
            assertEquals("missing", result.getString(1));
            assertNull(result.getObject(2));
            assertNull(result.getObject(3));
            assertEquals("1", result.getString(4));
            assertFalse(result.next());
        }
        if (version == 24) {
            assertEquals(root, scalar(sql, "select directroot from " + db.table("refcarrier") + carrier));
            String tower = scalar(sql, "select t.t_id from " + db.table("tower") + " t join " + db.table("mast")
                    + " m on m.t_id=t.t_id and m.t_basket=t.t_basket where m.t_ili_tid='" + dataset + "-tower'");
            assertNotNull(tower);
            assertEquals(tower, scalar(sql, "select directbranch from " + db.table("refcarrier") + carrier));
        }
    }

    private static void assertStoredStructureReferences(Target db, Connection sql, String dataset)
            throws SQLException {
        String rows = """
                select l.label, l.t_basket, l.t_seq, l.destination, p.t_id as target_id,
                       p.t_ili_tid as target_tid, p.t_basket as target_basket
                from %s l join %s r on r.t_id=l.report_links
                left join %s p on p.t_id=l.destination
                where r.t_ili_tid='%s' order by l.t_seq
                """.formatted(db.table("linkdetail"), db.table("report"), db.table("place"), dataset + "-report-2");
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals("first", result.getString("label"));
            assertEquals(0, result.getInt("t_seq"));
            assertNotNull(result.getString("destination"));
            assertEquals(result.getString("target_id"), result.getString("destination"));
            assertEquals(dataset + "-place-3", result.getString("target_tid"));
            assertEquals(result.getString("t_basket"), result.getString("target_basket"));
            assertTrue(result.next());
            assertEquals("second", result.getString("label"));
            assertEquals(1, result.getInt("t_seq"));
            assertNull(result.getString("destination"));
            assertFalse(result.next());
        }
    }
}
