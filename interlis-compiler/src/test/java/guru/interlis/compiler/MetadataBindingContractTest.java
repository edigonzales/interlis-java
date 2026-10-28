package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** K4: bound metadata, axes, class parameters and translation identities. */
class MetadataBindingContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String BASE = "TOPIC Systems = CLASS CS EXTENDS INTERLIS.COORDSYSTEM = END CS; CLASS SS EXTENDS INTERLIS.SCALSYSTEM = END SS; END Systems; ";
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; MODEL M AT \"https://example.org\" VERSION \"1\" = " + body + " END M.";
    }
    private static CompilationRequest request(String text) {
        var source = CompilerTestSupport.source("memory:/metadata.ili", text);
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
    private static Symbol named(SemanticGraph g, String name) { return g.symbols().stream().filter(s -> s.name().value().equals(name)).findFirst().orElseThrow(); }

    @Test void basketsKeepTopicsBasesOrderedObjectsAndClassBindings() {
        for (String v : List.of("2.3", "2.4")) {
            var result = ok(model(v, BASE + "REFSYSTEM BASKET Base ~ Systems; REFSYSTEM BASKET Local (FINAL) EXTENDS Base ~ Systems OBJECTS OF CS: A,B; OBJECTS OF SS: C;"));
            var graph = result.schema().semanticGraph(); var basket = (MetadataBasketSymbol)named(graph, "Local");
            assertEquals(named(graph, "Systems").id(), basket.topic().orElseThrow());
            assertEquals(named(graph, "Base").id(), basket.baseBasket().orElseThrow());
            assertEquals(List.of("A","B","C"), basket.objects().stream().map(id -> graph.symbol(id).orElseThrow().name().value()).toList());
            assertEquals(named(graph, "CS").id(), ((MetaObjectSymbol)named(graph, "B")).objectClass().orElseThrow());
            assertTrue(basket.header().has(Modifier.FINAL));
            var source = result.analysis().syntaxSnapshots().values().iterator().next().source();
            int offset = source.text().indexOf("CS: A");
            assertEquals(named(graph, "CS").id(), LanguageQueries.create().symbolAt(result.analysis(), source.range(offset,offset), TOKEN).known().orElseThrow().symbol());
        }
    }

    @Test void axesKeepUnitsMetadataIdentityOneBasedAxisAndRotation() {
        for (String v : List.of("2.3", "2.4")) {
            var graph = ok(model(v, BASE + "REFSYSTEM BASKET B ~ Systems OBJECTS OF CS: CH; DOMAIN Coord = COORD 0.000..100.000 [INTERLIS.m] {B.CH[1]}, 0.000..100.000 [INTERLIS.m] {B.CH[2]}, ROTATION 2 -> 1; Height = 0.000..10.000 [INTERLIS.m] <Coord[2]>;")).schema().semanticGraph();
            var coord = (CoordinateType)graph.type(((DomainSymbol)named(graph, "Coord")).typeId()).orElseThrow();
            assertEquals(new CoordinateType.Rotation(2,1), coord.rotation().orElseThrow());
            for (int i=0; i<2; i++) {
                var axis = coord.axes().get(i); assertEquals(named(graph,"INTERLIS.m").id(), axis.unit().orElseThrow());
                var ref = axis.referenceSystem().orElseThrow(); assertFalse(ref.domain()); assertEquals(i+1,ref.axis().orElseThrow());
                assertEquals(named(graph,"CH").id(),ref.target());
            }
            var height = (NumericType)graph.type(((DomainSymbol)named(graph,"Height")).typeId()).orElseThrow();
            assertTrue(height.range().referenceSystem().orElseThrow().domain());
            assertEquals(named(graph,"Coord").id(),height.range().referenceSystem().orElseThrow().target());
        }
    }

    @Test void inheritedObjectsAndTopicsResolveWithoutCopyingIdentities() {
        for (String v : List.of("2.3", "2.4")) {
            var graph = ok(model(v, BASE + "TOPIC More EXTENDS Systems = END More; REFSYSTEM BASKET B ~ Systems OBJECTS OF SS: S; REFSYSTEM BASKET C EXTENDS B ~ More; DOMAIN N = 0..100 {C.S};")).schema().semanticGraph();
            var numeric = (NumericType)graph.type(((DomainSymbol)named(graph,"N")).typeId()).orElseThrow();
            assertEquals(named(graph,"S").id(), numeric.range().referenceSystem().orElseThrow().target());
            assertTrue(((MetadataBasketSymbol)named(graph,"C")).objects().isEmpty());
        }
    }

    @Test void predefinedMetadataHasNameConstraintAndTypedUnitAndAxisMembers() {
        for (String v : List.of("2.3", "2.4")) {
            var graph = ok(model(v, BASE + "TOPIC Other = CLASS Narrow EXTENDS INTERLIS.COORDSYSTEM = Axis (EXTENDED): LIST {2} OF INTERLIS.AXIS; END Narrow; END Other;")).schema().semanticGraph();
            var root = (ClassSymbol)named(graph,"INTERLIS.METAOBJECT"); assertEquals(1,root.constraints().size());
            var constraint = (ConstraintSymbol)graph.symbol(root.constraints().getFirst()).orElseThrow();
            var unique = assertInstanceOf(UniquenessConstraint.class,constraint.definition());
            var expr = assertInstanceOf(PathExpression.class,graph.expression(unique.keys().getFirst()).orElseThrow());
            assertEquals(root.members().getFirst(), ((PathStep.AttributeStep)expr.path().steps().getFirst()).target());
            assertEquals(List.of("Name", "Axis"),graph.effectiveMembers(named(graph,"CS").id()).stream().map(e->graph.symbol(e.declaredSymbol()).orElseThrow().name().value()).toList());
        }
    }

    @Test void unitParameterOverridesAreTypedAndEnforced() {
        for (String v : List.of("2.3", "2.4")) {
            String base = "TOPIC Systems = CLASS SS EXTENDS INTERLIS.SCALSYSTEM = PARAMETER Unit (EXTENDED): NUMERIC [INTERLIS.LENGTH]; END SS; END Systems; REFSYSTEM BASKET B ~ Systems OBJECTS OF SS: S; DOMAIN N = 0..100 [INTERLIS.m] {B.S};";
            var graph = ok(model(v,base)).schema().semanticGraph(); var cls = (ClassSymbol)named(graph,"SS");
            var p = (ParameterSymbol)graph.symbol(cls.members().getFirst()).orElseThrow(); assertTrue(p.overrides().isPresent());
            assertEquals(named(graph,"INTERLIS.LENGTH").id(), ((NumericType)graph.type(p.typeId()).orElseThrow()).range().unit().orElseThrow());
            bad(model(v,base.replace("[INTERLIS.m]", "[INTERLIS.s]")), "REFSYSTEM_UNIT");
            bad(model(v,base.replace("[INTERLIS.m]", "")), "REFSYSTEM_UNIT_REQUIRED");
            bad(model(v,base.replace("Unit (EXTENDED)","Other")), "PARAMETER_UNIT");
            bad(model(v,base.replace("Unit (EXTENDED)","Unit")), "PARAMETER_OVERRIDE");
        }
    }

    @Test void wrongBasketAndAxisReferencesAreLanguageErrors() {
        for (String v : List.of("2.3", "2.4")) {
            String base = BASE + "REFSYSTEM BASKET B ~ Systems OBJECTS OF CS: CH; DOMAIN Coord = COORD 0..100 {B.CH[1]}, 0..100 {B.CH[2]};";
            bad(model(v,base.replace("CH[2]","CH[4]")),"REFSYSTEM_AXIS");
            bad(model(v,base.replace("B.CH[2]","Missing.CH[2]")),"METAOBJECT_REFERENCE");
            bad(model(v,BASE + "REFSYSTEM BASKET B ~ Systems OBJECTS OF CS: CH,CH;"),"METAOBJECT_NAME");
            bad(model(v,BASE + "REFSYSTEM BASKET B (FINAL) ~ Systems; REFSYSTEM BASKET C EXTENDS B ~ Systems;"),"METADATA_BASKET_FINAL");
            bad(model(v,BASE + "REFSYSTEM BASKET B EXTENDS C ~ Systems; REFSYSTEM BASKET C EXTENDS B ~ Systems;"),"METADATA_BASKET_CYCLE");
            bad(model(v,base + "DOMAIN Wrong = 0..10 <Coord>;"),"REFSYSTEM_DOMAIN");
            bad(model(v,base.replace("CH[1]","CH[0]")),"REFSYSTEM_AXIS");
        }
    }

    @Test void translationsCompareBoundTargetsAndExternalObjectNames() {
        for (String v : List.of("2.3", "2.4")) {
            String body = BASE + "REFSYSTEM BASKET B ~ Systems OBJECTS OF CS: CH; DOMAIN Coord = COORD 0..100 {B.CH[1]}, 0..100 {B.CH[2]};";
            String original = model(v,body).replace("MODEL M", "MODEL Original").replace("END M.","END Original.");
            String translated = model(v,body.replace("Systems","Systemes").replace("CS", "Systeme").replace("B ~","Panier ~").replace("B.CH", "Panier.CH"))
                    .replace("INTERLIS " + v + "; ", "").replace("VERSION \"1\" =","VERSION \"1\" TRANSLATION OF Original [\"1\"] =");
            ok(original+translated);
            bad(original+translated.replace("CH", "Autre"),"TRANSLATION_METAOBJECT");
            bad(original+translated.replace("CH[2]", "CH[1]"),"TRANSLATION_REFERENCE");
        }
    }
    @Test void importedBasketsAndAxesAreOrderIndependentAndReuseObservesChangedBindings() {
        for (String v : List.of("2.3", "2.4")) for (boolean reverse : List.of(false, true)) {
            var base = CompilerTestSupport.source("memory:/" + (reverse ? "z" : "a") + ".ili", model(v, BASE + "REFSYSTEM BASKET B ~ Systems OBJECTS OF CS: CH;").replace("MODEL M", "MODEL Base").replace("END M.", "END Base."));
            var use = CompilerTestSupport.source("memory:/" + (reverse ? "a" : "z") + ".ili", model(v, "IMPORTS Base; DOMAIN Coord = COORD 0..100 {Base.B.CH[1]}, 0..100 {Base.B.CH[2]};"));
            var b = CompilerTestSupport.parse(base); var u = CompilerTestSupport.parse(use);
            var request = CompilerTestSupport.request(List.of("M"), Map.of(base.id(),base,use.id(),use), List.of(CompilerTestSupport.binding(u,"Base",base,b)), CompilerOptions.defaults());
            var engine = CompilerEngines.create(); var result = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(request,TOKEN));
            var graph=result.schema().semanticGraph(); var coordinate=(CoordinateType)graph.type(((DomainSymbol)named(graph,"Coord")).typeId()).orElseThrow();
            assertEquals(named(graph,"CH").id(),coordinate.axes().getFirst().referenceSystem().orElseThrow().target());
            var edited = new SourceText(base.id(),new SourceRevision(2),base.text().replace("OBJECTS OF CS", "OBJECTS OF SS"));
            var changed = CompilerTestSupport.request(List.of("M"),Map.of(edited.id(),edited,use.id(),use),List.of(CompilerTestSupport.binding(u,"Base",edited,CompilerTestSupport.parse(edited))),CompilerOptions.defaults());
            var fresh=engine.analyze(changed,TOKEN); var reused=engine.analyze(changed,new AnalysisReuse(Optional.of(result.analysis()),result.analysis().syntaxSnapshots()),TOKEN);
            assertEquals(fresh.diagnostics(),reused.diagnostics()); assertTrue(fresh.diagnostics().toString().contains("REFSYSTEM_OBJECT"));
            bad(model(v,"DOMAIN Coord = COORD 0..100 {Missing.B.CH[1]},0..100 {Missing.B.CH[2]};"),"METAOBJECT_REFERENCE");
        }
    }
    @Test void angularReferenceUsesCoordinateRotationInsteadOfAnAxis() {
        for (String v : List.of("2.3", "2.4")) {
            String body = "DOMAIN C = COORD 0.00..100.00 [INTERLIS.m],0.00..100.00 [INTERLIS.m], ROTATION 2 -> 1; Azimuth = 0.0000..399.9999 CIRCULAR <C>;";
            var graph = ok(model(v, body)).schema().semanticGraph();
            var range = ((NumericType)graph.type(((DomainSymbol)named(graph,"Azimuth")).typeId()).orElseThrow()).range();
            assertEquals(named(graph,"C").id(),range.referenceSystem().orElseThrow().target()); assertTrue(range.referenceSystem().orElseThrow().axis().isEmpty());
            ok(model(v, body.replace("CIRCULAR <C>", "CIRCULAR [INTERLIS.rad] <C>")));
            bad(model(v, body.replace("CIRCULAR <C>", "CIRCULAR [INTERLIS.m] <C>")), "REFSYSTEM_DIRECTION_UNIT");
        }
    }
}
