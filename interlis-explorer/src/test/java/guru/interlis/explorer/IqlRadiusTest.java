package guru.interlis.explorer;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IqlRadiusTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final Path FIXTURES = Path.of(System.getProperty("testdata.dir"), "reference", "iox", "ili24");
    private static final String SOURCE = "SELECT COUNT(*) FROM Reference24.Data.Item i WHERE ";

    @TempDir Path directory;

    @Test
    void bindsAnIndexedNativeSearchAndChecksTheCrsOnlyOnRelevantOccurrences() throws Exception {
        var lv95 = plan("EPSG:2056");
        var lv03 = plan("EPSG:21781");
        var unknown = plan(null);
        String radius = "WITHIN_DISTANCE(i.shape, POINT(2600000,1200000),500)";
        var compiled = Iql.compile(lv95, SOURCE + radius, 100);
        assertEquals(List.of(2600000.0, 1200000.0, 500.0), compiled.parameters());
        assertTrue(compiled.sql().contains("crs = 'EPSG:2056' AND ST_DWithin(geom, ST_Point(?, ?), ?)"));
        assertNull(preflight(load(lv95, "lv95.duckdb"), compiled));

        Path foreignGrid = load(lv03, "lv03.duckdb");
        var wrongCrs = Iql.compile(lv03, SOURCE + radius, 100);
        assertTrue(preflight(foreignGrid, wrongCrs).contains("requires LV95 (EPSG:2056)"));
        assertNull(preflight(foreignGrid, Iql.compile(lv03, SOURCE + "i.count = 5 AND " + radius, 100)));
        assertTrue(preflight(load(unknown, "unknown.duckdb"), Iql.compile(unknown, SOURCE + radius, 100))
                .contains("reference system is unknown"));

        var invalid = assertThrows(Iql.QueryException.class, () -> Iql.compile(lv95,
                SOURCE + radius.replace("i.shape", "i.required"), 100));
        assertTrue(invalid.getMessage().contains("requires a geometry attribute"));
    }

    private Path load(TransferPlan plan, String name) throws Exception {
        var file = directory.resolve(name);
        try (var store = DuckDbStore.open(file, plan);
             var input = Files.newInputStream(FIXTURES.resolve("geometry.xtf"));
             var reader = new Xtf24Reader(plan, input)) {
            store.importTransfer(reader, RUN);
        }
        return file;
    }

    private static String preflight(Path file, Iql.Compiled compiled) throws Exception {
        var check = compiled.checks().getFirst();
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + file);
             var query = connection.prepareStatement(check.sql())) {
            for (int index = 0; index < check.parameters().size(); index++) {
                query.setObject(index + 1, check.parameters().get(index));
            }
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private static TransferPlan plan(String crs) throws Exception {
        String model = Files.readString(FIXTURES.resolve("Reference24.ili"));
        if (crs != null) {
            model = model.replace("    P = ", "    !!@CRS=" + crs + "\n    P = ");
        }
        var source = new SourceText(new SourceId("fixture:/Reference24.ili"), new SourceRevision(1), model);
        var root = new ModelSelector.ByName("Reference24", Optional.empty(), Optional.empty());
        var request = new CompilationRequest(List.of(root), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        var compilation = assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, RUN));
        return assertInstanceOf(TransferPlanningResult.Ready.class,
                TransferPlanner.plan(compilation.schema(), TransferPlanner.Names.DECLARED)).plan();
    }
}
