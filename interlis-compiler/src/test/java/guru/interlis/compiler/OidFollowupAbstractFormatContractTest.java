package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OidFollowupAbstractFormatContractTest {
    private static CompilationOutcome compile(String version, String body) {
        var source = CompilerTestSupport.source("memory:/oid-followups.ili", "INTERLIS " + version
                + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.");
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"), Map.of(source.id(),source),
                List.of(), CompilerOptions.defaults()), CancellationToken.none());
    }
    private static SemanticGraph ok(String version, String body) {
        var result = compile(version,body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,
                () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString())
                .schema().semanticGraph();
    }
    private static void bad(String version, String body, String code) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class,compile(version,body),body);
        assertTrue(result.analysis().diagnostics().toString().contains(code), () -> result.analysis().diagnostics().toString());
        assertFalse(result.analysis().diagnostics().toString().contains("FORMAT_NUMERIC_PRECISION"));
    }
    private static Symbol symbol(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(s -> s.name().value().equals(name)).findFirst().orElseThrow();
    }

    @Test void noOidAvailabilityAndConcretizationAreVersioned() {
        bad("2.3","STRUCTURE S = id: INTERLIS.NOOID; END S;", "unresolved");
        ok("2.4","STRUCTURE S = id: INTERLIS.NOOID; END S;");
        String explicit = "TOPIC T (ABSTRACT) = CLASS A = NO OID; END A; "
                + "CLASS B (ABSTRACT) EXTENDS A = OID AS INTERLIS.ANYOID; END B; END T;";
        bad("2.3",explicit,"OID_OVERRIDE"); ok("2.4",explicit);
        for (String version : List.of("2.3","2.4")) {
            bad(version,explicit.replace("CLASS B (ABSTRACT)","CLASS B").replace("INTERLIS.ANYOID","INTERLIS.UUIDOID"),"OID_OVERRIDE");
            ok(version,explicit.replace("OID AS INTERLIS.ANYOID","NO OID"));
        }
    }

    @Test void topicAssignmentsStayFixed() {
        for (String version : List.of("2.3","2.4")) {
            String topics = "TOPIC T (ABSTRACT) = OID AS INTERLIS.ANYOID; END T; "
                    + "TOPIC U (ABSTRACT) EXTENDS T = OID AS INTERLIS.UUIDOID; END U;";
            bad(version,topics,"OID_OVERRIDE");
            ok(version,topics.replace("INTERLIS.UUIDOID","INTERLIS.ANYOID"));
        }

    }

    private static final String ABSTRACT = "STRUCTURE S (ABSTRACT) = n (ABSTRACT): NUMERIC; END S; "
            + "DOMAIN Code (ABSTRACT) = FORMAT BASED ON S (n/2); ";
    private static final String CONCRETE = ABSTRACT
            + "STRUCTURE Concrete EXTENDS S = n (EXTENDED): 0.00..99.99; tail: 0..9; END Concrete; "
            + "DOMAIN Value EXTENDS Code = FORMAT BASED ON Concrete (INHERITANCE \"-\" tail) \"01.20-0\" .. \"20.00-9\"; "
            + "TOPIC T = CLASS A = value: Value; MANDATORY CONSTRAINT value == \"12.34-5\"; END A; END T;";

    @Test void abstractFormatIsRepresentableWithoutAnImplementationGap() {
        for (String version : List.of("2.3","2.4")) {
            var graph = ok(version,ABSTRACT);
            var domain = (DomainSymbol)symbol(graph,"Code");
            var format = assertInstanceOf(FormattedType.class,graph.type(domain.typeId()).orElseThrow());
            assertEquals(symbol(graph,"n").id(),format.parts().getFirst().attribute().orElseThrow());
            assertTrue(domain.header().modifiers().contains(Modifier.ABSTRACT));
            bad(version,ABSTRACT.replace("Code (ABSTRACT)","Code"),"FORMAT_ABSTRACT");
            bad(version,ABSTRACT + "STRUCTURE Holder = value: Code; END Holder;","FORMAT_ABSTRACT");
            ok(version,ABSTRACT + "STRUCTURE Holder (ABSTRACT) = value (ABSTRACT): Code; END Holder;");
            ok(version,ABSTRACT.replace("(n/2);","(n/2) \"01\" .. \"20\";"));
        }
    }

    @Test void inheritedFormatPartsUseTheSpecializedAttributeAndPrecision() {
        for (String version : List.of("2.3","2.4")) {
            var graph = ok(version,CONCRETE);
            var value = (DomainSymbol)symbol(graph,"Value");
            var format = assertInstanceOf(FormattedType.class,graph.type(value.typeId()).orElseThrow());
            var attribute = graph.symbol(format.parts().getFirst().attribute().orElseThrow()).orElseThrow();
            assertEquals(symbol(graph,"Concrete").id(),attribute.header().owner().orElseThrow());
            var original = (FormattedType)graph.type(((DomainSymbol)symbol(graph,"Code")).typeId()).orElseThrow();
            assertEquals(original.parts().getFirst().origin(),format.parts().getFirst().origin());
            bad(version,CONCRETE.replace("01.20","01.2"),"FORMAT_BOUNDS");
            bad(version,CONCRETE.replace("12.34","12.3"),"FORMAT");
            bad(version,CONCRETE.replace("n (EXTENDED): 0.00..99.99;", ""),"FORMAT_ABSTRACT");
        }
    }

    @Test void nestedAbstractFormatPropagatesUntilExplicitlyConcretized() {
        for (String version : List.of("2.3","2.4")) {
            String nested = ABSTRACT + "STRUCTURE Box (ABSTRACT) = item (ABSTRACT): S; END Box; "
                    + "DOMAIN Wrapped (ABSTRACT) = FORMAT BASED ON Box (item/Code);";
            ok(version,nested);
            bad(version,nested.replace("Wrapped (ABSTRACT)","Wrapped"),"FORMAT_ABSTRACT");
            bad(version,nested + "STRUCTURE H = value: Wrapped; END H;","FORMAT_ABSTRACT");
        }
    }
    @Test void abstractBoundsRemainEffectiveAfterSpecialization() {
        for (String version : List.of("2.3","2.4")) {
            String bounded = CONCRETE.replace("(n/2);", "(n/2) \"01\" .. \"20\";");
            ok(version,bounded);
            bad(version,bounded.replace("20.00-9", "21.00-9"),"FORMAT_BOUNDS");
            bad(version,bounded.replace("01.20-0", "00.20-0"),"FORMAT_BOUNDS");
            bad(version,bounded.replace("\"01\" .. \"20\"", "\"20\" .. \"01\""),"FORMAT_BOUNDS");
            bad(version,bounded.replace("\"01\" .. \"20\"", "\"1\" .. \"20\""),"FORMAT_BOUNDS");
        }
    }

    @Test void noOidCannotBeExtendedIntoAnArbitraryOidDomain() {
        bad("2.4","DOMAIN D EXTENDS INTERLIS.NOOID = OID TEXT*36;", "OID_EXTENSION");
        bad("2.4","DOMAIN D (ABSTRACT) EXTENDS INTERLIS.NOOID = OID ANY;", "OID_EXTENSION");
        for (String version : List.of("2.3","2.4")) {
            ok(version,"DOMAIN D EXTENDS INTERLIS.ANYOID = OID TEXT*36;");
            bad(version,"DOMAIN D EXTENDS INTERLIS.UUIDOID = OID TEXT*36;", "OID_EXTENSION");
        }
    }

    @Test void cyclicOidDomainIsRejectedWithoutAnInternalFailure() {
        for (String version : List.of("2.3","2.4"))
            bad(version,"DOMAIN D (ABSTRACT) EXTENDS D = OID ANY;", "DOMAIN_TYPE_CYCLE");
    }

}
