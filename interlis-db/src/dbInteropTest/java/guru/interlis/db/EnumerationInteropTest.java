package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;

final class EnumerationInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageEnumItfSchema23() throws Exception { exchangeEnumItfSchema(23, null); }
    @Test void geoPackageEnumItfSchema24() throws Exception { exchangeEnumItfSchema(24, null); }
    @Test void postgisEnumItfSchema23() throws Exception { exchangeEnumItfSchema(23, POSTGIS.container()); }
    @Test void postgisEnumItfSchema24() throws Exception { exchangeEnumItfSchema(24, POSTGIS.container()); }

    private void exchangeEnumItfSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_itf_ddl_ref", "v" + version + "_");
        var db = new TestDatabase(work, postgres, "enum_itf_ddl_own", "v" + version + "_");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).itfSchema(expected, db);
    }

    @Test void geoPackageEnumText23() throws Exception { exchangeEnumText(23, null); }
    @Test void geoPackageEnumText24() throws Exception { exchangeEnumText(24, null); }
    @Test void postgisEnumText23() throws Exception { exchangeEnumText(23, POSTGIS.container()); }
    @Test void postgisEnumText24() throws Exception { exchangeEnumText(24, POSTGIS.container()); }

    private void exchangeEnumText(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_txt_ref", "v" + version + "_");
        var db = new TestDatabase(work, postgres, "enum_txt_own", "v" + version + "_");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).enumText(expected, db);
    }

    @Test void geoPackageEnumSingleLookup23() throws Exception { exchangeEnumSingleLookup(23, null); }
    @Test void geoPackageEnumSingleLookup24() throws Exception { exchangeEnumSingleLookup(24, null); }
    @Test void postgisEnumSingleLookup23() throws Exception { exchangeEnumSingleLookup(23, POSTGIS.container()); }
    @Test void postgisEnumSingleLookup24() throws Exception { exchangeEnumSingleLookup(24, POSTGIS.container()); }

    private void exchangeEnumSingleLookup(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_single_ref", "v" + version + "_");
        var db = new TestDatabase(work, postgres, "enum_single_own", "v" + version + "_");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).singleLookup(expected, db);
    }

    @Test void geoPackageEnumPerLookup23() throws Exception { exchangeEnumPerLookup(23, null); }
    @Test void geoPackageEnumPerLookup24() throws Exception { exchangeEnumPerLookup(24, null); }
    @Test void postgisEnumPerLookup23() throws Exception { exchangeEnumPerLookup(23, POSTGIS.container()); }
    @Test void postgisEnumPerLookup24() throws Exception { exchangeEnumPerLookup(24, POSTGIS.container()); }

    private void exchangeEnumPerLookup(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_per_ref", "v" + version + "_");
        var db = new TestDatabase(work, postgres, "enum_per_own", "v" + version + "_");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).perLookup(expected, db);
    }

    @Test void geoPackageEnumIdExtension23() throws Exception { exchangeEnumIdExtension(23, null); }
    @Test void geoPackageEnumIdExtension24() throws Exception { exchangeEnumIdExtension(24, null); }
    @Test void postgisEnumIdExtension23() throws Exception { exchangeEnumIdExtension(23, POSTGIS.container()); }
    @Test void postgisEnumIdExtension24() throws Exception { exchangeEnumIdExtension(24, POSTGIS.container()); }

    private void exchangeEnumIdExtension(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_id_ext_ref", "v" + version + "_");
        var db = new TestDatabase(work, postgres, "enum_id_ext_own", "v" + version + "_");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).idExtension(expected, db);
    }

    @Test void geoPackageEnumAppend23() throws Exception { exchangeEnumAppend(23, null); }
    @Test void geoPackageEnumAppend24() throws Exception { exchangeEnumAppend(24, null); }
    @Test void postgisEnumAppend23() throws Exception { exchangeEnumAppend(23, POSTGIS.container()); }
    @Test void postgisEnumAppend24() throws Exception { exchangeEnumAppend(24, POSTGIS.container()); }

    private void exchangeEnumAppend(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_append_ref", "v" + version + "_");
        var db = new TestDatabase(work, postgres, "enum_append_own", "v" + version + "_");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).appendEnums(expected, db);
    }

    @Test void geoPackageEnumNumericArrays23() throws Exception { exchangeEnumNumericArrays(23, null); }
    @Test void geoPackageEnumNumericArrays24() throws Exception { exchangeEnumNumericArrays(24, null); }
    @Test void postgisEnumNumericArrays23() throws Exception { exchangeEnumNumericArrays(23, POSTGIS.container()); }
    @Test void postgisEnumNumericArrays24() throws Exception { exchangeEnumNumericArrays(24, POSTGIS.container()); }

    private void exchangeEnumNumericArrays(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_numeric_array_ref", "v" + version + "_");
        var db = new TestDatabase(work, postgres, "enum_numeric_array_own", "v" + version + "_");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).numericArrays(expected, db);
    }
}
