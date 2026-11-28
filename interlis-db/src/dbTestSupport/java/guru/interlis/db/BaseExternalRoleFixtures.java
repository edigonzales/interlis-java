package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertExternalReference;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.text;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.fixture;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

record BaseExternalRoleFixtures(int version, String parent, String derived, Path parentSource, Path childSource, Path delivery) {
    static BaseExternalRoleFixtures create(Path work, int version) throws Exception {
        String parent = "ExternalBase" + version;
        String child = "ExternalChild" + version;
        return new BaseExternalRoleFixtures(version, parent, child, fixture(work, "topics/" + parent + ".ili"),
                fixture(work, "topics/" + child + ".ili"), fixture(work, "topics/external" + version + ".xtf"));
    }

    void schema(Target db) throws Exception {
        assertSchemaWithIdentityColumns(db, derived, childSource, "--model-file", parentSource.toString());
    }

    void assertProjection(Path path, boolean full, boolean nativeExport) throws Exception {
        var data = readDataSection(path, version);
        assertEquals(List.of(parent), headerModels(data, version));
        assertEquals(full ? 2 : 1, children(data).size());
        if (full) {
            var catalog = basket(data, version, parent + ".Registry", "catalog-b");
            assertEquals(1, children(catalog).size());
            var site = children(catalog).getFirst();
            assertName(site, version, parent + ".Registry.RegisteredSite");
            assertEquals("catalog-site", id(site, version, "tid"));
            assertEquals("railway station", text(site, "Label"));
        }

        String selection = full ? "full" : "partial";
        var surveys = basket(data, version, parent + ".Surveys", "region-" + selection);
        assertEquals(1, children(surveys).size());
        var report = children(surveys).getFirst();
        assertName(report, version, parent + ".Surveys.FieldReport");
        assertEquals("report-" + selection, id(report, version, "tid"));
        assertEquals(full ? "first survey" : "second survey", text(report, "Note"));
        assertNull(child(report, "LocalNote"));
        assertExternalReference(child(report, "CatalogSite"), version, "catalog-site", nativeExport ? "catalog-b" : "");
    }

    void assertReimportedRoles(Target db) throws Exception {
        try (var sql = db.connect()) {
            for (String part : List.of("full", "partial")) {
                assertEquals("region-" + part + ":catalog-b:catalog-site:railway station", scalar(sql, """
                        SELECT src.t_ili_tid || ':' || dst.t_ili_tid || ':' || site.t_ili_tid || ':' || site.label
                        FROM %s report JOIN %s site ON site.t_id=report.catalogsite
                        JOIN %s src ON src.t_id=report.t_basket
                        JOIN %s dst ON dst.t_id=site.t_basket
                        WHERE report.t_ili_tid='report-%s'
                        """.formatted(db.table("fieldreport"), db.table("registeredsite"), db.table("t_ili2db_basket"),
                        db.table("t_ili2db_basket"), part)));
            }
        }
    }
}
