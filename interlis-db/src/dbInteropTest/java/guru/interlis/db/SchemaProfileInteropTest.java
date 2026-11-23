package guru.interlis.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

import static guru.interlis.db.TestDatabase.exchangeWithPostgis;

final class SchemaProfileInteropTest {
    @TempDir(cleanup = CleanupMode.ALWAYS) Path work;

    @Test void geoPackageProfileNullability23() throws Exception { exchangeProfileNullability(23, null); }
    @Test void geoPackageProfileNullability24() throws Exception { exchangeProfileNullability(24, null); }
    @Test void postgisProfileNullability23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileNullability); }
    @Test void postgisProfileNullability24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileNullability); }

    private void exchangeProfileNullability(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_nullability_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).modelNullability(expected);
    }

    @Test void geoPackageProfileForeignKeys23() throws Exception { exchangeProfileForeignKeys(23, null); }
    @Test void geoPackageProfileForeignKeys24() throws Exception { exchangeProfileForeignKeys(24, null); }
    @Test void postgisProfileForeignKeys23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileForeignKeys); }
    @Test void postgisProfileForeignKeys24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileForeignKeys); }

    private void exchangeProfileForeignKeys(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_fk_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).foreignKeyProfiles(expected, expected.peer("profile_fk_own"));
    }

    @Test void geoPackageProfileHierarchyKeys23() throws Exception { exchangeProfileHierarchyKeys(23, null); }
    @Test void geoPackageProfileHierarchyKeys24() throws Exception { exchangeProfileHierarchyKeys(24, null); }
    @Test void postgisProfileHierarchyKeys23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileHierarchyKeys); }
    @Test void postgisProfileHierarchyKeys24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileHierarchyKeys); }

    private void exchangeProfileHierarchyKeys(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_hierarchy_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).hierarchyKeys(expected, expected.peer("profile_hierarchy_own"));
    }

    @Test void geoPackageProfileDiscriminator23() throws Exception { exchangeProfileDiscriminator(23, null); }
    @Test void geoPackageProfileDiscriminator24() throws Exception { exchangeProfileDiscriminator(24, null); }
    @Test void postgisProfileDiscriminator23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileDiscriminator); }
    @Test void postgisProfileDiscriminator24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileDiscriminator); }

    private void exchangeProfileDiscriminator(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_type_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).discriminator(expected, expected.peer("profile_type_own"));
    }

    @Test void geoPackageProfileStandardColumns23() throws Exception { exchangeProfileStandardColumns(23, null); }
    @Test void geoPackageProfileStandardColumns24() throws Exception { exchangeProfileStandardColumns(24, null); }
    @Test void postgisProfileStandardColumns23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileStandardColumns); }
    @Test void postgisProfileStandardColumns24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileStandardColumns); }

    private void exchangeProfileStandardColumns(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_audit_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).standardColumns(expected, expected.peer("profile_audit_own"));
    }

    @Test void geoPackageProfileUniqueBasket24() throws Exception { exchangeProfileUniqueBasket(24, null); }
    @Test void postgisProfileUniqueBasket24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileUniqueBasket); }

    private void exchangeProfileUniqueBasket(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_unique_basket_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version)
                .basketUniqueConstraints(expected, expected.peer("profile_unique_basket_own"));
    }

    @Test void geoPackageProfileMetaInformation23() throws Exception { exchangeProfileMetaInformation(23, null); }
    @Test void geoPackageProfileMetaInformation24() throws Exception { exchangeProfileMetaInformation(24, null); }
    @Test void postgisProfileMetaInformation23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileMetaInformation); }
    @Test void postgisProfileMetaInformation24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileMetaInformation); }

    private void exchangeProfileMetaInformation(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_meta_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).metaInformation(expected, expected.peer("profile_meta_own"));
    }
}
