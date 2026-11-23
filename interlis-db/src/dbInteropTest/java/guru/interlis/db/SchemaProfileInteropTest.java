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

    @Test void geoPackageProfileFkIndex23() throws Exception { exchangeProfileFkIndex(23, null); }
    @Test void geoPackageProfileFkIndex24() throws Exception { exchangeProfileFkIndex(24, null); }
    @Test void postgisProfileFkIndex23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileFkIndex); }
    @Test void postgisProfileFkIndex24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileFkIndex); }

    private void exchangeProfileFkIndex(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_idx_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).foreignKeyIndexes(expected, expected.peer("profile_idx_own"));
    }

    @Test void geoPackageProfileForeignKeys23() throws Exception { exchangeProfileForeignKeys(23, null); }
    @Test void geoPackageProfileForeignKeys24() throws Exception { exchangeProfileForeignKeys(24, null); }
    @Test void postgisProfileForeignKeys23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileForeignKeys); }
    @Test void postgisProfileForeignKeys24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileForeignKeys); }

    private void exchangeProfileForeignKeys(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_fk_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).foreignKeys(expected, expected.peer("profile_fk_own"));
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

    @Test void geoPackageProfileDatasetColumn23() throws Exception { exchangeProfileDatasetColumn(23, null); }
    @Test void geoPackageProfileDatasetColumn24() throws Exception { exchangeProfileDatasetColumn(24, null); }
    @Test void postgisProfileDatasetColumn23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileDatasetColumn); }
    @Test void postgisProfileDatasetColumn24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileDatasetColumn); }

    private void exchangeProfileDatasetColumn(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_dataset_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).datasetColumn(expected, expected.peer("profile_dataset_own"));
    }

    @Test void geoPackageProfileStandardColumns23() throws Exception { exchangeProfileStandardColumns(23, null); }
    @Test void geoPackageProfileStandardColumns24() throws Exception { exchangeProfileStandardColumns(24, null); }
    @Test void postgisProfileStandardColumns23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileStandardColumns); }
    @Test void postgisProfileStandardColumns24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileStandardColumns); }

    private void exchangeProfileStandardColumns(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_audit_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).standardColumns(expected, expected.peer("profile_audit_own"));
    }

    @Test void geoPackageProfileUnique23() throws Exception { exchangeProfileUnique(23, null); }
    @Test void geoPackageProfileUnique24() throws Exception { exchangeProfileUnique(24, null); }
    @Test void postgisProfileUnique23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileUnique); }
    @Test void postgisProfileUnique24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileUnique); }

    private void exchangeProfileUnique(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_unique_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).uniqueConstraints(expected, expected.peer("profile_unique_own"));
    }

    @Test void geoPackageProfileNumericChecks23() throws Exception { exchangeProfileNumericChecks(23, null); }
    @Test void geoPackageProfileNumericChecks24() throws Exception { exchangeProfileNumericChecks(24, null); }
    @Test void postgisProfileNumericChecks23() throws Exception { exchangeWithPostgis(23, this::exchangeProfileNumericChecks); }
    @Test void postgisProfileNumericChecks24() throws Exception { exchangeWithPostgis(24, this::exchangeProfileNumericChecks); }

    private void exchangeProfileNumericChecks(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "profile_numeric_ref");
        new SchemaProfileCases(work, new ReferenceTool(work, expected.postgis()), version).numericChecks(expected, expected.peer("profile_numeric_own"));
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
