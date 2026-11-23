package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertLocalReference;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.orderedOwners;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.text;
import static guru.interlis.db.TransferCatalogAssertions.assertReferenceMapping;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrderedRoleFixtures {
    private OrderedRoleFixtures() { }

    static Path copyOrderedFixture(Path work, String name) throws IOException {
        Path path = work.resolve(name);
        try (var input = OrderedRoleFixtures.class.getResourceAsStream("/" + name)) {
            Files.copy(input, path);
        }
        return path;
    }

    static void assertOrderColumn(Target db, Connection sql, String table, String fk) throws SQLException {
        String sequence = fk + "_t_seq";
        if (db.postgis) {
            assertEquals("bigint|YES", scalar(sql, "select data_type||'|'||is_nullable from information_schema.columns"
                    + " where table_schema='" + db.schema + "' and table_name='" + table + "' and column_name='" + sequence + "'"));
        } else {
            assertEquals("INTEGER|0", scalar(sql, "select type||'|'||\"notnull\" from pragma_table_info('" + table
                    + "') where name='" + fk + "_T_Seq'"));
        }
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                + " where lower(sqlname)='" + sequence + "' and colowner='" + table + "'"));
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_column_prop")
                + " where lower(columnname)='" + sequence + "' and tablename='" + table + "'"));
    }

    static void assertEmbeddedOrderOwner(Target db, Connection sql, String model) throws SQLException {
        assertReferenceMapping(db, sql, model, "Filing.WrittenIn", "document", "writtenin", "folder");
        assertEquals("embedded", scalar(sql, "select setting from " + db.table("t_ili2db_trafo")
                + " where iliname='" + model + ".Data.Filing' and tag='ch.ehi.ili2db.inheritance'"));
    }

    static void assertStoredOrderedPositions(Target db, Connection sql, String dataset, boolean present)
            throws SQLException {
        String rows = """
                select m.t_ili_tid, t.t_ili_tid, l.amember_t_seq, l.team_t_seq, b.t_ili_tid
                from %s l join %s m on m.t_id=l.amember join %s t on t.t_id=l.team
                join %s b on b.t_id=l.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' and m.t_basket=b.t_id and t.t_basket=b.t_id
                order by m.t_ili_tid,t.t_ili_tid
                """.formatted(db.table("membership"), db.table("amember"), db.table("team"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            for (var expected : orderedMemberships(dataset, present)) {
                assertTrue(result.next());
                assertEquals(expected.member(), result.getString(1));
                assertEquals(expected.team(), result.getString(2));
                assertEquals(expected.memberPosition(), emptyIfNull(result.getString(3)));
                assertEquals(expected.teamPosition(), emptyIfNull(result.getString(4)));
                assertEquals(dataset + "-basket", result.getString(5));
            }
            assertFalse(result.next());
        }
        String entry = """
                select r.t_ili_tid||'|'||t.t_ili_tid||'|'||coalesce(cast(l.runner_t_seq as text),'')
                from %s l join %s r on r.t_id=l.runner join %s t on t.t_id=l.track
                join %s b on b.t_id=l.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' and r.t_basket=b.t_id and t.t_basket=b.t_id
                """.formatted(db.table("entry"), db.table("runner"), db.table("track"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        assertEquals(dataset + "-runner|" + dataset + "-track|" + (present ? "4" : ""), scalar(sql, entry));

        String document = """
                select coalesce(f.t_ili_tid,'')||'|'||coalesce(cast(doc.writtenin_t_seq as text),'')
                from %s doc left join %s f on f.t_id=doc.writtenin and f.t_basket=doc.t_basket
                where doc.t_ili_tid='%s-doc-%s'
                """;
        assertEquals("|", scalar(sql, document.formatted(db.table("document"), db.table("folder"), dataset, "loose")));
        assertEquals(dataset + "-folder|", scalar(sql, document.formatted(db.table("document"), db.table("folder"), dataset, "one")));
        assertEquals(dataset + "-folder|" + (present ? "21" : ""),
                scalar(sql, document.formatted(db.table("document"), db.table("folder"), dataset, "two")));

        String placements = """
                select o.t_ili_tid, coalesce(f.t_ili_tid,''), coalesce(l.memo,''), coalesce(cast(l.operatives_t_seq as text),'')
                from %s o left join %s l on l.operatives=o.t_id and l.t_basket=o.t_basket
                left join %s f on f.t_id=l.site and f.t_basket=o.t_basket
                where o.t_ili_tid like '%s-operative-%%'
                order by o.t_ili_tid
                """.formatted(db.table("operative"), db.table("placement"), db.table("dutyarea"), dataset);
        var actual = new ArrayList<List<String>>();
        try (var statement = sql.createStatement(); var result = statement.executeQuery(placements)) {
            while (result.next()) actual.add(List.of(result.getString(1), result.getString(2), result.getString(3), result.getString(4)));
        }
        assertEquals(List.of(List.of(dataset + "-operative-absent", "", "", ""),
                List.of(dataset + "-operative-direct", dataset + "-area", "", present ? "18" : ""),
                List.of(dataset + "-operative-nested", dataset + "-area", "kit", present ? "34" : "")), actual);
    }

    static void assertOrderedTransfer(Path output, String model, int version, String dataset, boolean present)
            throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(dataset + "-basket", id(basket, version, "bid"));
        String prefix = version == 23 ? model + ".Data." : "";
        var memberships = new HashSet<OrderedMembership>();
        for (var link : children(basket)) {
            if (!link.getLocalName().equals(prefix + "Membership")) continue;
            assertEquals("", id(link, version, "tid"));
            var member = child(link, "Member");
            var team = child(link, "Team");
            assertTrue(memberships.add(new OrderedMembership(id(member, version, "ref"), id(team, version, "ref"),
                    id(member, version, "order_pos"), id(team, version, "order_pos"))));
        }
        assertEquals(Set.copyOf(orderedMemberships(dataset, present)), memberships);
        var entry = child(basket, prefix + "Entry");
        assertPosition(entry, "Runner", version, dataset + "-runner", present ? "4" : "");
        assertPosition(entry, "Track", version, dataset + "-track", "");

        var documents = orderedOwners(basket, prefix + "Document", version);
        assertEquals(Set.of(dataset + "-doc-loose", dataset + "-doc-one", dataset + "-doc-two"), documents.keySet());
        assertNull(child(documents.get(dataset + "-doc-loose"), "WrittenIn"));
        assertPosition(documents.get(dataset + "-doc-one"), "WrittenIn", version, dataset + "-folder", "");
        assertPosition(documents.get(dataset + "-doc-two"), "WrittenIn", version, dataset + "-folder", present ? "21" : "");

        var operatives = orderedOwners(basket, prefix + "Operative", version);
        assertEquals(Set.of(dataset + "-operative-absent", dataset + "-operative-direct", dataset + "-operative-nested"),
                operatives.keySet());
        assertNull(child(operatives.get(dataset + "-operative-absent"), "Site"));
        var direct = operatives.get(dataset + "-operative-direct");
        assertPosition(direct, "Site", version, dataset + "-area", present ? "18" : "");
        assertTrue(children(child(direct, "Site")).isEmpty());
        var nested = operatives.get(dataset + "-operative-nested");
        assertPosition(nested, "Site", version, dataset + "-area", present ? "34" : "");
        var payload = child(child(nested, "Site"), prefix + "Placement");
        assertNotNull(payload);
        assertEquals("kit", text(payload, "Memo"));
    }

    static void assertPosition(Element owner, String role, int version, String target, String position) {
        assertLocalReference(owner, role, version, target);
        assertEquals(position, id(child(owner, role), version, "order_pos"), role);
    }

    static List<OrderedMembership> orderedMemberships(String dataset, boolean present) {
        return List.of(new OrderedMembership(dataset + "-alpha", dataset + "-blue", present ? "2" : "", ""),
                new OrderedMembership(dataset + "-beta", dataset + "-blue", present ? "9" : "", present ? "3" : ""),
                new OrderedMembership(dataset + "-beta", dataset + "-red", "", present ? Long.toString(Long.MAX_VALUE) : ""));
    }

    static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
    record OrderedMembership(String member, String team, String memberPosition, String teamPosition) { }
}
