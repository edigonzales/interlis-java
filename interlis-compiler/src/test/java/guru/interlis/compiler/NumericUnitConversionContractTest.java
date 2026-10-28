package guru.interlis.compiler;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.QueryResult;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import guru.interlis.syntax.UnitDeclarationSyntax;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** K6: exact conversion operands and bound units in scalar numeric extensions. */
class NumericUnitConversionContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final List<String> VERSIONS = List.of("2.3", "2.4");
    private static final String UNITS = "UNIT Length (ABSTRACT); Time (ABSTRACT); "
            + "Meter [m] EXTENDS Length; Second [s] EXTENDS Time; Kilometre [km] = 1000 [m]; ";

    private static String model(String version, String name, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL " + name
                + " AT \"https://example.org\" VERSION \"1\" = " + body + " END " + name + ".";
    }

    private static CompilationRequest request(String text) {
        var source = CompilerTestSupport.source("memory:/units.ili", text);
        return CompilerTestSupport.request(List.of("F"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
    }

    private static CompilationOutcome.Succeeded ok(String version, String body) {
        return succeeded(request(model(version, "F", body)));
    }

    private static CompilationOutcome.Succeeded succeeded(CompilationRequest request) {
        var outcome = CompilerEngines.create().compile(request, TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : outcome.toString());
    }

    private static CompilationOutcome.Rejected rejected(CompilationRequest request, String reason) {
        var outcome = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request, TOKEN));
        assertTrue(outcome.analysis().diagnostics().stream().anyMatch(d -> hasReason(d, reason)),
                () -> outcome.analysis().diagnostics().toString());
        assertTrue(outcome.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
        assertFalse(outcome.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
        assertTrue(outcome.analysis().diagnostics().stream().allMatch(d -> d.failureId().isEmpty()));
        return outcome;
    }

    private static boolean hasReason(Diagnostic diagnostic, String reason) {
        return diagnostic.arguments().toString().contains(reason);
    }

    private static UnitSymbol unit(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(UnitSymbol.class::isInstance).map(UnitSymbol.class::cast)
                .filter(u -> u.name().value().equals(name)).findFirst().orElseThrow();
    }

    private static DomainSymbol domain(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast)
                .filter(d -> d.name().value().equals(name)).findFirst().orElseThrow();
    }

    private static AttributeSymbol attribute(SemanticGraph graph, String owner, String name) {
        return graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(a -> a.name().value().equals(name)
                        && graph.symbol(a.owner().orElseThrow()).orElseThrow().name().value().equals(owner))
                .findFirst().orElseThrow();
    }

    private static NumericType effective(SemanticGraph graph, TypeId type) {
        var id = new TypeRelations(graph).resolveAliases(new TypeRef(graph.snapshotId(), type))
                .known().orElseThrow().effectiveType();
        return assertInstanceOf(NumericType.class, graph.type(id).orElseThrow());
    }

    @Test void sixOriginalFalseAcceptsAreSourceErrorsInBothVersions() throws Exception {
        for (String version : VERSIONS) {
            for (var sample : Map.of("zero-factor", "UNIT_CONVERSION_ZERO_FACTOR", "zero-divisor", "UNIT_CONVERSION_ZERO_DIVISOR",
                    "numeric-unit-mismatch", "NUMERIC_UNIT_EXTENSION").entrySet()) {
                String text = Files.readString(Path.of("../testdata/conformance/k6-units/" + version + "-" + sample.getKey() + ".ili"));
                var result = rejected(request(text), sample.getValue());
                assertTrue(result.analysis().diagnostics().stream().filter(d -> hasReason(d, sample.getValue()))
                        .allMatch(d -> d.code().value().equals("ILI-SEM-0010")));
            }
            ok(version, "UNIT M [m]; Z = 2 [m]; Q = 1 / 3 [m]; DOMAIN D = 0..100 [m]; X EXTENDS D = 0..10 [m];");
        }
    }

    @Test void everyZeroSpellingAndOperandPositionReportsTheExactLiteral() {
        for (String version : VERSIONS) for (String zero : List.of("0", "-0", "+0", "0.000", "-0.000", "0e100", "0.000e-100")) {
            for (String prefix : List.of("", "2 * ", "2 / ", "2 / 3 * ")) {
                String reason = prefix.endsWith("/ ") ? "UNIT_CONVERSION_ZERO_DIVISOR" : "UNIT_CONVERSION_ZERO_FACTOR";
                String text = model(version, "F", "UNIT M [m]; Z = " + prefix + zero + " [m];");
                var outcome = rejected(request(text), reason);
                var diagnostics = outcome.analysis().diagnostics().stream().filter(d -> hasReason(d, reason)).toList();
                assertEquals(1, diagnostics.size(), diagnostics::toString);
                var diagnostic = diagnostics.getFirst();
                assertEquals("ILI-SEM-0010", diagnostic.code().value());
                var range = diagnostic.primaryRange().orElseThrow();
                int offset = text.indexOf("Z = ") + "Z = ".length() + prefix.length();
                assertEquals(offset, range.startOffset());
                assertEquals(offset + zero.length(), range.endOffset());
                assertEquals(zero, text.substring(range.startOffset(), range.endOffset()));
                assertEquals(new SourceId("memory:/units.ili"), range.source());
                assertEquals(new SourceRevision(1), range.revision());
            }
        }
    }

    @Test void conversionOperandsRetainExactValuesOperatorsPrecisionAndRanges() {
        String formula = "900719925474099312345.125e2 / 3 * -2 / PI * LNBASE";
        for (String version : VERSIONS) {
            var result = ok(version, "UNIT M [m]; Exact = " + formula + " [m]; Tiny = 1e-1000 [m]; Huge = 1e1000 [m];");
            var graph = result.schema().semanticGraph();
            var exact = unit(graph, "Exact");
            var factors = exact.conversionFactors();
            assertEquals(List.of(UnitSymbol.Operator.MULTIPLY, UnitSymbol.Operator.DIVIDE, UnitSymbol.Operator.MULTIPLY,
                    UnitSymbol.Operator.DIVIDE, UnitSymbol.Operator.MULTIPLY), factors.stream().map(UnitSymbol.ConversionFactor::operator).toList());
            assertEquals(List.of(UnitSymbol.ConstantKind.DECIMAL, UnitSymbol.ConstantKind.DECIMAL, UnitSymbol.ConstantKind.DECIMAL,
                    UnitSymbol.ConstantKind.PI, UnitSymbol.ConstantKind.LNBASE), factors.stream().map(UnitSymbol.ConversionFactor::kind).toList());
            var decimal = factors.getFirst().decimal().orElseThrow();
            assertEquals("900719925474099312345.125e2", decimal.lexeme());
            assertEquals(0, new BigDecimal("900719925474099312345.125e2").compareTo(decimal.exactValue()));
            assertEquals(new DecimalPrecision(3, 2, PrecisionOrigin.LEXICAL), decimal.precision());
            assertEquals("-2", factors.get(2).decimal().orElseThrow().lexeme());
            assertTrue(factors.get(3).decimal().isEmpty()); assertTrue(factors.get(4).decimal().isEmpty());
            var source = result.analysis().syntaxSnapshots().values().iterator().next().source();
            assertEquals(List.of("900719925474099312345.125e2", "3", "-2", "PI", "LNBASE"), factors.stream()
                    .map(f -> source.text().substring(f.range().startOffset(), f.range().endOffset())).toList());
            assertEquals(unit(graph, "m").id(), exact.derivedFrom().orElseThrow());
            assertEquals(0, new BigDecimal("1e-1000").compareTo(unit(graph, "Tiny").conversionFactors().getFirst().decimal().orElseThrow().exactValue()));
            assertEquals(0, new BigDecimal("1e1000").compareTo(unit(graph, "Huge").conversionFactors().getFirst().decimal().orElseThrow().exactValue()));
            var syntax = result.analysis().syntaxSnapshots().values().iterator().next().root().models().getFirst().members().stream()
                    .filter(UnitDeclarationSyntax.class::isInstance).map(UnitDeclarationSyntax.class::cast)
                    .filter(u -> u.name().equals("Exact")).findFirst().orElseThrow();
            assertEquals(factors, syntax.conversionFactors());
            assertThrows(UnsupportedOperationException.class, () -> factors.clear());
            assertThrows(UnsupportedOperationException.class, () -> syntax.conversionFactors().clear());
        }
    }

    @Test void conversionTextAndLiteralRangesUseUtf16WithUnicodeAndCrLf() {
        for (String version : VERSIONS) {
            var result = ok(version, "!! 🌐 ä\r\nUNIT M [m]; Exact = 2 / PI [m];");
            var graph = result.schema().semanticGraph();
            assertEquals("2 / PI [m]", unit(graph, "Exact").definition().orElseThrow().strip());
            var text = result.analysis().syntaxSnapshots().values().iterator().next().source().text();
            for (var factor : unit(graph, "Exact").conversionFactors())
                assertEquals(factor.kind() == UnitSymbol.ConstantKind.PI ? "PI" : "2",
                        text.substring(factor.range().startOffset(), factor.range().endOffset()));
            var source = model(version, "F", "!! 🌐 ä\r\nUNIT M [m]; Z = 2 / -0.00 [m];");
            var rejected = rejected(request(source), "UNIT_CONVERSION_ZERO_DIVISOR");
            var range = rejected.analysis().diagnostics().stream().filter(d -> hasReason(d, "UNIT_CONVERSION_ZERO_DIVISOR"))
                    .findFirst().orElseThrow().primaryRange().orElseThrow();
            assertEquals("-0.00", source.substring(range.startOffset(), range.endOffset()));
        }
    }

    @Test void identityAndFunctionConversionsStaySeparateFromNumericalFactors() {
        for (String version : VERSIONS) {
            var graph = ok(version, "UNIT M [m]; Identity = [m]; Symbolic = FUNCTION // nonlinear(value) // [m];").schema().semanticGraph();
            var identity = unit(graph, "Identity"); var function = unit(graph, "Symbolic");
            assertTrue(identity.conversionFactors().isEmpty()); assertTrue(identity.conversionFunction().isEmpty());
            assertEquals(unit(graph, "m").id(), identity.derivedFrom().orElseThrow());
            assertTrue(function.conversionFactors().isEmpty());
            assertTrue(function.conversionFunction().orElseThrow().contains("nonlinear(value)"));
            assertTrue(function.definition().orElseThrow().contains("FUNCTION"));
            assertEquals(identity.derivedFrom(), function.derivedFrom());
        }
    }

    @Test void unresolvedConversionTargetsRetainOperandsInThePartialModel() {
        for (String version : VERSIONS) {
            var outcome = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(
                    request(model(version, "F", "UNIT Z = 2 / PI [Missing];")), TOKEN));
            assertTrue(outcome.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
            assertFalse(outcome.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
            var partial = unit(outcome.analysis().semanticGraph(), "Z");
            assertTrue(partial.derivedFrom().isEmpty());
            assertEquals(List.of(UnitSymbol.ConstantKind.DECIMAL, UnitSymbol.ConstantKind.PI),
                    partial.conversionFactors().stream().map(UnitSymbol.ConversionFactor::kind).toList());
            assertEquals("2", partial.conversionFactors().getFirst().decimal().orElseThrow().lexeme());
            assertTrue(outcome.analysis().diagnostics().stream().allMatch(d -> d.failureId().isEmpty()));
        }
    }

    @Test void concreteNumericUnitsStayFixedEvenForConvertibleUnits() {
        for (String version : VERSIONS) {
            String base = UNITS + "DOMAIN D = 0..100 [m]; ";
            ok(version, base + "Narrow EXTENDS D = 0..10 [m]; Inherited EXTENDS D = 0..10;");
            for (String wrong : List.of("s", "km"))
                rejected(request(model(version, "F", base + "Wrong EXTENDS D = 0..10 [" + wrong + "];")), "NUMERIC_UNIT_EXTENSION");
            rejected(request(model(version, "F", UNITS + "DOMAIN Plain = 0..100; Wrong EXTENDS Plain = 0..10 [m];")), "NUMERIC_UNIT_EXTENSION");
        }
    }

    @Test void abstractUnitsCanBeSpecializedWhileUnrelatedUnitsAreRejected() {
        for (String version : VERSIONS) {
            String base = UNITS + "DOMAIN General (ABSTRACT) = NUMERIC [Length]; ";
            var graph = ok(version, base + "Distance EXTENDS General = 0..10 [m]; Converted EXTENDS General = 0..10 [km];").schema().semanticGraph();
            assertEquals(Optional.of(unit(graph, "km").id()), effective(graph, domain(graph, "Converted").typeId()).range().unit());
            rejected(request(model(version, "F", base + "Wrong EXTENDS General = 0..10 [s];")), "NUMERIC_UNIT_EXTENSION");
            ok(version, UNITS + "DOMAIN Any (ABSTRACT) = NUMERIC [INTERLIS.ANYUNIT]; Distance EXTENDS Any = 0..10 [m]; "
                    + "Unbounded (ABSTRACT) = NUMERIC; Timed EXTENDS Unbounded = 0..10 [s];");
        }
    }

    @Test void publicExtensionQueriesAgreeWithoutChangingAssignmentRules() {
        for (String version : VERSIONS) {
            var graph = ok(version, UNITS + "DOMAIN D = 0..100 [m]; Small = 0..10 [m]; Converted = 0..10 [km]; "
                    + "Timed = 0..10 [s]; Plain = 0..100; "
                    + "General (ABSTRACT) = NUMERIC [Length]; Any (ABSTRACT) = NUMERIC [INTERLIS.ANYUNIT]; "
                    + "Unbounded (ABSTRACT) = NUMERIC; Alias EXTENDS D = 0..100; "
                    + "TOPIC T = CLASS Probe = d: D; small: Small; timed: Timed; END Probe; END T;").schema().semanticGraph();
            var relations = new TypeRelations(graph);
            for (var pair : List.of(List.of("Small", "D"), List.of("Small", "Alias"), List.of("Small", "General"),
                    List.of("Converted", "General"), List.of("Timed", "Any"), List.of("Timed", "Unbounded")))
                assertEquals(RelationDecision.ALLOWED, extension(relations, graph, pair.get(0), pair.get(1)).decision(), pair::toString);
            for (var pair : List.of(List.of("Converted", "D"), List.of("Timed", "D"), List.of("Timed", "General"), List.of("Small", "Plain")))
                assertEquals(RelationDecision.DISALLOWED, extension(relations, graph, pair.get(0), pair.get(1)).decision(), pair::toString);
            var d = new TypeRef(graph.snapshotId(), attribute(graph, "Probe", "d").declaredType());
            var small = new TypeRef(graph.snapshotId(), attribute(graph, "Probe", "small").declaredType());
            var timed = new TypeRef(graph.snapshotId(), attribute(graph, "Probe", "timed").declaredType());
            assertInstanceOf(DomainReferenceType.class, graph.type(small.type()).orElseThrow());
            assertEquals(List.of(domain(graph, "Small").id()), relations.resolveAliases(small).known().orElseThrow().aliasPath());
            assertEquals(RelationDecision.ALLOWED, relations.canExtend(small, d, ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED, relations.canExtend(timed, d, ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()).known().orElseThrow().decision());
            assertInstanceOf(QueryResult.Unresolved.class, relations.canExtend(new TypeRef(graph.snapshotId(), new TypeId("missing")), ref(graph, "D"), ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()));
            assertThrows(SnapshotMismatchException.class, () -> relations.canExtend(new TypeRef(new SnapshotId("another-snapshot"), domain(graph, "D").typeId()),
                    ref(graph, "D"), ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()));
        }
    }

    private static TypeRef ref(SemanticGraph graph, String domain) {
        return new TypeRef(graph.snapshotId(), domain(graph, domain).typeId());
    }

    private static TypeRelation extension(TypeRelations relations, SemanticGraph graph, String actual, String base) {
        return relations.canExtend(ref(graph, actual), ref(graph, base), ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()).known().orElseThrow();
    }

    private static CompilationRequest imported(String version, String definitions, String consumer, boolean baseFirst, long revision) {
        var base = new SourceText(new SourceId("memory:/" + (baseFirst ? "a" : "z") + "-base.ili"), new SourceRevision(revision), model(version, "Base", definitions));
        var use = new SourceText(new SourceId("memory:/" + (baseFirst ? "z" : "a") + "-use.ili"), new SourceRevision(revision), model(version, "F", "IMPORTS Base; " + consumer));
        return CompilerTestSupport.request(List.of("F"), Map.of(base.id(), base, use.id(), use),
                List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(use), "Base", base, CompilerTestSupport.parse(base))), CompilerOptions.defaults());
    }

    @Test void importedAliasesInheritUnitsBeforeAttributeAndExpressionBinding() {
        String definitions = UNITS + "DOMAIN D = 0..100 [m]; Alias EXTENDS D = 0..100; "
                + "STRUCTURE Parent = value: D; END Parent;";
        String consumer = "DOMAIN Narrow EXTENDS Base.Alias = 0..10; Plain = 0..100; "
                + "TOPIC T = STRUCTURE Child EXTENDS Base.Parent = value (EXTENDED): 0..10; "
                + "MANDATORY CONSTRAINT value > 0; END Child; CLASS C = distance: Narrow; plain: Plain; "
                + "MANDATORY CONSTRAINT distance > 0; END C; END T;";
        for (String version : VERSIONS) for (boolean order : List.of(true, false)) {
            var graph = succeeded(imported(version, definitions, consumer, order, 1)).schema().semanticGraph();
            var expected = Optional.of(unit(graph, "m").id());
            assertEquals(expected, effective(graph, domain(graph, "Narrow").typeId()).range().unit());
            assertEquals(expected, effective(graph, attribute(graph, "Child", "value").declaredType()).range().unit());
            assertTrue(effective(graph, domain(graph, "Plain").typeId()).range().unit().isEmpty());
            var paths = graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast).toList();
            assertFalse(paths.isEmpty());
            assertTrue(paths.stream().allMatch(p -> effective(graph, p.typeId()).range().unit().equals(expected)));
            rejected(imported(version, definitions, consumer.replace("value (EXTENDED): 0..10", "value (EXTENDED): 0..10 [Base.s]"), order, 1), "ATTRIBUTE_OVERRIDE_TYPE");
        }
    }

    @Test void inheritedUnitsDoNotMutateSharedUnitlessAliases() {
        for (String version : VERSIONS) {
            String body = UNITS + "DOMAIN D = 0..100 [m]; DOMAIN Plain = 0..100; "
                    + "TOPIC T = STRUCTURE Parent = value: D; END Parent; "
                    + "STRUCTURE Child EXTENDS Parent = value (EXTENDED): Plain; END Child; "
                    + "CLASS C = plain: Plain; END C; END T;";
            var outcome = rejected(request(model(version, "F", body)), "ATTRIBUTE_OVERRIDE_TYPE");
            var graph = outcome.analysis().semanticGraph();
            assertTrue(effective(graph, domain(graph, "Plain").typeId()).range().unit().isEmpty());
            assertTrue(effective(graph, attribute(graph, "C", "plain").declaredType()).range().unit().isEmpty());
            assertTrue(effective(graph, attribute(graph, "Child", "value").declaredType()).range().unit().isEmpty());
        }
    }

    @Test void validInvalidValidEditsMatchFreshAnalysisAndPreserveOldSnapshots() {
        String consumer = "UNIT Imported = 2 [Base.km]; DOMAIN Narrow EXTENDS Base.D = 0..10; "
                + "TOPIC T = CLASS C = n: Narrow; MANDATORY CONSTRAINT n > 0; END C; END T;";
        for (String version : VERSIONS) {
            var engine = CompilerEngines.create();
            var initialRequest = imported(version, UNITS + "DOMAIN D = 0..100 [m];", consumer, false, 1);
            var initial = succeeded(initialRequest); var previous = initial.analysis(); long revision = 1;
            for (String factor : List.of("0", "1000", "1 / 0", "1000")) {
                var next = imported(version, UNITS.replace("1000 [m]", factor + " [m]") + "DOMAIN D = 0..100 [m];", consumer, false, ++revision);
                var reused = engine.analyze(next, new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), TOKEN);
                var fresh = engine.analyze(next, TOKEN);
                assertEquals(fresh.diagnostics().stream().map(d -> List.of(d.code(), d.arguments(), d.primaryRange())).toList(),
                        reused.diagnostics().stream().map(d -> List.of(d.code(), d.arguments(), d.primaryRange())).toList());
                assertEquals(factor.equals("1000"), reused.diagnostics().isEmpty());
                assertEquals(unit(fresh.semanticGraph(), "km").conversionFactors(), unit(reused.semanticGraph(), "km").conversionFactors());
                assertEquals(effective(fresh.semanticGraph(), domain(fresh.semanticGraph(), "Narrow").typeId()).range(),
                        effective(reused.semanticGraph(), domain(reused.semanticGraph(), "Narrow").typeId()).range());
                previous = reused;
            }
            assertEquals("1000", unit(initial.schema().semanticGraph(), "km").conversionFactors().getFirst().decimal().orElseThrow().lexeme());
        }
    }

    @Test void translationsCompareConversionFactorsAndOperatorsByStructuredValues() {
        for (String version : VERSIONS) {
            String original = model(version, "Base", "UNIT Meter [m]; Kilometre [km] = 1000 / 2 [m];");
            String translated = model(version, "F", "UNIT Metre9 [a]; Grande [b] = 1000 / 2 [a];")
                    .replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Base [\"1\"] =")
                    .substring(("INTERLIS " + version + "; ").length());
            succeeded(request(original + " " + translated));
            succeeded(request(original + " " + translated.replace("1000 / 2", "1000.0 / 2.00")));
            String base = model(version, "Base", "UNIT Root (ABSTRACT); U EXTENDS Root;");
            String good = model(version, "F", "UNIT Racine (ABSTRACT); V EXTENDS Racine;")
                    .replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Base [\"1\"] =")
                    .substring(("INTERLIS " + version + "; ").length());
            succeeded(request(base + " " + good));
            rejected(request(base + " " + good.replace("V EXTENDS", "V (ABSTRACT) EXTENDS")), "TRANSLATION_REFERENCE");
            for (String changed : List.of(translated.replace("1000 / 2", "1000 / 3"), translated.replace("1000 / 2", "1000 * 2"))) {
                var result = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request(original + " " + changed), TOKEN));
                assertTrue(result.analysis().diagnostics().stream().anyMatch(d -> hasReason(d, "TRANSLATION")),
                        () -> result.analysis().diagnostics().toString());
                assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
            }
        }
    }
}
