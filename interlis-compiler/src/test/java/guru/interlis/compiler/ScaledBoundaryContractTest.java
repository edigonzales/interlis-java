package guru.interlis.compiler;

import guru.interlis.diagnostics.Phase;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** Float mantissas are checked on numeric type boundaries, not on independent constant contexts. */
class ScaledBoundaryContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static String wrap(String v,String body) {
        return "INTERLIS "+v+";\r\n!! 🦊 source prefix\r\nCONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.";
    }
    private static CompilationRequest input(String v,String body,long revision) {
        var text=new SourceText(new SourceId("memory:/scaled-boundary.ili"),new SourceRevision(revision),wrap(v,body));
        return request(List.of("F"),Map.of(text.id(),text),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String v,String body) {
        var r=CompilerEngines.create().compile(input(v,body,1),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString());
    }
    @Test void scaledZeroSignsAndEqualExponentsKeepTheirDeclaredPrecisionAndValues() {
        for(String v:List.of("2.3","2.4"))for(var pair:List.of(
                List.of("0.00e1","0.10e2"),List.of("0.1e1","0.2e1"),List.of("-0.0e0","0.0e0"),
                List.of("+0.1E-2","+0.2E-1"),List.of("-0.1e2","+0.2e3"),List.of("0.01","0.20"),List.of("0","10"))) {
            var result=ok(v,"DOMAIN D="+pair.getFirst()+".."+pair.getLast()+";");var graph=result.schema().semanticGraph();
            var domain=graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(d->d.name().value().equals("D")).findFirst().orElseThrow();
            var range=assertInstanceOf(NumericType.class,graph.type(domain.typeId()).orElseThrow()).range();
            assertEquals(pair.getFirst(),range.minimum().orElseThrow().lexeme());assertEquals(pair.getLast(),range.maximum().orElseThrow().lexeme());
            assertEquals(DecimalLiteral.parse(pair.getFirst()).precision(),range.minimum().orElseThrow().precision());
            assertEquals(DecimalLiteral.parse(pair.getLast()).precision(),range.maximum().orElseThrow().precision());
        }
    }
    @Test void malformedScaledBoundariesHaveOneSourceSyntaxErrorAtTheExactTokenInEveryNumericTypeContext() {
        var templates=List.of("DOMAIN D=%s;","TOPIC T=CLASS C=value:%s;END C;END T;", "DOMAIN P=COORD %s,0.0..10.0;",
                "DOMAIN Identity=OID %s;","STRUCTURE S=x:%s;END S;DOMAIN Form=FORMAT BASED ON S(x);");
        for(String v:List.of("2.3","2.4"))for(String template:templates)for(var values:List.of(
                List.of("1.0e1","0.2e3","1.0e1"),List.of("0.1e1","2.0e2","2.0e2"),
                List.of("0.01e1","0.20e2","0.01e1"),List.of("-0.01e1","0.20e2","-0.01e1"),
                List.of("0.10e1","+0.02e3","+0.02e3"))) {
            var request=input(v,template.formatted(values.get(0)+".."+values.get(1)),9);
            var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request,TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());
            var diagnostics=r.analysis().diagnostics();
            assertEquals(1,diagnostics.size(),diagnostics.toString());var diagnostic=diagnostics.getFirst();
            assertEquals("ILI-SYN-0002",diagnostic.code().value());assertEquals(Phase.PARSING,diagnostic.phase());
            var source=request.sources().sources().values().iterator().next();
            assertEquals(values.get(2),source.slice(diagnostic.primaryRange().orElseThrow()));
            assertEquals(source.revision(),diagnostic.primaryRange().orElseThrow().revision());
        }
    }
    @Test void ordinaryConstantsAndUnitFactorsKeepTheirIndependentNumericProfile() {
        for(String v:List.of("2.3","2.4")) {
            var r=ok(v,"UNIT scaled=1.0e1[INTERLIS.m];FUNCTION accepts(value:NUMERIC):BOOLEAN;TOPIC T=CLASS C=MANDATORY CONSTRAINT accepts(1.0e1) AND accepts(0.01e1);END C;END T;");
            var unit=r.schema().semanticGraph().symbols().stream().filter(UnitSymbol.class::isInstance).map(UnitSymbol.class::cast).filter(u->u.name().value().equals("scaled")).findFirst().orElseThrow();
            assertEquals(0,new java.math.BigDecimal("10").compareTo(unit.conversionFactors().getFirst().decimal().orElseThrow().exactValue()));
        }
    }
    @Test void declarationFragmentsKeepOriginalUtf16RangesAndExpressionFragmentsKeepTheirNumericProfile() {
        for(var language:LanguageVersion.values()) {
            String prefix="🦊\r\n  ",declaration="value:1.0e1..0.2e3;";
            var source=new SourceText(new SourceId("memory:/scaled-fragment.ili"),new SourceRevision(7),prefix+declaration+" trailing");
            var parsed=SyntaxServices.create().parseFragment(source,source.range(prefix.length(),prefix.length()+declaration.length()),FragmentKind.ATTRIBUTE_DECLARATION,language,TOKEN);
            assertEquals(1,parsed.diagnostics().size());var range=parsed.diagnostics().getFirst().primaryRange().orElseThrow();
            assertEquals(source.range(prefix.length()+6,prefix.length()+11),range);assertEquals("1.0e1",source.slice(range));
            var expression=SyntaxServices.create().parseFragment(source,range,FragmentKind.EXPRESSION,language,TOKEN);
            assertTrue(expression.diagnostics().isEmpty(),expression.diagnostics().toString());
            assertEquals("1.0e1",assertInstanceOf(LiteralExpressionSyntax.class,expression.root()).lexeme());
        }
    }
    @Test void validInvalidValidEditsMatchFreshAndReusedDiagnostics() {
        for(String v:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String lower:List.of("0.1e1","1.0e1","0.1e1")) {
                var input=input(v,"DOMAIN D="+lower+"..0.2e2;",++revision);
                var fresh=engine.analyze(input,TOKEN);var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(lower.startsWith("1."),!fresh.diagnostics().isEmpty());
                if(lower.startsWith("1."))assertEquals("ILI-SYN-0002",fresh.diagnostics().getFirst().code().value());
                previous=reused;
            }
        }
    }
    @Test void importedBoundaryEditsAndReverseSourceOrderMatchFreshAnalysis() {
        for(String v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var baseId=new SourceId(reverse?"memory:/z-base.ili":"memory:/a-base.ili");
            var use=source(reverse?"memory:/a-use.ili":"memory:/z-use.ili",wrap(v,"IMPORTS Base;STRUCTURE Row=value:Base.D;END Row;"));
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String lower:List.of("0.1e1","1.0e1","0.1e1")) {
                var base=new SourceText(baseId,new SourceRevision(++revision),wrap(v,"DOMAIN D="+lower+"..0.2e2;").replace("MODEL F ","MODEL Base ").replace("END F.","END Base."));
                var request=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=CompilerEngines.create().analyze(request,TOKEN);
                var reused=engine.analyze(request,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(revision==2,!fresh.diagnostics().isEmpty());
                if(revision==2) {
                    assertEquals(1,reused.diagnostics().size());var d=reused.diagnostics().getFirst();
                    assertEquals("ILI-SYN-0002",d.code().value());assertEquals(base.id(),d.primaryRange().orElseThrow().source());
                    assertEquals(base.revision(),d.primaryRange().orElseThrow().revision());
                } else {
                    var graph=reused.semanticGraph();var attribute=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).findFirst().orElseThrow();
                    var target=assertInstanceOf(DomainReferenceType.class,graph.type(attribute.declaredType()).orElseThrow()).targetDomain();
                    var domain=assertInstanceOf(DomainSymbol.class,graph.symbol(target).orElseThrow());
                    assertEquals("D",domain.name().value());
                    assertEquals("0.1e1",assertInstanceOf(NumericType.class,graph.type(domain.typeId()).orElseThrow()).range().minimum().orElseThrow().lexeme());
                }
                previous=reused;
            }
        }
    }

}
