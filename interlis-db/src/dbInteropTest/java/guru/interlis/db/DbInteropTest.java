package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DbInteropTest {
    private static final String XTF24_NS = "http://www.interlis.ch/xtf/2.4/INTERLIS";
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

    @Test void geoPackageArrayText23() throws Exception { exchangeArrayText(23, null); }
    @Test void geoPackageArrayText24() throws Exception { exchangeArrayText(24, null); }
    @Test void postgisArrayText23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayText); }
    @Test void postgisArrayText24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayText); }

    private void exchangeArrayText(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "array_text");
        new ArrayCases(work, new ReferenceTool(db.postgis()), version).text(db);
    }

    @Test void geoPackageArrayBoolean23() throws Exception { exchangeArrayBoolean(23, null); }
    @Test void geoPackageArrayBoolean24() throws Exception { exchangeArrayBoolean(24, null); }
    @Test void postgisArrayBoolean23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayBoolean); }
    @Test void postgisArrayBoolean24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayBoolean); }

    private void exchangeArrayBoolean(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "array_boolean");
        new ArrayCases(work, new ReferenceTool(db.postgis()), version).bool(db);
    }

    @Test void geoPackageArrayInteger23() throws Exception { exchangeArrayInteger(23, null); }
    @Test void geoPackageArrayInteger24() throws Exception { exchangeArrayInteger(24, null); }
    @Test void postgisArrayInteger23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayInteger); }
    @Test void postgisArrayInteger24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayInteger); }

    private void exchangeArrayInteger(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "array_integer");
        new ArrayCases(work, new ReferenceTool(db.postgis()), version).integer(db);
    }

    @Test void geoPackageArrayEnumeration23() throws Exception { exchangeArrayEnumeration(23, null); }
    @Test void geoPackageArrayEnumeration24() throws Exception { exchangeArrayEnumeration(24, null); }
    @Test void postgisArrayEnumeration23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayEnumeration); }
    @Test void postgisArrayEnumeration24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayEnumeration); }

    private void exchangeArrayEnumeration(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "array_enumeration");
        new ArrayCases(work, new ReferenceTool(db.postgis()), version).enumeration(db);
    }

    @Test void geoPackageArrayBag23() throws Exception { exchangeArrayBag(23, null); }
    @Test void geoPackageArrayBag24() throws Exception { exchangeArrayBag(24, null); }
    @Test void postgisArrayBag23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayBag); }
    @Test void postgisArrayBag24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayBag); }

    private void exchangeArrayBag(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "array_bag");
        new ArrayCases(work, new ReferenceTool(db.postgis()), version).bag(db);
    }

    @Test void geoPackageArraySingle23() throws Exception { exchangeArraySingle(23, null); }
    @Test void geoPackageArraySingle24() throws Exception { exchangeArraySingle(24, null); }
    @Test void postgisArraySingle23() throws Exception { exchangeWithPostgis(23, this::exchangeArraySingle); }
    @Test void postgisArraySingle24() throws Exception { exchangeWithPostgis(24, this::exchangeArraySingle); }

    private void exchangeArraySingle(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "array_single");
        new ArrayCases(work, new ReferenceTool(db.postgis()), version).single(db);
    }

    @Test void geoPackageArrayDirect23() throws Exception { exchangeArrayDirect(23, null); }
    @Test void geoPackageArrayDirect24() throws Exception { exchangeArrayDirect(24, null); }
    @Test void postgisArrayDirect23() throws Exception { exchangeWithPostgis(23, this::exchangeArrayDirect); }
    @Test void postgisArrayDirect24() throws Exception { exchangeWithPostgis(24, this::exchangeArrayDirect); }

    private void exchangeArrayDirect(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "array_direct");
        new ArrayCases(work, new ReferenceTool(db.postgis()), version).direct(db);
    }

    @Test void geoPackageExpandSingle23() throws Exception { exchangeExpandSingle(23, null); }
    @Test void geoPackageExpandSingle24() throws Exception { exchangeExpandSingle(24, null); }
    @Test void postgisExpandSingle23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandSingle); }
    @Test void postgisExpandSingle24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandSingle); }

    private void exchangeExpandSingle(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "expand_single");
        new StructureMappingCases(work, new ReferenceTool(db.postgis()), version).single(db);
    }

    @Test void geoPackageExpandList23() throws Exception { exchangeExpandList(23, null); }
    @Test void geoPackageExpandList24() throws Exception { exchangeExpandList(24, null); }
    @Test void postgisExpandList23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandList); }
    @Test void postgisExpandList24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandList); }

    private void exchangeExpandList(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "expand_list");
        new StructureMappingCases(work, new ReferenceTool(db.postgis()), version).list(db);
    }

    @Test void geoPackageExpandBag23() throws Exception { exchangeExpandBag(23, null); }
    @Test void geoPackageExpandBag24() throws Exception { exchangeExpandBag(24, null); }
    @Test void postgisExpandBag23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandBag); }
    @Test void postgisExpandBag24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandBag); }

    private void exchangeExpandBag(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "expand_bag");
        new StructureMappingCases(work, new ReferenceTool(db.postgis()), version).bag(db);
    }

    @Test void geoPackageExpandNested23() throws Exception { exchangeExpandNested(23, null); }
    @Test void geoPackageExpandNested24() throws Exception { exchangeExpandNested(24, null); }
    @Test void postgisExpandNested23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandNested); }
    @Test void postgisExpandNested24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandNested); }

    private void exchangeExpandNested(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "expand_nested");
        new StructureMappingCases(work, new ReferenceTool(db.postgis()), version).nested(db);
    }

    @Test void geoPackageExpandSchema23() throws Exception { exchangeExpandSchema(23, null); }
    @Test void geoPackageExpandSchema24() throws Exception { exchangeExpandSchema(24, null); }
    @Test void postgisExpandSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeExpandSchema); }
    @Test void postgisExpandSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeExpandSchema); }

    private void exchangeExpandSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "expand_baseline");
        var own = new TestDatabase(postgres, "expand_own");
        new StructureMappingCases(work, new ReferenceTool(own.postgis()), version).schema(baseline, own);
    }

    @Test void geoPackageExpandSmart223() throws Exception { exchangeExpandSmart2(23, null); }
    @Test void geoPackageExpandSmart224() throws Exception { exchangeExpandSmart2(24, null); }
    @Test void postgisExpandSmart223() throws Exception { exchangeWithPostgis(23, this::exchangeExpandSmart2); }
    @Test void postgisExpandSmart224() throws Exception { exchangeWithPostgis(24, this::exchangeExpandSmart2); }

    private void exchangeExpandSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "expand_smart_baseline");
        var own = new TestDatabase(postgres, "expand_smart_own");
        new StructureMappingCases(work, new ReferenceTool(own.postgis()), version).smart2(baseline, own);
    }

    @Test void geoPackageJsonStructure23() throws Exception { exchangeJsonStructure(23, null); }
    @Test void geoPackageJsonStructure24() throws Exception { exchangeJsonStructure(24, null); }
    @Test void postgisJsonStructure23() throws Exception { exchangeWithPostgis(23, this::exchangeJsonStructure); }
    @Test void postgisJsonStructure24() throws Exception { exchangeWithPostgis(24, this::exchangeJsonStructure); }

    private void exchangeJsonStructure(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "json_structure");
        new StructureMappingCases(work, new ReferenceTool(db.postgis()), version).jsonSingle(db);
    }

    @Test void geoPackageJsonCollections23() throws Exception { exchangeJsonCollections(23, null); }
    @Test void geoPackageJsonCollections24() throws Exception { exchangeJsonCollections(24, null); }
    @Test void postgisJsonCollections23() throws Exception { exchangeWithPostgis(23, this::exchangeJsonCollections); }
    @Test void postgisJsonCollections24() throws Exception { exchangeWithPostgis(24, this::exchangeJsonCollections); }

    private void exchangeJsonCollections(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "json_collections");
        new StructureMappingCases(work, new ReferenceTool(db.postgis()), version).jsonCollections(db);
    }

    @Test void geoPackageJsonSchema23() throws Exception { exchangeJsonSchema(23, null); }
    @Test void geoPackageJsonSchema24() throws Exception { exchangeJsonSchema(24, null); }
    @Test void postgisJsonSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeJsonSchema); }
    @Test void postgisJsonSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeJsonSchema); }

    private void exchangeJsonSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "json_schema_baseline");
        var own = new TestDatabase(postgres, "json_schema_own");
        new StructureMappingCases(work, new ReferenceTool(own.postgis()), version).jsonSchema(baseline, own);
    }

    @Test void geoPackageChbasePoints23() throws Exception { exchangeChbasePoints(23, null); }
    @Test void geoPackageChbasePoints24() throws Exception { exchangeChbasePoints(24, null); }
    @Test void geoPackageStrokePolyline23() throws Exception { exchangeStrokePolyline(23, null); }
    @Test void geoPackageStrokePolyline24() throws Exception { exchangeStrokePolyline(24, null); }
    @Test void postgisStrokePolyline23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokePolyline); }
    @Test void postgisStrokePolyline24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokePolyline); }

    private void exchangeStrokePolyline(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "stroke_polyline");
        new StrokeCases(work, new ReferenceTool(db.postgis()), version).polyline(db);
    }

    @Test void geoPackageStrokeArcs23() throws Exception { exchangeStrokeArcs(23, null); }
    @Test void geoPackageStrokeArcs24() throws Exception { exchangeStrokeArcs(24, null); }
    @Test void postgisStrokeArcs23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeArcs); }
    @Test void postgisStrokeArcs24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeArcs); }

    private void exchangeStrokeArcs(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "stroke_arcs");
        new StrokeCases(work, new ReferenceTool(db.postgis()), version).arcs(db);
    }

    @Test void geoPackageStrokeSurface23() throws Exception { exchangeStrokeSurface(23, null); }
    @Test void geoPackageStrokeSurface24() throws Exception { exchangeStrokeSurface(24, null); }
    @Test void postgisStrokeSurface23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeSurface); }
    @Test void postgisStrokeSurface24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeSurface); }

    private void exchangeStrokeSurface(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "stroke_surface");
        new StrokeCases(work, new ReferenceTool(db.postgis()), version).surface(db);
    }

    @Test void geoPackageStrokeArea23() throws Exception { exchangeStrokeArea(23, null); }
    @Test void geoPackageStrokeArea24() throws Exception { exchangeStrokeArea(24, null); }
    @Test void postgisStrokeArea23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeArea); }
    @Test void postgisStrokeArea24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeArea); }

    private void exchangeStrokeArea(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "stroke_area");
        new StrokeCases(work, new ReferenceTool(db.postgis()), version).area(db);
    }

    @Test void geoPackageStrokeMultiPolyline23() throws Exception { exchangeStrokeMultiPolyline(23, null); }
    @Test void geoPackageStrokeMultiPolyline24() throws Exception { exchangeStrokeMultiPolyline(24, null); }
    @Test void postgisStrokeMultiPolyline23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeMultiPolyline); }
    @Test void postgisStrokeMultiPolyline24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeMultiPolyline); }

    private void exchangeStrokeMultiPolyline(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "stroke_multi_line");
        new StrokeCases(work, new ReferenceTool(db.postgis()), version).multiPolyline(db);
    }

    @Test void geoPackageStrokeMultiSurface23() throws Exception { exchangeStrokeMultiSurface(23, null); }
    @Test void geoPackageStrokeMultiSurface24() throws Exception { exchangeStrokeMultiSurface(24, null); }
    @Test void postgisStrokeMultiSurface23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeMultiSurface); }
    @Test void postgisStrokeMultiSurface24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeMultiSurface); }

    private void exchangeStrokeMultiSurface(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "stroke_multi_surface");
        new StrokeCases(work, new ReferenceTool(db.postgis()), version).multiSurface(db);
    }

    @Test void geoPackageStrokeSchema23() throws Exception { exchangeStrokeSchema(23, null); }
    @Test void geoPackageStrokeSchema24() throws Exception { exchangeStrokeSchema(24, null); }
    @Test void postgisStrokeSchema23() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeSchema); }
    @Test void postgisStrokeSchema24() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeSchema); }

    private void exchangeStrokeSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "stroke_baseline");
        var own = new TestDatabase(postgres, "stroke_own");
        new StrokeCases(work, new ReferenceTool(own.postgis()), version).schema(baseline, own);
    }

    @Test void geoPackageStrokeSmart223() throws Exception { exchangeStrokeSmart2(23, null); }
    @Test void geoPackageStrokeSmart224() throws Exception { exchangeStrokeSmart2(24, null); }
    @Test void postgisStrokeSmart223() throws Exception { exchangeWithPostgis(23, this::exchangeStrokeSmart2); }
    @Test void postgisStrokeSmart224() throws Exception { exchangeWithPostgis(24, this::exchangeStrokeSmart2); }

    private void exchangeStrokeSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "stroke_smart_baseline");
        var own = new TestDatabase(postgres, "stroke_smart_own");
        new StrokeCases(work, new ReferenceTool(own.postgis()), version).smart2(baseline, own);
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
        var db = new TestDatabase(postgres, "chbase_points");
        new ChbaseGeometryCases(work, new ReferenceTool(db.postgis()), version).points(db);
    }

    private void exchangeChbaseLines(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "chbase_lines");
        new ChbaseGeometryCases(work, new ReferenceTool(db.postgis()), version).lines(db);
    }

    private void exchangeChbaseSurfaces(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "chbase_surfaces");
        new ChbaseGeometryCases(work, new ReferenceTool(db.postgis()), version).surfaces(db);
    }

    private void exchangeChbaseGeometrySchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "chbase_geometry_ref");
        new ChbaseGeometryCases(work, new ReferenceTool(baseline.postgis()), version)
                .schema(baseline, new TestDatabase(postgres, "chbase_geometry_own"));
    }

    private void exchangeChbaseSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "chbase_smart2_ref");
        new ChbaseGeometryCases(work, new ReferenceTool(baseline.postgis()), version)
                .smart2(baseline, new TestDatabase(postgres, "chbase_smart2_own"));
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
        var db = new TestDatabase(postgres, "chbase_multilingual");
        new ChbaseTextCases(work, new ReferenceTool(db.postgis()), version).multilingual(db);
    }

    private void exchangeChbaseLocalised(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "chbase_localised");
        new ChbaseTextCases(work, new ReferenceTool(db.postgis()), version).localised(db);
    }

    private void exchangeChbaseTextSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "chbase_text_ref");
        new ChbaseTextCases(work, new ReferenceTool(baseline.postgis()), version)
                .schema(baseline, new TestDatabase(postgres, "chbase_text_own"));
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
        var db = new TestDatabase(postgres, "catalogue_reference");
        var cases = new CatalogueCases(work, new ReferenceTool(db.postgis()), version);
        cases.singleTarget(db);
        if (version == 24) cases.directReference(new TestDatabase(postgres, "catalogue_direct"));
    }

    private void exchangeCatalogueSmart2(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "catalogue_smart2");
        new CatalogueCases(work, new ReferenceTool(db.postgis()), version).smart2(db);
    }

    private void exchangeCatalogueSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var baseline = new TestDatabase(postgres, "catalogue_reference_newclass");
        new CatalogueCases(work, new ReferenceTool(baseline.postgis()), version).schema(baseline,
                new TestDatabase(postgres, "catalogue_native_newclass"), new TestDatabase(postgres, "catalogue_reference_smart2"),
                new TestDatabase(postgres, "catalogue_native_smart2"));
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

    private void exchangeSmart1Scalars(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart1_scalars");
        new SmartCases(work, new ReferenceTool(db.postgis()), version)
                .smart1Scalars(db, new TestDatabase(postgres, "smart1_defaults"));
    }

    private void exchangeSmart1References(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart1_references");
        new SmartCases(work, new ReferenceTool(db.postgis()), version).smart1References(db);
    }

    private void exchangeSmart2Scalars(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart2_scalars");
        new SmartCases(work, new ReferenceTool(db.postgis()), version).smart2Scalars(db);
    }

    private void exchangeSmart2References(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart2_references");
        new SmartCases(work, new ReferenceTool(db.postgis()), version).smart2References(db);
    }

    private void exchangeSmartStructures(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart1_structures");
        new SmartCases(work, new ReferenceTool(db.postgis()), version)
                .structures(db, new TestDatabase(postgres, "smart2_structures"));
    }

    private void exchangeSmartGeometries(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart1_geometries");
        new SmartCases(work, new ReferenceTool(db.postgis()), version)
                .geometries(db, new TestDatabase(postgres, "smart2_geometries"));
    }

    private void exchangeSmartMutations(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart1_mutations");
        new SmartCases(work, new ReferenceTool(db.postgis()), version)
                .mutations(db, new TestDatabase(postgres, "smart2_mutations"));
    }

    private void exchangeSmart1Schema(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart1_schema_reference");
        new SmartCases(work, new ReferenceTool(db.postgis()), version)
                .smart1Schema(db, new TestDatabase(postgres, "smart1_schema_native"));
    }

    private void exchangeSmart2Schema(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "smart2_schema_reference");
        new SmartCases(work, new ReferenceTool(db.postgis()), version)
                .smart2Schema(db, new TestDatabase(postgres, "smart2_schema_native"));
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
        assertWidePolymorphicProjection();
        exchange(24, null);
    }
    @Test void postgis23() throws Exception { exchangeWithPostgis(23, this::exchange); }
    @Test void postgis24() throws Exception { exchangeWithPostgis(24, this::exchange); }

    @Test void geoPackageMutation23() throws Exception { exchangeMutation(23, null); }
    @Test void geoPackageMutation24() throws Exception { exchangeMutation(24, null); }
    @Test void postgisMutation23() throws Exception { exchangeWithPostgis(23, this::exchangeMutation); }
    @Test void postgisMutation24() throws Exception { exchangeWithPostgis(24, this::exchangeMutation); }

    @Test void geoPackageDelete23() throws Exception { exchangeDelete(23, null); }
    @Test void geoPackageDelete24() throws Exception { exchangeDelete(24, null); }
    @Test void postgisDelete23() throws Exception { exchangeWithPostgis(23, this::exchangeDelete); }
    @Test void postgisDelete24() throws Exception { exchangeWithPostgis(24, this::exchangeDelete); }

    @Test void geoPackageSpatial23() throws Exception { exchangeSpatial(23, null); }
    @Test void geoPackageSpatial24() throws Exception { exchangeSpatial(24, null); }
    @Test void postgisSpatial23() throws Exception { exchangeWithPostgis(23, this::exchangeSpatial); }
    @Test void postgisSpatial24() throws Exception { exchangeWithPostgis(24, this::exchangeSpatial); }

    @Test void geoPackageXyz23() throws Exception { exchangeXyz(23, null); }
    @Test void geoPackageXyz24() throws Exception { exchangeXyz(24, null); }
    @Test void postgisXyz23() throws Exception { exchangeWithPostgis(23, this::exchangeXyz); }
    @Test void postgisXyz24() throws Exception { exchangeWithPostgis(24, this::exchangeXyz); }

    private void exchangeXyz(int version, PostgreSQLContainer postgres) throws Exception {
        new XyzGeometryCases(work, version, new ReferenceTool(postgres != null))
                .exchange(new TestDatabase(postgres, "xyz_reference"), new TestDatabase(postgres, "xyz_own"));
    }

    @Test void geoPackageGeometryLayout23() throws Exception { exchangeGeometryLayout(23, null); }
    @Test void geoPackageGeometryLayout24() throws Exception { exchangeGeometryLayout(24, null); }
    @Test void postgisGeometryLayout23() throws Exception { exchangeWithPostgis(23, this::exchangeGeometryLayout); }
    @Test void postgisGeometryLayout24() throws Exception { exchangeWithPostgis(24, this::exchangeGeometryLayout); }

    private void exchangeGeometryLayout(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new GeometryLayoutCases(work, version, new ReferenceTool(postgres != null));
        if (postgres != null) {
            cases.sameTable(new TestDatabase(postgres, "geometry_layout_reference"),
                    new TestDatabase(postgres, "geometry_layout_own"));
        } else {
            cases.wideGeoPackage(new TestDatabase(null, "wide_geometry_reference"),
                    new TestDatabase(null, "wide_geometry_own"));
        }
        cases.split(new TestDatabase(postgres, "split_geometry_reference"),
                new TestDatabase(postgres, "split_geometry_own"));
    }

    @Test void geoPackageGeometryHierarchy23() throws Exception { exchangeGeometryHierarchy(23, null); }
    @Test void geoPackageGeometryHierarchy24() throws Exception { exchangeGeometryHierarchy(24, null); }
    @Test void postgisGeometryHierarchy23() throws Exception { exchangeWithPostgis(23, this::exchangeGeometryHierarchy); }
    @Test void postgisGeometryHierarchy24() throws Exception { exchangeWithPostgis(24, this::exchangeGeometryHierarchy); }

    private void exchangeGeometryHierarchy(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new GeometryLayoutCases(work, version, new ReferenceTool(postgres != null));
        cases.hierarchy(new TestDatabase(postgres, "hierarchy_geometry_reference"),
                new TestDatabase(postgres, "hierarchy_geometry_own"), postgres == null);
        cases.hierarchy(new TestDatabase(postgres, "alternate_hierarchy_reference"),
                new TestDatabase(postgres, "alternate_hierarchy_own"), postgres != null);
    }

    @Test void geoPackageCrs23() throws Exception { exchangeCrs(23, null); }
    @Test void geoPackageCrs24() throws Exception { exchangeCrs(24, null); }
    @Test void postgisCrs23() throws Exception { exchangeWithPostgis(23, this::exchangeCrs); }
    @Test void postgisCrs24() throws Exception { exchangeWithPostgis(24, this::exchangeCrs); }

    private void exchangeCrs(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        for (int epsg : List.of(21781, 4326)) {
            cases.defaults(new TestDatabase(postgres, "crs_reference_" + epsg),
                    new TestDatabase(postgres, "crs_own_" + epsg), epsg);
        }
        cases.rejectMissingDefault(new TestDatabase(postgres, "crs_missing"));
        cases.declaredPointCrs(new TestDatabase(postgres, "declared_crs_reference"),
                new TestDatabase(postgres, "declared_crs_own"));
        cases.vertexCrs(new TestDatabase(postgres, "vertex_crs_reference"),
                new TestDatabase(postgres, "vertex_crs_own"));
        cases.modelOverride(new TestDatabase(postgres, "model_crs_reference"));
    }

    @Test void geoPackageMixedSrid23() throws Exception { exchangeMixedSrid(23, null); }
    @Test void geoPackageMixedSrid24() throws Exception { exchangeMixedSrid(24, null); }
    @Test void postgisMixedSrid23() throws Exception { exchangeWithPostgis(23, this::exchangeMixedSrid); }
    @Test void postgisMixedSrid24() throws Exception { exchangeWithPostgis(24, this::exchangeMixedSrid); }

    private void exchangeMixedSrid(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.mixedSrid(new TestDatabase(postgres, "mixed_split_reference"),
                new TestDatabase(postgres, "mixed_split_own"), true);
        cases.mixedSrid(new TestDatabase(postgres, "mixed_wide_reference"),
                new TestDatabase(postgres, "mixed_wide_own"), false);
    }

    @Test void geoPackageBasketCrsMutation23() throws Exception { exchangeBasketCrsMutation(23, null); }
    @Test void geoPackageBasketCrsMutation24() throws Exception { exchangeBasketCrsMutation(24, null); }
    @Test void postgisBasketCrsMutation23() throws Exception { exchangeWithPostgis(23, this::exchangeBasketCrsMutation); }
    @Test void postgisBasketCrsMutation24() throws Exception { exchangeWithPostgis(24, this::exchangeBasketCrsMutation); }

    private void exchangeBasketCrsMutation(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "basket_crs_mutation");
        var reference = new ReferenceTool(postgres != null);
        if (version == 23) new AlternateCrsCases(work, reference).mutate(db);
        else new CrsCases(work, version, reference).mutateDeferred(db);
    }

    @Test void geoPackageAlternateCrsExchange23() throws Exception { exchangeAlternateCrs(23, null); }
    @Test void postgisAlternateCrsExchange23() throws Exception { exchangeWithPostgis(23, this::exchangeAlternateCrs); }

    private void exchangeAlternateCrs(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new AlternateCrsCases(work, new ReferenceTool(postgres != null));
        cases.exchange(new TestDatabase(postgres, "alternate_crs_legacy"),
                new TestDatabase(postgres, "alternate_crs_own"), new TestDatabase(postgres, "alternate_crs_invalid"));
    }

    @Test void geoPackageAlternateCrsExport23() throws Exception { exchangeAlternateCrsExport(23, null); }
    @Test void postgisAlternateCrsExport23() throws Exception { exchangeWithPostgis(23, this::exchangeAlternateCrsExport); }

    private void exchangeAlternateCrsExport(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new AlternateCrsCases(work, new ReferenceTool(postgres != null));
        cases.export(new TestDatabase(postgres, "alternate_crs_reference"));
    }

    @Test void geoPackageDeferredMulti24() throws Exception { exchangeDeferredMulti(24, null); }
    @Test void postgisDeferredMulti24() throws Exception { exchangeWithPostgis(24, this::exchangeDeferredMulti); }

    private void exchangeDeferredMulti(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.deferredMulti(new TestDatabase(postgres, "deferred_multi_reference"),
                new TestDatabase(postgres, "deferred_multi_own"));
    }

    @Test void geoPackageDeferredCurves24() throws Exception { exchangeDeferredCurves(24, null); }
    @Test void postgisDeferredCurves24() throws Exception { exchangeWithPostgis(24, this::exchangeDeferredCurves); }

    private void exchangeDeferredCurves(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.deferredCurves(new TestDatabase(postgres, "deferred_curves_reference"),
                new TestDatabase(postgres, "deferred_curves_own"));
    }

    @Test void geoPackageCreateDeferred24() throws Exception { exchangeCreateDeferred(24, null); }
    @Test void postgisCreateDeferred24() throws Exception { exchangeWithPostgis(24, this::exchangeCreateDeferred); }

    private void exchangeCreateDeferred(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.createDeferred(new TestDatabase(postgres, "deferred_native_ddl"),
                new TestDatabase(postgres, "deferred_reference_ddl"), new TestDatabase(postgres, "deferred_rejected_ddl"));
    }

    @Test void geoPackageImportDeferred24() throws Exception { exchangeImportDeferred(24, null); }
    @Test void postgisImportDeferred24() throws Exception { exchangeWithPostgis(24, this::exchangeImportDeferred); }

    private void exchangeImportDeferred(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.importDeferred(new TestDatabase(postgres, "deferred_import"));
    }

    @Test void geoPackageExportDeferred24() throws Exception { exchangeExportDeferred(24, null); }
    @Test void postgisExportDeferred24() throws Exception { exchangeWithPostgis(24, this::exchangeExportDeferred); }

    private void exchangeExportDeferred(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.exportDeferred(new TestDatabase(postgres, "deferred_reference"));
    }

    @Test void geoPackageStaticGeneric24() throws Exception { exchangeStaticGeneric(24, null); }
    @Test void postgisStaticGeneric24() throws Exception { exchangeWithPostgis(24, this::exchangeStaticGeneric); }

    private void exchangeStaticGeneric(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.staticGeneric(new TestDatabase(postgres, "static_crs_reference"),
                new TestDatabase(postgres, "static_crs_own"));
    }

    @Test void geoPackageFixedMultiSrs23() throws Exception { exchangeFixedMultiSrs(23, null); }
    @Test void geoPackageFixedMultiSrs24() throws Exception { exchangeFixedMultiSrs(24, null); }
    @Test void postgisFixedMultiSrs23() throws Exception { exchangeWithPostgis(23, this::exchangeFixedMultiSrs); }
    @Test void postgisFixedMultiSrs24() throws Exception { exchangeWithPostgis(24, this::exchangeFixedMultiSrs); }

    private void exchangeFixedMultiSrs(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.fixedMultiSrs(new TestDatabase(postgres, "suffix_split_reference"),
                new TestDatabase(postgres, "suffix_split_own"), true);
        cases.fixedMultiSrs(new TestDatabase(postgres, "suffix_wide_reference"),
                new TestDatabase(postgres, "suffix_wide_own"), false);
    }

    @Test void geoPackageLocalSrid23() throws Exception { exchangeLocalSrid(23, null); }
    @Test void geoPackageLocalSrid24() throws Exception { exchangeLocalSrid(24, null); }
    @Test void postgisLocalSrid23() throws Exception { exchangeWithPostgis(23, this::exchangeLocalSrid); }
    @Test void postgisLocalSrid24() throws Exception { exchangeWithPostgis(24, this::exchangeLocalSrid); }

    private void exchangeLocalSrid(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new CrsCases(work, version, new ReferenceTool(postgres != null));
        cases.localSrid(new TestDatabase(postgres, "local_srid"));
    }

    private void exchangeSpatial(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new SpatialIndexCases(work, version, new ReferenceTool(postgres != null));
        cases.points(new TestDatabase(postgres, "spatial_points"));
        cases.straightGeometries(new TestDatabase(postgres, "spatial_straight"));
        cases.circularGeometries(new TestDatabase(postgres, "spatial_arcs"));
        if (version == 24) cases.collections(new TestDatabase(postgres, "spatial_collections"));
        cases.createdSchemas(new TestDatabase(postgres, "spatial_created_reference"),
                new TestDatabase(postgres, "spatial_created_native"));
    }

    private void exchangeDelete(int version, PostgreSQLContainer postgres) throws Exception {
        var cases = new DatasetWriteCases(work, version, new ReferenceTool(postgres != null));
        cases.deleteRelations(new TestDatabase(postgres, "delete_reference"), true);
        cases.deleteRelations(new TestDatabase(postgres, "delete_native"), false);
        cases.deleteReferenced(new TestDatabase(postgres, "delete_references"));
    }

    private void exchangeMutation(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new ReferenceTool(postgres != null);
        var cases = new DatasetWriteCases(work, version, reference);
        cases.replace(new TestDatabase(postgres, "mutation_reference"), true);
        cases.replace(new TestDatabase(postgres, "mutation_native"), false);
        cases.replaceRelations(new TestDatabase(postgres, "replace_rel_reference"), true);
        cases.replaceRelations(new TestDatabase(postgres, "replace_rel_native"), false);
        cases.replaceExternal(new TestDatabase(postgres, "replace_ext_reference"), true);
        cases.replaceExternal(new TestDatabase(postgres, "replace_ext_native"), false);
        cases.updateFlat(new TestDatabase(postgres, "update_stable_reference"), true, true);
        cases.updateFlat(new TestDatabase(postgres, "update_stable_native"), false, true);
        cases.updateFlat(new TestDatabase(postgres, "update_stored_reference"), true, false);
        cases.updateFlat(new TestDatabase(postgres, "update_stored_native"), false, false);
        cases.updateGeometry(new TestDatabase(postgres, "update_geom_reference"), true);
        cases.updateGeometry(new TestDatabase(postgres, "update_geom_native"), false);
        cases.updateStructures(new TestDatabase(postgres, "update_struct_reference"), true);
        cases.updateStructures(new TestDatabase(postgres, "update_struct_native"), false);
        cases.updateLocalReferences(new TestDatabase(postgres, "update_local_reference"), true);
        cases.updateLocalReferences(new TestDatabase(postgres, "update_local_native"), false);
        cases.updateAssociations(new TestDatabase(postgres, "update_link_reference"), true);
        cases.updateAssociations(new TestDatabase(postgres, "update_link_native"), false);
        cases.updateExternalReferences(new TestDatabase(postgres, "update_ext_reference"), true);
        cases.updateExternalReferences(new TestDatabase(postgres, "update_ext_native"), false);
    }

    private void assertWidePolymorphicProjection() throws Exception {
        var attributes = new StringBuilder();
        var values = new StringBuilder();
        // A source table plus two joins per field would exceed SQLite's 64-table limit.
        for (int index = 0; index < 32; index++) {
            attributes.append("    To").append(index).append(" : REFERENCE TO Target;\n");
            values.append("<To").append(index).append(" i:ref=\"target\"/>");
        }
        Path model = Files.writeString(work.resolve("WideRefs.ili"), """
                INTERLIS 2.4;
                MODEL WideRefs (en) AT "https://example.org/interlis/db" VERSION "2026-11-20" =
                  TOPIC Data =
                    CLASS Base (ABSTRACT) = END Base;
                    CLASS Target EXTENDS Base = END Target;
                    CLASS Source =
                %s    END Source;
                  END Data;
                END WideRefs.
                """.formatted(attributes));
        Path input = Files.writeString(work.resolve("wide-input.xtf"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="http://www.interlis.ch/xtf/2.4/WideRefs">
                  <i:headersection><i:models><i:model>WideRefs</i:model></i:models><i:sender>interop fixture</i:sender></i:headersection>
                  <i:datasection><Data i:bid="basket"><Source i:tid="source">%s</Source>
                    <Target i:tid="target"/></Data></i:datasection>
                </i:transfer>
                """.formatted(values));
        var db = new TestDatabase(null, "wide");
        assertCliSuccess(db, "schemaimport", "WideRefs", null, model);
        assertCliSuccess(db, "import", "WideRefs", "wide", input);
        Path output = work.resolve("wide-output.xtf");
        assertCliSuccess(db, "export", "WideRefs", "wide", output);
        var source = child(children(readDataSection(output, 24)).getFirst(), "Source");
        assertEquals(32, children(source).size());
        for (int index = 0; index < 32; index++) {
            assertLocalReference(source, "To" + index, 24, "target");
        }
    }

    @FunctionalInterface
    private interface PostgisCase {
        void run(int version, PostgreSQLContainer postgres) throws Exception;
    }

    private void exchangeWithPostgis(int version, PostgisCase test) throws Exception {
        var image = DockerImageName.parse("sogis/postgis:16-3.4").asCompatibleSubstituteFor("postgres");
        try (var postgres = new PostgreSQLContainer(image)
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,size=256m"))
                .withPassword("db_interop_password")) {
            postgres.start();
            test.run(version, postgres);
        }
    }

    private void exchangeTranslationSources(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "translation_sources");
        new TranslationCases(work, new ReferenceTool(db.postgis()), version).sources(db);
    }

    private void exchangeTranslationScalars(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "translation_scalars");
        new TranslationCases(work, new ReferenceTool(db.postgis()), version).scalars(db);
    }

    private void exchangeTranslationNested(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "translation_nested");
        new TranslationCases(work, new ReferenceTool(db.postgis()), version).nested(db);
    }

    private void exchangeTranslationSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_translation");
        var own = new TestDatabase(postgres, "own_translation");
        var extended = new TestDatabase(postgres, "extended_translation");
        new TranslationCases(work, new ReferenceTool(reference.postgis()), version).schema(reference, own, extended);
    }

    private void exchangeTranslationExport(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "translation_export");
        new TranslationCases(work, new ReferenceTool(db.postgis()), version).exportLanguage(db);
    }

    private void exchangeTopicInherited(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_topic_inherited");
        var own = new TestDatabase(postgres, "own_topic_inherited");
        new TopicCases(work, new ReferenceTool(reference.postgis()), version).inherited(reference, own);
    }

    private void exchangeTopicFragments(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_topic_fragments");
        var own = new TestDatabase(postgres, "own_topic_fragments");
        new TopicCases(work, new ReferenceTool(reference.postgis()), version).fragments(reference, own);
    }

    private void exchangeTopicRefinement(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_topic_refinement");
        var own = new TestDatabase(postgres, "own_topic_refinement");
        new TopicCases(work, new ReferenceTool(reference.postgis()), version).refinement(reference, own);
    }

    private void exchangeTopicReferences(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_topic_references");
        var own = new TestDatabase(postgres, "own_topic_references");
        new TopicCases(work, new ReferenceTool(reference.postgis()), version).references(reference, own);
    }

    private void exchangeTopicMutations(int version, PostgreSQLContainer postgres) throws Exception {
        var translated = new TestDatabase(postgres, "translation_mutation");
        var inherited = new TestDatabase(postgres, "topic_mutation");
        var reference = new ReferenceTool(inherited.postgis());
        new TranslationCases(work, reference, version).mutation(translated);
        new TopicCases(work, reference, version).mutation(inherited);
    }

    private void exchangeSharedSource(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "shared_source");
        new ModelCases(work, new ReferenceTool(db.postgis()), version).sharedSource(db);
    }

    private void exchangeStoredImports(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "stored_imports");
        new ModelCases(work, new ReferenceTool(db.postgis()), version).storedImports(db);
    }

    private void exchangeLocalImports(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_imports");
        var nativeSchema = new TestDatabase(postgres, "native_imports");
        new ModelCases(work, new ReferenceTool(reference.postgis()), version).localImports(reference, nativeSchema);
    }

    private void exchangeImportedStruct(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_detail");
        var nativeSchema = new TestDatabase(postgres, "native_detail");
        new ModelCases(work, new ReferenceTool(reference.postgis()), version).importedStructures(reference, nativeSchema);
    }

    private void exchangeImportedTopic(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_topic");
        var nativeSchema = new TestDatabase(postgres, "native_topic");
        new ModelCases(work, new ReferenceTool(reference.postgis()), version).importedTopics(reference, nativeSchema);
    }

    private void exchangeJointSchema(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_joint");
        var nativeSchema = new TestDatabase(postgres, "native_joint");
        new ModelCases(work, new ReferenceTool(reference.postgis()), version).jointSchema(reference, nativeSchema);
    }

    private void exchangeMixedExport(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "mixed_export");
        new ModelCases(work, new ReferenceTool(db.postgis()), version).mixedExport(db);
    }

    private void exchangeMixedImport(int version, PostgreSQLContainer postgres) throws Exception {
        var reference = new TestDatabase(postgres, "reference_mixed");
        var nativeSchema = new TestDatabase(postgres, "native_mixed");
        new ModelCases(work, new ReferenceTool(reference.postgis()), version).mixedImport(reference, nativeSchema);
    }

    private void exchangeMixedMutation(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "mixed_mutation");
        new ModelCases(work, new ReferenceTool(db.postgis()), version).mixedMutation(db);
    }

    private void exchangeAppendModels(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(postgres, "append_models");
        new ModelCases(work, new ReferenceTool(db.postgis()), version).appendModels(db);
    }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        String model = "DbInterop" + version;
        Path ili = work.resolve(model + ".ili");
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + ili.getFileName())) {
            Files.copy(resource, ili);
        }
        String fixture;
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + model + ".xtf")) {
            fixture = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }

        var reference = new ReferenceTool(postgres != null);
        var db = new TestDatabase(postgres, "reference");
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
            assertRollback(db, sql, model, version, fixture);
            assertStructureRollback(db, sql, model, version, fixture);
            assertMissingStructureTargetRollback(db, sql, model, version, fixture);
            assertMissingReferenceRollback(db, sql, model, version, fixture,
                    "Select", "missing-role-marker-1", "missing-role", 3);
            assertExplicitRadiusRollback(db, sql, model, version, fixture);
            assertOpenSurfaceRollback(db, sql, model, version, fixture);
            assertCorruptStructureRejected(db, sql, model, nativeOutput);
            assertNestedSequenceOrder(db, sql, model, version);
            assertCorruptNestedStructureRejected(db, sql, model, nativeOutput);
            assertCorruptRoleMappingRejected(db, sql, model, nativeOutput);
            assertWrongStructureBranchRejected(db, sql, model, nativeOutput);
            assertCorruptStructureTargetRejected(db, sql, model, nativeOutput);
            assertCorruptPolymorphicTargetsRejected(db, sql, model, nativeOutput);
            assertCorruptHierarchyRoleRejected(db, sql, model, nativeOutput);
            assertCorruptHierarchyLinkRejected(db, sql, model, nativeOutput);
            assertCorruptTernaryLinkRejected(db, sql, model, nativeOutput);
            assertCorruptClassHierarchyRejected(db, sql, model, nativeOutput);
            assertMissingAssociationFragmentRejected(db, sql, model, nativeOutput);
            assertCorruptInheritedOwnerRejected(db, sql, model, nativeOutput);
            assertClassHierarchyRollback(db, sql, model, version, fixture);
            assertUnresolvedHierarchyReferenceRollback(db, sql, model, version, fixture);
            assertWrongRelayReviewOwnerRejected(db, sql, model, nativeOutput);
            assertAmbiguousRatingRejected(db, sql, model, nativeOutput);
            assertAmbiguousReviewRejected(db, sql, model, nativeOutput);
            if (version == 24) {
                assertEmptyCollectionRollback(db, sql, model, fixture, "multisurface", "Park.Coverage", "multisurface", 13);
                assertEmptyCollectionRollback(db, sql, model, fixture, "multipolyline", "RouteGroup.Traces", "multicurve", 15);
                assertEmptyCollectionRollback(db, sql, model, fixture, "multicoord", "Survey.Locations", "multipoint", 16);
                assertCorruptNewCollectionRejected(db, sql, model, nativeOutput);
                assertEmptyCollectionExport(db, sql, model);
                assertCorruptMultiSurfaceRejected(db, sql, model, nativeOutput);
            }
            assertCorruptGeometriesRejected(db, sql, model, nativeOutput);
            execute(sql, "update " + db.table("t_ili2db_attrname") + " set sqlname='missing_select'"
                    + " where iliname='" + model + ".Data.Place.Select'");
            String originalOutput = Files.readString(nativeOutput);
            var corrupt = cli(db, "export", model, "legacy", nativeOutput, CancellationToken.none());
            assertNotEquals(0, corrupt.code(), corrupt::diagnostics);
            assertTrue(corrupt.diagnostics().contains("Select") || corrupt.diagnostics().contains("missing_select"),
                    corrupt::diagnostics);
            assertEquals(originalOutput, Files.readString(nativeOutput));
        }
        exchangeExternal(version, postgres, reference);
        exchangeExternalTopics(version, postgres, reference);
        exchangeOrdered(reference, postgres, version);
        exchangeOid(reference, postgres, version);
    }

    private void exchangeExternal(int version, PostgreSQLContainer postgres, ReferenceTool reference) throws Exception {
        String model = "DbExternal" + version;
        Path ili = work.resolve(model + ".ili");
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + ili.getFileName())) {
            Files.copy(resource, ili);
        }
        String fixture;
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + model + ".xtf")) {
            fixture = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        var referenceDb = new TestDatabase(postgres, "external_reference");
        reference.run(referenceDb, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                ili.toString());
        var nativeDb = new TestDatabase(postgres, "external_native");
        assertCliSuccess(nativeDb, "schemaimport", model, null, ili);
        try (var referenceSql = referenceDb.connect(); var nativeSql = nativeDb.connect()) {
            DbSchemaCatalog.read(referenceSql, referenceDb.postgis, referenceDb.schema)
                    .assertSameAs(DbSchemaCatalog.read(nativeSql, nativeDb.postgis, nativeDb.schema));
            assertReferenceMapping(referenceDb, referenceSql, model, "Detail.TargetRef", "detail", "targetref", "targetbranch");
            if (version == 24) {
                assertReferenceMapping(referenceDb, referenceSql, model, "Report.DirectRef", "report", "directref", "targetbranch");
            }
            assertReferenceMapping(referenceDb, referenceSql, model, "ReportPlace.BaseSite", "report", "basesite", "targetbranch");
            assertReferenceMapping(referenceDb, referenceSql, model, "ReportPairing.PairedSite", "report", "pairedsite", "targetbranch");
            for (String association : List.of("ReportPlace", "ReportPairing")) {
                assertEquals("embedded", scalar(referenceSql, "select setting from " + referenceDb.table("t_ili2db_trafo")
                        + " where iliname='" + model + ".Data." + association + "' and tag='ch.ehi.ili2db.inheritance'"));
            }
            assertAssociationCatalog(referenceDb, referenceSql, model, "ExternalPair",
                    Map.of("PairObject", "report", "PairTarget", "targetbranch"));
            assertAssociationCatalog(referenceDb, referenceSql, model, "ExternalReview",
                    Map.of("ReviewOwner", "report", "ReviewSite", "targetbranch"));
            assertTypeKind(referenceDb, referenceSql, "externalreview", "reviewmemo", "TEXT");
            exchangeExternalDatasets(reference, referenceDb, referenceSql, model, version, fixture);
            exchangeExternalDatasets(reference, nativeDb, nativeSql, model, version, fixture);
            exchangeReferenceDataset(reference, referenceDb, referenceSql, model, version);
            exchangeReferenceDataset(reference, nativeDb, nativeSql, model, version);
        }
    }

    private void exchangeOid(ReferenceTool reference, PostgreSQLContainer postgres, int version) throws Exception {
        String model = "DbOid" + version;
        Path ili = work.resolve(model + ".ili");
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + ili.getFileName())) {
            Files.copy(resource, ili);
        }
        var referenceDb = new TestDatabase(postgres, "oid_reference");
        reference.run(referenceDb, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", ili.toString());
        var nativeDb = new TestDatabase(postgres, "oid_native");
        assertCliSuccess(nativeDb, "schemaimport", model, null, ili);
        try (var legacy = referenceDb.connect(); var own = nativeDb.connect()) {
            DbSchemaCatalog.read(legacy, referenceDb.postgis, referenceDb.schema)
                    .assertSameAs(DbSchemaCatalog.read(own, nativeDb.postgis, nativeDb.schema));
        }
        String referenceId = "C09B1740-5C5A-48EA-B095-938D1C946974";
        String nativeId = "D09B1740-5C5A-48EA-B095-938D1C946974";
        String tailId = "E09B1740-5C5A-48EA-B095-938D1C946974";
        Path legacyInput = oidFixture(model, version, "oid-legacy", referenceId);
        Path nativeInput = oidFixture(model, version, "oid-own", nativeId);
        Path tailInput = oidFixture(model, version, "oid-tail", tailId);
        for (var db : List.of(referenceDb, nativeDb)) {
            try (var sql = db.connect()) {
                reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "oid-legacy",
                        legacyInput.toString());
                assertOidRows(db, sql, "oid-legacy", referenceId);
                Path ownOutput = work.resolve("oid-own-output.xtf");
                assertCliSuccess(db, "export", model, "oid-legacy", ownOutput);
                assertOidXtf(ownOutput, model, version, "oid-legacy", referenceId);

                assertCliSuccess(db, "import", model, "oid-own", nativeInput);
                assertOidRows(db, sql, "oid-own", nativeId);
                Path legacyOutput = work.resolve("oid-legacy-output.xtf");
                reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "oid-own", legacyOutput.toString());
                assertOidXtf(legacyOutput, model, version, "oid-own", nativeId);

                reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "oid-tail",
                        tailInput.toString());
                assertOidRows(db, sql, "oid-tail", tailId);
                Path invalid = oidFixture(model, version, "oid-invalid", "malformed");
                var rejected = cli(db, "import", model, "oid-invalid", invalid, CancellationToken.none());
                assertNotEquals(0, rejected.code(), rejected::diagnostics);
                assertTrue(rejected.diagnostics().contains("Invalid UUID"), rejected::diagnostics);
                assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_dataset") + " where datasetname='oid-invalid'"));

                exchangeOidReference(reference, db, sql, model, version, referenceId);
                String metadata = db.table("t_ili2db_column_prop") + " where tablename='site' and tag='ch.ehi.ili2db.oidDomain'";
                assertEquals("INTERLIS.UUIDOID", scalar(sql, "select setting from " + metadata));
                execute(sql, "delete from " + metadata);
                assertExportRejected(db, model, "oid-legacy", ownOutput, "oidDomain", "site.t_ili_tid");
            }
        }
    }

    private void exchangeOidReference(ReferenceTool reference, TestDatabase db, Connection sql, String model,
                                      int version, String targetId) throws Exception {
        String objects = version == 23 ? """
                <%1$s.Data.Inspection TID="remote-inspection"><Links><%1$s.Data.InspectionRefs>
                  <RemoteSite REF="%2$s" BID="%3$s"/>
                </%1$s.Data.InspectionRefs></Links></%1$s.Data.Inspection>
                """.formatted(model, targetId, oidBasket("oid-legacy")) : """
                <Inspection i:tid="remote-inspection"><Links><InspectionRefs><RemoteSite i:ref="%s"/></InspectionRefs></Links></Inspection>
                """.formatted(targetId);
        Path legacy = Files.writeString(work.resolve("oid-reference-legacy.xtf"),
                referenceDatasetFixture(model, version, oidBasket("oid-reference-legacy"), objects));
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "oid-reference-legacy", legacy.toString());
        assertOidReferenceRow(db, sql, "remote-inspection", targetId, "oid-legacy", "remotesite");
        Path output = work.resolve("oid-reference-output.xtf");
        assertCliSuccess(db, "export", model, "oid-reference-legacy", output);
        assertOidReferenceXtf(output, version, targetId, true);

        Path own = Files.writeString(work.resolve("oid-reference-own.xtf"),
                referenceDatasetFixture(model, version, oidBasket("oid-reference-own"), objects.replace("remote-inspection", "own-inspection")));
        assertCliSuccess(db, "import", model, "oid-reference-own", own);
        assertOidReferenceRow(db, sql, "own-inspection", targetId, "oid-legacy", "remotesite");
        reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "oid-reference-own", output.toString());
        assertOidReferenceXtf(output, version, targetId, false);

        Path duplicate = oidFixture(model, version, "oid-duplicate", targetId);
        assertCliSuccess(db, "import", model, "oid-duplicate", duplicate);
        var ambiguous = Files.writeString(work.resolve("oid-reference-ambiguous.xtf"), Files.readString(output)
                .replace("own-inspection", "ambiguous-inspection")
                .replace(oidBasket("oid-reference-own"), oidBasket("oid-reference-ambiguous")));
        var rejected = cli(db, "import", model, "oid-reference-ambiguous", ambiguous, CancellationToken.none());
        assertNotEquals(0, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("Ambiguous stored reference target"), rejected::diagnostics);
        assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_dataset") + " where datasetname='oid-reference-ambiguous'"));
    }

    private static void assertOidReferenceRow(TestDatabase db, Connection sql, String owner, String target,
                                              String targetBid, String column) throws SQLException {
        String query = """
                select s.t_ili_tid from %s s join %s r on r.%s=s.t_id
                join %s i on r.inspection_links=i.t_id and r.t_basket=i.t_basket
                join %s b on b.t_id=s.t_basket where i.t_ili_tid='%s' and b.t_ili_tid='%s' and s.t_type='marker'
                """.formatted(db.table("site"), db.table("inspectionrefs"), column, db.table("inspection"),
                db.table("t_ili2db_basket"), owner, oidBasket(targetBid));
        assertEquals(target.toLowerCase(Locale.ROOT), scalar(sql, query));
    }

    private static void assertOidReferenceXtf(Path output, int version, String tid, boolean own) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(1, children(data).size());
        var basket = children(data).getFirst();
        assertEquals(1, children(basket).size());
        var inspection = children(basket).getFirst();
        var refs = children(child(inspection, "Links")).getFirst();
        assertExternalReference(child(refs, "RemoteSite"), version, tid.toLowerCase(Locale.ROOT),
                own ? oidBasket("oid-legacy") : "");
    }

    private Path oidFixture(String model, int version, String bid, String tid) throws IOException {
        String object = version == 23 ? """
                <%1$s.Data.Marker TID="%2$s"><Label>marker</Label><Category>survey</Category></%1$s.Data.Marker>
                """.formatted(model, tid) : """
                <Marker i:tid="%s"><Label>marker</Label><Category>survey</Category></Marker>
                """.formatted(tid);
        String defaults = version == 23 ? """
                <%1$s.Defaults BID="%2$s-defaults"><%1$s.Defaults.Address TID="%3$s"><Text>address</Text></%1$s.Defaults.Address></%1$s.Defaults>
                """.formatted(model, bid, defaultOid(tid)) : """
                <Defaults i:bid="%s-defaults"><Address i:tid="%s"><Text>address</Text></Address></Defaults>
                """.formatted(bid, defaultOid(tid));
        String inspection = version == 23 ? """
                <%1$s.Data.Inspection TID="%2$s-inspection"><Links><%1$s.Data.InspectionRefs><LocalSite REF="%3$s"/><CatalogId REF="%4$s"/></%1$s.Data.InspectionRefs></Links></%1$s.Data.Inspection>
                """.formatted(model, bid, tid, standardOid(tid)) : """
                <Inspection i:tid="%s-inspection"><Links><InspectionRefs><LocalSite i:ref="%s"/><CatalogId i:ref="%s"/></InspectionRefs></Links></Inspection>
                """.formatted(bid, tid, standardOid(tid));
        String standard = version == 23 ? """
                <%1$s.Data.StandardSite TID="%2$s"><Name>catalog</Name></%1$s.Data.StandardSite>
                """.formatted(model, standardOid(tid)) : """
                <StandardSite i:tid="%s"><Name>catalog</Name></StandardSite>
                """.formatted(standardOid(tid));
        String evidence = version == 23 ? """
                <%1$s.Data.Evidence TID="%2$s"><EvidenceMarker REF="%3$s"/><EvidenceInspection REF="%4$s-inspection"/><Memo>evidence</Memo></%1$s.Data.Evidence>
                """.formatted(model, evidenceOid(tid), tid, bid) : """
                <Evidence i:tid="%s"><EvidenceMarker i:ref="%s"/><EvidenceInspection i:ref="%s-inspection"/><Memo>evidence</Memo></Evidence>
                """.formatted(evidenceOid(tid), tid, bid);
        String second = version == 23 ? """
                <%1$s.Data BID="%2$s"><%1$s.Data.Site TID="%3$s"><Label>second</Label></%1$s.Data.Site></%1$s.Data>
                """.formatted(model, oidBasket(bid + "-second"), secondOid(tid)) : """
                <Data i:bid="%s"><Site i:tid="%s"><Label>second</Label></Site></Data>
                """.formatted(oidBasket(bid + "-second"), secondOid(tid));
        String fixture = referenceDatasetFixture(model, version, oidBasket(bid), inspection + object + evidence + standard)
                .replace(version == 23 ? "</DATASECTION>" : "</i:datasection>",
                        second + defaults + (version == 23 ? "</DATASECTION>" : "</i:datasection>"));
        return Files.writeString(work.resolve(bid + ".xtf"), fixture);
    }

    private static String standardOid(String tid) {
        return "ch203400000000%02d".formatted(Math.max(0, Character.digit(tid.charAt(0), 16)));
    }

    private static String evidenceOid(String tid) {
        return tid.replace("-5C5A-", "-CCCC-");
    }

    private static String oidBasket(String name) {
        return java.util.UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String secondOid(String tid) {
        return tid.replace("-5C5A-", "-BBBB-");
    }

    private static String defaultOid(String tid) {
        return tid.replace("-5C5A-", "-ABCD-");
    }

    private static void assertOidRows(TestDatabase db, Connection sql, String bid, String tid) throws SQLException {
        assertEquals(tid.toLowerCase(Locale.ROOT), scalar(sql, "select s.t_ili_tid from " + db.table("site")
                + " s join " + db.table("marker") + " m on m.t_id=s.t_id and m.t_basket=s.t_basket"
                + " join " + db.table("t_ili2db_basket") + " b on b.t_id=s.t_basket where b.t_ili_tid='" + oidBasket(bid)
                + "' and s.label='marker' and s.t_type='marker' and m.category='survey'"));
        assertEquals(secondOid(tid).toLowerCase(Locale.ROOT), scalar(sql, "select s.t_ili_tid from " + db.table("site")
                + " s join " + db.table("t_ili2db_basket") + " b on b.t_id=s.t_basket where b.t_ili_tid='"
                + oidBasket(bid + "-second") + "' and s.label='second'"));
        assertOidReferenceRow(db, sql, bid + "-inspection", tid, bid, "localsite");
        String catalog = """
                select s.t_ili_tid from %s s join %s r on r.catalogid=s.t_id and r.t_basket=s.t_basket
                join %s i on r.inspection_links=i.t_id and r.t_basket=i.t_basket
                where i.t_ili_tid='%s-inspection' and s.aname='catalog'
                """.formatted(db.table("standardsite"), db.table("inspectionrefs"), db.table("inspection"), bid);
        assertEquals(standardOid(tid), scalar(sql, catalog));
        String evidence = """
                select e.t_ili_tid from %s e join %s b on b.t_id=e.t_basket
                join %s s on s.t_id=e.evidencemarker and s.t_basket=e.t_basket
                join %s i on i.t_id=e.evidenceinspection and i.t_basket=e.t_basket
                where b.t_ili_tid='%s' and e.memo='evidence' and s.t_ili_tid='%s' and i.t_ili_tid='%s-inspection'
                """.formatted(db.table("evidence"), db.table("t_ili2db_basket"), db.table("site"),
                db.table("inspection"), oidBasket(bid), tid.toLowerCase(Locale.ROOT), bid);
        assertEquals(evidenceOid(tid).toLowerCase(Locale.ROOT), scalar(sql, evidence));
        assertEquals(defaultOid(tid).toLowerCase(Locale.ROOT), scalar(sql, "select a.t_ili_tid from " + db.table("address")
                + " a join " + db.table("t_ili2db_basket") + " b on b.t_id=a.t_basket where b.t_ili_tid='"
                + bid + "-defaults' and a.atext='address'"));
    }

    private static void assertOidXtf(Path output, String model, int version, String bid, String tid) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(3, children(data).size());
        var basket = children(data).stream().filter(value -> id(value, version, "bid").equals(oidBasket(bid)))
                .findFirst().orElseThrow();
        var second = children(data).stream().filter(value -> id(value, version, "bid").equals(oidBasket(bid + "-second")))
                .findFirst().orElseThrow();
        assertEquals(secondOid(tid).toLowerCase(Locale.ROOT), id(children(second).getFirst(), version, "tid"));
        var object = child(basket, version == 23 ? model + ".Data.Marker" : "Marker");
        assertNotNull(object);
        assertEquals(tid.toLowerCase(Locale.ROOT), id(object, version, "tid"));
        assertEquals("marker", child(object, "Label").getTextContent());
        assertEquals("survey", child(object, "Category").getTextContent());
        var inspection = child(basket, version == 23 ? model + ".Data.Inspection" : "Inspection");
        var refs = children(child(inspection, "Links")).getFirst();
        assertExternalReference(child(refs, "LocalSite"), version, tid.toLowerCase(Locale.ROOT), "");
        assertExternalReference(child(refs, "CatalogId"), version, standardOid(tid), "");
        var standard = child(basket, version == 23 ? model + ".Data.StandardSite" : "StandardSite");
        assertEquals(standardOid(tid), id(standard, version, "tid"));
        assertEquals("catalog", child(standard, "Name").getTextContent());
        var evidence = child(basket, version == 23 ? model + ".Data.Evidence" : "Evidence");
        assertNotNull(evidence);
        assertEquals(evidenceOid(tid).toLowerCase(Locale.ROOT), id(evidence, version, "tid"));
        assertEquals("evidence", child(evidence, "Memo").getTextContent());
        assertExternalReference(child(evidence, "EvidenceMarker"), version, tid.toLowerCase(Locale.ROOT), "");
        assertExternalReference(child(evidence, "EvidenceInspection"), version, bid + "-inspection", "");
        var inherited = child(data, version == 23 ? model + ".Defaults" : "Defaults");
        assertEquals(bid + "-defaults", id(inherited, version, "bid"));
        assertEquals(defaultOid(tid).toLowerCase(Locale.ROOT),
                id(child(inherited, version == 23 ? model + ".Defaults.Address" : "Address"), version, "tid"));
    }

    private void exchangeReferenceDataset(ReferenceTool reference, TestDatabase db, Connection sql, String model,
                                          int version) throws Exception {
        String leaf = version == 23 ? "<%s.Data.TargetLeaf TID=\"catalog-leaf\"/>".formatted(model)
                : "<TargetLeaf i:tid=\"catalog-leaf\"/>";
        Path target = Files.writeString(work.resolve("catalog.xtf"), referenceDatasetFixture(model, version, "catalog-basket", leaf));
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "catalog", target.toString());
        String report = version == 23 ? """
                <%1$s.Data.Report TID="report-id"><DetailValue><%1$s.Data.Detail>
                  <TargetRef REF="catalog-leaf" BID="catalog-basket"/>
                </%1$s.Data.Detail></DetailValue>
                  <BaseSite REF="catalog-leaf" BID="catalog-basket"/>
                  <PairedSite REF="catalog-leaf" BID="catalog-basket"/>
                  <ReviewSite REF="catalog-leaf" BID="catalog-basket"><%1$s.Data.ExternalReview><ReviewMemo>catalogue check</ReviewMemo></%1$s.Data.ExternalReview></ReviewSite>
                </%1$s.Data.Report>
                <%1$s.Data.Report TID="direct-report-id"><ReviewSite REF="catalog-leaf" BID="catalog-basket"/></%1$s.Data.Report>
                <%1$s.Data.Report TID="empty-report-id"/>
                """.formatted(model) : """
                <Report i:tid="report-id"><DetailValue><Detail><TargetRef i:ref="catalog-leaf"/></Detail></DetailValue>
                  <DirectRef i:ref="catalog-leaf"/>
                  <BaseSite i:ref="catalog-leaf"/>
                  <PairedSite i:ref="catalog-leaf"/>
                  <ReviewSite i:ref="catalog-leaf"><ExternalReview><ReviewMemo>catalogue check</ReviewMemo></ExternalReview></ReviewSite>
                </Report>
                <Report i:tid="direct-report-id"><ReviewSite i:ref="catalog-leaf"/></Report>
                <Report i:tid="empty-report-id"/>
                """;
        Path input = Files.writeString(work.resolve("catalog-source.xtf"),
                referenceDatasetFixture(model, version, "catalog-source", report));
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "catalog-source", input.toString());
        assertReferenceDatasetRows(db, sql, version, "report-id");
        Path output = work.resolve("catalog-native-output.xtf");
        assertCliSuccess(db, "export", model, "catalog-source", output);
        assertReferenceDatasetXtf(output, version, true);

        Path other = Files.writeString(work.resolve("other-catalog.xtf"), referenceDatasetFixture(model, version, "other-basket", leaf));
        Path replay = Files.writeString(work.resolve("catalog-replay.xtf"),
                Files.readString(output).replace("report-id", "replay-report").replace("catalog-source", "replay-basket"));
        assertCliSuccess(db, "import", model, "catalog-replay", replay);
        assertReferenceDatasetRows(db, sql, version, "replay-report");
        Path referenceOutput = work.resolve("catalog-reference-output.xtf");
        reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "catalog-replay", referenceOutput.toString());
        assertReferenceDatasetXtf(referenceOutput, version, false);
        Path referenceReplay = Files.writeString(work.resolve("catalog-reference-replay.xtf"),
                Files.readString(referenceOutput).replace("replay-report", "reference-report").replace("replay-basket", "reference-basket"));
        assertCliSuccess(db, "import", model, "catalog-reference-replay", referenceReplay);
        assertReferenceDatasetRows(db, sql, version, "reference-report");

        if (version == 23) {
            Path wrongBid = Files.writeString(work.resolve("catalog-bad-bid.xtf"),
                    Files.readString(replay).replace("BID=\"catalog-basket\"", "BID=\"wrong-bid\""));
            assertReferenceDatasetRollback(db, sql, model, wrongBid, "catalog-leaf");
        }
        assertCliSuccess(db, "import", model, "other-catalog", other);
        assertReferenceDatasetRollback(db, sql, model, referenceReplay, "Ambiguous stored reference target catalog-leaf");
        if (version == 23) {
            assertCliSuccess(db, "export", model, "catalog-source", output);
            assertReferenceDatasetXtf(output, version, true);
        } else {
            String previous = Files.readString(output);
            var rejected = cli(db, "export", model, "catalog-source", output, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertTrue(rejected.diagnostics().contains("Ambiguous stored reference target catalog-leaf"), rejected::diagnostics);
            assertEquals(previous, Files.readString(output));
        }

        Path tail = Files.writeString(work.resolve("catalog-tail.xtf"),
                referenceDatasetFixture(model, version, "tail-catalog-basket", leaf.replace("catalog-leaf", "tail-catalog-leaf")));
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "catalog-tail", tail.toString());
        assertEquals("1", scalar(sql, "select count(*) from " + db.table("targetroot") + " where t_ili_tid='tail-catalog-leaf'"));
    }

    static String referenceDatasetFixture(String model, int version, String bid, String objects) {
        if (version == 23) return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="interop"><MODELS><MODEL NAME="%1$s" VERSION="1" URI="https://example.org/interlis/db"/></MODELS></HEADERSECTION>
                  <DATASECTION><%1$s.Data BID="%2$s">%3$s</%1$s.Data></DATASECTION>
                </TRANSFER>
                """.formatted(model, bid, objects);
        return """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="http://www.interlis.ch/xtf/2.4/%1$s">
                  <i:headersection><i:models><i:model>%1$s</i:model></i:models><i:sender>interop</i:sender></i:headersection>
                  <i:datasection><Data i:bid="%2$s">%3$s</Data></i:datasection>
                </i:transfer>
                """.formatted(model, bid, objects);
    }

    private static void assertReferenceDatasetRows(TestDatabase db, Connection sql, int version, String tid) throws SQLException {
        String query = """
                select r.t_basket<>b.t_id, ds.datasetname, dest.t_ili_tid, d.targetref, r.basesite, r.pairedsite%s
                from %s r join %s d on d.report_detailvalue=r.t_id and d.t_basket=r.t_basket
                join %s dest on dest.t_id=d.targetref
                join %s b on b.t_id=dest.t_basket
                join %s ds on ds.t_id=b.dataset where r.t_ili_tid='%s'
                """.formatted(version == 24 ? ", r.directref" : "", db.table("report"), db.table("detail"), db.table("targetroot"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"), tid);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertTrue(rows.getBoolean(1));
            assertEquals("catalog", rows.getString(2));
            assertEquals("catalog-leaf", rows.getString(3));
            assertEquals(rows.getLong(4), rows.getLong(5));
            assertEquals(rows.getLong(4), rows.getLong(6));
            if (version == 24) assertEquals(rows.getLong(4), rows.getLong(7));
            assertFalse(rows.next());
        }
        String target = "(select dest.t_id from " + db.table("targetroot") + " dest join "
                + db.table("t_ili2db_basket") + " b on b.t_id=dest.t_basket where dest.t_ili_tid='catalog-leaf' and b.t_ili_tid='catalog-basket')";
        String reviews = " from " + db.table("externalreview") + " l join " + db.table("report")
                + " r on r.t_id=l.reviewowner and l.t_basket=r.t_basket where l.reviewsite=" + target + " and r.t_ili_tid=";
        assertEquals("catalogue check", scalar(sql, "select l.reviewmemo" + reviews + "'" + tid + "'"));
        assertEquals("1", scalar(sql, "select count(*)" + reviews + "'direct-" + tid + "' and l.reviewmemo is null"));
        assertEquals("0", scalar(sql, "select count(*)" + reviews + "'empty-" + tid + "'"));
    }

    private static void assertReferenceDatasetXtf(Path output, int version, boolean nativeExport) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var objects = children(baskets.getFirst());
        assertEquals(3, objects.size());
        var report = objects.stream().filter(object -> child(object, "DetailValue") != null).findFirst().orElseThrow();
        var detail = children(child(report, "DetailValue")).getFirst();
        assertExternalReference(child(detail, "TargetRef"), version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        assertExternalReference(child(report, "BaseSite"), version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        assertExternalReference(child(report, "PairedSite"), version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        var review = child(report, "ReviewSite");
        assertExternalReference(review, version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        assertEquals("catalogue check", child(children(review).getFirst(), "ReviewMemo").getTextContent());
        var direct = objects.stream().filter(object -> id(object, version, "tid").startsWith("direct-")).findFirst().orElseThrow();
        var directReview = child(direct, "ReviewSite");
        assertExternalReference(directReview, version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        assertTrue(children(directReview).isEmpty());
        var empty = objects.stream().filter(object -> id(object, version, "tid").startsWith("empty-")).findFirst().orElseThrow();
        assertNull(child(empty, "ReviewSite"));
        if (version == 24) assertExternalReference(child(report, "DirectRef"), version, "catalog-leaf", "");
    }

    private static void assertReferenceDatasetRollback(TestDatabase db, Connection sql, String model, Path input,
                                                       String expected) throws SQLException {
        var result = cli(db, "import", model, "failed-catalog", input, CancellationToken.none());
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains(expected), result::diagnostics);
        assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_dataset") + " where datasetname='failed-catalog'"));
        assertEquals("1", scalar(sql, "select count(*) from " + db.table("report") + " where t_ili_tid='replay-report'"));
        assertEquals("1", scalar(sql, "select count(*) from " + db.table("report") + " where t_ili_tid='reference-report'"));
    }

    private void exchangeExternalDatasets(ReferenceTool reference, TestDatabase db, Connection sql, String model,
                                          int version, String fixture) throws Exception {
        Path referenceInput = Files.writeString(work.resolve("external-input.xtf"), fixture);
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "external",
                referenceInput.toString());
        assertExternalRows(db, sql, version, "external");
        Path ownOutput = work.resolve("external-own-output.xtf");
        assertCliSuccess(db, "export", model, "external", ownOutput);
        assertExternalXtf(ownOutput, model, version, "external", true);

        Path ownInput = Files.writeString(work.resolve("external-own-input.xtf"), fixture.replace("external-", "own-"));
        assertCliSuccess(db, "import", model, "own", ownInput);
        assertExternalRows(db, sql, version, "own");
        Path referenceOutput = work.resolve("external-reference-output.xtf");
        reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "own", referenceOutput.toString());
        assertExternalXtf(referenceOutput, model, version, "own", false);

        Path replay = Files.writeString(work.resolve("external-replay.xtf"),
                Files.readString(referenceOutput).replace("own-", "replay-"));
        assertCliSuccess(db, "import", model, "replay", replay);
        assertExternalRows(db, sql, version, "replay");

        String wrongDataset = fixture.replace("external-", "failed-")
                .replace((version == 23 ? "REF" : "ref") + "=\"failed-late\"",
                        (version == 23 ? "REF" : "ref") + "=\"absent-late\"");
        assertExternalRollback(db, sql, model, wrongDataset, "absent-late", "dataset");
        if (version == 23) {
            String wrongBid = fixture.replace("external-", "failed-")
                    .replace("REF=\"failed-late\" BID=\"failed-last\"", "REF=\"failed-late\" BID=\"failed-first\"");
            assertExternalRollback(db, sql, model, wrongBid, "Wrong target BID", "failed-late");
        }
        String baseSite = version == 23 ? "BaseSite REF" : "BaseSite ili:ref";
        String wrongRoleTarget = fixture.replace("external-", "failed-")
                .replace(baseSite + "=\"failed-late\"", baseSite + "=\"failed-sibling\"");
        assertExternalRollback(db, sql, model, wrongRoleTarget, "ReportPlace.BaseSite", "failed-sibling");
        String pairObject = version == 23 ? "PairObject REF" : "PairObject ili:ref";
        String wrongLocalBasket = fixture.replace("external-", "failed-")
                .replace(pairObject + "=\"failed-backward\"", pairObject + "=\"failed-forward\"");
        assertExternalRollback(db, sql, model, wrongLocalBasket, "ExternalPair.PairObject", "failed-forward", "basket");

        String where = " where note='forward' and t_basket=(select t_basket from " + db.table("report")
                + " where t_ili_tid='external-forward')";
        String detail = db.table("detail");
        String target = scalar(sql, "select targetref from " + detail + where);
        String foreign = scalar(sql, "select t_id from " + db.table("targetroot") + " where t_ili_tid='own-sibling'");
        try {
            execute(sql, "update " + detail + " set targetref=" + foreign + where);
            assertExportRejected(db, model, "external", ownOutput, "same-dataset reference target TID", "Detail.TargetRef");
        } finally {
            execute(sql, "update " + detail + " set targetref=" + target + where);
        }
        String review = db.table("externalreview");
        execute(sql, "insert into " + review + " (t_id,t_basket,reviewowner,reviewsite,reviewmemo) "
                + "select -1,t_basket,reviewowner,reviewsite,reviewmemo from " + review
                + " where reviewowner=(select t_id from " + db.table("report") + " where t_ili_tid='external-forward')");
        try {
            assertExportRejected(db, model, "external", ownOutput, "Multiple link rows", "ExternalReview.ReviewSite");
        } finally {
            execute(sql, "delete from " + review + " where t_id=-1");
        }
        Path tail = Files.writeString(work.resolve("external-tail.xtf"), fixture.replace("external-", "tail-"));
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "tail", tail.toString());
        assertExternalRows(db, sql, version, "tail");
    }

    private void assertExternalRollback(TestDatabase db, Connection sql, String model, String fixture, String... messages)
            throws Exception {
        var tables = List.of("t_ili2db_dataset", "t_ili2db_basket", "report", "detail", "targetroot", "targetleaf",
                "externalpair", "externalreview");
        var counts = new ArrayList<String>();
        for (String table : tables) counts.add(scalar(sql, "select count(*) from " + db.table(table)));
        Path input = Files.writeString(work.resolve("external-failed.xtf"), fixture);
        var result = cli(db, "import", model, "failed", input, CancellationToken.none());
        assertNotEquals(0, result.code(), result::diagnostics);
        for (String message : messages) assertTrue(result.diagnostics().contains(message), result::diagnostics);
        for (int index = 0; index < tables.size(); index++) {
            assertEquals(counts.get(index), scalar(sql, "select count(*) from " + db.table(tables.get(index))));
        }
    }

    private static void assertExternalRows(TestDatabase db, Connection sql, int version, String dataset) throws SQLException {
        String query = """
                select d.note, b.t_ili_tid, t.t_ili_tid, tb.t_ili_tid, t.t_type, t.t_id, leaf.t_id, r.basesite, r.pairedsite%s
                from %s d join %s r on r.t_id=d.report_detailvalue and r.t_basket=d.t_basket
                join %s b on b.t_id=d.t_basket join %s ds on ds.t_id=b.dataset
                left join %s t on t.t_id=d.targetref
                left join %s tb on tb.t_id=t.t_basket and tb.dataset=ds.t_id
                left join %s leaf on leaf.t_id=t.t_id and leaf.t_basket=t.t_basket
                where ds.datasetname='%s' order by d.note
                """.formatted(version == 24 ? ", r.directref" : "", db.table("detail"), db.table("report"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"), db.table("targetroot"),
                db.table("t_ili2db_basket"), db.table("targetleaf"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertEquals("absent", rows.getString(1));
            assertEquals(dataset + "-first", rows.getString(2));
            assertNull(rows.getString(3));
            assertNull(rows.getString(8));
            assertNull(rows.getString(9));
            if (version == 24) assertNull(rows.getString(10));
            for (String direction : List.of("backward", "forward")) {
                boolean backward = direction.equals("backward");
                assertTrue(rows.next());
                assertEquals(direction, rows.getString(1));
                assertEquals(dataset + (backward ? "-last" : "-first"), rows.getString(2));
                assertEquals(dataset + (backward ? "-early" : "-late"), rows.getString(3));
                assertEquals(dataset + (backward ? "-first" : "-last"), rows.getString(4));
                assertEquals("targetleaf", rows.getString(5));
                assertEquals(rows.getString(6), rows.getString(7));
                assertEquals(rows.getString(6), rows.getString(8));
                assertEquals(rows.getString(6), rows.getString(9));
                if (version == 24) assertEquals(rows.getString(6), rows.getString(10));
            }
            assertFalse(rows.next());
        }
        String links = """
                select r.t_ili_tid, t.t_ili_tid, b.t_ili_tid, tb.t_ili_tid from %s l
                join %s r on r.t_id=l.pairobject and r.t_basket=l.t_basket
                join %s t on t.t_id=l.pairtarget join %s b on b.t_id=l.t_basket
                join %s tb on tb.t_id=t.t_basket and tb.dataset=b.dataset
                join %s ds on ds.t_id=b.dataset where ds.datasetname='%s' order by r.t_ili_tid
                """.formatted(db.table("externalpair"), db.table("report"), db.table("targetroot"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(links)) {
            assertTrue(rows.next());
            assertEquals(List.of(dataset + "-backward", dataset + "-early", dataset + "-last", dataset + "-first"),
                    List.of(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)));
            assertTrue(rows.next());
            assertEquals(List.of(dataset + "-forward", dataset + "-late", dataset + "-first", dataset + "-last"),
                    List.of(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)));
            assertFalse(rows.next());
        }
        String reviews = """
                select r.t_ili_tid, t.t_ili_tid, l.reviewmemo from %s l
                join %s b on b.t_id=l.t_basket join %s ds on ds.t_id=b.dataset
                left join %s r on r.t_id=l.reviewowner and r.t_basket=l.t_basket
                left join %s t on t.t_id=l.reviewsite and t.t_basket<>l.t_basket
                where ds.datasetname='%s' order by r.t_ili_tid
                """.formatted(db.table("externalreview"), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"),
                db.table("report"), db.table("targetroot"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(reviews)) {
            assertTrue(rows.next());
            assertEquals(dataset + "-backward", rows.getString(1));
            assertEquals(dataset + "-early", rows.getString(2));
            assertNull(rows.getString(3));
            assertTrue(rows.next());
            assertEquals(dataset + "-forward", rows.getString(1));
            assertEquals(dataset + "-late", rows.getString(2));
            assertEquals("observed", rows.getString(3));
            assertFalse(rows.next());
        }
    }

    private static void assertExternalXtf(Path output, String model, int version, String dataset, boolean own)
            throws Exception {
        var reports = new HashMap<String, Element>();
        var targets = new HashMap<String, String>();
        var pairs = new HashSet<String>();
        for (Element basket : children(readDataSection(output, version))) {
            String bid = id(basket, version, "bid");
            for (Element object : children(basket)) {
                String name = object.getLocalName();
                String tid = id(object, version, "tid");
                if (name.equals("Report") || name.equals(model + ".Data.Report")) {
                    reports.put(tid, object);
                } else if (name.equals("ExternalPair") || name.equals(model + ".Data.ExternalPair")) {
                    assertEquals("", tid);
                    assertTrue(pairs.add(bid));
                    boolean first = bid.equals(dataset + "-first");
                    assertExternalReference(child(object, "PairObject"), version, dataset + (first ? "-forward" : "-backward"), "");
                    assertExternalReference(child(object, "PairTarget"), version, dataset + (first ? "-late" : "-early"),
                            own ? dataset + (first ? "-last" : "-first") : "");
                } else {
                    targets.put(tid, bid);
                }
            }
        }
        assertEquals(dataset + "-first", targets.get(dataset + "-early"));
        assertEquals(dataset + "-last", targets.get(dataset + "-late"));
        assertEquals(Set.of(dataset + "-first", dataset + "-last"), pairs);
        for (String direction : List.of("absent", "backward", "forward")) {
            var report = reports.get(dataset + "-" + direction);
            assertNotNull(report);
            var detail = children(child(report, "DetailValue")).getFirst();
            assertEquals(direction, text(detail, "Note"));
            var role = child(detail, "TargetRef");
            if (direction.equals("absent")) {
                assertNull(role);
                assertNull(child(report, "DirectRef"));
                assertNull(child(report, "BaseSite"));
                assertNull(child(report, "PairedSite"));
                assertNull(child(report, "ReviewSite"));
                continue;
            }
            String target = dataset + (direction.equals("backward") ? "-early" : "-late");
            String bid = own ? targets.get(target) : "";
            assertExternalReference(role, version, target, bid);
            assertExternalReference(child(report, "BaseSite"), version, target, bid);
            assertExternalReference(child(report, "PairedSite"), version, target, bid);
            var review = child(report, "ReviewSite");
            assertExternalReference(review, version, target, bid);
            var payload = children(review);
            if (direction.equals("forward")) {
                assertEquals(1, payload.size());
                var association = payload.getFirst();
                assertEquals(version == 23 ? model + ".Data.ExternalReview" : "ExternalReview", association.getLocalName());
                assertEquals("observed", text(association, "ReviewMemo"));
            } else {
                assertTrue(payload.isEmpty());
            }
            if (version == 24) assertExternalReference(child(report, "DirectRef"), version, target, "");
        }
    }

    private static void assertExternalReference(Element reference, int version, String tid, String bid) {
        assertNotNull(reference);
        assertEquals(tid, id(reference, version, "ref"));
        assertEquals(version == 23 ? bid : "", id(reference, version, "bid"));
    }

    private void exchangeExternalTopics(int version, PostgreSQLContainer postgres, ReferenceTool reference) throws Exception {
        String model = "DbExternalTopics" + version;
        Path ili = work.resolve(model + ".ili");
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + ili.getFileName())) {
            Files.copy(resource, ili);
        }
        String fixture;
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + model + ".xtf")) {
            fixture = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path referenceInput = Files.writeString(work.resolve("topics-input.xtf"), fixture);
        Path ownInput = Files.writeString(work.resolve("topics-own-input.xtf"), fixture.replace("fixture-", "own-"));
        Path tailInput = Files.writeString(work.resolve("topics-tail-input.xtf"), fixture.replace("fixture-", "tail-"));
        Path ownOutput = work.resolve("topics-own-output.xtf");
        Path referenceOutput = work.resolve("topics-reference-output.xtf");
        var referenceDb = new TestDatabase(postgres, "topics_reference");
        reference.run(referenceDb, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", ili.toString());
        var nativeDb = new TestDatabase(postgres, "topics_native");
        assertCliSuccess(nativeDb, "schemaimport", model, null, ili);
        try (var referenceSql = referenceDb.connect(); var nativeSql = nativeDb.connect()) {
            DbSchemaCatalog.read(referenceSql, referenceDb.postgis, referenceDb.schema)
                    .assertSameAs(DbSchemaCatalog.read(nativeSql, nativeDb.postgis, nativeDb.schema));
            assertQualifiedReferenceMapping(referenceDb, referenceSql, model + ".Surveys.SiteAnnotation.AnnotationSite",
                    "siteannotation", "annotationsite", "registeredsite");
            assertQualifiedReferenceMapping(referenceDb, referenceSql, model + ".Surveys.SiteAnnotation.FlatSite",
                    "siteannotation", "flatsite", "landmark");
            if (version == 24) {
                assertQualifiedReferenceMapping(referenceDb, referenceSql, model + ".Surveys.SurveyRecord.DirectSite",
                        "surveyrecord", "directsite", "registeredsite");
            }
            assertQualifiedReferenceMapping(referenceDb, referenceSql, model + ".Surveys.SurveyArea.MarkedSite",
                    "surveyrecord", "markedsite", "registeredsite");
            assertEquals("embedded", scalar(referenceSql, "select setting from " + referenceDb.table("t_ili2db_trafo")
                    + " where iliname='" + model + ".Surveys.SurveyArea' and tag='ch.ehi.ili2db.inheritance'"));
            assertQualifiedAssociationCatalog(referenceDb, referenceSql, model + ".Surveys", "SurveyReview",
                    Map.of("Reviews", "surveyrecord", "ReviewedAt", "registeredsite"));
            assertQualifiedAssociationCatalog(referenceDb, referenceSql, model + ".Surveys", "SurveyMatch",
                    Map.of("MatchedRecord", "surveyrecord", "Surveyed", "registeredsite"));
            assertQualifiedAssociationCatalog(referenceDb, referenceSql, model + ".Surveys", "SitePermit",
                    Map.of("IssuedPermit", "permitrecord", "CoveredSite", "registeredsite"));
        }
        for (var db : List.of(referenceDb, nativeDb)) {
            try (var sql = db.connect()) {
                reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "fixture",
                        referenceInput.toString());
                assertExternalTopicRows(db, sql, model, version, "fixture");
                assertCliSuccess(db, "export", model, "fixture", ownOutput);
                assertExternalTopicXtf(ownOutput, model, version, "fixture", true);
                assertWrongFlatTargetTopicRejected(db, sql, model, ownOutput);
                assertCliSuccess(db, "import", model, "own", ownInput);
                assertExternalTopicRows(db, sql, model, version, "own");
                reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "own", referenceOutput.toString());
                assertExternalTopicXtf(referenceOutput, model, version, "own", false);
                reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "tail",
                        tailInput.toString());
                assertExternalTopicRows(db, sql, model, version, "tail");
                exchangeExternalTopicDatasets(reference, db, sql, model, version, fixture);
            }
        }
    }

    private void exchangeExternalTopicDatasets(ReferenceTool reference, TestDatabase db, Connection sql, String model,
                                               int version, String fixture) throws Exception {
        String catalog = version == 23 ? model + ".Catalog" : "Catalog";
        String surveys = version == 23 ? model + ".Surveys" : "Surveys";
        String split = fixture.replace("fixture-", "split-");
        Path target = Files.writeString(work.resolve("topics-split-catalog.xtf"), withoutBasket(split, surveys));
        Path source = Files.writeString(work.resolve("topics-split-surveys.xtf"), withoutBasket(split, catalog));
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "split-catalog",
                target.toString());
        reference.run(db, work.toString(), "--import", "--importTid", "--importBid", "--dataset", "split",
                source.toString());
        assertSplitTopicLinks(db, sql, "split");
        Path ownOutput = work.resolve("topics-split-own-output.xtf");
        assertCliSuccess(db, "export", model, "split", ownOutput);
        assertSplitTopicLinksXtf(ownOutput, model, version, "split", true);
        Path local = Files.writeString(work.resolve("topics-split-native.xtf"),
                Files.readString(source).replace("split-survey", "split-own-survey").replace("split-permit", "split-own-permit"));
        assertCliSuccess(db, "import", model, "split-own", local);
        assertSplitTopicLinks(db, sql, "split-own");
        Path referenceOutput = work.resolve("topics-split-reference-output.xtf");
        reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "split-own", referenceOutput.toString());
        assertSplitTopicLinksXtf(referenceOutput, model, version, "split-own", false);

        String role = version == 23 ? "MatchedRecord REF" : "MatchedRecord i:ref";
        Path broken = Files.writeString(work.resolve("topics-split-local-error.xtf"), Files.readString(local)
                .replace(role + "=\"split-own-survey\"", role + "=\"split-survey\""));
        var rejected = cli(db, "import", model, "split-failed", broken, CancellationToken.none());
        assertNotEquals(0, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("MatchedRecord") && rejected.diagnostics().contains("basket"), rejected::diagnostics);
        assertNull(scalar(sql, "select t_id from " + db.table("t_ili2db_dataset") + " where datasetname='split-failed'"));
    }

    private static String withoutBasket(String fixture, String tag) {
        String name = java.util.regex.Pattern.quote(tag);
        return fixture.replaceFirst("(?s)<" + name + " .*?</" + name + ">", "");
    }

    private static void assertSplitTopicLinks(TestDatabase db, Connection sql, String source) throws SQLException {
        assertExternalTopicLink(db, sql, "surveymatch", "surveyrecord", "matchedrecord", "surveyed",
                source, source + "-survey", null, "split");
        assertExternalTopicLink(db, sql, "sitepermit", "permitrecord", "issuedpermit", "coveredsite",
                source, source + "-permit", "access", "split");
    }

    private static void assertSplitTopicLinksXtf(Path output, String model, int version, String source, boolean own)
            throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(source + "-surveys", id(baskets.getFirst(), version, "bid"));
        String prefix = version == 23 ? model + ".Surveys." : "";
        var match = child(baskets.getFirst(), prefix + "SurveyMatch");
        var permit = child(baskets.getFirst(), prefix + "SitePermit");
        String bid = own ? "split-catalog" : "";
        assertExternalReference(child(match, "Surveyed"), version, "split-leaf", bid);
        assertExternalReference(child(match, "MatchedRecord"), version, source + "-survey", "");
        assertExternalReference(child(permit, "CoveredSite"), version, "split-leaf", bid);
        assertExternalReference(child(permit, "IssuedPermit"), version, source + "-permit", "");
        assertEquals("access", child(permit, "Reason").getTextContent());
    }

    private static void assertWrongFlatTargetTopicRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String update = "update " + db.table("landmark") + " set t_basket=(select t_id from "
                + db.table("t_ili2db_basket") + " where t_ili_tid='fixture-%s') where t_ili_tid='fixture-landmark'";
        execute(sql, update.formatted("surveys"));
        try {
            assertExportRejected(db, model, "fixture", output, "SiteAnnotation.FlatSite");
        } finally {
            execute(sql, update.formatted("catalog"));
        }
    }

    private static void assertExternalTopicRows(TestDatabase db, Connection sql, String model, int version, String dataset)
            throws SQLException {
        String query = """
                select src.topic, dst.topic, t.t_ili_tid, t.t_type, t.t_id, r.markedsite, v.reviewedat, v.reviewmemo%s
                from %s r join %s a on a.surveyrecord_annotation=r.t_id and a.t_basket=r.t_basket
                join %s v on v.reviews=r.t_id and v.t_basket=r.t_basket
                join %s t on t.t_id=a.annotationsite join %s leaf on leaf.t_id=t.t_id and leaf.t_basket=t.t_basket
                join %s src on src.t_id=r.t_basket join %s dst on dst.t_id=t.t_basket and dst.dataset=src.dataset
                where r.t_ili_tid='%s-survey'
                """.formatted(version == 24 ? ", r.directsite" : "", db.table("surveyrecord"), db.table("siteannotation"),
                db.table("surveyreview"), db.table("registry"), db.table("siteleaf"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_basket"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertEquals(model + ".Surveys", rows.getString(1));
            assertEquals(model + ".Catalog", rows.getString(2));
            assertEquals(dataset + "-leaf", rows.getString(3));
            assertEquals("siteleaf", rows.getString(4));
            assertEquals(rows.getString(5), rows.getString(6));
            assertEquals(rows.getString(5), rows.getString(7));
            assertEquals("seen", rows.getString(8));
            if (version == 24) assertEquals(rows.getString(5), rows.getString(9));
            assertFalse(rows.next());
        }
        assertExternalTopicLink(db, sql, "surveymatch", "surveyrecord", "matchedrecord", "surveyed",
                dataset, dataset + "-survey", null, dataset);
        assertExternalTopicLink(db, sql, "sitepermit", "permitrecord", "issuedpermit", "coveredsite",
                dataset, dataset + "-permit", "access", dataset);
    }

    private static void assertExternalTopicLink(TestDatabase db, Connection sql, String linkTable, String localTable,
                                                String localColumn, String externalColumn, String dataset,
                                                String localTid, String reason, String target) throws SQLException {
        String query = """
                select local.t_ili_tid, foreign_site.t_ili_tid, src.t_ili_tid, dst.t_ili_tid, %s from %s link
                join %s src on src.t_id=link.t_basket
                join %s ds on ds.t_id=src.dataset
                left join %s local on local.t_id=link.%s and local.t_basket=link.t_basket
                left join %s foreign_site on foreign_site.t_id=link.%s
                left join %s dst on dst.t_id=foreign_site.t_basket
                where ds.datasetname='%s'
                """.formatted(reason == null ? "null" : "link.reason", db.table(linkTable), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), db.table(localTable), localColumn, db.table("registry"), externalColumn,
                db.table("t_ili2db_basket"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertEquals(localTid, rows.getString(1));
            assertEquals(target + "-leaf", rows.getString(2));
            assertEquals(dataset + "-surveys", rows.getString(3));
            assertEquals(target + "-catalog", rows.getString(4));
            assertEquals(reason, rows.getString(5));
            assertFalse(rows.next());
        }
    }

    private static void assertExternalTopicXtf(Path output, String model, int version, String dataset, boolean own)
            throws Exception {
        String catalog = version == 23 ? model + ".Catalog" : "Catalog";
        String surveys = version == 23 ? model + ".Surveys" : "Surveys";
        var section = readDataSection(output, version);
        var source = child(section, surveys);
        var target = child(section, catalog);
        assertEquals(dataset + "-surveys", id(source, version, "bid"));
        assertEquals(dataset + "-catalog", id(target, version, "bid"));
        var report = child(source, version == 23 ? surveys + ".SurveyRecord" : "SurveyRecord");
        var leaf = child(target, version == 23 ? catalog + ".SiteLeaf" : "SiteLeaf");
        assertEquals(dataset + "-survey", id(report, version, "tid"));
        assertEquals(dataset + "-leaf", id(leaf, version, "tid"));
        var landmark = child(target, version == 23 ? catalog + ".Landmark" : "Landmark");
        assertEquals(dataset + "-landmark", id(landmark, version, "tid"));
        String bid = own ? dataset + "-catalog" : "";
        var note = children(child(report, "Annotation")).getFirst();
        assertExternalReference(child(note, "AnnotationSite"), version, dataset + "-leaf", bid);
        assertExternalReference(child(note, "FlatSite"), version, dataset + "-landmark", bid);
        if (version == 24) assertExternalReference(child(report, "DirectSite"), version, dataset + "-leaf", "");
        assertExternalReference(child(report, "MarkedSite"), version, dataset + "-leaf", bid);
        var reviewed = child(report, "ReviewedAt");
        assertExternalReference(reviewed, version, dataset + "-leaf", bid);
        var payload = children(reviewed);
        assertEquals(1, payload.size());
        assertEquals(version == 23 ? surveys + ".SurveyReview" : "SurveyReview", payload.getFirst().getLocalName());
        assertEquals("seen", text(payload.getFirst(), "ReviewMemo"));
        var match = child(source, version == 23 ? surveys + ".SurveyMatch" : "SurveyMatch");
        assertEquals("", id(match, version, "tid"));
        assertExternalReference(child(match, "Surveyed"), version, dataset + "-leaf", bid);
        assertExternalReference(child(match, "MatchedRecord"), version, dataset + "-survey", "");
        var permit = child(source, version == 23 ? surveys + ".SitePermit" : "SitePermit");
        assertEquals("", id(permit, version, "tid"));
        assertExternalReference(child(permit, "CoveredSite"), version, dataset + "-leaf", bid);
        assertExternalReference(child(permit, "IssuedPermit"), version, dataset + "-permit", "");
        assertEquals("access", text(permit, "Reason"));
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

    private static void assertAssociationCatalog(TestDatabase db, Connection sql, String model, String name,
                                                 Map<String, String> roles) throws SQLException {
        assertQualifiedAssociationCatalog(db, sql, model + ".Data", name, roles);
    }

    private static void assertQualifiedAssociationCatalog(TestDatabase db, Connection sql, String topic, String name,
                                                          Map<String, String> roles) throws SQLException {
        String association = topic + "." + name;
        String table = name.toLowerCase(Locale.ROOT);
        assertEquals(table, scalar(sql, "select sqlname from " + db.table("t_ili2db_classname")
                + " where iliname='" + association + "'"));
        assertEquals("ASSOCIATION", scalar(sql, "select setting from " + db.table("t_ili2db_table_prop")
                + " where tablename='" + table + "' and tag='ch.ehi.ili2db.tableKind'"));
        assertEquals("newClass", scalar(sql, "select setting from " + db.table("t_ili2db_trafo")
                + " where iliname='" + association + "' and tag='ch.ehi.ili2db.inheritance'"));
        for (var role : roles.entrySet()) {
            String target = role.getValue();
            String column = role.getKey().toLowerCase(Locale.ROOT);
            assertEquals("1", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                    + " where iliname='" + association + "." + role.getKey() + "' and sqlname='" + column
                    + "' and colowner='" + table + "' and target='" + target + "'"));
            assertEquals(target, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                    + " where tablename='" + table + "' and subtype is null and columnname='" + column
                    + "' and tag='ch.ehi.ili2db.foreignKey'"));
        }
        String identityColumn = db.postgis
                ? "select column_name from information_schema.columns where table_schema='" + db.schema
                        + "' and table_name='" + table + "' and column_name='t_ili_tid'"
                : "select name from pragma_table_info('" + table + "') where lower(name)='t_ili_tid'";
        assertNull(scalar(sql, identityColumn));
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

    private static void assertMissingAssociationFragmentRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String basket = scalar(sql, "select t_id from " + db.table("t_ili2db_basket") + " where t_ili_tid='legacy-basket-2'");
        String id = scalar(sql, "select t_id from " + db.table("allocationbase") + " where t_basket=" + basket);
        assertNotNull(id);
        String leaf = db.table("taggedallocation");
        execute(sql, "delete from " + leaf + " where t_id=" + id);
        try {
            assertExportRejected(db, model, "legacy", output, "Missing inheritance fragment in taggedallocation");
        } finally {
            execute(sql, "insert into " + leaf + " (t_id,t_basket,detailrank) values (" + id + "," + basket + ",17)");
        }
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

    private static void assertCorruptHierarchyRoleRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String mast = db.table("mast");
        String service = db.table("servicebase");
        String row = " where t_ili_tid='legacy-serviceunit'";
        String original = scalar(sql, "select supervisor from " + service + row);
        try {
            for (String tid : List.of("legacy-beacon", "native-relay")) {
                execute(sql, "update " + service + " set supervisor=(select t_id from " + mast
                        + " where t_ili_tid='" + tid + "')" + row);
                assertExportRejected(db, model, "legacy", output, "HierarchyCrew.Supervisor");
            }
        } finally {
            execute(sql, "update " + service + " set supervisor=" + original + row);
        }
    }

    private void assertWrongHierarchyRoleRollback(TestDatabase db, Connection sql, String model, int version,
                                                   String fixture) throws Exception {
        String role = "<Supervisor " + (version == 24 ? "i:ref" : "REF") + "=";
        String broken = fixture.replace(role + "\"legacy-relay\"", role + "\"legacy-beacon\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve("wrong-role-branch.xtf"), broken.replace("legacy-", "wrong-role-branch-"));
        var rejected = cli(db, "import", model, "wrong-role-branch", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("HierarchyCrew.Supervisor")
                && rejected.diagnostics().contains("wrong-role-branch-beacon"), rejected::diagnostics);
        assertRows(db, sql, version, 2);
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

    private static void assertReferenceMapping(TestDatabase db, Connection sql, String model, String attribute,
                                               String owner, String column, String target) throws SQLException {
        assertQualifiedReferenceMapping(db, sql, model + ".Data." + attribute, owner, column, target);
    }

    private static void assertQualifiedReferenceMapping(TestDatabase db, Connection sql, String attribute,
                                                        String owner, String column, String target) throws SQLException {
        assertEquals("1", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                + " where iliname='" + attribute + "' and sqlname='" + column
                + "' and colowner='" + owner + "' and target='" + target + "'"));
        assertEquals(target, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                + " where tablename='" + owner + "' and subtype is null and columnname='" + column
                + "' and tag='ch.ehi.ili2db.foreignKey'"));
    }

    private static void assertCorruptHierarchyLinkRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String links = db.table("hierarchylink");
        String mast = db.table("mast");
        String service = db.table("servicebase");
        String row = " where t_basket=(select t_basket from " + mast + " where t_ili_tid='legacy-relay')";
        String branch = scalar(sql, "select workbranch from " + links + row);
        String staff = scalar(sql, "select staffroot from " + links + row);
        String sibling = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-beacon'");
        String otherBasket = scalar(sql, "select t_id from " + service + " where t_ili_tid='native-serviceunit'");
        try {
            execute(sql, "update " + links + " set workbranch=" + sibling + row);
            assertExportRejected(db, model, "legacy", output, "HierarchyLink.WorkBranch");
            execute(sql, "update " + links + " set workbranch=" + branch + ", staffroot=" + otherBasket + row);
            assertExportRejected(db, model, "legacy", output, "HierarchyLink.StaffRoot");
        } finally {
            execute(sql, "update " + links + " set workbranch=" + branch + ", staffroot=" + staff + row);
        }
    }

    private static void assertCorruptTernaryLinkRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String links = db.table("dispatchrecord");
        String mast = db.table("mast");
        String row = " where t_basket=(select t_basket from " + mast + " where t_ili_tid='legacy-relay')";
        String branch = scalar(sql, "select dispatchmast from " + links + row);
        String sibling = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-beacon'");
        try {
            execute(sql, "update " + links + " set dispatchmast=" + sibling + row);
            assertExportRejected(db, model, "legacy", output, "DispatchRecord.DispatchMast");
        } finally {
            execute(sql, "update " + links + " set dispatchmast=" + branch + row);
        }
    }

    private void assertWrongStandaloneBranchRollback(TestDatabase db, Connection sql, String model, int version,
                                                     String fixture, String association, String role, String dataset)
            throws Exception {
        String ref = version == 24 ? "i:ref" : "REF";
        String field = "<" + role + " " + ref + "=";
        String broken = fixture.replace(field + "\"legacy-relay\"", field + "\"legacy-beacon\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve(dataset + ".xtf"), broken.replace("legacy-", dataset + "-"));
        var rejected = cli(db, "import", model, dataset, input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(association + "." + role)
                && rejected.diagnostics().contains(dataset + "-beacon"), rejected::diagnostics);
        assertRows(db, sql, version, 2);
    }

    private static void assertCorruptPolymorphicTargetsRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String table = db.table("targetpair");
        String pair = " where label='forward' and refcarrier_pairs=(select t_id from " + db.table("refcarrier")
                + " where t_ili_tid='legacy-refcarrier')";
        String change = "update " + table + " set ";
        String branch = scalar(sql, "select branchtarget from " + table + pair);
        String root = scalar(sql, "select roottarget from " + table + pair);
        String sibling = scalar(sql, "select t_id from " + db.table("mast") + " where t_ili_tid='legacy-beacon'");
        String otherBasket = scalar(sql, "select t_id from " + db.table("servicebase") + " where t_ili_tid='native-serviceunit'");

        execute(sql, change + "branchtarget=" + sibling + pair);
        assertExportRejected(db, model, "legacy", output, "TargetPair.BranchTarget");
        execute(sql, change + "branchtarget=" + branch + pair);
        execute(sql, change + "roottarget=" + otherBasket + pair);
        assertExportRejected(db, model, "legacy", output, "TargetPair.RootTarget");
        execute(sql, change + "roottarget=" + root + pair);
    }

    private void assertWrongTargetBranchRollback(TestDatabase db, Connection sql, String model, int version,
                                                 String fixture) throws Exception {
        String ref = version == 24 ? "i:ref" : "REF";
        String field = "<RootTarget " + ref + "=\"legacy-serviceunit\"/><BranchTarget " + ref + "=";
        String broken = fixture.replace(field + "\"legacy-relay\"", field + "\"legacy-beacon\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve("wrong-branch.xtf"), broken.replace("legacy-", "wrong-branch-"));
        var rejected = cli(db, "import", model, "wrong-branch", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("TargetPair.BranchTarget")
                && rejected.diagnostics().contains("wrong-branch-beacon"), rejected::diagnostics);
        assertRows(db, sql, version, 2);
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

    private static void assertLocalReference(Element owner, String field, int version, String tid) {
        var value = child(owner, field);
        assertNotNull(value, field);
        assertEquals(tid, id(value, version, "ref"));
        assertEquals("", id(value, version, "bid"));
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

    private static void assertCorruptStructureTargetRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String structure = db.table("linkdetail");
        String place = db.table("place");
        String row = " where destination=(select t_id from " + place + " where t_ili_tid='legacy-place-3')";
        execute(sql, "update " + structure + " set destination=(select t_id from " + place
                + " where t_ili_tid='legacy-place-1')" + row);
        assertExportRejected(db, model, "legacy", output,
                model + ".Data.LinkDetail.Destination of object legacy-report-2");
        execute(sql, "update " + structure + " set destination=(select t_id from " + place
                + " where t_ili_tid='legacy-place-3') where destination=(select t_id from " + place
                + " where t_ili_tid='legacy-place-1')");
    }

    private void assertMissingStructureTargetRollback(TestDatabase db, Connection sql, String model, int version,
                                                      String fixture) throws Exception {
        String ref = version == 24 ? "i:ref" : "REF";
        String broken = fixture.replace("<Destination " + ref + "=\"legacy-place-3\"",
                "<Destination " + ref + "=\"missing-destination\"");
        Path input = Files.writeString(work.resolve("missing-structure-target.xtf"),
                broken.replace("legacy-", "missing-structure-target-"));
        var rejected = cli(db, "import", model, "missing-structure-target", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("missing-structure-target-report-2 at " + model + ".Data.LinkDetail.Destination")
                && rejected.diagnostics().contains("missing-destination"), rejected::diagnostics);
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset")
                + " where datasetname='missing-structure-target'"));
        assertRows(db, sql, version, 3);
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

    private static void assertTypeKind(TestDatabase db, Connection sql, String table, String column, String kind)
            throws SQLException {
        assertEquals(kind, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                + " where tablename='" + table + "' and columnname='" + column + "' and tag='ch.ehi.ili2db.typeKind'"));
    }

    private static void assertCorruptGeometriesRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String corruptLine = db.postgis
                ? "st_geomfromtext('COMPOUNDCURVE EMPTY', 2056)"
                : "X'4750000200000808'";
        execute(sql, "update " + db.table("route") + " set trace=" + corruptLine + " where t_ili_tid='tail-route-1'");
        assertExportRejected(db, model, "tail", output, model + ".Data.Route.Trace");

        String tower = db.table("tower");
        String mast = db.table("mast");
        String nativeRelay = " where t_id=(select t_id from " + mast + " where t_ili_tid='native-relay')";
        execute(sql, "update " + tower + " set alignment=" + corruptLine + nativeRelay);
        try {
            assertExportRejected(db, model, "native", output, model + ".Data.Tower.Alignment");
        } finally {
            execute(sql, "update " + tower + " set alignment=(select t.alignment from " + tower
                    + " t join " + mast + " m on m.t_id=t.t_id where m.t_ili_tid='legacy-relay')" + nativeRelay);
        }

        String corruptSurface = db.postgis
                ? "st_geomfromtext('CURVEPOLYGON EMPTY', 2056)"
                : "X'4750000000000808000000000A00000001'";
        execute(sql, "update " + db.table("plot") + " set footprint=" + corruptSurface
                + " where t_ili_tid='native-plot-1'");
        assertExportRejected(db, model, "native", output, model + ".Data.Plot.Footprint");
    }

    private static void assertCorruptMultiSurfaceRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String corrupt = db.postgis
                ? "st_geomfromtext('MULTISURFACE(CURVEPOLYGON((0 0,10 0,0 10,0 0)),CURVEPOLYGON EMPTY)', 2056)"
                : "X'4750000000000808000000000C00000001'";
        String park = db.table("park");
        String row = " where t_ili_tid='legacy-park-1'";
        execute(sql, "update " + park + " set coverage=" + corrupt + row);
        if (db.postgis) {
            assertEquals("2", scalar(sql, "select st_numgeometries(coverage) from " + park + row));
            assertEquals("true", scalar(sql, "select cast(st_isempty(st_geometryn(coverage, 2)) as text) from "
                    + park + row));
        }
        assertExportRejected(db, model, "legacy", output, model + ".Data.Park.Coverage");
    }

    private static void assertCorruptNewCollectionRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        if (db.postgis) {
            String survey = db.table("survey");
            String row = " where t_ili_tid='legacy-survey-1'";
            execute(sql, "update " + survey
                    + " set locations=st_geomfromtext('MULTIPOINT((2601500.625 1201500.500),EMPTY)', 2056)" + row);
            assertEquals("2", scalar(sql, "select st_numgeometries(locations) from " + survey + row));
            assertEquals("true", scalar(sql, "select cast(st_isempty(st_geometryn(locations, 2)) as text) from "
                    + survey + row));
            assertExportRejected(db, model, "legacy", output, model + ".Data.Survey.Locations");
        } else {
            execute(sql, "update routegroup set traces=X'4750000000000808000000000B00000001'"
                    + " where t_ili_tid='legacy-routegroup-1'");
            assertExportRejected(db, model, "legacy", output, model + ".Data.RouteGroup.Traces");
        }
    }

    private static void assertCorruptStructureRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String detail = db.table("detail");
        String parent = scalar(sql, "select t_id from " + db.table("report")
                + " where t_ili_tid='legacy-report-1'");
        String child = scalar(sql, "select t_id from " + detail + " where t_seq=0 and report_entries=" + parent);
        String row = " where t_id=" + child;
        execute(sql, "update " + detail + " set report_summary=report_entries" + row);
        assertExportRejected(db, model, "legacy", output, "owner");
        execute(sql, "update " + detail + " set report_summary=null" + row);

        execute(sql, "update " + detail + " set t_seq=1" + row);
        assertExportRejected(db, model, "legacy", output, "Duplicate or out-of-order T_Seq");
        execute(sql, "update " + detail + " set t_seq=0, report_entries=-1" + row);
        assertExportRejected(db, model, "legacy", output, "Orphan structure row");
        execute(sql, "update " + detail + " set report_entries=" + parent + row);

        String mapping = db.table("t_ili2db_attrname");
        String attribute = " where iliname='" + model + ".Data.Report.Entries'";
        execute(sql, "update " + mapping + " set sqlname='missing_report_entries'" + attribute);
        assertExportRejected(db, model, "legacy", output, "Report.Entries", "missing_report_entries");
        execute(sql, "update " + mapping + " set sqlname='report_entries'" + attribute);
    }

    private void assertNestedSequenceOrder(TestDatabase db, Connection sql, String model, int version)
            throws Exception {
        String leaf = db.table("nestedleaf");
        String parent = nestedInnerId(db, sql);
        String positions = " where nestedinner_values=" + parent + " and t_seq in (0, 1)";
        Path output = work.resolve("nested-reordered.xtf");
        execute(sql, "update " + leaf + " set t_seq=1-t_seq" + positions);
        try {
            assertCliSuccess(db, "export", model, "legacy", output);
            var record = children(readDataSection(output, version)).stream().flatMap(basket -> children(basket).stream())
                    .filter(object -> id(object, version, "tid").equals("legacy-nested-1")).findFirst().orElseThrow();
            assertTransferredNestedTree(record, version, List.of("3", "2", "2"));
        } finally {
            execute(sql, "update " + leaf + " set t_seq=1-t_seq" + positions);
        }
    }

    private static void assertCorruptNestedStructureRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String leaf = db.table("nestedleaf");
        String parent = nestedInnerId(db, sql);
        String child = scalar(sql, "select t_id from " + leaf + " where nestedinner_values=" + parent + " and t_seq=0");
        String row = " where t_id=" + child;
        execute(sql, "update " + leaf + " set nestedinner_values=-1" + row);
        try {
            assertExportRejected(db, model, "legacy", output, "Orphan structure row", "NestedInner.Values");
        } finally {
            execute(sql, "update " + leaf + " set nestedinner_values=" + parent + row);
        }
    }

    private static String nestedInnerId(TestDatabase db, Connection sql) throws SQLException {
        return scalar(sql, """
                select i.t_id from %s i join %s o on o.t_id=i.nestedouter_items
                join %s r on r.t_id=o.nestedrecord_tree where r.t_ili_tid='legacy-nested-1' and i.t_seq=0
                """.formatted(db.table("nestedinner"), db.table("nestedouter"), db.table("nestedrecord")));
    }

    private static void assertCorruptRoleMappingRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String mapping = db.table("t_ili2db_attrname");
        var roles = Map.of("MarkerHub.Select", "hub", "MarkerLink.LinkedHub", "hub", "Review.Witness", "hub",
                "AnnotatedLink.ConsultedReport", "report");
        for (var entry : roles.entrySet()) {
            String role = model + ".Data." + entry.getKey();
            execute(sql, "update " + mapping + " set target='place' where iliname='" + role + "'");
            assertExportRejected(db, model, "legacy", output, role);
            execute(sql, "update " + mapping + " set target='" + entry.getValue() + "' where iliname='" + role + "'");
        }
    }

    private static void assertAmbiguousReviewRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String review = db.table("review");
        String hub = db.table("hub");
        String marker = db.table("marker");
        String duplicateId = scalar(sql, "select max(t_id)+1000 from " + review);
        execute(sql, """
                insert into %s (t_id, t_basket, witness, reviewmarkers)
                select %s, r.t_basket, (select t_id from %s where t_ili_tid='legacy-hub-2'), r.reviewmarkers
                from %s r join %s m on m.t_id=r.reviewmarkers where m.t_ili_tid='legacy-marker-1'
                """.formatted(review, duplicateId, hub, review, marker));
        assertExportRejected(db, model, "legacy", output, "Review.Witness");
        execute(sql, "delete from " + review + " where t_id=" + duplicateId);
    }

    private static void assertWrongRelayReviewOwnerRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String mast = db.table("mast");
        String valid = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-relay'");
        String invalid = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-beacon'");
        String update = "update " + db.table("relayreview") + " set reviewingrelays=";
        execute(sql, update + invalid + " where reviewingrelays=" + valid);
        try {
            assertExportRejected(db, model, "legacy", output,
                    "Missing or inconsistent link owner at " + model + ".Data.RelayReview.Recommended");
        } finally {
            execute(sql, update + valid + " where reviewingrelays=" + invalid);
        }
    }

    private static void assertAmbiguousRatingRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String table = db.table("rating");
        String id = scalar(sql, "select max(t_id)+1000 from " + table);
        execute(sql, """
                insert into %s (t_id, t_basket, rated, ratingforms)
                select %s, r.t_basket, r.rated, r.ratingforms from %s r
                join %s f on f.t_id=r.ratingforms where f.t_ili_tid='legacy-ratingform-1'
                """.formatted(table, id, table, db.table("ratingform")));
        try {
            assertExportRejected(db, model, "legacy", output, "Multiple link rows at " + model + ".Data.Rating.Rated");
        } finally {
            execute(sql, "delete from " + table + " where t_id=" + id);
        }
    }

    private static void assertWrongStructureBranchRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String notes = db.table("notebase");
        String owner = scalar(sql, "select t_id from " + db.table("typedrecord") + " where t_ili_tid='legacy-typed-1'");
        String main = " where t_id=" + scalar(sql, "select t_id from " + notes + " where typedrecord_main=" + owner);
        String sibling = " where t_id=" + scalar(sql, "select t_id from " + notes
                + " where typedrecord_values=" + owner + " and t_type='flagnote'");
        // Both rows retain valid SQL fragments, but Main is declared as CountNote.
        execute(sql, "update " + notes + " set typedrecord_main=null, typedrecord_values=" + owner + ", t_seq=2" + main);
        execute(sql, "update " + notes + " set typedrecord_values=null, typedrecord_main=" + owner + ", t_seq=0" + sibling);
        try {
            assertExportRejected(db, model, "legacy", output, "TypedRecord.Main", "flagnote");
        } finally {
            execute(sql, "update " + notes + " set typedrecord_main=null, typedrecord_values=" + owner + ", t_seq=2" + sibling);
            execute(sql, "update " + notes + " set typedrecord_values=null, typedrecord_main=" + owner + ", t_seq=0" + main);
        }
    }

    private static void assertCorruptClassHierarchyRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String root = db.table("mast");
        String relay = db.table("arelay");
        String beacon = db.table("beacon");
        String rootRow = " where t_ili_tid='legacy-relay'";
        String id = scalar(sql, "select t_id from " + root + rootRow);
        String basket = scalar(sql, "select t_basket from " + root + rootRow);
        execute(sql, "update " + root + " set t_type='unknown'" + rootRow);
        assertExportRejected(db, model, "legacy", output, "T_Type", "mast");
        execute(sql, "update " + root + " set t_type='arelay'" + rootRow);

        execute(sql, "delete from " + relay + " where t_id=" + id);
        assertExportRejected(db, model, "legacy", output, "relay", "Relay");
        execute(sql, "insert into " + relay + " (t_id,t_basket,aonline) values (" + id + "," + basket + ",false)");

        execute(sql, "insert into " + beacon + " (t_id,t_basket) values (" + id + "," + basket + ")");
        assertExportRejected(db, model, "legacy", output, "beacon", "Beacon");
        execute(sql, "delete from " + beacon + " where t_id=" + id);

        String inheritance = db.table("t_ili2db_inheritance");
        String entry = " where thisclass='" + model + ".Data.Relay'";
        execute(sql, "update " + inheritance + " set baseclass='" + model + ".Data.Mast'" + entry);
        assertExportRejected(db, model, "legacy", output, "INHERITANCE", "Relay");
        execute(sql, "update " + inheritance + " set baseclass='" + model + ".Data.Tower'" + entry);
    }

    private static void assertCorruptInheritedOwnerRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String mast = db.table("mast");
        String outer = db.table("nestedouter");
        String relayId = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-relay'");
        String mastId = scalar(sql, "select t_id from " + mast + " where t_ili_tid='legacy-mast'");
        assertNotNull(relayId);
        assertNotNull(mastId);
        execute(sql, "update " + outer + " set tower_supplement=" + mastId + " where tower_supplement=" + relayId);
        try {
            assertExportRejected(db, model, "legacy", output, "Orphan structure row", "Tower.Supplement");
        } finally {
            execute(sql, "update " + outer + " set tower_supplement=" + relayId + " where tower_supplement=" + mastId);
        }
    }

    private void assertUnresolvedHierarchyReferenceRollback(TestDatabase db, Connection sql, String model,
                                                             int version, String fixture) throws Exception {
        String dataset = "unresolved-hierarchy-source";
        String ref = version == 24 ? "i:ref" : "REF";
        String noteReference = "            <BranchTarget " + ref + "=";
        String broken = fixture.replace(noteReference + "\"legacy-relay\"", noteReference + "\"missing-branch-target\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve(dataset + ".xtf"), broken.replace("legacy-", dataset + "-"));
        var rejected = cli(db, "import", model, dataset, input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data.RefNoteBranch.BranchTarget")
                && rejected.diagnostics().contains("missing-branch-target"), rejected::diagnostics);
        assertNull(scalar(sql, "select t_id from " + db.table("refnotebook")
                + " where t_ili_tid='" + dataset + "-ref-notebook'"));
        assertRows(db, sql, version, 3);
    }

    private void assertClassHierarchyRollback(TestDatabase db, Connection sql, String model, int version, String fixture)
            throws Exception {
        String condition = "exists (select 1 from " + db.table("mast") + " m join " + db.table("tower")
                + " t on t.t_id=m.t_id and t.t_basket=m.t_basket"
                + " where m.t_id=new.t_id and m.t_ili_tid='class-fail-relay' and t.height=24)";
        if (db.postgis) {
            String function = db.table("reject_relay_import");
            execute(sql, """
                    create function %s() returns trigger language plpgsql as $$
                    begin
                        if %s then raise exception using errcode = 'P0054', message = 'rollback after parent fragments'; end if;
                        return new;
                    end;
                    $$
                    """.formatted(function, condition));
            execute(sql, "create trigger reject_relay_import before insert on " + db.table("arelay")
                    + " for each row execute function " + function + "()");
        } else {
            execute(sql, """
                    create trigger reject_relay_import before insert on arelay when %s
                    begin select raise(abort, 'rollback after parent fragments'); end
                    """.formatted(condition));
        }
        Path input = Files.writeString(work.resolve("class-rollback.xtf"), fixture.replace("legacy-", "class-fail-"));
        var rejected = cli(db, "import", model, "class-fail", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(db.postgis ? "SQL code P0054" : "SQL code"), rejected::diagnostics);
        assertNull(scalar(sql, "select t_id from " + db.table("mast") + " where t_ili_tid='class-fail-relay'"));
        assertRows(db, sql, version, 3);
    }

    private static void assertExportRejected(TestDatabase db, String model, String dataset, Path output, String... diagnostics)
            throws Exception {
        String original = Files.readString(output);
        var rejected = cli(db, "export", model, dataset, output, CancellationToken.none());
        assertNotEquals(0, rejected.code(), rejected::diagnostics);
        assertTrue(List.of(diagnostics).stream().anyMatch(rejected.diagnostics()::contains), rejected::diagnostics);
        assertEquals(original, Files.readString(output));
    }

    private void assertSchemaCreation(ReferenceTool reference, PostgreSQLContainer postgres, String model, int version,
                                      Path ili, String fixture, DbSchemaCatalog catalog) throws Exception {
        boolean postgis = postgres != null;
        var db = new TestDatabase(postgres, "native");
        CliResult creation = cli(db, "schemaimport", model, null, ili, CancellationToken.none());
        assertEquals(0, creation.code(), creation::diagnostics);
        try (var sql = db.connect()) {
            catalog.assertSameAs(DbSchemaCatalog.read(sql, postgis, db.schema));
            var duplicate = cli(db, "schemaimport", model, null, ili, CancellationToken.none());
            assertNotEquals(0, duplicate.code(), duplicate::diagnostics);
            catalog.assertSameAs(DbSchemaCatalog.read(sql, postgis, db.schema));
            assertEmptySchema(sql, db, version);

            // Only the stored model is available under its recorded filename in this direction.
            Path heldModel = Files.move(ili, work.resolve("held-model"));
            try {
                exchangeDatasets(reference, db, sql, STORED_MODELS, model, version, fixture);
            } finally {
                Files.move(heldModel, ili);
            }
        }

        var invalidDb = new TestDatabase(postgres, "invalid");
        Path invalid = Files.writeString(work.resolve("invalid.ili"), "INTERLIS 2.4; MODEL");
        var failure = cli(invalidDb, "schemaimport", model, null, invalid, CancellationToken.none());
        assertNotEquals(0, failure.code(), failure::diagnostics);
        assertMissingTarget(invalidDb);

        assertExistingTargetPreserved(postgres, model, ili);
        var cancelledDb = new TestDatabase(postgres, "cancelled");
        var plan = SchemaPlan.read(model, ili, !postgis, !postgis, 2056, CancellationToken.none());
        if (postgis) {
            try (var reserved = Database.openPostgres(cancelledDb.url, "select", cancelledDb.user,
                    cancelledDb.password, true)) {
                var rejected = assertThrows(DbException.class,
                        () -> SchemaSql.createPostgres(reserved, plan, false, CancellationToken.none()));
                assertTrue(rejected.getMessage().contains("reserved word"), rejected::getMessage);
            }
            try (var target = Database.openPostgres(cancelledDb.url, cancelledDb.schema, cancelledDb.user,
                    cancelledDb.password, true)) {
                var checks = new AtomicInteger();
                int srsChecks = plan.geometryEpsgCodes().size();
                // Let the SRS catalog checks finish and cancel at the first checkpoint after CREATE SCHEMA.
                assertThrows(OperationCancelledException.class,
                        () -> SchemaSql.createPostgres(target, plan, false, () -> checks.incrementAndGet() > srsChecks));
                assertEquals(cancelledDb.schema, scalar(target.connection(),
                        "select nspname from pg_namespace where nspname='" + cancelledDb.schema + "'"));
            }
        } else {
            int before = temporaryFiles();
            var checks = new AtomicInteger();
            // The fourth check precedes CREATE TABLE gpkg_spatial_ref_sys.
            assertThrows(OperationCancelledException.class,
                    () -> GeoPackageSchema.create(cancelledDb.file, plan, false, () -> checks.incrementAndGet() == 5));
            assertEquals(before, temporaryFiles());
        }
        assertMissingTarget(cancelledDb);
    }

    private int temporaryFiles() throws IOException {
        try (var files = Files.list(work)) {
            return Math.toIntExact(files.filter(file -> file.getFileName().toString().startsWith(".interlis-db-"))
                    .count());
        }
    }

    private void assertExistingTargetPreserved(PostgreSQLContainer postgres, String model, Path ili) throws Exception {
        var db = new TestDatabase(postgres, "occupied");
        if (db.postgis) {
            try (var sql = db.connect()) {
                execute(sql, "create schema " + db.schema);
                var failure = cli(db, "schemaimport", model, null, ili, CancellationToken.none());
                assertNotEquals(0, failure.code(), failure::diagnostics);
                assertEquals(db.schema, scalar(sql, "select nspname from pg_namespace where nspname='"
                        + db.schema + "'"));
                assertEquals("0", scalar(sql, "select count(*) from information_schema.tables where table_schema='"
                        + db.schema + "'"));
            }
        } else {
            String original = "Existing unrelated file";
            Files.writeString(db.file, original);
            var failure = cli(db, "schemaimport", model, null, ili, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertEquals(original, Files.readString(db.file));
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

        assertMissingReferenceRollback(db, sql, model, version, fixture, "LinkedHub", "MarkerLink", "missing-link", 2);
        assertMissingReferenceRollback(db, sql, model, version, fixture, "Witness", "Review", "missing-review", 2);
        assertMissingReferenceRollback(db, sql, model, version, fixture,
                "ConsultedMarker", "AnnotatedLink", "missing-annotated-link", 2);
        assertWrongTargetBranchRollback(db, sql, model, version, fixture);
        assertWrongHierarchyRoleRollback(db, sql, model, version, fixture);
        assertWrongStandaloneBranchRollback(db, sql, model, version, fixture,
                "HierarchyLink", "WorkBranch", "wrong-hierarchy-link");
        assertWrongStandaloneBranchRollback(db, sql, model, version, fixture,
                "DispatchRecord", "DispatchMast", "wrong-ternary-link");
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

    private static void assertEmptySchema(Connection sql, TestDatabase db, int version) throws SQLException {
        assertRows(db, sql, version, 0);
        if (db.postgis) {
            assertEquals("false", scalar(sql, "select cast(is_called as text) from " + db.table("t_ili2db_seq")));
        } else {
            assertEquals("0", scalar(sql, "select count(*) from t_key_object"));
        }
    }

    static void assertMissingTarget(TestDatabase db) throws SQLException {
        if (db.postgis) {
            try (var sql = db.connect()) {
                assertNull(scalar(sql, "select nspname from pg_namespace where nspname='" + db.schema + "'"));
            }
        } else {
            assertFalse(Files.exists(db.file), db.file::toString);
        }
    }

    private void assertRollback(TestDatabase db, Connection sql, String model, int version, String fixture)
            throws Exception {
        Path broken = Files.writeString(work.resolve("rollback.xtf"), fixture.replace("legacy-", "fail-"));
        if (db.postgis) {
            String sequenceValue = "select last_value from " + db.table("t_ili2db_seq");
            long before = Long.parseLong(scalar(sql, sequenceValue));
            // Sequence increments are visible outside the transaction. The third allocation is for the
            // first Route object, after the dataset and basket. The reader checks cancellation between inserts.
            CancellationToken afterFirstObject = () -> {
                try {
                    return Long.parseLong(scalar(sql, sequenceValue)) >= before + 3;
                } catch (SQLException failure) {
                    throw new AssertionError(failure);
                }
            };
            var cancelled = cli(db, "import", model, "fail", broken, afterFirstObject);
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(Long.toString(before + 3), scalar(sql, sequenceValue));
        } else {
            // The trigger fires after the line and first place have both reached the transaction.
            execute(sql, """
                    create trigger fail_place before insert on place
                    when new.t_ili_tid='fail-place-2' and exists(select 1 from place where t_ili_tid='fail-place-1')
                    and exists(select 1 from route where t_ili_tid='fail-route-1' and trace is not null)
                    begin select raise(abort, 'rollback probe after line'); end
                    """);
            var rejected = cli(db, "import", model, "fail", broken, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertTrue(rejected.diagnostics().contains("SQL code"), rejected::diagnostics);
        }
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='fail'"));
        assertRows(db, sql, version, 3);
    }

    private void assertStructureRollback(TestDatabase db, Connection sql, String model, int version, String fixture)
            throws Exception {
        // Reject the second basket's child after the flat collections and nested leaves reached the database.
        String condition = """
                new.label='later' and exists (
                    select 1 from %s d join %s r on r.t_id=d.report_entries
                    where r.t_ili_tid='structure-fail-report-1' and d.t_seq=2 and d.label='b'
                )
                and exists (
                    select 1 from %s d join %s r on r.t_id=d.report_observations
                    where r.t_ili_tid='structure-fail-report-1' and d.t_seq=2 and d.label='x'
                )
                and exists (
                    select 1 from %s l join %s i on i.t_id=l.nestedinner_values
                    join %s o on o.t_id=i.nestedouter_items join %s r on r.t_id=o.nestedrecord_tree
                    where r.t_ili_tid='structure-fail-nested-1' and l.t_seq=2 and l.amount=2
                )
                """.formatted(db.table("detail"), db.table("report"), db.table("detail"), db.table("report"),
                db.table("nestedleaf"), db.table("nestedinner"), db.table("nestedouter"), db.table("nestedrecord"));
        if (db.postgis) {
            String function = db.table("reject_detail_import");
            execute(sql, """
                    create function %s() returns trigger language plpgsql as $$
                    begin
                        if %s then raise exception using errcode = 'P0052', message = 'rollback after structure'; end if;
                        return new;
                    end;
                    $$
                    """.formatted(function, condition));
            execute(sql, "create trigger reject_detail_import before insert on " + db.table("detail")
                    + " for each row execute function " + function + "()");
        } else {
            execute(sql, """
                    create trigger reject_detail_import before insert on detail when %s
                    begin select raise(abort, 'rollback after structure'); end
                    """.formatted(condition));
        }
        Path input = Files.writeString(work.resolve("structure-rollback.xtf"), fixture.replace("legacy-", "structure-fail-"));
        var rejected = cli(db, "import", model, "structure-fail", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(db.postgis ? "SQL code P0052" : "SQL code"), rejected::diagnostics);
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset")
                + " where datasetname='structure-fail'"));
        assertRows(db, sql, version, 3);
    }

    private void assertMissingReferenceRollback(TestDatabase db, Connection sql, String model, int version,
                                                String fixture, String role, String affectedObject, String dataset,
                                                int existingDatasets) throws Exception {
        String ref = version == 24 ? "i:ref" : "REF";
        String roleStart = "<" + role + " " + ref + "=\"";
        String broken = fixture.replaceFirst(roleStart + "legacy-[^\"]+", roleStart + "unresolved-target");
        Path input = Files.writeString(work.resolve(dataset + "-target.xtf"), broken.replace("legacy-", dataset + "-"));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, dataset, input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(affectedObject)
                && rejected.diagnostics().contains("unresolved-target"), rejected::diagnostics);
        // PostgreSQL has allocated IDs for the dataset, first basket and all its rows, including both late Hubs.
        long allocated = version == 24 ? 35 : 30;
        assertEquals(Long.toString(db.postgis ? before + allocated : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset")
                + " where datasetname='" + dataset + "'"));
        assertRows(db, sql, version, existingDatasets);
    }

    private void assertExplicitRadiusRollback(TestDatabase db, Connection sql, String model, int version, String fixture)
            throws Exception {
        // The first arc follows the complete straight route in the same basket.
        String closing = version == 24 ? "</g:arc>" : "</ARC>";
        String radius = version == 24 ? "<g:r>5</g:r>" : "<R>5</R>";
        Path input = Files.writeString(work.resolve("radius.xtf"),
                fixture.replace("legacy-", "radius-").replaceFirst(closing, radius + closing));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "radius", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data.Route.Trace"), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("Explicit arc radius"), rejected::diagnostics);
        // On PostGIS the dataset, basket, straight route and rejected route each allocate an ID.
        // GeoPackage rolls back its counter together with the rows.
        assertEquals(Long.toString(db.postgis ? before + 4 : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='radius'"));
        assertRows(db, sql, version, 3);
    }

    private void assertOpenSurfaceRollback(TestDatabase db, Connection sql, String model, int version, String fixture)
            throws Exception {
        // Move only the closing position of the second plot, after the first plot and other objects.
        String x = "2601200.250";
        int plot = fixture.indexOf("legacy-plot-2");
        int closing = fixture.lastIndexOf(x, fixture.indexOf("</Footprint>", plot));
        assertTrue(closing > fixture.indexOf(x, plot));
        String openRing = fixture.substring(0, closing) + "2601200.375" + fixture.substring(closing + x.length());
        Path input = Files.writeString(work.resolve("open-surface.xtf"), openRing.replace("legacy-", "open-"));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "open", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data.Plot.Footprint"), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("closed"), rejected::diagnostics);
        // PostgreSQL allocates IDs for the dataset, basket, initial objects and the rejected plot.
        long allocated = version == 24 ? 10 : 9;
        assertEquals(Long.toString(db.postgis ? before + allocated : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='open'"));
        assertRows(db, sql, version, 3);
    }

    private void assertEmptyCollectionRollback(TestDatabase db, Connection sql, String model, String fixture,
                                               String tag, String attribute, String kind, long allocated) throws Exception {
        int start = fixture.indexOf("<g:" + tag + ">");
        String endTag = "</g:" + tag + ">";
        int end = fixture.indexOf(endTag, start) + endTag.length();
        String empty = fixture.substring(0, start) + "<g:" + tag + "/>" + fixture.substring(end);
        Path input = Files.writeString(work.resolve("empty-" + tag + ".xtf"), empty.replace("legacy-", "empty-"));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "empty", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data." + attribute), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("empty " + kind), rejected::diagnostics);
        // PostgreSQL allocates IDs for the dataset, basket, initial objects and the rejected object.
        assertEquals(Long.toString(db.postgis ? before + allocated : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='empty'"));
        assertRows(db, sql, 24, 3);
    }

    private void assertEmptyCollectionExport(TestDatabase db, Connection sql, String model) throws Exception {
        String emptySurface = emptyCollection(db, "MULTISURFACE", MULTISURFACE);
        for (String table : List.of("park", "district")) {
            execute(sql, "update " + db.table(table) + " set coverage=" + emptySurface
                    + " where t_ili_tid='legacy-" + table + "-1'");
        }
        execute(sql, "update " + db.table("routegroup") + " set traces=" + emptyCollection(db, "MULTICURVE", MULTICURVE)
                + " where t_ili_tid='legacy-routegroup-1'");
        execute(sql, "update " + db.table("survey") + " set locations=" + emptyCollection(db, "MULTIPOINT", MULTIPOINT)
                + " where t_ili_tid='legacy-survey-1'");
        Path output = work.resolve("empty-collection-export.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        var basket = children(readDataSection(output, 24)).stream()
                .filter(element -> id(element, 24, "bid").equals("legacy-basket-1")).findFirst().orElseThrow();
        var attributes = Map.of("Park", "Coverage", "District", "Coverage", "RouteGroup", "Traces", "Survey", "Locations");
        for (var attribute : attributes.entrySet()) {
            var object = child(basket, attribute.getKey());
            assertNotNull(object);
            assertEquals("legacy-" + attribute.getKey().toLowerCase(Locale.ROOT) + "-1", id(object, 24, "tid"));
            assertNull(child(object, attribute.getValue()));
        }
    }

    static String emptyCollection(TestDatabase db, String geometryType, int wkbType) {
        return db.postgis ? "st_geomfromtext('" + geometryType + " EMPTY', 2056)"
                : "X'475000100000080800%08X00000000'".formatted(wkbType);
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

    private static void assertRows(TestDatabase db, Connection sql, int version, int datasets) throws SQLException {
        assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("t_ili2db_dataset")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("t_ili2db_basket")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("place")));
        assertEquals(Integer.toString(datasets * 4), scalar(sql, "select count(*) from " + db.table("route")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("plot")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("parcel")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("report")));
        for (var count : Map.of("nestedrecord", 2, "nestedouter", 2, "nestedinner", 3, "nestedleaf", 10).entrySet()) {
            assertEquals(Integer.toString(datasets * count.getValue()),
                    scalar(sql, "select count(*) from " + db.table(count.getKey())));
        }
        assertEquals(Integer.toString(datasets * 8), scalar(sql, "select count(*) from " + db.table("detail")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("linkdetail")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("marker")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("hub")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("markerlink")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("review")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("annotatedlink")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("pairleft")));
        assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("pairright")));
        var tables = new ArrayList<>(List.of("t_ili2db_dataset", "t_ili2db_basket", "place", "route", "plot", "parcel",
                "report", "detail", "marker", "hub", "markerlink", "review", "annotatedlink", "pairleft", "pairright", "linkdetail",
                "nestedrecord", "nestedouter", "nestedinner", "nestedleaf"));
        var hierarchyCounts = Map.of("mast", 4, "tower", 2, "arelay", 1, "beacon", 1, "servicebase", 3, "serviceunit", 3);
        for (var entry : hierarchyCounts.entrySet()) {
            assertEquals(Integer.toString(datasets * entry.getValue()),
                    scalar(sql, "select count(*) from " + db.table(entry.getKey())), entry.getKey());
        }
        tables.addAll(hierarchyCounts.keySet());
        var structureCounts = Map.of("typedrecord", 2, "notebase", 4, "countnote", 2,
                "flagnote", 1, "notekind", 2, "namednote", 2);
        for (var entry : structureCounts.entrySet()) {
            assertEquals(Integer.toString(datasets * entry.getValue()),
                    scalar(sql, "select count(*) from " + db.table(entry.getKey())), entry.getKey());
        }
        tables.addAll(structureCounts.keySet());
        for (var entry : Map.of("refnotebook", 1, "refnotebase", 2, "refnotebranch", 2, "refnoteleaf", 2).entrySet()) {
            assertEquals(Integer.toString(datasets * entry.getValue()),
                    scalar(sql, "select count(*) from " + db.table(entry.getKey())));
            tables.add(entry.getKey());
        }
        if (version == 24) {
            assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("link")));
            for (String table : List.of("refassetbase", "refassetbranch", "refassetleaf")) {
                assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table(table)));
                tables.add(table);
            }
            var collections = List.of("park", "district", "routegroup", "survey");
            for (String table : collections) {
                assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table(table)));
            }
            tables.add("link");
            tables.addAll(collections);
        }
        assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("refcarrier")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("targetpair")));
        tables.addAll(List.of("refcarrier", "targetpair"));
        for (String table : List.of("hierarchylink", "hierarchynote", "dispatchrecord")) {
            assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table(table)));
            tables.add(table);
        }
        assertTableRows(db, sql, "ratingform", datasets * 3);
        assertTableRows(db, sql, "rating", datasets * 2);
        assertTableRows(db, sql, "ratingtag", datasets * 2);
        tables.add("ratingtag");
        assertTableRows(db, sql, "relayreview", datasets);
        tables.add("relayreview");
        tables.addAll(List.of("ratingform", "rating"));
        assertTableRows(db, sql, "serviceaggregation", datasets);
        tables.add("serviceaggregation");
        for (String table : List.of("allocationbase", "taggedallocation", "contractsite", "contractsigner")) {
            assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table(table)));
            tables.add(table);
        }
        var ids = tables.stream().map(table -> "select t_id from " + db.table(table)).toList();
        assertEquals(Integer.toString(datasets * (version == 24 ? 107 : 96)),
                scalar(sql, "select count(distinct t_id) from (" + String.join(" union all ", ids) + ") ids"));
    }

    private static void assertTableRows(TestDatabase db, Connection sql, String table, int count) throws SQLException {
        assertEquals(Integer.toString(count), scalar(sql, "select count(*) from " + db.table(table)), table);
    }

    static Element readDataSection(Path output, int version) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(output.toFile());
        var data = child(document.getDocumentElement(), version == 24 ? "datasection" : "DATASECTION");
        assertNotNull(data);
        return data;
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

    private static void assertTransferredAmounts(Element owner, String attribute, int version, String prefix,
                                                  List<String> amounts) {
        var leaves = transferredStructures(owner, attribute);
        assertEquals(amounts, leaves.stream().map(leaf -> text(leaf, "Amount")).toList());
        assertTrue(leaves.stream().allMatch(leaf -> leaf.getLocalName().equals(prefix + "NestedLeaf")
                && id(leaf, version, "tid").isEmpty()));
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

    private static void assertTransferredNestedTree(Element record, int version, List<String> values) {
        String prefix = version == 23 ? "DbInterop23.Data." : "";
        assertEquals(prefix + "NestedRecord", record.getLocalName());
        var tree = transferredStructures(record, "Tree");
        assertEquals(List.of(prefix + "NestedOuter"), tree.stream().map(Element::getLocalName).toList());
        var items = transferredStructures(tree.getFirst(), "Items");
        assertEquals(List.of(prefix + "NestedInner", prefix + "NestedInner"),
                items.stream().map(Element::getLocalName).toList());
        assertEquals("populated", text(items.getFirst(), "Headline"));
        assertEquals("empty", text(items.getLast(), "Headline"));
        for (var entry : List.of(Map.entry("Main", List.of("1")), Map.entry("Values", values),
                Map.entry("Extras", List.of("4", "4")))) {
            assertTransferredAmounts(items.getFirst(), entry.getKey(), version, prefix, entry.getValue());
            assertNull(child(items.getLast(), entry.getKey()));
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

    private static List<Element> transferredStructures(Element owner, String attribute) {
        // The reference groups the children. The XTF 2.4 writer emits consecutive wrappers.
        return children(owner).stream().filter(element -> element.getLocalName().equals(attribute))
                .flatMap(element -> children(element).stream()).toList();
    }

    private static void assertTransferredDetail(Element detail, int version, String name, DetailValue expected) {
        assertEquals(name, detail.getLocalName());
        assertEquals("", id(detail, version, "tid"));
        assertEquals(expected.label(), text(detail, "Label"));
        assertEquals(expected.enabled() == null ? null : expected.enabled().toString(), text(detail, "Enabled"));
        assertEquals(expected.quantity() == null ? null : expected.quantity().toString(), text(detail, "Quantity"));
        assertEquals(expected.decision(), text(detail, "Decision"));
    }

    private static String text(Element parent, String name) {
        var child = child(parent, name);
        return child == null ? null : child.getTextContent();
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

    private void exchangeOrdered(ReferenceTool reference, PostgreSQLContainer postgres, int version) throws Exception {
        String model = "OrderedInterop" + version;
        Path ili = copyOrderedFixture(model + ".ili");
        Path input = copyOrderedFixture(model + ".xtf");
        String fixture = Files.readString(input);
        Path nativeInput = Files.writeString(work.resolve("ordered-native.xtf"), fixture.replace("ordered-", "native-"));
        var referenceSchema = new TestDatabase(postgres, "ordered_reference");
        var nativeSchema = new TestDatabase(postgres, "ordered_native");

        reference.run(referenceSchema, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                ili.toString());
        assertCliSuccess(nativeSchema, "schemaimport", model, null, ili);
        try (var original = referenceSchema.connect(); var own = nativeSchema.connect()) {
            DbSchemaCatalog.read(original, referenceSchema.postgis, referenceSchema.schema)
                    .assertSameAs(DbSchemaCatalog.read(own, nativeSchema.postgis, nativeSchema.schema));
        }
        for (var db : List.of(referenceSchema, nativeSchema)) {
            reference.run(db, STORED_MODELS, "--import", "--importTid", "--importBid", "--dataset", "ordered", input.toString());
            Path nativeOutput = work.resolve(db.schema + "-native-export.xtf");
            Path referenceOutput = work.resolve(db.schema + "-reference-export.xtf");
            try (var sql = db.connect()) {
                assertOrderColumn(db, sql, "membership", "amember");
                assertOrderColumn(db, sql, "membership", "team");
                assertOrderColumn(db, sql, "entry", "runner");
                assertOrderColumn(db, sql, "document", "writtenin");
                assertOrderColumn(db, sql, "placement", "operatives");
                assertEmbeddedOrderOwner(db, sql, model);
                assertAssociationCatalog(db, sql, model, "Placement", Map.of("Site", "dutyarea", "Operatives", "operative"));
                assertTypeKind(db, sql, "placement", "memo", "TEXT");
                assertStoredOrderedPositions(db, sql, "ordered", false);
                assertCliSuccess(db, "export", model, "ordered", nativeOutput);
                assertOrderedTransfer(nativeOutput, model, version, "ordered", false);

                seedStandalonePositions(db, sql);
                execute(sql, "update " + db.table("document") + " set writtenin_t_seq=21 where t_ili_tid='ordered-doc-two'");
                execute(sql, "update " + db.table("placement") + " set operatives_t_seq=case when memo is null then 18 else 34 end");
                assertCliSuccess(db, "export", model, "ordered", nativeOutput);
                assertOrderedTransfer(nativeOutput, model, version, "ordered", true);

                assertCliSuccess(db, "import", model, "native", nativeInput);
                assertStoredOrderedPositions(db, sql, "native", true);
                assertCliSuccess(db, "export", model, "native", nativeOutput);
                assertOrderedTransfer(nativeOutput, model, version, "native", true);
                reference.run(db, STORED_MODELS, "--export", "--exportTid", "--dataset", "native", referenceOutput.toString());
                assertOrderedTransfer(referenceOutput, model, version, "native", false);

                if (db == nativeSchema) {
                    assertOrderOverflowRollback(db, sql, model, fixture, version);
                    String orphan = " where t_ili_tid='native-doc-loose'";
                    execute(sql, "update " + db.table("document") + " set writtenin_t_seq=6" + orphan);
                    assertExportRejected(db, model, "native", nativeOutput, "Filing.WrittenIn");
                    execute(sql, "update " + db.table("document") + " set writtenin_t_seq=null" + orphan);
                    execute(sql, "update " + db.table("entry") + " set runner_t_seq=0");
                    assertExportRejected(db, model, "native", nativeOutput, "Entry.Runner");
                }
            }
        }
    }

    private Path copyOrderedFixture(String name) throws IOException {
        Path path = work.resolve(name);
        try (var input = DbInteropTest.class.getResourceAsStream("/" + name)) {
            Files.copy(input, path);
        }
        return path;
    }

    private static void assertOrderColumn(TestDatabase db, Connection sql, String table, String fk) throws SQLException {
        String sequence = fk + "_t_seq";
        if (db.postgis) {
            assertEquals("bigint|YES", scalar(sql, "select data_type||'|'||is_nullable from information_schema.columns"
                    + " where table_schema='" + db.schema + "' and table_name='" + table + "' and column_name='" + sequence + "'"));
        } else {
            assertEquals("INTEGER|0", scalar(sql, "select type||'|'||\"notnull\" from pragma_table_info('" + table
                    + "') where name='" + fk + "_T_Seq'"));
        }
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                + " where lower(sqlname)='" + sequence + "' and colowner='" + table + "'"));
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_column_prop")
                + " where lower(columnname)='" + sequence + "' and tablename='" + table + "'"));
    }

    private static void assertEmbeddedOrderOwner(TestDatabase db, Connection sql, String model) throws SQLException {
        assertReferenceMapping(db, sql, model, "Filing.WrittenIn", "document", "writtenin", "folder");
        assertEquals("embedded", scalar(sql, "select setting from " + db.table("t_ili2db_trafo")
                + " where iliname='" + model + ".Data.Filing' and tag='ch.ehi.ili2db.inheritance'"));
    }

    private static void assertStoredOrderedPositions(TestDatabase db, Connection sql, String dataset, boolean present)
            throws SQLException {
        String rows = """
                select m.t_ili_tid, t.t_ili_tid, l.amember_t_seq, l.team_t_seq, b.t_ili_tid
                from %s l join %s m on m.t_id=l.amember join %s t on t.t_id=l.team
                join %s b on b.t_id=l.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' and m.t_basket=b.t_id and t.t_basket=b.t_id
                order by m.t_ili_tid,t.t_ili_tid
                """.formatted(db.table("membership"), db.table("amember"), db.table("team"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            for (var expected : orderedMemberships(dataset, present)) {
                assertTrue(result.next());
                assertEquals(expected.member(), result.getString(1));
                assertEquals(expected.team(), result.getString(2));
                assertEquals(expected.memberPosition(), emptyIfNull(result.getString(3)));
                assertEquals(expected.teamPosition(), emptyIfNull(result.getString(4)));
                assertEquals(dataset + "-basket", result.getString(5));
            }
            assertFalse(result.next());
        }
        String entry = """
                select r.t_ili_tid||'|'||t.t_ili_tid||'|'||coalesce(cast(l.runner_t_seq as text),'')
                from %s l join %s r on r.t_id=l.runner join %s t on t.t_id=l.track
                join %s b on b.t_id=l.t_basket join %s d on d.t_id=b.dataset
                where d.datasetname='%s' and r.t_basket=b.t_id and t.t_basket=b.t_id
                """.formatted(db.table("entry"), db.table("runner"), db.table("track"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), dataset);
        assertEquals(dataset + "-runner|" + dataset + "-track|" + (present ? "4" : ""), scalar(sql, entry));

        String document = """
                select coalesce(f.t_ili_tid,'')||'|'||coalesce(cast(doc.writtenin_t_seq as text),'')
                from %s doc left join %s f on f.t_id=doc.writtenin and f.t_basket=doc.t_basket
                where doc.t_ili_tid='%s-doc-%s'
                """;
        assertEquals("|", scalar(sql, document.formatted(db.table("document"), db.table("folder"), dataset, "loose")));
        assertEquals(dataset + "-folder|", scalar(sql, document.formatted(db.table("document"), db.table("folder"), dataset, "one")));
        assertEquals(dataset + "-folder|" + (present ? "21" : ""),
                scalar(sql, document.formatted(db.table("document"), db.table("folder"), dataset, "two")));

        String placements = """
                select o.t_ili_tid, coalesce(f.t_ili_tid,''), coalesce(l.memo,''), coalesce(cast(l.operatives_t_seq as text),'')
                from %s o left join %s l on l.operatives=o.t_id and l.t_basket=o.t_basket
                left join %s f on f.t_id=l.site and f.t_basket=o.t_basket
                where o.t_ili_tid like '%s-operative-%%'
                order by o.t_ili_tid
                """.formatted(db.table("operative"), db.table("placement"), db.table("dutyarea"), dataset);
        var actual = new ArrayList<List<String>>();
        try (var statement = sql.createStatement(); var result = statement.executeQuery(placements)) {
            while (result.next()) actual.add(List.of(result.getString(1), result.getString(2), result.getString(3), result.getString(4)));
        }
        assertEquals(List.of(List.of(dataset + "-operative-absent", "", "", ""),
                List.of(dataset + "-operative-direct", dataset + "-area", "", present ? "18" : ""),
                List.of(dataset + "-operative-nested", dataset + "-area", "kit", present ? "34" : "")), actual);
    }

    private static void seedStandalonePositions(TestDatabase db, Connection sql) throws SQLException {
        for (var pair : orderedMemberships("ordered", true)) {
            execute(sql, "update " + db.table("membership") + " set amember_t_seq=" + nullIfEmpty(pair.memberPosition())
                    + ", team_t_seq=" + nullIfEmpty(pair.teamPosition()) + " where amember=(select t_id from " + db.table("amember")
                    + " where t_ili_tid='" + pair.member() + "') and team=(select t_id from " + db.table("team")
                    + " where t_ili_tid='" + pair.team() + "')");
        }
        execute(sql, "update " + db.table("entry") + " set runner_t_seq=4");
    }

    private static void assertOrderedTransfer(Path output, String model, int version, String dataset, boolean present)
            throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var basket = baskets.getFirst();
        assertEquals(dataset + "-basket", id(basket, version, "bid"));
        String prefix = version == 23 ? model + ".Data." : "";
        var memberships = new HashSet<OrderedMembership>();
        for (var link : children(basket)) {
            if (!link.getLocalName().equals(prefix + "Membership")) continue;
            assertEquals("", id(link, version, "tid"));
            var member = child(link, "Member");
            var team = child(link, "Team");
            assertTrue(memberships.add(new OrderedMembership(id(member, version, "ref"), id(team, version, "ref"),
                    id(member, version, "order_pos"), id(team, version, "order_pos"))));
        }
        assertEquals(Set.copyOf(orderedMemberships(dataset, present)), memberships);
        var entry = child(basket, prefix + "Entry");
        assertPosition(entry, "Runner", version, dataset + "-runner", present ? "4" : "");
        assertPosition(entry, "Track", version, dataset + "-track", "");

        var documents = orderedOwners(basket, prefix + "Document", version);
        assertEquals(Set.of(dataset + "-doc-loose", dataset + "-doc-one", dataset + "-doc-two"), documents.keySet());
        assertNull(child(documents.get(dataset + "-doc-loose"), "WrittenIn"));
        assertPosition(documents.get(dataset + "-doc-one"), "WrittenIn", version, dataset + "-folder", "");
        assertPosition(documents.get(dataset + "-doc-two"), "WrittenIn", version, dataset + "-folder", present ? "21" : "");

        var operatives = orderedOwners(basket, prefix + "Operative", version);
        assertEquals(Set.of(dataset + "-operative-absent", dataset + "-operative-direct", dataset + "-operative-nested"),
                operatives.keySet());
        assertNull(child(operatives.get(dataset + "-operative-absent"), "Site"));
        var direct = operatives.get(dataset + "-operative-direct");
        assertPosition(direct, "Site", version, dataset + "-area", present ? "18" : "");
        assertTrue(children(child(direct, "Site")).isEmpty());
        var nested = operatives.get(dataset + "-operative-nested");
        assertPosition(nested, "Site", version, dataset + "-area", present ? "34" : "");
        var payload = child(child(nested, "Site"), prefix + "Placement");
        assertNotNull(payload);
        assertEquals("kit", text(payload, "Memo"));
    }

    private static Map<String, Element> orderedOwners(Element basket, String objectName, int version) {
        var owners = new HashMap<String, Element>();
        for (var object : children(basket)) {
            if (object.getLocalName().equals(objectName)) {
                assertNull(owners.put(id(object, version, "tid"), object));
            }
        }
        return owners;
    }

    private static void assertPosition(Element owner, String role, int version, String target, String position) {
        assertLocalReference(owner, role, version, target);
        assertEquals(position, id(child(owner, role), version, "order_pos"), role);
    }

    private void assertOrderOverflowRollback(TestDatabase db, Connection sql, String model, String fixture, int version)
            throws Exception {
        String order = version == 23 ? "ORDER_POS" : "i:order_pos";
        String broken = fixture.replace(order + "=\"4\"", order + "=\"9223372036854775808\"");
        assertNotEquals(fixture, broken);
        Path input = Files.writeString(work.resolve("order-overflow.xtf"), broken.replace("ordered-", "overflow-"));
        var rejected = cli(db, "import", model, "overflow", input, CancellationToken.none());
        assertNotEquals(0, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("Entry.Runner"), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("64-bit"), rejected::diagnostics);
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_dataset") + " where datasetname='overflow'"));
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_basket") + " where t_ili_tid='overflow-basket'"));
        assertEquals("6", scalar(sql, "select count(*) from " + db.table("membership")));
        assertEquals("0", scalar(sql, "select count(*) from " + db.table("amember") + " where t_ili_tid like 'overflow-%'"));
    }

    private static List<OrderedMembership> orderedMemberships(String dataset, boolean present) {
        return List.of(new OrderedMembership(dataset + "-alpha", dataset + "-blue", present ? "2" : "", ""),
                new OrderedMembership(dataset + "-beta", dataset + "-blue", present ? "9" : "", present ? "3" : ""),
                new OrderedMembership(dataset + "-beta", dataset + "-red", "", present ? Long.toString(Long.MAX_VALUE) : ""));
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? "null" : value;
    }

    private record OrderedMembership(String member, String team, String memberPosition, String teamPosition) { }

    private record CurveComponent(int type, List<List<String>> vertices) { }
    private record DetailValue(String label, Boolean enabled, Integer quantity, String decision) { }
    private record AnnotatedPair(String marker, String report, String memo, String grade, String decision) { }

    static List<Element> children(Element parent) {
        var elements = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) elements.add(child);
        }
        return elements;
    }

    static Element child(Element parent, String name) {
        return children(parent).stream().filter(element -> element.getLocalName().equals(name)).findFirst().orElse(null);
    }

    static String id(Element element, int version, String name) {
        return version == 24 ? element.getAttributeNS(XTF24_NS, name) : element.getAttribute(name.toUpperCase(Locale.ROOT));
    }

    static String scalar(Connection sql, String query) throws SQLException {
        try (var statement = sql.createStatement(); var result = statement.executeQuery(query)) {
            if (!result.next()) return null;
            String value = result.getString(1);
            assertFalse(result.next(), query);
            return value;
        }
    }

    static void execute(Connection sql, String query) throws SQLException {
        try (var statement = sql.createStatement()) {
            statement.executeUpdate(query);
        }
    }

    static void assertCliSuccess(TestDatabase db, String operation, String model, String dataset, Path path) {
        var result = cli(db, operation, model, dataset, path, CancellationToken.none());
        assertEquals(0, result.code(), result::diagnostics);
    }

    static void assertCliFailure(TestDatabase db, String operation, String model, String dataset, Path path,
                                 String diagnostic, String... options) {
        var result = cli(db, operation, model, dataset, path, CancellationToken.none(), options);
        assertEquals(1, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains(diagnostic), result::diagnostics);
    }

    static CliResult cli(TestDatabase db, String operation, String model, String dataset, Path path,
                                 CancellationToken token, String... options) {
        var diagnostics = new StringWriter();
        var print = new PrintWriter(diagnostics);
        var args = new ArrayList<>(List.of(operation, "--model", model));
        if (dataset != null) args.addAll(List.of("--dataset", dataset));
        args.addAll(db.nativeOptions());
        args.addAll(List.of(options));
        if (path != null) args.add(path.toString());
        int code = Main.run(args.toArray(String[]::new), print, print, db.password, token);
        return new CliResult(code, diagnostics.toString());
    }

    record CliResult(int code, String diagnostics) {}

    final class ReferenceTool {
        private final Path jar;
        private final Path home;
        private int runs;

        private ReferenceTool(boolean postgis) throws IOException {
            String version = System.getProperty("ili2db.reference.version");
            String directory = System.getProperty("ili2db.reference.dir");
            assertNotNull(version, "Gradle must provide ili2db.reference.version");
            assertNotNull(directory, "Gradle must provide ili2db.reference.dir");
            String name = (postgis ? "ili2pg" : "ili2gpkg") + "-" + version;
            Path archive = Path.of(directory).resolve(name + ".zip");
            Path dist = Files.createDirectory(work.resolve("reference"));
            home = Files.createDirectory(work.resolve("reference-home"));
            try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    Path file = dist.resolve(entry.getName()).normalize();
                    if (!file.startsWith(dist)) throw new IOException("Invalid reference ZIP entry");
                    if (entry.isDirectory()) Files.createDirectories(file);
                    else {
                        Files.createDirectories(file.getParent());
                        Files.copy(zip, file);
                    }
                }
            }
            jar = dist.resolve(name + ".jar");
        }

        void run(TestDatabase db, String modelDir, String... args) throws Exception {
            run(db, modelDir, null, args);
        }

        void expectFailure(TestDatabase db, String modelDir, String error, String... args) throws Exception {
            run(db, modelDir, error, args);
        }

        private void run(TestDatabase db, String modelDir, String expectedError, String[] args) throws Exception {
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            var command = new ArrayList<>(List.of(java.toString(), "--enable-native-access=ALL-UNNAMED",
                    "-Duser.home=" + home, "-Djava.io.tmpdir=" + work, "-jar", jar.toString(), "--modeldir", modelDir));
            command.addAll(db.referenceOptions());
            command.addAll(List.of(args));
            Path log = work.resolve("reference-" + ++runs + ".log");
            Process process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true)
                    .redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(60, TimeUnit.SECONDS), () -> "Reference timed out: " + args[0]);
                if (expectedError == null) {
                    assertEquals(0, process.exitValue(), () -> logText(log, db.password));
                } else {
                    String output = logText(log, db.password);
                    assertNotEquals(0, process.exitValue(), output);
                    assertTrue(output.contains(expectedError), output);
                }
            } finally {
                if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    final class TestDatabase {
        private final PostgreSQLContainer postgres;
        private final boolean postgis;
        private final Path file;
        private final String url;
        private final String user;
        private final String password;
        private final String schema;

        private TestDatabase(PostgreSQLContainer postgres, String name) {
            this.postgres = postgres;
            postgis = postgres != null;
            file = work.resolve(name + ".gpkg");
            url = postgis ? postgres.getJdbcUrl() : "jdbc:sqlite:" + file;
            user = postgis ? postgres.getUsername() : "";
            password = postgis ? postgres.getPassword() : "";
            schema = "db_interop_" + name;
        }

        Connection connect() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        private List<String> nativeOptions() {
            return postgis ? List.of("--pg-url", url, "--schema", schema, "--user", user)
                    : List.of("--gpkg", file.toString());
        }

        private List<String> referenceOptions() {
            if (!postgis) return List.of("--dbfile", file.toString());
            return List.of("--dbhost", postgres.getHost(), "--dbport", postgres.getFirstMappedPort().toString(),
                    "--dbdatabase", postgres.getDatabaseName(), "--dbschema", schema, "--dbusr", user, "--dbpwd", password);
        }

        boolean postgis() { return postgis; }

        String schema() { return schema; }

        String table(String name) { return postgis ? schema + "." + name : name; }
    }

    private static String logText(Path log, String password) {
        try {
            String text = Files.readString(log);
            return password.isEmpty() ? text : text.replace(password, "[redacted]");
        } catch (IOException failure) {
            return log + ": " + failure.getMessage();
        }
    }
}
