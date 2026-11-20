package guru.interlis.db;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SqlNamesTest {
    @Test
    void allocatesQualifiedTableFallbacksAndOwnerLocalColumnSuffixes() {
        var names = new SqlNames();

        assertEquals("foo", names.table("NameTest", "Z", "Foo"));
        assertEquals("nametestz_foo", names.table("NameTest", "Z", "FOO"));
        assertEquals("nametestz_foo1", names.table("NameTest", "Z", "foo"));
        assertEquals("nametesta_foo", names.table("NameTest", "A", "Foo"));
        assertEquals("aselect", names.table("NameTest", "Z", "Select"));

        assertEquals("aobjectid", names.column("foo", "ObjectId"));
        assertEquals("aobjectid1", names.column("foo", "aObjectId"));
        assertEquals("aobjectid", names.column("nametestz_foo", "ObjectId"));
    }

    @Test
    void shortensBeforeApplyingBackendIndependentColumnSuffixes() {
        var names = new SqlNames();
        String table = names.table("NameTest", "Z", "Foo");
        String expected = "abc" + "b".repeat(54) + "ewx";

        assertEquals(expected, names.column(table, "Abc" + "B".repeat(55) + "aeiouEwX"));
        assertEquals(expected + "1", names.column(table, expected));
    }

    @Test
    void rejectsAnAlreadyOccupiedQualifiedNameWithoutRoomForASuffix() {
        var names = new SqlNames();
        String model = "M".repeat(20);
        String topic = "T".repeat(20);

        assertEquals("z".repeat(60), names.table(model, topic, "Z".repeat(64)));
        assertEquals("m".repeat(14) + "t".repeat(17) + "_" + "z".repeat(28),
                names.table(model, topic, "Z".repeat(65)));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThrows(DbException.class, () -> names.table(model, topic, "Z".repeat(66))));
    }
}
