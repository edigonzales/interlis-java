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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IqlTypedComparisonTest {
    private static final String SELECT = "SELECT COUNT(*) FROM TypedComparison.Data.Item WHERE ";
    private static final CancellationToken RUN = CancellationToken.none();
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL TypedComparison AT "https://example.org" VERSION "1" =
              XMLNS "urn:typed-comparison";
              UNIT Kilometre [km] = 1000 [INTERLIS.m];
              DOMAIN State = (active, open);
                ExtendedState EXTENDS State = (waiting);
                UnrelatedState = (active, closed);
              TOPIC Data =
                CLASS Item =
                  length : 0..100 [INTERLIS.m];
                  width : 0..200 [INTERLIS.m];
                  duration : 0..100 [INTERLIS.s];
                  lengthKm : 0.000..1.000 [km];
                  unitless : 0..100;
                  primaryState : State;
                  secondaryState : State;
                  extendedState : ExtendedState;
                  unrelatedState : UnrelatedState;
                END Item;
              END Data;
            END TypedComparison.
            """;

    @TempDir Path temp;

    @Test
    void rejectsIncompatibleAttributesBeforeSqlExecution() {
        var plan = plan();
        assertComparisonError(plan, "length = duration", "matching units");
        assertComparisonError(plan, "length = lengthKm", "requires unit conversion");
        assertComparisonError(plan, "unitless = length", "matching units");
        assertComparisonError(plan, "primaryState = unrelatedState", "related enumeration types");
    }

    @Test
    void comparesEqualUnitsAndRelatedEnumerationsWithTheUsualLiterals() throws Exception {
        var plan = plan();
        Path database = temp.resolve("typed.duckdb");
        String transfer = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:typed-comparison">
                  <i:headersection><i:models><i:model>TypedComparison</i:model></i:models><i:sender>Fixture</i:sender></i:headersection>
                  <i:datasection><Data i:bid="b1">
                    <Item i:tid="one"><length>5</length><width>5</width><primaryState>active</primaryState>
                      <secondaryState>active</secondaryState><extendedState>active</extendedState></Item>
                    <Item i:tid="two"><length>5</length><width>4</width><primaryState>active</primaryState>
                      <secondaryState>active</secondaryState><extendedState>waiting</extendedState></Item>
                  </Data></i:datasection>
                </i:transfer>
                """;
        try (var store = DuckDbStore.open(database, plan);
             var reader = new Xtf24Reader(plan, new ByteArrayInputStream(transfer.getBytes(StandardCharsets.UTF_8)))) {
            store.importTransfer(reader, RUN);
        }
        try (var store = DuckDbStore.openReadOnly(database, plan)) {
            var queries = new ExplorerQueries(store);
            var numeric = queries.execute(ExplorerQueries.Mode.IQL, SELECT + "length = width AND length > 4.5 AND 5 = length",
                    new ExplorerQueries.Cancellation());
            assertEquals(List.of(List.of("1")), numeric.rows());
            var enumeration = queries.execute(ExplorerQueries.Mode.IQL,
                    SELECT + "primaryState = secondaryState AND primaryState = extendedState AND #active = primaryState",
                    new ExplorerQueries.Cancellation());
            assertEquals(List.of(List.of("1")), enumeration.rows());
        }
    }

    private static void assertComparisonError(TransferPlan plan, String comparison, String message) {
        String query = SELECT + comparison;
        var error = assertThrows(Iql.QueryException.class, () -> Iql.compile(plan, query, 100));
        assertEquals("=", query.substring(error.start(), error.end()));
        assertTrue(error.getMessage().contains(message), error::getMessage);
    }

    private static TransferPlan plan() {
        var source = new SourceText(new SourceId("fixture:/TypedComparison.ili"), new SourceRevision(1), MODEL);
        var root = new ModelSelector.ByName("TypedComparison", Optional.empty(), Optional.empty());
        var request = new CompilationRequest(List.of(root), SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, RUN);
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, outcome::toString).schema();
        return assertInstanceOf(TransferPlanningResult.Ready.class,
                TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED)).plan();
    }
}
