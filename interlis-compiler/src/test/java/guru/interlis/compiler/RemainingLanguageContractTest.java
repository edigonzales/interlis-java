package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** L4-1: uncovered lexical, enumeration and expression observations. */
class RemainingLanguageContractTest {
    private static CompilationRequest request(String version,String body) {
        var source=CompilerTestSupport.source("memory:/remaining.ili","INTERLIS "+version+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String version,String body) {
        var out=CompilerEngines.create().compile(request(version,body),CancellationToken.none());return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static EnumerationType enumeration(SemanticGraph graph,String name) {
        var domain=graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(d->d.name().value().equals(name)).findFirst().orElseThrow();
        return assertInstanceOf(EnumerationType.class,graph.type(domain.typeId()).orElseThrow());
    }
    private static void bad(String version,String body,String rule) {
        var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,body),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0010")&&d.arguments().toString().contains(rule)),out.analysis().diagnostics().toString());
    }
    @Test void enumerationOrderingCircularityAndClosedSubtreesArePublicAndEnforced() {
        for(String version:List.of("2.3","2.4")) {
            var graph=ok(version,"DOMAIN E=(a,b) ORDERED;Next EXTENDS E=(c);Cycle=(red,green,blue) CIRCULAR;").schema().semanticGraph();
            var ordered=enumeration(graph,"Next");assertTrue(ordered.ordered());assertFalse(ordered.circular());
            assertEquals(List.of("a","b","c"),ordered.values().stream().map(EnumerationValue::path).toList());
            assertEquals(List.of(0,1,2),ordered.values().stream().map(EnumerationValue::order).toList());
            assertEquals(Optional.of(enumeration(graph,"E").id()),ordered.baseType());
            assertTrue(enumeration(graph,"Cycle").circular());assertTrue(enumeration(graph,"Cycle").ordered());
            bad(version,"DOMAIN E=(a,b) CIRCULAR;Next EXTENDS E=(c);","ENUM_CIRCULAR");
            bad(version,"DOMAIN E=(a,b);Next EXTENDS E=(c) CIRCULAR;","ENUM_ORDER");
            bad(version,"DOMAIN E=(a,b:FINAL);Next EXTENDS E=(c);","ENUM_FINAL");
            bad(version,"DOMAIN E=(a(x,y:FINAL),b);Next EXTENDS E=(a(z));","ENUM_FINAL");
        }
    }
    @Test void publicExpressionTreePreservesComparisonAndBooleanPrecedence() {
        for(String version:List.of("2.3","2.4")) {
            var graph=ok(version,"TOPIC T=CLASS C=n:0..10;MANDATORY CONSTRAINT n==1 OR n==2 AND n==3;END C;END T;").schema().semanticGraph();
            var constraint=graph.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast).findFirst().orElseThrow();
            var mandatory=assertInstanceOf(MandatoryConstraint.class,constraint.definition());
            var root=assertInstanceOf(BinaryExpression.class,graph.expression(mandatory.condition()).orElseThrow());assertEquals(BinaryOperator.OR,root.operator());
            assertEquals(BinaryOperator.EQ,((BinaryExpression)graph.expression(root.left()).orElseThrow()).operator());
            var right=assertInstanceOf(BinaryExpression.class,graph.expression(root.right()).orElseThrow());assertEquals(BinaryOperator.AND,right.operator());
            for(var child:right.children())assertEquals(BinaryOperator.EQ,((BinaryExpression)graph.expression(child).orElseThrow()).operator());
            assertInstanceOf(BooleanType.class,graph.type(root.typeId()).orElseThrow());assertEquals(ExpressionTypeState.RESOLVED,root.typeState());
            assertEquals("n==1 OR n==2 AND n==3",request(version,"TOPIC T=CLASS C=n:0..10;MANDATORY CONSTRAINT n==1 OR n==2 AND n==3;END C;END T;").sources().sources().values().iterator().next().slice(root.range()));
        }
    }
    @Test void everyPinnedAppendixCharacterSurvivesSourceSyntaxAndDecodedLiteral() throws Exception {
        var profile=new Properties();try(var reader=Files.newBufferedReader(Path.of(System.getProperty("testdata.dir"),"g2/appendix-characters.properties"))){profile.load(reader);}
        for(String version:List.of("2.3","2.4")) {
            var decoded=new StringBuilder();for(String hex:profile.getProperty(version).split(","))decoded.appendCodePoint(Integer.parseInt(hex,16));
            assertEquals(version.equals("2.3")?131:162,decoded.codePointCount(0,decoded.length()));
            String literal=decoded.toString().replace("\\","\\\\").replace("\"","\\\"");String body="STRUCTURE S=value:TEXT:=\""+literal+"\";END S;";
            var input=request(version,body);var outcome=ok(version,body);var graph=outcome.schema().semanticGraph();
            var expression=graph.expressions().stream().filter(LiteralExpression.class::isInstance).map(LiteralExpression.class::cast).filter(e->e.textValue().equals(Optional.of(decoded.toString()))).findFirst().orElseThrow();
            assertEquals("\""+literal+"\"",input.sources().sources().values().iterator().next().slice(expression.range()));
            var syntax=outcome.analysis().syntaxSnapshots().values().iterator().next();assertEquals(input.sources().sources().values().iterator().next().text(),syntax.source().text());
        }
    }
    @Test void acceptedAndRejectedWhitespaceAreExplicitInBothLanguageProfiles() {
        for(String version:List.of("2.3","2.4"))for(String space:List.of(" ","\t","\r","\n","\r\n")) {
            String text="INTERLIS"+space+version+";MODEL"+space+"F AT \"x\" VERSION \"1\"=END F.";
            var source=CompilerTestSupport.source("memory:/whitespace.ili",text);var in=CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
            assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(in,CancellationToken.none()));
        }
        for(String version:List.of("2.3","2.4"))for(String unsupported:List.of("\f","\u000b","\u00a0")) {
            var source=CompilerTestSupport.source("memory:/whitespace.ili","INTERLIS"+unsupported+version+";MODEL F AT \"x\" VERSION \"1\"=END F.");
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),CancellationToken.none()));
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.code().value().startsWith("ILI-SYN-")));
        }
    }
    @Test void everyPredefinedUnitKeepsItsVersionedDesignationCanonicalOwnerAndBase() {
        var names=List.of("ANYUNIT","DIMENSIONLESS","LENGTH","MASS","TIME","ELECTRIC_CURRENT","TEMPERATURE","AMOUNT_OF_MATTER","ANGLE","SOLID_ANGLE","LUMINOUS_INTENSITY","MONEY",
                "m","kg","s","A","K","mol","rad","sr","cd","min","h","d","M","Y");
        var concrete=Map.of("m","METER","kg","KILOGRAM","s","SECOND","A","AMPERE","K","DEGREE_KELVIN","mol","MOLE","rad","RADIAN","sr","STERADIAN","cd","CANDELA");
        var temporal=Map.of("min","Minute","h","Hour","d","Day","M","Month","Y","Year");
        var bases=Map.of("m","LENGTH","kg","MASS","s","TIME","A","ELECTRIC_CURRENT","K","TEMPERATURE","mol","AMOUNT_OF_MATTER","rad","ANGLE","sr","SOLID_ANGLE","cd","LUMINOUS_INTENSITY");
        for(String version:List.of("2.3","2.4")) {
            StringBuilder body=new StringBuilder("DOMAIN ");for(int i=0;i<names.size();i++)body.append("U").append(i).append("(ABSTRACT)=NUMERIC [INTERLIS.").append(names.get(i)).append("];");
            var out=ok(version,body.toString());var graph=out.schema().semanticGraph();
            for(String name:names) {
                var found=out.schema().queries().findQualifiedName(QualifiedName.of("INTERLIS",name));assertEquals(1,found.size(),name);
                var unit=assertInstanceOf(UnitSymbol.class,out.schema().queries().symbol(found.getFirst()).known().orElseThrow());
                String designation=concrete.getOrDefault(name,temporal.getOrDefault(name,name));
                if(version.equals("2.3")&&name.equals("kg"))designation="KILOGRAMM";
                assertEquals(designation,unit.designation(),version+":"+name);
                assertEquals(version.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,unit.languageVersion());
                var owner=assertInstanceOf(ModelSymbol.class,graph.symbol(unit.owner().orElseThrow()).orElseThrow());assertEquals("INTERLIS",owner.name().value());assertTrue(owner.members().contains(unit.id()));
                if(bases.containsKey(name))assertEquals(out.schema().queries().findQualifiedName(QualifiedName.of("INTERLIS",bases.get(name))).getFirst().symbol(),unit.base().orElseThrow());
            }
        }
    }
}
