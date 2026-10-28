package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** INTERLIS 2.4 OF accepts named domains as well as structures; 2.3 remains structure-only. */
class NamedCollectionElementContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static String wrap(String version,String name,String body) {
        return "INTERLIS "+version+";CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\"="+body+"END "+name+".";
    }
    private static CompilationRequest input(String version,String body) {
        var text=source("memory:/named-collections.ili",wrap(version,"F",body));
        return request(List.of("F"),Map.of(text.id(),text),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String version,String body) {
        var result=CompilerEngines.create().compile(input(version,body),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
    }
    private static AttributeSymbol attribute(SemanticGraph graph,String name) {
        return graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(a->a.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static CollectionType collection(SemanticGraph graph,String name) {
        return assertInstanceOf(CollectionType.class,graph.type(attribute(graph,name).declaredType()).orElseThrow());
    }
    private static TypeDescriptor effective(SemanticGraph graph,TypeId type) {
        var result=new TypeRelations(graph).resolveAliases(new TypeRef(graph.snapshotId(),type)).known().orElseThrow();
        return graph.type(result.effectiveType()).orElseThrow();
    }
    private static java.util.stream.Stream<SyntaxNode> syntaxNodes(SyntaxNode node) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(node),node.children().stream().flatMap(NamedCollectionElementContractTest::syntaxNodes));
    }
    @Test void namedScalarsKeepDomainReferencesAndResolvedElementTypesInBothCollectionKinds() {
        for(String kind:List.of("BAG","LIST")) {
            var result=ok("2.4","DOMAIN Identifier=OID TEXT*36;Label=TEXT*20;RequiredLabel EXTENDS Label=MANDATORY;Amount=0.0..100.0;"
                    +"TOPIC T=CLASS C=ids:"+kind+"{1..3} OF Identifier;labels:"+kind+" OF RequiredLabel;amounts:"+kind+" OF Amount;direct:"+kind+" OF INTERLIS.UUIDOID;END C;END T;");
            var graph=result.schema().semanticGraph();
            var ids=collection(graph,"ids");assertEquals(kind.equals("BAG")?CollectionKind.BAG:CollectionKind.LIST,ids.collectionKind());
            assertEquals(Cardinality.of(1,3),ids.cardinality());
            var declared=assertInstanceOf(DomainReferenceType.class,graph.type(ids.elementType()).orElseThrow());
            assertEquals("Identifier",graph.symbol(declared.targetDomain()).orElseThrow().name().value());
            assertEquals(OidKind.TEXT,assertInstanceOf(OidType.class,effective(graph,ids.elementType())).oidKind());
            assertEquals(20,assertInstanceOf(TextType.class,effective(graph,collection(graph,"labels").elementType())).maxLength().orElseThrow());
            assertEquals("100.0",assertInstanceOf(NumericType.class,effective(graph,collection(graph,"amounts").elementType())).range().maximum().orElseThrow().lexeme());
            var direct=assertInstanceOf(DomainReferenceType.class,graph.type(collection(graph,"direct").elementType()).orElseThrow());
            assertEquals(new Origin.PredefinedOrigin(LanguageVersion.ILI24,"INTERLIS.UUIDOID"),graph.symbol(direct.targetDomain()).orElseThrow().origin());
            assertEquals(OidKind.UUID,assertInstanceOf(OidType.class,effective(graph,collection(graph,"direct").elementType())).oidKind());
            var labelRef=assertInstanceOf(DomainReferenceType.class,graph.type(collection(graph,"labels").elementType()).orElseThrow());
            var requiredLabel=assertInstanceOf(DomainSymbol.class,graph.symbol(labelRef.targetDomain()).orElseThrow());
            assertEquals("RequiredLabel",requiredLabel.name().value());assertTrue(requiredLabel.mandatory());
            assertEquals("Label",graph.symbol(requiredLabel.baseDomain().orElseThrow()).orElseThrow().name().value());
            var syntax=result.analysis().syntaxSnapshots().values().iterator().next();
            var types=syntaxNodes(syntax.root()).filter(CollectionTypeSyntax.class::isInstance).map(CollectionTypeSyntax.class::cast).toList();
            assertEquals(4,types.size());assertTrue(types.stream().allMatch(c->c.elementType() instanceof DomainReferenceSyntax));
            var text=syntax.source();int offset=text.text().indexOf("OF Identifier")+3;
            assertEquals(declared.targetDomain(),LanguageQueries.create().symbolAt(result.analysis(),text.range(offset,offset),TOKEN).known().orElseThrow().symbol());
        }
    }
    @Test void structuresRestrictionsAndExistingFunctionConsumersKeepTheirBindings() {
        for(String version:List.of("2.3","2.4")) {
            var result=ok(version,"STRUCTURE S=value:TEXT;END S;STRUCTURE A EXTENDS S=END A;"
                    +"FUNCTION countRows(rows:BAG OF S):NUMERIC;TOPIC T=CLASS C=items:BAG OF S;restricted:LIST OF S RESTRICTION(A);any:LIST OF ANYSTRUCTURE RESTRICTION(A);"
                    +"MANDATORY CONSTRAINT countRows(items)>=0 AND INTERLIS.elementCount(items)>=0;END C;END T;");
            var graph=result.schema().semanticGraph();
            var ordinary=assertInstanceOf(StructureValueType.class,graph.type(collection(graph,"items").elementType()).orElseThrow());
            assertEquals("S",graph.symbol(ordinary.target().orElseThrow()).orElseThrow().name().value());
            var restricted=assertInstanceOf(StructureValueType.class,graph.type(collection(graph,"restricted").elementType()).orElseThrow());
            assertEquals(List.of("A"),restricted.restrictions().stream().map(id->graph.symbol(id).orElseThrow().name().value()).toList());
            var wildcard=assertInstanceOf(StructureValueType.class,graph.type(collection(graph,"any").elementType()).orElseThrow());
            assertTrue(wildcard.target().isEmpty());assertEquals(restricted.restrictions(),wildcard.restrictions());
            var function=graph.symbols().stream().filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast).filter(f->f.name().value().equals("countRows")).findFirst().orElseThrow();
            var formal=assertInstanceOf(CollectionType.class,graph.type(function.parameters().getFirst().typeId()).orElseThrow());
            assertEquals(ordinary.target(),assertInstanceOf(StructureValueType.class,graph.type(formal.elementType()).orElseThrow()).target());
        }
    }
    @Test void olderLanguageAndWrongTargetKindsRemainRegularSourceErrors() {
        for(String kind:List.of("BAG","LIST"))for(String name:List.of("INTERLIS.UUIDOID","Code","Label","Amount")) {
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input("2.3",
                    "DOMAIN Code=OID TEXT*36;Label=TEXT;Amount=0..10;TOPIC T=CLASS C=items:"+kind+" OF "+name+";END C;END T;"),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
        }
        for(String version:List.of("2.3","2.4"))for(String name:List.of("C","INTERLIS.NoSuchDomain","Code RESTRICTION(S)")) {
            var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,
                    "DOMAIN Code=TEXT;STRUCTURE S=END S;TOPIC T=CLASS C=items:BAG OF "+name+";END C;END T;"),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
            assertTrue(result.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0001")));
        }
    }
    @Test void importedInheritedDomainsSurviveSourceOrderAndValidInvalidValidReuse() {
        for(boolean reverse:List.of(false,true))for(String kind:List.of("BAG","LIST")) {
            var base=source(reverse?"memory:/z-base.ili":"memory:/a-base.ili",wrap("2.4","Base",
                    "DOMAIN Label=TEXT*20;RequiredLabel EXTENDS Label=MANDATORY;Identifier=OID TEXT*36;Number=0..10;STRUCTURE Row=value:TEXT;END Row;TOPIC Other=CLASS Wrong=END Wrong;END Other;"));
            var useId=new SourceId(reverse?"memory:/a-use.ili":"memory:/z-use.ili");var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String element:List.of("Base.RequiredLabel","Base.Other.Wrong","Base.RequiredLabel")) {
                var use=new SourceText(useId,new SourceRevision(++revision),wrap("2.4","F","IMPORTS Base;TOPIC T=CLASS C=labels:"+kind+"{1..3} OF "+element+";ids:BAG OF Base.Identifier;numbers:LIST OF Base.Number;rows:BAG OF Base.Row;END C;END T;"));
                var request=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(request,TOKEN);var reused=previous==null?fresh:engine.analyze(request,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(element.endsWith("Wrong"),!fresh.diagnostics().isEmpty());
                if(!element.endsWith("Wrong")) {
                    var graph=reused.semanticGraph();var collection=collection(graph,"labels");
                    var declared=assertInstanceOf(DomainReferenceType.class,graph.type(collection.elementType()).orElseThrow());
                    var requiredLabel=assertInstanceOf(DomainSymbol.class,graph.symbol(declared.targetDomain()).orElseThrow());
                    assertEquals("RequiredLabel",requiredLabel.name().value());assertTrue(requiredLabel.mandatory());
                    assertEquals("Label",graph.symbol(requiredLabel.baseDomain().orElseThrow()).orElseThrow().name().value());
                    var resolved=new TypeRelations(graph).resolveAliases(new TypeRef(graph.snapshotId(),collection.elementType())).known().orElseThrow();
                    assertEquals(declared.targetDomain(),resolved.aliasPath().getFirst());
                    assertEquals(20,assertInstanceOf(TextType.class,graph.type(resolved.effectiveType()).orElseThrow()).maxLength().orElseThrow());
                    assertEquals(OidKind.TEXT,assertInstanceOf(OidType.class,effective(graph,collection(graph,"ids").elementType())).oidKind());assertInstanceOf(NumericType.class,effective(graph,collection(graph,"numbers").elementType()));
                    assertInstanceOf(StructureValueType.class,graph.type(collection(graph,"rows").elementType()).orElseThrow());
                    int offset=use.text().indexOf(element)+5;
                    assertEquals(declared.targetDomain(),LanguageQueries.create().symbolAt(reused,use.range(offset,offset),TOKEN).known().orElseThrow().symbol());
                }
                previous=reused;
            }
        }
    }
}
