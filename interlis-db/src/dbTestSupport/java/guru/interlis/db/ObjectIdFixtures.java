package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertExternalReference;
import static guru.interlis.db.DbTestSupport.assertLocalReference;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.orderedOwners;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ObjectIdFixtures {
    static final String REFERENCE_ID = "C09B1740-5C5A-48EA-B095-938D1C946974";
    private final Path work;

    ObjectIdFixtures(Path work) {
        this.work = work;
    }

    Path oidReferenceFixture(String model, int version, String targetId, String dataset, String owner) throws IOException {
        String objects = version == 23 ? """
                <%1$s.Data.Inspection TID="remote-inspection"><Links><%1$s.Data.InspectionRefs>
                  <RemoteSite REF="%2$s" BID="%3$s"/>
                </%1$s.Data.InspectionRefs></Links></%1$s.Data.Inspection>
                """.formatted(model, targetId, oidBasket("oid-legacy")) : """
                <Inspection i:tid="remote-inspection"><Links><InspectionRefs><RemoteSite i:ref="%s"/></InspectionRefs></Links></Inspection>
                """.formatted(targetId);
        return Files.writeString(work.resolve(dataset + ".xtf"),
                referenceDatasetFixture(model, version, oidBasket(dataset), objects.replace("remote-inspection", owner)));
    }

    static void assertTextOidTransfer(Path output, int version) throws Exception {
        var data = readDataSection(output, version);
        var basket = child(data, "Data");
        assertEquals("Basket.Mixed-Case", id(basket, version, "bid"));
        var entries = orderedOwners(basket, "Entry", version);
        assertEquals(Set.of("Entry.Mixed-Case", "Entry.mixed-case"), entries.keySet());
        var entry = entries.get("Entry.Mixed-Case");
        assertEquals("Code.Mixed-Case", text(entry, "CodeValue"));
        assertLocalReference(entry, "PreviousEntry", version, "Entry.mixed-case");
        assertEquals("Plain-1", id(child(basket, "Plain"), version, "tid"));
        var link = child(basket, "EntryPair");
        assertEquals("Pair.Mixed-Case", id(link, version, "tid"));
        assertLocalReference(link, "SourceEntry", version, "Entry.Mixed-Case");
        assertLocalReference(link, "TargetEntry", version, "Entry.mixed-case");
        var registry = child(data, "Registry");
        assertEquals("Registry-Basket", id(registry, version, "bid"));
        assertEquals("Register.Mixed-Case", id(child(registry, "Register"), version, "tid"));
    }

    static void assertOidReferenceRow(Target db, Connection sql, String owner, String target,
                                              String targetBid, String column) throws SQLException {
        String query = """
                select s.t_ili_tid from %s s join %s r on r.%s=s.t_id
                join %s i on r.inspection_links=i.t_id and r.t_basket=i.t_basket
                join %s b on b.t_id=s.t_basket where i.t_ili_tid='%s' and b.t_ili_tid='%s' and s.t_type='marker'
                """.formatted(db.table("site"), db.table("inspectionrefs"), column, db.table("inspection"),
                db.table("t_ili2db_basket"), owner, oidBasket(targetBid));
        assertEquals(target.toLowerCase(Locale.ROOT), scalar(sql, query));
    }

    static void assertOidReferenceXtf(Path output, int version, String tid, boolean own) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(1, children(data).size());
        var basket = children(data).getFirst();
        assertEquals(1, children(basket).size());
        var inspection = children(basket).getFirst();
        var refs = children(child(inspection, "Links")).getFirst();
        assertExternalReference(child(refs, "RemoteSite"), version, tid.toLowerCase(Locale.ROOT),
                own ? oidBasket("oid-legacy") : "");
    }

    Path oidFixture(String model, int version, String bid, String tid) throws IOException {
        String object = version == 23 ? """
                <%1$s.Data.Marker TID="%2$s"><Label>marker</Label><Category>survey</Category></%1$s.Data.Marker>
                """.formatted(model, tid) : """
                <Marker i:tid="%s"><Label>marker</Label><Category>survey</Category></Marker>
                """.formatted(tid);
        String defaults = version == 23 ? """
                <%1$s.Defaults BID="%2$s-defaults"><%1$s.Defaults.Address TID="%3$s"><Text>address</Text></%1$s.Defaults.Address></%1$s.Defaults>
                """.formatted(model, bid, defaultOid(tid)) : """
                <Defaults i:bid="%s-defaults"><Address i:tid="%s"><Text>address</Text></Address></Defaults>
                """.formatted(bid, defaultOid(tid));
        String inspection = version == 23 ? """
                <%1$s.Data.Inspection TID="%2$s-inspection"><Links><%1$s.Data.InspectionRefs><LocalSite REF="%3$s"/><CatalogId REF="%4$s"/></%1$s.Data.InspectionRefs></Links></%1$s.Data.Inspection>
                """.formatted(model, bid, tid, standardOid(tid)) : """
                <Inspection i:tid="%s-inspection"><Links><InspectionRefs><LocalSite i:ref="%s"/><CatalogId i:ref="%s"/></InspectionRefs></Links></Inspection>
                """.formatted(bid, tid, standardOid(tid));
        String standard = version == 23 ? """
                <%1$s.Data.StandardSite TID="%2$s"><Name>catalog</Name></%1$s.Data.StandardSite>
                """.formatted(model, standardOid(tid)) : """
                <StandardSite i:tid="%s"><Name>catalog</Name></StandardSite>
                """.formatted(standardOid(tid));
        String evidence = version == 23 ? """
                <%1$s.Data.Evidence TID="%2$s"><EvidenceMarker REF="%3$s"/><EvidenceInspection REF="%4$s-inspection"/><Memo>evidence</Memo></%1$s.Data.Evidence>
                """.formatted(model, evidenceOid(tid), tid, bid) : """
                <Evidence i:tid="%s"><EvidenceMarker i:ref="%s"/><EvidenceInspection i:ref="%s-inspection"/><Memo>evidence</Memo></Evidence>
                """.formatted(evidenceOid(tid), tid, bid);
        String second = version == 23 ? """
                <%1$s.Data BID="%2$s"><%1$s.Data.Site TID="%3$s"><Label>second</Label></%1$s.Data.Site></%1$s.Data>
                """.formatted(model, oidBasket(bid + "-second"), secondOid(tid)) : """
                <Data i:bid="%s"><Site i:tid="%s"><Label>second</Label></Site></Data>
                """.formatted(oidBasket(bid + "-second"), secondOid(tid));
        String fixture = referenceDatasetFixture(model, version, oidBasket(bid), inspection + object + evidence + standard)
                .replace(version == 23 ? "</DATASECTION>" : "</i:datasection>",
                        second + defaults + (version == 23 ? "</DATASECTION>" : "</i:datasection>"));
        return Files.writeString(work.resolve(bid + ".xtf"), fixture);
    }

    static String standardOid(String tid) {
        return "ch203400000000%02d".formatted(Math.max(0, Character.digit(tid.charAt(0), 16)));
    }

    static String evidenceOid(String tid) {
        return tid.replace("-5C5A-", "-CCCC-");
    }

    static String oidBasket(String name) {
        return java.util.UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String secondOid(String tid) {
        return tid.replace("-5C5A-", "-BBBB-");
    }

    static String defaultOid(String tid) {
        return tid.replace("-5C5A-", "-ABCD-");
    }

    static void assertOidRows(Target db, Connection sql, String bid, String tid) throws SQLException {
        assertEquals(tid.toLowerCase(Locale.ROOT), scalar(sql, "select s.t_ili_tid from " + db.table("site")
                + " s join " + db.table("marker") + " m on m.t_id=s.t_id and m.t_basket=s.t_basket"
                + " join " + db.table("t_ili2db_basket") + " b on b.t_id=s.t_basket where b.t_ili_tid='" + oidBasket(bid)
                + "' and s.label='marker' and s.t_type='marker' and m.category='survey'"));
        assertEquals(secondOid(tid).toLowerCase(Locale.ROOT), scalar(sql, "select s.t_ili_tid from " + db.table("site")
                + " s join " + db.table("t_ili2db_basket") + " b on b.t_id=s.t_basket where b.t_ili_tid='"
                + oidBasket(bid + "-second") + "' and s.label='second'"));
        assertOidReferenceRow(db, sql, bid + "-inspection", tid, bid, "localsite");
        String catalog = """
                select s.t_ili_tid from %s s join %s r on r.catalogid=s.t_id and r.t_basket=s.t_basket
                join %s i on r.inspection_links=i.t_id and r.t_basket=i.t_basket
                where i.t_ili_tid='%s-inspection' and s.aname='catalog'
                """.formatted(db.table("standardsite"), db.table("inspectionrefs"), db.table("inspection"), bid);
        assertEquals(standardOid(tid), scalar(sql, catalog));
        String evidence = """
                select e.t_ili_tid from %s e join %s b on b.t_id=e.t_basket
                join %s s on s.t_id=e.evidencemarker and s.t_basket=e.t_basket
                join %s i on i.t_id=e.evidenceinspection and i.t_basket=e.t_basket
                where b.t_ili_tid='%s' and e.memo='evidence' and s.t_ili_tid='%s' and i.t_ili_tid='%s-inspection'
                """.formatted(db.table("evidence"), db.table("t_ili2db_basket"), db.table("site"),
                db.table("inspection"), oidBasket(bid), tid.toLowerCase(Locale.ROOT), bid);
        assertEquals(evidenceOid(tid).toLowerCase(Locale.ROOT), scalar(sql, evidence));
        assertEquals(defaultOid(tid).toLowerCase(Locale.ROOT), scalar(sql, "select a.t_ili_tid from " + db.table("address")
                + " a join " + db.table("t_ili2db_basket") + " b on b.t_id=a.t_basket where b.t_ili_tid='"
                + bid + "-defaults' and a.atext='address'"));
    }

    static void assertOidXtf(Path output, String model, int version, String bid, String tid) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(3, children(data).size());
        var basket = children(data).stream().filter(value -> id(value, version, "bid").equals(oidBasket(bid)))
                .findFirst().orElseThrow();
        var second = children(data).stream().filter(value -> id(value, version, "bid").equals(oidBasket(bid + "-second")))
                .findFirst().orElseThrow();
        assertEquals(secondOid(tid).toLowerCase(Locale.ROOT), id(children(second).getFirst(), version, "tid"));
        var object = child(basket, version == 23 ? model + ".Data.Marker" : "Marker");
        assertNotNull(object);
        assertEquals(tid.toLowerCase(Locale.ROOT), id(object, version, "tid"));
        assertEquals("marker", child(object, "Label").getTextContent());
        assertEquals("survey", child(object, "Category").getTextContent());
        var inspection = child(basket, version == 23 ? model + ".Data.Inspection" : "Inspection");
        var refs = children(child(inspection, "Links")).getFirst();
        assertExternalReference(child(refs, "LocalSite"), version, tid.toLowerCase(Locale.ROOT), "");
        assertExternalReference(child(refs, "CatalogId"), version, standardOid(tid), "");
        var standard = child(basket, version == 23 ? model + ".Data.StandardSite" : "StandardSite");
        assertEquals(standardOid(tid), id(standard, version, "tid"));
        assertEquals("catalog", child(standard, "Name").getTextContent());
        var evidence = child(basket, version == 23 ? model + ".Data.Evidence" : "Evidence");
        assertNotNull(evidence);
        assertEquals(evidenceOid(tid).toLowerCase(Locale.ROOT), id(evidence, version, "tid"));
        assertEquals("evidence", child(evidence, "Memo").getTextContent());
        assertExternalReference(child(evidence, "EvidenceMarker"), version, tid.toLowerCase(Locale.ROOT), "");
        assertExternalReference(child(evidence, "EvidenceInspection"), version, bid + "-inspection", "");
        var inherited = child(data, version == 23 ? model + ".Defaults" : "Defaults");
        assertEquals(bid + "-defaults", id(inherited, version, "bid"));
        assertEquals(defaultOid(tid).toLowerCase(Locale.ROOT),
                id(child(inherited, version == 23 ? model + ".Defaults.Address" : "Address"), version, "tid"));
    }
}
