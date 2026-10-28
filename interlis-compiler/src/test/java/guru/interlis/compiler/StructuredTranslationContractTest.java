package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** Translation compares actual model structure and bound values, not a second lexer pass. */
class StructuredTranslationContractTest {
    private static String pair(String v,String a,String b) {
        return "INTERLIS "+v+";CONTRACTED MODEL Original AT \"https://example.org\" VERSION \"1\"="+a+"END Original."
                +"CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" TRANSLATION OF Original[\"1\"]="+b+"END F.";
    }
    private static CompilationRequest input(String text,long revision) {
        var source=new SourceText(new SourceId("memory:/structured-translation.ili"),new SourceRevision(revision),text);
        return request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String v,String a,String b) {
        var result=CompilerEngines.create().compile(input(pair(v,a,b),1),CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
    }
    private static void bad(String v,String a,String b) {
        var result=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(pair(v,a,b),1),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),result.rejection().reasons());
        assertTrue(result.analysis().diagnostics().stream().anyMatch(d->d.arguments().toString().contains("TRANSLATION_")),result.analysis().diagnostics().toString());
    }
    @Test void versionedIdentifiersAndDocumentaryParameterNamesNeedNoNameHeuristics() {
        var result=ok("2.3","DOMAIN DATETIME=TEXT;FUNCTION f(first:BOOLEAN):BOOLEAN;","DOMAIN Zeitpunkt=TEXT;FUNCTION g(zweite:BOOLEAN):BOOLEAN;");
        var graph=result.schema().semanticGraph();var model=graph.symbols().stream().filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast).filter(m->m.name().value().equals("F")).findFirst().orElseThrow();
        var link=model.translation().orElseThrow().correspondences().stream().filter(l->graph.symbol(l.original()).orElseThrow().name().value().equals("DATETIME")).findFirst().orElseThrow();
        assertEquals("Zeitpunkt",graph.symbol(link.translated()).orElseThrow().name().value());
    }
    @Test void translationCorrespondencesPreserveStructuralMemberOrder() {
        for(String v:List.of("2.3","2.4")) {
            String a="DOMAIN A1=TEXT;DOMAIN A2=BOOLEAN;DOMAIN A3=0..10;";
            String b="DOMAIN B1=TEXT;DOMAIN B2=BOOLEAN;DOMAIN B3=0..10;";
            var result=ok(v,a,b);var graph=result.schema().semanticGraph();
            var model=graph.symbols().stream().filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast)
                    .filter(m->m.name().value().equals("F")).findFirst().orElseThrow();
            var originals=model.translation().orElseThrow().correspondences().stream()
                    .filter(l->graph.symbol(l.original()).orElseThrow() instanceof DomainSymbol)
                    .map(l->graph.symbol(l.original()).orElseThrow().name().value()).toList();
            assertEquals(List.of("A1","A2","A3"),originals,v);
            bad(v,a,"DOMAIN B1=BOOLEAN;DOMAIN B2=TEXT;DOMAIN B3=0..10;");
        }
    }
    @Test void everyComparedTypeFamilyKeepsItsActualProperties() {
        for(String v:List.of("2.3","2.4")) {
            for(var types:List.of(List.of("TEXT*10","TEXT*11"),List.of("TEXT*10","MTEXT*10"),List.of("BOOLEAN","HALIGNMENT"),List.of("HALIGNMENT","VALIGNMENT"),List.of("BLACKBOX XML","BLACKBOX BINARY"),List.of("OID TEXT*10","OID TEXT*11")))
                bad(v,"DOMAIN A="+types.getFirst()+";","DOMAIN B="+types.getLast()+";");
            String a="STRUCTURE S=x:TEXT;END S;TOPIC T=CLASS C=items:LIST {1..3} OF S;END C;END T;";
            ok(v,a,a.replace("items","values"));
            bad(v,a,a.replace("LIST","BAG"));bad(v,a,a.replace("1..3","1..4"));
        }
    }
    @Test void numericValuesNormalizeSpellingWhileTypePrecisionRemainsSignificant() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,"DOMAIN A=00.0..10.0;","DOMAIN B=0.0..10.0;");
            bad(v,"DOMAIN A=0.0..10.0;","DOMAIN B=0.00..10.00;");
            String prefix="TOPIC T=CLASS C=x:0..10;MANDATORY CONSTRAINT x > ";String suffix=";END C;END T;";
            ok(v,prefix+"01"+suffix,prefix+"1.0"+suffix);
            bad(v,prefix+"1"+suffix,prefix+"2"+suffix);
        }
    }
    @Test void enumTopologyAndBoundConstantsCannotBeReplacedByEqualSizedTrees() {
        for(String v:List.of("2.3","2.4")) {
            String a="DOMAIN E=(a(b),c);TOPIC T=CLASS C=x:E;MANDATORY CONSTRAINT x == #a.b;END C;END T;";
            String b="DOMAIN F=(d(e),f);TOPIC T=CLASS C=x:F;MANDATORY CONSTRAINT x == #d.e;END C;END T;";
            ok(v,a,b);bad(v,a,b.replace("#d.e","#f"));
            bad(v,"DOMAIN E=(a(b),c);","DOMAIN F=(d,e(f));");
        }
    }
    @Test void formattedComponentsRetainTheirBoundAttributeIdentity() {
        for(String v:List.of("2.3","2.4")) {
            String a="STRUCTURE S=a:0..9;b:0..9;END S;DOMAIN D=FORMAT BASED ON S(a/1);";
            String b="STRUCTURE Q=x:0..9;y:0..9;END Q;DOMAIN E=FORMAT BASED ON Q(x/1);";
            ok(v,a,b);bad(v,a,b.replace("Q(x/1)","Q(y/1)"));
        }
    }
    @Test void domainConstraintsKeepTheirDefinitionsAndCorrespondences() {
        String a="DOMAIN D=0..10 CONSTRAINTS Limit:THIS > 1;";
        String b="DOMAIN E=0..10 CONSTRAINTS Grenze:THIS > 1;";
        var result=ok("2.4",a,b);var graph=result.schema().semanticGraph();
        var links=graph.symbols().stream().filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast).filter(m->m.name().value().equals("F")).findFirst().orElseThrow().translation().orElseThrow().correspondences();
        var constraint=links.stream().filter(l->graph.symbol(l.original()).orElseThrow() instanceof ConstraintSymbol).findFirst().orElseThrow();
        var original=graph.symbol(constraint.original()).orElseThrow();var translated=graph.symbol(constraint.translated()).orElseThrow();
        assertEquals("Limit",original.name().value());assertEquals("Grenze",translated.name().value());
        var source=result.analysis().syntaxSnapshots().values().iterator().next().source();
        assertEquals("Limit",source.slice(original.nameRange().orElseThrow()));assertEquals("Grenze",source.slice(translated.nameRange().orElseThrow()));
        bad("2.4",a,b.replace("> 1","> 2"));bad("2.4",a,b.replace("> 1","< 1"));
    }
    @Test void stringValuesCompareDecodedContentAndSourceEditsMatchReuse() {
        for(String v:List.of("2.3","2.4")) {
            String a="TOPIC T=CLASS C=x:TEXT;MANDATORY CONSTRAINT x == \"a\";END C;END T;";
            String b="TOPIC T=CLASS C=x:TEXT;MANDATORY CONSTRAINT x == \"\\u0061\";END C;END T;";
            ok(v,a,b);
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String body:List.of(b,b.replace("0061","0062"),b)) {
                var request=input(pair(v,a,body),++revision);var fresh=engine.analyze(request,CancellationToken.none());
                var reused=previous==null?fresh:engine.analyze(request,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(body.contains("0062"),!fresh.diagnostics().isEmpty());
                var model=fresh.semanticGraph().symbols().stream().filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast).filter(m->m.name().value().equals("F")).findFirst().orElseThrow();
                assertTrue(model.translation().orElseThrow().correspondences().size()>=4);previous=reused;
            }
        }
    }
    @Test void plausibilityRetainsExactThresholdOperatorAndOriginalPercentageRange() {
        for(String v:List.of("2.3","2.4")) {
            String original="TOPIC T=CLASS C=flag:BOOLEAN;CONSTRAINT >= 90.50% flag;END C;END T;";
            var result=ok(v,original,original.replace("90.50","90.5"));var graph=result.schema().semanticGraph();
            var constraints=graph.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast).toList();
            for(var c:constraints) {
                var value=assertInstanceOf(PlausibilityConstraint.class,c.definition());
                assertEquals(0,value.percentage().compareTo(new java.math.BigDecimal("90.5")));assertEquals(BinaryOperator.GTE,value.direction());
            }
            var snapshot=result.analysis().syntaxSnapshots().values().iterator().next();
            var syntax=syntaxNodes(snapshot.root()).filter(guru.interlis.syntax.ConstraintDeclarationSyntax.class::isInstance)
                    .map(guru.interlis.syntax.ConstraintDeclarationSyntax.class::cast).filter(c->c.plausibility().isPresent()).findFirst().orElseThrow();
            assertEquals("90.50",snapshot.source().slice(syntax.plausibility().orElseThrow().range()));
            bad(v,original,original.replace(">=","<="));bad(v,original,original.replace("90.50","80.50"));
        }
    }
    @Test void setFiltersAndScopeAreBoundAndComparedIndependentlyOfTheSetCondition() {
        for(String v:List.of("2.3","2.4")) {
            String original="FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;TOPIC T=CLASS C=flag:BOOLEAN;other:BOOLEAN;SET CONSTRAINT WHERE flag:check(ALL);END C;END T;";
            var result=ok(v,original,original);var graph=result.schema().semanticGraph();
            for(var c:graph.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast).toList()) {
                var set=assertInstanceOf(SetConstraint.class,c.definition());assertEquals(SetConstraint.SetConstraintScope.GLOBAL,set.scope());
                var path=assertInstanceOf(PathExpression.class,graph.expression(set.where().orElseThrow()).orElseThrow());
                var step=assertInstanceOf(PathStep.AttributeStep.class,path.path().steps().getLast());assertEquals("flag",graph.symbol(step.target()).orElseThrow().name().value());
                var range=path.range();assertEquals(step.target(),guru.interlis.compiler.query.LanguageQueries.create().symbolAt(result.analysis(),new SourceRange(range.source(),range.revision(),range.startOffset(),range.startOffset()),CancellationToken.none()).known().orElseThrow().symbol());
            }
            bad(v,original,original.replace("WHERE flag:","WHERE other:"));bad(v,original,original.replace("WHERE flag:",""));
            bad(v,original,original.replace("flag:BOOLEAN","flag:0..10"));
        }
        var result=ok("2.4","FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;TOPIC T=CLASS C=SET CONSTRAINT (BASKET) check(ALL);END C;END T;","FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;TOPIC T=CLASS C=SET CONSTRAINT (BASKET) check(ALL);END C;END T;");
        assertTrue(result.schema().semanticGraph().symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast)
                .allMatch(c->((SetConstraint)c.definition()).scope()==SetConstraint.SetConstraintScope.BASKET));
        bad("2.4","FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;TOPIC T=CLASS C=SET CONSTRAINT check(ALL);END C;END T;","FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;TOPIC T=CLASS C=SET CONSTRAINT (BASKET) check(ALL);END C;END T;");
    }
    @Test void constraintNamesAndBasketSyntaxFollowTheirLanguageVersion() {
        for(String clause:List.of("MANDATORY CONSTRAINT Named:#true;","CONSTRAINT Named:>=90% #true;","SET CONSTRAINT Named:#true;","SET CONSTRAINT (BASKET) #true;")) {
            var body="FUNCTION check(objects:OBJECTS OF ANYCLASS):BOOLEAN;TOPIC T=CLASS C="+(clause.startsWith("SET")?clause.replace("#true","check(ALL)"):clause)+"END C;END T;";ok("2.4",body,body);
            var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(pair("2.3",body,body),1),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());assertTrue(r.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SYN-0006")));
        }
        for(String v:List.of("2.3","2.4"))for(String clause:List.of("SET CONSTRAINT (LOCAL) #true;","CONSTRAINT >=PI% #true;","CONSTRAINT >=1[INTERLIS.m]% #true;")) {
            var body="TOPIC T=CLASS C="+clause+"END C;END T;";
            var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(pair(v,body,body),1),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());
        }
    }
    private static java.util.stream.Stream<guru.interlis.syntax.SyntaxNode> syntaxNodes(guru.interlis.syntax.SyntaxNode node) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(node),node.children().stream().flatMap(StructuredTranslationContractTest::syntaxNodes));
    }

}
