package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.format.FormatOptions;
import guru.interlis.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormatterSemanticsTest {
    @Test
    void recompilesTheSameLocalClosureForBothLanguageVersions() {
        for (String version : List.of("2.3", "2.4")) {
            String input = "INTERLIS " + version + "; MODEL M AT \"https://example.org\" VERSION \"1\" = "
                    + "TOPIC T = OID AS INTERLIS.UUIDOID; CLASS Catalogue = Key: TEXT*30; END Catalogue; "
                    + "CLASS Item = Key: TEXT*20; Active: BOOLEAN; "
                    + "!!@ ilivalid.msg = \"Key is required\"\n"
                    + "MANDATORY CONSTRAINT DEFINED(Key); CONSTRAINT >= 90.00% Active; "
                    + "EXISTENCE CONSTRAINT Key REQUIRED IN Catalogue:Key; UNIQUE WHERE Active: Key; "
                    + "SET CONSTRAINT WHERE Active: INTERLIS.objectCount(ALL) >= 1; END Item; "
                    + "ASSOCIATION Link = items -- {0..*} Item; catalogue -<> {1} Catalogue; END Link; "
                    + "END T; END M.";
            var output = FormatterSoStandardTest.verify(input,
                    new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, 40));
            var original = compile(input);
            var formatted = compile(output);
            assertEquals(original.analysis().diagnostics().stream()
                            .map(d -> List.of(d.code(), d.severity(), d.phase(), d.arguments())).toList(),
                    formatted.analysis().diagnostics().stream()
                            .map(d -> List.of(d.code(), d.severity(), d.phase(), d.arguments())).toList());
            var before = original.schema().semanticGraph();
            var after = formatted.schema().semanticGraph();
            assertEquals(symbols(before), symbols(after));
            var expected = constraints(before);
            assertEquals(5, expected.size());
            assertEquals(expected, constraints(after));
        }
    }

    @Test
    void formatterDistinguishesLiteralSignsFromBinaryOperators() {
        String input = """
                INTERLIS 2.4;
                MODEL M AT "https://example.org" VERSION "1" =
                  DOMAIN Negative = -10..-1;
                  UNIT metre; factor = 0.12e-3 [metre];
                  TOPIC T = CLASS C =
                    n: 0..10;
                    MANDATORY CONSTRAINT (n-1) >= -1;
                    MANDATORY CONSTRAINT (n--1) >= +007;
                  END C; END T;
                END M.
                """;
        String output = FormatterSoStandardTest.verify(input,
                new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, 60));
        for (String literal : List.of("-10", "-1", "+007", "0.12e-3")) {
            assertTrue(output.contains(literal), output);
        }
        assertEquals(constraints(compile(input).schema().semanticGraph()),
                constraints(compile(output).schema().semanticGraph()));
    }

    @Test
    void sectionFormattingPreservesOrderMetadataAndReferenceTargets() {
        String input = """
                INTERLIS 2.4;
                MODEL M AT "https://example.org" VERSION "1" =
                  DOMAIN
                  !!@ label = "Coordinate"
                  G (GENERIC) = COORD NUMERIC, NUMERIC;
                  A = COORD 0..10, 0..10;
                  CONTEXT empty = chosen = G = A;
                  UNIT metre; square = (metre * metre);
                  DOMAIN L (ABSTRACT) = POLYLINE WITH (STRAIGHTS) VERTEX INTERLIS.LineCoord;
                END M.
                """;
        var output = FormatterSoStandardTest.verify(input,
                new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, 40));
        var original = compile(input).schema().semanticGraph();
        var formatted = compile(output).schema().semanticGraph();
        assertEquals(symbols(original), symbols(formatted));
        assertEquals(referenceTargets(original), referenceTargets(formatted));
    }

    private static List<?> referenceTargets(SemanticGraph graph) {
        return graph.references().stream()
                .map(ref -> ref.segments().stream()
                        .map(segment -> List.of(segment.text(), segment.target().map(target -> name(graph, target))))
                        .toList())
                .toList();
    }

    private static CompilationOutcome.Succeeded compile(String text) {
        var source = CompilerTestSupport.source("memory:/format-semantic.ili", text);
        var request = CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source),
                List.of(), CompilerOptions.defaults());
        var result = CompilerEngines.create().compile(request, CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> result instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().diagnostics().toString() : result.toString());
    }

    private static List<?> symbols(SemanticGraph graph) {
        return graph.declarationsInOrder().stream()
                .map(symbol -> List.of(symbol.kind(), declaredName(symbol), symbol.header().modifiers(),
                        symbol.owner().map(owner -> name(graph, owner)),
                        symbol.header().metaAttributes().entries().stream()
                                .map(entry -> List.of(entry.key(), entry.value().text())).toList())).toList();
    }

    private static List<?> constraints(SemanticGraph graph) {
        return graph.declarationsInOrder().stream().filter(ConstraintSymbol.class::isInstance)
                .map(ConstraintSymbol.class::cast).map(symbol -> {
                    var definition = symbol.definition();
                    return List.of(definition.getClass().getSimpleName(), declaredName(symbol),
                            switch (definition) {
                                case MandatoryConstraint c -> expression(graph, c.condition());
                                case PlausibilityConstraint c -> List.of(c.percentage(), c.direction(),
                                        expression(graph, c.condition()));
                                case ExistenceConstraint c -> List.of(expression(graph, c.source()),
                                        c.targets().stream().map(target -> List.of(name(graph, target.viewable()),
                                                expression(graph, target.path()))).toList());
                                case UniquenessConstraint c -> List.of(c.basket(),
                                        c.where().map(id -> expression(graph, id)),
                                        c.keys().stream().map(id -> expression(graph, id)).toList(),
                                        c.localKeys().stream().map(key -> List.of(expression(graph, key.prefix()),
                                                key.fields().stream().map(id -> expression(graph, id)).toList())).toList());
                                case SetConstraint c -> List.of(c.scope(),
                                        c.where().map(id -> expression(graph, id)), expression(graph, c.condition()));
                                case DomainConstraint ignored -> throw new AssertionError("unexpected domain condition");
                            });
                }).toList();
    }

    /** Compare the sampled payload without snapshot-local IDs and source positions. */
    private static List<?> expression(SemanticGraph graph, ExpressionId id) {
        var expr = graph.expression(id).orElseThrow();
        var fields = new ArrayList<>();
        fields.add(expr.getClass().getSimpleName());
        fields.add(expr.typeState().getClass().getSimpleName());
        fields.add(graph.type(expr.typeId()).orElseThrow().getClass().getSimpleName());
        fields.add(expr.children().stream().map(child -> expression(graph, child)).toList());
        switch (expr) {
            case BinaryExpression binary -> fields.add(binary.operator());
            case LiteralExpression literal ->
                    fields.add(List.of(literal.literalKind(), literal.lexeme(), literal.textValue()));
            case PathExpression path -> fields.add(path.path().steps().stream().map(step ->
                    step instanceof PathStep.AttributeStep attribute
                            ? List.of(attribute.name(), name(graph, attribute.target()))
                            : step.getClass().getSimpleName()).toList());
            case FunctionCallExpression call ->
                    fields.add(List.of(call.functionName(), call.targetFunction().map(target -> name(graph, target))));
            default -> { }
        }
        return fields;
    }

    private static String declaredName(Symbol symbol) {
        String value = symbol.name().value();
        // The compiler derives this display name from the original UTF-16 offset.
        if (symbol.kind() == SymbolKind.CONSTRAINT && value.matches("constraint@[0-9]+")) {
            return "constraint#" + symbol.declarationOrder();
        }
        return value;
    }

    private static String name(SemanticGraph graph, SymbolId id) {
        var symbol = graph.symbol(id).orElseThrow();
        return symbol.owner().map(owner -> name(graph, owner) + ".").orElse("") + symbol.name().value();
    }
}
