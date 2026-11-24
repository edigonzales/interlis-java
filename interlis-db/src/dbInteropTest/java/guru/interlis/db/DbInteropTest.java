package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static guru.interlis.db.BaseDbFixture.assertRows;
import static guru.interlis.db.BaseDbFixture.assertTransferredAmounts;
import static guru.interlis.db.BaseDbFixture.assertTransferredNestedTree;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertLocalReference;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.text;
import static guru.interlis.db.DbTestSupport.transferredStructures;
import static guru.interlis.db.ObjectIdFixtures.assertTextOidTransfer;
import static guru.interlis.db.ReferenceTool.logText;
import static guru.interlis.db.TestDatabase.exchangeWithPostgis;
import static guru.interlis.db.TransferCatalogAssertions.assertAssociationCatalog;
import static guru.interlis.db.TransferCatalogAssertions.assertReferenceMapping;
import static guru.interlis.db.TransferCatalogAssertions.assertTypeKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DbInteropTest {
    private static final String STORED_MODELS = "%ILI_FROM_DB";
    private static final int POINT = 1;
    private static final int LINESTRING = 2;
    private static final int MULTIPOINT = 4;
    private static final int CIRCULARSTRING = 8;
    private static final int COMPOUNDCURVE = 9;
    private static final int CURVEPOLYGON = 10;
    private static final int MULTICURVE = 11;
    private static final int MULTISURFACE = 12;
    private static final List<String> MAST_CENTER = List.of("2600000.125", "1200000.375");
    private static final List<String> SERVICE_POINT = List.of("2600100.250", "1200100.625");
    private static final List<List<String>> SURVEY_POINTS = List.of(
            List.of("2601500.625", "1201500.500"), List.of("2601100.125", "1201100.750"));
    private static final DetailValue SUMMARY = new DetailValue("review", true, -3, "approved");
    private static final DetailValue DRAFT_ENTRY = new DetailValue("b", false, 2, "draft");
    private static final List<DetailValue> ENTRIES = List.of(
            DRAFT_ENTRY, new DetailValue("a", null, -1, "approved"), DRAFT_ENTRY);
    private static final DetailValue FIRST_OBSERVATION = new DetailValue("x", true, null, null);
    private static final List<DetailValue> OBSERVATIONS = List.of(
            FIRST_OBSERVATION, new DetailValue("r", null, 7, "approved"), FIRST_OBSERVATION);
    private static final DetailValue LATER_ENTRY = new DetailValue("later", null, null, null);
    private static final List<String> MARKER_LABELS = List.of("first", "second", "unlinked");
    private static final List<CurveComponent> STRAIGHT_ROUTE = List.of(new CurveComponent(LINESTRING, List.of(
            List.of("2600123.125", "1200456.375"), List.of("2600345.250", "1200678.500"),
            List.of("2600567.625", "1200456.750"))));
    private static final List<CurveComponent> ARC_ROUTE = List.of(new CurveComponent(CIRCULARSTRING, List.of(
            List.of("2600500.125", "1200500.250"), List.of("2600505.125", "1200505.250"),
            List.of("2600510.125", "1200500.250"), List.of("2600515.125", "1200495.250"),
            List.of("2600520.125", "1200500.250"))));
    private static final List<CurveComponent> MIXED_ROUTE = List.of(
            new CurveComponent(LINESTRING, List.of(
                    List.of("2600600.500", "1200600.250"), List.of("2600610.500", "1200600.250"))),
            new CurveComponent(CIRCULARSTRING, List.of(
                    List.of("2600610.500", "1200600.250"), List.of("2600615.500", "1200605.250"),
                    List.of("2600620.500", "1200600.250"))),
            new CurveComponent(LINESTRING, List.of(
                    List.of("2600620.500", "1200600.250"), List.of("2600630.500", "1200600.250"))));
    private static final List<List<CurveComponent>> SURFACE_WITH_HOLE = List.of(
            List.of(new CurveComponent(LINESTRING, List.of(
                    List.of("2601000.125", "1201000.375"), List.of("2601040.250", "1201000.375"),
                    List.of("2601040.250", "1201040.500"), List.of("2601000.125", "1201040.500"),
                    List.of("2601000.125", "1201000.375")))),
            List.of(new CurveComponent(LINESTRING, List.of(
                    List.of("2601008.125", "1201010.375"), List.of("2601008.125", "1201028.625"),
                    List.of("2601020.250", "1201020.500"), List.of("2601008.125", "1201010.375")))));
    private static final List<List<CurveComponent>> CURVED_SURFACE = List.of(List.of(
            new CurveComponent(CIRCULARSTRING, List.of(
                    List.of("2601200.250", "1201200.625"), List.of("2601210.250", "1201190.625"),
                    List.of("2601220.250", "1201200.625"))),
            new CurveComponent(LINESTRING, List.of(
                    List.of("2601220.250", "1201200.625"), List.of("2601200.250", "1201200.625")))));
    // The two parcel rings traverse their common edge in opposite directions.
    private static final List<List<CurveComponent>> WEST_AREA = List.of(List.of(new CurveComponent(LINESTRING, List.of(
            List.of("2602000.125", "1202000.250"), List.of("2602020.250", "1202000.250"),
            List.of("2602020.250", "1202020.750"), List.of("2602000.125", "1202020.750"),
            List.of("2602000.125", "1202000.250")))));
    private static final List<List<CurveComponent>> EAST_AREA = List.of(List.of(new CurveComponent(LINESTRING, List.of(
            List.of("2602020.250", "1202000.250"), List.of("2602040.500", "1202000.250"),
            List.of("2602040.500", "1202020.750"), List.of("2602020.250", "1202020.750"),
            List.of("2602020.250", "1202000.250")))));

    @TempDir(cleanup = CleanupMode.ALWAYS) Path work;

    @Test void geoPackageScalarWide23() throws Exception { exchangeScalarWide(23, null); }
    @Test void geoPackageScalarWide24() throws Exception { exchangeScalarWide(24, null); }
    @Test void postgisScalarWide23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarWide); }
    @Test void postgisScalarWide24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarWide); }

    private void exchangeScalarWide(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_wide_ref");
        var own = new TestDatabase(work, postgres, "scalar_wide_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis()), version).wideIntegers(expected, own);
    }

    @Test void geoPackageScalarDecimal23() throws Exception { exchangeScalarDecimal(23, null); }
    @Test void geoPackageScalarDecimal24() throws Exception { exchangeScalarDecimal(24, null); }
    @Test void postgisScalarDecimal23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarDecimal); }
    @Test void postgisScalarDecimal24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarDecimal); }

    private void exchangeScalarDecimal(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_decimal_ref");
        var own = new TestDatabase(work, postgres, "scalar_decimal_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis()), version).decimals(expected, own);
    }

    @Test void geoPackageScalarDate23() throws Exception { exchangeScalarDate(23, null); }
    @Test void geoPackageScalarDate24() throws Exception { exchangeScalarDate(24, null); }
    @Test void postgisScalarDate23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarDate); }
    @Test void postgisScalarDate24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarDate); }

    private void exchangeScalarDate(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_date_ref");
        var own = new TestDatabase(work, postgres, "scalar_date_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis()), version).calendarDate(expected, own);
    }

    @Test void geoPackageScalarTime23() throws Exception { exchangeScalarTime(23, null); }
    @Test void geoPackageScalarTime24() throws Exception { exchangeScalarTime(24, null); }
    @Test void postgisScalarTime23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarTime); }
    @Test void postgisScalarTime24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarTime); }

    private void exchangeScalarTime(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_time_ref");
        var own = new TestDatabase(work, postgres, "scalar_time_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis(), "UTC"), version).localTime(expected, own);
    }

    @Test void geoPackageScalarTimestamp23() throws Exception { exchangeScalarTimestamp(23, null); }
    @Test void geoPackageScalarTimestamp24() throws Exception { exchangeScalarTimestamp(24, null); }
    @Test void postgisScalarTimestamp23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarTimestamp); }
    @Test void postgisScalarTimestamp24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarTimestamp); }

    private void exchangeScalarTimestamp(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_timestamp_ref");
        var own = new TestDatabase(work, postgres, "scalar_timestamp_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis(), "UTC"), version).localTimestamp(expected, own);
    }

    @Test void geoPackageScalarFormatted23() throws Exception { exchangeScalarFormatted(23, null); }
    @Test void geoPackageScalarFormatted24() throws Exception { exchangeScalarFormatted(24, null); }
    @Test void postgisScalarFormatted23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarFormatted); }
    @Test void postgisScalarFormatted24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarFormatted); }

    private void exchangeScalarFormatted(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_formatted_ref");
        var own = new TestDatabase(work, postgres, "scalar_formatted_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis()), version).formatted(expected, own);
    }

    @Test void geoPackageScalarUuid23() throws Exception { exchangeScalarUuid(23, null); }
    @Test void geoPackageScalarUuid24() throws Exception { exchangeScalarUuid(24, null); }
    @Test void postgisScalarUuid23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarUuid); }
    @Test void postgisScalarUuid24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarUuid); }

    private void exchangeScalarUuid(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_uuid_ref");
        var own = new TestDatabase(work, postgres, "scalar_uuid_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis()), version).uuidAttribute(expected, own);
    }

    @Test void geoPackageScalarBinary23() throws Exception { exchangeScalarBinary(23, null); }
    @Test void geoPackageScalarBinary24() throws Exception { exchangeScalarBinary(24, null); }
    @Test void postgisScalarBinary23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarBinary); }
    @Test void postgisScalarBinary24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarBinary); }

    private void exchangeScalarBinary(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_binary_ref");
        var own = new TestDatabase(work, postgres, "scalar_binary_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis()), version).binary(expected, own);
    }

    @Test void geoPackageScalarXml23() throws Exception { exchangeScalarXml(23, null); }
    @Test void geoPackageScalarXml24() throws Exception { exchangeScalarXml(24, null); }
    @Test void postgisScalarXml23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarXml); }
    @Test void postgisScalarXml24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarXml); }

    private void exchangeScalarXml(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_xml_ref");
        var own = new TestDatabase(work, postgres, "scalar_xml_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis()), version).xml(expected, own);
    }

    @Test void geoPackageScalarDecimalArray23() throws Exception { exchangeScalarDecimalArray(23, null); }
    @Test void geoPackageScalarDecimalArray24() throws Exception { exchangeScalarDecimalArray(24, null); }
    @Test void postgisScalarDecimalArray23() throws Exception { exchangeWithPostgis(23, this::exchangeScalarDecimalArray); }
    @Test void postgisScalarDecimalArray24() throws Exception { exchangeWithPostgis(24, this::exchangeScalarDecimalArray); }

    private void exchangeScalarDecimalArray(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "scalar_decimal_array_ref");
        var own = new TestDatabase(work, postgres, "scalar_decimal_array_own");
        new ScalarCases(work, new ReferenceTool(work, own.postgis()), version).decimalArrays(expected, own);
    }

    @Test void geoPackageEnumItfCodes23() throws Exception { exchangeEnumItfCodes(23, null); }
    @Test void geoPackageEnumItfCodes24() throws Exception { exchangeEnumItfCodes(24, null); }
    @Test void postgisEnumItfCodes23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumItfCodes); }
    @Test void postgisEnumItfCodes24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumItfCodes); }

    private void exchangeEnumItfCodes(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "enum_itf");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).itfCodes(db);
    }

    @Test void geoPackageEnumItfSchema23() throws Exception { exchangeEnumItfSchema(23, null); }
    @Test void geoPackageEnumItfSchema24() throws Exception { exchangeEnumItfSchema(24, null); }
    @Test void postgisEnumItfSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumItfSchema); }
    @Test void postgisEnumItfSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumItfSchema); }

    private void exchangeEnumItfSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_itf_ddl_ref");
        var db = new TestDatabase(work, postgres, "enum_itf_ddl_own");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).itfSchema(expected, db);
    }

    @Test void geoPackageEnumText23() throws Exception { exchangeEnumText(23, null); }
    @Test void geoPackageEnumText24() throws Exception { exchangeEnumText(24, null); }
    @Test void postgisEnumText23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumText); }
    @Test void postgisEnumText24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumText); }

    private void exchangeEnumText(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_txt_ref");
        var db = new TestDatabase(work, postgres, "enum_txt_own");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).enumText(expected, db);
    }

    @Test void geoPackageEnumSingleLookup23() throws Exception { exchangeEnumSingleLookup(23, null); }
    @Test void geoPackageEnumSingleLookup24() throws Exception { exchangeEnumSingleLookup(24, null); }
    @Test void postgisEnumSingleLookup23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumSingleLookup); }
    @Test void postgisEnumSingleLookup24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumSingleLookup); }

    private void exchangeEnumSingleLookup(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_single_ref");
        var db = new TestDatabase(work, postgres, "enum_single_own");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).singleLookup(expected, db);
    }

    @Test void geoPackageEnumPerLookup23() throws Exception { exchangeEnumPerLookup(23, null); }
    @Test void geoPackageEnumPerLookup24() throws Exception { exchangeEnumPerLookup(24, null); }
    @Test void postgisEnumPerLookup23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumPerLookup); }
    @Test void postgisEnumPerLookup24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumPerLookup); }

    private void exchangeEnumPerLookup(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_per_ref");
        var db = new TestDatabase(work, postgres, "enum_per_own");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).perLookup(expected, db);
    }

    @Test void geoPackageEnumStoredIds23() throws Exception { exchangeEnumStoredIds(23, null); }
    @Test void geoPackageEnumStoredIds24() throws Exception { exchangeEnumStoredIds(24, null); }
    @Test void postgisEnumStoredIds23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumStoredIds); }
    @Test void postgisEnumStoredIds24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumStoredIds); }

    private void exchangeEnumStoredIds(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "enum_stored_id");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).storedIds(db);
    }

    @Test void geoPackageEnumIdSchema23() throws Exception { exchangeEnumIdSchema(23, null); }
    @Test void geoPackageEnumIdSchema24() throws Exception { exchangeEnumIdSchema(24, null); }
    @Test void postgisEnumIdSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumIdSchema); }
    @Test void postgisEnumIdSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumIdSchema); }

    private void exchangeEnumIdSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_id_ddl_ref");
        var db = new TestDatabase(work, postgres, "enum_id_ddl_own");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).idSchema(expected, db);
    }

    @Test void geoPackageEnumIdExtension23() throws Exception { exchangeEnumIdExtension(23, null); }
    @Test void geoPackageEnumIdExtension24() throws Exception { exchangeEnumIdExtension(24, null); }
    @Test void postgisEnumIdExtension23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumIdExtension); }
    @Test void postgisEnumIdExtension24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumIdExtension); }

    private void exchangeEnumIdExtension(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_id_ext_ref");
        var db = new TestDatabase(work, postgres, "enum_id_ext_own");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).idExtension(expected, db);
    }

    @Test void geoPackageEnumAppend23() throws Exception { exchangeEnumAppend(23, null); }
    @Test void geoPackageEnumAppend24() throws Exception { exchangeEnumAppend(24, null); }
    @Test void postgisEnumAppend23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumAppend); }
    @Test void postgisEnumAppend24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumAppend); }

    private void exchangeEnumAppend(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_append_ref");
        var db = new TestDatabase(work, postgres, "enum_append_own");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).appendEnums(expected, db);
    }

    @Test void geoPackageEnumNumericArrays23() throws Exception { exchangeEnumNumericArrays(23, null); }
    @Test void geoPackageEnumNumericArrays24() throws Exception { exchangeEnumNumericArrays(24, null); }
    @Test void postgisEnumNumericArrays23() throws Exception { exchangeWithPostgis(23, this::exchangeEnumNumericArrays); }
    @Test void postgisEnumNumericArrays24() throws Exception { exchangeWithPostgis(24, this::exchangeEnumNumericArrays); }

    private void exchangeEnumNumericArrays(int version, PostgreSQLContainer postgres) throws Exception {
        var expected = new TestDatabase(work, postgres, "enum_numeric_array_ref");
        var db = new TestDatabase(work, postgres, "enum_numeric_array_own");
        new EnumerationCases(work, new ReferenceTool(work, db.postgis()), version).numericArrays(expected, db);
    }

    @Test void geoPackageArrayEnumeration23() throws Exception { exchangeArrayEnumeration(23, null); }
    @Test void geoPackageArrayEnumeration24() throws Exception { exchangeArrayEnumeration(24, null); }
    @Test void postgisArrayEnumeration23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayEnumeration); }
    @Test void postgisArrayEnumeration24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayEnumeration); }

    private void exchangeArrayEnumeration(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "array_enumeration");
        new ArrayCases(work, new ReferenceTool(work, db.postgis()), version).enumeration(db);
    }

    @Test void geoPackageArrayBag23() throws Exception { exchangeArrayBag(23, null); }
    @Test void geoPackageArrayBag24() throws Exception { exchangeArrayBag(24, null); }
    @Test void postgisArrayBag23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayBag); }
    @Test void postgisArrayBag24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayBag); }

    private void exchangeArrayBag(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "array_bag");
        new ArrayCases(work, new ReferenceTool(work, db.postgis()), version).bag(db);
    }

    @Test void geoPackageArraySingle23() throws Exception { exchangeArraySingle(23, null); }
    @Test void geoPackageArraySingle24() throws Exception { exchangeArraySingle(24, null); }
    @Test void postgisArraySingle23() throws Exception { exchangeWithPostgis(23, this::exchangeArraySingle); }
    @Test void postgisArraySingle24() throws Exception { exchangeWithPostgis(24, this::exchangeArraySingle); }

    private void exchangeArraySingle(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "array_single");
        new ArrayCases(work, new ReferenceTool(work, db.postgis()), version).single(db);
    }

    @Test void geoPackageArrayScalars23() throws Exception { exchangeArrayScalars(23, null); }
    @Test void geoPackageArrayScalars24() throws Exception { exchangeArrayScalars(24, null); }
    @Test void postgisArrayScalars23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayScalars); }
    @Test void postgisArrayScalars24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayScalars); }

    private void exchangeArrayScalars(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "array_scalars");
        new ArrayCases(work, new ReferenceTool(work, db.postgis()), version).scalars(db);
    }

    @Test void geoPackageArraySchema23() throws Exception { exchangeArraySchema(23, null); }
    @Test void geoPackageArraySchema24() throws Exception { exchangeArraySchema(24, null); }
    @Test void postgisArraySchema23() throws Exception { exchangeWithPostgis(23, this::exchangeArraySchema); }
    @Test void postgisArraySchema24() throws Exception { exchangeWithPostgis(24, this::exchangeArraySchema); }

    private void exchangeArraySchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "array_schema_reference");
        var db = new TestDatabase(work, postgres, "array_schema_native");
        new ArrayCases(work, new ReferenceTool(work, db.postgis()), version).createSchema(baseline, db);
    }

    @Test void geoPackageArraySmart223() throws Exception { exchangeArraySmart2(23, null); }
    @Test void geoPackageArraySmart224() throws Exception { exchangeArraySmart2(24, null); }
    @Test void postgisArraySmart223() throws Exception { exchangeWithPostgis(23, this::exchangeArraySmart2); }
    @Test void postgisArraySmart224() throws Exception { exchangeWithPostgis(24, this::exchangeArraySmart2); }

    private void exchangeArraySmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "array_smart_reference");
        var db = new TestDatabase(work, postgres, "array_smart_native");
        new ArrayCases(work, new ReferenceTool(work, db.postgis()), version).smart2(baseline, db);
    }

    @Test void geoPackageArrayReferences23() throws Exception { exchangeArrayReferences(23, null); }
    @Test void geoPackageArrayReferences24() throws Exception { exchangeArrayReferences(24, null); }
    @Test void postgisArrayReferences23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayReferences); }
    @Test void postgisArrayReferences24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayReferences); }

    private void exchangeArrayReferences(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "array_refs_reference");
        var db = new TestDatabase(work, postgres, "array_refs_native");
        new ArrayCases(work, new ReferenceTool(work, db.postgis()), version).references(baseline, db);
    }

    @Test void geoPackageExpandSingle23() throws Exception { exchangeExpandSingle(23, null); }
    @Test void geoPackageExpandSingle24() throws Exception { exchangeExpandSingle(24, null); }
    @Test void postgisExpandSingle23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandSingle); }
    @Test void postgisExpandSingle24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandSingle); }

    private void exchangeExpandSingle(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "expand_single");
        new StructureMappingCases(work, new ReferenceTool(work, db.postgis()), version).single(db);
    }

    @Test void geoPackageExpandList23() throws Exception { exchangeExpandList(23, null); }
    @Test void geoPackageExpandList24() throws Exception { exchangeExpandList(24, null); }
    @Test void postgisExpandList23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandList); }
    @Test void postgisExpandList24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandList); }

    private void exchangeExpandList(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "expand_list");
        new StructureMappingCases(work, new ReferenceTool(work, db.postgis()), version).list(db);
    }

    @Test void geoPackageExpandBag23() throws Exception { exchangeExpandBag(23, null); }
    @Test void geoPackageExpandBag24() throws Exception { exchangeExpandBag(24, null); }
    @Test void postgisExpandBag23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandBag); }
    @Test void postgisExpandBag24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandBag); }

    private void exchangeExpandBag(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "expand_bag");
        new StructureMappingCases(work, new ReferenceTool(work, db.postgis()), version).bag(db);
    }

    @Test void geoPackageExpandNested23() throws Exception { exchangeExpandNested(23, null); }
    @Test void geoPackageExpandNested24() throws Exception { exchangeExpandNested(24, null); }
    @Test void postgisExpandNested23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandNested); }
    @Test void postgisExpandNested24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandNested); }

    private void exchangeExpandNested(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "expand_nested");
        new StructureMappingCases(work, new ReferenceTool(work, db.postgis()), version).nested(db);
    }

    @Test void geoPackageExpandSchema23() throws Exception { exchangeExpandSchema(23, null); }
    @Test void geoPackageExpandSchema24() throws Exception { exchangeExpandSchema(24, null); }
    @Test void postgisExpandSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandSchema); }
    @Test void postgisExpandSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandSchema); }

    private void exchangeExpandSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "expand_baseline");
        var own = new TestDatabase(work, postgres, "expand_own");
        new StructureMappingCases(work, new ReferenceTool(work, own.postgis()), version).schema(baseline, own);
    }

    @Test void geoPackageExpandSmart223() throws Exception { exchangeExpandSmart2(23, null); }
    @Test void geoPackageExpandSmart224() throws Exception { exchangeExpandSmart2(24, null); }
    @Test void postgisExpandSmart223() throws Exception { exchangeWithPostgis(23, this::exchangeExpandSmart2); }
    @Test void postgisExpandSmart224() throws Exception { exchangeWithPostgis(24, this::exchangeExpandSmart2); }

    private void exchangeExpandSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "expand_smart_baseline");
        var own = new TestDatabase(work, postgres, "expand_smart_own");
        new StructureMappingCases(work, new ReferenceTool(work, own.postgis()), version).smart2(baseline, own);
    }

    @Test void geoPackageJsonStructure23() throws Exception { exchangeJsonStructure(23, null); }
    @Test void geoPackageJsonStructure24() throws Exception { exchangeJsonStructure(24, null); }
    @Test void postgisJsonStructure23() throws Exception { exchangeWithPostgis(23, this::exchangeJsonStructure); }
    @Test void postgisJsonStructure24() throws Exception { exchangeWithPostgis(24, this::exchangeJsonStructure); }

    private void exchangeJsonStructure(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "json_structure");
        new StructureMappingCases(work, new ReferenceTool(work, db.postgis()), version).jsonSingle(db);
    }

    @Test void geoPackageJsonCollections23() throws Exception { exchangeJsonCollections(23, null); }
    @Test void geoPackageJsonCollections24() throws Exception { exchangeJsonCollections(24, null); }
    @Test void postgisJsonCollections23() throws Exception { exchangeWithPostgis(23, this::exchangeJsonCollections); }
    @Test void postgisJsonCollections24() throws Exception { exchangeWithPostgis(24, this::exchangeJsonCollections); }

    private void exchangeJsonCollections(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "json_collections");
        new StructureMappingCases(work, new ReferenceTool(work, db.postgis()), version).jsonCollections(db);
    }

    @Test void geoPackageJsonSchema23() throws Exception { exchangeJsonSchema(23, null); }
    @Test void geoPackageJsonSchema24() throws Exception { exchangeJsonSchema(24, null); }
    @Test void postgisJsonSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeJsonSchema); }
    @Test void postgisJsonSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeJsonSchema); }

    private void exchangeJsonSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "json_schema_baseline");
        var own = new TestDatabase(work, postgres, "json_schema_own");
        new StructureMappingCases(work, new ReferenceTool(work, own.postgis()), version).jsonSchema(baseline, own);
    }

    @Test void geoPackageChbasePoints23() throws Exception { exchangeChbasePoints(23, null); }
    @Test void geoPackageChbasePoints24() throws Exception { exchangeChbasePoints(24, null); }
    @Test void geoPackageStrokePolyline23() throws Exception { exchangeStrokePolyline(23, null); }
    @Test void geoPackageStrokePolyline24() throws Exception { exchangeStrokePolyline(24, null); }
    @Test void postgisStrokePolyline23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokePolyline); }
    @Test void postgisStrokePolyline24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokePolyline); }

    private void exchangeStrokePolyline(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "stroke_polyline");
        new StrokeCases(work, new ReferenceTool(work, db.postgis()), version).polyline(db);
    }

    @Test void geoPackageStrokeArcs23() throws Exception { exchangeStrokeArcs(23, null); }
    @Test void geoPackageStrokeArcs24() throws Exception { exchangeStrokeArcs(24, null); }
    @Test void postgisStrokeArcs23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeArcs); }
    @Test void postgisStrokeArcs24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeArcs); }

    private void exchangeStrokeArcs(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "stroke_arcs");
        new StrokeCases(work, new ReferenceTool(work, db.postgis()), version).arcs(db);
    }

    @Test void geoPackageStrokeSurface23() throws Exception { exchangeStrokeSurface(23, null); }
    @Test void geoPackageStrokeSurface24() throws Exception { exchangeStrokeSurface(24, null); }
    @Test void postgisStrokeSurface23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeSurface); }
    @Test void postgisStrokeSurface24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeSurface); }

    private void exchangeStrokeSurface(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "stroke_surface");
        new StrokeCases(work, new ReferenceTool(work, db.postgis()), version).surface(db);
    }

    @Test void geoPackageStrokeArea23() throws Exception { exchangeStrokeArea(23, null); }
    @Test void geoPackageStrokeArea24() throws Exception { exchangeStrokeArea(24, null); }
    @Test void postgisStrokeArea23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeArea); }
    @Test void postgisStrokeArea24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeArea); }

    private void exchangeStrokeArea(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "stroke_area");
        new StrokeCases(work, new ReferenceTool(work, db.postgis()), version).area(db);
    }

    @Test void geoPackageStrokeMultiPolyline23() throws Exception { exchangeStrokeMultiPolyline(23, null); }
    @Test void geoPackageStrokeMultiPolyline24() throws Exception { exchangeStrokeMultiPolyline(24, null); }
    @Test void postgisStrokeMultiPolyline23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeMultiPolyline); }
    @Test void postgisStrokeMultiPolyline24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeMultiPolyline); }

    private void exchangeStrokeMultiPolyline(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "stroke_multi_line");
        new StrokeCases(work, new ReferenceTool(work, db.postgis()), version).multiPolyline(db);
    }

    @Test void geoPackageStrokeMultiSurface23() throws Exception { exchangeStrokeMultiSurface(23, null); }
    @Test void geoPackageStrokeMultiSurface24() throws Exception { exchangeStrokeMultiSurface(24, null); }
    @Test void postgisStrokeMultiSurface23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeMultiSurface); }
    @Test void postgisStrokeMultiSurface24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeMultiSurface); }

    private void exchangeStrokeMultiSurface(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "stroke_multi_surface");
        new StrokeCases(work, new ReferenceTool(work, db.postgis()), version).multiSurface(db);
    }

    @Test void geoPackageStrokeSchema23() throws Exception { exchangeStrokeSchema(23, null); }
    @Test void geoPackageStrokeSchema24() throws Exception { exchangeStrokeSchema(24, null); }
    @Test void postgisStrokeSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeSchema); }
    @Test void postgisStrokeSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeSchema); }

    private void exchangeStrokeSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "stroke_baseline");
        var own = new TestDatabase(work, postgres, "stroke_own");
        new StrokeCases(work, new ReferenceTool(work, own.postgis()), version).schema(baseline, own);
    }

    @Test void geoPackageStrokeSmart223() throws Exception { exchangeStrokeSmart2(23, null); }
    @Test void geoPackageStrokeSmart224() throws Exception { exchangeStrokeSmart2(24, null); }
    @Test void postgisStrokeSmart223() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeSmart2); }
    @Test void postgisStrokeSmart224() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeSmart2); }

    private void exchangeStrokeSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "stroke_smart_baseline");
        var own = new TestDatabase(work, postgres, "stroke_smart_own");
        new StrokeCases(work, new ReferenceTool(work, own.postgis()), version).smart2(baseline, own);
    }

    @Test void postgisChbasePoints23() throws Exception { exchangeWithPostgis(23, this::exchangeChbasePoints); }
    @Test void postgisChbasePoints24() throws Exception { exchangeWithPostgis(24, this::exchangeChbasePoints); }
    @Test void geoPackageChbaseLines23() throws Exception { exchangeChbaseLines(23, null); }
    @Test void geoPackageChbaseLines24() throws Exception { exchangeChbaseLines(24, null); }
    @Test void postgisChbaseLines23() throws Exception { exchangeWithPostgis(23, this::exchangeChbaseLines); }
    @Test void postgisChbaseLines24() throws Exception { exchangeWithPostgis(24, this::exchangeChbaseLines); }
    @Test void geoPackageChbaseSurfaces23() throws Exception { exchangeChbaseSurfaces(23, null); }
    @Test void geoPackageChbaseSurfaces24() throws Exception { exchangeChbaseSurfaces(24, null); }
    @Test void postgisChbaseSurfaces23() throws Exception { exchangeWithPostgis(23, this::exchangeChbaseSurfaces); }
    @Test void postgisChbaseSurfaces24() throws Exception { exchangeWithPostgis(24, this::exchangeChbaseSurfaces); }
    @Test void geoPackageChbaseGeometrySchema23() throws Exception { exchangeChbaseGeometrySchema(23, null); }
    @Test void geoPackageChbaseGeometrySchema24() throws Exception { exchangeChbaseGeometrySchema(24, null); }
    @Test void postgisChbaseGeometrySchema23() throws Exception { exchangeWithPostgis(23, this::exchangeChbaseGeometrySchema); }
    @Test void postgisChbaseGeometrySchema24() throws Exception { exchangeWithPostgis(24, this::exchangeChbaseGeometrySchema); }
    @Test void geoPackageChbaseSmart223() throws Exception { exchangeChbaseSmart2(23, null); }
    @Test void geoPackageChbaseSmart224() throws Exception { exchangeChbaseSmart2(24, null); }
    @Test void postgisChbaseSmart223() throws Exception { exchangeWithPostgis(23, this::exchangeChbaseSmart2); }
    @Test void postgisChbaseSmart224() throws Exception { exchangeWithPostgis(24, this::exchangeChbaseSmart2); }

    private void exchangeChbasePoints(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "chbase_points");
        new ChbaseGeometryCases(work, new ReferenceTool(work, db.postgis()), version).points(db);
    }

    private void exchangeChbaseLines(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "chbase_lines");
        new ChbaseGeometryCases(work, new ReferenceTool(work, db.postgis()), version).lines(db);
    }

    private void exchangeChbaseSurfaces(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "chbase_surfaces");
        new ChbaseGeometryCases(work, new ReferenceTool(work, db.postgis()), version).surfaces(db);
    }

    private void exchangeChbaseGeometrySchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "chbase_geometry_ref");
        new ChbaseGeometryCases(work, new ReferenceTool(work, baseline.postgis()), version)
                .schema(baseline, new TestDatabase(work, postgres, "chbase_geometry_own"));
    }

    private void exchangeChbaseSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "chbase_smart2_ref");
        new ChbaseGeometryCases(work, new ReferenceTool(work, baseline.postgis()), version)
                .smart2(baseline, new TestDatabase(work, postgres, "chbase_smart2_own"));
    }

    @Test void geoPackageChbaseMultilingual23() throws Exception { exchangeChbaseMultilingual(23, null); }
    @Test void geoPackageChbaseMultilingual24() throws Exception { exchangeChbaseMultilingual(24, null); }
    @Test void postgisChbaseMultilingual23() throws Exception { exchangeWithPostgis(23, this::exchangeChbaseMultilingual); }
    @Test void postgisChbaseMultilingual24() throws Exception { exchangeWithPostgis(24, this::exchangeChbaseMultilingual); }
    @Test void geoPackageChbaseLocalised23() throws Exception { exchangeChbaseLocalised(23, null); }
    @Test void geoPackageChbaseLocalised24() throws Exception { exchangeChbaseLocalised(24, null); }
    @Test void postgisChbaseLocalised23() throws Exception { exchangeWithPostgis(23, this::exchangeChbaseLocalised); }
    @Test void postgisChbaseLocalised24() throws Exception { exchangeWithPostgis(24, this::exchangeChbaseLocalised); }
    @Test void geoPackageChbaseTextSchema23() throws Exception { exchangeChbaseTextSchema(23, null); }
    @Test void geoPackageChbaseTextSchema24() throws Exception { exchangeChbaseTextSchema(24, null); }
    @Test void postgisChbaseTextSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeChbaseTextSchema); }
    @Test void postgisChbaseTextSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeChbaseTextSchema); }

    private void exchangeChbaseMultilingual(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "chbase_multilingual");
        new ChbaseTextCases(work, new ReferenceTool(work, db.postgis()), version).multilingual(db);
    }

    private void exchangeChbaseLocalised(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "chbase_localised");
        new ChbaseTextCases(work, new ReferenceTool(work, db.postgis()), version).localised(db);
    }

    private void exchangeChbaseTextSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "chbase_text_ref");
        new ChbaseTextCases(work, new ReferenceTool(work, baseline.postgis()), version)
                .schema(baseline, new TestDatabase(work, postgres, "chbase_text_own"));
    }

    @Test void geoPackageCatalogueReference23() throws Exception { exchangeCatalogueReference(23, null); }
    @Test void geoPackageCatalogueReference24() throws Exception { exchangeCatalogueReference(24, null); }
    @Test void postgisCatalogueReference23() throws Exception { exchangeWithPostgis(23, this::exchangeCatalogueReference); }
    @Test void postgisCatalogueReference24() throws Exception { exchangeWithPostgis(24, this::exchangeCatalogueReference); }
    @Test void geoPackageCatalogueSmart223() throws Exception { exchangeCatalogueSmart2(23, null); }
    @Test void geoPackageCatalogueSmart224() throws Exception { exchangeCatalogueSmart2(24, null); }
    @Test void postgisCatalogueSmart223() throws Exception { exchangeWithPostgis(23, this::exchangeCatalogueSmart2); }
    @Test void postgisCatalogueSmart224() throws Exception { exchangeWithPostgis(24, this::exchangeCatalogueSmart2); }
    @Test void geoPackageCatalogueSchema23() throws Exception { exchangeCatalogueSchema(23, null); }
    @Test void geoPackageCatalogueSchema24() throws Exception { exchangeCatalogueSchema(24, null); }
    @Test void postgisCatalogueSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeCatalogueSchema); }
    @Test void postgisCatalogueSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeCatalogueSchema); }

    private void exchangeCatalogueReference(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "catalogue_reference");
        var cases = new CatalogueCases(work, new ReferenceTool(work, db.postgis()), version);
        cases.singleTarget(db);
        if (version == 24) cases.directReference(new TestDatabase(work, postgres, "catalogue_direct"));
    }

    private void exchangeCatalogueSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "catalogue_smart2");
        new CatalogueCases(work, new ReferenceTool(work, db.postgis()), version).smart2(db);
    }

    private void exchangeCatalogueSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(work, postgres, "catalogue_reference_newclass");
        new CatalogueCases(work, new ReferenceTool(work, baseline.postgis()), version).schema(baseline,
                new TestDatabase(work, postgres, "catalogue_native_newclass"), new TestDatabase(work, postgres, "catalogue_reference_smart2"),
                new TestDatabase(work, postgres, "catalogue_native_smart2"));
    }

    @Test void geoPackageSmart1Scalars23() throws Exception { exchangeSmart1Scalars(23, null); }
    @Test void geoPackageSmart1Scalars24() throws Exception { exchangeSmart1Scalars(24, null); }
    @Test void postgisSmart1Scalars23() throws Exception { exchangeWithPostgis(23, this::exchangeSmart1Scalars); }
    @Test void postgisSmart1Scalars24() throws Exception { exchangeWithPostgis(24, this::exchangeSmart1Scalars); }
    @Test void geoPackageSmart1References23() throws Exception { exchangeSmart1References(23, null); }
    @Test void geoPackageSmart1References24() throws Exception { exchangeSmart1References(24, null); }
    @Test void postgisSmart1References23() throws Exception { exchangeWithPostgis(23, this::exchangeSmart1References); }
    @Test void postgisSmart1References24() throws Exception { exchangeWithPostgis(24, this::exchangeSmart1References); }
    @Test void geoPackageSmart2Scalars23() throws Exception { exchangeSmart2Scalars(23, null); }
    @Test void geoPackageSmart2Scalars24() throws Exception { exchangeSmart2Scalars(24, null); }
    @Test void postgisSmart2Scalars23() throws Exception { exchangeWithPostgis(23, this::exchangeSmart2Scalars); }
    @Test void postgisSmart2Scalars24() throws Exception { exchangeWithPostgis(24, this::exchangeSmart2Scalars); }
    @Test void geoPackageSmart2References23() throws Exception { exchangeSmart2References(23, null); }
    @Test void geoPackageSmart2References24() throws Exception { exchangeSmart2References(24, null); }
    @Test void postgisSmart2References23() throws Exception { exchangeWithPostgis(23, this::exchangeSmart2References); }
    @Test void postgisSmart2References24() throws Exception { exchangeWithPostgis(24, this::exchangeSmart2References); }
    @Test void geoPackageSmartStructures23() throws Exception { exchangeSmartStructures(23, null); }
    @Test void geoPackageSmartStructures24() throws Exception { exchangeSmartStructures(24, null); }
    @Test void postgisSmartStructures23() throws Exception { exchangeWithPostgis(23, this::exchangeSmartStructures); }
    @Test void postgisSmartStructures24() throws Exception { exchangeWithPostgis(24, this::exchangeSmartStructures); }
    @Test void geoPackageSmartGeometries23() throws Exception { exchangeSmartGeometries(23, null); }
    @Test void geoPackageSmartGeometries24() throws Exception { exchangeSmartGeometries(24, null); }
    @Test void postgisSmartGeometries23() throws Exception { exchangeWithPostgis(23, this::exchangeSmartGeometries); }
    @Test void postgisSmartGeometries24() throws Exception { exchangeWithPostgis(24, this::exchangeSmartGeometries); }
    @Test void geoPackageSmartMutations23() throws Exception { exchangeSmartMutations(23, null); }
    @Test void geoPackageSmartMutations24() throws Exception { exchangeSmartMutations(24, null); }
    @Test void postgisSmartMutations23() throws Exception { exchangeWithPostgis(23, this::exchangeSmartMutations); }
    @Test void postgisSmartMutations24() throws Exception { exchangeWithPostgis(24, this::exchangeSmartMutations); }
    @Test void geoPackageSmart1Schema23() throws Exception { exchangeSmart1Schema(23, null); }
    @Test void geoPackageSmart1Schema24() throws Exception { exchangeSmart1Schema(24, null); }
    @Test void postgisSmart1Schema23() throws Exception { exchangeWithPostgis(23, this::exchangeSmart1Schema); }
    @Test void postgisSmart1Schema24() throws Exception { exchangeWithPostgis(24, this::exchangeSmart1Schema); }
    @Test void geoPackageSmart2Schema23() throws Exception { exchangeSmart2Schema(23, null); }
    @Test void geoPackageSmart2Schema24() throws Exception { exchangeSmart2Schema(24, null); }
    @Test void postgisSmart2Schema23() throws Exception { exchangeWithPostgis(23, this::exchangeSmart2Schema); }
    @Test void postgisSmart2Schema24() throws Exception { exchangeWithPostgis(24, this::exchangeSmart2Schema); }

    @Test void geoPackageIlisMeta16Schema() throws Exception { checkIlisMeta16Schema(24, null); }
    @Test void postgisIlisMeta16Schema() throws Exception { exchangeWithPostgis(24, this::checkIlisMeta16Schema); }
    @Test void geoPackageTextOid24() throws Exception { exchangeTextOid(24, null); }
    @Test void postgisTextOid24() throws Exception { exchangeWithPostgis(24, this::exchangeTextOid); }
    @Test void geoPackageIdentityColumns24() throws Exception { exchangeIdentityColumns(24, null); }
    @Test void postgisIdentityColumns24() throws Exception { exchangeWithPostgis(24, this::exchangeIdentityColumns); }
    @Test void geoPackageIdentityColumns23() throws Exception { exchangeIdentityColumns(23, null); }
    @Test void postgisIdentityColumns23() throws Exception { exchangeWithPostgis(23, this::exchangeIdentityColumns); }

    private void exchangeIdentityColumns(int version, PostgreSQLContainer postgres) throws Exception {
        new IdentityColumnCases(work, new ReferenceTool(work, postgres != null), version)
                .exercise(new TestDatabase(work, postgres, "identity"));
    }

    private void checkIlisMeta16Schema(int version, PostgreSQLContainer postgres) throws Exception {
        var source = Path.of("../testdata/conformance/metamodel/IlisMeta16.ili");
        var ili = Files.copy(source, work.resolve(source.getFileName()));
        var reference = new ReferenceTool(work, postgres != null);
        for (String profile : List.of("newclass", "smart1", "smart2")) {
            boolean newClass = profile.equals("newclass");
            var referenceDb = new TestDatabase(work, postgres, "meta_ref_" + profile);
            var args = new ArrayList<>(List.of("--schemaimport", "--createBasketCol", "--noSmartMapping"));
            if (!newClass) args.add("--" + profile + "Inheritance");
            args.add(ili.toString());
            reference.run(referenceDb, work.toString(), args.toArray(String[]::new));
            try (var sql = referenceDb.connect()) {
                var inheritance = "select setting from " + referenceDb.table("t_ili2db_trafo")
                        + " where tag = 'ch.ehi.ili2db.inheritance' and iliname = 'IlisMeta16.ModelData.";
                assertEquals("embedded", scalar(sql, inheritance + "BasketOID'"), profile);
                assertEquals(newClass ? "newClass" : "subClass", scalar(sql, inheritance + "Factor'"), profile);
                assertEquals(profile.equals("smart2") ? "newAndSubClass" : "newClass",
                        scalar(sql, inheritance + "Model'"), profile);

                String oidColumn = newClass ? "oid" : "oid_texttype";
                assertNull(scalar(sql, "select t_basket, " + oidColumn
                        + " from " + referenceDb.table("dataunit") + " where 1 = 0"), profile);
            }

            var nativeDb = new TestDatabase(work, postgres, "meta_native_" + profile);
            var nativeArgs = newClass ? new String[0] : new String[]{"--inheritance", profile};
            assertCliFailure(nativeDb, "schemaimport", "IlisMeta16", null, ili,
                    "IlisMeta16.ModelData requires --create-basket-col", nativeArgs);
            var result = schemaWithIdentityColumns(nativeDb, "IlisMeta16", ili, CancellationToken.none(), nativeArgs);
            assertEquals(1, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains(
                    "Unsupported ili2db mapping (composition in structure inheritance at "
                            + "IlisMeta16.ModelData.UnaryExpr.SubExpression)"), result::diagnostics);
            if (nativeDb.postgis()) {
                try (var sql = nativeDb.connect()) {
                    assertEquals("0", scalar(sql, "select count(*) from information_schema.schemata where schema_name = '"
                            + nativeDb.schema() + "'"), profile);
                }
            } else {
                assertFalse(Files.exists(nativeDb.file), profile);
            }
        }
    }

    private void exchangeTextOid(int version, PostgreSQLContainer postgres) throws Exception {
        String model = "TextOid24";
        var ili = work.resolve(model + ".ili");
        var xtf = work.resolve(model + ".xtf");
        for (var path : List.of(ili, xtf)) {
            try (var resource = DbInteropTest.class.getResourceAsStream("/" + path.getFileName())) {
                Files.copy(resource, path);
            }
        }
        var reference = new ReferenceTool(work, postgres != null);
        for (String profile : List.of("newclass", "smart1", "smart2")) {
            boolean newClass = profile.equals("newclass");
            var legacyDb = new TestDatabase(work, postgres, "textoid_ref_" + profile);
            var args = new ArrayList<>(List.of("--schemaimport", "--createBasketCol", "--createTidCol", "--sqlEnableNull",
                    "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--noSmartMapping"));
            if (!newClass) args.add("--" + profile + "Inheritance");
            args.add(ili.toString());
            reference.run(legacyDb, work.toString(), args.toArray(String[]::new));

            var ownDb = new TestDatabase(work, postgres, "textoid_own_" + profile);
            var nativeArgs = newClass ? new String[0] : new String[]{"--inheritance", profile};
            var result = schemaWithIdentityColumns(ownDb, model, ili, CancellationToken.none(), nativeArgs);
            assertEquals(0, result.code(), result::diagnostics);
            try (var legacy = legacyDb.connect(); var own = ownDb.connect()) {
                DbSchemaCatalog.read(legacy, legacyDb.postgis(), legacyDb.schema())
                        .assertSameAs(DbSchemaCatalog.read(own, ownDb.postgis(), ownDb.schema()));
            }
            for (var db : List.of(legacyDb, ownDb)) {
                var output = work.resolve(db.schema() + ".xtf");
                assertCliSuccess(db, "import", model, "text-oid", xtf);
                assertCliSuccess(db, "export", model, "text-oid", output);
                assertTextOidTransfer(output, version);
            }
        }
    }

    private void exchangeSmart1Scalars(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart1_scalars");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version)
                .smart1Scalars(db, new TestDatabase(work, postgres, "smart1_defaults"));
    }

    private void exchangeSmart1References(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart1_references");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version).smart1References(db);
    }

    private void exchangeSmart2Scalars(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart2_scalars");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version).smart2Scalars(db);
    }

    private void exchangeSmart2References(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart2_references");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version).smart2References(db);
    }

    private void exchangeSmartStructures(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart1_structures");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version)
                .structures(db, new TestDatabase(work, postgres, "smart2_structures"));
    }

    private void exchangeSmartGeometries(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart1_geometries");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version)
                .geometries(db, new TestDatabase(work, postgres, "smart2_geometries"));
    }

    private void exchangeSmartMutations(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart1_mutations");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version)
                .mutations(db, new TestDatabase(work, postgres, "smart2_mutations"));
    }

    private void exchangeSmart1Schema(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart1_schema_reference");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version)
                .smart1Schema(db, new TestDatabase(work, postgres, "smart1_schema_native"));
    }

    private void exchangeSmart2Schema(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "smart2_schema_reference");
        new SmartCases(work, new ReferenceTool(work, db.postgis()), version)
                .smart2Schema(db, new TestDatabase(work, postgres, "smart2_schema_native"));
    }

    @Test void geoPackageTranslationSources23() throws Exception { exchangeTranslationSources(23, null); }
    @Test void geoPackageTranslationSources24() throws Exception { exchangeTranslationSources(24, null); }
    @Test void postgisTranslationSources23() throws Exception { exchangeWithPostgis(23, this::exchangeTranslationSources); }
    @Test void postgisTranslationSources24() throws Exception { exchangeWithPostgis(24, this::exchangeTranslationSources); }
    @Test void geoPackageTranslationScalars23() throws Exception { exchangeTranslationScalars(23, null); }
    @Test void geoPackageTranslationScalars24() throws Exception { exchangeTranslationScalars(24, null); }
    @Test void postgisTranslationScalars23() throws Exception { exchangeWithPostgis(23, this::exchangeTranslationScalars); }
    @Test void postgisTranslationScalars24() throws Exception { exchangeWithPostgis(24, this::exchangeTranslationScalars); }
    @Test void geoPackageTranslationNested23() throws Exception { exchangeTranslationNested(23, null); }
    @Test void geoPackageTranslationNested24() throws Exception { exchangeTranslationNested(24, null); }
    @Test void postgisTranslationNested23() throws Exception { exchangeWithPostgis(23, this::exchangeTranslationNested); }
    @Test void postgisTranslationNested24() throws Exception { exchangeWithPostgis(24, this::exchangeTranslationNested); }
    @Test void geoPackageTranslationSchema23() throws Exception { exchangeTranslationSchema(23, null); }
    @Test void geoPackageTranslationSchema24() throws Exception { exchangeTranslationSchema(24, null); }
    @Test void postgisTranslationSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeTranslationSchema); }
    @Test void postgisTranslationSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeTranslationSchema); }
    @Test void geoPackageTranslationExport23() throws Exception { exchangeTranslationExport(23, null); }
    @Test void geoPackageTranslationExport24() throws Exception { exchangeTranslationExport(24, null); }
    @Test void postgisTranslationExport23() throws Exception { exchangeWithPostgis(23, this::exchangeTranslationExport); }
    @Test void postgisTranslationExport24() throws Exception { exchangeWithPostgis(24, this::exchangeTranslationExport); }
    @Test void geoPackageTopicInherited23() throws Exception { exchangeTopicInherited(23, null); }
    @Test void geoPackageTopicInherited24() throws Exception { exchangeTopicInherited(24, null); }
    @Test void postgisTopicInherited23() throws Exception { exchangeWithPostgis(23, this::exchangeTopicInherited); }
    @Test void postgisTopicInherited24() throws Exception { exchangeWithPostgis(24, this::exchangeTopicInherited); }
    @Test void geoPackageTopicFragments23() throws Exception { exchangeTopicFragments(23, null); }
    @Test void geoPackageTopicFragments24() throws Exception { exchangeTopicFragments(24, null); }
    @Test void postgisTopicFragments23() throws Exception { exchangeWithPostgis(23, this::exchangeTopicFragments); }
    @Test void postgisTopicFragments24() throws Exception { exchangeWithPostgis(24, this::exchangeTopicFragments); }
    @Test void geoPackageTopicRefinement23() throws Exception { exchangeTopicRefinement(23, null); }
    @Test void geoPackageTopicRefinement24() throws Exception { exchangeTopicRefinement(24, null); }
    @Test void postgisTopicRefinement23() throws Exception { exchangeWithPostgis(23, this::exchangeTopicRefinement); }
    @Test void postgisTopicRefinement24() throws Exception { exchangeWithPostgis(24, this::exchangeTopicRefinement); }
    @Test void geoPackageTopicReferences23() throws Exception { exchangeTopicReferences(23, null); }
    @Test void geoPackageTopicReferences24() throws Exception { exchangeTopicReferences(24, null); }
    @Test void postgisTopicReferences23() throws Exception { exchangeWithPostgis(23, this::exchangeTopicReferences); }
    @Test void postgisTopicReferences24() throws Exception { exchangeWithPostgis(24, this::exchangeTopicReferences); }
    @Test void geoPackageTopicMutations23() throws Exception { exchangeTopicMutations(23, null); }
    @Test void geoPackageTopicMutations24() throws Exception { exchangeTopicMutations(24, null); }
    @Test void postgisTopicMutations23() throws Exception { exchangeWithPostgis(23, this::exchangeTopicMutations); }
    @Test void postgisTopicMutations24() throws Exception { exchangeWithPostgis(24, this::exchangeTopicMutations); }

    @Test void geoPackageSharedSource23() throws Exception { exchangeSharedSource(23, null); }
    @Test void geoPackageSharedSource24() throws Exception { exchangeSharedSource(24, null); }
    @Test void postgisSharedSource23() throws Exception { exchangeWithPostgis(23, this::exchangeSharedSource); }
    @Test void postgisSharedSource24() throws Exception { exchangeWithPostgis(24, this::exchangeSharedSource); }
    @Test void geoPackageStoredImports23() throws Exception { exchangeStoredImports(23, null); }
    @Test void geoPackageStoredImports24() throws Exception { exchangeStoredImports(24, null); }
    @Test void postgisStoredImports23() throws Exception { exchangeWithPostgis(23, this::exchangeStoredImports); }
    @Test void postgisStoredImports24() throws Exception { exchangeWithPostgis(24, this::exchangeStoredImports); }
    @Test void geoPackageLocalImports23() throws Exception { exchangeLocalImports(23, null); }
    @Test void geoPackageLocalImports24() throws Exception { exchangeLocalImports(24, null); }
    @Test void postgisLocalImports23() throws Exception { exchangeWithPostgis(23, this::exchangeLocalImports); }
    @Test void postgisLocalImports24() throws Exception { exchangeWithPostgis(24, this::exchangeLocalImports); }
    @Test void geoPackageImportedStruct23() throws Exception { exchangeImportedStruct(23, null); }
    @Test void geoPackageImportedStruct24() throws Exception { exchangeImportedStruct(24, null); }
    @Test void postgisImportedStruct23() throws Exception { exchangeWithPostgis(23, this::exchangeImportedStruct); }
    @Test void postgisImportedStruct24() throws Exception { exchangeWithPostgis(24, this::exchangeImportedStruct); }
    @Test void geoPackageImportedTopic23() throws Exception { exchangeImportedTopic(23, null); }
    @Test void geoPackageImportedTopic24() throws Exception { exchangeImportedTopic(24, null); }
    @Test void postgisImportedTopic23() throws Exception { exchangeWithPostgis(23, this::exchangeImportedTopic); }
    @Test void postgisImportedTopic24() throws Exception { exchangeWithPostgis(24, this::exchangeImportedTopic); }
    @Test void geoPackageJointSchema23() throws Exception { exchangeJointSchema(23, null); }
    @Test void geoPackageJointSchema24() throws Exception { exchangeJointSchema(24, null); }
    @Test void postgisJointSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeJointSchema); }
    @Test void postgisJointSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeJointSchema); }
    @Test void geoPackageMixedExport23() throws Exception { exchangeMixedExport(23, null); }
    @Test void geoPackageMixedExport24() throws Exception { exchangeMixedExport(24, null); }
    @Test void postgisMixedExport23() throws Exception { exchangeWithPostgis(23, this::exchangeMixedExport); }
    @Test void postgisMixedExport24() throws Exception { exchangeWithPostgis(24, this::exchangeMixedExport); }
    @Test void geoPackageMixedImport23() throws Exception { exchangeMixedImport(23, null); }
    @Test void geoPackageMixedImport24() throws Exception { exchangeMixedImport(24, null); }
    @Test void postgisMixedImport23() throws Exception { exchangeWithPostgis(23, this::exchangeMixedImport); }
    @Test void postgisMixedImport24() throws Exception { exchangeWithPostgis(24, this::exchangeMixedImport); }
    @Test void geoPackageMixedMutation23() throws Exception { exchangeMixedMutation(23, null); }
    @Test void geoPackageMixedMutation24() throws Exception { exchangeMixedMutation(24, null); }
    @Test void postgisMixedMutation23() throws Exception { exchangeWithPostgis(23, this::exchangeMixedMutation); }
    @Test void postgisMixedMutation24() throws Exception { exchangeWithPostgis(24, this::exchangeMixedMutation); }
    @Test void geoPackageAppendModels23() throws Exception { exchangeAppendModels(23, null); }
    @Test void geoPackageAppendModels24() throws Exception { exchangeAppendModels(24, null); }
    @Test void postgisAppendModels23() throws Exception { exchangeWithPostgis(23, this::exchangeAppendModels); }
    @Test void postgisAppendModels24() throws Exception { exchangeWithPostgis(24, this::exchangeAppendModels); }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception {
        exchange(24, null);
    }
    @Test void postgis23() throws Exception { exchangeWithPostgis(23, this::exchange); }
    @Test void postgis24() throws Exception { exchangeWithPostgis(24, this::exchange); }


    @Test void geoPackageXyz23() throws Exception { exchangeXyz(23, null); }
    @Test void geoPackageXyz24() throws Exception { exchangeXyz(24, null); }
    @Test void postgisXyz23() throws Exception { exchangeWithPostgis(23, this::exchangeXyz); }
    @Test void postgisXyz24() throws Exception { exchangeWithPostgis(24, this::exchangeXyz); }

    private void exchangeXyz(int version, PostgreSQLContainer postgres) throws Exception {
        new XyzGeometryCases(work, version, new ReferenceTool(work, postgres != null))
                .exchange(new TestDatabase(work, postgres, "xyz_reference"), new TestDatabase(work, postgres, "xyz_own"));
    }

    @Test void geoPackageGeometryLayout23() throws Exception { exchangeGeometryLayout(23, null); }
    @Test void geoPackageGeometryLayout24() throws Exception { exchangeGeometryLayout(24, null); }
    @Test void postgisGeometryLayout23() throws Exception { exchangeWithPostgis(23, this::exchangeGeometryLayout); }
    @Test void postgisGeometryLayout24() throws Exception { exchangeWithPostgis(24, this::exchangeGeometryLayout); }

    private void exchangeGeometryLayout(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new GeometryLayoutCases(work, version, new ReferenceTool(work, postgres != null));
        if (postgres != null) {
            cases.sameTable(new TestDatabase(work, postgres, "geometry_layout_reference"),
                    new TestDatabase(work, postgres, "geometry_layout_own"));
        } else {
            cases.wideGeoPackage(new TestDatabase(work, null, "wide_geometry_reference"),
                    new TestDatabase(work, null, "wide_geometry_own"));
        }
        cases.split(new TestDatabase(work, postgres, "split_geometry_reference"),
                new TestDatabase(work, postgres, "split_geometry_own"));
    }

    @Test void geoPackageGeometryHierarchy23() throws Exception { exchangeGeometryHierarchy(23, null); }
    @Test void geoPackageGeometryHierarchy24() throws Exception { exchangeGeometryHierarchy(24, null); }
    @Test void postgisGeometryHierarchy23() throws Exception { exchangeWithPostgis(23, this::exchangeGeometryHierarchy); }
    @Test void postgisGeometryHierarchy24() throws Exception { exchangeWithPostgis(24, this::exchangeGeometryHierarchy); }

    private void exchangeGeometryHierarchy(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new GeometryLayoutCases(work, version, new ReferenceTool(work, postgres != null));
        cases.hierarchy(new TestDatabase(work, postgres, "hierarchy_geometry_reference"),
                new TestDatabase(work, postgres, "hierarchy_geometry_own"), postgres == null);
        cases.hierarchy(new TestDatabase(work, postgres, "alternate_hierarchy_reference"),
                new TestDatabase(work, postgres, "alternate_hierarchy_own"), postgres != null);
    }

    @Test void geoPackageCrs23() throws Exception { exchangeCrs(23, null); }
    @Test void geoPackageCrs24() throws Exception { exchangeCrs(24, null); }
    @Test void postgisCrs23() throws Exception { exchangeWithPostgis(23, this::exchangeCrs); }
    @Test void postgisCrs24() throws Exception { exchangeWithPostgis(24, this::exchangeCrs); }

    private void exchangeCrs(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        for (int epsg : List.of(21781, 4326)) {
            cases.defaults(new TestDatabase(work, postgres, "crs_reference_" + epsg),
                    new TestDatabase(work, postgres, "crs_own_" + epsg), epsg);
        }
        cases.rejectMissingDefault(new TestDatabase(work, postgres, "crs_missing"));
        cases.declaredPointCrs(new TestDatabase(work, postgres, "declared_crs_reference"),
                new TestDatabase(work, postgres, "declared_crs_own"));
        cases.vertexCrs(new TestDatabase(work, postgres, "vertex_crs_reference"),
                new TestDatabase(work, postgres, "vertex_crs_own"));
        cases.modelOverride(new TestDatabase(work, postgres, "model_crs_reference"));
    }

    @Test void geoPackageMixedSrid23() throws Exception { exchangeMixedSrid(23, null); }
    @Test void geoPackageMixedSrid24() throws Exception { exchangeMixedSrid(24, null); }
    @Test void postgisMixedSrid23() throws Exception { exchangeWithPostgis(23, this::exchangeMixedSrid); }
    @Test void postgisMixedSrid24() throws Exception { exchangeWithPostgis(24, this::exchangeMixedSrid); }

    private void exchangeMixedSrid(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.mixedSrid(new TestDatabase(work, postgres, "mixed_split_reference"),
                new TestDatabase(work, postgres, "mixed_split_own"), true);
        cases.mixedSrid(new TestDatabase(work, postgres, "mixed_wide_reference"),
                new TestDatabase(work, postgres, "mixed_wide_own"), false);
    }

    @Test void geoPackageBasketCrsMutation23() throws Exception { exchangeBasketCrsMutation(23, null); }
    @Test void geoPackageBasketCrsMutation24() throws Exception { exchangeBasketCrsMutation(24, null); }
    @Test void postgisBasketCrsMutation23() throws Exception { exchangeWithPostgis(23, this::exchangeBasketCrsMutation); }
    @Test void postgisBasketCrsMutation24() throws Exception { exchangeWithPostgis(24, this::exchangeBasketCrsMutation); }

    private void exchangeBasketCrsMutation(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "basket_crs_mutation");
        var reference = new ReferenceTool(work, postgres != null);
        if (version == 23) new AlternateCrsCases(work, reference).mutate(db);
        else new CrsCases(work, version, reference).mutateDeferred(db);
    }

    @Test void geoPackageAlternateCrsExchange23() throws Exception { exchangeAlternateCrs(23, null); }
    @Test void postgisAlternateCrsExchange23() throws Exception { exchangeWithPostgis(23, this::exchangeAlternateCrs); }

    private void exchangeAlternateCrs(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new AlternateCrsCases(work, new ReferenceTool(work, postgres != null));
        cases.exchange(new TestDatabase(work, postgres, "alternate_crs_legacy"),
                new TestDatabase(work, postgres, "alternate_crs_own"), new TestDatabase(work, postgres, "alternate_crs_invalid"));
    }

    @Test void geoPackageAlternateCrsExport23() throws Exception { exchangeAlternateCrsExport(23, null); }
    @Test void postgisAlternateCrsExport23() throws Exception { exchangeWithPostgis(23, this::exchangeAlternateCrsExport); }

    private void exchangeAlternateCrsExport(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new AlternateCrsCases(work, new ReferenceTool(work, postgres != null));
        cases.export(new TestDatabase(work, postgres, "alternate_crs_reference"));
    }

    @Test void geoPackageDeferredMulti24() throws Exception { exchangeDeferredMulti(24, null); }
    @Test void postgisDeferredMulti24() throws Exception { exchangeWithPostgis(24, this::exchangeDeferredMulti); }

    private void exchangeDeferredMulti(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.deferredMulti(new TestDatabase(work, postgres, "deferred_multi_reference"),
                new TestDatabase(work, postgres, "deferred_multi_own"));
    }

    @Test void geoPackageDeferredCurves24() throws Exception { exchangeDeferredCurves(24, null); }
    @Test void postgisDeferredCurves24() throws Exception { exchangeWithPostgis(24, this::exchangeDeferredCurves); }

    private void exchangeDeferredCurves(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.deferredCurves(new TestDatabase(work, postgres, "deferred_curves_reference"),
                new TestDatabase(work, postgres, "deferred_curves_own"));
    }

    @Test void geoPackageCreateDeferred24() throws Exception { exchangeCreateDeferred(24, null); }
    @Test void postgisCreateDeferred24() throws Exception { exchangeWithPostgis(24, this::exchangeCreateDeferred); }

    private void exchangeCreateDeferred(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.createDeferred(new TestDatabase(work, postgres, "deferred_native_ddl"),
                new TestDatabase(work, postgres, "deferred_reference_ddl"), new TestDatabase(work, postgres, "deferred_rejected_ddl"));
    }

    @Test void geoPackageImportDeferred24() throws Exception { exchangeImportDeferred(24, null); }
    @Test void postgisImportDeferred24() throws Exception { exchangeWithPostgis(24, this::exchangeImportDeferred); }

    private void exchangeImportDeferred(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.importDeferred(new TestDatabase(work, postgres, "deferred_import"));
    }

    @Test void geoPackageExportDeferred24() throws Exception { exchangeExportDeferred(24, null); }
    @Test void postgisExportDeferred24() throws Exception { exchangeWithPostgis(24, this::exchangeExportDeferred); }

    private void exchangeExportDeferred(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.exportDeferred(new TestDatabase(work, postgres, "deferred_reference"));
    }

    @Test void geoPackageStaticGeneric24() throws Exception { exchangeStaticGeneric(24, null); }
    @Test void postgisStaticGeneric24() throws Exception { exchangeWithPostgis(24, this::exchangeStaticGeneric); }

    private void exchangeStaticGeneric(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.staticGeneric(new TestDatabase(work, postgres, "static_crs_reference"),
                new TestDatabase(work, postgres, "static_crs_own"));
    }

    @Test void geoPackageFixedMultiSrs23() throws Exception { exchangeFixedMultiSrs(23, null); }
    @Test void geoPackageFixedMultiSrs24() throws Exception { exchangeFixedMultiSrs(24, null); }
    @Test void postgisFixedMultiSrs23() throws Exception { exchangeWithPostgis(23, this::exchangeFixedMultiSrs); }
    @Test void postgisFixedMultiSrs24() throws Exception { exchangeWithPostgis(24, this::exchangeFixedMultiSrs); }

    private void exchangeFixedMultiSrs(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.fixedMultiSrs(new TestDatabase(work, postgres, "suffix_split_reference"),
                new TestDatabase(work, postgres, "suffix_split_own"), true);
        cases.fixedMultiSrs(new TestDatabase(work, postgres, "suffix_wide_reference"),
                new TestDatabase(work, postgres, "suffix_wide_own"), false);
    }

    @Test void geoPackageLocalSrid23() throws Exception { exchangeLocalSrid(23, null); }
    @Test void geoPackageLocalSrid24() throws Exception { exchangeLocalSrid(24, null); }
    @Test void postgisLocalSrid23() throws Exception { exchangeWithPostgis(23, this::exchangeLocalSrid); }
    @Test void postgisLocalSrid24() throws Exception { exchangeWithPostgis(24, this::exchangeLocalSrid); }

    private void exchangeLocalSrid(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(work, postgres != null));
        cases.localSrid(new TestDatabase(work, postgres, "local_srid"));
    }

    private void exchangeTranslationSources(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "translation_sources");
        new TranslationCases(work, new ReferenceTool(work, db.postgis()), version).sources(db);
    }

    private void exchangeTranslationScalars(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "translation_scalars");
        new TranslationCases(work, new ReferenceTool(work, db.postgis()), version).scalars(db);
    }

    private void exchangeTranslationNested(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "translation_nested");
        new TranslationCases(work, new ReferenceTool(work, db.postgis()), version).nested(db);
    }

    private void exchangeTranslationSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_translation");
        var own = new TestDatabase(work, postgres, "own_translation");
        var extended = new TestDatabase(work, postgres, "extended_translation");
        new TranslationCases(work, new ReferenceTool(work, reference.postgis()), version).schema(reference, own, extended);
    }

    private void exchangeTranslationExport(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "translation_export");
        new TranslationCases(work, new ReferenceTool(work, db.postgis()), version).exportLanguage(db);
    }

    private void exchangeTopicInherited(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_topic_inherited");
        var own = new TestDatabase(work, postgres, "own_topic_inherited");
        new TopicCases(work, new ReferenceTool(work, reference.postgis()), version).inherited(reference, own);
    }

    private void exchangeTopicFragments(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_topic_fragments");
        var own = new TestDatabase(work, postgres, "own_topic_fragments");
        new TopicCases(work, new ReferenceTool(work, reference.postgis()), version).fragments(reference, own);
    }

    private void exchangeTopicRefinement(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_topic_refinement");
        var own = new TestDatabase(work, postgres, "own_topic_refinement");
        new TopicCases(work, new ReferenceTool(work, reference.postgis()), version).refinement(reference, own);
    }

    private void exchangeTopicReferences(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_topic_references");
        var own = new TestDatabase(work, postgres, "own_topic_references");
        new TopicCases(work, new ReferenceTool(work, reference.postgis()), version).references(reference, own);
    }

    private void exchangeTopicMutations(int version, PostgreSQLContainer postgres) throws Exception {
        var translated = new TestDatabase(work, postgres, "translation_mutation");
        var inherited = new TestDatabase(work, postgres, "topic_mutation");
        var reference = new ReferenceTool(work, inherited.postgis());
        new TranslationCases(work, reference, version).mutation(translated);
        new TopicCases(work, reference, version).mutation(inherited);
    }

    private void exchangeSharedSource(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "shared_source");
        new ModelCases(work, new ReferenceTool(work, db.postgis()), version).sharedSource(db);
    }

    private void exchangeStoredImports(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "stored_imports");
        new ModelCases(work, new ReferenceTool(work, db.postgis()), version).storedImports(db);
    }

    private void exchangeLocalImports(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_imports");
        var nativeSchema = new TestDatabase(work, postgres, "native_imports");
        new ModelCases(work, new ReferenceTool(work, reference.postgis()), version).localImports(reference, nativeSchema);
    }

    private void exchangeImportedStruct(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_detail");
        var nativeSchema = new TestDatabase(work, postgres, "native_detail");
        new ModelCases(work, new ReferenceTool(work, reference.postgis()), version).importedStructures(reference, nativeSchema);
    }

    private void exchangeImportedTopic(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_topic");
        var nativeSchema = new TestDatabase(work, postgres, "native_topic");
        new ModelCases(work, new ReferenceTool(work, reference.postgis()), version).importedTopics(reference, nativeSchema);
    }

    private void exchangeJointSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_joint");
        var nativeSchema = new TestDatabase(work, postgres, "native_joint");
        new ModelCases(work, new ReferenceTool(work, reference.postgis()), version).jointSchema(reference, nativeSchema);
    }

    private void exchangeMixedExport(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "mixed_export");
        new ModelCases(work, new ReferenceTool(work, db.postgis()), version).mixedExport(db);
    }

    private void exchangeMixedImport(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(work, postgres, "reference_mixed");
        var nativeSchema = new TestDatabase(work, postgres, "native_mixed");
        new ModelCases(work, new ReferenceTool(work, reference.postgis()), version).mixedImport(reference, nativeSchema);
    }

    private void exchangeMixedMutation(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "mixed_mutation");
        new ModelCases(work, new ReferenceTool(work, db.postgis()), version).mixedMutation(db);
    }

    private void exchangeAppendModels(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "append_models");
        new ModelCases(work, new ReferenceTool(work, db.postgis()), version).appendModels(db);
    }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var base = new BaseDbFixture(work, version);
        String model = base.model();
        Path ili = base.source();
        String fixture = base.transfer();

        var reference = new ReferenceTool(work, postgres != null);
        var db = new TestDatabase(work, postgres, "reference");
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                ili.toString());
        try (var sql = db.connect()) {
            assertClassHierarchyCatalog(db, sql, model);
            assertClassGeometryCatalog(db, sql, model, version);
            assertReferenceSourceHierarchyCatalog(db, sql, model, version);
            assertStructureCatalog(db, sql, model, version);
            assertNestedStructureCatalog(db, sql, model);
            assertEmbeddedRoleCatalog(db, sql, model);
            assertHierarchyRoleCatalog(db, sql, model, version);
            assertStructureHierarchyCatalog(db, sql, model);
            assertStructureReferenceCatalog(db, sql, model);
            assertPolymorphicTargetCatalog(db, sql, model, version);
            assertPairingCatalog(db, sql, model, version);
            assertAssociationCatalog(db, sql, model, "MarkerLink", Map.of("LinkedMarker", "marker", "LinkedHub", "hub"));
            assertAssociationCatalog(db, sql, model, "Review", Map.of("ReviewMarkers", "marker", "Witness", "hub"));
            assertAssociationCatalog(db, sql, model, "AnnotatedLink",
                    Map.of("ConsultedMarker", "marker", "ConsultedReport", "report"));
            assertAssociationCatalog(db, sql, model, "HierarchyLink", Map.of("WorkBranch", "tower", "StaffRoot", "servicebase"));
            assertAssociationCatalog(db, sql, model, "HierarchyNote", Map.of("RaisedAt", "mast", "SharedAt", "servicebase"));
            assertTypeKind(db, sql, "hierarchynote", "memo", "TEXT");
            assertTypeKind(db, sql, "hierarchynote", "severity", "ENUM");
            assertAssociationCatalog(db, sql, model, "RatingTag",
                    Map.of("TaggedUnit", "serviceunit", "TaggedForm", "ratingform"));
            assertAssociationCatalog(db, sql, model, "RelayReview",
                    Map.of("Recommended", "servicebase", "ReviewingRelays", "arelay"));
            assertAssociationCatalog(db, sql, model, "Rating", Map.of("Rated", "tower", "RatingForms", "ratingform"));
            assertAssociationCatalog(db, sql, model, "ServiceAggregation",
                    Map.of("GroupWhole", "refcarrier", "GroupPart", "servicebase"));
            assertReferenceMapping(db, sql, model, "MastOwnership.WholeMast", "servicebase", "wholemast", "mast");
            assertEquals("embedded", scalar(sql, "select setting from " + db.table("t_ili2db_trafo")
                    + " where iliname='" + model + ".Data.MastOwnership' and tag='ch.ehi.ili2db.inheritance'"));
            assertAssociationCatalog(db, sql, model, "DispatchRecord",
                    Map.of("DispatchLeft", "pairleft", "DispatchLocation", "place", "DispatchMast", "tower"));
            assertTypeKind(db, sql, "dispatchrecord", "memo", "TEXT");
            assertTypeKind(db, sql, "dispatchrecord", "severity", "ENUM");
            assertAssociationHierarchyCatalog(db, sql, model);
            assertTypeKind(db, sql, "review", "memo", "TEXT");
            assertTypeKind(db, sql, "review", "grade", "NUMERIC");
            assertTypeKind(db, sql, "annotatedlink", "memo", "TEXT");
            assertTypeKind(db, sql, "annotatedlink", "grade", "NUMERIC");
            assertTypeKind(db, sql, "annotatedlink", "localdecision", "ENUM");
            assertTypeKind(db, sql, "parcel", "boundary", "AREA");
            if (version == 24) {
                assertTypeKind(db, sql, "park", "coverage", "MULTISURFACE");
                assertTypeKind(db, sql, "district", "coverage", "MULTIAREA");
                assertTypeKind(db, sql, "routegroup", "traces", "MULTIPOLYLINE");
                assertTypeKind(db, sql, "survey", "locations", "MULTICOORD");
            }
            var catalog = DbSchemaCatalog.read(sql, db.postgis, db.schema);
            if (!db.postgis) {
                catalog = catalog.expectGeoPackageGeometryExtension("route", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("tower", "alignment", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("plot", "footprint", "CURVEPOLYGON")
                        .expectGeoPackageGeometryExtension("parcel", "boundary", "CURVEPOLYGON");
                if (version == 24) {
                    catalog = catalog.expectGeoPackageGeometryExtension("park", "coverage", "MULTISURFACE")
                            .expectGeoPackageGeometryExtension("district", "coverage", "MULTISURFACE")
                            .expectGeoPackageGeometryExtension("routegroup", "traces", "MULTICURVE");
                }
            }
            assertSchemaCreation(reference, postgres, model, version, ili, fixture, catalog);
            Path nativeOutput = exchangeDatasets(reference, db, sql, work.toString(), model, version, fixture);
            execute(sql, "update " + db.table("t_ili2db_attrname") + " set sqlname='missing_select'"
                    + " where iliname='" + model + ".Data.Place.Select'");
            String originalOutput = Files.readString(nativeOutput);
            var corrupt = cli(db, "export", model, "legacy", nativeOutput, CancellationToken.none());
            assertNotEquals(0, corrupt.code(), corrupt::diagnostics);
            assertTrue(corrupt.diagnostics().contains("Select") || corrupt.diagnostics().contains("missing_select"),
                    corrupt::diagnostics);
            assertEquals(originalOutput, Files.readString(nativeOutput));
        }
    }

    private static void assertStructureCatalog(TestDatabase db, Connection sql, String model, int version)
            throws SQLException {
        String structure = model + (version == 23 ? ".Data.Detail" : ".Detail");
        assertEquals("detail", scalar(sql, "select sqlname from " + db.table("t_ili2db_classname")
                + " where iliname='" + structure + "'"));
        assertEquals("STRUCTURE", scalar(sql, "select setting from " + db.table("t_ili2db_table_prop")
                + " where tablename='detail' and tag='ch.ehi.ili2db.tableKind'"));
        for (String attribute : List.of("Summary", "Entries", "Observations")) {
            String column = "report_" + attribute.toLowerCase(Locale.ROOT);
            assertEquals("1", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                    + " where iliname='" + model + ".Data.Report." + attribute + "' and sqlname='" + column
                    + "' and colowner='detail' and target='report'"));
            assertEquals("report", scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                    + " where tablename='detail' and subtype is null and columnname='" + column
                    + "' and tag='ch.ehi.ili2db.foreignKey'"));
        }
    }

    private static void assertNestedStructureCatalog(TestDatabase db, Connection sql, String model) throws SQLException {
        var childTables = Map.of("NestedRecord.Tree", "nestedouter", "NestedOuter.Items", "nestedinner",
                "NestedInner.Main", "nestedleaf", "NestedInner.Values", "nestedleaf", "NestedInner.Extras", "nestedleaf",
                "Mast.Notes", "nestedleaf", "Tower.Supplement", "nestedouter");
        for (var field : childTables.entrySet()) {
            String parent = field.getKey().substring(0, field.getKey().indexOf('.')).toLowerCase(Locale.ROOT);
            String column = field.getKey().replace('.', '_').toLowerCase(Locale.ROOT);
            String child = field.getValue();
            assertEquals("1", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                    + " where iliname='" + model + ".Data." + field.getKey() + "' and sqlname='" + column
                    + "' and colowner='" + child + "' and target='" + parent + "'"));
            assertEquals(parent, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                    + " where tablename='" + child + "' and subtype is null and columnname='" + column
                    + "' and tag='ch.ehi.ili2db.foreignKey'"));
        }
    }

    private static void assertEmbeddedRoleCatalog(TestDatabase db, Connection sql, String model) throws SQLException {
        assertEquals("markerhub", scalar(sql, "select sqlname from " + db.table("t_ili2db_classname")
                + " where iliname='" + model + ".Data.MarkerHub'"));
        String associationTable = db.postgis
                ? "select count(*) from information_schema.tables where table_schema='" + db.schema + "'"
                : "select count(*) from sqlite_schema where type='table'";
        assertEquals("0", scalar(sql, associationTable + (db.postgis ? " and table_name" : " and name") + "='markerhub'"));
        String columns = db.table("t_ili2db_attrname");
        assertEquals("1", scalar(sql, "select count(*) from " + columns + " where iliname='" + model
                + ".Data.Marker.ASelect' and sqlname='aselect' and colowner='marker' and target is null"));
        assertEquals("1", scalar(sql, "select count(*) from " + columns + " where iliname='" + model
                + ".Data.MarkerHub.Select' and sqlname='aselect1' and colowner='marker' and target='hub'"));
        assertEquals("hub", scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                + " where tablename='marker' and subtype is null and columnname='aselect1'"
                + " and tag='ch.ehi.ili2db.foreignKey'"));
    }

    private static void assertAssociationHierarchyCatalog(TestDatabase db, Connection sql, String model)
            throws SQLException {
        assertAssociationCatalog(db, sql, model, "AllocationBase",
                Map.of("ListedSite", "contractsite", "ListedSigner", "contractsigner"));
        String prefix = model + ".Data.";
        assertEquals(prefix + "AllocationBase", scalar(sql, "select baseclass from " + db.table("t_ili2db_inheritance")
                + " where thisclass='" + prefix + "TaggedAllocation'"));
        String properties = db.table("t_ili2db_column_prop");
        assertEquals("allocationbase", scalar(sql, "select setting from " + properties
                + " where tablename='taggedallocation' and lower(columnname)='t_id' and tag='ch.ehi.ili2db.foreignKey'"));
        assertEquals("[\"taggedallocation\"]", scalar(sql, "select setting from " + properties
                + " where tablename='allocationbase' and lower(columnname)='t_type' and tag='ch.ehi.ili2db.types'"));
        String names = db.table("t_ili2db_attrname");
        assertEquals("allocationbase", scalar(sql, "select colowner from " + names
                + " where iliname='" + prefix + "AllocationBase.BaseNote'"));
        assertEquals("taggedallocation", scalar(sql, "select colowner from " + names
                + " where iliname='" + prefix + "TaggedAllocation.DetailRank'"));
    }

    private static void assertPairingCatalog(TestDatabase db, Connection sql, String model, int version) throws SQLException {
        String association = model + ".Data.Pairing";
        String role = version == 23 ? "LeftLink" : "RightLink";
        String inverse = version == 23 ? "RightLink" : "LeftLink";
        String owner = version == 23 ? "pairright" : "pairleft";
        String target = version == 23 ? "pairleft" : "pairright";
        String column = role.toLowerCase(Locale.ROOT);
        assertEquals("pairing", scalar(sql, "select sqlname from " + db.table("t_ili2db_classname")
                + " where iliname='" + association + "'"));
        assertEquals("embedded", scalar(sql, "select setting from " + db.table("t_ili2db_trafo")
                + " where iliname='" + association + "' and tag='ch.ehi.ili2db.inheritance'"));
        assertEquals("1", scalar(sql, "select count(*) from " + db.table("t_ili2db_inheritance")
                + " where thisclass='" + association + "' and baseclass is null"));
        String mapping = db.table("t_ili2db_attrname");
        assertEquals("1", scalar(sql, "select count(*) from " + mapping + " where iliname='" + association + "."
                + role + "' and sqlname='" + column + "' and colowner='" + owner + "' and target='" + target + "'"));
        assertEquals("0", scalar(sql, "select count(*) from " + mapping + " where iliname='" + association + "." + inverse + "'"));
        assertEquals(target, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                + " where tablename='" + owner + "' and subtype is null and columnname='" + column + "'"
                + " and tag='ch.ehi.ili2db.foreignKey'"));
        String associationTable = db.postgis
                ? "select count(*) from information_schema.tables where table_schema='" + db.schema + "' and table_name='pairing'"
                : "select count(*) from sqlite_schema where type='table' and name='pairing'";
        assertEquals("0", scalar(sql, associationTable));
        String nullableColumn = db.postgis
                ? "select count(*) from information_schema.columns where table_schema='" + db.schema
                        + "' and table_name='" + owner + "' and column_name='" + column + "' and is_nullable='YES'"
                : "select count(*) from pragma_table_info('" + owner + "') where name='" + column + "' and \"notnull\"=0";
        assertEquals("1", scalar(sql, nullableColumn));
    }

    private static void assertPolymorphicTargetCatalog(TestDatabase db, Connection sql, String model, int version)
            throws SQLException {
        assertReferenceMapping(db, sql, model, "TargetPair.RootTarget", "targetpair", "roottarget", "servicebase");
        assertReferenceMapping(db, sql, model, "TargetPair.BranchTarget", "targetpair", "branchtarget", "tower");
        if (version == 24) {
            assertReferenceMapping(db, sql, model, "RefCarrier.DirectRoot", "refcarrier", "directroot", "servicebase");
            assertReferenceMapping(db, sql, model, "RefCarrier.DirectBranch", "refcarrier", "directbranch", "tower");
        }
    }

    private static void assertHierarchyRoleCatalog(TestDatabase db, Connection sql, String model, int version)
            throws SQLException {
        assertReferenceMapping(db, sql, model, "HierarchyCrew.Supervisor", "servicebase", "supervisor", "tower");
        assertReferenceMapping(db, sql, model, "MastMentoring.Mentor", "tower", "mentor", "mast");
        if (version == 23) {
            assertReferenceMapping(db, sql, model, "HierarchyPair.PairedService", "tower", "pairedservice", "servicebase");
        } else {
            assertReferenceMapping(db, sql, model, "HierarchyPair.PairedTower", "servicebase", "pairedtower", "tower");
        }
        for (String association : List.of("HierarchyCrew", "MastMentoring", "HierarchyPair")) {
            assertEquals("embedded", scalar(sql, "select setting from " + db.table("t_ili2db_trafo")
                    + " where iliname='" + model + ".Data." + association + "' and tag='ch.ehi.ili2db.inheritance'"));
        }
    }

    private static void assertStoredHierarchyRoles(TestDatabase db, Connection sql, int version, String dataset)
            throws SQLException {
        String tower = " from " + db.table("tower") + " t join " + db.table("mast")
                + " m on m.t_id=t.t_id and m.t_basket=t.t_basket where m.t_ili_tid='" + dataset;
        String relay = tower + "-relay'";
        String empty = tower + "-tower'";
        String service = " from " + db.table("servicebase") + " where t_ili_tid='" + dataset + "-serviceunit'";
        String relayId = scalar(sql, "select t.t_id" + relay);
        String serviceId = scalar(sql, "select t_id" + service);
        assertNotNull(relayId);
        assertNotNull(serviceId);
        assertEquals(relayId, scalar(sql, "select supervisor" + service));
        assertEquals(relayId, scalar(sql, "select t.mentor" + relay));
        assertNull(scalar(sql, "select t.mentor" + empty));
        if (version == 23) {
            assertEquals(serviceId, scalar(sql, "select t.pairedservice" + relay));
            assertNull(scalar(sql, "select t.pairedservice" + empty));
        } else {
            assertEquals(relayId, scalar(sql, "select pairedtower" + service));
        }
    }

    private static void assertStoredAggregationAndComposition(TestDatabase db, Connection sql, String dataset)
            throws SQLException {
        String roles = """
                from %s a
                join %s r on r.t_id=a.groupwhole and r.t_basket=a.t_basket
                join %s s on s.t_id=a.grouppart and s.t_basket=a.t_basket
                join %s b on b.t_id=a.t_basket
                where r.t_ili_tid='%s-refcarrier' and s.t_ili_tid='%s-serviceunit' and b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("serviceaggregation"), db.table("refcarrier"), db.table("servicebase"),
                db.table("t_ili2db_basket"), dataset, dataset, dataset);
        assertEquals("1", scalar(sql, "select count(*) " + roles));
        String composition = """
                select m.t_ili_tid from %s s
                join %s m on m.t_id=s.wholemast and m.t_basket=s.t_basket
                where s.t_ili_tid='%s-serviceunit'
                """.formatted(db.table("servicebase"), db.table("mast"), dataset);
        assertEquals(dataset + "-beacon", scalar(sql, composition));
    }

    private static void assertStoredPolymorphicTargets(TestDatabase db, Connection sql, String dataset, int version)
            throws SQLException {
        String root = scalar(sql, "select t_id from " + db.table("servicebase") + " where t_ili_tid='" + dataset + "-serviceunit'");
        String branch = scalar(sql, "select t.t_id from " + db.table("tower") + " t join " + db.table("mast")
                + " m on m.t_id=t.t_id and m.t_basket=t.t_basket where m.t_ili_tid='" + dataset + "-relay'");
        assertNotNull(root);
        assertNotNull(branch);
        String carrier = " where t_ili_tid='" + dataset + "-refcarrier'";
        String rows = """
                select p.label, p.roottarget, p.branchtarget, p.t_seq from %s p
                join %s c on c.t_id=p.refcarrier_pairs and c.t_basket=p.t_basket
                where c.t_ili_tid='%s-refcarrier' order by p.t_seq
                """.formatted(db.table("targetpair"), db.table("refcarrier"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals("forward", result.getString(1));
            assertEquals(root, result.getString(2));
            assertEquals(branch, result.getString(3));
            assertEquals("0", result.getString(4));
            assertTrue(result.next());
            assertEquals("missing", result.getString(1));
            assertNull(result.getObject(2));
            assertNull(result.getObject(3));
            assertEquals("1", result.getString(4));
            assertFalse(result.next());
        }
        if (version == 24) {
            assertEquals(root, scalar(sql, "select directroot from " + db.table("refcarrier") + carrier));
            String tower = scalar(sql, "select t.t_id from " + db.table("tower") + " t join " + db.table("mast")
                    + " m on m.t_id=t.t_id and m.t_basket=t.t_basket where m.t_ili_tid='" + dataset + "-tower'");
            assertNotNull(tower);
            assertEquals(tower, scalar(sql, "select directbranch from " + db.table("refcarrier") + carrier));
        }
    }

    private static void assertTransferredPolymorphicTargets(Element carrier, int version, String dataset) {
        String prefix = version == 23 ? "DbInterop23.Data." : "";
        assertNotNull(carrier);
        assertEquals(prefix + "RefCarrier", carrier.getLocalName());
        var pairs = children(carrier).stream().filter(value -> value.getLocalName().equals("Pairs"))
                .flatMap(value -> children(value).stream()).toList();
        assertEquals(List.of(prefix + "TargetPair", prefix + "TargetPair"),
                pairs.stream().map(Element::getLocalName).toList());
        assertLocalReference(pairs.getFirst(), "RootTarget", version, dataset + "-serviceunit");
        assertLocalReference(pairs.getFirst(), "BranchTarget", version, dataset + "-relay");
        assertNull(child(pairs.getLast(), "RootTarget"));
        assertNull(child(pairs.getLast(), "BranchTarget"));
        if (version == 24) {
            assertLocalReference(carrier, "DirectRoot", version, dataset + "-serviceunit");
            assertLocalReference(carrier, "DirectBranch", version, dataset + "-tower");
        }
    }

    private static void assertStructureReferenceCatalog(TestDatabase db, Connection sql, String model)
            throws SQLException {
        String names = db.table("t_ili2db_attrname");
        assertEquals("1", scalar(sql, "select count(*) from " + names
                + " where iliname='" + model + ".Data.LinkDetail.Destination'"
                + " and sqlname='destination' and colowner='linkdetail' and target='place'"));
        assertEquals("place", scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                + " where tablename='linkdetail' and subtype is null and columnname='destination'"
                + " and tag='ch.ehi.ili2db.foreignKey'"));
    }

    private static void assertStoredStructureReferences(TestDatabase db, Connection sql, String dataset)
            throws SQLException {
        String rows = """
                select l.label, l.t_basket, l.t_seq, l.destination, p.t_id as target_id,
                       p.t_ili_tid as target_tid, p.t_basket as target_basket
                from %s l join %s r on r.t_id=l.report_links
                left join %s p on p.t_id=l.destination
                where r.t_ili_tid='%s' order by l.t_seq
                """.formatted(db.table("linkdetail"), db.table("report"), db.table("place"), dataset + "-report-2");
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals("first", result.getString("label"));
            assertEquals(0, result.getInt("t_seq"));
            assertNotNull(result.getString("destination"));
            assertEquals(result.getString("target_id"), result.getString("destination"));
            assertEquals(dataset + "-place-3", result.getString("target_tid"));
            assertEquals(result.getString("t_basket"), result.getString("target_basket"));
            assertTrue(result.next());
            assertEquals("second", result.getString("label"));
            assertEquals(1, result.getInt("t_seq"));
            assertNull(result.getString("destination"));
            assertFalse(result.next());
        }
    }

    private static void assertTransferredStructureReferences(Element report, int version, String dataset) {
        var links = children(report).stream().filter(element -> element.getLocalName().equals("Links"))
                .flatMap(element -> children(element).stream()).toList();
        String name = version == 23 ? "DbInterop23.Data.LinkDetail" : "LinkDetail";
        assertEquals(List.of(name, name), links.stream().map(Element::getLocalName).toList());
        assertEquals("first", text(links.getFirst(), "Label"));
        var destination = child(links.getFirst(), "Destination");
        assertNotNull(destination);
        assertEquals(dataset + "-place-3", id(destination, version, "ref"));
        assertEquals("", id(destination, version, "bid"));
        assertEquals("second", text(links.getLast(), "Label"));
        assertNull(child(links.getLast(), "Destination"));
    }

    private static void assertClassHierarchyCatalog(TestDatabase db, Connection sql, String model) throws SQLException {
        String prefix = model + ".Data.";
        String inheritance = db.table("t_ili2db_inheritance");
        assertEquals(prefix + "Tower", scalar(sql, "select baseclass from " + inheritance
                + " where thisclass='" + prefix + "Relay'"));
        assertEquals(prefix + "ServiceBase", scalar(sql, "select baseclass from " + inheritance
                + " where thisclass='" + prefix + "ServiceUnit'"));
        String properties = db.table("t_ili2db_column_prop");
        assertEquals("tower", scalar(sql, "select setting from " + properties
                + " where tablename='arelay' and lower(columnname)='t_id' and tag='ch.ehi.ili2db.foreignKey'"));
        assertEquals("[\"arelay\",\"beacon\",\"mast\",\"tower\"]", scalar(sql, "select setting from " + properties
                + " where tablename='mast' and lower(columnname)='t_type' and tag='ch.ehi.ili2db.types'"));
        assertEquals("[\"serviceunit\"]", scalar(sql, "select setting from " + properties
                + " where tablename='servicebase' and lower(columnname)='t_type' and tag='ch.ehi.ili2db.types'"));
        String columns = db.table("t_ili2db_attrname");
        assertEquals("mast", scalar(sql, "select colowner from " + columns + " where iliname='" + prefix + "Mast.Caption'"));
        assertEquals("tower", scalar(sql, "select colowner from " + columns + " where iliname='" + prefix + "Tower.Height'"));
        assertEquals("arelay", scalar(sql, "select colowner from " + columns + " where iliname='" + prefix + "Relay.Online'"));
    }

    private static void assertReferenceSourceHierarchyCatalog(TestDatabase db, Connection sql, String model,
                                                              int version) throws SQLException {
        var sourceTypes = version == 24 ? List.of("RefNote", "RefAsset") : List.of("RefNote");
        for (String source : sourceTypes) {
            for (var field : List.of(List.of("Base", "RootTarget", "mast"),
                    List.of("Branch", "BranchTarget", "tower"), List.of("Leaf", "OwnTarget", "mast"))) {
                String declaredType = source + field.get(0);
                String table = declaredType.toLowerCase(Locale.ROOT);
                String attribute = field.get(1);
                String column = attribute.toLowerCase(Locale.ROOT);
                String target = field.get(2);
                assertReferenceMapping(db, sql, model, declaredType + "." + attribute, table, column, target);
            }
        }
    }

    private static void assertStructureHierarchyCatalog(TestDatabase db, Connection sql, String model) throws SQLException {
        String types = " from " + db.table("t_ili2db_column_prop")
                + " where lower(columnname)='t_type' and tag='ch.ehi.ili2db.types' and tablename=";
        assertEquals("[\"countnote\",\"flagnote\",\"notebase\"]", scalar(sql, "select setting" + types + "'notebase'"));
        assertEquals("[\"namednote\"]", scalar(sql, "select setting" + types + "'notekind'"));
        var roots = Map.of("Main", "notebase", "Values", "notebase", "Extras", "notekind");
        for (var field : roots.entrySet()) {
            String column = "typedrecord_" + field.getKey().toLowerCase(Locale.ROOT);
            assertEquals("1", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                    + " where iliname='" + model + ".Data.TypedRecord." + field.getKey()
                    + "' and sqlname='" + column + "' and colowner='" + field.getValue()
                    + "' and target='typedrecord'"));
            assertEquals("typedrecord", scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                    + " where tablename='" + field.getValue() + "' and columnname='" + column
                    + "' and tag='ch.ehi.ili2db.foreignKey'"));
        }
    }

    private static void assertClassGeometryCatalog(TestDatabase db, Connection sql, String model, int version)
            throws SQLException {
        var owners = new HashMap<>(Map.of("Mast.Center", "mast", "Tower.Alignment", "tower",
                "ServiceBase.ServicePoint", "servicebase"));
        if (version == 24) owners.put("Relay.Sites", "arelay");
        for (var entry : owners.entrySet()) {
            String attribute = entry.getKey();
            String table = entry.getValue();
            String column = attribute.substring(attribute.indexOf('.') + 1).toLowerCase(Locale.ROOT);
            assertEquals(column, scalar(sql, "select sqlname from " + db.table("t_ili2db_attrname")
                    + " where iliname='" + model + ".Data." + attribute + "' and colowner='" + table + "'"));
            if (!db.postgis) {
                assertEquals("features", scalar(sql, "select data_type from gpkg_contents where table_name='" + table + "'"));
            }
        }
        if (!db.postgis) {
            for (String table : version == 24 ? List.of("serviceunit") : List.of("arelay", "serviceunit")) {
                assertEquals("attributes", scalar(sql, "select data_type from gpkg_contents where table_name='" + table + "'"));
            }
        }
    }

    private void assertSchemaCreation(ReferenceTool reference, PostgreSQLContainer postgres, String model, int version,
                                      Path ili, String fixture, DbSchemaCatalog catalog) throws Exception {
        var db = new TestDatabase(work, postgres, "native");
        assertSchemaWithIdentityColumns(db, model, ili);
        try (var sql = db.connect()) {
            catalog.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            // Only the stored model is available under its recorded filename in this direction.
            Path heldModel = Files.move(ili, work.resolve("held-model"));
            try {
                exchangeDatasets(reference, db, sql, STORED_MODELS, model, version, fixture);
            } finally {
                Files.move(heldModel, ili);
            }
        }
    }

    private Path exchangeDatasets(ReferenceTool reference, TestDatabase db, Connection sql, String modelDir,
                                  String model, int version, String fixture) throws Exception {
        String selectMapping = " from " + db.table("t_ili2db_attrname")
                + " where iliname='" + model + ".Data.Place.Select'";
        assertEquals("aselect", scalar(sql, "select sqlname" + selectMapping));

        Path legacyInput = Files.writeString(work.resolve("legacy.xtf"), fixture);
        reference.run(db, modelDir, "--import", "--importTid", "--importBid", "--dataset", "legacy",
                legacyInput.toString());
        assertStoredDataset(db, sql, version, "legacy", false);
        // The reference normalizes empty TEXT to NULL. Restore an actual empty SQL value to test our export.
        execute(sql, "update " + db.table("place") + " set aselect='' where t_ili_tid='legacy-place-2'");
        execute(sql, "update " + db.table("annotatedlink") + " set memo='' where consultedmarker=(select t_id from "
                + db.table("marker") + " where t_ili_tid='legacy-marker-2')");
        Path nativeOutput = work.resolve("native-export.xtf");
        assertCliSuccess(db, "export", model, "legacy", nativeOutput);
        assertTransfer(nativeOutput, version, "legacy", true);

        Path nativeInput = Files.writeString(work.resolve("native.xtf"), fixture.replace("legacy-", "native-"));
        assertCliSuccess(db, "import", model, "native", nativeInput);
        assertStoredDataset(db, sql, version, "native", true);
        Path legacyOutput = work.resolve("legacy-export.xtf");
        reference.run(db, modelDir, "--export", "--exportTid", "--dataset", "native", legacyOutput.toString());
        assertTransfer(legacyOutput, version, "native", false);

        if (db.postgis && modelDir.equals(STORED_MODELS)) assertPostgresIdDefault(db);
        Path tail = Files.writeString(work.resolve("tail.xtf"), fixture.replace("legacy-", "tail-"));
        reference.run(db, modelDir, "--import", "--importTid", "--importBid", "--dataset", "tail", tail.toString());
        assertStoredDataset(db, sql, version, "tail", false);
        assertRows(db, sql, version, 3);
        return nativeOutput;
    }

    private static void assertPostgresIdDefault(TestDatabase db) throws SQLException {
        try (var sql = db.connect()) {
            sql.setAutoCommit(false);
            long lastId = Long.parseLong(scalar(sql, "select last_value from " + db.table("t_ili2db_seq")));
            String newId = scalar(sql, "insert into " + db.table("place") + " (t_basket) values ((select min(t_id) from "
                    + db.table("t_ili2db_basket") + ")) returning t_id");
            assertEquals(Long.toString(lastId + 1), newId);
            sql.rollback();
        }
    }

    private static void assertStoredHierarchyReferences(TestDatabase db, Connection sql, int version, String dataset)
            throws SQLException {
        String notes = """
                join %s book on book.t_id=src.refnotebook_entries and book.t_basket=src.t_basket
                where book.t_ili_tid='%s-ref-notebook' order by src.t_seq
                """.formatted(db.table("refnotebook"), dataset);
        assertReferenceFragmentRows(db, sql, "refnote", dataset, notes);
        if (version == 24) {
            assertReferenceFragmentRows(db, sql, "refasset", dataset,
                    " where src.t_ili_tid in ('" + dataset + "-ref-asset-1','" + dataset
                            + "-ref-asset-2') order by src.t_ili_tid");
        }
    }

    private static void assertReferenceFragmentRows(TestDatabase db, Connection sql, String prefix, String dataset,
                                                    String selection) throws SQLException {
        String mast = db.table("mast");
        String query = """
                select src.label, src.roottarget, branch.branchtarget, leaf.owntarget,
                       root_target.t_ili_tid, branch_target.t_ili_tid, own_target.t_ili_tid
                from %s src
                join %s branch on branch.t_id=src.t_id and branch.t_basket=src.t_basket
                join %s leaf on leaf.t_id=src.t_id and leaf.t_basket=src.t_basket
                left join %s root_target on root_target.t_id=src.roottarget and root_target.t_basket=src.t_basket
                left join %s branch_target on branch_target.t_id=branch.branchtarget and branch_target.t_basket=src.t_basket
                left join %s own_target on own_target.t_id=leaf.owntarget and own_target.t_basket=src.t_basket
                """.formatted(db.table(prefix + "base"), db.table(prefix + "branch"), db.table(prefix + "leaf"),
                mast, mast, mast) + selection;
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertEquals("populated", rows.getString(1));
            assertEquals(dataset + "-beacon", rows.getString(5));
            assertEquals(dataset + "-relay", rows.getString(6));
            assertEquals(dataset + "-mast", rows.getString(7));
            assertTrue(rows.next());
            assertEquals("empty", rows.getString(1));
            assertNull(rows.getObject(2));
            assertNull(rows.getObject(3));
            assertNull(rows.getObject(4));
            assertFalse(rows.next());
        }
    }

    private static void assertStoredClassHierarchy(TestDatabase db, Connection sql, int version, String dataset)
            throws SQLException {
        String mast = db.table("mast");
        String tower = db.table("tower");
        String relay = db.table("arelay");
        String beacon = db.table("beacon");
        String basket = db.table("t_ili2db_basket");
        String sites = version == 24 ? ", " + geometryField(db, "r.sites") : "";
        String objects = """
                select b.t_ili_tid, m.t_ili_tid, m.t_type, m.caption, t.height, r.aonline, f.asignal,
                       %s, %s%s
                from %s m join %s b on b.t_id=m.t_basket
                left join %s t on t.t_id=m.t_id and t.t_basket=m.t_basket
                left join %s r on r.t_id=m.t_id and r.t_basket=m.t_basket
                left join %s f on f.t_id=m.t_id and f.t_basket=m.t_basket
                where b.t_ili_tid='%s-basket-2' order by m.t_type
                """.formatted(geometryField(db, "m.center"), geometryField(db, "t.alignment"), sites,
                mast, basket, tower, relay, beacon, dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(objects)) {
            for (var item : List.of(List.of("relay", "leaf"), List.of("beacon", "sibling"),
                    List.of("mast", "base"), List.of("tower", "middle"))) {
                String type = item.getFirst();
                assertTrue(rows.next());
                assertEquals(dataset + "-basket-2", rows.getString(1));
                assertEquals(dataset + "-" + type, rows.getString(2));
                assertEquals(type.equals("relay") ? "arelay" : type, rows.getString(3));
                assertEquals(item.getLast(), rows.getString(4));
                assertEquals(switch (type) { case "relay" -> "24"; case "tower" -> "12"; default -> null; },
                        rows.getString(5));
                if (type.equals("relay")) {
                    assertFalse(rows.getBoolean(6));
                    assertFalse(rows.wasNull());
                    assertStoredPoint(hierarchyGeometry(db, rows, "center"), MAST_CENTER);
                    var alignment = hierarchyGeometry(db, rows, "alignment");
                    assertCompoundCurve(alignment, STRAIGHT_ROUTE);
                    assertFalse(alignment.hasRemaining());
                    if (version == 24) {
                        var locations = hierarchyGeometry(db, rows, "sites");
                        assertEquals(MULTIPOINT, readWkbType(locations));
                        assertEquals(SURVEY_POINTS.size(), locations.getInt());
                        for (var point : SURVEY_POINTS) {
                            assertEquals(POINT, readWkbType(locations));
                            assertStoredPosition(locations, point);
                        }
                        assertFalse(locations.hasRemaining());
                    }
                } else {
                    assertNull(rows.getObject(6));
                    assertNull(rows.getBytes("center"));
                    assertNull(rows.getBytes("alignment"));
                }
                assertEquals(type.equals("beacon") ? "green" : null, rows.getString(7));
            }
            assertFalse(rows.next());
        }
        String service = " from " + db.table("servicebase") + " b join " + db.table("serviceunit")
                + " u on b.t_id=u.t_id and b.t_basket=u.t_basket join " + basket + " k on k.t_id=b.t_basket"
                + " where b.t_ili_tid='" + dataset + "-serviceunit' and k.t_ili_tid='" + dataset + "-basket-2'"
                + " and b.t_type='serviceunit' and b.servicecode='unit-1'";
        try (var statement = sql.createStatement(); var rows = statement.executeQuery("select u.label, "
                + geometryField(db, "b.servicepoint") + service)) {
            assertTrue(rows.next());
            assertEquals("active", rows.getString(1));
            assertStoredPoint(hierarchyGeometry(db, rows, "servicepoint"), SERVICE_POINT);
            assertFalse(rows.next());
        }
    }

    private static String geometryField(TestDatabase db, String field) {
        if (!db.postgis) return field;
        String name = field.substring(field.indexOf('.') + 1);
        return "st_asbinary(" + field + ") as " + name + ", st_srid(" + field + ") as " + name + "_srid";
    }

    private static ByteBuffer hierarchyGeometry(TestDatabase db, ResultSet rows, String name) throws SQLException {
        byte[] bytes = rows.getBytes(name);
        assertNotNull(bytes, name);
        var geometry = ByteBuffer.wrap(bytes);
        assertEquals(2056, db.postgis ? rows.getInt(name + "_srid") : readGeoPackageHeader(geometry), name);
        return geometry;
    }

    private static void assertStoredPoint(ByteBuffer geometry, List<String> position) {
        assertEquals(POINT, readWkbType(geometry));
        assertStoredPosition(geometry, position);
        assertFalse(geometry.hasRemaining());
    }

    private static void assertStoredStructureHierarchy(TestDatabase db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select n.t_id, n.t_type, n.t_seq, n.label, n.t_ili_tid, n.typedrecord_main, c.t_id as count_id,
                       f.t_id as flag_id, c.amount, f.enabled, b.t_ili_tid as bid
                from %s r join %s b on b.t_id=r.t_basket
                join %s n on coalesce(n.typedrecord_main, n.typedrecord_values)=r.t_id and n.t_basket=r.t_basket
                left join %s c on c.t_id=n.t_id and c.t_basket=n.t_basket
                left join %s f on f.t_id=n.t_id and f.t_basket=n.t_basket
                where r.t_ili_tid='%s-typed-1'
                order by case when n.typedrecord_main is not null then 0 else 1 end, n.t_seq
                """.formatted(db.table("typedrecord"), db.table("t_ili2db_basket"), db.table("notebase"),
                db.table("countnote"), db.table("flagnote"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            var types = List.of("countnote", "notebase", "countnote", "flagnote");
            var labels = List.of("main", "root", "count", "flag");
            for (int index = 0; index < types.size(); index++) {
                assertTrue(result.next());
                String type = types.get(index);
                String id = result.getString("t_id");
                assertEquals(dataset + "-basket-2", result.getString("bid"));
                assertEquals(type, result.getString("t_type"));
                assertEquals(labels.get(index), result.getString("label"));
                assertNull(result.getString("t_ili_tid"));
                assertEquals(Math.max(0, index - 1), result.getInt("t_seq"));
                assertEquals(index == 0, result.getObject("typedrecord_main") != null);
                assertEquals(type.equals("countnote") ? id : null, result.getString("count_id"));
                assertEquals(type.equals("flagnote") ? id : null, result.getString("flag_id"));
                assertEquals(index == 0 ? "5" : index == 2 ? "7" : null, result.getString("amount"));
                if (index == 3) {
                    assertTrue(result.getBoolean("enabled"));
                    assertFalse(result.wasNull());
                } else {
                    assertNull(result.getObject("enabled"));
                }
            }
            assertFalse(result.next());
        }

        String abstractRows = """
                select k.t_type, k.t_seq, k.label, k.t_ili_tid, n.acode
                from %s k join %s n on n.t_id=k.t_id and n.t_basket=k.t_basket
                join %s r on r.t_id=k.typedrecord_extras and r.t_basket=k.t_basket
                where r.t_ili_tid='%s-typed-1' order by k.t_seq
                """.formatted(db.table("notekind"), db.table("namednote"), db.table("typedrecord"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(abstractRows)) {
            for (int sequence = 0; sequence < 2; sequence++) {
                assertTrue(result.next());
                assertEquals("namednote", result.getString("t_type"));
                assertEquals(sequence, result.getInt("t_seq"));
                assertEquals("duplicate", result.getString("label"));
                assertNull(result.getString("t_ili_tid"));
                assertEquals("ABC", result.getString("acode"));
            }
            assertFalse(result.next());
        }
    }

    private static void assertStoredDataset(TestDatabase db, Connection sql, int version, String dataset, boolean nativeImport)
            throws SQLException {
        String point = db.postgis ? "st_srid(p.ageometry)" : "p.ageometry";
        String rows = "select p.t_ili_tid, b.t_ili_tid, p.aselect, " + point + " from " + db.table("place") + " p"
                + " join " + db.table("t_ili2db_basket") + " b on p.t_basket=b.t_id"
                + " join " + db.table("t_ili2db_dataset") + " d on b.dataset=d.t_id"
                + " where d.datasetname='" + dataset + "' order by p.t_ili_tid";
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals(dataset + "-place-1", result.getString(1));
            assertEquals(dataset + "-basket-1", result.getString(2));
            assertEquals("Brücke", result.getString(3));
            if (db.postgis) {
                srsId = result.getInt(4);
            } else {
                byte[] binary = result.getBytes(4);
                assertNotNull(binary);
                srsId = readGeoPackageHeader(ByteBuffer.wrap(binary));
            }
            assertTrue(result.next());
            assertEquals(dataset + "-place-2", result.getString(1));
            assertEquals(dataset + "-basket-1", result.getString(2));
            assertEquals(nativeImport ? "" : null, result.getString(3));
            assertNull(result.getObject(4));
            assertTrue(result.next());
            assertEquals(dataset + "-place-3", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getString(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, "place", "ageometry", srsId);
        assertStoredClassHierarchy(db, sql, version, dataset);
        assertStoredHierarchyRoles(db, sql, version, dataset);
        assertStoredAggregationAndComposition(db, sql, dataset);
        assertStoredHierarchyReferences(db, sql, version, dataset);
        assertStoredInheritedStructures(db, sql, dataset);
        assertStoredStructures(db, sql, dataset);
        assertStoredNestedStructures(db, sql, dataset);
        assertStoredRoles(db, sql, dataset);
        assertStoredStructureHierarchy(db, sql, dataset);
        assertStoredPolymorphicTargets(db, sql, dataset, version);
        assertStoredStructureReferences(db, sql, dataset);
        assertStoredPairing(db, sql, version, dataset);
        assertStoredStandaloneLinks(db, sql, dataset);
        assertStoredHierarchyLinks(db, sql, dataset);
        assertStoredRatingTags(db, sql, version, dataset);
        assertStoredEmbeddedLink(db, sql, "relayreview", "reviewingrelays", "mast", dataset + "-relay",
                "recommended", "servicebase", dataset + "-serviceunit", "accepted");
        assertStoredEmbeddedLink(db, sql, "rating", "ratingforms", "ratingform", dataset + "-ratingform-1",
                "rated", "mast", dataset + "-relay", "checked");
        assertStoredEmbeddedLink(db, sql, "rating", "ratingforms", "ratingform", dataset + "-ratingform-2",
                "rated", "mast", dataset + "-tower", null);
        assertStoredEmbeddedLink(db, sql, "rating", "ratingforms", "ratingform", dataset + "-ratingform-3",
                "rated", "mast", null, null);
        assertStoredTernaryLink(db, sql, dataset);
        assertStoredAssociationHierarchy(db, sql, dataset);
        assertStoredAttributedLinks(db, sql, dataset, "review", "reviewmarkers", "witness", "hub", "reviewed", nativeImport);
        assertStoredAttributedLinks(db, sql, dataset, "annotatedlink", "consultedmarker", "consultedreport", "report",
                "confirmed", nativeImport);
        assertStoredRoutes(db, sql, dataset, nativeImport);
        assertStoredSurfaces(db, sql, dataset, "plot", "footprint", SURFACE_WITH_HOLE, CURVED_SURFACE, nativeImport);
        assertStoredSurfaces(db, sql, dataset, "parcel", "boundary", WEST_AREA, EAST_AREA, nativeImport);
        if (version == 24) {
            for (String table : List.of("park", "district")) {
                assertStoredCollection(db, sql, dataset, table, "coverage", MULTISURFACE, nativeImport, geometry -> {
                    assertCurvePolygon(geometry, SURFACE_WITH_HOLE);
                    assertCurvePolygon(geometry, CURVED_SURFACE);
                });
            }
            assertStoredCollection(db, sql, dataset, "routegroup", "traces", MULTICURVE, nativeImport, geometry -> {
                assertCompoundCurve(geometry, STRAIGHT_ROUTE);
                assertCompoundCurve(geometry, MIXED_ROUTE);
            });
            assertStoredCollection(db, sql, dataset, "survey", "locations", MULTIPOINT, nativeImport, geometry -> {
                for (var surveyPoint : SURVEY_POINTS) {
                    assertEquals(POINT, readWkbType(geometry));
                    assertStoredPosition(geometry, surveyPoint);
                }
            });
        }
    }

    private static void assertStoredRoles(TestDatabase db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select m.t_ili_tid, m.aselect, m.aselect1, b.t_ili_tid as bid, m.t_basket as marker_basket,
                       h.t_id as hub_id, h.t_ili_tid as hub_tid, h.t_basket as hub_basket
                from %s m left join %s h on h.t_id=m.aselect1
                join %s b on b.t_id=m.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' order by m.t_ili_tid
                """.formatted(db.table("marker"), db.table("hub"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            for (int index = 0; index < MARKER_LABELS.size(); index++) {
                assertTrue(result.next());
                assertEquals(dataset + "-marker-" + (index + 1), result.getString("t_ili_tid"));
                assertEquals(MARKER_LABELS.get(index), result.getString("aselect"));
                if (index < 2) {
                    assertEquals(dataset + "-basket-1", result.getString("bid"));
                    assertEquals(dataset + "-hub-1", result.getString("hub_tid"));
                    assertEquals(result.getString("hub_id"), result.getString("aselect1"));
                    assertEquals(result.getString("marker_basket"), result.getString("hub_basket"));
                } else {
                    assertEquals(dataset + "-basket-2", result.getString("bid"));
                    assertNull(result.getString("aselect1"));
                }
            }
            assertFalse(result.next());
        }
    }

    private static void assertStoredHierarchyLinks(TestDatabase db, Connection sql, String dataset) throws SQLException {
        String mast = db.table("mast");
        String service = db.table("servicebase");
        String basket = db.table("t_ili2db_basket");
        String bare = """
                select m.t_ili_tid, s.t_ili_tid
                from %s l join %s m on m.t_id=l.workbranch and m.t_basket=l.t_basket
                join %s t on t.t_id=m.t_id and t.t_basket=l.t_basket
                join %s s on s.t_id=l.staffroot and s.t_basket=l.t_basket
                join %s b on b.t_id=l.t_basket where b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("hierarchylink"), mast, db.table("tower"), service, basket, dataset);
        String attributed = """
                select m.t_ili_tid, s.t_ili_tid, n.memo, n.severity
                from %s n join %s m on m.t_id=n.raisedat and m.t_basket=n.t_basket
                join %s s on s.t_id=n.sharedat and s.t_basket=n.t_basket
                join %s b on b.t_id=n.t_basket where b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("hierarchynote"), mast, service, basket, dataset);
        try (var statement = sql.createStatement()) {
            try (var row = statement.executeQuery(bare)) {
                assertTrue(row.next());
                assertEquals(dataset + "-relay", row.getString(1));
                assertEquals(dataset + "-serviceunit", row.getString(2));
                assertFalse(row.next());
            }
            try (var row = statement.executeQuery(attributed)) {
                assertTrue(row.next());
                assertEquals(dataset + "-beacon", row.getString(1));
                assertEquals(dataset + "-serviceunit", row.getString(2));
                assertEquals("reviewed", row.getString(3));
                assertEquals("major", row.getString(4));
                assertFalse(row.next());
            }
        }
    }

    private static void assertStoredTernaryLink(TestDatabase db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select a.t_ili_tid, p.t_ili_tid, m.t_ili_tid, d.memo, d.severity
                from %s d join %s a on a.t_id=d.dispatchleft and a.t_basket=d.t_basket
                join %s p on p.t_id=d.dispatchlocation and p.t_basket=d.t_basket
                join %s t on t.t_id=d.dispatchmast and t.t_basket=d.t_basket
                join %s m on m.t_id=t.t_id and m.t_basket=d.t_basket
                join %s b on b.t_id=d.t_basket where b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("dispatchrecord"), db.table("pairleft"), db.table("place"), db.table("tower"),
                db.table("mast"), db.table("t_ili2db_basket"), dataset);
        try (var statement = sql.createStatement(); var row = statement.executeQuery(rows)) {
            assertTrue(row.next());
            assertEquals(dataset + "-pairleft-1", row.getString(1));
            assertEquals(dataset + "-place-3", row.getString(2));
            assertEquals(dataset + "-relay", row.getString(3));
            assertEquals("dispatched", row.getString(4));
            assertEquals("minor", row.getString(5));
            assertFalse(row.next());
        }
    }

    private static void assertStoredStandaloneLinks(TestDatabase db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select l.t_id, b.t_ili_tid, m.t_ili_tid, h.t_ili_tid, l.t_basket, m.t_basket, h.t_basket
                from %s l join %s m on m.t_id=l.linkedmarker join %s h on h.t_id=l.linkedhub
                join %s b on b.t_id=l.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s'
                """.formatted(db.table("markerlink"), db.table("marker"), db.table("hub"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        var pairs = new HashSet<List<String>>();
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            while (result.next()) {
                assertTrue(result.getLong(1) > 0);
                assertEquals(dataset + "-basket-1", result.getString(2));
                assertEquals(result.getLong(5), result.getLong(6));
                assertEquals(result.getLong(5), result.getLong(7));
                assertTrue(pairs.add(List.of(result.getString(3), result.getString(4))));
            }
        }
        assertEquals(expectedMarkerLinks(dataset), pairs);
    }

    private static void assertStoredAssociationHierarchy(TestDatabase db, Connection sql, String dataset)
            throws SQLException {
        String rows = """
                select a.t_type, s.t_ili_tid, g.t_ili_tid, a.basenote, t.detailrank from %s a
                join %s t on t.t_id=a.t_id and t.t_basket=a.t_basket
                join %s s on s.t_id=a.listedsite and s.t_basket=a.t_basket
                join %s g on g.t_id=a.listedsigner and g.t_basket=a.t_basket
                join %s b on b.t_id=a.t_basket where b.t_ili_tid='%s-basket-2'
                """.formatted(db.table("allocationbase"), db.table("taggedallocation"), db.table("contractsite"),
                db.table("contractsigner"), db.table("t_ili2db_basket"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals("taggedallocation", result.getString(1));
            assertEquals(dataset + "-contract-site", result.getString(2));
            assertEquals(dataset + "-contract-signer", result.getString(3));
            assertEquals("base allocation", result.getString(4));
            assertEquals("17", result.getString(5));
            assertFalse(result.next());
        }
    }

    private static void assertStoredPairing(TestDatabase db, Connection sql, int version, String dataset) throws SQLException {
        String owner = version == 23 ? "pairright" : "pairleft";
        String target = version == 23 ? "pairleft" : "pairright";
        String column = version == 23 ? "leftlink" : "rightlink";
        String rows = """
                select o.t_ili_tid as owner_tid, o.%s as stored_id, t.t_id as target_id,
                       t.t_ili_tid as target_tid, o.t_basket as owner_basket, t.t_basket as target_basket, b.t_ili_tid as bid
                from %s o left join %s t on t.t_id=o.%s
                join %s b on b.t_id=o.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' order by o.t_ili_tid
                """.formatted(column, db.table(owner), db.table(target), column, db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals(dataset + "-" + owner + "-1", result.getString("owner_tid"));
            assertEquals(dataset + "-" + target + "-1", result.getString("target_tid"));
            assertEquals(result.getLong("target_id"), result.getLong("stored_id"));
            assertEquals(result.getLong("target_basket"), result.getLong("owner_basket"));
            assertEquals(dataset + "-basket-2", result.getString("bid"));
            if (version == 24) {
                assertTrue(result.next());
                assertEquals(dataset + "-pairleft-2", result.getString("owner_tid"));
                assertNull(result.getObject("stored_id"));
            }
            assertFalse(result.next());
        }
    }

    private static Set<List<String>> expectedMarkerLinks(String dataset) {
        return Set.of(List.of(dataset + "-marker-1", dataset + "-hub-1"),
                List.of(dataset + "-marker-1", dataset + "-hub-2"), List.of(dataset + "-marker-2", dataset + "-hub-1"));
    }

    private static void assertStoredRatingTags(TestDatabase db, Connection sql, int version, String dataset)
            throws SQLException {
        String owner = version == 23 ? "taggedform" : "taggedunit";
        String target = version == 23 ? "taggedunit" : "taggedform";
        String ownerRoot = version == 23 ? "ratingform" : "servicebase";
        String targetRoot = version == 23 ? "servicebase" : "ratingform";
        for (int index = 1; index <= 3; index++) {
            String form = dataset + "-ratingform-" + index;
            String unit = dataset + "-serviceunit" + (index == 1 ? "" : "-" + index);
            assertStoredEmbeddedLink(db, sql, "ratingtag", owner, ownerRoot, version == 23 ? form : unit,
                    target, targetRoot, index == 3 ? null : version == 23 ? unit : form, index == 1 ? "tagged" : null);
        }
    }

    private static void assertStoredEmbeddedLink(TestDatabase db, Connection sql, String table, String ownerColumn,
                                                 String ownerRoot, String ownerTid, String targetColumn,
                                                 String targetRoot, String targetTid, String memo) throws SQLException {
        String query = """
                select t.t_ili_tid, link.memo from %s link
                join %s o on o.t_id=link.%s and o.t_basket=link.t_basket
                left join %s t on t.t_id=link.%s and t.t_basket=link.t_basket
                where o.t_ili_tid='%s'
                """.formatted(db.table(table), db.table(ownerRoot), ownerColumn, db.table(targetRoot), targetColumn, ownerTid);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            if (targetTid != null) {
                assertTrue(rows.next(), ownerTid);
                assertEquals(targetTid, rows.getString(1));
                assertEquals(memo, rows.getString(2));
            }
            assertFalse(rows.next(), ownerTid);
        }
    }

    private static void assertStoredAttributedLinks(TestDatabase db, Connection sql, String dataset, String association,
                                                   String markerColumn, String targetColumn, String targetTable,
                                                   String memo, boolean nativeImport) throws SQLException {
        boolean annotated = association.equals("annotatedlink");
        String rows = """
                select r.t_id, b.t_ili_tid, m.t_ili_tid, h.t_ili_tid, r.t_basket, m.t_basket, h.t_basket, r.memo, r.grade, %s
                from %s r join %s m on m.t_id=r.%s join %s h on h.t_id=r.%s
                join %s b on b.t_id=r.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' order by m.t_ili_tid
                """.formatted(annotated ? "r.localdecision" : "null", db.table(association), db.table("marker"),
                markerColumn, db.table(targetTable), targetColumn, db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"),
                dataset);
        String emptyMemo = annotated && nativeImport ? "" : null;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            for (int index = 1; index <= 2; index++) {
                assertTrue(result.next());
                assertTrue(result.getLong(1) > 0);
                assertEquals(dataset + "-basket-1", result.getString(2));
                assertEquals(dataset + "-marker-" + index, result.getString(3));
                assertEquals(dataset + "-" + targetTable + "-1", result.getString(4));
                assertEquals(result.getLong(5), result.getLong(6));
                assertEquals(result.getLong(5), result.getLong(7));
                assertEquals(index == 1 ? memo : emptyMemo, result.getString(8));
                assertEquals(index == 1 ? "-3" : null, result.getString(9));
                assertEquals(index == 1 && annotated ? "approved" : null, result.getString(10));
            }
            assertFalse(result.next());
        }
    }

    private static void assertStoredInheritedStructures(TestDatabase db, Connection sql, String dataset)
            throws SQLException {
        String rootObjects = "('" + dataset + "-mast','" + dataset + "-relay','"
                + dataset + "-tower','" + dataset + "-beacon')";
        String notes = """
                select m.t_ili_tid, m.t_basket, l.t_basket, l.t_seq, l.amount, l.t_ili_tid,
                       l.nestedinner_main, l.nestedinner_values, l.nestedinner_extras
                from %s l join %s m on m.t_id=l.mast_notes
                where m.t_ili_tid in %s
                order by m.t_ili_tid, l.t_seq
                """.formatted(db.table("nestedleaf"), db.table("mast"), rootObjects);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(notes)) {
            for (var expected : List.of(List.of("mast", "0", "5"), List.of("mast", "1", "6"),
                    List.of("relay", "0", "7"))) {
                assertTrue(rows.next());
                assertEquals(dataset + "-" + expected.get(0), rows.getString(1));
                assertEquals(rows.getString(2), rows.getString(3));
                assertEquals(expected.get(1), rows.getString(4));
                assertEquals(expected.get(2), rows.getString(5));
                for (int column = 6; column <= 9; column++) assertNull(rows.getString(column));
            }
            assertFalse(rows.next());
        }

        String supplement = """
                select m.t_ili_tid, m.t_basket, t.t_basket, o.t_basket, i.t_basket, l.t_basket,
                       o.t_seq, i.t_seq, l.t_seq, i.headline, l.amount,
                       o.nestedrecord_tree, l.mast_notes, l.nestedinner_values, l.nestedinner_extras
                from %s m join %s t on t.t_id=m.t_id
                join %s o on o.tower_supplement=t.t_id
                join %s i on i.nestedouter_items=o.t_id
                join %s l on l.nestedinner_main=i.t_id
                where m.t_ili_tid in %s
                """.formatted(db.table("mast"), db.table("tower"), db.table("nestedouter"),
                db.table("nestedinner"), db.table("nestedleaf"), rootObjects);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(supplement)) {
            assertTrue(rows.next());
            assertEquals(dataset + "-relay", rows.getString(1));
            for (int column = 3; column <= 6; column++) assertEquals(rows.getString(2), rows.getString(column));
            for (int column = 7; column <= 9; column++) assertEquals("0", rows.getString(column));
            assertEquals("inherited", rows.getString(10));
            assertEquals("8", rows.getString(11));
            for (int column = 12; column <= 15; column++) assertNull(rows.getString(column));
            assertFalse(rows.next());
        }
    }

    private static void assertStoredNestedStructures(TestDatabase db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select r.t_ili_tid as root_tid, b.t_ili_tid as bid, r.t_basket as root_basket,
                       o.t_basket as outer_basket, o.t_ili_tid as outer_tid, o.t_seq as outer_seq,
                       i.t_id as inner_id, i.t_basket as inner_basket, i.t_ili_tid as inner_tid, i.t_seq as inner_seq,
                       i.headline, l.t_basket as leaf_basket, l.t_ili_tid as leaf_tid, l.t_seq as leaf_seq, l.amount,
                       l.nestedinner_main, l.nestedinner_values, l.nestedinner_extras
                from %s r join %s b on b.t_id=r.t_basket
                left join %s o on o.nestedrecord_tree=r.t_id
                left join %s i on i.nestedouter_items=o.t_id
                left join %s l on coalesce(l.nestedinner_main, l.nestedinner_values, l.nestedinner_extras)=i.t_id
                where r.t_ili_tid in ('%s-nested-1', '%s-nested-2')
                order by r.t_ili_tid, i.t_seq,
                         case when l.nestedinner_main is not null then 0
                              when l.nestedinner_values is not null then 1 else 2 end, l.t_seq
                """.formatted(db.table("nestedrecord"), db.table("t_ili2db_basket"), db.table("nestedouter"),
                db.table("nestedinner"), db.table("nestedleaf"), dataset, dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            for (var values : List.of(Map.entry("main", List.of("1")), Map.entry("values", List.of("2", "3", "2")),
                    Map.entry("extras", List.of("4", "4")))) {
                for (int sequence = 0; sequence < values.getValue().size(); sequence++) {
                    assertTrue(result.next());
                    assertEquals(dataset + "-nested-1", result.getString("root_tid"));
                    assertEquals(dataset + "-basket-2", result.getString("bid"));
                    assertEquals(result.getString("root_basket"), result.getString("outer_basket"));
                    assertEquals(result.getString("outer_basket"), result.getString("inner_basket"));
                    assertEquals(result.getString("inner_basket"), result.getString("leaf_basket"));
                    assertNull(result.getString("outer_tid"));
                    assertNull(result.getString("inner_tid"));
                    assertNull(result.getString("leaf_tid"));
                    assertEquals("0", result.getString("outer_seq"));
                    assertEquals("0", result.getString("inner_seq"));
                    assertEquals("populated", result.getString("headline"));
                    for (String field : List.of("main", "values", "extras")) {
                        assertEquals(field.equals(values.getKey()) ? result.getString("inner_id") : null,
                                result.getString("nestedinner_" + field));
                    }
                    assertEquals(Integer.toString(sequence), result.getString("leaf_seq"));
                    assertEquals(values.getValue().get(sequence), result.getString("amount"));
                }
            }
            assertTrue(result.next());
            assertEquals(dataset + "-nested-1", result.getString("root_tid"));
            assertEquals("empty", result.getString("headline"));
            assertEquals("1", result.getString("inner_seq"));
            assertNull(result.getString("leaf_basket"));
            assertTrue(result.next());
            assertEquals(dataset + "-nested-2", result.getString("root_tid"));
            assertEquals(dataset + "-basket-2", result.getString("bid"));
            assertNull(result.getString("outer_basket"));
            assertFalse(result.next());
        }
    }

    private static void assertStoredStructures(TestDatabase db, Connection sql, String dataset) throws SQLException {
        String rows = """
                select d.t_id as detail_id, d.t_basket as detail_basket, d.t_ili_tid as detail_tid, d.t_seq,
                       d.report_summary, d.report_entries, d.report_observations, d.label, d.enabled, d.quantity, d.decision,
                       r.t_id as report_id, r.t_basket as report_basket, r.t_ili_tid as report_tid, b.t_ili_tid as bid
                from %s d join %s r on r.t_id=coalesce(d.report_summary, d.report_entries, d.report_observations)
                join %s b on b.t_id=r.t_basket join %s s on s.t_id=b.dataset
                where s.datasetname='%s'
                order by r.t_ili_tid,
                         case when d.report_summary is not null then 0 when d.report_entries is not null then 1 else 2 end,
                         d.t_seq
                """.formatted(db.table("detail"), db.table("report"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            String firstReport = dataset + "-report-1";
            String firstBasket = dataset + "-basket-1";
            assertStoredDetail(result, firstReport, firstBasket, "report_summary", 0, SUMMARY);
            for (int index = 0; index < ENTRIES.size(); index++) {
                assertStoredDetail(result, firstReport, firstBasket, "report_entries", index, ENTRIES.get(index));
            }
            for (int index = 0; index < OBSERVATIONS.size(); index++) {
                assertStoredDetail(result, firstReport, firstBasket, "report_observations", index, OBSERVATIONS.get(index));
            }
            assertStoredDetail(result, dataset + "-report-2", dataset + "-basket-2", "report_entries", 0, LATER_ENTRY);
            assertFalse(result.next());
        }
    }

    private static void assertStoredDetail(ResultSet result, String tid, String bid, String owner, int sequence,
                                           DetailValue expected) throws SQLException {
        assertTrue(result.next());
        assertTrue(result.getLong("detail_id") > 0);
        assertEquals(result.getString("report_basket"), result.getString("detail_basket"));
        assertNull(result.getString("detail_tid"));
        assertEquals(tid, result.getString("report_tid"));
        assertEquals(bid, result.getString("bid"));
        assertEquals(result.getString("report_id"), result.getString(owner));
        for (String column : List.of("report_summary", "report_entries", "report_observations")) {
            if (!owner.equals(column)) assertNull(result.getString(column), column);
        }
        assertEquals(Integer.toString(sequence), result.getString("t_seq"));
        boolean enabled = result.getBoolean("enabled");
        Boolean optionalEnabled = result.wasNull() ? null : enabled;
        int quantity = result.getInt("quantity");
        Integer optionalQuantity = result.wasNull() ? null : quantity;
        assertEquals(expected, new DetailValue(result.getString("label"), optionalEnabled, optionalQuantity,
                result.getString("decision")));
    }

    private static void assertStoredRoutes(TestDatabase db, Connection sql, String dataset, boolean nativeImport)
            throws SQLException {
        String rows = geometryRows(db, dataset, "route", "trace");
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            String firstBasket = dataset + "-basket-1";
            srsId = assertStoredRoute(db, result, dataset + "-route-1", firstBasket, STRAIGHT_ROUTE, false);
            assertTrue(result.next());
            assertEquals(dataset + "-route-2", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertEquals(srsId, assertStoredRoute(db, result, dataset + "-route-3", firstBasket, ARC_ROUTE, nativeImport));
            assertEquals(srsId, assertStoredRoute(db, result, dataset + "-route-4", firstBasket, MIXED_ROUTE, nativeImport));
            assertFalse(result.next());
        }
        assertSrs(db, sql, "route", "trace", srsId);
    }

    private static int assertStoredRoute(TestDatabase db, ResultSet result, String tid, String bid,
                                         List<CurveComponent> components, boolean withoutEnvelope) throws SQLException {
        var geometry = readStoredGeometry(result, tid, bid);
        int srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
        if (!db.postgis && withoutEnvelope) assertEquals(8, geometry.position(), "Curved GeoPackage header length");
        assertCompoundCurve(geometry, components);
        assertFalse(geometry.hasRemaining());
        return srsId;
    }

    private static void assertStoredSurfaces(TestDatabase db, Connection sql, String dataset, String table, String column,
                                             List<List<CurveComponent>> firstRings,
                                             List<List<CurveComponent>> secondRings, boolean nativeImport)
            throws SQLException {
        String rows = geometryRows(db, dataset, table, column);
        String tidPrefix = dataset + "-" + table;
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            String firstBasket = dataset + "-basket-1";
            srsId = assertStoredSurface(db, result, tidPrefix + "-1", firstBasket, firstRings, nativeImport);
            assertEquals(srsId,
                    assertStoredSurface(db, result, tidPrefix + "-2", firstBasket, secondRings, nativeImport));
            assertTrue(result.next());
            assertEquals(tidPrefix + "-3", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, table, column, srsId);
    }

    private static int assertStoredSurface(TestDatabase db, ResultSet result, String tid, String bid,
                                          List<List<CurveComponent>> rings, boolean nativeImport) throws SQLException {
        var geometry = readStoredGeometry(result, tid, bid);
        int srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
        if (!db.postgis && nativeImport) assertEquals(8, geometry.position(), "Surface GeoPackage header length");
        assertCurvePolygon(geometry, rings);
        assertFalse(geometry.hasRemaining());
        return srsId;
    }

    private static void assertStoredCollection(TestDatabase db, Connection sql, String dataset, String table,
                                               String column, int type, boolean nativeImport,
                                               Consumer<ByteBuffer> assertMembers) throws SQLException {
        String rows = geometryRows(db, dataset, table, column);
        String tidPrefix = dataset + "-" + table;
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            var geometry = readStoredGeometry(result, tidPrefix + "-1", dataset + "-basket-1");
            srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
            if (!db.postgis && nativeImport) assertEquals(8, geometry.position(), "Collection GeoPackage header length");
            assertEquals(type, readWkbType(geometry));
            assertEquals(2, geometry.getInt());
            assertMembers.accept(geometry);
            assertFalse(geometry.hasRemaining());
            assertTrue(result.next());
            assertEquals(tidPrefix + "-2", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, table, column, srsId);
    }

    private static String geometryRows(TestDatabase db, String dataset, String table, String column) {
        String field = "o." + column;
        String geometry = db.postgis ? "st_asbinary(" + field + "), st_srid(" + field + ")" : field;
        return "select o.t_ili_tid, b.t_ili_tid, " + geometry + " from " + db.table(table) + " o"
                + " join " + db.table("t_ili2db_basket") + " b on o.t_basket=b.t_id"
                + " join " + db.table("t_ili2db_dataset") + " d on b.dataset=d.t_id"
                + " where d.datasetname='" + dataset + "' order by o.t_ili_tid";
    }

    private static void assertCurvePolygon(ByteBuffer geometry, List<List<CurveComponent>> rings) {
        assertEquals(CURVEPOLYGON, readWkbType(geometry));
        assertEquals(rings.size(), geometry.getInt());
        for (var ring : rings) assertCompoundCurve(geometry, ring);
    }

    private static ByteBuffer readStoredGeometry(ResultSet result, String tid, String bid) throws SQLException {
        assertTrue(result.next());
        assertEquals(tid, result.getString(1));
        assertEquals(bid, result.getString(2));
        byte[] bytes = result.getBytes(3);
        assertNotNull(bytes);
        return ByteBuffer.wrap(bytes);
    }

    private static void assertCompoundCurve(ByteBuffer geometry, List<CurveComponent> components) {
        assertEquals(COMPOUNDCURVE, readWkbType(geometry));
        assertEquals(components.size(), geometry.getInt());
        for (var component : components) {
            assertEquals(component.type(), readWkbType(geometry));
            assertEquals(component.vertices().size(), geometry.getInt());
            for (var vertex : component.vertices()) {
                assertStoredPosition(geometry, vertex);
            }
        }
    }

    private static void assertStoredPosition(ByteBuffer geometry, List<String> position) {
        for (String ordinate : position) {
            assertEquals(Double.parseDouble(ordinate), geometry.getDouble());
        }
    }

    private static int readGeoPackageHeader(ByteBuffer bytes) {
        assertEquals('G', bytes.get());
        assertEquals('P', bytes.get());
        assertEquals(0, bytes.get());
        int flags = Byte.toUnsignedInt(bytes.get());
        assertTrue(flags <= 3, "Expected a nonempty standard 2D GeoPackage geometry");
        bytes.order((flags & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int srs = bytes.getInt();
        if ((flags & 2) != 0) bytes.position(bytes.position() + 4 * Double.BYTES);
        return srs;
    }

    private static int readWkbType(ByteBuffer bytes) {
        int order = bytes.get();
        assertTrue(order == 0 || order == 1, "Invalid WKB byte order");
        bytes.order(order == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return bytes.getInt();
    }

    private static void assertSrs(TestDatabase db, Connection sql, String table, String column, int srsId)
            throws SQLException {
        if (db.postgis) {
            assertEquals(2056, srsId);
            return;
        }
        assertEquals("2056", scalar(sql, "select organization_coordsys_id from gpkg_spatial_ref_sys"
                + " where organization='EPSG' and srs_id=" + srsId));
        assertEquals(Integer.toString(srsId), scalar(sql, "select srs_id from gpkg_geometry_columns"
                + " where table_name='" + table + "' and column_name='" + column + "'"));
    }

    private static void assertTransfer(Path output, int version, String dataset, boolean hasEmptyText) throws Exception {
        var data = readDataSection(output, version);
        Map<String, Set<String>> baskets = new HashMap<>();
        Map<String, Element> objects = new HashMap<>();
        var markerLinks = new HashSet<List<String>>();
        var annotatedLinks = new HashSet<AnnotatedPair>();
        String markerLinkName = version == 24 ? "MarkerLink" : "DbInterop23.Data.MarkerLink";
        String hierarchyPrefix = version == 24 ? "" : "DbInterop23.Data.";
        var hierarchyLinks = new HashSet<String>();
        String annotatedLinkName = version == 24 ? "AnnotatedLink" : "DbInterop23.Data.AnnotatedLink";
        String aggregationName = version == 24 ? "ServiceAggregation" : "DbInterop23.Data.ServiceAggregation";
        int aggregations = 0;
        String allocationName = version == 24 ? "TaggedAllocation" : "DbInterop23.Data.TaggedAllocation";
        int allocations = 0;
        for (Element basket : children(data)) {
            assertEquals(version == 24 ? "Data" : "DbInterop23.Data", basket.getLocalName());
            String bid = id(basket, version, "bid");
            var tids = new java.util.HashSet<String>();
            for (Element object : children(basket)) {
                String tid = id(object, version, "tid");
                if (object.getLocalName().equals(markerLinkName)) {
                    assertEquals("", tid);
                    assertEquals(dataset + "-basket-1", bid);
                    assertTrue(markerLinks.add(transferredMarkerLink(object, version)));
                    continue;
                }
                if (object.getLocalName().equals(annotatedLinkName)) {
                    assertEquals("", tid);
                    assertEquals(dataset + "-basket-1", bid);
                    assertTrue(annotatedLinks.add(transferredAnnotatedLink(object, version)));
                    continue;
                }
                if (object.getLocalName().equals(hierarchyPrefix + "HierarchyLink")) {
                    assertTrue(hierarchyLinks.add("HierarchyLink"));
                    assertEquals(dataset + "-basket-2", bid);
                    assertEquals("", tid);
                    assertEquals(List.of("WorkBranch", "StaffRoot"),
                            children(object).stream().map(Element::getLocalName).toList());
                    assertLocalReference(object, "WorkBranch", version, dataset + "-relay");
                    assertLocalReference(object, "StaffRoot", version, dataset + "-serviceunit");
                    continue;
                }
                if (object.getLocalName().equals(hierarchyPrefix + "HierarchyNote")) {
                    assertTrue(hierarchyLinks.add("HierarchyNote"));
                    assertEquals(dataset + "-basket-2", bid);
                    assertEquals("", tid);
                    assertEquals(List.of("RaisedAt", "SharedAt", "Memo", "Severity"),
                            children(object).stream().map(Element::getLocalName).toList());
                    assertLocalReference(object, "RaisedAt", version, dataset + "-beacon");
                    assertLocalReference(object, "SharedAt", version, dataset + "-serviceunit");
                    assertEquals("reviewed", text(object, "Memo"));
                    assertEquals("major", text(object, "Severity"));
                    continue;
                }
                if (object.getLocalName().equals(aggregationName)) {
                    assertEquals("", tid);
                    assertEquals(dataset + "-basket-2", bid);
                    assertLocalReference(object, "GroupWhole", version, dataset + "-refcarrier");
                    assertLocalReference(object, "GroupPart", version, dataset + "-serviceunit");
                    aggregations++;
                    continue;
                }
                if (object.getLocalName().equals(hierarchyPrefix + "DispatchRecord")) {
                    assertTrue(hierarchyLinks.add("DispatchRecord"));
                    assertEquals(dataset + "-basket-2", bid);
                    assertEquals("", tid);
                    assertEquals(List.of("DispatchLeft", "DispatchLocation", "DispatchMast", "Memo", "Severity"),
                            children(object).stream().map(Element::getLocalName).toList());
                    assertLocalReference(object, "DispatchLeft", version, dataset + "-pairleft-1");
                    assertLocalReference(object, "DispatchLocation", version, dataset + "-place-3");
                    assertLocalReference(object, "DispatchMast", version, dataset + "-relay");
                    assertEquals("dispatched", text(object, "Memo"));
                    assertEquals("minor", text(object, "Severity"));
                    continue;
                }
                if (object.getLocalName().equals(allocationName)) {
                    assertEquals("", tid);
                    assertEquals(dataset + "-basket-2", bid);
                    assertLocalReference(object, "ListedSite", version, dataset + "-contract-site");
                    assertLocalReference(object, "ListedSigner", version, dataset + "-contract-signer");
                    assertEquals("base allocation", text(object, "BaseNote"));
                    assertEquals("17", text(object, "DetailRank"));
                    allocations++;
                    continue;
                }
                assertNull(objects.put(tid, object));
                tids.add(tid);
            }
            assertNull(baskets.put(bid, tids));
        }
        assertEquals(Set.of("HierarchyLink", "HierarchyNote", "DispatchRecord"), hierarchyLinks);
        assertEquals(expectedMarkerLinks(dataset), markerLinks);
        assertEquals(1, aggregations);
        assertEquals(1, allocations);
        assertEquals(Set.of(new AnnotatedPair(dataset + "-marker-1", dataset + "-report-1", "confirmed", "-3", "approved"),
                new AnnotatedPair(dataset + "-marker-2", dataset + "-report-1", hasEmptyText ? "" : null, null, null)),
                annotatedLinks);
        String first = dataset + "-place-1";
        String second = dataset + "-place-2";
        String third = dataset + "-place-3";
        String link = dataset + "-link";
        String line = dataset + "-route-1";
        String noLine = dataset + "-route-2";
        String arcs = dataset + "-route-3";
        String mixed = dataset + "-route-4";
        String plotWithHole = dataset + "-plot-1";
        String curvedPlot = dataset + "-plot-2";
        String noSurface = dataset + "-plot-3";
        String westArea = dataset + "-parcel-1";
        String eastArea = dataset + "-parcel-2";
        String noArea = dataset + "-parcel-3";
        String firstReport = dataset + "-report-1";
        String secondReport = dataset + "-report-2";
        var firstBasket = new HashSet<>(Set.of(first, second, line, arcs, mixed, plotWithHole, curvedPlot,
                westArea, eastArea, firstReport, dataset + "-marker-1", dataset + "-marker-2", dataset + "-hub-1",
                dataset + "-hub-2"));
        var secondBasket = new HashSet<>(Set.of(third, noLine, noSurface, noArea, secondReport, dataset + "-marker-3",
                dataset + "-nested-1", dataset + "-nested-2", dataset + "-typed-1", dataset + "-typed-2",
                dataset + "-pairleft-1", dataset + "-pairleft-2", dataset + "-pairright-1"));
        secondBasket.addAll(List.of(dataset + "-contract-site", dataset + "-contract-signer"));
        if (version == 24) {
            firstBasket.add(link);
            for (String table : List.of("park", "district", "routegroup", "survey")) {
                firstBasket.add(dataset + "-" + table + "-1");
                secondBasket.add(dataset + "-" + table + "-2");
            }
        }
        for (String name : List.of("mast", "tower", "relay", "beacon", "serviceunit")) {
            secondBasket.add(dataset + "-" + name);
        }
        for (int index = 1; index <= 3; index++) secondBasket.add(dataset + "-ratingform-" + index);
        secondBasket.addAll(List.of(dataset + "-serviceunit-2", dataset + "-serviceunit-3"));
        secondBasket.add(dataset + "-refcarrier");
        assertTransferredClassHierarchy(objects, version, dataset);
        secondBasket.add(dataset + "-ref-notebook");
        if (version == 24) {
            secondBasket.add(dataset + "-ref-asset-1");
            secondBasket.add(dataset + "-ref-asset-2");
        }
        assertTransferredHierarchyReferences(objects, version, dataset);
        assertEquals(Map.of(dataset + "-basket-1", firstBasket, dataset + "-basket-2", secondBasket), baskets);
        assertTransferredReports(objects.get(firstReport), objects.get(secondReport), version);
        assertTransferredNestedTree(objects.get(dataset + "-nested-1"), version, List.of("2", "3", "2"));
        assertTrue(children(objects.get(dataset + "-nested-2")).isEmpty());
        assertTransferredStructureHierarchy(objects.get(dataset + "-typed-1"), version);
        assertTrue(children(objects.get(dataset + "-typed-2")).isEmpty());
        assertTransferredPolymorphicTargets(objects.get(dataset + "-refcarrier"), version, dataset);
        assertTransferredStructureReferences(objects.get(secondReport), version, dataset);
        assertTransferredRoles(objects, version, dataset);
        assertEmbeddedLink(objects.get(dataset + "-ratingform-1"), "Rated", dataset + "-relay", "Rating", version, "checked");
        assertEmbeddedLink(objects.get(dataset + "-ratingform-2"), "Rated", dataset + "-tower", "Rating", version, null);
        assertEmbeddedLink(objects.get(dataset + "-ratingform-3"), "Rated", null, "Rating", version, null);
        assertEmbeddedLink(objects.get(dataset + "-relay"), "Recommended", dataset + "-serviceunit",
                "RelayReview", version, "accepted");
        assertNull(child(objects.get(dataset + "-beacon"), "Recommended"));
        assertTransferredRatingTags(objects, version, dataset);
        assertTransferredReviews(objects, version, dataset);
        assertTransferredPairing(objects, version, dataset);

        String placeName = version == 24 ? "Place" : "DbInterop23.Data.Place";
        for (String tid : List.of(first, second, third)) {
            assertEquals(placeName, objects.get(tid).getLocalName());
        }
        Element place = objects.get(first);
        assertEquals("Brücke", child(place, "Select").getTextContent());
        assertEquals("true", child(place, "Active").getTextContent());
        assertEquals("-7", child(place, "Count").getTextContent());
        assertEquals("approved", child(place, "Status").getTextContent());
        var coord = children(child(place, "Geometry")).getFirst();
        assertEquals(0, new BigDecimal("2600000.125").compareTo(new BigDecimal(children(coord).getFirst().getTextContent())));
        assertEquals(0, new BigDecimal("1200000.375").compareTo(new BigDecimal(children(coord).get(1).getTextContent())));
        Element empty = child(objects.get(second), "Select");
        if (hasEmptyText) {
            assertNotNull(empty);
            assertEquals("", empty.getTextContent());
        } else {
            assertNull(empty);
        }
        assertEquals("false", child(objects.get(second), "Active").getTextContent());
        assertEquals(List.of(), children(objects.get(third)));
        if (version == 24) {
            assertEquals("Link", objects.get(link).getLocalName());
            assertEquals(first, id(child(objects.get(link), "Target"), version, "ref"));
        }
        String routeName = version == 24 ? "Route" : "DbInterop23.Data.Route";
        for (String tid : List.of(line, noLine, arcs, mixed)) {
            assertEquals(routeName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noLine), "Trace"));
        assertTransferRoute(child(objects.get(line), "Trace"), version, STRAIGHT_ROUTE);
        assertTransferRoute(child(objects.get(arcs), "Trace"), version, ARC_ROUTE);
        assertTransferRoute(child(objects.get(mixed), "Trace"), version, MIXED_ROUTE);
        String plotName = version == 24 ? "Plot" : "DbInterop23.Data.Plot";
        for (String tid : List.of(plotWithHole, curvedPlot, noSurface)) {
            assertEquals(plotName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noSurface), "Footprint"));
        assertTransferSurface(child(objects.get(plotWithHole), "Footprint"), version, SURFACE_WITH_HOLE);
        assertTransferSurface(child(objects.get(curvedPlot), "Footprint"), version, CURVED_SURFACE);
        String parcelName = version == 24 ? "Parcel" : "DbInterop23.Data.Parcel";
        for (String tid : List.of(westArea, eastArea, noArea)) {
            assertEquals(parcelName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noArea), "Boundary"));
        assertTransferSurface(child(objects.get(westArea), "Boundary"), version, WEST_AREA);
        assertTransferSurface(child(objects.get(eastArea), "Boundary"), version, EAST_AREA);
        if (version == 24) {
            for (String name : List.of("Park", "District")) {
                var surfaces = assertTransferCollection(objects, dataset, name, "Coverage", "multisurface", "surface");
                assertTransferRings(surfaces.getFirst(), 24, SURFACE_WITH_HOLE);
                assertTransferRings(surfaces.getLast(), 24, CURVED_SURFACE);
            }
            var lines = assertTransferCollection(objects, dataset, "RouteGroup", "Traces", "multipolyline", "polyline");
            assertTransferPolyline(lines.getFirst(), 24, STRAIGHT_ROUTE);
            assertTransferPolyline(lines.getLast(), 24, MIXED_ROUTE);
            var points = assertTransferCollection(objects, dataset, "Survey", "Locations", "multicoord", "coord");
            for (int index = 0; index < points.size(); index++) {
                var point = points.get(index);
                assertEquals(2, children(point).size());
                assertTransferPosition(point, "c", SURVEY_POINTS.get(index));
            }
        }
    }

    private static List<String> transferredMarkerLink(Element object, int version) {
        var roles = children(object).stream().map(Element::getLocalName).toList();
        assertEquals(version == 24 ? List.of("LinkedHub", "LinkedMarker") : List.of("LinkedMarker", "LinkedHub"), roles);
        var marker = child(object, "LinkedMarker");
        var hub = child(object, "LinkedHub");
        assertEquals("", id(marker, version, "bid"));
        assertEquals("", id(hub, version, "bid"));
        return List.of(id(marker, version, "ref"), id(hub, version, "ref"));
    }

    private static AnnotatedPair transferredAnnotatedLink(Element object, int version) {
        var fields = children(object).stream().map(Element::getLocalName).toList();
        var expectedFields = new ArrayList<>(List.of("ConsultedMarker", "ConsultedReport"));
        for (String field : List.of("Memo", "Grade", "LocalDecision")) {
            if (child(object, field) != null) expectedFields.add(field);
        }
        assertEquals(expectedFields, fields);
        return new AnnotatedPair(id(child(object, "ConsultedMarker"), version, "ref"),
                id(child(object, "ConsultedReport"), version, "ref"), text(object, "Memo"), text(object, "Grade"),
                text(object, "LocalDecision"));
    }

    private static void assertTransferredRoles(Map<String, Element> objects, int version, String dataset) {
        String prefix = version == 23 ? "DbInterop23.Data." : "";
        String hub = dataset + "-hub-1";
        assertEquals(prefix + "Hub", objects.get(hub).getLocalName());
        assertEquals(prefix + "Hub", objects.get(dataset + "-hub-2").getLocalName());
        for (int index = 0; index < MARKER_LABELS.size(); index++) {
            var marker = objects.get(dataset + "-marker-" + (index + 1));
            assertEquals(prefix + "Marker", marker.getLocalName());
            assertEquals(MARKER_LABELS.get(index), text(marker, "ASelect"));
            var role = child(marker, "Select");
            if (index < 2) {
                assertNotNull(role);
                assertEquals(hub, id(role, version, "ref"));
                assertEquals("", id(role, version, "bid"));
                assertEquals(List.of(), children(role));
            } else {
                assertNull(role);
            }
        }
    }

    private static void assertTransferredRatingTags(Map<String, Element> objects, int version, String dataset) {
        for (int index = 1; index <= 3; index++) {
            String form = dataset + "-ratingform-" + index;
            String unit = dataset + "-serviceunit" + (index == 1 ? "" : "-" + index);
            String owner = version == 23 ? form : unit;
            String target = index == 3 ? null : version == 23 ? unit : form;
            assertEmbeddedLink(objects.get(owner), version == 23 ? "TaggedUnit" : "TaggedForm", target,
                    "RatingTag", version, index == 1 ? "tagged" : null);
            assertNull(child(objects.get(version == 23 ? unit : form), version == 23 ? "TaggedForm" : "TaggedUnit"));
        }
    }

    private static void assertEmbeddedLink(Element owner, String role, String targetTid, String association,
                                           int version, String memo) {
        var element = child(owner, role);
        if (targetTid == null) {
            assertNull(element, role);
            return;
        }
        assertLocalReference(owner, role, version, targetTid);
        if (memo == null) {
            assertTrue(children(element).isEmpty(), role);
        } else {
            var payload = children(element);
            assertEquals(1, payload.size());
            String name = version == 23 ? "DbInterop23.Data." + association : association;
            assertEquals(name, payload.getFirst().getLocalName());
            assertEquals(memo, text(payload.getFirst(), "Memo"));
        }
    }

    private static void assertTransferredReviews(Map<String, Element> objects, int version, String dataset) {
        String hub = dataset + "-hub-1";
        var first = child(objects.get(dataset + "-marker-1"), "Witness");
        assertNotNull(first);
        assertEquals(hub, id(first, version, "ref"));
        assertEquals(1, children(first).size());
        var payload = children(first).getFirst();
        assertEquals(version == 24 ? "Review" : "DbInterop23.Data.Review", payload.getLocalName());
        assertEquals("reviewed", text(payload, "Memo"));
        assertEquals("-3", text(payload, "Grade"));

        var second = child(objects.get(dataset + "-marker-2"), "Witness");
        assertNotNull(second);
        assertEquals(hub, id(second, version, "ref"));
        assertEquals(List.of(), children(second));
        assertNull(child(objects.get(dataset + "-marker-3"), "Witness"));
    }

    private static void assertTransferredClassHierarchy(Map<String, Element> objects, int version, String dataset) {
        String prefix = version == 23 ? "DbInterop23.Data." : "";
        var mast = objects.get(dataset + "-mast");
        var relay = objects.get(dataset + "-relay");
        assertClassFields(mast, prefix + "Mast", Map.of("Caption", "base"), "Notes");
        assertClassFields(objects.get(dataset + "-tower"), prefix + "Tower", Map.of("Caption", "middle", "Height", "12"));
        assertClassFields(relay, prefix + "Relay", Map.of("Caption", "leaf", "Height", "24", "Online", "false"),
                "Notes", "Supplement", "Center", "Alignment", "Sites", "Mentor", "PairedService", "Recommended");
        assertLocalReference(relay, "Mentor", version, dataset + "-relay");
        assertTransferClassPoint(relay, "Center", version, MAST_CENTER);
        assertTransferRoute(child(relay, "Alignment"), version, STRAIGHT_ROUTE);
        if (version == 24) {
            var sites = child(relay, "Sites");
            assertNotNull(sites);
            var multicoord = child(sites, "multicoord");
            assertNotNull(multicoord);
            var points = children(multicoord);
            assertEquals(List.of("coord", "coord"), points.stream().map(Element::getLocalName).toList());
            for (int index = 0; index < points.size(); index++) {
                assertTransferPosition(points.get(index), "c", SURVEY_POINTS.get(index));
            }
        } else {
            assertNull(child(relay, "Sites"));
        }
        assertTransferredAmounts(mast, "Notes", version, prefix, List.of("5", "6"));
        assertTransferredAmounts(relay, "Notes", version, prefix, List.of("7"));
        var supplement = transferredStructures(relay, "Supplement");
        assertEquals(List.of(prefix + "NestedOuter"), supplement.stream().map(Element::getLocalName).toList());
        var items = transferredStructures(supplement.getFirst(), "Items");
        assertEquals(List.of(prefix + "NestedInner"), items.stream().map(Element::getLocalName).toList());
        var inner = items.getFirst();
        assertEquals("inherited", text(inner, "Headline"));
        assertTransferredAmounts(inner, "Main", version, prefix, List.of("8"));
        assertNull(child(inner, "Values"));
        assertNull(child(inner, "Extras"));
        assertClassFields(objects.get(dataset + "-beacon"), prefix + "Beacon", Map.of("Caption", "sibling", "Signal", "green"));
        var serviceUnit = objects.get(dataset + "-serviceunit");
        assertClassFields(serviceUnit, prefix + "ServiceUnit", Map.of("ServiceCode", "unit-1", "Label", "active"),
                "ServicePoint", "Supervisor", "PairedTower", "TaggedForm", "WholeMast");
        assertLocalReference(serviceUnit, "Supervisor", version, dataset + "-relay");
        assertLocalReference(serviceUnit, "WholeMast", version, dataset + "-beacon");
        if (version == 23) {
            assertLocalReference(relay, "PairedService", version, dataset + "-serviceunit");
            assertNull(child(serviceUnit, "PairedTower"));
        } else {
            assertLocalReference(serviceUnit, "PairedTower", version, dataset + "-relay");
            assertNull(child(relay, "PairedService"));
        }
        assertTransferClassPoint(serviceUnit, "ServicePoint", version, SERVICE_POINT);
    }

    private static void assertTransferredHierarchyReferences(Map<String, Element> objects, int version, String dataset) {
        var book = objects.get(dataset + "-ref-notebook");
        assertNotNull(book);
        var entries = transferredStructures(book, "Entries");
        String note = version == 23 ? "DbInterop23.Data.RefNoteLeaf" : "RefNoteLeaf";
        assertEquals(List.of(note, note), entries.stream().map(Element::getLocalName).toList());
        assertTrue(entries.stream().allMatch(entry -> id(entry, version, "tid").isEmpty()));
        assertTransferredReferenceFields(entries.getFirst(), entries.getLast(), version, dataset);
        if (version == 24) {
            var populated = objects.get(dataset + "-ref-asset-1");
            var empty = objects.get(dataset + "-ref-asset-2");
            assertNotNull(populated);
            assertNotNull(empty);
            assertEquals("RefAssetLeaf", populated.getLocalName());
            assertEquals("RefAssetLeaf", empty.getLocalName());
            assertTransferredReferenceFields(populated, empty, version, dataset);
        }
    }

    private static void assertTransferredReferenceFields(Element populated, Element empty, int version, String dataset) {
        assertEquals("populated", text(populated, "Label"));
        assertEquals("empty", text(empty, "Label"));
        for (var field : Map.of("RootTarget", "beacon", "BranchTarget", "relay", "OwnTarget", "mast").entrySet()) {
            assertLocalReference(populated, field.getKey(), version, dataset + "-" + field.getValue());
            assertNull(child(empty, field.getKey()));
        }
    }

    private static void assertTransferClassPoint(Element object, String attribute, int version, List<String> position) {
        var value = child(object, attribute);
        assertNotNull(value, attribute);
        var point = child(value, version == 24 ? "coord" : "COORD");
        assertNotNull(point, attribute);
        assertTransferPosition(point, version == 24 ? "c" : "C", position);
    }

    private static void assertClassFields(Element object, String name, Map<String, String> fields, String... nestedFields) {
        assertNotNull(object, name);
        assertEquals(name, object.getLocalName());
        var nestedNames = Set.of(nestedFields);
        var actual = new HashMap<String, String>();
        for (var child : children(object)) {
            if (!nestedNames.contains(child.getLocalName())) {
                assertNull(actual.put(child.getLocalName(), child.getTextContent()));
            }
        }
        assertEquals(fields, actual);
    }

    private static void assertTransferredPairing(Map<String, Element> objects, int version, String dataset) {
        String prefix = version == 23 ? "DbInterop23.Data." : "";
        String leftTid = dataset + "-pairleft-1";
        String rightTid = dataset + "-pairright-1";
        var left = objects.get(leftTid);
        var leftWithoutPair = objects.get(dataset + "-pairleft-2");
        var right = objects.get(rightTid);
        assertEquals(prefix + "PairLeft", left.getLocalName());
        assertEquals(prefix + "PairLeft", leftWithoutPair.getLocalName());
        assertEquals(prefix + "PairRight", right.getLocalName());
        assertTrue(children(leftWithoutPair).isEmpty());
        var owner = version == 23 ? right : left;
        var target = version == 23 ? left : right;
        assertTrue(children(target).isEmpty());
        var role = child(owner, version == 23 ? "LeftLink" : "RightLink");
        assertNotNull(role);
        assertEquals(version == 23 ? leftTid : rightTid, id(role, version, "ref"));
        assertEquals("", id(role, version, "bid"));
        assertTrue(children(role).isEmpty());
    }

    private static void assertTransferredStructureHierarchy(Element record, int version) {
        String prefix = version == 23 ? "DbInterop23.Data." : "";
        assertEquals(prefix + "TypedRecord", record.getLocalName());
        var main = transferredStructures(record, "Main");
        assertEquals(1, main.size());
        assertClassFields(main.getFirst(), prefix + "CountNote", Map.of("Label", "main", "Amount", "5"));

        var values = transferredStructures(record, "Values");
        assertEquals(3, values.size());
        assertClassFields(values.get(0), prefix + "NoteBase", Map.of("Label", "root"));
        assertClassFields(values.get(1), prefix + "CountNote", Map.of("Label", "count", "Amount", "7"));
        assertClassFields(values.get(2), prefix + "FlagNote", Map.of("Label", "flag", "Enabled", "true"));

        var extras = transferredStructures(record, "Extras");
        assertEquals(2, extras.size());
        for (var duplicate : extras) {
            assertClassFields(duplicate, prefix + "NamedNote", Map.of("Label", "duplicate", "Code", "ABC"));
        }
        for (var field : List.of(main, values, extras)) {
            assertTrue(field.stream().allMatch(structure -> id(structure, version, "tid").isEmpty()));
        }
    }

    private static void assertTransferredReports(Element first, Element second, int version) {
        String prefix = version == 23 ? "DbInterop23.Data." : "";
        assertEquals(prefix + "Report", first.getLocalName());
        assertEquals(prefix + "Report", second.getLocalName());
        var summary = child(first, "Summary");
        assertNotNull(summary);
        assertEquals(1, children(summary).size());
        assertTransferredDetail(children(summary).getFirst(), version, prefix + "Detail", SUMMARY);
        assertNull(child(second, "Summary"));
        assertTransferredStructures(first, "Entries", version, prefix + "Detail", ENTRIES);
        assertTransferredStructures(second, "Entries", version, prefix + "Detail", List.of(LATER_ENTRY));
        assertTransferredStructures(first, "Observations", version, prefix + "Detail", OBSERVATIONS);
        assertNull(child(second, "Observations"));
    }

    private static void assertTransferredStructures(Element report, String attribute, int version, String detailName,
                                                    List<DetailValue> expected) {
        var entries = transferredStructures(report, attribute);
        assertEquals(expected.size(), entries.size());
        for (int index = 0; index < entries.size(); index++) {
            assertTransferredDetail(entries.get(index), version, detailName, expected.get(index));
        }
    }

    private static void assertTransferredDetail(Element detail, int version, String name, DetailValue expected) {
        assertEquals(name, detail.getLocalName());
        assertEquals("", id(detail, version, "tid"));
        assertEquals(expected.label(), text(detail, "Label"));
        assertEquals(expected.enabled() == null ? null : expected.enabled().toString(), text(detail, "Enabled"));
        assertEquals(expected.quantity() == null ? null : expected.quantity().toString(), text(detail, "Quantity"));
        assertEquals(expected.decision(), text(detail, "Decision"));
    }

    private static void assertTransferRoute(Element trace, int version, List<CurveComponent> components) {
        assertNotNull(trace);
        assertTransferPolyline(child(trace, version == 24 ? "polyline" : "POLYLINE"), version, components);
    }

    private static void assertTransferSurface(Element footprint, int version, List<List<CurveComponent>> rings) {
        assertNotNull(footprint);
        var surface = child(footprint, version == 24 ? "surface" : "SURFACE");
        assertNotNull(surface);
        assertTransferRings(surface, version, rings);
    }

    private static List<Element> assertTransferCollection(Map<String, Element> objects, String dataset, String name,
                                                          String attribute, String collectionTag, String memberTag) {
        String tidPrefix = dataset + "-" + name.toLowerCase(Locale.ROOT);
        var populated = objects.get(tidPrefix + "-1");
        var absent = objects.get(tidPrefix + "-2");
        assertEquals(name, populated.getLocalName());
        assertEquals(name, absent.getLocalName());
        assertNull(child(absent, attribute));
        var value = child(populated, attribute);
        assertNotNull(value);
        var multi = child(value, collectionTag);
        assertNotNull(multi);
        assertEquals("http://www.interlis.ch/geometry/1.0", multi.getNamespaceURI());
        var members = children(multi);
        assertEquals(List.of(memberTag, memberTag), members.stream().map(Element::getLocalName).toList());
        return members;
    }

    private static void assertTransferRings(Element surface, int version, List<List<CurveComponent>> rings) {
        var boundaries = children(surface);
        assertEquals(rings.size(), boundaries.size());
        for (int index = 0; index < rings.size(); index++) {
            var boundary = boundaries.get(index);
            if (version == 24) {
                assertEquals(index == 0 ? "exterior" : "interior", boundary.getLocalName());
            } else {
                assertEquals("BOUNDARY", boundary.getLocalName());
            }
            var lines = children(boundary);
            assertEquals(1, lines.size());
            var line = lines.getFirst();
            assertEquals(version == 24 ? "polyline" : "POLYLINE", line.getLocalName());
            assertTransferPolyline(line, version, rings.get(index));
        }
    }

    private static void assertTransferPolyline(Element polyline, int version, List<CurveComponent> components) {
        assertNotNull(polyline);
        var segments = children(polyline);
        int expectedSize = 1 + components.stream().mapToInt(component ->
                (component.vertices().size() - 1) / (component.type() == CIRCULARSTRING ? 2 : 1)).sum();
        assertEquals(expectedSize, segments.size());
        String coord = version == 24 ? "coord" : "COORD";
        String arc = version == 24 ? "arc" : "ARC";
        String c = version == 24 ? "c" : "C";
        String a = version == 24 ? "a" : "A";
        assertEquals(coord, segments.getFirst().getLocalName());
        assertEquals(2, children(segments.getFirst()).size());
        assertTransferPosition(segments.getFirst(), c, components.getFirst().vertices().getFirst());
        int segmentIndex = 1;
        for (var component : components) {
            boolean circular = component.type() == CIRCULARSTRING;
            for (int vertex = 1; vertex < component.vertices().size(); vertex += circular ? 2 : 1) {
                var segment = segments.get(segmentIndex++);
                assertEquals(circular ? arc : coord, segment.getLocalName());
                assertEquals(circular ? 4 : 2, children(segment).size());
                if (circular) {
                    // ili2db's XTF 2.4 exporter writes the intermediate point first.
                    assertTransferPosition(segment, a, component.vertices().get(vertex));
                    assertTransferPosition(segment, c, component.vertices().get(vertex + 1));
                } else {
                    assertTransferPosition(segment, c, component.vertices().get(vertex));
                }
            }
        }
    }

    private static void assertTransferPosition(Element segment, String prefix, List<String> expected) {
        for (int axis = 0; axis < expected.size(); axis++) {
            String name = prefix + (axis + 1);
            var ordinate = child(segment, name);
            assertNotNull(ordinate, name);
            assertEquals(0, new BigDecimal(expected.get(axis)).compareTo(new BigDecimal(ordinate.getTextContent())), name);
        }
    }

    private record CurveComponent(int type, List<List<String>> vertices) { }
    private record DetailValue(String label, Boolean enabled, Integer quantity, String decision) { }
    private record AnnotatedPair(String marker, String report, String memo, String grade, String decision) { }

    static void assertCliInZone(TestDatabase db, String operation, String model, String dataset, Path path, String zone)
            throws Exception {
        String modules = Main.class.getModule().getLayer().configuration().modules().stream()
                .flatMap(module -> module.reference().location().stream())
                .filter(uri -> "file".equals(uri.getScheme())).map(uri -> Path.of(uri).toString()).sorted()
                .collect(Collectors.joining(System.getProperty("path.separator")));
        var java = Path.of(System.getProperty("java.home"), "bin", "java");
        var command = new ArrayList<>(List.of(java.toString(), "-Duser.timezone=" + zone,
                "--enable-native-access=org.xerial.sqlitejdbc", "--module-path", modules,
                "--module", "guru.interlis.db/guru.interlis.db.Main",
                operation, "--model", model, "--dataset", dataset));
        command.addAll(db.nativeOptions());
        command.add(path.toString());
        Path log = Files.createTempFile(path.getParent(), "native-zone-", ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("INTERLIS_DB_PASSWORD", db.password);
        var process = builder.start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), () -> "Native CLI timed out in " + zone);
            assertEquals(0, process.exitValue(), () -> logText(log, db.password));
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }
}
