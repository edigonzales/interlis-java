package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

final class SchemaProfileInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir(cleanup = CleanupMode.ALWAYS) Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageProfileNullability23() throws Exception { exchangeProfileNullability(23, null); }
    @Test void geoPackageProfileNullability24() throws Exception { exchangeProfileNullability(24, null); }
    @Test void postgisProfileNullability23() throws Exception { exchangeProfileNullability(23, POSTGIS.container()); }
    @Test void postgisProfileNullability24() throws Exception { exchangeProfileNullability(24, POSTGIS.container()); }

    private void exchangeProfileNullability(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_nullability_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .modelNullability(expected, expected.peer("profile_nullability_own"));
    }

    @Test void geoPackageProfileRequiredRoles23() throws Exception { exchangeProfileRequiredRoles(23, null); }
    @Test void geoPackageProfileRequiredRoles24() throws Exception { exchangeProfileRequiredRoles(24, null); }
    @Test void postgisProfileRequiredRoles23() throws Exception { exchangeProfileRequiredRoles(23, POSTGIS.container()); }
    @Test void postgisProfileRequiredRoles24() throws Exception { exchangeProfileRequiredRoles(24, POSTGIS.container()); }

    private void exchangeProfileRequiredRoles(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_required_role_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .requiredRoles(expected, expected.peer("profile_required_role_own"));
    }

    @Test void geoPackageProfileStrictLinks23() throws Exception { exchangeProfileStrictLinks(23, null); }
    @Test void geoPackageProfileStrictLinks24() throws Exception { exchangeProfileStrictLinks(24, null); }
    @Test void postgisProfileStrictLinks23() throws Exception { exchangeProfileStrictLinks(23, POSTGIS.container()); }
    @Test void postgisProfileStrictLinks24() throws Exception { exchangeProfileStrictLinks(24, POSTGIS.container()); }

    private void exchangeProfileStrictLinks(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_strict_links_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .strictLinks(expected, expected.peer("profile_strict_links_own"));
    }

    @Test void geoPackageProfileStrictSmart123() throws Exception { exchangeProfileStrictSmart1(23, null); }
    @Test void geoPackageProfileStrictSmart124() throws Exception { exchangeProfileStrictSmart1(24, null); }
    @Test void postgisProfileStrictSmart123() throws Exception { exchangeProfileStrictSmart1(23, POSTGIS.container()); }
    @Test void postgisProfileStrictSmart124() throws Exception { exchangeProfileStrictSmart1(24, POSTGIS.container()); }

    private void exchangeProfileStrictSmart1(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_strict_smart1_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .strictSmart1(expected, expected.peer("profile_strict_smart1_own"));
    }

    @Test void geoPackageProfileRequiredReferences23() throws Exception { exchangeProfileRequiredReferences(23, null); }
    @Test void geoPackageProfileRequiredReferences24() throws Exception { exchangeProfileRequiredReferences(24, null); }
    @Test void postgisProfileRequiredReferences23() throws Exception { exchangeProfileRequiredReferences(23, POSTGIS.container()); }
    @Test void postgisProfileRequiredReferences24() throws Exception { exchangeProfileRequiredReferences(24, POSTGIS.container()); }

    private void exchangeProfileRequiredReferences(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_required_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .requiredReferences(expected, expected.peer("profile_required_ref_own"));
    }

    @Test void geoPackageProfileNestedStructures23() throws Exception { exchangeProfileNestedStructures(23, null); }
    @Test void geoPackageProfileNestedStructures24() throws Exception { exchangeProfileNestedStructures(24, null); }
    @Test void postgisProfileNestedStructures23() throws Exception { exchangeProfileNestedStructures(23, POSTGIS.container()); }
    @Test void postgisProfileNestedStructures24() throws Exception { exchangeProfileNestedStructures(24, POSTGIS.container()); }

    private void exchangeProfileNestedStructures(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_nested_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .nestedStructures(expected, expected.peer("profile_nested_own"));
    }

    @Test void geoPackageProfileRequiredHierarchy23() throws Exception { exchangeProfileRequiredHierarchy(23, null); }
    @Test void geoPackageProfileRequiredHierarchy24() throws Exception { exchangeProfileRequiredHierarchy(24, null); }
    @Test void postgisProfileRequiredHierarchy23() throws Exception { exchangeProfileRequiredHierarchy(23, POSTGIS.container()); }
    @Test void postgisProfileRequiredHierarchy24() throws Exception { exchangeProfileRequiredHierarchy(24, POSTGIS.container()); }

    private void exchangeProfileRequiredHierarchy(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_strict_hierarchy_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .requiredHierarchy(expected, expected.peer("profile_strict_hierarchy_own"));
    }

    @Test void geoPackageProfileRequiredGeometries23() throws Exception { exchangeProfileRequiredGeometries(23, null); }
    @Test void geoPackageProfileRequiredGeometries24() throws Exception { exchangeProfileRequiredGeometries(24, null); }
    @Test void postgisProfileRequiredGeometries23() throws Exception { exchangeProfileRequiredGeometries(23, POSTGIS.container()); }
    @Test void postgisProfileRequiredGeometries24() throws Exception { exchangeProfileRequiredGeometries(24, POSTGIS.container()); }

    private void exchangeProfileRequiredGeometries(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_required_geom_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .requiredGeometries(expected, expected.peer("profile_required_geom_own"));
    }

    @Test void geoPackageProfileRequiredGeometryFragments23() throws Exception { exchangeProfileRequiredGeometryFragments(23, null); }
    @Test void geoPackageProfileRequiredGeometryFragments24() throws Exception { exchangeProfileRequiredGeometryFragments(24, null); }
    @Test void postgisProfileRequiredGeometryFragments23() throws Exception { exchangeProfileRequiredGeometryFragments(23, POSTGIS.container()); }
    @Test void postgisProfileRequiredGeometryFragments24() throws Exception { exchangeProfileRequiredGeometryFragments(24, POSTGIS.container()); }

    private void exchangeProfileRequiredGeometryFragments(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_required_geom_fragments_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .requiredGeometryFragments(expected, expected.peer("profile_required_geom_fragments_own"));
    }

    @Test void geoPackageProfileForeignKeys23() throws Exception { exchangeProfileForeignKeys(23, null); }
    @Test void geoPackageProfileForeignKeys24() throws Exception { exchangeProfileForeignKeys(24, null); }
    @Test void postgisProfileForeignKeys23() throws Exception { exchangeProfileForeignKeys(23, POSTGIS.container()); }
    @Test void postgisProfileForeignKeys24() throws Exception { exchangeProfileForeignKeys(24, POSTGIS.container()); }

    private void exchangeProfileForeignKeys(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_fk_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).foreignKeyProfiles(expected, expected.peer("profile_fk_own"));
    }

    @Test void geoPackageProfileHierarchyKeys23() throws Exception { exchangeProfileHierarchyKeys(23, null); }
    @Test void geoPackageProfileHierarchyKeys24() throws Exception { exchangeProfileHierarchyKeys(24, null); }
    @Test void postgisProfileHierarchyKeys23() throws Exception { exchangeProfileHierarchyKeys(23, POSTGIS.container()); }
    @Test void postgisProfileHierarchyKeys24() throws Exception { exchangeProfileHierarchyKeys(24, POSTGIS.container()); }

    private void exchangeProfileHierarchyKeys(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_hierarchy_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).hierarchyKeys(expected, expected.peer("profile_hierarchy_own"));
    }

    @Test void geoPackageProfileDiscriminator23() throws Exception { exchangeProfileDiscriminator(23, null); }
    @Test void geoPackageProfileDiscriminator24() throws Exception { exchangeProfileDiscriminator(24, null); }
    @Test void postgisProfileDiscriminator23() throws Exception { exchangeProfileDiscriminator(23, POSTGIS.container()); }
    @Test void postgisProfileDiscriminator24() throws Exception { exchangeProfileDiscriminator(24, POSTGIS.container()); }

    private void exchangeProfileDiscriminator(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_type_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).discriminator(expected, expected.peer("profile_type_own"));
    }

    @Test void geoPackageProfileStandardColumns23() throws Exception { exchangeProfileStandardColumns(23, null); }
    @Test void geoPackageProfileStandardColumns24() throws Exception { exchangeProfileStandardColumns(24, null); }
    @Test void postgisProfileStandardColumns23() throws Exception { exchangeProfileStandardColumns(23, POSTGIS.container()); }
    @Test void postgisProfileStandardColumns24() throws Exception { exchangeProfileStandardColumns(24, POSTGIS.container()); }

    private void exchangeProfileStandardColumns(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_audit_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).standardColumns(expected, expected.peer("profile_audit_own"));
    }

    @Test void geoPackageProfileUniqueBasket24() throws Exception { exchangeProfileUniqueBasket(24, null); }
    @Test void postgisProfileUniqueBasket24() throws Exception { exchangeProfileUniqueBasket(24, POSTGIS.container()); }

    private void exchangeProfileUniqueBasket(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_unique_basket_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .basketUniqueConstraints(expected, expected.peer("profile_unique_basket_own"));
    }

    @Test void geoPackageProfileMetaInformation23() throws Exception { exchangeProfileMetaInformation(23, null); }
    @Test void geoPackageProfileMetaInformation24() throws Exception { exchangeProfileMetaInformation(24, null); }
    @Test void postgisProfileMetaInformation23() throws Exception { exchangeProfileMetaInformation(23, POSTGIS.container()); }
    @Test void postgisProfileMetaInformation24() throws Exception { exchangeProfileMetaInformation(24, POSTGIS.container()); }

    private void exchangeProfileMetaInformation(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_meta_ref", "v" + version + "_");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis(), "UTC"), version)
                .metaInformation(expected, expected.peer("profile_meta_own"));
    }
}
