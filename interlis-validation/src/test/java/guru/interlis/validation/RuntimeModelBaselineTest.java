package guru.interlis.validation;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.BinaryExpression;
import guru.interlis.model.BinaryOperator;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.ConstantExpression;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.FunctionCallExpression;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.LiteralExpression;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.ObjectArgumentType;
import guru.interlis.model.ObjectSetExpression;
import guru.interlis.model.Origin;
import guru.interlis.model.ParameterSymbol;
import guru.interlis.model.PathExpression;
import guru.interlis.model.PathStep;
import guru.interlis.model.SetConstraint;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.SymbolValueExpression;
import guru.interlis.model.TypeKind;
import guru.interlis.model.ViewSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeModelBaselineTest {
    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void bindsWhereAllFunctionsParametersAndVirtualViewInOneSchema(String version) {
        var schema = compile("""
                INTERLIS %s;
                CONTRACTED MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  PARAMETER enabled: BOOLEAN;
                  FUNCTION objectCount(population: OBJECTS OF ANYCLASS): NUMERIC;
                  TOPIC Data =
                    CLASS Item =
                      amount: 0..100;
                      SET CONSTRAINT WHERE amount > 0:
                        INTERLIS.objectCount(ALL) > 0 AND (Checks.objectCount(ALL) > 0 AND PARAMETER enabled);
                    END Item;
                    VIEW Selected PROJECTION OF src~Item;
                      WHERE src->amount > 0;
                      = ALL OF src;
                    END Selected;
                  END Data;
                END Checks.
                """.formatted(version));
        var graph = schema.semanticGraph();
        var item = graph.symbols().stream().filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                .filter(symbol -> symbol.name().value().equals("Item")).findFirst().orElseThrow();
        var amount = (AttributeSymbol) graph.symbol(item.members().getFirst()).orElseThrow();
        var declaration = graph.symbols().stream().filter(ConstraintSymbol.class::isInstance)
                .map(ConstraintSymbol.class::cast).filter(symbol -> symbol.definition() instanceof SetConstraint)
                .findFirst().orElseThrow();
        var set = (SetConstraint) declaration.definition();
        var filter = assertInstanceOf(BinaryExpression.class, graph.expression(set.where().orElseThrow()).orElseThrow());
        var filterPath = assertInstanceOf(PathExpression.class, graph.expression(filter.left()).orElseThrow());
        assertEquals(amount.id(),
                assertInstanceOf(PathStep.AttributeStep.class, filterPath.path().steps().getLast()).target());

        var calls = graph.expressions().stream().filter(FunctionCallExpression.class::isInstance)
                .map(FunctionCallExpression.class::cast).toList();
        assertEquals(2, calls.size());
        for (var call : calls) {
            var argument = assertInstanceOf(ObjectSetExpression.class,
                    graph.expression(call.children().getFirst()).orElseThrow());
            var argumentType = assertInstanceOf(ObjectArgumentType.class, graph.type(argument.typeId()).orElseThrow());
            assertTrue(argumentType.objects());
            assertEquals(Optional.of(item.id()), argumentType.base());
            assertTrue(argumentType.restrictions().isEmpty());
        }
        var functions = calls.stream().map(call ->
                assertInstanceOf(FunctionSymbol.class, graph.symbol(call.targetFunction().orElseThrow()).orElseThrow()))
                .toList();
        var predefined = functions.stream().filter(f -> f.origin() instanceof Origin.PredefinedOrigin).findFirst().orElseThrow();
        var shadow = functions.stream().filter(f -> f.origin() instanceof Origin.DeclaredOrigin).findFirst().orElseThrow();
        assertEquals("INTERLIS.objectCount", ((Origin.PredefinedOrigin) predefined.origin()).key());
        assertEquals("INTERLIS.objectCount", predefined.name().value());
        assertEquals("objectCount", shadow.name().value());
        assertNotEquals(predefined.id(), shadow.id());

        var parameterValue = graph.expressions().stream().filter(SymbolValueExpression.class::isInstance)
                .map(SymbolValueExpression.class::cast).findFirst().orElseThrow();
        var parameter = assertInstanceOf(ParameterSymbol.class, graph.symbol(parameterValue.target()).orElseThrow());
        assertEquals("enabled", parameter.name().value());
        assertEquals(TypeKind.BOOLEAN, graph.type(parameter.typeId()).orElseThrow().kind());

        var view = graph.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast)
                .findFirst().orElseThrow();
        var formation = assertInstanceOf(ViewSymbol.Projection.class, view.formation().orElseThrow());
        var base = assertInstanceOf(ViewSymbol.Base.class, graph.symbol(formation.base()).orElseThrow());
        assertEquals(item.id(), base.target());
        var selection = assertInstanceOf(BinaryExpression.class,
                graph.expression(view.selections().getFirst()).orElseThrow());
        var viewPath = assertInstanceOf(PathExpression.class, graph.expression(selection.left()).orElseThrow());
        assertInstanceOf(PathStep.ViewBaseStep.class, viewPath.path().steps().getFirst());

        var transfer = transfer(schema);
        assertTrue(transfer.object(new SymbolRef(schema.snapshotId(), item.id())).isPresent());
        assertTrue(transfer.object(new SymbolRef(schema.snapshotId(), view.id())).isEmpty());
        var validator = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET)).plan();
        assertEquals(List.of(new SymbolRef(schema.snapshotId(), declaration.id())),
                validator.deferredRules().stream().map(RuleKey::declaration).toList());
    }

    @Test
    void preservesExactOperandsWithoutTurningStaticUnboundednessIntoUndefined() {
        var schema = compile("""
                INTERLIS 2.4;
                CONTRACTED MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    CLASS Item = amount: 0..100; END Item;
                    VIEW Result PROJECTION OF src~Item; =
                      third := 1/3;
                      irrational := PI;
                      exponential := LNBASE;
                    END Result;
                  END Data;
                END Checks.
                """);
        var graph = schema.semanticGraph();
        var division = graph.expressions().stream().filter(BinaryExpression.class::isInstance)
                .map(BinaryExpression.class::cast).filter(node -> node.operator() == BinaryOperator.DIV)
                .findFirst().orElseThrow();
        assertEquals(NumericRange.unbounded(),
                assertInstanceOf(NumericType.class, graph.type(division.typeId()).orElseThrow()).range());
        assertEquals(List.of("1", "3"), division.children().stream().map(graph::expression)
                .map(Optional::orElseThrow).map(node -> assertInstanceOf(LiteralExpression.class, node).lexeme()).toList());
        assertTrue(graph.expressions().stream().filter(ConstantExpression.class::isInstance)
                .map(ConstantExpression.class::cast).map(ConstantExpression::constantName).toList()
                .containsAll(List.of("PI", "LNBASE")));
    }

    @Test
    void leavesTheArithmeticSyntaxBoundaryAtTheCompiler() {
        var id = new SourceId("memory:/Checks.ili");
        var source = new SourceText(id, new SourceRevision(1), """
                INTERLIS 2.3;
                CONTRACTED MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    CLASS Item =
                      amount: 0..100;
                      MANDATORY CONSTRAINT (amount + 1) > 0;
                    END Item;
                  END Data;
                END Checks.
                """);
        var request = new CompilationRequest(List.of(new ModelSelector.ByName("Checks", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(id, source)), CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
