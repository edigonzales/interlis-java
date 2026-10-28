package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionConstraintContractTest {
    private static CompilationOutcome compile(String text) {
        SourceText source = new SourceText(new SourceId("memory:/expression-constraint.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("M",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }

    @Test
    void builtinFunctionAndImplicationCompile() {
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A =
                      attr : TEXT*10;
                      MANDATORY CONSTRAINT INTERLIS.len(attr) >= 0;
                      MANDATORY CONSTRAINT #true => #true;
                    END A;
                  END T;
                END M.
                """));
    }

    @Test
    void existenceTypeMismatchIsRejected() {
        assertInstanceOf(CompilationOutcome.Rejected.class, compile("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = attr : TEXT*10; END A;
                    CLASS B =
                      number : 1 .. 10;
                      EXISTENCE CONSTRAINT number REQUIRED IN A:attr;
                    END B;
                  END T;
                END M.
                """));
    }

    @Test
    void externalConstraintBindsItsClassAndMissingAttributeIsAUserError() {
        String model = """
                INTERLIS 2.4;
                MODEL M AT "x" VERSION "1" =
                  TOPIC T =
                    CLASS A = value : 0..10; END A;
                    CLASS B = number : 0..10; END B;
                    CONSTRAINTS OF B =
                      EXISTENCE CONSTRAINT number REQUIRED IN A:value;
                      MANDATORY CONSTRAINT number > 0;
                    END;
                  END T;
                END M.
                """;
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile(model));
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                compile(model.replace("CONSTRAINT number REQUIRED", "CONSTRAINT missing REQUIRED")));
        var constraint = rejected.analysis().semanticGraph().symbols().stream()
                .filter(guru.interlis.model.ConstraintSymbol.class::isInstance)
                .map(guru.interlis.model.ConstraintSymbol.class::cast)
                .map(guru.interlis.model.ConstraintSymbol::definition)
                .filter(guru.interlis.model.ExistenceConstraint.class::isInstance)
                .map(guru.interlis.model.ExistenceConstraint.class::cast)
                .findFirst().orElseThrow();
        assertInstanceOf(guru.interlis.model.PathStep.UnresolvedStep.class,
                ((guru.interlis.model.PathExpression) rejected.analysis().semanticGraph().expression(constraint.source()).orElseThrow()).path().steps().getFirst());
    }

    @Test
    void objectPathsKeepTheirTargetTypeAndCannotContinueThroughScalarValues() {
        for (String version : List.of("2.3", "2.4")) {
            String model = """
                    INTERLIS %s;
                    CONTRACTED MODEL M AT "x" VERSION "1" =
                      TOPIC T = OID AS INTERLIS.UUIDOID;
                        CLASS A = value : 0..10; END A;
                        STRUCTURE Link =
                          target : REFERENCE TO A;
                          value : 0..10;
                          MANDATORY CONSTRAINT %s;
                        END Link;
                      END T;
                    END M.
                    """;
            var success = assertInstanceOf(CompilationOutcome.Succeeded.class,
                    compile(model.formatted(version, "target->value > 0")));
            var graph = success.analysis().semanticGraph();
            var path = graph.expressions().stream()
                    .filter(guru.interlis.model.PathExpression.class::isInstance)
                    .map(guru.interlis.model.PathExpression.class::cast).findFirst().orElseThrow();
            assertEquals(2, path.path().steps().size());
            assertInstanceOf(guru.interlis.model.NumericType.class, graph.type(path.typeId()).orElseThrow());
            var target = assertInstanceOf(guru.interlis.model.PathStep.AttributeStep.class,
                    path.path().steps().getLast());
            assertEquals("A", graph.symbol(graph.symbol(target.target()).orElseThrow()
                    .owner().orElseThrow()).orElseThrow().name().value());

            for (String expression : List.of("value->value > 0", "target", "THIS")) {
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                        compile(model.formatted(version, expression)), expression);
                assertFalse(rejected.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT),
                        rejected.rejection().toString());
            }
            var object = assertInstanceOf(CompilationOutcome.Succeeded.class,
                    compile(model.formatted(version, "DEFINED(target)")));
            var objectPath = object.analysis().semanticGraph().expressions().stream()
                    .filter(guru.interlis.model.PathExpression.class::isInstance)
                    .map(guru.interlis.model.PathExpression.class::cast).findFirst().orElseThrow();
            assertInstanceOf(guru.interlis.model.ReferenceType.class,
                    object.analysis().semanticGraph().type(objectPath.typeId()).orElseThrow());
        }
    }

    @Test
    void invalidPathOperatorsCannotSilentlyBecomeOrdinaryNames() {
        for (String expression : List.of("\\value", "value[FIRST]", "PARENT->value")) {
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, compile("""
                    INTERLIS 2.4; MODEL M AT "x" VERSION "1" =
                      TOPIC T = CLASS A = value : BOOLEAN;
                        MANDATORY CONSTRAINT %s;
                      END A; END T;
                    END M.
                    """.formatted(expression)), expression);
            org.junit.jupiter.api.Assertions.assertFalse(rejected.rejection().reasons()
                    .contains(RejectionReason.INCOMPLETE_SUPPORT), expression);
            if (expression.startsWith("PARENT")) assertTrue(rejected.analysis().diagnostics().toString().contains("VIEW_INSPECTION_CONTEXT"));
        }
    }

    @Test
    void domainConstraintsAreBoundToTheirOwnTypeAndRemainInDeclarationOrder() {
        String model = """
                INTERLIS 2.4; MODEL M AT "x" VERSION "1" =
                  DOMAIN
                    Words = TEXT*30 CONSTRAINTS Length: INTERLIS.len(THIS) > 2;
                    Amount = 0..100 CONSTRAINTS Minimum: THIS > 10, Maximum: THIS <= 90;
                END M.
                """;
        var success = assertInstanceOf(CompilationOutcome.Succeeded.class, compile(model));
        var graph = success.analysis().semanticGraph();
        var constraints = graph.symbols().stream()
                .filter(guru.interlis.model.ConstraintSymbol.class::isInstance)
                .map(guru.interlis.model.ConstraintSymbol.class::cast).toList();
        assertEquals(List.of("Length", "Minimum", "Maximum"), constraints.stream()
                .map(c -> c.name().value()).toList());
        assertEquals(List.of("Words", "Amount", "Amount"), constraints.stream()
                .map(c -> graph.symbol(c.owner().orElseThrow()).orElseThrow().name().value()).toList());
        constraints.forEach(c -> assertInstanceOf(guru.interlis.model.DomainConstraint.class,
                c.definition()));
        for (var path : graph.expressions().stream()
                .filter(guru.interlis.model.PathExpression.class::isInstance)
                .map(guru.interlis.model.PathExpression.class::cast).toList()) {
            assertInstanceOf(guru.interlis.model.PathStep.ThisStep.class, path.path().steps().getFirst());
            org.junit.jupiter.api.Assertions.assertTrue(graph.type(path.typeId()).orElseThrow()
                    instanceof guru.interlis.model.TextType
                    || graph.type(path.typeId()).orElseThrow() instanceof guru.interlis.model.NumericType);
        }
        for (String invalid : List.of(model.replace("INTERLIS.len(THIS)", "THIS"),
                model.replace("THIS > 10", "THIS"),
                model.replace("Maximum:", "Minimum:"),
                model.replace("INTERLIS 2.4", "INTERLIS 2.3"))) {
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, compile(invalid), invalid);
            assertFalse(rejected.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT),
                    rejected.rejection().toString());
        }
    }

    @Test
    void externalConstraintCannotBindAnUnrelatedSameNamedClassOrMissingTarget() {
        for (String target : List.of("A", "Other.A", "Missing")) {
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, compile("""
                    INTERLIS 2.4; MODEL M AT "x" VERSION "1" =
                      TOPIC Other = CLASS A = END A; END Other;
                      TOPIC T =
                        CONSTRAINTS OF %s = MANDATORY CONSTRAINT #true; END;
                      END T;
                    END M.
                    """.formatted(target)), target);
            assertFalse(rejected.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT),
                    rejected.rejection().toString());
        }
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile("""
                INTERLIS 2.4; MODEL M AT "x" VERSION "1" =
                  TOPIC Base = CLASS A = value: 0..10; END A; END Base;
                  TOPIC T EXTENDS Base =
                    CONSTRAINTS OF A = MANDATORY CONSTRAINT value > 0; END;
                  END T;
                END M.
                """));
    }
}
