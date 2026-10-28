package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 2.3 §2.15 / 2.4 §3.15: schema contents, binding and negative rules. */
class ViewBindingContractTest {
    static CompilationOutcome compile(String version, String declarations) {
        var source = new SourceText(new SourceId("memory:/views.ili"), new SourceRevision(1),
                "INTERLIS " + version + "; MODEL M AT \"https://example.com\" VERSION \"1\" = " + declarations + " END M.");
        return CompilerEngines.create().compile(new CompilationRequest(List.of(new ModelSelector.ByName("M", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults()), CancellationToken.none());
    }
    static CompilationOutcome.Succeeded success(String v, String body) {
        var outcome = compile(v, body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : outcome.toString());
    }
    static void rule(String v, String body, String rule) {
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, compile(v, body));
        assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains(rule)),
                () -> rejected.analysis().diagnostics().toString());
    }
    @Test void projectionKeepsOrderTypesAndDerivationsInSchema() {
        for (String v : List.of("2.3", "2.4")) {
            var result = success(v, """
                    TOPIC T =
                      CLASS A = x : TEXT*12; y : 1 .. 10; END A;
                      VIEW V PROJECTION OF src~A; WHERE src->y > 2; =
                        z := src->y;
                        ALL OF src;
                        MANDATORY CONSTRAINT z > 1;
                      END V;
                    END T;
                    """);
            var graph = result.analysis().semanticGraph();
            var view = graph.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast).findFirst().orElseThrow();
            assertEquals(List.of("z", "x", "y"), view.members().stream().map(id -> graph.symbol(id).orElseThrow().name().value()).toList());
            assertEquals(3, graph.effectiveMembers(view.id()).size());
            assertEquals(1, view.selections().size()); assertEquals(1, view.constraints().size());
            for (var id : view.members()) {
                var attribute = (AttributeSymbol)graph.symbol(id).orElseThrow();
                assertEquals(1, attribute.derivations().size());
                assertTrue(result.schema().semanticGraph().expression(attribute.derivations().getFirst()).isPresent());
            }
            var basis = (ViewSymbol.Base)graph.symbol(view.bases().getFirst()).orElseThrow();
            assertEquals("A", graph.symbol(basis.target()).orElseThrow().name().value());
        }
    }
    @Test void selectionsAndInferredAttributesAreTypeChecked() {
        for (String v : List.of("2.3", "2.4")) {
            rule(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; WHERE A->x; = END V; END T;", "BOOLEAN");
            rule(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; = x := missing->x; END V; END T;", "missing");
            rule(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; = x : BOOLEAN := A->x; END V; END T;", "BOOLEAN");
        }
    }
    @Test void dependencyAndAliasCollisionsHaveSpecificErrors() {
        for (String v : List.of("2.3", "2.4")) {
            rule(v, "TOPIC T = CLASS A = x : TEXT; END A; END T; TOPIC U = VIEW V PROJECTION OF M.T.A; = END V; END U;", "VIEW_TOPIC_DEPENDENCY");
            rule(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; = ALL OF A; x := A->x; END V; END T;", "VIEW_ATTRIBUTE_DUPLICATE");
            rule(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; = A := A->x; END V; END T;", "VIEW_ATTRIBUTE_DUPLICATE");
        }
    }
    @Test void explicitAndTopicViewInheritancePreserveMembers() {
        for (String v : List.of("2.3", "2.4")) {
            var result = success(v, """
                    TOPIC T =
                      CLASS A = x : TEXT; END A;
                      VIEW V PROJECTION OF a~A; = x := a->x; END V;
                      VIEW W EXTENDS V = y := a->x; END W;
                    END T;
                    TOPIC U EXTENDS T = VIEW V (EXTENDED) = z := a->x; END V; END U;
                    """);
            for (var symbol : result.analysis().semanticGraph().symbols()) if (symbol instanceof ViewSymbol view && view.baseView().isPresent())
                assertEquals(2, view.members().size());
        }
    }
    @Test void cyclesAndWrongExtensionTargetsAreLanguageErrors() {
        for (String v : List.of("2.3", "2.4")) {
            rule(v, "TOPIC T = VIEW A EXTENDS B = END A; VIEW B EXTENDS A = END B; END T;", "VIEW_CYCLE");
            rule(v, "TOPIC T = CLASS A = END A; CLASS B = END B; VIEW V PROJECTION OF A; BASE A EXTENDED BY b~B = END V; END T;", "VIEW_BASE_EXTENSION");
        }
    }
    @Test void joinsAndUnionsKeepAllBasesAndCheckMandatoryValues() {
        for (String v : List.of("2.3", "2.4")) {
            var definitions = "TOPIC T = CLASS A = x : MANDATORY TEXT*20; END A; CLASS B = y : MANDATORY TEXT*10; END B; ";
            var joined = success(v, definitions + "VIEW V JOIN OF a~A,b~B (OR NULL); = ALL OF a; ALL OF b; END V; END T;");
            var graph = joined.schema().semanticGraph();
            var view = graph.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast).findFirst().orElseThrow();
            assertInstanceOf(ViewSymbol.Join.class, view.formation().orElseThrow());
            assertEquals(2, view.bases().size());
            assertFalse(((AttributeSymbol)graph.symbol(view.members().getLast()).orElseThrow()).mandatory());
            rule(v, definitions + "VIEW V JOIN OF a~A,b~B (OR NULL); = z : MANDATORY TEXT*20 := b->y; END V; END T;", "VIEW_NULLABLE_ATTRIBUTE");
            success(v, definitions + "VIEW V UNION OF a~A,b~B; = z : MANDATORY TEXT*20 := a->x,b->y; END V; END T;");
            rule(v, definitions + "VIEW V UNION OF a~A,b~B; = z : MANDATORY TEXT*20 := a->x; END V; END T;", "VIEW_UNION_ATTRIBUTE");
            rule(v, definitions + "VIEW V UNION OF a~A,b~B; = ALL OF a; END V; END T;", "VIEW_ALL_OF");
        }
    }
    @Test void aggregationExposesItsBagWithoutMakingItATransferMember() {
        for (String v : List.of("2.3", "2.4")) {
            var result = success(v, "TOPIC T = CLASS A = x : TEXT; y : BOOLEAN; END A; VIEW V AGGREGATION OF a~A EQUAL (a->x,a->y); = count := INTERLIS.elementCount(AGGREGATES); END V; END T;");
            var graph = result.schema().semanticGraph();
            var view = graph.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast).findFirst().orElseThrow();
            assertEquals(2, assertInstanceOf(ViewSymbol.Aggregation.class, view.formation().orElseThrow()).equal().size());
            assertEquals(1, view.members().size());
            var aggregate = graph.symbols().stream().filter(s -> s.name().value().equals("AGGREGATES")).findFirst().orElseThrow();
            assertFalse(view.members().contains(aggregate.id()));
            assertInstanceOf(CollectionType.class, graph.type(((AttributeSymbol)aggregate).declaredType()).orElseThrow());
        }
    }

    @Test void structureInspectionBindsElementAndParentRatherThanRootClass() {
        for (String v : List.of("2.3", "2.4")) {
            var result = success(v, """
                    TOPIC T =
                      STRUCTURE S = value : TEXT; END S;
                      CLASS A = label : TEXT; children : BAG OF S; END A;
                      VIEW V INSPECTION OF child~A->children; =
                        ALL OF child;
                        parentLabel := PARENT->label;
                      END V;
                    END T;
                    """);
            var graph = result.schema().semanticGraph();
            var view = graph.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast).findFirst().orElseThrow();
            var formation = assertInstanceOf(ViewSymbol.Inspection.class, view.formation().orElseThrow());
            assertEquals("S", graph.symbol(formation.element()).orElseThrow().name().value());
            assertEquals("A", graph.symbol(formation.parent()).orElseThrow().name().value());
            assertEquals(List.of("value", "parentLabel"), view.members().stream().map(id -> graph.symbol(id).orElseThrow().name().value()).toList());
            var attribute = (AttributeSymbol)graph.symbol(view.members().getLast()).orElseThrow();
            var path = assertInstanceOf(PathExpression.class, graph.expression(attribute.derivations().getFirst()).orElseThrow());
            assertEquals(formation.parent(), assertInstanceOf(PathStep.InspectionStep.class, path.path().steps().getFirst()).target());
            rule(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V INSPECTION OF A->x; = END V; END T;", "VIEW_INSPECTION_TYPE");
            rule(v, "TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; = x := PARENT->x; END V; END T;", "VIEW_INSPECTION_CONTEXT");
        }
    }

    @Test void geometryInspectionPublishesReachableStructuresAndIndexedPath() {
        for (String v : List.of("2.3", "2.4")) {
            var result = assertInstanceOf(CompilationOutcome.Succeeded.class, compile(v, """
                    DOMAIN Coord = COORD 0.00 .. 100.00, 0.00 .. 100.00;
                    TOPIC T = CLASS A = start : Coord;
                      line : POLYLINE WITH (STRAIGHTS,ARCS) VERTEX Coord;
                    END A;
                    VIEW V INSPECTION OF line~A->line; =
                      MANDATORY CONSTRAINT line->Segments[FIRST]->SegmentEndPoint == PARENT->start;
                    END V; END T;
                    """));
            var graph = result.analysis().semanticGraph();
            var view = graph.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast).findFirst().orElseThrow();
            var inspected = assertInstanceOf(ViewSymbol.Inspection.class, view.formation().orElseThrow());
            var geometry = assertInstanceOf(StructureSymbol.class, graph.symbol(inspected.element()).orElseThrow());
            assertEquals("LineGeometry", geometry.name().value());
            var segments = (AttributeSymbol)graph.symbol(geometry.members().getFirst()).orElseThrow();
            var list = assertInstanceOf(CollectionType.class, graph.type(segments.declaredType()).orElseThrow());
            assertEquals(CollectionKind.LIST, list.collectionKind());
            var element = assertInstanceOf(StructureValueType.class, graph.type(list.elementType()).orElseThrow());
            assertEquals("LineSegment", graph.symbol(element.target().orElseThrow()).orElseThrow().name().value());
        }
    }

    @Test void projectionsPreserveAssociationAndClassRolePaths() {
        for (String v : List.of("2.3", "2.4")) {
            String oid = v.equals("2.4") ? "OID AS INTERLIS.STANDARDOID;" : "";
            var result = success(v, "TOPIC T = CLASS A = " + oid + " x : TEXT; END A; CLASS B = " + oid + " y : TEXT; END B; " + """
                    ASSOCIATION AB = a -- {0..1} A; b -- {0..*} B; END AB;
                    VIEW V PROJECTION OF AB; = MANDATORY CONSTRAINT a->x == b->y; END V;
                    VIEW W PROJECTION OF B; = MANDATORY CONSTRAINT a->x == "x"; END W;
                    VIEW X PROJECTION OF A; = MANDATORY CONSTRAINT INTERLIS.objectCount(b) > 0; END X;
                    END T;
                    """);
            assertEquals(3, result.schema().semanticGraph().symbols().stream().filter(ViewSymbol.class::isInstance).count());
        }
    }

    @Test void derivedAssociationKeepsBasisAndRoleExpressions() {
        for (String v : List.of("2.3", "2.4")) {
            String oid = v.equals("2.4") ? "OID AS INTERLIS.STANDARDOID;" : "";
            var result = success(v, "TOPIC T = CLASS A = " + oid + " END A; CLASS B = " + oid + " END B; " + """
                    VIEW V JOIN OF A,B; = END V;
                    ASSOCIATION AB DERIVED FROM V = a -- A := V->A; b -- B := V->B; END AB;
                    END T;
                    """);
            var graph = result.schema().semanticGraph();
            var association = graph.symbols().stream().filter(AssociationSymbol.class::isInstance).map(AssociationSymbol.class::cast).findFirst().orElseThrow();
            assertInstanceOf(ViewSymbol.Base.class, graph.symbol(association.derivedFrom().orElseThrow()).orElseThrow());
            for (SymbolId id : association.roles()) {
                var role = (RoleSymbol)graph.symbol(id).orElseThrow();
                var path = assertInstanceOf(PathExpression.class, graph.expression(role.derivation().orElseThrow()).orElseThrow());
                assertEquals(2, path.path().steps().size());
                assertTrue(path.path().steps().stream().allMatch(PathStep.ViewBaseStep.class::isInstance));
            }
        }
    }

    @Test void importedBasesSurviveFileReorderingEditsAndAliasNavigation() {
        for (String version : List.of("2.3", "2.4")) for (boolean baseFirst : List.of(true, false)) {
            var engine = CompilerEngines.create(); AnalysisSnapshot previous = null;
            for (int revision = 1; revision <= 3; revision++) {
                String type = revision == 2 ? "TEXT" : "BOOLEAN";
                var base = new SourceText(new SourceId("memory:/" + (baseFirst ? "a" : "z") + "-base.ili"), new SourceRevision(revision),
                        "INTERLIS " + version + "; MODEL Base AT \"x\" VERSION \"1\" = TOPIC T = CLASS A = flag : " + type + "; END A; END T; END Base.");
                var use = CompilerTestSupport.source("memory:/" + (baseFirst ? "z" : "a") + "-use.ili",
                        "INTERLIS " + version + "; MODEL M AT \"x\" VERSION \"1\" = IMPORTS Base; TOPIC T = DEPENDS ON Base.T; VIEW V PROJECTION OF src~Base.T.A; WHERE src->flag; = ALL OF src; END V; END T; END M.");
                var request = CompilerTestSupport.request(List.of("M"), Map.of(base.id(), base, use.id(), use),
                        List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(use), "Base", base, CompilerTestSupport.parse(base))), CompilerOptions.defaults());
                var reused = engine.analyze(request, previous == null ? AnalysisReuse.none() : new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), CancellationToken.none());
                var fresh = engine.analyze(request, CancellationToken.none());
                assertEquals(fresh.diagnostics().stream().map(d -> List.of(d.code(), d.arguments(), d.primaryRange())).toList(),
                        reused.diagnostics().stream().map(d -> List.of(d.code(), d.arguments(), d.primaryRange())).toList());
                assertEquals(revision != 2, reused.diagnostics().isEmpty(), () -> reused.diagnostics().toString());
                var queries = guru.interlis.compiler.query.LanguageQueries.create();
                int offset = use.text().indexOf("src->flag");
                var ref = queries.symbolAt(reused, use.range(offset, offset), CancellationToken.none()).known().orElseThrow();
                var alias = assertInstanceOf(ViewSymbol.Base.class, reused.semanticGraph().symbol(ref.symbol()).orElseThrow());
                assertEquals("A", reused.semanticGraph().symbol(alias.target()).orElseThrow().name().value());
                if (revision != 2) {
                    var rename = queries.planRename(reused, new guru.interlis.compiler.query.RenameRequest(ref, "source", reused.sourceRevisions()), CancellationToken.none());
                    assertTrue(rename.complete(), rename.toString());
                    assertEquals(3, rename.edits().getFirst().edits().size());
                }
                previous = reused;
            }
        }
    }

}
