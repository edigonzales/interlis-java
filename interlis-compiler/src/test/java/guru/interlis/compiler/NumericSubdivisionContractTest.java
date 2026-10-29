package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** L1-2: numeric subdivisions, their actual predecessors and exact conversion factors. */
class NumericSubdivisionContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final List<String> VERSIONS=List.of("2.3","2.4");
    private static CompilationRequest input(String version,String body,int revision) {
        var s=new SourceText(new SourceId("memory:/subdivision.ili"),new SourceRevision(revision),
                "INTERLIS "+version+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String version,String body) {
        var out=CompilerEngines.create().compile(input(version,body,1),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static CompilationOutcome.Rejected bad(String version,String body,String reason) {
        var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(version,body,7),TOKEN),body);
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
        var diagnostics=out.analysis().diagnostics().stream().filter(d->d.arguments().toString().contains(reason)).toList();
        assertFalse(diagnostics.isEmpty(),()->out.analysis().diagnostics().toString());
        for(var d:diagnostics) {
            assertEquals("ILI-SEM-0010",d.code().value());assertTrue(d.failureId().isEmpty());
            assertEquals(new SourceId("memory:/subdivision.ili"),d.primaryRange().orElseThrow().source());
            assertEquals(new SourceRevision(7),d.primaryRange().orElseThrow().revision());
        }
        return out;
    }
    private static AttributeSymbol attribute(SemanticGraph graph,String owner,String name) {
        return graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(a->a.name().value().equals(name)&&graph.symbol(a.owner().orElseThrow()).orElseThrow().name().value().equals(owner))
                .findFirst().orElseThrow();
    }
    private static NumericType numeric(SemanticGraph graph,AttributeSymbol a) {
        var resolved=new TypeRelations(graph).resolveAliases(new TypeRef(graph.snapshotId(),a.declaredType())).known().orElseThrow();
        return assertInstanceOf(NumericType.class,graph.type(resolved.effectiveType()).orElseThrow());
    }
    @Test void standardClockKeepsItsFlagsPrecisionUnitTargetsAndFormattedConsumers() {
        for(String v:VERSIONS) {
            var out=ok(v,"STRUCTURE Clock=Hours:0..23 CIRCULAR[INTERLIS.h];"
                    +"CONTINUOUS SUBDIVISION Minutes:0..59 CIRCULAR[INTERLIS.min];"
                    +"CONTINUOUS SUBDIVISION Seconds:0.000..59.999 CIRCULAR[INTERLIS.s];END Clock;"
                    +"DOMAIN Stamp=FORMAT BASED ON Clock(Hours/2 \":\" Minutes/2 \":\" Seconds);");
            var g=out.schema().semanticGraph();var hours=attribute(g,"Clock","Hours");
            var minutes=attribute(g,"Clock","Minutes");var seconds=attribute(g,"Clock","Seconds");
            assertFalse(hours.subdivision());assertFalse(hours.continuous());
            for(var a:List.of(minutes,seconds)){assertTrue(a.subdivision());assertTrue(a.continuous());assertTrue(numeric(g,a).range().circular());}
            assertEquals("59.999",numeric(g,seconds).range().maximum().orElseThrow().lexeme());
            assertEquals(new DecimalPrecision(3,0,PrecisionOrigin.LEXICAL),numeric(g,seconds).range().maximum().orElseThrow().precision());
            var minuteUnit=assertInstanceOf(UnitSymbol.class,g.symbol(numeric(g,minutes).range().unit().orElseThrow()).orElseThrow());
            assertEquals(0,new BigDecimal("60").compareTo(minuteUnit.conversionFactors().getFirst().decimal().orElseThrow().exactValue()));
            assertEquals(numeric(g,seconds).range().unit(),minuteUnit.derivedFrom());
            var clock=g.symbol(hours.owner().orElseThrow()).orElseThrow();
            assertEquals(List.of(hours.id(),minutes.id(),seconds.id()),g.effectiveMembers(clock.id()).stream().map(EffectiveMember::declaredSymbol).toList());
            var stamp=g.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(d->d.name().value().equals("Stamp")).findFirst().orElseThrow();
            var format=assertInstanceOf(FormattedType.class,g.type(stamp.typeId()).orElseThrow());
            assertEquals(Optional.of(clock.id()),format.structure());
            assertEquals(List.of(hours.id(),minutes.id(),seconds.id()),format.parts().stream().flatMap(p->p.attribute().stream()).toList());
        }
    }
    @Test void subdivisionsRequireAnActualNumericIntegerPredecessorAndNonnegativeRange() {
        var cases=new LinkedHashMap<String,String>();
        cases.put("SUBDIVISION V:0..59;","SUBDIVISION_PREDECESSOR");
        cases.put("P:TEXT;SUBDIVISION V:0..59;","SUBDIVISION_PREDECESSOR_NUMERIC");
        cases.put("P:0..23;SUBDIVISION V:TEXT;","SUBDIVISION_NUMERIC");
        cases.put("P:0.0..23.0;SUBDIVISION V:0..59;","SUBDIVISION_PREDECESSOR_INTEGER");
        cases.put("P:0..23;SUBDIVISION V:-1..59;","SUBDIVISION_NONNEGATIVE");
        for(String v:VERSIONS)for(var sample:cases.entrySet())bad(v,"STRUCTURE S="+sample.getKey()+"END S;",sample.getValue());
    }
    @Test void continuousSubdivisionsRequireCircularityAndTheCorrectConversionPeriod() {
        for(String v:VERSIONS) {
            bad(v,"STRUCTURE S=P:0..23[INTERLIS.h];CONTINUOUS SUBDIVISION V:0..59[INTERLIS.min];END S;","SUBDIVISION_CIRCULAR");
            bad(v,"STRUCTURE S=P:0..23[INTERLIS.h];CONTINUOUS SUBDIVISION V:0..58 CIRCULAR[INTERLIS.min];END S;","SUBDIVISION_FACTOR");
            bad(v,"STRUCTURE S=P:0..23[INTERLIS.h];CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[INTERLIS.m];END S;","SUBDIVISION_FACTOR");
            bad(v,"STRUCTURE S(ABSTRACT)=P(ABSTRACT):NUMERIC[INTERLIS.TIME];"
                    +"CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[INTERLIS.m];END S;","SUBDIVISION_FACTOR");
            bad(v,"UNIT coarse=(INTERLIS.m/INTERLIS.s);fine=(INTERLIS.m*INTERLIS.m);"
                    +"STRUCTURE S=P:0..23[coarse];CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[fine];END S;","SUBDIVISION_FACTOR");
            bad(v,"UNIT unknown;unitless=(unknown/unknown);STRUCTURE S=P:0..23[unitless];"
                    +"CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[INTERLIS.s];END S;","SUBDIVISION_FACTOR");
        }
    }
    @Test void exactFactorsHandleDivisionCompositionAndSymbolicCancellation() {
        for(String v:VERSIONS)for(String units:List.of(
                "coarse=1[INTERLIS.s];fine=1/3[INTERLIS.s];",
                "a=3[INTERLIS.s];b=1[INTERLIS.s];coarse=(a*b);fine=(b*b);",
                "coarse=3*PI[INTERLIS.s];fine=PI[INTERLIS.s];",
                "coarse=3*LNBASE[INTERLIS.s];fine=LNBASE[INTERLIS.s];"))
            ok(v,"UNIT "+units+"STRUCTURE S=P:0..23[coarse];CONTINUOUS SUBDIVISION V:0..2 CIRCULAR[fine];END S;");
        for(String v:VERSIONS)bad(v,"UNIT coarse=1[INTERLIS.s];fine=1/3[INTERLIS.s];"
                +"STRUCTURE S=P:0..23[coarse];CONTINUOUS SUBDIVISION V:0..1 CIRCULAR[fine];END S;","SUBDIVISION_FACTOR");
        for(String v:VERSIONS)for(String constant:List.of("PI","LNBASE"))
            bad(v,"UNIT coarse="+constant+"[INTERLIS.s];STRUCTURE S=P:0..23[coarse];"
                    +"CONTINUOUS SUBDIVISION V:0..2 CIRCULAR[INTERLIS.s];END S;","SUBDIVISION_FACTOR");
    }
    @Test void approvedUnknownFactorsRemainValidWhileKnownFactorsAcrossOpaqueBasesAreChecked() {
        for(String v:VERSIONS) {
            var unknown=ok(v,"UNIT first EXTENDS INTERLIS.TIME;second EXTENDS INTERLIS.TIME;"
                    +"STRUCTURE S=P:0..23[first];CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[second];END S;");
            var graph=unknown.schema().semanticGraph();var units=graph.symbols().stream().filter(UnitSymbol.class::isInstance).map(UnitSymbol.class::cast)
                    .filter(u->Set.of("first","second").contains(u.name().value())).toList();
            assertEquals(2,units.size());assertEquals(units.getFirst().base(),units.getLast().base());
            assertTrue(units.stream().allMatch(u->u.derivedFrom().isEmpty()&&u.conversionFactors().isEmpty()));
            assertEquals(RelationDecision.DISALLOWED,new TypeRelations(graph).compareUnits(new SymbolRef(graph.snapshotId(),units.getFirst().id()),
                    new SymbolRef(graph.snapshotId(),units.getLast().id())).known().orElseThrow().decision());
            ok(v,"UNIT first;second;STRUCTURE S=P:0..23[first];CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[second];END S;");
            ok(v,"STRUCTURE S=P:0..23;CONTINUOUS SUBDIVISION V:0..59 CIRCULAR;END S;");
            ok(v,"UNIT u=FUNCTION // external conversion // [INTERLIS.s];"
                    +"STRUCTURE S=P:0..23[u];CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[INTERLIS.s];END S;");
            ok(v,"UNIT u=FUNCTION // external conversion // [INTERLIS.s];h=60[u];"
                    +"STRUCTURE S=P:0..23[h];CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[u];END S;");
            bad(v,"UNIT u=FUNCTION // external conversion // [INTERLIS.s];h=60[u];"
                    +"STRUCTURE S=P:0..23[h];CONTINUOUS SUBDIVISION V:0..58 CIRCULAR[u];END S;","SUBDIVISION_FACTOR");
        }
    }
    @Test void aliasesAbstractRangesAndOrdinaryCalendarSubdivisionsRemainLegal() {
        for(String v:VERSIONS) {
            ok(v,"DOMAIN Hours=0..23[INTERLIS.h];Minutes=0..59 CIRCULAR[INTERLIS.min];"
                    +"STRUCTURE S=P:Hours;CONTINUOUS SUBDIVISION V:Minutes;END S;");
            ok(v,"STRUCTURE S(ABSTRACT)=P(ABSTRACT):NUMERIC;SUBDIVISION V(ABSTRACT):NUMERIC;END S;");
            ok(v,"STRUCTURE Base(ABSTRACT)=P(ABSTRACT):NUMERIC[INTERLIS.TIME];"
                    +"CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[INTERLIS.min];END Base;"
                    +"STRUCTURE Child EXTENDS Base=P(EXTENDED):0..23[INTERLIS.h];END Child;");
            ok(v,"STRUCTURE S=Year:INTERLIS.GregorianYear;SUBDIVISION Month:1..12[INTERLIS.M];SUBDIVISION Day:1..31[INTERLIS.d];END S;");
            ok(v,"STRUCTURE S=P:0..23[INTERLIS.s];CONTINUOUS SUBDIVISION V:0..0 CIRCULAR[INTERLIS.s];END S;");
            ok(v,"UNIT hundred=100[INTERLIS.s];STRUCTURE S=P:0..23[hundred];"
                    +"CONTINUOUS SUBDIVISION V:0.00e1..0.99e2 CIRCULAR[INTERLIS.s];END S;");
        }
    }
    @Test void commonUnitDependenciesCancelBeforeLargeOrDeepDefinitionsAreEvaluated() {
        for(String v:VERSIONS) {
            var chain=new StringBuilder("UNIT U0=1[INTERLIS.s];");
            for(int i=1;i<=1000;i++)chain.append("U").append(i).append("=1[U").append(i-1).append("]; ");
            chain.append("coarse=3[U1000];STRUCTURE S=P:0..23[coarse];CONTINUOUS SUBDIVISION V:0..2 CIRCULAR[U1000];END S;");
            ok(v,chain.toString());
            var huge=new StringBuilder("UNIT U0=").append("9".repeat(4000)).append("[INTERLIS.s];");
            for(int i=1;i<=12;i++)huge.append("U").append(i).append("=(U").append(i-1).append("*U").append(i-1).append(");");
            huge.append("coarse=3[U12];STRUCTURE S=P:0..23[coarse];CONTINUOUS SUBDIVISION V:0..2 CIRCULAR[U12];END S;");
            ok(v,huge.toString());
        }
    }
    @Test void largeExactFactorWorkUsesResourceCancellationAndSucceedsWithALargerBudget() {
        var x=new java.math.BigInteger("9".repeat(1000));
        String coarse=String.join("*",Collections.nCopies(80,"a"));
        var fine=new ArrayList<>(Collections.nCopies(40,"b"));fine.addAll(Collections.nCopies(40,"INTERLIS.s"));
        String body="UNIT a="+x+"[INTERLIS.s];b="+x.multiply(x)+"[INTERLIS.s];coarse=("+coarse+");fine=("+String.join("*",fine)+");"
                +"STRUCTURE S=P:0..23[coarse];CONTINUOUS SUBDIVISION V:0..0 CIRCULAR[fine];END S;";
        var in=input("2.4",body,1);
        var limited=assertInstanceOf(CompilationOutcome.Cancelled.class,CompilerEngines.create().compile(in,TOKEN));
        assertEquals(guru.interlis.execution.CancellationReason.RESOURCE_LIMIT,limited.reason());
        var defaults=CompilerOptions.defaults();
        var options=new CompilerOptions(defaults.semanticProfile(),defaults.allowedLanguages(),defaults.warningPolicy(),
                defaults.budget().withMaxSemanticWorkUnits(100_000_000L),defaults.overlays());
        var result=CompilerEngines.create().compile(new CompilationRequest(in.roots(),in.sources(),options),TOKEN);
        assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result.toString());
    }
    @Test void raisedLiteralBudgetsCannotBypassSemanticArithmeticLimits() {
        var defaults=CompilerOptions.defaults();var b=defaults.budget();
        var expanded=new AnalysisBudget(b.maxSourceUtf16Length(),b.maxTotalSourceUtf16Length(),b.maxTokens(),b.maxSyntaxNodes(),
                b.maxRecoveryEvents(),b.maxParseRuleDepth(),b.maxSemanticWorkUnits(),b.maxDiagnostics(),b.maxNumericLiteralDigits(),Integer.MAX_VALUE);
        var options=new CompilerOptions(defaults.semanticProfile(),defaults.allowedLanguages(),defaults.warningPolicy(),expanded,defaults.overlays());
        for(String version:VERSIONS) {
            var in=input(version,"UNIT coarse=2e2147483646[INTERLIS.s];STRUCTURE S=P:0..23[coarse];"
                    +"CONTINUOUS SUBDIVISION V:0.1e1..0.1e2147483647 CIRCULAR[INTERLIS.s];END S;",1);
            var result=CompilerEngines.create().compile(new CompilationRequest(in.roots(),in.sources(),options),TOKEN);
            var cancelled=assertInstanceOf(CompilationOutcome.Cancelled.class,result,()->result.toString());
            assertEquals(guru.interlis.execution.CancellationReason.RESOURCE_LIMIT,cancelled.reason());
        }
    }
    @Test void inheritedPredecessorsAreUsedAndInheritedSubdivisionsAreRechecked() {
        for(String v:VERSIONS) {
            ok(v,"STRUCTURE Base=P:0..23[INTERLIS.h];END Base;STRUCTURE Child EXTENDS Base="
                    +"CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[INTERLIS.min];END Child;");
            bad(v,"STRUCTURE Base=P:TEXT;END Base;STRUCTURE Child EXTENDS Base=SUBDIVISION V:0..59;END Child;","SUBDIVISION_PREDECESSOR_NUMERIC");
        }
        bad("2.3","STRUCTURE Base=P:0..23[INTERLIS.h];CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[INTERLIS.min];END Base;"
                +"STRUCTURE Child EXTENDS Base=P(EXTENDED):0.0..23.0[INTERLIS.h];END Child;","SUBDIVISION_PREDECESSOR_INTEGER");
    }
    @Test void unchangedInvalidInheritedRelationsReportOnlyTheirOriginalDeclaration() {
        for(String v:VERSIONS) {
            var out=bad(v,"STRUCTURE Base=P:0..23;SUBDIVISION V:-1..59;END Base;"
                    +"STRUCTURE Child EXTENDS Base=END Child;STRUCTURE Further EXTENDS Child=END Further;","SUBDIVISION_NONNEGATIVE");
            var diagnostics=out.analysis().diagnostics().stream().filter(d->d.arguments().toString().contains("SUBDIVISION_NONNEGATIVE")).toList();
            assertEquals(1,diagnostics.size());
            var source=out.analysis().syntaxSnapshots().values().iterator().next().source();
            assertEquals("SUBDIVISION V:-1..59;",source.slice(diagnostics.getFirst().primaryRange().orElseThrow()));
        }
    }
    @Test void factorEditsMatchFreshAnalysisWithoutChangingPublishedValues() {
        for(String v:VERSIONS) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;UnitSymbol published=null;
            for(int revision=1;revision<=3;revision++) {
                var in=input(v,"UNIT h="+(revision==2?"59":"60")+"[INTERLIS.s];"
                        +"STRUCTURE S=P:0..23[h];CONTINUOUS SUBDIVISION V:0..59 CIRCULAR[INTERLIS.s];END S;",revision);
                var reused=engine.analyze(in,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                var fresh=CompilerEngines.create().analyze(in,TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(revision!=2,reused.diagnostics().isEmpty());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(),reused.semanticGraph()));
                if(revision==1)published=reused.semanticGraph().symbols().stream().filter(UnitSymbol.class::isInstance).map(UnitSymbol.class::cast)
                        .filter(u->u.name().value().equals("h")).findFirst().orElseThrow();
                previous=reused;
            }
            assertEquals(0,new BigDecimal("60").compareTo(published.conversionFactors().getFirst().decimal().orElseThrow().exactValue()));
        }
    }
    @Test void continuousWithoutSubdivisionIsAParsingErrorWithExactNameRangeAndFragmentRecovery() {
        for(String v:VERSIONS) {
            String attribute="CONTINUOUS minutes:0..59 CIRCULAR;";
            var in=input(v,"!! 🌐 ä\r\nSTRUCTURE S="+attribute+"END S;",9);
            var source=in.sources().sources().values().iterator().next();
            for(var mode:List.of(ParseMode.EDITOR,ParseMode.STRICT)) {
                var syntax=SyntaxServices.create().parse(source,ParseOptions.defaults(mode),TOKEN);
                assertEquals(1,syntax.diagnostics().size());var d=syntax.diagnostics().getFirst();
                assertEquals("ILI-SYN-0002",d.code().value());assertEquals(guru.interlis.diagnostics.Phase.PARSING,d.phase());
                assertEquals(source.range(source.text().indexOf("minutes"),source.text().indexOf("minutes")+7),d.primaryRange().orElseThrow());
            }
            int start=source.text().indexOf(attribute);
            var fragment=SyntaxServices.create().parseFragment(source,source.range(start,start+attribute.length()),FragmentKind.ATTRIBUTE_DECLARATION,
                    v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,TOKEN);
            assertEquals(1,fragment.diagnostics().size());assertTrue(fragment.consumedEntireFragment());
            assertEquals(source.range(source.text().indexOf("minutes"),source.text().indexOf("minutes")+7),fragment.diagnostics().getFirst().primaryRange().orElseThrow());
            var recovered=assertInstanceOf(AttributeDeclarationSyntax.class,fragment.root());
            assertTrue(recovered.continuous());assertFalse(recovered.subdivision());
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(in,TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
        }
    }
}
