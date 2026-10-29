package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** E1: predefined OID identity, versioned declarations, source navigation and reuse. */
class PredefinedOidIdentityContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version,String name,String body) {
        return "INTERLIS "+version+";CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\"="+body+"END "+name+".";
    }
    private static CompilationRequest input(String version,String body) {
        var source=source("memory:/predefined-oid.ili",model(version,"F",body));
        return request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(CompilationRequest input) {
        var result=CompilerEngines.create().compile(input,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,
                ()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
    }
    private static DomainSymbol predefined(AnalysisSnapshot analysis,String name,LanguageVersion version) {
        var refs=analysis.queries().findQualifiedName(new QualifiedName(List.of("INTERLIS",name)));
        return refs.stream().map(ref->analysis.queries().symbol(ref).known().orElseThrow())
                .filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast)
                .filter(d->d.languageVersion()==version).findFirst().orElseThrow();
    }
    private static AttributeSymbol attribute(SemanticGraph graph,String qualifiedName) {
        return (AttributeSymbol)graph.symbol(graph.qualifiedNames().get(QualifiedName.of(qualifiedName.split("\\."))).getFirst()).orElseThrow();
    }
    private static SymbolId aliasTarget(SemanticGraph graph,TypeId alias) {
        return assertInstanceOf(DomainReferenceType.class,graph.type(alias).orElseThrow()).targetDomain();
    }
    private static void checkVersionedDeclaration(AnalysisSnapshot analysis,String shortName,LanguageVersion version) {
        var graph=analysis.semanticGraph();var domain=predefined(analysis,shortName,version);
        var origin=new Origin.PredefinedOrigin(version,"INTERLIS."+shortName);
        assertEquals(origin,domain.origin());assertEquals(version,domain.languageVersion());
        var owner=assertInstanceOf(ModelSymbol.class,graph.symbol(domain.owner().orElseThrow()).orElseThrow());
        assertEquals(new Origin.PredefinedOrigin(version,"INTERLIS"),owner.origin());assertTrue(owner.members().contains(domain.id()));
        assertTrue(domain.nameRange().isEmpty());
        assertFalse(domain.header().has(Modifier.FINAL));
        assertEquals(version==LanguageVersion.ILI24 && shortName.equals("ANYOID"),domain.header().has(Modifier.ABSTRACT));
        String base=version==LanguageVersion.ILI23 || shortName.equals("NOOID")?null:shortName.equals("ANYOID")?"NOOID":"ANYOID";
        assertEquals(base==null?Optional.empty():Optional.of(predefined(analysis,base,version).id()),domain.baseDomain());
        var type=assertInstanceOf(OidType.class,graph.type(domain.typeId()).orElseThrow());assertEquals(origin,type.origin());
        var kind=switch(shortName) {case "I32OID"->OidKind.NUMERIC;case "UUIDOID"->OidKind.UUID;case "STANDARDOID"->OidKind.TEXT;default->OidKind.ANY;};
        assertEquals(kind,type.oidKind());
        if(kind==OidKind.ANY)assertTrue(type.valueType().isEmpty());
        else {
            var carrier=graph.type(type.valueType().orElseThrow()).orElseThrow();assertEquals(origin,carrier.origin());
            if(kind==OidKind.NUMERIC) {
                var range=assertInstanceOf(NumericType.class,carrier).range();
                assertEquals(0,BigDecimal.ZERO.compareTo(range.minimum().orElseThrow().exactValue()));
                assertEquals(0,new BigDecimal("2147483647").compareTo(range.maximum().orElseThrow().exactValue()));
            } else assertEquals(shortName.equals("UUIDOID")?36:16,assertInstanceOf(TextType.class,carrier).maxLength().orElseThrow());
        }
    }

    @Test void predefinedOidDomainsExposeCanonicalQueriesOriginsAndVersionedInheritance() {
        for(String version:List.of("2.3","2.4")) {
            var language=version.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24;
            String body="STRUCTURE Types=anyId:INTERLIS.ANYOID;integerId:INTERLIS.I32OID;standardId:INTERLIS.STANDARDOID;uuidId:INTERLIS.UUIDOID;"
                    +(version.equals("2.4")?"unstableId:INTERLIS.NOOID;":"")+"END Types;";
            var result=ok(input(version,body));
            var names=version.equals("2.3")?List.of("ANYOID","I32OID","STANDARDOID","UUIDOID"):List.of("NOOID","ANYOID","I32OID","STANDARDOID","UUIDOID");
            for(String name:names) {
                var refs=result.analysis().queries().findQualifiedName(new QualifiedName(List.of("INTERLIS",name)));
                assertEquals(1,refs.size(),name);checkVersionedDeclaration(result.analysis(),name,language);
            }
            if(version.equals("2.3"))assertTrue(result.analysis().queries().findQualifiedName(new QualifiedName(List.of("INTERLIS","NOOID"))).isEmpty());
        }
    }

    @Test void oidReferencesFunctionTypesCollectionsAndOidRulesRetainEachSourceReference() {
        for(String version:List.of("2.3","2.4")) {
            String body="DOMAIN LocalId=OID TEXT*16;Concrete EXTENDS INTERLIS.ANYOID=OID TEXT*16;"
                    +"FUNCTION identity(value:INTERLIS.UUIDOID):INTERLIS.UUIDOID;"
                    +"STRUCTURE Item=identifier:INTERLIS.UUIDOID;localIdentifier:LocalId;END Item;"
                    +"TOPIC T=OID AS INTERLIS.UUIDOID;CLASS C=items:BAG OF Item;END C;END T;";
            var result=ok(input(version,body));var analysis=result.analysis();
            var source=analysis.syntaxSnapshots().values().iterator().next().source();
            var language=version.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24;
            var uuid=predefined(analysis,"UUIDOID",language);
            for(String shortName:List.of("UUIDOID","ANYOID")) {
                String spelling="INTERLIS."+shortName;int start=-1;int count=0;
                while((start=source.text().indexOf(spelling,start+1))>=0) {
                    int cursor=start+"INTERLIS.".length();var range=source.range(cursor,cursor);
                    var expected=predefined(analysis,shortName,language);
                    var symbol=LanguageQueries.create().symbolAt(analysis,range,TOKEN).known().orElseThrow();
                    assertEquals(expected.id(),symbol.symbol(),source.text().substring(start));
                    var definition=LanguageQueries.create().definition(analysis,range,TOKEN).known().orElseThrow();
                    assertEquals(new Origin.PredefinedOrigin(language,spelling),definition.origin());count++;
                }
                assertTrue(count>0);
            }
            var itemIdentifier=attribute(analysis.semanticGraph(),"F.Item.identifier");
            assertEquals(uuid.id(),aliasTarget(analysis.semanticGraph(),itemIdentifier.declaredType()));
            var resolved=new TypeRelations(analysis.semanticGraph()).resolveAliases(new TypeRef(analysis.snapshotId(),itemIdentifier.declaredType())).known().orElseThrow();
            assertEquals(List.of(uuid.id()),resolved.aliasPath());assertEquals(uuid.typeId(),resolved.effectiveType());
            var items=attribute(analysis.semanticGraph(),"F.T.C.items");
            var collection=assertInstanceOf(CollectionType.class,analysis.semanticGraph().type(items.declaredType()).orElseThrow());
            var element=assertInstanceOf(StructureValueType.class,analysis.semanticGraph().type(collection.elementType()).orElseThrow());
            assertEquals(itemIdentifier.owner(),element.target());
            var function=analysis.semanticGraph().symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast)
                    .filter(f->f.name().value().equals("identity")).findFirst().orElseThrow();
            for(var type:List.of(function.parameters().getFirst().typeId(),function.returnType()))
                assertEquals(uuid.id(),assertInstanceOf(DomainReferenceType.class,analysis.semanticGraph().type(type).orElseThrow()).targetDomain());
        }
    }

    private record Project(CompilationRequest input,SourceText base,SourceText use) { }
    private static Project project(String version,boolean reverse,long revision,boolean valid) {
        var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili",model(version,"Base","FUNCTION accepts(value:INTERLIS.UUIDOID):BOOLEAN;"));
        var id=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");
        var use=new SourceText(id,new SourceRevision(revision),model(version,"F","IMPORTS Base;STRUCTURE Holder=identifier:INTERLIS.UUIDOID;END Holder;TOPIC T=CLASS C=value:INTERLIS."
                +(valid?"UUIDOID":"TypoOID")+";MANDATORY CONSTRAINT Base.accepts(value);END C;END T;"));
        var input=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
        return new Project(input,base,use);
    }
    private static SymbolId checkSharedUuid(AnalysisSnapshot analysis) {
        var graph=analysis.semanticGraph();var refs=analysis.queries().findQualifiedName(new QualifiedName(List.of("INTERLIS","UUIDOID")));
        assertEquals(1,refs.size());var uuid=assertInstanceOf(DomainSymbol.class,analysis.queries().symbol(refs.getFirst()).known().orElseThrow());
        var function=graph.symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast)
                .filter(f->f.name().value().equals("accepts")).findFirst().orElseThrow();
        assertEquals(uuid.id(),assertInstanceOf(DomainReferenceType.class,graph.type(function.parameters().getFirst().typeId()).orElseThrow()).targetDomain());
        assertEquals(uuid.id(),aliasTarget(graph,attribute(graph,"F.Holder.identifier").declaredType()));
        var attribute=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(a->a.name().value().equals("value")).findFirst().orElseThrow();
        assertEquals(uuid.id(),assertInstanceOf(DomainReferenceType.class,graph.type(attribute.declaredType()).orElseThrow()).targetDomain());
        var relation=new TypeRelations(graph).canAssign(new TypeRef(graph.snapshotId(),attribute.declaredType()),new TypeRef(graph.snapshotId(),function.parameters().getFirst().typeId()),AssignmentContext.FUNCTION_ARGUMENT).known().orElseThrow();
        assertEquals(RelationDecision.ALLOWED,relation.decision());return uuid.id();
    }

    @Test void importedSignatureAndLocalAttributeShareOneUuidIdentityRegardlessOfSourceOrder() {
        for(String version:List.of("2.3","2.4")) {
            SymbolId first=null;
            for(boolean reverse:List.of(false,true)) {
                var project=project(version,reverse,1,true);var result=ok(project.input());
                var id=checkSharedUuid(result.analysis());
                if(first!=null)assertEquals(first,id);first=id;
                assertEquals(2,result.schema().semanticGraph().models().size());
            }
        }
    }

    @Test void validInvalidValidImportedEditsMatchFreshAnalysisAndPreserveCanonicalIdentity() {
        for(String version:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;SymbolId uuid=null;
            for(int step=1;step<=3;step++) {
                boolean valid=step!=2;var project=project(version,reverse,step,valid);
                var fresh=CompilerEngines.create().analyze(project.input(),TOKEN);
                var reused=engine.analyze(project.input(),previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(valid,reused.diagnostics().isEmpty(),reused.diagnostics().toString());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(),reused.semanticGraph()));
                if(valid) {
                    var id=checkSharedUuid(reused);assertEquals(id,checkSharedUuid(fresh));if(uuid!=null)assertEquals(uuid,id);uuid=id;
                } else {
                    assertTrue(reused.diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0001")));
                    var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,engine.compile(project.input(),TOKEN));
                    assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
                }
                previous=reused;
            }
        }
    }

    @Test void mixedLanguageRootsKeepSeparatePredefinedIdentities() {
        for(boolean reverse:List.of(false,true)) {
            var a=source(reverse?"memory:/z-a.ili":"memory:/a-a.ili",model("2.3","A","STRUCTURE S=identifier:INTERLIS.UUIDOID;END S;"));
            var b=source(reverse?"memory:/a-b.ili":"memory:/z-b.ili",model("2.4","B","STRUCTURE S=identifier:INTERLIS.UUIDOID;END S;"));
            var input=request(List.of("A","B"),Map.of(a.id(),a,b.id(),b),List.of(),CompilerOptions.defaults());
            var result=ok(input);var graph=result.schema().semanticGraph();
            var old=predefined(result.analysis(),"UUIDOID",LanguageVersion.ILI23);var modern=predefined(result.analysis(),"UUIDOID",LanguageVersion.ILI24);
            assertNotEquals(old.id(),modern.id());assertNotEquals(old.typeId(),modern.typeId());
            assertEquals(old.id(),aliasTarget(graph,attribute(graph,"A.S.identifier").declaredType()));assertEquals(modern.id(),aliasTarget(graph,attribute(graph,"B.S.identifier").declaredType()));
            assertEquals(2,result.analysis().queries().findQualifiedName(new QualifiedName(List.of("INTERLIS","UUIDOID"))).size());
            checkVersionedDeclaration(result.analysis(),"UUIDOID",LanguageVersion.ILI23);checkVersionedDeclaration(result.analysis(),"UUIDOID",LanguageVersion.ILI24);
            var reused=CompilerEngines.create().analyze(input,new AnalysisReuse(Optional.of(result.analysis()),result.analysis().syntaxSnapshots()),TOKEN);
            assertTrue(reused.diagnostics().isEmpty());assertEquals(old.id(),predefined(reused,"UUIDOID",LanguageVersion.ILI23).id());assertEquals(modern.id(),predefined(reused,"UUIDOID",LanguageVersion.ILI24).id());
        }
    }

    @Test void nonexistentNooidIn23AndUnknownOidNamesRemainSourceErrors() {
        for(String version:List.of("2.3","2.4"))for(String name:version.equals("2.3")?List.of("NOOID","TypoOID"):List.of("TypoOID")) {
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,"STRUCTURE S=identifier:INTERLIS."+name+";END S;"),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0001")));
            assertTrue(rejected.analysis().queries().findQualifiedName(new QualifiedName(List.of("INTERLIS",name))).isEmpty());
        }
    }
}
