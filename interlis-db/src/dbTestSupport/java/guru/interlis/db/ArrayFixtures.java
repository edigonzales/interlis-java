package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArrayFixtures {
    private ArrayFixtures() { }

    static Path withPrefix(Path work, Path source, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(source).replace("fixture-", prefix + "-"));
    }

    static Map<String, Element> objects(Path output, int version, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-b", id(baskets.getFirst(), version, "bid"));
        return children(baskets.getFirst()).stream().collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
    }

    static List<Element> fields(Element object, String name) {
        return children(object).stream().filter(field -> field.getLocalName().equals(name)).toList();
    }

    static List<String> wrappedItems(Element object, String name) {
        return fields(object, name).stream().flatMap(field -> children(field).stream())
                .map(entry -> child(entry, "Entry")).map(value -> value == null ? null : value.getTextContent()).toList();
    }

    static List<String> storedItems(Target db, Connection sql, String table, String column, String tid, int size) throws Exception {
        String row = " FROM " + db.table(table) + " WHERE t_ili_tid='" + tid + "'";
        String length = (db.postgis() ? "cardinality" : "json_array_length") + "(" + column + ")";
        assertEquals(Integer.toString(size), scalar(sql, "SELECT " + length + row), column);
        var values = new ArrayList<String>();
        for (int i = 0; i < size; i++) {
            String item = db.postgis() ? column + "[" + (i + 1) + "]::text" : "json_extract(" + column + ", '$[" + i + "]')";
            values.add(scalar(sql, "SELECT " + item + row));
        }
        return values;
    }
}
