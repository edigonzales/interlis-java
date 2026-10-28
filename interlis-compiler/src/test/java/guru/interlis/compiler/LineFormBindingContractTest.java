package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** K4 line declarations: identities, public geometric data, translations and closure/reuse. */
class LineFormBindingContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String SEGMENTS = "STRUCTURE S EXTENDS INTERLIS.LineSegment = a: TEXT*10; END S; LINE FORM Curve: S;";
    private static final String COORD = "DOMAIN Coord = COORD 0.000..100.000, 0.000..100.000;";
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL M AT \"https://example.org\" VERSION \"1\" = " + body + " END M.";
    }
    private static CompilationRequest request(String text) {
        var source = CompilerTestSupport.source("memory:/lines.ili", text);
        return CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String text) {
        var result = CompilerEngines.create().compile(request(text), TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
    }
    private static void bad(String text, String reason) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request(text), TOKEN));
        assertTrue(result.analysis().diagnostics().toString().contains(reason), result.analysis().diagnostics()::toString);
        assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
    }
    private static Symbol named(SemanticGraph g, String name) {
        return g.symbols().stream().filter(s -> s.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static GeometryType geometry(SemanticGraph g, String name) {
        return (GeometryType)g.type(((DomainSymbol)named(g, name)).typeId()).orElseThrow();
    }

    @Test void customFormsExposeSegmentInheritanceMembersAndSourceReferences() {
        for (String v : List.of("2.3", "2.4")) {
            var result = ok(model(v, SEGMENTS + COORD + "DOMAIN Line = POLYLINE WITH (Curve,STRAIGHTS,ARCS) VERTEX Coord;"));
            var graph = result.schema().semanticGraph(); var form = (LineFormSymbol)named(graph, "Curve");
            assertEquals(named(graph, "S").id(), form.segmentStructure().orElseThrow());
            var segment = (StructureSymbol)named(graph, "S");
            assertEquals(named(graph, "INTERLIS.LineSegment").id(), segment.baseType().orElseThrow());
            assertEquals(List.of("SegmentEndPoint", "a"), graph.effectiveMembers(segment.id()).stream()
                    .map(m -> graph.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
            assertEquals(List.of(form.id(), named(graph, "INTERLIS.STRAIGHTS").id(), named(graph, "INTERLIS.ARCS").id()), geometry(graph, "Line").lineForms());
            var arc = (StructureSymbol)named(graph, "INTERLIS.ArcSegment");
            assertTrue(arc.header().has(Modifier.FINAL));
            assertEquals(List.of("SegmentEndPoint", "ArcPoint", "Radius"), graph.effectiveMembers(arc.id()).stream()
                    .map(m -> graph.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
            var source = result.analysis().syntaxSnapshots().values().iterator().next().source();
            for (String needle : List.of("S;DOMAIN", "Curve,STRAIGHTS", "STRAIGHTS,ARCS", "ARCS) VERTEX", "Coord;")) {
                int offset = source.text().indexOf(needle); assertTrue(offset >= 0, needle);
                assertTrue(LanguageQueries.create().symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().isPresent(), needle);
            }
        }
    }

    @Test void selectedSchemaKeepsCanonicalBuiltinsWithoutForeignInspectionOwners() {
        var result = ok(model("2.4", SEGMENTS + COORD + "DOMAIN L = POLYLINE WITH (ARCS) VERTEX Coord;")
                + " MODEL Other AT \"https://example.org\" VERSION \"1\" = " + COORD
                + " TOPIC T = CLASS A = line: POLYLINE WITH (STRAIGHTS,ARCS) VERTEX Coord; END A;"
                + " VIEW V INSPECTION OF edge~A->line; = END V; END T; END Other.");
        var graph = result.schema().semanticGraph();
        assertFalse(result.analysis().diagnostics().toString().contains("INSPECTION_GEOMETRY_SCHEMA"));
        assertTrue(graph.symbols().stream().noneMatch(s -> s.name().value().equals("Other") || s.name().value().equals("V")));
        for (Symbol symbol : graph.symbols()) symbol.owner().ifPresent(owner -> assertTrue(graph.symbol(owner).isPresent()));
        var arc = (StructureSymbol) named(graph, "INTERLIS.ArcSegment");
        var radius = arc.members().stream().map(graph::symbol).flatMap(Optional::stream).filter(s -> s.name().value().equals("Radius"))
                .map(AttributeSymbol.class::cast).findFirst().orElseThrow();
        var numeric = (NumericType)graph.type(radius.declaredType()).orElseThrow();
        assertEquals(named(graph, "INTERLIS.LENGTH").id(), numeric.range().unit().orElseThrow());
    }

    @Test void geometryRetainsDirectionLineAttributesAndExactTolerance() {
        for (String v : List.of("2.3", "2.4")) {
            String body = COORD + "STRUCTURE Attrs = label: TEXT; END Attrs; DOMAIN Line = DIRECTED POLYLINE WITH (STRAIGHTS) VERTEX Coord; "
                    + "Surface = SURFACE WITH (ARCS,STRAIGHTS) VERTEX Coord WITHOUT OVERLAPS > 0.0100 LINE ATTRIBUTES Attrs;";
            if (v.equals("2.4")) bad(model(v, body), "LINE_ATTRIBUTES_VERSION");
            var graph = ok(model(v, v.equals("2.3") ? body : body.replace(" LINE ATTRIBUTES Attrs", "").replace("0.0100", "0.010"))).schema().semanticGraph();
            assertTrue(geometry(graph, "Line").directed()); assertFalse(geometry(graph, "Surface").directed());
            var surface = geometry(graph, "Surface"); assertTrue(surface.withoutOverlaps());
            assertEquals(DecimalLiteral.parse(v.equals("2.3") ? "0.0100" : "0.010"),
                    assertInstanceOf(GeometryType.Explicit.class, surface.overlap().orElseThrow()).value());
            if (v.equals("2.3")) assertEquals(named(graph, "Attrs").id(), surface.lineAttributes().orElseThrow());
            else assertTrue(surface.lineAttributes().isEmpty());
        }
    }

    @Test void wrongTargetsAndUnknownQualifiersAreLanguageErrors() {
        for (String v : List.of("2.3", "2.4")) {
            bad(model(v, "STRUCTURE S = END S; LINE FORM Curve: S;"), "LINE_FORM_STRUCTURE");
            bad(model(v, "CLASS S = END S; LINE FORM Curve: S;"), "ILI-SEM-0001");
            bad(model(v, "LINE FORM Curve: Missing;"), "ILI-SEM-0001");
            bad(model(v, SEGMENTS + COORD + "DOMAIN L = POLYLINE WITH (Missing.Curve) VERTEX Coord;"), "ILI-SEM-0001");
            ok(model(v, SEGMENTS + COORD + "DOMAIN L = POLYLINE WITH (Curve,Curve) VERTEX Coord;"));
            bad(model(v, SEGMENTS + " DOMAIN Number = 0..10; Line = POLYLINE WITH (Curve) VERTEX Number;"), "LINE_VERTEX_COORDINATE");
            bad(model(v, "STRUCTURE S EXTENDS INTERLIS.StraightSegment = END S; LINE FORM Curve: S;"), "OBJECT_FINAL");
        }
    }

    @Test void contractRequirementIsVersionSpecific() {
        bad(model("2.3", SEGMENTS).replace("CONTRACTED ", ""), "LINE_FORM_CONTRACTED");
        ok(model("2.4", SEGMENTS).replace("CONTRACTED ", ""));
    }

    @Test void translatedFormsCompareBoundTargetsAndFormSetsNotTheirSpellingOrOrder() {
        for (String v : List.of("2.3", "2.4")) {
            String body = "STRUCTURE S1 EXTENDS INTERLIS.LineSegment = END S1; STRUCTURE S2 EXTENDS INTERLIS.LineSegment = END S2; "
                    + "LINE FORM Curve: S1;" + COORD + "DOMAIN L = POLYLINE WITH (Curve,STRAIGHTS) VERTEX Coord;";
            String original = model(v, body).replace("MODEL M", "MODEL Original").replace("END M.", "END Original.");
            String translated = model(v, body.replace("S1", "T1").replace("S2", "T2").replace("Curve", "Courbe")
                    .replace("Courbe,STRAIGHTS", "STRAIGHTS,Courbe")).replace("INTERLIS " + v + "; ", "")
                    .replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Original [\"1\"] =");
            ok(original + translated);
            bad(original + translated.replace("Courbe: T1", "Courbe: T2"), "TRANSLATION_LINE_FORM");
            bad(original + translated.replace("STRAIGHTS,Courbe", "ARCS,Courbe"), "TRANSLATION");
        }
    }

    @Test void importedFormsAndSegmentComponentsAreOrderIndependentAndReuseTracksEdits() {
        for (String v : List.of("2.3", "2.4")) for (boolean reverse : List.of(false, true)) {
            var base = CompilerTestSupport.source("memory:/" + (reverse ? "z" : "a") + ".ili", model(v, SEGMENTS).replace("MODEL M", "MODEL Base").replace("END M.", "END Base."));
            var consumer = CompilerTestSupport.source("memory:/" + (reverse ? "a" : "z") + ".ili", model(v,
                    "IMPORTS Base; STRUCTURE Local EXTENDS Base.S = END Local; LINE FORM LocalCurve: Local;" + COORD
                    + "DOMAIN L = POLYLINE WITH (Base.Curve,LocalCurve) VERTEX Coord;"));
            var b = CompilerTestSupport.parse(base); var c = CompilerTestSupport.parse(consumer);
            var req = CompilerTestSupport.request(List.of("M"), Map.of(base.id(), base, consumer.id(), consumer),
                    List.of(CompilerTestSupport.binding(c, "Base", base, b)), CompilerOptions.defaults());
            var engine = CompilerEngines.create(); var result = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(req, TOKEN));
            var graph = result.schema().semanticGraph();
            assertEquals(named(graph, "S").id(), ((LineFormSymbol)named(graph, "Curve")).segmentStructure().orElseThrow());
            var edited = new SourceText(base.id(), new SourceRevision(2), base.text().replace("EXTENDS INTERLIS.LineSegment", ""));
            var changed = CompilerTestSupport.request(List.of("M"), Map.of(edited.id(), edited, consumer.id(), consumer),
                    List.of(CompilerTestSupport.binding(c, "Base", edited, CompilerTestSupport.parse(edited))), CompilerOptions.defaults());
            var fresh = engine.analyze(changed, TOKEN);
            var reused = engine.analyze(changed, new AnalysisReuse(Optional.of(result.analysis()), result.analysis().syntaxSnapshots()), TOKEN);
            assertEquals(fresh.diagnostics(), reused.diagnostics());
            assertTrue(fresh.diagnostics().toString().contains("LINE_FORM_STRUCTURE"));
        }
    }
}
