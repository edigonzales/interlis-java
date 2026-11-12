package guru.interlis.validation;

import guru.interlis.model.BinaryExpression;
import guru.interlis.model.BinaryOperator;
import guru.interlis.model.BooleanType;
import guru.interlis.model.ConstantExpression;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.ExpressionTypeState;
import guru.interlis.model.Origin;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.model.SetConstraint;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static guru.interlis.validation.DatasetRuleTestSupport.plan;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionParityTest {
    @Test
    void separatesMissingPopulationFromUndefinedAndKeepsBlockedCause() {
        var validation = plan("""
                CLASS Item =
                  SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
                END Item;
                """);
        var rule = (SetConstraint) validation.datasetRules().getFirst().declaration().definition();
        var program = validation.datasetExpression(rule.condition()).orElseThrow();
        assertTrue(program.requirements().populationRequired());
        assertSame(EvaluationResult.MissingContext.POPULATION, program.evaluate(frame(validation, OptionalLong.empty())));
        assertEquals(new EvaluationResult.Known(new TransferValue.BooleanValue(false)),
                program.evaluate(frame(validation, OptionalLong.of(0))));
        assertEquals(new EvaluationResult.Known(new TransferValue.BooleanValue(true)),
                program.evaluate(frame(validation, OptionalLong.of(2))));

        var binary = assertInstanceOf(BinaryExpression.class, program.expression());
        var cause = new DataLocation(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of());
        var blocked = new EvaluationResult.BlockedByViolation(cause);
        assertSame(blocked, withLeft(program, binary, blocked).evaluate(frame(validation, OptionalLong.empty())));
        assertEquals(new EvaluationResult.UndefinedByData(true),
                withLeft(program, binary, new EvaluationResult.UndefinedByData(false))
                        .evaluate(frame(validation, OptionalLong.empty())));
        assertSame(EvaluationResult.Unsupported.INSTANCE,
                withLeft(program, binary, EvaluationResult.Unsupported.INSTANCE)
                        .evaluate(frame(validation, OptionalLong.empty())));
    }

    @Test
    void shortCircuitSkipsMissingDatasetContext() {
        var validation = plan("""
                CLASS Item =
                  SET CONSTRAINT #true OR (INTERLIS.objectCount(ALL) > 0);
                END Item;
                """);
        var rule = (SetConstraint) validation.datasetRules().getFirst().declaration().definition();
        var program = validation.datasetExpression(rule.condition()).orElseThrow();
        assertTrue(program.requirements().populationRequired());
        assertEquals(new EvaluationResult.Known(new TransferValue.BooleanValue(true)),
                program.evaluate(frame(validation, OptionalLong.empty())));
    }

    @Test
    void compilesSharedNodesOnceAndAppliesDepthLimitToTheFullPath() {
        var source = new SourceId("memory:/shared.ili");
        var revision = new SourceRevision(1);
        var range = new SourceRange(source, revision, 0, 1);
        var type = new BooleanType(new TypeId("boolean"), new Origin.DeclaredOrigin(range));
        var leaf = new ExpressionId("leaf");
        var nodes = new ArrayList<TypedExpression>();
        nodes.add(new ConstantExpression(leaf, type.id(), ExpressionTypeState.RESOLVED, range, List.of(), "#true"));
        for (int depth = 1; depth <= 129; depth++) {
            var child = nodes.getLast().id();
            nodes.add(new BinaryExpression(new ExpressionId("node-" + depth), type.id(), ExpressionTypeState.RESOLVED,
                    range, List.of(child, child), BinaryOperator.OR, child, child));
        }
        var validation = plan("CLASS Item = END Item;");
        var graph = new SemanticGraph(validation.snapshotId(), List.of(), List.of(type), nodes, List.of(),
                Map.of(), Map.of(), Map.of(), List.of(), List.of(), Set.of(), Map.of(source, revision));
        var relations = new TypeRelations(graph);
        var program = ExpressionCompiler.compile(graph, relations, nodes.get(128).id()).orElseThrow();
        assertEquals(129, program.nodes().size());
        var visits = new AtomicInteger();
        assertEquals(new EvaluationResult.Known(new TransferValue.BooleanValue(true)),
                program.evaluate(new EvaluationFrame(validation, TransferValue.Undefined.INSTANCE, false,
                        visits::incrementAndGet)));
        assertEquals(129, visits.get());
        assertThrows(ValidationLimitException.class,
                () -> ExpressionCompiler.compile(graph, relations, nodes.get(129).id()));
    }

    private static ExpressionProgram withLeft(ExpressionProgram program, BinaryExpression binary,
                                              EvaluationResult replacement) {
        var constants = new HashMap<>(program.constants());
        constants.put(binary.left(), replacement);
        return new ExpressionProgram(program.snapshot(), program.root(), program.nodes(), constants,
                program.comparisons(), program.requirements());
    }

    private static EvaluationFrame frame(ValidationPlan plan, OptionalLong count) {
        return new EvaluationFrame(plan, TransferValue.Undefined.INSTANCE, false, () -> { }, count);
    }
}
