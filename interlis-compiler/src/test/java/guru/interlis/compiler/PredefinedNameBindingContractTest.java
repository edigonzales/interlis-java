package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** E1: one explicit source binding for qualified and imported predefined names. */
class PredefinedNameBindingContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static String model(String name,String body) {
        return "CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\"="+body+"END "+name+".";
    }
    private static CompilationRequest input(String version,String body) {
        return project(version,"",body);
    }
    private static CompilationRequest project(String version,String otherModels,String body) {
        var source=source("memory:/predefined-names.ili","INTERLIS "+version+";"+otherModels+model("F",body));
        var syntax=parse(source);var bindings=new ArrayList<ImportBinding>();
        for(var importer:syntax.root().models())for(var site:importer.imports()) {
            if(site.modelName().equals("INTERLIS"))continue;
            var target=syntax.root().models().stream().filter(m->m.name().equals(site.modelName())).findFirst().orElseThrow();
            bindings.add(new ImportBinding(new ImportSite(site.range(),importer.name(),site.modelName()),
                    new ImportResolution.Resolved(new ModelLocation(source.id(),source.revision(),target.name(),Optional.of(target.modelVersion()),target.range().startOffset()))));
        }
        return request(List.of("F"),Map.of(source.id(),source),bindings,CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(CompilationRequest input) {
        var result=CompilerEngines.create().compile(input,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
    }
    private static CompilationOutcome.Rejected bad(CompilationRequest input) {
        var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input,TOKEN));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
        assertTrue(result.analysis().diagnostics().stream().allMatch(d->d.code().value().startsWith("ILI-SEM-") || d.code().value().startsWith("ILI-SYN-")));
        return result;
    }
    private record Family(String name,String body,String predefinedName) { }
    private static final List<Family> FAMILIES=List.of(
            new Family("oid","STRUCTURE S=value:%sUUIDOID;END S;","UUIDOID"),
            new Family("date","STRUCTURE S=value:%sXMLDate;END S;","XMLDate"),
            new Family("legacy-date","STRUCTURE S=value:%sINTERLIS_1_DATE;END S;","INTERLIS_1_DATE"),
            new Family("unit","DOMAIN Dist=0..100 [%sm];","m"),
            new Family("structure","STRUCTURE S=value:%sTimeOfDay;END S;","TimeOfDay"),
            new Family("structure-base","STRUCTURE S(ABSTRACT) EXTENDS %sLineSegment=END S;","LineSegment"),
            new Family("metadata-base","TOPIC T(ABSTRACT)=CLASS R(ABSTRACT) EXTENDS %sSCALSYSTEM=END R;END T;","SCALSYSTEM"),
            new Family("function","TOPIC T=CLASS C=MANDATORY CONSTRAINT %slen(\"abc\")>0;END C;END T;","len"));

    @Test void qualifiedNamesNeedNoImportAndBareNamesNeedExplicitUnqualifiedInterlis() {
        for(String version:List.of("2.3","2.4"))for(var family:FAMILIES) {
            for(String body:List.of(family.body().formatted("INTERLIS."),"IMPORTS UNQUALIFIED INTERLIS;"+family.body().formatted(""))) {
                var result=ok(input(version,body));var refs=result.analysis().queries().findQualifiedName(QualifiedName.of("INTERLIS",family.predefinedName()));
                assertEquals(1,refs.size(),family.name());
                var symbol=result.analysis().queries().symbol(refs.getFirst()).known().orElseThrow();
                assertEquals(new Origin.PredefinedOrigin(version.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,"INTERLIS."+family.predefinedName()),symbol.origin());
            }
            bad(input(version,family.body().formatted("")));
            bad(input(version,"IMPORTS INTERLIS;"+family.body().formatted("")));
        }
    }

    @Test void primitiveTypeKeywordsRemainAvailableWithoutAnImport() {
        for(String version:List.of("2.3","2.4"))
            ok(input(version,"STRUCTURE S=flag:BOOLEAN;url:URI;name:NAME;horizontal:HALIGNMENT;vertical:VALIGNMENT;END S;"));
        ok(input("2.4","STRUCTURE S=date:DATE;time:TIMEOFDAY;timestamp:DATETIME;END S;"));
    }

    @Test void nestedPredefinedTimeSystemClassesArePublicInheritanceTargets() {
        for (String version : List.of("2.3", "2.4")) {
            var result = ok(input(version, "TOPIC T = "
                    + "CLASS Zone EXTENDS INTERLIS.TIMESYSTEMS.TIMEOFDAYSYS = END Zone; "
                    + "CLASS Calendar EXTENDS INTERLIS.TIMESYSTEMS.CALENDAR = END Calendar; END T;"));
            var graph = result.schema().semanticGraph();
            for (String name : List.of("TIMEOFDAYSYS", "CALENDAR")) {
                var refs = result.analysis().queries().findQualifiedName(QualifiedName.of("INTERLIS", "TIMESYSTEMS", name));
                assertEquals(1, refs.size());
                var symbol = assertInstanceOf(ClassSymbol.class,
                        result.analysis().queries().symbol(refs.getFirst()).known().orElseThrow());
                assertFalse(symbol.header().has(Modifier.ABSTRACT));
                assertInstanceOf(TopicSymbol.class, graph.symbol(symbol.owner().orElseThrow()).orElseThrow());
                assertEquals(1, symbol.members().size());
                assertInstanceOf(ParameterSymbol.class, graph.symbol(symbol.members().getFirst()).orElseThrow());
            }
            bad(input(version, "TOPIC T = CLASS Zone EXTENDS INTERLIS.TIMESYSTEMS.Missing = END Zone; END T;"));
        }
    }

    @Test void localDomainsStructuresUnitsAndFunctionsKeepTheirDeclaredIdentity() {
        for(String version:List.of("2.3","2.4"))for(var c:List.of(
                List.of("DOMAIN UUIDOID=BOOLEAN;STRUCTURE S=value:UUIDOID;END S;","UUIDOID"),
                List.of("DOMAIN XMLDate=BOOLEAN;STRUCTURE S=value:XMLDate;END S;","XMLDate"),
                List.of("STRUCTURE TimeOfDay=flag:BOOLEAN;END TimeOfDay;STRUCTURE S=value:TimeOfDay;END S;","TimeOfDay"),
                List.of("UNIT m;DOMAIN Distance=0..10[m];","m"),
                List.of("FUNCTION len(v:BOOLEAN):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(#true);END C;END T;","len"))) {
            var result=ok(input(version,"IMPORTS UNQUALIFIED INTERLIS;"+c.getFirst()));var graph=result.schema().semanticGraph();
            var local=graph.symbols().stream().filter(s->s.name().value().equals(c.getLast()) && s.origin() instanceof Origin.DeclaredOrigin).findFirst().orElseThrow();
            assertTrue(graph.references().stream().flatMap(r->r.segments().stream()).anyMatch(r->r.target().filter(local.id()::equals).isPresent()),c.getLast());
            var source=result.analysis().syntaxSnapshots().values().iterator().next().source();
            var occurrence=graph.references().stream().filter(r->source.slice(r.range()).equals(c.getLast()) && r.segments().getLast().target().filter(local.id()::equals).isPresent()).findFirst().orElseThrow();
            assertEquals(local.id(),LanguageQueries.create().symbolAt(result.analysis(),source.range(occurrence.range().startOffset(),occurrence.range().startOffset()),TOKEN).known().orElseThrow().symbol());
        }
    }

    private static String competingModels() {
        return model("Other","FUNCTION len(v:BOOLEAN):BOOLEAN;")+model("More","FUNCTION len(v:BOOLEAN):BOOLEAN;");
    }
    @Test void twoUserImportsCannotBeReplacedByTheBuiltinWhenTheirResolutionIsAmbiguous() {
        for(String version:List.of("2.3","2.4")) {
            var rejected=bad(project(version,competingModels(),"IMPORTS UNQUALIFIED Other,UNQUALIFIED More,UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0;END C;END T;"));
            var diagnostic=rejected.analysis().diagnostics().stream().filter(d->d.code().value().equals("ILI-SEM-0002")).findFirst().orElseThrow();
            assertTrue(rejected.analysis().semanticGraph().types().stream().filter(ErrorType.class::isInstance).map(ErrorType.class::cast).anyMatch(t->t.diagnosticIds().contains(diagnostic.id())));
        }
    }
    @Test void explicitFunctionNamesDisambiguateCompetingImports() {
        for(String version:List.of("2.3","2.4")) {
            var result=ok(project(version,competingModels(),"IMPORTS UNQUALIFIED Other,UNQUALIFIED More,UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT Other.len(#true) AND INTERLIS.len(\"abc\")>0;END C;END T;"));
            var graph=result.schema().semanticGraph();var functions=graph.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast)
                    .map(f->graph.symbol(f.targetFunction().orElseThrow()).orElseThrow()).toList();
            assertEquals(2,functions.size());assertEquals(1,functions.stream().filter(f->f.origin() instanceof Origin.PredefinedOrigin).count());
            assertEquals(1,functions.stream().filter(f->f.origin() instanceof Origin.DeclaredOrigin).count());
        }
    }

    @Test void sourceQueriesKeepTheRangesOfTheActualBareNames() {
        for(String version:List.of("2.3","2.4")) {
            var result=ok(input(version,"IMPORTS UNQUALIFIED INTERLIS;DOMAIN Distance=0..10[m];STRUCTURE S=id:UUIDOID;date:XMLDate;clock:TimeOfDay;END S;FUNCTION isId(v:UUIDOID):BOOLEAN;"));
            var source=result.analysis().syntaxSnapshots().values().iterator().next().source();var found=new HashSet<String>();
            for(var reference:result.schema().semanticGraph().references()) {
                String text=source.slice(reference.range());
                if(!Set.of("m","UUIDOID","XMLDate","TimeOfDay").contains(text))continue;
                var target=result.schema().semanticGraph().symbol(reference.segments().getLast().target().orElseThrow()).orElseThrow();
                assertEquals(new Origin.PredefinedOrigin(version.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,"INTERLIS."+text),target.origin());
                assertEquals(source.id(),reference.range().source());assertEquals(source.revision(),reference.range().revision());
                var cursor=source.range(reference.range().startOffset(),reference.range().startOffset());
                assertEquals(target.id(),LanguageQueries.create().symbolAt(result.analysis(),cursor,TOKEN).known().orElseThrow().symbol());
                assertEquals(target.origin(),LanguageQueries.create().definition(result.analysis(),cursor,TOKEN).known().orElseThrow().origin());found.add(text);
            }
            assertEquals(Set.of("m","UUIDOID","XMLDate","TimeOfDay"),found);
        }
    }

    @Test void unknownQualifiedTargetsFailAndBareLenBindsTheActualCallTarget() {
        for(String version:List.of("2.3","2.4")) {
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,
                    CompilerEngines.create().compile(input(version,"TOPIC T=CLASS C=value:INTERLIS.NoSuchDomain;END C;END T;"),TOKEN));
            assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0001")));
            assertEquals(0,rejected.analysis().queries().findQualifiedName(QualifiedName.of("INTERLIS","NoSuchDomain")).size());

            var result=ok(input(version,"IMPORTS UNQUALIFIED INTERLIS;TOPIC T=CLASS C=MANDATORY CONSTRAINT len(\"abc\")>0;END C;END T;"));
            var graph=result.schema().semanticGraph();
            var call=graph.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
            var target=graph.symbol(call.targetFunction().orElseThrow()).orElseThrow();
            assertEquals(new Origin.PredefinedOrigin(version.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,"INTERLIS.len"),target.origin());
            var source=result.analysis().syntaxSnapshots().values().iterator().next().source();
            var range=call.range();
            assertEquals("len",source.slice(source.range(range.startOffset(),range.startOffset()+3)));
            var cursor=source.range(range.startOffset(),range.startOffset());
            assertEquals(target.id(),LanguageQueries.create().symbolAt(result.analysis(),cursor,TOKEN).known().orElseThrow().symbol());
            assertEquals(target.id(),LanguageQueries.create().definition(result.analysis(),cursor,TOKEN).known().orElseThrow().symbol().symbol());
        }
    }
    @Test void importPolicyChangesAndReversedSourceOrderMatchFreshAnalysis() {
        for(String version:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili","INTERLIS "+version+";"+model("Base","IMPORTS UNQUALIFIED INTERLIS;FUNCTION f(v:UUIDOID):BOOLEAN;"));
            var id=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");var engine=CompilerEngines.create();AnalysisSnapshot previous=null;
            for(int step=1;step<=3;step++) {
                boolean valid=step!=2;
                var use=new SourceText(id,new SourceRevision(step),"INTERLIS "+version+";"+model("F","IMPORTS Base;IMPORTS "+(valid?"UNQUALIFIED ":"")+"INTERLIS;TOPIC T=CLASS C=value:UUIDOID;MANDATORY CONSTRAINT Base.f(value);END C;END T;"));
                var input=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=CompilerEngines.create().analyze(input,TOKEN);var reused=engine.analyze(input,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(valid,reused.diagnostics().isEmpty(),reused.diagnostics().toString());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(),reused.semanticGraph()));
                for(var reference:reused.semanticGraph().references())if(reference.range().source().equals(use.id()))assertEquals(use.revision(),reference.range().revision());
                int offset=use.text().indexOf("value:UUIDOID")+"value:".length();
                var at=LanguageQueries.create().symbolAt(reused,use.range(offset,offset),TOKEN);
                if(valid) {
                    var ids=reused.queries().findQualifiedName(QualifiedName.of("INTERLIS","UUIDOID"));assertEquals(1,ids.size());
                    assertEquals(ids.getFirst().symbol(),at.known().orElseThrow().symbol());
                } else assertTrue(at.known().isEmpty());
                previous=reused;
            }
        }
    }
}
