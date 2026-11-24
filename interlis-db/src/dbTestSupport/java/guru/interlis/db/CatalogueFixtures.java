package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CatalogueFixtures {
    private final Path work;
    private final int version;
    private final Path source;
    final String model;
    final Path catalogue;
    final Path survey;

    CatalogueFixtures(Path work, String model, int version) throws Exception {
        this.work = work;
        this.model = model;
        this.version = version;
        source = fixture(model + ".ili");
        catalogue = fixture(model + "-catalog.xtf");
        survey = fixture(model + "-survey.xtf");
    }

    void schema(Target db, String layout) {
        if (layout.equals("newClass")) {
            assertSchemaWithIdentityColumns(db, model, source, "--coalesce-catalogue-ref");
        } else {
            assertSchemaWithIdentityColumns(db, model, source, "--coalesce-catalogue-ref", "--inheritance", layout);
        }
    }

    Path smart2Survey() throws Exception {
        return Files.writeString(work.resolve("smart2-survey.xtf"), Files.readString(survey).replaceFirst("material-a", "secondary-a"));
    }

    void assertStoredReferences(Target db, boolean smart2, String dataset, String prefix) throws Exception {
        String targetTable = db.table(smart2 ? "asecondary" : "item");
        String requiredTable = db.table(smart2 ? "material" : "item");
        String mainColumn = smart2 ? "mainmaterial_asecondary" : "mainmaterial";
        String requiredColumn = smart2 ? "requiredmaterial_material" : "requiredmaterial";
        String baskets = db.table("t_ili2db_basket");
        String datasets = db.table("t_ili2db_dataset");
        String assets = db.table("asset");
        try (var sql = db.connect()) {
            String surveyRows = " FROM " + assets + " a JOIN " + baskets + " b ON b.t_id=a.t_basket"
                    + " JOIN " + datasets + " d ON d.t_id=b.dataset";
            String surveyBasket = " d.datasetname='" + dataset + "' AND b.t_ili_tid='" + prefix + "-b'";
            assertEquals("3", scalar(sql, "SELECT count(*)" + surveyRows + " WHERE" + surveyBasket));
            String alternatives = smart2 ? " AND a.mainmaterial_material IS NULL AND a.requiredmaterial_asecondary IS NULL" : "";
            if (!smart2) {
                // newClass stores the targets' identities in the shared item table and their values in material.
                surveyRows += " JOIN " + db.table("material") + " m ON m.t_id=a." + requiredColumn;
            }
            assertEquals("1", scalar(sql, "SELECT count(*)" + surveyRows
                    + " JOIN " + targetTable + " t ON t.t_id=a." + mainColumn
                    + " JOIN " + requiredTable + " r ON r.t_id=a." + requiredColumn
                    + " JOIN " + baskets + " cb ON cb.t_id=t.t_basket AND cb.t_id=r.t_basket"
                    + " JOIN " + datasets + " cd ON cd.t_id=cb.dataset WHERE" + surveyBasket
                    + " AND a.t_ili_tid='" + prefix + "-a' AND t.t_ili_tid='" + (smart2 ? "secondary-a" : "material-a")
                    + "' AND r.t_ili_tid='material-a' AND cb.t_ili_tid='cat-b' AND cd.datasetname='catalog'" + alternatives));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + assets
                    + " a WHERE a.t_ili_tid IN ('" + prefix + "-empty','" + prefix + "-absent')"
                    + " AND a." + mainColumn + " IS NULL AND a." + requiredColumn + " IS NULL" + alternatives));
        }
    }

    void assertSurvey(Path output, String prefix, String mainTarget) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(prefix + "-b", id(basket, version, "bid"));
        assertEquals(version == 23 ? model + ".Survey" : "Survey", basket.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + model, basket.getNamespaceURI());
        Map<String, Element> assets = children(basket).stream()
                .collect(Collectors.toMap(asset -> id(asset, version, "tid"), Function.identity()));
        assertEquals(3, assets.size());
        var record = assets.get(prefix + "-a");
        assertReference(record, "MainMaterial", "MaterialRef", mainTarget);
        assertReference(record, "RequiredMaterial", "MaterialMandatory", "material-a");
        for (var name : List.of("empty", "absent")) {
            assertNull(child(assets.get(prefix + "-" + name), "MainMaterial"));
            assertNull(child(assets.get(prefix + "-" + name), "RequiredMaterial"));
        }
    }

    private void assertReference(Element asset, String field, String structure, String target) {
        var attribute = child(asset, field);
        assertNotNull(attribute, field);
        var wrapper = children(attribute).getFirst();
        assertEquals(version == 23 ? model + ".Catalog." + structure : structure, wrapper.getLocalName());
        var ref = child(wrapper, "Reference");
        assertNotNull(ref);
        assertEquals(target, id(ref, version, "ref"));
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/CatalogueObjects_V1", ref.getNamespaceURI());
    }

    Path surveyWithPrefix(Path input, String prefix) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"), Files.readString(input)
                .replace("survey-", prefix + "-"));
    }

    private Path fixture(String name) throws Exception {
        var path = work.resolve(name);
        try (var source = getClass().getResourceAsStream("/catalogue/" + name)) {
            Files.copy(Objects.requireNonNull(source, name), path);
        }
        return path;
    }
}
