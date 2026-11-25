package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static guru.interlis.db.ArrayFixtures.fields;
import static guru.interlis.db.ArrayFixtures.objects;
import static guru.interlis.db.ArrayFixtures.storedItems;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ArrayReferenceFixtures {
    static final List<String> ORDER = List.of("b", "a", "b");

    private final Path work;
    private final Path source;
    private final int version;
    final String model;

    ArrayReferenceFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        model = "ArrayRefs" + version;
        source = fixture(work, model + ".ili");
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, model, source, "--coalesce-array");
    }

    Path delivery(String file, String prefix, List<String> order, boolean includeA) throws Exception {
        String refs = order.stream().map(value -> {
            String tid = prefix + "-target-" + value;
            return version == 23 ? "<%1$s.Data.Link><Entry REF=\"%2$s\"/></%1$s.Data.Link>".formatted(model, tid)
                    : "<Links i:ref=\"" + tid + "\"/>";
        }).collect(Collectors.joining());
        String links = version == 23 ? "<Links>" + refs + "</Links>" : refs;
        String rows = object(model, "Asset", prefix + "-value", links)
                + object(model, "Asset", prefix + "-missing", "")
                + (includeA ? object(model, "Target", prefix + "-target-a", "<Label>A</Label>") : "")
                + object(model, "Target", prefix + "-target-b", "<Label>B</Label>");
        return transfer(model, file, prefix + "-b", rows);
    }

    Path otherTargetDelivery(String model, String file, boolean includeReferenced) throws Exception {
        String rows = object(model, "OtherTarget", "kept", "<Label>Keep</Label>")
                + (includeReferenced ? object(model, "OtherTarget", "referenced", "<Label>Drop</Label>") : "");
        return transfer(model, file, "target-b", rows);
    }

    private Path transfer(String model, String file, String bid, String rows) throws Exception {
        return Files.writeString(work.resolve(file + ".xtf"), referenceDatasetFixture(model, version, bid, rows));
    }

    private String object(String model, String type, String tid, String content) {
        String tag = version == 23 ? model + ".Data." + type : type;
        return "<%s %s=\"%s\">%s</%s>".formatted(tag, version == 23 ? "TID" : "i:tid", tid, content, tag);
    }

    void assertDelivery(Path output, String prefix, List<String> order, boolean includesA) throws Exception {
        var rows = objects(output, version, prefix);
        assertEquals(includesA ? 4 : 3, rows.size());
        assertNull(child(rows.get(prefix + "-missing"), "Links"));
        var value = rows.get(prefix + "-value");
        assertName(value, version, model + ".Data.Asset");
        Stream<Element> refs;
        if (version == 23) {
            var links = fields(value, "Links");
            assertEquals(1, links.size());
            refs = children(links.getFirst()).stream().map(entry -> {
                assertName(entry, version, model + ".Data.Link");
                return child(entry, "Entry");
            });
        } else {
            refs = fields(value, "Links").stream()
                    .flatMap(field -> id(field, version, "ref").isEmpty() ? children(field).stream() : Stream.of(field));
        }
        assertEquals(order.stream().map(target -> prefix + "-target-" + target).toList(),
                refs.map(field -> id(field, version, "ref")).toList());
        if (includesA) assertEquals("A", child(rows.get(prefix + "-target-a"), "Label").getTextContent());
        assertEquals("B", child(rows.get(prefix + "-target-b"), "Label").getTextContent());
    }

    static List<String> storedReferences(Target db, Connection sql, String tid, int size) throws Exception {
        String row = " FROM " + db.table("asset") + " WHERE t_ili_tid='" + tid + "'";
        if (db.postgis()) {
            assertEquals("bigint[]", scalar(sql, "SELECT pg_typeof(links)::text" + row));
        } else {
            for (int i = 0; i < size; i++) {
                assertEquals("text", scalar(sql, "SELECT json_type(links, '$[" + i + "]')" + row));
            }
        }
        return storedItems(db, sql, "asset", "links", tid, size);
    }

    static String targetId(Target db, Connection sql, String tid) throws Exception {
        return scalar(sql, "SELECT t_id FROM " + db.table("target") + " WHERE t_ili_tid='" + tid + "'");
    }

    static void assertStoredDelivery(Target db, Connection sql, String prefix, List<String> order) throws Exception {
        var ids = new ArrayList<String>();
        for (String target : order) ids.add(targetId(db, sql, prefix + "-target-" + target));
        assertEquals(ids, storedReferences(db, sql, prefix + "-value", order.size()));
        assertNull(scalar(sql, "SELECT links FROM " + db.table("asset") + " WHERE t_ili_tid='" + prefix + "-missing'"));
    }
}
