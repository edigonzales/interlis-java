package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IdentityColumnCases {
    private static final String SOURCE_TID = "dbid0.o.6d61726b6572.1";
    private static final String UUID_OID = "c09b1740-5c5a-48ea-b095-938d1c946974";
    private final Path work;
    private final DbInteropTest.ReferenceTool reference;
    private final int version;
    private final String model;

    IdentityColumnCases(Path work, DbInteropTest.ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
        model = "IdentityColumns" + version;
    }

    void exercise(DbInteropTest.TestDatabase server) throws Exception {
        if (server.postgis()) {
            try (var sql = server.connect()) {
                execute(sql, "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            }
        }
        String source;
        try (var resource = getClass().getResourceAsStream("/IdentityColumns24.ili")) {
            source = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (version == 23) {
            source = source.replace("INTERLIS 2.4;", "INTERLIS 2.3;")
                    .replace("IdentityColumns24", model).replace("MODEL ", "CONTRACTED MODEL ");
        }
        var ili = Files.writeString(work.resolve(model + ".ili"), source);
        var input = Files.writeString(work.resolve("objects.xtf"), fixture());
        var profiles = version == 24 ? List.of("newclass", "smart1", "smart2") : List.of("newclass");
        for (String profile : profiles) {
            for (boolean baskets : List.of(false, true)) {
                for (boolean tids : List.of(false, true)) {
                    String name = profile + (baskets ? "_b" : "") + (tids ? "_t" : "");
                    var legacy = server.peer("ref_" + name);
                    var own = server.peer("own_" + name);
                    createSchemas(legacy, own, ili, profile, baskets, tids);
                    var fixture = profile.equals("newclass") ? legacy : own;
                    var output = exchange(fixture, input, name, baskets, tids);
                    if (!baskets && !tids && profile.equals("newclass")) {
                        appendModel(own);
                        verifyRollback(own);
                        assertCliSuccess(own, "import", model, null, output);
                        try (var sql = own.connect()) {
                            assertEquals("2", scalar(sql, "select count(*) from " + own.table("marker")));
                        }
                    }
                }
            }
        }
        if (version == 24) checkAbstractOid(server);
    }

    private void createSchemas(DbInteropTest.TestDatabase legacy, DbInteropTest.TestDatabase own,
                               Path ili, String profile, boolean baskets, boolean tids) throws Exception {
        var refOptions = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--sqlEnableNull",
                "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056"));
        var options = new ArrayList<String>();
        if (!profile.equals("newclass")) {
            refOptions.add("--" + profile + "Inheritance");
            options.addAll(List.of("--inheritance", profile));
        }
        if (baskets) {
            refOptions.add("--createBasketCol");
            options.add("--create-basket-col");
        }
        if (tids) {
            refOptions.add("--createTidCol");
            options.add("--create-tid-col");
        }
        refOptions.add(ili.toString());
        reference.run(legacy, work.toString(), refOptions.toArray(String[]::new));
        var result = cli(own, "schemaimport", model, null, ili, CancellationToken.none(), options.toArray(String[]::new));
        assertEquals(0, result.code(), result::diagnostics);
        try (var expected = legacy.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, legacy.postgis(), legacy.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
    }

    private Path exchange(DbInteropTest.TestDatabase db, Path input, String label, boolean baskets, boolean tids)
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
            if (!baskets && !tids && label.equals("newclass")) {
                String targetColumn = scalar(sql, "select sqlname from " + db.table("t_ili2db_attrname")
                        + " where iliname='" + model + ".Data.Detail.Target'");
                execute(sql, "update " + db.table("detail") + " set " + Database.quote(targetColumn)
                        + " = (select t_id from " + db.table("marker") + " where label='Direct SQL')");
            }
        }
        if (!baskets && !tids && label.equals("newclass")) {
            assertCliSuccess(db, "export", model, null, output);
            var reexported = children(children(readDataSection(output, version)).getFirst());
            var updated = reexported.stream().filter(object -> typeName(object).equals("Holder")).findFirst().orElseThrow();
            var reference = child(children(child(updated, "Note")).getFirst(), "Target");
            assertEquals(id(direct, version, "tid"), id(reference, version, "ref"));
        }
        return output;
    }

    private void insertDirectly(DbInteropTest.TestDatabase db) throws Exception {
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

    private void appendModel(DbInteropTest.TestDatabase db) throws Exception {
        String name = "Extra" + version;
        var ili = Files.writeString(work.resolve(name + ".ili"), """
                INTERLIS 2.%s;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data = CLASS Addition = END Addition; END Data;
                END %s.
                """.formatted(version - 20, name, name));
        var result = cli(db, "schemaimport", name, null, ili, CancellationToken.none(), "--add-models");
        assertEquals(0, result.code(), result::diagnostics);
        try (var sql = db.connect()) {
            execute(sql, "insert into " + db.table("addition") + " (t_id) values (100)");
        }
    }

    private void verifyRollback(DbInteropTest.TestDatabase db) throws Exception {
        var bad = Files.writeString(work.resolve("broken.xtf"), fixture().replace("next-basket", "source-basket"));
        var result = cli(db, "import", model, null, bad, CancellationToken.none());
        assertNotEquals(0, result.code(), result::diagnostics);
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "select count(*) from " + db.table("marker")));
        }
        assertCliFailure(db, "import", model, "named", bad, "require a schema created with --create-basket-col");
    }

    private void checkAbstractOid(DbInteropTest.TestDatabase server) throws Exception {
        var ili = Files.writeString(work.resolve("AbstractOid.ili"), """
                INTERLIS 2.4;
                MODEL AbstractOid (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    CLASS Root (ABSTRACT) = OID AS INTERLIS.ANYOID; END Root;
                    CLASS Leaf EXTENDS Root = OID AS INTERLIS.UUIDOID; END Leaf;
                  END Data;
                END AbstractOid.
                """);
        var db = server.peer("abstract_oid");
        assertCliSuccess(db, "schemaimport", "AbstractOid", null, ili);
        try (var sql = db.connect()) {
            assertEquals("INTERLIS.UUIDOID", scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                    + " where tablename='root' and tag='ch.ehi.ili2db.oidDomain'"));
        }
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
