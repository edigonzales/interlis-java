package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ArithmeticExpressionTypeContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String UNITS = "UNIT Length (ABSTRACT); m EXTENDS Length; km = 1000 [m]; ";
    private static final String VIEW_BASE = "TOPIC T = CLASS C = n : 0..10; END C; "
            + "VIEW V PROJECTION OF c~C; = ";

    @Test
    void computedSingletonsControlAssignmentAndKeepOriginalOperands() {
        for (String value : List.of("1 + 1000", "1000 + 1")) {
            var rejected = rejected("2.4", attribute("0..10", value), "ILI-SEM-0005");
            assertEquals(RelationDecision.DISALLOWED, assignment(rejected.analysis().semanticGraph(), "value"));
        }
        rejected("2.4", attribute("3..3", "4"), "ILI-SEM-0005");
        ok("2.4", attribute("3..3", "3"));
        var success = ok("2.4", attribute("3..3", "+001 + 2"));
        var graph = success.schema().semanticGraph();
        var binary = expression(graph, "+001 + 2", success);
        var result = numeric(graph, binary.typeId());
        assertBounds(result, "3", "3");
        assertEquals(RelationDecision.ALLOWED, assignment(graph, "value"));
        assertEquals("3", result.range().minimum().orElseThrow().lexeme());
        var original = assertInstanceOf(LiteralExpression.class, graph.expression(binary.left()).orElseThrow());
        assertEquals("+001", original.lexeme());
        assertEquals(PrecisionOrigin.LEXICAL, original.decimal().orElseThrow().precision().origin());
        assertEquals(binary.range(), ((Origin.DeclaredOrigin) result.origin()).range());
    }

    @Test
    void exactSmallIntervalsAndLiteralTreesPublishOnlyProvenBounds() {
        String body = VIEW_BASE
                + "a := c->n + c->n; b := 3-5; product := c->n * -2; reversed := -2 * c->n; "
                + "half := 1/2; smallTree := (1.0 + 0.25) * 2; uncertain := 0.1+0.2; "
                + "third := 1/3; zeroDivisor := 1/0; variableDivisor := 1/(c->n-1); "
                + "halfRange := c->n / 2; declared : 0..20 := c->n+c->n; END V; END T;";
        var graph = ok("2.4", body).schema().semanticGraph();
        assertBounds(type(graph, "a"), "0", "20");
        assertBounds(type(graph, "n"), "0", "10", PrecisionOrigin.LEXICAL);
        assertBounds(type(graph, "b"), "-2", "-2");
        assertBounds(type(graph, "product"), "-20", "0");
        assertEquals(type(graph, "product").range(), type(graph, "reversed").range());
        assertBounds(type(graph, "half"), "0.5", "0.5");
        assertBounds(type(graph, "smallTree"), "2.5", "2.5");
        for (String name : List.of("uncertain", "third", "zeroDivisor", "variableDivisor", "halfRange")) {
            assertEquals(NumericRange.unbounded(), type(graph, name).range(), name);
        }
        assertFalse(attribute(graph, "n").mandatory());
        assertFalse(attribute(graph, "a").mandatory());
        assertNotEquals(attribute(graph, "n").declaredType(), attribute(graph, "a").declaredType());
        assertEquals(RelationDecision.DISALLOWED, relation(graph, type(graph, "halfRange"), type(graph, "n"), true));
        assertEquals(RelationDecision.DISALLOWED, relation(graph, type(graph, "a"), type(graph, "declared"), true));
        rejected("2.4", attribute("1..1", "(10000000000000000+1)-10000000000000000"), "ILI-SEM-0005");
        rejected("2.4", attribute("0.3..0.3", "0.1+0.2"), "ILI-SEM-0005");
        ok("2.4", "CLASS C = n : 0..10; MANDATORY CONSTRAINT (n / 0) >= 1; END C;");
    }

    @Test
    void concreteUnitsAreCheckedInAssignmentsOverridesAndPublicQueries() {
        for (String version : List.of("2.3", "2.4")) {
            var rejected = rejected(version, UNITS + attribute("1..1 [km]", "1 [m]"), "ILI-SEM-0005");
            assertEquals(RelationDecision.DISALLOWED, assignment(rejected.analysis().semanticGraph(), "value"));
            String override = "CLASS Base = value : 1..1 [km]; END Base; "
                    + "CLASS Derived EXTENDS Base = value (EXTENDED) : 1..1 [km] := %s; END Derived;";
            rejected(version, UNITS + override.formatted("1 [m]"), "ILI-SEM-0005");
            ok(version, UNITS + override.formatted("1 [km]"));
            for (String expression : List.of("1 [km]", "1", "INTERLIS.convertUnit(1000 [m])")) {
                var success = ok(version, UNITS + attribute("1..1 [km]", expression));
                assertEquals(RelationDecision.ALLOWED, assignment(success.schema().semanticGraph(), "value"));
            }
            var success = ok(version, UNITS + "FUNCTION check(v : NUMERIC) : BOOLEAN; "
                    + "CLASS C = MANDATORY CONSTRAINT check(1[m]); END C;");
            var graph = success.schema().semanticGraph();
            var call = graph.expressions().stream().filter(FunctionCallExpression.class::isInstance)
                    .map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
            var actual = numeric(graph, graph.expression(call.children().getFirst()).orElseThrow().typeId());
            var target = (FunctionSymbol) graph.symbol(call.targetFunction().orElseThrow()).orElseThrow();
            assertEquals("m", graph.symbol(actual.range().unit().orElseThrow()).orElseThrow().name().value());
            assertEquals(RelationDecision.ALLOWED, relation(graph, actual,
                    numeric(graph, target.parameters().getFirst().typeId()), false));
        }
        rejected("2.4", UNITS + attribute("0..10 [km]", "1[m]+1[m]"), "ILI-SEM-0005");
        var graph = ok("2.4", UNITS + VIEW_BASE
                + "a := 1[m]+1; b := 1+1[m]; area := 1[m]*1[m]; END V; END T;").schema().semanticGraph();
        assertBounds(type(graph, "a"), "2", "2");
        assertEquals(type(graph, "a").range(), type(graph, "b").range());
        assertTrue(type(graph, "a").range().unit().isPresent());
        assertEquals(NumericRange.unbounded(), type(graph, "area").range());
        assertEquals(RelationDecision.DISALLOWED, relation(graph, type(graph, "area"), type(graph, "n"), true));
    }

    @Test
    void undefinedAndInvalidArithmeticRemainSeparateFromOpenNumericResults() {
        for (String expression : List.of("UNDEFINED + 1", "1 + UNDEFINED")) {
            var rejected = rejected("2.4", attribute("MANDATORY 0..10", expression), "MANDATORY_VALUE");
            var binary = expression(rejected.analysis().semanticGraph(), expression, rejected.analysis());
            var descriptor = rejected.analysis().semanticGraph().type(binary.typeId()).orElseThrow();
            assertEquals("UNDEFINED", assertInstanceOf(GenericType.class, descriptor).name());
        }
        rejected("2.4", attribute("0..10", "\"x\" + 1"), "ILI-SEM-0005");
        var source = source("2.4", attribute("NUMERIC", "1.0e999 + 1"));
        var defaults = CompilerOptions.defaults();
        var options = new CompilerOptions(defaults.semanticProfile(), defaults.allowedLanguages(),
                defaults.warningPolicy(), defaults.budget().withMaxSemanticWorkUnits(1), defaults.overlays());
        var cancelled = assertInstanceOf(CompilationOutcome.Cancelled.class,
                CompilerEngines.create().compile(request(source, options), TOKEN));
        assertEquals(guru.interlis.execution.CancellationReason.RESOURCE_LIMIT, cancelled.reason());
    }

    @Test
    void changedImportedOperandBoundsAgreeWithFreshAnalysis() {
        var engine = CompilerEngines.create();
        var root = source("2.4", "IMPORTS Base; TOPIC T = CLASS C = n : Base.N; END C; "
                + "VIEW V PROJECTION OF c~C; = out : 0..20 := c->n+c->n; END V; END T;");
        var base = CompilerTestSupport.source("memory:/arith-base.ili",
                "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = DOMAIN N = 0..10; END Base.");
        AnalysisSnapshot previous = null;
        NumericRange oldRange = null;
        SemanticGraph oldGraph = null;
        for (int i = 0; i < 3; i++) {
            var revision = new SourceText(base.id(), new SourceRevision(i + 1),
                    i == 1 ? base.text().replace("0..10", "0..11") : base.text());
            var binding = CompilerTestSupport.binding(CompilerTestSupport.parse(root), "Base", revision,
                    CompilerTestSupport.parse(revision));
            var request = CompilerTestSupport.request(List.of("M"), Map.of(root.id(), root, revision.id(), revision),
                    List.of(binding), CompilerOptions.defaults());
            var fresh = engine.analyze(request, TOKEN);
            var reused = previous == null ? fresh : engine.analyze(request,
                    new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), TOKEN);
            assertEquals(fresh.diagnostics().stream().map(d -> List.of(d.code(), d.arguments())).toList(),
                    reused.diagnostics().stream().map(d -> List.of(d.code(), d.arguments())).toList());
            assertEquals(i == 1, fresh.diagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SEM-0005")));
            var range = numeric(reused.semanticGraph(), expression(reused.semanticGraph(),
                    "c->n+c->n", reused).typeId()).range();
            assertEquals(numeric(fresh.semanticGraph(), expression(fresh.semanticGraph(),
                    "c->n+c->n", fresh).typeId()).range(), range);
            if (i == 0) {
                oldGraph = reused.semanticGraph();
                oldRange = range;
            }
            previous = reused;
        }
        assertNotNull(oldGraph);
        var original = oldGraph.expressions().stream().filter(BinaryExpression.class::isInstance)
                .findFirst().orElseThrow();
        assertEquals(oldRange, numeric(oldGraph, original.typeId()).range());
    }

    private static String attribute(String type, String expression) {
        return "CLASS C = value (TRANSIENT): " + type + " := " + expression + "; END C;";
    }

    private static SourceText source(String version, String body) {
        return CompilerTestSupport.source("memory:/arithmetic.ili",
                "INTERLIS " + version + "; CONTRACTED MODEL M AT \"x\" VERSION \"1\" = " + body + " END M.");
    }

    private static CompilationRequest request(SourceText source, CompilerOptions options) {
        return CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source), List.of(), options);
    }

    private static CompilationOutcome.Succeeded ok(String version, String body) {
        var outcome = CompilerEngines.create().compile(request(source(version, body), CompilerOptions.defaults()), TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : outcome.toString());
    }

    private static CompilationOutcome.Rejected rejected(String version, String body, String expected) {
        var outcome = CompilerEngines.create().compile(request(source(version, body), CompilerOptions.defaults()), TOKEN);
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, outcome);
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
        assertTrue(rejected.analysis().diagnostics().toString().contains(expected), rejected.analysis().diagnostics()::toString);
        return rejected;
    }

    private static BinaryExpression expression(SemanticGraph graph, String text, CompilationOutcome.Succeeded success) {
        return expression(graph, text, success.analysis());
    }

    private static BinaryExpression expression(SemanticGraph graph, String text, AnalysisSnapshot analysis) {
        return assertInstanceOf(BinaryExpression.class, findExpression(graph, text, analysis));
    }

    private static TypedExpression findExpression(SemanticGraph graph, String text, AnalysisSnapshot analysis) {
        return graph.expressions().stream().filter(expression -> analysis.syntaxSnapshots().get(expression.range().source())
                .source().slice(expression.range()).equals(text)).findFirst().orElseThrow();
    }

    private static AttributeSymbol attribute(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(attribute -> attribute.name().value().equals(name)).findFirst().orElseThrow();
    }

    private static NumericType type(SemanticGraph graph, String attribute) {
        return numeric(graph, attribute(graph, attribute).declaredType());
    }

    private static NumericType numeric(SemanticGraph graph, TypeId id) {
        return assertInstanceOf(NumericType.class, graph.type(id).orElseThrow());
    }

    private static RelationDecision assignment(SemanticGraph graph, String attribute) {
        var target = attribute(graph, attribute);
        var actual = graph.expression(target.derivations().getFirst()).orElseThrow();
        return relation(graph, numeric(graph, actual.typeId()), numeric(graph, target.declaredType()), false);
    }

    private static RelationDecision relation(SemanticGraph graph, NumericType actual, NumericType target, boolean extension) {
        var queries = new TypeRelations(graph);
        var sourceRef = new TypeRef(graph.snapshotId(), actual.id());
        var targetRef = new TypeRef(graph.snapshotId(), target.id());
        return (extension
                ? queries.canExtend(sourceRef, targetRef, ExtensionContext.DOMAIN_EXTENSION, LanguageVersion.ILI24)
                : queries.canAssign(sourceRef, targetRef, AssignmentContext.NORMAL)).known().orElseThrow().decision();
    }

    private static void assertBounds(NumericType type, String low, String high) {
        assertBounds(type, low, high, PrecisionOrigin.COMPUTED);
    }

    private static void assertBounds(NumericType type, String low, String high, PrecisionOrigin origin) {
        var range = type.range();
        assertBoundary(range.minimum().orElseThrow(), low, origin);
        assertBoundary(range.maximum().orElseThrow(), high, origin);
    }

    private static void assertBoundary(DecimalLiteral literal, String value, PrecisionOrigin origin) {
        assertEquals(0, new BigDecimal(value).compareTo(literal.exactValue()));
        assertEquals(origin, literal.precision().origin());
        assertEquals(literal.exactValue(), new BigDecimal(literal.lexeme()));
    }
}
