package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class IdentityColumnFixtures {
    private static final String SOURCE_TID = "dbid0.o.6d61726b6572.1";
    private static final String UUID_OID = "c09b1740-5c5a-48ea-b095-938d1c946974";
    private final Path work;
    private final int version;
    final String model;
    final Path source;
    final Path input;

    IdentityColumnFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        model = "IdentityColumns" + version;
        String content;
        try (var resource = getClass().getResourceAsStream("/IdentityColumns24.ili")) {
            content = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (version == 23) {
            content = content.replace("INTERLIS 2.4;", "INTERLIS 2.3;")
                    .replace("IdentityColumns24", model).replace("MODEL ", "CONTRACTED MODEL ");
        }
        source = Files.writeString(work.resolve(model + ".ili"), content);
        input = Files.writeString(work.resolve("objects.xtf"), fixture());
    }

    void schema(Target db, String profile, boolean baskets, boolean tids) {
        var options = new ArrayList<String>();
        if (!profile.equals("newclass")) options.addAll(List.of("--inheritance", profile));
        if (baskets) options.add("--create-basket-col");
        if (tids) options.add("--create-tid-col");
        assertCliSuccess(db, "schemaimport", model, null, source, options.toArray(String[]::new));
    }

    Path roundTrip(Target db, String label, String profile, boolean baskets, boolean tids)
            throws Exception {
        String dataset = baskets ? "objects" : null;
        if (!baskets) insertDirectly(db);
        assertCliSuccess(db, "import", model, dataset, input);
        if (baskets) insertDirectly(db);
        var output = work.resolve(label + ".xtf");
        assertCliSuccess(db, "export", model, dataset, output);
        var groups = children(readDataSection(output, version));
        assertEquals(baskets ? 2 : 1, groups.size());
        var objects = groups.stream().flatMap(group -> children(group).stream()).toList();
        var target = objects.stream().filter(object -> "Imported".equals(text(object, "Label"))).findFirst().orElseThrow();
        var direct = objects.stream().filter(object -> "Direct SQL".equals(text(object, "Label"))).findFirst().orElseThrow();
        var holder = objects.stream().filter(object -> typeName(object).equals("Holder")).findFirst().orElseThrow();
        String tid = id(target, version, "tid");
        if (tids) assertEquals(SOURCE_TID, tid);
        else assertNotEquals(SOURCE_TID, tid);
        assertNotEquals("", id(direct, version, "tid"));
        assertNotEquals(tid, id(direct, version, "tid"));
        var detail = children(child(holder, "Note")).getFirst();
        assertEquals(tid, id(child(detail, "Target"), version, "ref"));
        assertEquals("Nested", text(detail, "Memo"));
        var entry = objects.stream().filter(object -> typeName(object).equals("Entry")).findFirst().orElseThrow();
        assertEquals("12", text(entry, "Amount"));
        assertEquals("Text-Identity", id(objects.stream().filter(object -> typeName(object).equals("StableText"))
                .findFirst().orElseThrow(), version, "tid"));
        assertEquals(UUID_OID, id(objects.stream().filter(object -> typeName(object).equals("StableUuid"))
                .findFirst().orElseThrow(), version, "tid"));
        try (var sql = db.connect()) {
            assertEquals(baskets ? "2" : "0", scalar(sql, "select count(*) from " + db.table("t_ili2db_basket")));
            if (!baskets && !tids && profile.equals("newclass")) {
                String targetColumn = scalar(sql, "select sqlname from " + db.table("t_ili2db_attrname")
                        + " where iliname='" + model + ".Data.Detail.Target'");
                execute(sql, "update " + db.table("detail") + " set " + Database.quote(targetColumn)
                        + " = (select t_id from " + db.table("marker") + " where label='Direct SQL')");
            }
        }
        if (!baskets && !tids && profile.equals("newclass")) {
            assertCliSuccess(db, "export", model, null, output);
            var reexported = children(children(readDataSection(output, version)).getFirst());
            var updated = reexported.stream().filter(object -> typeName(object).equals("Holder")).findFirst().orElseThrow();
            var reference = child(children(child(updated, "Note")).getFirst(), "Target");
            assertEquals(id(direct, version, "tid"), id(reference, version, "ref"));
        }
        return output;
    }

    private void insertDirectly(Target db) throws Exception {
        try (var sql = db.connect()) {
            String basket = scalar(sql, "select t_id from " + db.table("t_ili2db_basket") + " where t_ili_tid='source-basket'");
            // Query returns null when the schema does not track baskets.
            if (basket == null) {
                execute(sql, "insert into " + db.table("marker") + " (label) values ('Direct SQL')");
            } else {
                execute(sql, "insert into " + db.table("marker") + " (label, t_basket) values ('Direct SQL', " + basket + ")");
            }
        }
    }

    private String fixture() {
        if (version == 24) {
            return referenceDatasetFixture(model, version, "source-basket", """
                    <Holder i:tid="source-holder">
                      <Note><Detail><Memo>Nested</Memo><Target i:ref="%1$s"/></Detail></Note></Holder>
                    <Marker i:tid="%1$s"><Label>Imported</Label></Marker>
                    """.formatted(SOURCE_TID)).replace("</i:datasection>", """
                    <Data i:bid="next-basket">
                      <Entry i:tid="source-entry"><Description>Example</Description><Amount>12</Amount></Entry>
                      <StableText i:tid="Text-Identity"/>
                      <StableUuid i:tid="%s"/>
                    </Data></i:datasection>""".formatted(UUID_OID));
        }
        return referenceDatasetFixture(model, version, "source-basket", """
                <%1$s.Data.Holder TID="source-holder">
                  <Note><%1$s.Data.Detail><Memo>Nested</Memo><Target REF="%2$s"/></%1$s.Data.Detail></Note></%1$s.Data.Holder>
                <%1$s.Data.Marker TID="%2$s"><Label>Imported</Label></%1$s.Data.Marker>
                """.formatted(model, SOURCE_TID)).replace("</DATASECTION>", """
                <%1$s.Data BID="next-basket">
                  <%1$s.Data.Entry TID="source-entry"><Description>Example</Description><Amount>12</Amount></%1$s.Data.Entry>
                  <%1$s.Data.StableText TID="Text-Identity"/>
                  <%1$s.Data.StableUuid TID="%2$s"/>
                </%1$s.Data></DATASECTION>""".formatted(model, UUID_OID));
    }

    private static String text(Element object, String field) {
        var element = child(object, field);
        return element == null ? "" : element.getTextContent();
    }

    private static String typeName(Element object) {
        String name = object.getLocalName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
