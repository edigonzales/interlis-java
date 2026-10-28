package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class UnitRelationContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final String UNITS="UNIT L (ABSTRACT); m EXTENDS L; Other EXTENDS L; s; km=1000[m]; cm=1/100[m]; h=3600[s]; v=(m/s); fast=(km/h); inverse=(s/m); a=(v/s); a2=(m/s/s); f=FUNCTION //external conversion// [m]; square=(m*m); square2=(km*cm);";
    private static SemanticGraph graph(String version,String body) {
        var source=source("memory:/units.ili","INTERLIS "+version+"; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = "+body+" END F.");
        var result=CompilerEngines.create().compile(request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString()).schema().semanticGraph();
    }
    private static SymbolRef ref(SemanticGraph graph,String name) {return new SymbolRef(graph.snapshotId(),graph.symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow().id());}
    private static RelationDecision compare(SemanticGraph graph,String a,String b) {return new TypeRelations(graph).compareUnits(ref(graph,a),ref(graph,b)).known().orElseThrow().decision();}
    @Test void conversionsUseBoundDefinitionsAndComposedProductsWithoutExecutingFunctionsOrDecimals() {
        for(var version:List.of("2.3","2.4")) {
            var graph=graph(version,UNITS);
            for(var pair:List.of(List.of("m","m"),List.of("m","km"),List.of("cm","km"),List.of("v","fast"),List.of("a","a2"),List.of("square","square2"),List.of("m","f"))) {
                assertEquals(RelationDecision.ALLOWED,compare(graph,pair.get(0),pair.get(1)),pair::toString);
                assertEquals(RelationDecision.ALLOWED,compare(graph,pair.get(1),pair.get(0)),pair::toString);
            }
            for(var pair:List.of(List.of("m","s"),List.of("m","Other"),List.of("m","L"),List.of("v","inverse"),List.of("m","square")))
                assertEquals(RelationDecision.DISALLOWED,compare(graph,pair.get(0),pair.get(1)),pair::toString);
        }
    }
    @Test void numericReferenceSystemsUseSymmetricConversionCompatibility() {
        for(var version:List.of("2.3","2.4")) {
            var graph=graph(version,UNITS+" DOMAIN Target=0..100 [km]; Value=0..100 [m] <Target>; Speed=0..100 [fast]; Rate=0..100 [v] <Speed>;");
            assertEquals(RelationDecision.ALLOWED,compare(graph,"m","km"));
            assertEquals(RelationDecision.ALLOWED,compare(graph,"v","fast"));
            var rate=graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(d->d.name().value().equals("Rate")).findFirst().orElseThrow();
            var type=assertInstanceOf(NumericType.class,graph.type(rate.typeId()).orElseThrow());
            assertEquals(ref(graph,"Speed").symbol(),type.range().referenceSystem().orElseThrow().target());
        }
    }
    @Test void wrongSnapshotsUnknownIdsAndNonUnitSymbolsNeverReceiveAnIdentitySuccess() {
        var graph=graph("2.4",UNITS);var query=new TypeRelations(graph);var m=ref(graph,"m");var model=ref(graph,"F");
        var missing=new SymbolRef(graph.snapshotId(),new SymbolId("unknown"));
        assertEquals(UnresolvedReason.UNKNOWN_ID,assertInstanceOf(QueryResult.Unresolved.class,query.compareUnits(missing,missing)).reason());
        assertEquals(UnresolvedReason.NOT_A_UNIT,assertInstanceOf(QueryResult.Unresolved.class,query.compareUnits(model,model)).reason());
        var foreign=new SymbolRef(new SnapshotId("foreign"),m.symbol());
        assertThrows(SnapshotMismatchException.class,()->query.compareUnits(foreign,m));
        assertThrows(SnapshotMismatchException.class,()->query.compareUnits(m,foreign));
    }
    @Test void cyclicUnitDependenciesRemainUnknownEvenWhenComparingTheSameUnit() {
        var source=source("memory:/cycle.ili","INTERLIS 2.4; MODEL F AT \"https://example.org\" VERSION \"1\" = UNIT a=2[b]; b=2[a]; END F.");
        var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),TOKEN));
        var graph=result.analysis().semanticGraph();assertInstanceOf(QueryResult.NotAnalyzed.class,new TypeRelations(graph).compareUnits(ref(graph,"a"),ref(graph,"a")));
    }
    @Test void missingCompositionFactorsCannotDisappearFromThePublicRelation() {
        var source=source("memory:/missing.ili","INTERLIS 2.4; MODEL F AT \"https://example.org\" VERSION \"1\" = UNIT m; a=(m/Missing); b=(m); END F.");
        var outcome=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),TOKEN));
        var graph=outcome.analysis().semanticGraph();var query=new TypeRelations(graph);
        assertInstanceOf(QueryResult.NotAnalyzed.class,query.compareUnits(ref(graph,"a"),ref(graph,"b")));
    }
    @Test void conversionCompatibilityDoesNotPermitChangingAConcreteTypeUnit() {
        var graph=graph("2.4",UNITS+" DOMAIN Metres=0..1000 [m]; Kilometres=0..1 [km];");
        var query=new TypeRelations(graph);assertEquals(RelationDecision.ALLOWED,compare(graph,"m","km"));
        var metres=graph.declaredType(ref(graph,"Metres").symbol()).orElseThrow();var km=graph.declaredType(ref(graph,"Kilometres").symbol()).orElseThrow();
        assertEquals(RelationDecision.DISALLOWED,query.canExtend(new TypeRef(graph.snapshotId(),km),new TypeRef(graph.snapshotId(),metres),ExtensionContext.DOMAIN_EXTENSION,graph.languageVersions().iterator().next()).known().orElseThrow().decision());
    }
}
