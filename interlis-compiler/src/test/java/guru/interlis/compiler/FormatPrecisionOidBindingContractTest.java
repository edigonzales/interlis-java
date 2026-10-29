package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.compiler.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormatPrecisionOidBindingContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.";
    }
    private static CompilationOutcome compile(String text) {
        var source = CompilerTestSupport.source("memory:/format-oid.ili", text);
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"), Map.of(source.id(),source), List.of(), CompilerOptions.defaults()), TOKEN);
    }
    private static CompilationOutcome.Succeeded ok(String version, String body) {
        var result = compile(model(version,body));
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result, () -> body + "\n" + (result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics() : result));
    }
    private static CompilationOutcome.Rejected bad(String version, String body) {
        return assertInstanceOf(CompilationOutcome.Rejected.class, compile(model(version,body)), body);
    }
    private static Symbol symbol(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(s -> s.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static String numeric(String type, String width, String lower, String upper) {
        return "TOPIC T = STRUCTURE S = n: " + type + "; END S; DOMAIN Code = FORMAT BASED ON S (n" + width + ") \"" + lower + "\" .. \"" + upper + "\"; END T;";
    }

    @Test void fixedPrecisionPaddingAndExactLargeValues() {
        for (String version : List.of("2.3","2.4")) {
            ok(version,numeric("0.00..99.99","/2","01.20","20.00"));
            bad(version,numeric("0.00..99.99","/2","1.20","20.00"));
            bad(version,numeric("0.00..99.99","/2","01.2","20.00"));
            bad(version,numeric("0.00..99.99","/2","01.201","20.00"));
            ok(version,numeric("-99.99..99.99","/2","-01.20","20.00"));
            ok(version,numeric("0.00..9007199254740993.99","","9007199254740993.01","9007199254740993.02"));
            bad(version,numeric("0.00..9007199254740993.99","","9007199254740993.02","9007199254740993.01"));
            bad(version,numeric("0..99","/99999999999999999999999","01","20"));
        }
    }

    @Test void scaledPrecisionRetainsMantissaDigitsAndRejectsWrongForm() {
        var literal = DecimalLiteral.parse("0.12e+3");
        assertEquals(2,literal.precision().fractionalDigits()); assertEquals(3,literal.precision().exponent());
        assertEquals(0, literal.exactValue().compareTo(new java.math.BigDecimal("120")));
        for (String version : List.of("2.3","2.4")) {
            ok(version,numeric("0.10e1..0.99e3","","0.12e2","0.90e3"));
            bad(version,numeric("0.10e1..0.99e3","","0.123e2","0.90e3"));
            bad(version,numeric("0.10e1..0.99e3","","1.20e1","0.90e3"));
            bad(version,numeric("0.10e1..0.99e3","","12.00","900.00"));
            bad(version,numeric("0.00..99.99","","0.12e2","0.90e1"));
        }
    }

    private static final String NESTED = "TOPIC T = STRUCTURE S = n: 0..99; END S; "
            + "DOMAIN Code = FORMAT BASED ON S (n/2) \"10\" .. \"20\"; "
            + "STRUCTURE More EXTENDS S = extra: 0..9; END More; "
            + "DOMAIN Child EXTENDS Code = FORMAT BASED ON More (INHERITANCE \"-\" extra); "
            + "STRUCTURE Container = item: More; number: 0..9; END Container; "
            + "DOMAIN Pair = FORMAT BASED ON Container (item/Child \"/\" number) \"12-1/0\" .. \"18-9/9\"; "
            + "CLASS A = value: Pair; MANDATORY CONSTRAINT value == \"15-5/5\"; END A; END T;";

    @Test void nestedBoundsIncludeTheEntireBaseChainAndConstants() {
        for (String version : List.of("2.3","2.4")) {
            var graph = ok(version,NESTED).schema().semanticGraph();
            var pair = (DomainSymbol)symbol(graph,"Pair");
            var type = assertInstanceOf(FormattedType.class, graph.type(pair.typeId()).orElseThrow());
            assertEquals(symbol(graph,"Child").id(),type.parts().getFirst().format().orElseThrow());
            bad(version,NESTED.replace("12-1/0","09-1/0"));
            bad(version,NESTED.replace("18-9/9","21-9/9"));
            bad(version,NESTED.replace("15-5/5","09-5/5"));
            bad(version,NESTED.replace("15-5/5","19-5/5"));
        }
    }

    @Test void predefinedDateAndTimeHaveBoundedLexicalComponents() {
        for (String version : List.of("2.3","2.4")) {
            String date = "TOPIC T = DOMAIN D = FORMAT INTERLIS.XMLDate \"2010-01-01\" .. \"2020-12-31\"; END T;";
            ok(version,date);
            if (version.equals("2.3")) ok(version,date.replace("2010-01-01","2010-1-1"));
            else bad(version,date.replace("2010-01-01","2010-1-1"));
            bad(version,date.replace("2010-01-01","2010-01-32"));
        }
    }

    @Test void separateBasketAndObjectRulesRetainBindingProvenanceAndClosure() {
        for (String version : List.of("2.3","2.4")) {
            String body = "DOMAIN BasketId = OID TEXT*16; ObjectId = OID TEXT*36; TOPIC T = "
                    + "BASKET OID AS BasketId; OID AS ObjectId; CLASS A = END A; CLASS B = NO OID; END B; END T;";
            var result = ok(version,body); var graph = result.schema().semanticGraph();
            var topic = symbol(graph,"T"); var rules = topic.header().oidRules();
            assertEquals(2,rules.size()); assertTrue(rules.get(0).basket()); assertFalse(rules.get(1).basket());
            assertEquals(symbol(graph,"BasketId").id(),rules.get(0).domain().orElseThrow());
            assertEquals(symbol(graph,"ObjectId").id(),rules.get(1).domain().orElseThrow());
            assertEquals(List.of(rules.get(1)),symbol(graph,"A").header().oidRules());
            assertTrue(symbol(graph,"B").header().oidRules().getFirst().domain().isEmpty());
            var source = CompilerTestSupport.source("memory:/format-oid.ili",model(version,body));
            int offset = source.text().indexOf("AS ObjectId")+3;
            var queries = LanguageQueries.create();
            var handle = queries.symbolAt(result.analysis(),source.range(offset,offset),TOKEN).known().orElseThrow();
            assertEquals(symbol(graph,"ObjectId").id(),handle.symbol());
            var edit = queries.planRename(result.analysis(),new RenameRequest(handle,"StableId",result.analysis().sourceRevisions()),TOKEN);
            assertTrue(edit.complete());
            assertTrue(edit.edits().stream().flatMap(e -> e.edits().stream()).anyMatch(e -> e.range().equals(source.range(offset,offset+8))));
        }
    }

    @Test void oidBindingIsLocalTypedAndDoesNotScanChildDeclarations() {
        for (String version : List.of("2.3","2.4")) {
            var graph = ok(version,"TOPIC T = CLASS A = OID AS INTERLIS.UUIDOID; END A; CLASS B = END B; END T;").schema().semanticGraph();
            assertTrue(symbol(graph,"T").header().oidRules().isEmpty());
            assertTrue(symbol(graph,"B").header().oidRules().isEmpty());
            var target = symbol(graph,"A").header().oidRules().getFirst().domain().orElseThrow();
            assertEquals("INTERLIS.UUIDOID",graph.symbol(target).orElseThrow().name().value());
            bad(version,"TOPIC T = CLASS A = OID AS Missing; END A; END T;");
            bad(version,"DOMAIN Id = TEXT*36; TOPIC T = CLASS A = OID AS Id; END A; END T;");
            bad(version,"DOMAIN Id = OID TEXT*36; TOPIC T = CLASS A = OID AS Missing.Id; END A; END T;");
        }
    }

    @Test void oidInheritanceAllowsOnlyIdentityAndNormativeConcretization() {
        for (String version : List.of("2.3","2.4")) {
            String classes = "TOPIC T = CLASS A (ABSTRACT) = OID AS INTERLIS.ANYOID; END A; "
                    + "CLASS B EXTENDS A = OID AS INTERLIS.UUIDOID; END B; END T;";
            ok(version,classes);
            bad(version,classes.replace("CLASS A (ABSTRACT)","CLASS A"));
            bad(version,classes.replace("OID AS INTERLIS.UUIDOID", "NO OID"));
            bad(version,classes.replace("INTERLIS.ANYOID","INTERLIS.STANDARDOID"));
            var graph = ok(version,"TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = END A; END T; TOPIC U EXTENDS T = CLASS B = END B; END U;").schema().semanticGraph();
            assertEquals(symbol(graph,"T").header().oidRules(),symbol(graph,"U").header().oidRules());
            bad(version,"TOPIC T = OID AS INTERLIS.UUIDOID; END T; TOPIC U EXTENDS T = OID AS INTERLIS.STANDARDOID; END U;");
        }
    }

    @Test void namedOidIdentityAndExplicitNoOidRemainDistinct() {
        for (String version : List.of("2.3","2.4")) {
            String body = "DOMAIN Id = OID TEXT*36; TOPIC T = CLASS A = OID AS Id; END A; "
                    + "CLASS B EXTENDS A = OID AS Id; END B; END T;";
            var graph = ok(version,body).schema().semanticGraph();
            assertEquals(symbol(graph,"Id").id(),symbol(graph,"B").header().oidRules().getFirst().domain().orElseThrow());
            if (version.equals("2.4")) ok(version,"TOPIC T = CLASS A = OID AS INTERLIS.NOOID; END A; END T;");
            else bad(version,"TOPIC T = CLASS A = OID AS INTERLIS.NOOID; END A; END T;");
            bad(version,"TOPIC T = NO OID; END T;");
            ok(version,"TOPIC T = OID AS INTERLIS.UUIDOID; STRUCTURE S = END S; CLASS A EXTENDS S = NO OID; END A; END T;");
            String implicit = "TOPIC T = CLASS A = END A; CLASS B EXTENDS A = OID AS INTERLIS.UUIDOID; END B; END T;";
            if (version.equals("2.3")) ok(version,implicit); else bad(version,implicit);
            String abstractChild = "TOPIC T = CLASS A = END A; CLASS B (ABSTRACT) EXTENDS A = OID AS INTERLIS.ANYOID; END B; END T;";
            bad(version,abstractChild);
            ok(version,abstractChild.replace("TOPIC T =", "TOPIC T (ABSTRACT) ="));
        }
    }

    @Test void translatedOidRulesCompareMappedIdentitiesInsteadOfEqualCarrierTypes() {
        for (String version : List.of("2.3","2.4")) {
            String original = "DOMAIN FirstId = OID TEXT*36; SecondId = OID TEXT*36; TOPIC T = "
                    + "BASKET OID AS FirstId; OID AS SecondId; CLASS A = END A; END T;";
            String base = model(version,original).replace("MODEL F ","MODEL Base ").replace("END F.","END Base.");
            String translated = model(version,original.replace("FirstId","Un").replace("SecondId","Deux"))
                    .replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Base [\"1\"] =").replace("INTERLIS " + version + "; ", "");
            var result = compile(base + translated);
            assertInstanceOf(CompilationOutcome.Succeeded.class,result, () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
            assertInstanceOf(CompilationOutcome.Rejected.class,compile(base + translated.replace("OID AS Deux", "OID AS Un")));
            String builtin = "TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = END A; END T;";
            base = model(version,builtin).replace("MODEL F ","MODEL Base ").replace("END F.","END Base.");
            translated = model(version,builtin).replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Base [\"1\"] =")
                    .replace("INTERLIS " + version + "; ", "");
            var builtinResult = compile(base + translated);
            assertInstanceOf(CompilationOutcome.Succeeded.class,builtinResult, () -> builtinResult instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : builtinResult.toString());
        }
    }

    @Test void oidContractsAreVersionSpecificAndAssociationRulesRemainBound() {
        String body="TOPIC T = CLASS A = OID AS INTERLIS.STANDARDOID; END A; CLASS B = OID AS INTERLIS.STANDARDOID; END B; ASSOCIATION Link = OID AS INTERLIS.UUIDOID; a -- A; b -- B; END Link; END T;";
        for (String version : List.of("2.3","2.4")) {
            var graph=ok(version,body).schema().semanticGraph();
            assertTrue(symbol(graph,"Link").header().oidRules().getFirst().domain().isPresent());
            String multiple = body.replace("END Link;", "CARDINALITY = {2}; END Link;");
            ok(version,multiple);
            bad(version,multiple.replace("OID AS INTERLIS.UUIDOID;", "NO OID;"));
            bad(version,multiple.replace("OID AS INTERLIS.UUIDOID;", ""));
        }
        assertInstanceOf(CompilationOutcome.Succeeded.class,compile(model("2.3",body).replace("CONTRACTED ","")));
        assertInstanceOf(CompilationOutcome.Succeeded.class,compile(model("2.4",body).replace("CONTRACTED ","")));
    }
}
