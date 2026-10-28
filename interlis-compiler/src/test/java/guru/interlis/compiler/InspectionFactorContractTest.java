package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 2.3 §2.13/2.15 and 2.4 §3.13/3.15: inspection factors, not a discarded OF path. */
class InspectionFactorContractTest {
    private static final String BASE = """
            TOPIC T =
              STRUCTURE S = value : TEXT; END S;
              STRUCTURE R = children : BAG OF S; END R;
              CLASS A = items : BAG OF R; label : TEXT; END A;
              CLASS Child EXTENDS A = END Child;
              CLASS Other = END Other;
              VIEW I INSPECTION OF part~A->items->children; = ALL OF part; END I;
            """;
    private CompilationOutcome.Succeeded ok(String version, String body) {
        return ViewBindingContractTest.success(version, body);
    }
    private void bad(String version, String body, String rule) {
        ViewBindingContractTest.rule(version, body, rule);
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, ViewBindingContractTest.compile(version, body));
        assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT), result.analysis().diagnostics().toString());
    }
    @Test void namedAndInlineFactorsRetainTheirChildrenAndCollectionTypes() {
        for (String v : List.of("2.3", "2.4")) for (String form : List.of("INSPECTION I", "INSPECTION OF A->items->children", "INSPECTION OF local~A->items->children")) {
            var result = ok(v, BASE + "VIEW V PROJECTION OF p~A; = n := INTERLIS.elementCount(" + form + " OF p); END V; END T;");
            var graph = result.schema().semanticGraph();
            var factor = graph.expressions().stream().filter(InspectionExpression.class::isInstance).map(InspectionExpression.class::cast).findFirst().orElseThrow();
            assertEquals(form.equals("INSPECTION I"), factor.view().isPresent());
            assertEquals("A", graph.symbol(factor.source()).orElseThrow().name().value());
            assertEquals("S", graph.symbol(factor.element()).orElseThrow().name().value());
            assertEquals(2, factor.children().size());
            assertEquals(2, ((PathExpression)graph.expression(factor.path()).orElseThrow()).path().steps().size());
            assertInstanceOf(PathExpression.class, graph.expression(factor.restriction().orElseThrow()).orElseThrow());
            var bag = assertInstanceOf(CollectionType.class, graph.type(factor.typeId()).orElseThrow());
            assertEquals(CollectionKind.BAG, bag.collectionKind());
            assertEquals(factor.element(), ((StructureValueType)graph.type(bag.elementType()).orElseThrow()).target().orElseThrow());
        }
    }
    @Test void inlineFactorInItsOwnSourceClassDoesNotRecursivelyBuildTheClass() {
        for (String v : List.of("2.3", "2.4")) ok(v, "TOPIC T = STRUCTURE S = x : TEXT; END S; CLASS A = items : BAG OF S; MANDATORY CONSTRAINT INTERLIS.elementCount(INSPECTION OF A->items OF THIS) > 0; END A; END T;");
    }
    @Test void restrictionRequiresOneCompatibleObjectAndUnrestrictedFactorKeepsItsSet() {
        for (String v : List.of("2.3", "2.4")) {
            ok(v, BASE + "VIEW V PROJECTION OF p~Child; = n := INTERLIS.elementCount(INSPECTION I OF p); allCount := INTERLIS.elementCount(INSPECTION I); END V; END T;");
            bad(v, BASE + "VIEW V PROJECTION OF p~Other; = n := INTERLIS.elementCount(INSPECTION I OF p); END V; END T;", "INSPECTION_FACTOR_RESTRICTION");
            bad(v, BASE + "VIEW V PROJECTION OF p~A; = n := INTERLIS.elementCount(INSPECTION I OF p->label); END V; END T;", "INSPECTION_FACTOR_RESTRICTION");
            bad(v, BASE + "VIEW V PROJECTION OF p~A; = x : TEXT := INSPECTION I; END V; END T;", "TEXT");
            bad(v, BASE + "VIEW V PROJECTION OF p~A; WHERE INSPECTION I; = END V; END T;", "BOOLEAN");
        }
    }
    @Test void invalidTargetsAndDecompositionsHaveLanguageDiagnostics() {
        for (String v : List.of("2.3", "2.4")) {
            for (String form : List.of("INSPECTION Missing", "INSPECTION A", "INSPECTION OF A->label", "AREA INSPECTION OF A->items", "INSPECTION OF A->missing"))
                bad(v, BASE + "VIEW V PROJECTION OF p~A; = n := INTERLIS.elementCount(" + form + "); END V; END T;", "INSPECTION_FACTOR_TARGET");
            bad(v, BASE + "VIEW P PROJECTION OF A; = END P; VIEW V PROJECTION OF A; = n := INTERLIS.elementCount(INSPECTION P); END V; END T;", "INSPECTION_FACTOR_TARGET");
        }
    }
    @Test void inheritedInspectionAndNestedParentKeepTheirIdentity() {
        for (String v : List.of("2.3", "2.4")) {
            var result = ok(v, BASE + "VIEW J EXTENDS I = END J; VIEW V PROJECTION OF A; = n := INTERLIS.elementCount(INSPECTION J OF A); END V; END T;");
            var graph = result.schema().semanticGraph();
            var factor = graph.expressions().stream().filter(InspectionExpression.class::isInstance).map(InspectionExpression.class::cast).findFirst().orElseThrow();
            assertEquals("J", graph.symbol(factor.view().orElseThrow()).orElseThrow().name().value());
            var view = (ViewSymbol)graph.symbol(factor.view().orElseThrow()).orElseThrow();
            assertEquals("R", graph.symbol(((ViewSymbol.Inspection)view.formation().orElseThrow()).parent()).orElseThrow().name().value());
            var source = result.analysis().syntaxSnapshots().values().iterator().next().source();
            int offset = source.text().indexOf("INSPECTION J") + "INSPECTION ".length();
            assertEquals(factor.view().orElseThrow(), LanguageQueries.create().symbolAt(result.analysis(), source.range(offset, offset), CancellationToken.none()).known().orElseThrow().symbol());
        }
    }
    @Test void finalAndExtendedViewAttributesAreIndependentlyChecked() {
        for (String v : List.of("2.3", "2.4")) {
            bad(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; = x := A->x; END V; VIEW W EXTENDS V = x (EXTENDED) : TEXT := A->x; END W; END T;", "VIEW_FINAL");
            bad(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; = x (EXTENDED) : TEXT := A->x; END V; END T;", "EXTENDED_BASE");
        }
    }
    @Test void importOrderReuseAndSourceNavigationStayConsistent() {
        for (String v : List.of("2.3", "2.4")) for (boolean first : List.of(true, false)) {
            var engine = CompilerEngines.create(); AnalysisSnapshot previous = null;
            for (int revision = 1; revision <= 3; revision++) {
                var base = new SourceText(new SourceId("memory:/" + (first ? "a" : "z") + "-base.ili"), new SourceRevision(revision),
                        "INTERLIS " + v + "; MODEL Base AT \"x\" VERSION \"1\" = TOPIC T = STRUCTURE S = value : TEXT; END S; CLASS A = items : " + (revision == 2 ? "TEXT" : "BAG OF S") + "; END A; END T; END Base.");
                var use = CompilerTestSupport.source("memory:/" + (first ? "z" : "a") + "-use.ili", "INTERLIS " + v + "; MODEL M AT \"x\" VERSION \"1\" = IMPORTS Base; TOPIC T = DEPENDS ON Base.T; VIEW V PROJECTION OF p~Base.T.A; = n := INTERLIS.elementCount(INSPECTION OF Base.T.A->items OF p); END V; END T; END M.");
                var request = CompilerTestSupport.request(List.of("M"), Map.of(base.id(), base, use.id(), use),
                        List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(use), "Base", base, CompilerTestSupport.parse(base))), CompilerOptions.defaults());
                var reused = engine.analyze(request, previous == null ? AnalysisReuse.none() : new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), CancellationToken.none());
                var fresh = engine.analyze(request, CancellationToken.none());
                assertEquals(fresh.diagnostics().stream().map(d -> List.of(d.code(), d.arguments(), d.primaryRange())).toList(), reused.diagnostics().stream().map(d -> List.of(d.code(), d.arguments(), d.primaryRange())).toList());
                assertEquals(revision != 2, reused.diagnostics().isEmpty(), () -> reused.diagnostics().toString());
                if (revision != 2) {
                    var factor = reused.semanticGraph().expressions().stream().filter(InspectionExpression.class::isInstance).map(InspectionExpression.class::cast).findFirst().orElseThrow();
                    int offset = use.text().indexOf("A->items");
                    var ref = LanguageQueries.create().symbolAt(reused, use.range(offset, offset), CancellationToken.none()).known().orElseThrow();
                    assertEquals(factor.source(), ref.symbol());
                    var compiled = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(request, CancellationToken.none()));
                    assertTrue(compiled.schema().semanticGraph().expressions().stream().anyMatch(InspectionExpression.class::isInstance));
                }
                previous = reused;
            }
        }
    }
    @Test void wrongTranslationTargetsCannotHideBehindEqualTypes() {
        for (String v : List.of("2.3", "2.4")) for (String formation : List.of("AGGREGATION OF a~A EQUAL (a->left)", "INSPECTION OF a~A->left")) {
            String sourceBody = "TOPIC T = STRUCTURE S = value : TEXT; END S; CLASS A = left : BAG OF S; right : BAG OF S; END A; VIEW V " + formation + "; = END V; END T;";
            for (boolean wrong : List.of(false, true)) {
                String targetBody = wrong ? sourceBody.replace("a->left", "a->right").replace("A->left", "A->right") : sourceBody;
                var source = CompilerTestSupport.source("memory:/translation.ili", "INTERLIS " + v + "; MODEL Base AT \"x\" VERSION \"1\" = " + sourceBody + " END Base. MODEL M AT \"x\" VERSION \"1\" TRANSLATION OF Base [\"1\"] = " + targetBody + " END M.");
                var request = CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
                var result = CompilerEngines.create().compile(request, CancellationToken.none());
                if (wrong) assertInstanceOf(CompilationOutcome.Rejected.class, result);
                else assertInstanceOf(CompilationOutcome.Succeeded.class, result, result.toString());
            }
        }
    }

    @Test void factorDependenciesAndSpecialContextAreLanguageErrors() {
        for (String v : List.of("2.3", "2.4")) {
            String declarations = "TOPIC T = STRUCTURE S = value : TEXT; END S; CLASS A = items : BAG OF S; END A; END T; TOPIC U = CLASS B = MANDATORY CONSTRAINT INTERLIS.elementCount(INSPECTION OF M.T.A->items) > 0; END B; END U;";
            bad(v, declarations, "VIEW_TOPIC_DEPENDENCY");
            ok(v, declarations.replace("TOPIC U =", "TOPIC U = DEPENDS ON M.T;"));
            bad(v, BASE + "VIEW V EXTENDS I = n := part->value->PARENT; END V; END T;", "VIEW_INSPECTION_CONTEXT");
            bad(v, BASE + "VIEW V PROJECTION OF A; = n := PARENT; END V; END T;", "VIEW_INSPECTION_CONTEXT");
        }
    }

    @Test void restrictionsCannotSelectMultipleSourceObjects() {
        for (String v : List.of("2.3", "2.4")) {
            String defs = v.equals("2.4") ? BASE.replace("TOPIC T =", "TOPIC T = OID AS INTERLIS.STANDARDOID;") : BASE;
            bad(v, defs + "ASSOCIATION Link = a -- {0..*} A; other -- {0..1} Other; END Link; VIEW V PROJECTION OF p~Other; = n := INTERLIS.elementCount(INSPECTION I OF p->a); END V; END T;", "INSPECTION_FACTOR_RESTRICTION");
        }
    }

}
