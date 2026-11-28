package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertLocalReference;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.headerModels;
import static guru.interlis.db.OrderedRoleFixtures.assertPosition;
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

    Path deliveryWithDroppedArchive() throws Exception {
        String main = version == 23 ? base + ".Data.MainArchive" : "b:MainArchive";
        String field = version == 23 ? extension + ".Work.FieldArchive" : "e:FieldArchive";
        return Files.writeString(work.resolve("field-archive.xtf"), Files.readString(delivery).replace(main, field));
    }

    void assertProjection(Path output, boolean positions) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(base), headerModels(data, version));
        assertEquals(1, children(data).size());
        var projected = basket(data, version, base + ".Data", "work");

        var survey = object(projected, "Survey", "survey");
        assertEquals("field work", child(survey, "Memo").getTextContent());
        assertNull(child(survey, "Crew"));
        assertLocalReference(survey, "Mark", version, "mark");
        var unassigned = object(projected, "Survey", "unassigned");
        assertNull(child(unassigned, "Mark"));
        object(projected, "FixedMarker", "mark");
        assertPosition(survey, "Follows", version, "course", positions ? "21" : "");
        assertPosition(unassigned, "Follows", version, "course", "");

        var detailed = object(projected, "Document", "detailed");
        assertPosition(detailed, "FiledAt", version, "archive", positions ? "34" : "");
        var payload = children(child(detailed, "FiledAt")).getFirst();
        assertName(payload, version, base + ".Data.Filing");
        assertEquals("registered", child(payload, "Remark").getTextContent());
        assertEquals("9", child(payload, "Priority").getTextContent());
        var direct = object(projected, "Document", "direct");
        assertPosition(direct, "FiledAt", version, "archive", positions ? "18" : "");
        assertEquals(List.of(), children(child(direct, "FiledAt")));
        assertNull(child(object(projected, "Document", "unfiled"), "FiledAt"));

        String linkTag = version == 23 ? base + ".Data.Observation" : "Observation";
        var links = children(projected).stream().filter(element -> linkTag.equals(element.getLocalName()))
                .collect(Collectors.toMap(element -> id(child(element, "ObservingSurvey"), version, "ref"),
                        Function.identity()));
        assertEquals(Set.of("survey", "unassigned"), links.keySet());
        for (var link : links.values()) assertName(link, version, base + ".Data.Observation");
        assertPosition(links.get("survey"), "ObservedCourse", version, "course", positions ? "3" : "");
        assertPosition(links.get("survey"), "ObservingSurvey", version, "survey", positions ? "12" : "");
        assertPosition(links.get("unassigned"), "ObservedCourse", version, "course", "");
        assertPosition(links.get("unassigned"), "ObservingSurvey", version, "unassigned", positions ? "2" : "");
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

            String documents = db.table("document");
            String filings = db.table("filing");
            String filedDocuments = " FROM " + filings + " f JOIN " + documents
                    + " d ON f.fileddocument=d.t_id AND f.t_basket=d.t_basket";
            assertEquals(Set.of("detailed:archive", "direct:archive"),
                    catalogKeys(sql, "SELECT d.t_ili_tid || ':' || a.t_ili_tid" + filedDocuments
                            + " JOIN " + db.table("archive") + " a ON f.filedat=a.t_id AND f.t_basket=a.t_basket"));
            assertEquals("registered:9", scalar(sql, "SELECT f.remark || ':' || f.priority" + filedDocuments
                    + " WHERE d.t_ili_tid='detailed'"));
            assertEquals("direct", scalar(sql, "SELECT d.t_ili_tid" + filedDocuments
                    + " WHERE f.remark IS NULL AND f.priority IS NULL"));
            assertEquals("unfiled", scalar(sql, "SELECT d.t_ili_tid FROM " + documents
                    + " d WHERE NOT EXISTS (SELECT 1 FROM " + filings + " f WHERE f.fileddocument=d.t_id)"));
        }
    }

    void assertStoredPositions(Target db, boolean positions) throws Exception {
        try (var sql = db.connect()) {
            String courses = db.table("course");
            String surveys = db.table("survey");
            assertEquals(Set.of("survey:course:" + (positions ? "21" : ""), "unassigned:course:"),
                    catalogKeys(sql, "SELECT s.t_ili_tid || ':' || c.t_ili_tid || ':'"
                            + " || coalesce(cast(s.afollows_t_seq as text),'') FROM " + surveys + " s JOIN " + courses
                            + " c ON s.afollows=c.t_id AND s.t_basket=c.t_basket"));
            assertEquals(Set.of("detailed:" + (positions ? "34" : ""), "direct:" + (positions ? "18" : "")),
                    catalogKeys(sql, "SELECT d.t_ili_tid || ':' || coalesce(cast(f.fileddocument_t_seq as text),'')"
                            + " FROM " + db.table("filing") + " f JOIN " + db.table("document")
                            + " d ON f.fileddocument=d.t_id AND f.t_basket=d.t_basket"));
            assertEquals(Set.of("course:survey:" + (positions ? "3:12" : ":"),
                            "course:unassigned:" + (positions ? ":2" : ":")),
                    catalogKeys(sql, "SELECT c.t_ili_tid || ':' || s.t_ili_tid || ':'"
                            + " || coalesce(cast(o.observedcourse_t_seq as text),'') || ':'"
                            + " || coalesce(cast(o.observingsurvey_t_seq as text),'') FROM " + db.table("observation")
                            + " o JOIN " + courses + " c ON o.observedcourse=c.t_id AND o.t_basket=c.t_basket JOIN "
                            + surveys + " s ON o.observingsurvey=s.t_id AND o.t_basket=s.t_basket"));
        }
    }
}
