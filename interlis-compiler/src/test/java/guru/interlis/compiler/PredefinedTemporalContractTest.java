package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Normative date/time declarations, rather than an independent date parser. */
class PredefinedTemporalContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.";
    }
    private static CompilationRequest request(String version, String body, int revision) {
        var source = new SourceText(new SourceId("memory:/temporal.ili"), new SourceRevision(revision), model(version, body));
        return CompilerTestSupport.request(List.of("F"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String version, String body) {
        var result = CompilerEngines.create().compile(request(version, body, 1), TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> body + "\n" + (result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics() : result));
    }
    private static void bad(String version, String body) {
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request(version, body, 1), TOKEN), body);
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejected.rejection().reasons());
        assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains("FORMAT_BOUNDS")),
                () -> rejected.analysis().diagnostics().toString());
    }
    private static String bounds(String domain, String lower, String upper) {
        return "DOMAIN D = FORMAT INTERLIS." + domain + " \"" + lower + "\" .. \"" + upper + "\";";
    }

    @Test void unchangedPinnedOriginalIsAcceptedWithBothAttributeBounds() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../testdata/conformance/g2-xml-date/FormattedType.ili"));
        assertEquals("df09fe0b6a252b0c5a5cbca847406c6d67a961f04acf12aca12dcf901fb658e1",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        var source = CompilerTestSupport.source("memory:/original.ili", new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        var input = CompilerTestSupport.request(List.of("FormattedType"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
        var result = assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create().compile(input, TOKEN));
        var formats = result.schema().semanticGraph().types().stream().filter(FormattedType.class::isInstance)
                .map(FormattedType.class::cast).filter(f -> f.minimum().isPresent()).toList();
        assertEquals(2, formats.size());
        assertTrue(formats.stream().allMatch(f -> f.minimum().orElseThrow().equals("2010-1-1") && f.structure().isPresent() && f.parts().size() == 5));
        assertEquals(Set.of("2110-1-1", "2110-12-31"), formats.stream().map(f -> f.maximum().orElseThrow()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void dateWidthsAreVersionedAndComponentRangesAreShared() {
        for (var version : List.of("2.3", "2.4")) {
            ok(version, bounds("XMLDate", "2010-01-01", "2110-12-31"));
            var shortDate = bounds("XMLDate", "2010-1-1", "2110-12-31");
            if (version.equals("2.3")) ok(version, shortDate); else bad(version, shortDate);
            for (String invalid : List.of("1581-01-01", "2010-00-01", "2010-13-01", "2010-01-00", "2010-01-32"))
                bad(version, bounds("XMLDate", invalid, "2110-12-31"));
            // There is no additional calendar engine: the declared Day range is 1..31.
            ok(version, bounds("XMLDate", "2010-02-31", "2110-12-31"));
        }
    }

    @Test void timeWidthsAndSecondPrecisionComeFromTheDeclarations() {
        for (var version : List.of("2.3", "2.4")) {
            ok(version, bounds("XMLTime", "00:00:00.000", "23:59:59.999"));
            ok(version, bounds("XMLDateTime", "2010-01-01T00:00:00.000", "2110-12-31T23:59:59.999"));
            var shortTime = bounds("XMLTime", "00:0:0.000", "23:59:59.999");
            if (version.equals("2.3")) ok(version, shortTime); else bad(version, shortTime);
            for (String invalid : List.of("0:00:00.000", "24:00:00.000", "00:60:00.000", "00:00:60.000", "00:00:00.00", "00:00:00.0000"))
                bad(version, bounds("XMLTime", invalid, "23:59:59.999"));
        }
    }

    @Test void predefinedFormatsExposeStructuresAttributesInheritanceAndReferences() {
        for (var version : List.of("2.3", "2.4")) {
            var result = ok(version, "CLASS C = date: INTERLIS.XMLDate; time: INTERLIS.XMLTime; stamp: INTERLIS.XMLDateTime; END C;");
            var graph = result.schema().semanticGraph();
            var references = result.analysis().queries().findQualifiedName(new QualifiedName(List.of("INTERLIS", "XMLDate")));
            assertEquals(1, references.size());
            assertInstanceOf(DomainSymbol.class, result.analysis().queries().symbol(references.getFirst()).known().orElseThrow());
            var formats = graph.types().stream().filter(FormattedType.class::isInstance).map(FormattedType.class::cast).toList();
            assertEquals(3, formats.size());
            for (var format : formats) {
                assertInstanceOf(Origin.PredefinedOrigin.class, format.origin());
                var structure = assertInstanceOf(StructureSymbol.class, graph.symbol(format.structure().orElseThrow()).orElseThrow());
                var expected = format.predefined().orElseThrow() == TemporalKind.DATETIME ? 6 : 3;
                var fields = format.parts().stream().filter(p -> p.attribute().isPresent()).toList();
                assertEquals(expected, fields.size());
                assertEquals(expected, result.analysis().queries().effectiveMembers(new SymbolRef(result.analysis().snapshotId(), structure.id())).known().orElseThrow().size());
                for (var field : fields) {
                    var attribute = assertInstanceOf(AttributeSymbol.class, graph.symbol(field.attribute().orElseThrow()).orElseThrow());
                    assertTrue(graph.declaredType(attribute.id()).isPresent());
                    if (version.equals("2.3") && format.predefined().orElseThrow() == TemporalKind.DATE) assertTrue(field.width().isEmpty());
                }
                if (format.predefined().orElseThrow() == TemporalKind.DATETIME) assertTrue(format.baseDomain().isPresent());
            }
            var date = formats.stream().filter(f -> f.predefined().orElseThrow() == TemporalKind.DATE).findFirst().orElseThrow();
            var year = assertInstanceOf(AttributeSymbol.class, graph.symbol(date.parts().getFirst().attribute().orElseThrow()).orElseThrow());
            var yearDomain = assertInstanceOf(DomainReferenceType.class, graph.type(year.declaredType()).orElseThrow());
            var domain = assertInstanceOf(DomainSymbol.class, graph.symbol(yearDomain.targetDomain()).orElseThrow());
            var range = assertInstanceOf(NumericType.class, graph.type(domain.typeId()).orElseThrow()).range();
            assertEquals("1582", range.minimum().orElseThrow().lexeme());
            assertInstanceOf(MetaObjectSymbol.class, graph.symbol(range.referenceSystem().orElseThrow().target()).orElseThrow());
            if (version.equals("2.4")) assertEquals(Optional.of(BigInteger.valueOf(4)), date.parts().getFirst().width());
        }
    }

    @Test void nestedPredefinedFormatsUseTheSameBoundStructuresAndValueChecks() {
        String body = "STRUCTURE S = date: INTERLIS.GregorianDate; END S; "
                + "DOMAIN D = FORMAT BASED ON S (date/INTERLIS.XMLDate) \"2010-01-01\" .. \"2110-12-31\"; "
                + "CLASS C = value: D; MANDATORY CONSTRAINT value == \"2015-02-31\"; END C;";
        for (var version : List.of("2.3", "2.4")) {
            var result = ok(version, body); var graph = result.schema().semanticGraph();
            var nested = graph.types().stream().filter(FormattedType.class::isInstance).map(FormattedType.class::cast)
                    .filter(f -> f.predefined().isEmpty()).findFirst().orElseThrow();
            assertTrue(nested.parts().getFirst().format().isPresent());
            assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request(version, body.replace("2015-02-31", "2015-02-32"), 1), TOKEN));
        }
    }

    @Test void explicitWidthsOnPredefinedStructuresApplyInBothVersions() {
        String body = "DOMAIN D = FORMAT BASED ON INTERLIS.GregorianDate (Year/4 \"-\" Month/2 \"-\" Day/2) \"2010-01-01\" .. \"2110-12-31\";";
        for (var version : List.of("2.3", "2.4")) { ok(version, body); bad(version, body.replace("2010-01-01", "2010-1-1")); }
    }

    @Test void predefinedDomainAndStructureInheritanceUseOrdinaryBoundTargets() {
        for (var version : List.of("2.3", "2.4")) {
            var body = "STRUCTURE S EXTENDS INTERLIS.GregorianDate = extra: 0..9; END S; "
                    + "DOMAIN D EXTENDS INTERLIS.XMLDate = FORMAT BASED ON S (INHERITANCE \"/\" extra) \"2010-01-01/0\" .. \"2110-12-31/9\"; "
                    + "CLASS C (ABSTRACT) = value: D; MANDATORY CONSTRAINT value==\"2015-02-31/5\"; END C;";
            var result = ok(version, body); var graph=result.schema().semanticGraph();
            var s=graph.symbols().stream().filter(x -> x.name().value().equals("S")).map(StructureSymbol.class::cast).findFirst().orElseThrow();
            assertEquals("INTERLIS.GregorianDate",graph.symbol(s.baseType().orElseThrow()).orElseThrow().name().value());
            var d=graph.symbols().stream().filter(x -> x.name().value().equals("D")).map(DomainSymbol.class::cast).findFirst().orElseThrow();
            assertEquals("INTERLIS.XMLDate",graph.symbol(d.baseDomain().orElseThrow()).orElseThrow().name().value());
            var format=assertInstanceOf(FormattedType.class,graph.type(d.typeId()).orElseThrow());
            assertEquals(Optional.of(s.id()),format.structure()); assertEquals(7,format.parts().size());
            assertEquals(4,format.parts().stream().filter(p -> p.attribute().isPresent()).count());
            ok(version, "DOMAIN D EXTENDS INTERLIS.XMLDate = \"2010-01-01\" .. \"2110-12-31\";");
            bad(version,body.replace("2010-01-01/0","2010-13-01/0"));
            for (String invalid : List.of("STRUCTURE S EXTENDS INTERLIS.UnknownBase = END S;",
                    "CLASS C (ABSTRACT) EXTENDS INTERLIS.UnknownBase = END C;")) {
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,invalid,1),TOKEN));
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
                assertTrue(rejected.analysis().diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().value().equals("ILI-SEM-0001")
                        && diagnostic.arguments().toString().contains("INTERLIS.UnknownBase")));
            }
        }
    }

    @Test void validInvalidValidEditsAgreeWithFreshAnalysis() {
        var engine = CompilerEngines.create();
        for (var version : List.of("2.3", "2.4")) {
            AnalysisSnapshot previous = null;
            for (int step = 1; step <= 3; step++) {
                var request = request(version, bounds("XMLDate", step == 2 ? "2010-13-01" : "2010-01-01", "2110-12-31"), step);
                var reused = engine.analyze(request, previous == null ? AnalysisReuse.none() : new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), TOKEN);
                var fresh = CompilerEngines.create().analyze(request, TOKEN);
                assertEquals(fresh.diagnostics(), reused.diagnostics()); assertEquals(step != 2, fresh.diagnostics().isEmpty());
                assertEquals(List.copyOf(fresh.semanticGraph().types()), List.copyOf(reused.semanticGraph().types()));
                previous = reused;
            }
        }
    }

    @Test void importedDomainsAndAliasPathsDoNotDependOnFileOrder() {
        for (var version : List.of("2.3", "2.4")) {
            var base = CompilerTestSupport.source("memory:/base.ili", "INTERLIS " + version
                    + "; MODEL Base AT \"https://example.org\" VERSION \"1\" = "
                    + bounds("XMLDate", "2010-01-01", "2110-12-31") + " END Base.");
            var root = CompilerTestSupport.source("memory:/root.ili", "INTERLIS " + version
                    + "; MODEL F AT \"https://example.org\" VERSION \"1\" = IMPORTS Base; "
                    + "CLASS C = value: Base.D; other: Base.D; MANDATORY CONSTRAINT value == \"2015-02-31\"; END C; END F.");
            for (boolean reversed : List.of(false, true)) {
                Map<SourceId, SourceText> sources = new LinkedHashMap<>();
                if (reversed) { sources.put(root.id(), root); sources.put(base.id(), base); }
                else { sources.put(base.id(), base); sources.put(root.id(), root); }
                var binding = CompilerTestSupport.binding(CompilerTestSupport.parse(root), "Base", base, CompilerTestSupport.parse(base));
                var input = CompilerTestSupport.request(List.of("F"), sources, List.of(binding), CompilerOptions.defaults());
                var result = assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create().compile(input, TOKEN));
                var graph = result.schema().semanticGraph();
                for (var attribute : graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                        .filter(a -> a.name().value().equals("value") || a.name().value().equals("other")).toList()) {
                    var type = graph.effectiveType(attribute.id()).orElseThrow();
                    assertInstanceOf(FormattedType.class, graph.type(type.effectiveType()).orElseThrow());
                }
            }
        }
    }
}
