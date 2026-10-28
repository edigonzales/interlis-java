package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComposedUnitContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String BASE = "UNIT Length (ABSTRACT); Time (ABSTRACT); Meter [m] EXTENDS Length; Second [s] EXTENDS Time; Speed (ABSTRACT) = (Length / Time); Kilometre [km] = 1000 [m];";
    private static String wrap(String version, String name, String body) {
        return "INTERLIS " + version + "; MODEL " + name + " AT \"https://example.org\" VERSION \"1\" = " + body + " END " + name + ".";
    }
    private static CompilationRequest request(String version, String base, String use, boolean baseFirst) {
        var a = CompilerTestSupport.source("memory:/" + (baseFirst ? "a" : "z") + "-base.ili", wrap(version,"Base",base));
        var b = CompilerTestSupport.source("memory:/" + (baseFirst ? "z" : "a") + "-use.ili", wrap(version,"F","IMPORTS Base; " + use));
        return CompilerTestSupport.request(List.of("F"), Map.of(a.id(),a,b.id(),b),
                List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(b), "Base", a, CompilerTestSupport.parse(a))), CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String version, String base, String use, boolean order) {
        var result = CompilerEngines.create().compile(request(version,base,use,order), TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result, () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
    }
    private static void bad(String version, String base, String use, boolean order, String reason) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,base,use,order),TOKEN));
        assertTrue(result.analysis().diagnostics().toString().contains(reason), () -> result.analysis().diagnostics().toString());
    }
    private static UnitSymbol unit(SemanticGraph g,String name) {
        return g.symbols().stream().filter(UnitSymbol.class::isInstance).map(UnitSymbol.class::cast).filter(u->u.name().value().equals(name)).findFirst().orElseThrow();
    }
    @Test void importedComponentsAreBoundInOrderAndUsedByNumericTypes() {
        for (String v:List.of("2.3","2.4")) for (boolean order:List.of(true,false)) {
            var graph=ok(v,BASE,"UNIT Velocity [v] EXTENDS Base.Speed = (Base.km / Base.s); DOMAIN Rate = 0..100 [v];",order).schema().semanticGraph();
            var speed=unit(graph,"v"); assertEquals("Velocity",speed.designation());
            assertEquals(unit(graph,"Speed").id(),speed.base().orElseThrow());
            assertEquals(List.of(new UnitSymbol.Component(UnitSymbol.Operator.MULTIPLY,unit(graph,"km").id()),
                    new UnitSymbol.Component(UnitSymbol.Operator.DIVIDE,unit(graph,"s").id())),speed.components());
            assertEquals(unit(graph,"m").id(),unit(graph,"km").derivedFrom().orElseThrow());
            assertThrows(UnsupportedOperationException.class,()->speed.components().clear());
            assertThrows(IllegalArgumentException.class,()->new UnitSymbol(speed.header(),speed.designation(),false,speed.base(),Optional.empty(),
                    List.of(new UnitSymbol.Component(UnitSymbol.Operator.DIVIDE,unit(graph,"m").id())),List.of(),Optional.empty(),speed.definition()));
            assertTrue(graph.types().stream().filter(NumericType.class::isInstance).map(NumericType.class::cast).anyMatch(n->n.range().unit().equals(Optional.of(speed.id()))));
        }
    }
    @Test void abstractAndConcreteComponentsCannotBeMixed() {
        for(String v:List.of("2.3","2.4")) for(boolean order:List.of(true,false)) {
            ok(v,BASE,"UNIT AbstractSpeed (ABSTRACT) = (Base.Length / Base.Time); Concrete = (Base.m / Base.s);",order);
            for(String use:List.of("UNIT Wrong = (Base.Length);","UNIT Wrong (ABSTRACT) = (Base.m);","UNIT Wrong = (Base.m / Base.Time);"))
                bad(v,BASE,use,order,"UNIT_COMPONENT_ABSTRACT");
        }
    }
    @Test void extensionsPreserveCompositionAndSpecializeEachComponent() {
        for(String v:List.of("2.3","2.4")) for(boolean order:List.of(true,false)) {
            ok(v,BASE,"UNIT L (ABSTRACT) EXTENDS Base.Length; V (ABSTRACT) EXTENDS Base.Speed = (L / Base.Time); Distance EXTENDS L; Speed EXTENDS V = (Distance / Base.s);",order);
            for(String use:List.of("UNIT V EXTENDS Base.Speed = (Base.m * Base.s);","UNIT V EXTENDS Base.Speed = (Base.s / Base.m);","UNIT V EXTENDS Base.Speed = (Base.m);","UNIT V EXTENDS Base.Length = (Base.m);"))
                bad(v,BASE,use,order,"UNIT_COMPOSITION_EXTENSION");
            ok(v,BASE,"UNIT V EXTENDS Base.Speed;",order);
            bad(v,BASE,"UNIT V EXTENDS Base.m;",order,"UNIT_BASE_ABSTRACT");
        }
    }
    @Test void derivedComponentsRequireConcreteTargetsAndRespectTheirAbstractAncestors() {
        for(String v:List.of("2.3","2.4")) for(boolean order:List.of(true,false)) {
            ok(v,BASE,"UNIT Distance = 2 [Base.km]; Velocity EXTENDS Base.Speed = (Distance / Base.s);",order);
            bad(v,BASE,"UNIT D = 2 [Base.Length];",order,"UNIT_DERIVED_ABSTRACT");
            bad(v,BASE,"UNIT D (ABSTRACT) = 2 [Base.m];",order,"UNIT_DERIVED_ABSTRACT");
        }
    }
    @Test void wrongQualificationsAndDocumentaryNamesDoNotBind() {
        for(String v:List.of("2.3","2.4")) for(boolean order:List.of(true,false)) {
            for(String name:List.of("Wrong.m","Base.Meter","m","INTERLIS.METER","INTERLIS.unknown"))
                bad(v,BASE,"UNIT V = ("+name+");",order,"unresolved");
            bad(v,BASE,"DOMAIN Number = 0..10 [Wrong.m];",order,"unresolved");
            bad(v,BASE,"UNIT Metre [x]; Second [x];",order,"UNIT_NAME_UNIQUE");
            bad(v,BASE,"DOMAIN Flag = BOOLEAN; UNIT V = (Flag);",order,"unresolved");
        }
    }
    @Test void builtinUnitsAndTheirBasesHaveRealIdentities() {
        for(String v:List.of("2.3","2.4")) {
            var graph=ok(v,BASE,"UNIT Speed (ABSTRACT) = (INTERLIS.LENGTH / INTERLIS.TIME); V EXTENDS Speed = (INTERLIS.m / INTERLIS.s);",false).schema().semanticGraph();
            assertEquals(unit(graph,"INTERLIS.LENGTH").id(),unit(graph,"INTERLIS.m").base().orElseThrow());
            assertTrue(unit(graph,"INTERLIS.m").header().origin() instanceof Origin.PredefinedOrigin);
            assertEquals("METER",unit(graph,"INTERLIS.m").designation());
        }
    }
    @Test void dependencyCyclesAreLanguageErrorsAndPowerIsNotComposition() {
        for(String v:List.of("2.3","2.4")) {
            for(String use:List.of("UNIT A = (B); B = (A);","UNIT A (ABSTRACT) EXTENDS B; B (ABSTRACT) EXTENDS A;","UNIT A = 2 [B]; B = 2 [A];"))
                bad(v,BASE,use,false,"UNIT_CYCLE");
            bad(v,BASE,"UNIT V = (Base.m ^ Base.s);",false,"ILI-SYN");
        }
    }
    @Test void importedComponentChangesMatchFreshAnalysis() {
        var engine=CompilerEngines.create();
        var original=request("2.4",BASE,"UNIT V = (Base.m / Base.s);",false);
        var first=engine.analyze(original,TOKEN); var previous=first;
        long revision=1;
        for (String replacement:List.of("Meter [m] EXTENDS Length;","m (ABSTRACT) EXTENDS Length;","Meter [m] EXTENDS Length;")) {
            var next=request("2.4",BASE.replace("Meter [m] EXTENDS Length;",replacement),"UNIT V = (Base.m / Base.s);",false);
            var sources=new LinkedHashMap<SourceId,SourceText>();
            for(var source:next.sources().sources().values()) sources.put(source.id(),new SourceText(source.id(),new SourceRevision(++revision),source.text()));
            var a=sources.values().stream().filter(x->x.text().contains("MODEL Base ")).findFirst().orElseThrow();
            var b=sources.values().stream().filter(x->x.text().contains("MODEL F ")).findFirst().orElseThrow();
            next=CompilerTestSupport.request(List.of("F"),sources,List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(b),"Base",a,CompilerTestSupport.parse(a))),CompilerOptions.defaults());
            var reused=engine.analyze(next,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
            var fresh=engine.analyze(next,TOKEN);
            assertEquals(fresh.diagnostics().stream().map(d->List.of(d.code(),d.arguments(),d.primaryRange())).toList(),
                    reused.diagnostics().stream().map(d->List.of(d.code(),d.arguments(),d.primaryRange())).toList());
            assertEquals(replacement.startsWith("m ("),reused.diagnostics().stream().anyMatch(d->d.arguments().toString().contains("UNIT_COMPONENT_ABSTRACT")));
            previous=reused;
        }
        assertFalse(unit(first.semanticGraph(),"m").abstractUnit());
    }
    @Test void compositionTranslationsUseBoundComponentsInsteadOfSpellingShape() {
        for(String v:List.of("2.3","2.4")) {
            String base=wrap(v,"Base","UNIT Length (ABSTRACT); Time (ABSTRACT); Speed (ABSTRACT) = (Length / Time);");
            String translated=wrap(v,"F","UNIT Laenge9 (ABSTRACT); Zeit77 (ABSTRACT); Tempo (ABSTRACT) = (Laenge9 / Zeit77);")
                    .replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Base [\"1\"] =");
            var source=CompilerTestSupport.source("memory:/units.ili",base + " " + translated.substring(("INTERLIS " + v + "; ").length()));
            var result=CompilerEngines.create().compile(CompilerTestSupport.request(List.of("Base","F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),TOKEN);
            assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
        }
    }
    @Test void componentNavigationUsesTheShortNameAndItsExactSourceRange() {
        var result=ok("2.4",BASE,"UNIT Velocity [v] = (Base.km / Base.s);",false);
        var source=result.analysis().syntaxSnapshots().values().stream().map(s->s.source()).filter(s->s.text().contains("MODEL F ")).findFirst().orElseThrow();
        int offset=source.text().indexOf("Base.km")+5;
        var ref=LanguageQueries.create().symbolAt(result.analysis(),source.range(offset,offset),TOKEN).known().orElseThrow();
        assertEquals(unit(result.schema().semanticGraph(),"km").id(),ref.symbol());
        var target=unit(result.schema().semanticGraph(),"km").header().nameRange().orElseThrow();
        var baseSource=result.analysis().syntaxSnapshots().get(target.source()).source();
        assertEquals("km",baseSource.text().substring(target.startOffset(),target.endOffset()));
    }
}
