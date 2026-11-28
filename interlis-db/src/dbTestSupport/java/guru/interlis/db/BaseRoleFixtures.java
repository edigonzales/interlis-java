package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertLocalReference;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class BaseRoleFixtures {
    private final Path work;
    private final int version;
    final String base;
    final String extension;
    final Path baseModel;
    final Path extensionModel;
    final Path delivery;

    BaseRoleFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        base = "RoleBase" + version;
        extension = "RoleExtension" + version;
        baseModel = ModelFixtures.fixture(work, "base-roles/" + base + ".ili");
        extensionModel = ModelFixtures.fixture(work, "base-roles/" + extension + ".ili");
        delivery = ModelFixtures.fixture(work, "base-roles/delivery" + version + ".xtf");
    }

    void createExtension(Target db) {
        assertSchemaWithIdentityColumns(db, extension, extensionModel, "--model-file", baseModel.toString());
    }

    Path deliveryWithDroppedMarker() throws Exception {
        String fixed = version == 23 ? base + ".Data.FixedMarker" : "b:FixedMarker";
        String field = version == 23 ? extension + ".Work.FieldMarker" : "e:FieldMarker";
        return Files.writeString(work.resolve("field-marker.xtf"), Files.readString(delivery).replace(fixed, field));
    }

    void assertProjection(Path output) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(base), headerModels(data, version));
        assertEquals(1, children(data).size());
        var projected = basket(data, version, base + ".Data", "work");

        var survey = object(projected, "Survey", "survey");
        assertEquals("field work", child(survey, "Memo").getTextContent());
        assertNull(child(survey, "Crew"));
        assertLocalReference(survey, "Mark", version, "mark");
        assertNull(child(object(projected, "Survey", "unassigned"), "Mark"));
        object(projected, "FixedMarker", "mark");
    }

    private Element object(Element basket, String type, String tid) {
        var object = children(basket).stream().filter(element -> tid.equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertName(object, version, base + ".Data." + type);
        return object;
    }

    void assertStoredRoles(Target db) throws Exception {
        try (var sql = db.connect()) {
            String surveys = db.table("survey");
            assertEquals("mark", scalar(sql, "SELECT m.t_ili_tid FROM " + surveys + " s JOIN " + db.table("marker")
                    + " m ON s.mark=m.t_id AND s.t_basket=m.t_basket WHERE s.t_ili_tid='survey'"));
            assertEquals("unassigned", scalar(sql, "SELECT t_ili_tid FROM " + surveys + " WHERE mark IS NULL"));
        }
    }
}
