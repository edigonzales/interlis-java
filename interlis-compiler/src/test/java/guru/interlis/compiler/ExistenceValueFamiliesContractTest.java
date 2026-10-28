package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.compiler.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExistenceValueFamiliesContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String text(String version, String body) {
        return "INTERLIS " + version + "; MODEL M AT \"https://example.org\" VERSION \"1\" = " + body + " END M.";
    }
    private static CompilationOutcome compile(String version, String body) {
        var source = CompilerTestSupport.source("memory:/values.ili", text(version, body));
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"), Map.of(source.id(),source), List.of(), CompilerOptions.defaults()), TOKEN);
    }
    private static CompilationOutcome.Succeeded ok(String version, String body) {
        var result = compile(version, body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> body + "\n" + (result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics() : result));
    }
    private static CompilationOutcome.Rejected bad(String version, String body) {
        return assertInstanceOf(CompilationOutcome.Rejected.class, compile(version, body), body);
    }
    private static String existence(String definitions, String source, String target) {
        return "TOPIC T = " + definitions + " CLASS A = value: " + target + "; END A; CLASS B = value: " + source
                + "; EXISTENCE CONSTRAINT value REQUIRED IN A:value; END B; END T;";
    }
    private static TypeDescriptor domain(SemanticGraph graph, String name) {
        var domain = graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast)
                .filter(s -> s.name().value().equals(name)).findFirst().orElseThrow();
        return graph.type(domain.typeId()).orElseThrow();
    }

    @Test void inheritedEnumerationRetainsOrderOriginFinalityAndLeafSemantics() {
        for (String version : List.of("2.3", "2.4")) {
            String defs = "DOMAIN Base = (red, green, blue) ORDERED; "
                    + "More EXTENDS Base = (red(dark, light: FINAL), yellow); Last EXTENDS More = (blue(pale));";
            var graph = ok(version, existence(defs, "Last", "Base")).schema().semanticGraph();
            var enumeration = assertInstanceOf(EnumerationType.class, domain(graph, "Last"));
            assertEquals(List.of("red", "red.dark", "red.light", "green", "blue", "blue.pale", "yellow"),
                    enumeration.values().stream().map(EnumerationValue::path).toList());
            assertEquals(List.of(0,1,2,3,4,5,6), enumeration.values().stream().map(EnumerationValue::order).toList());
            assertTrue(enumeration.baseType().isPresent());
            var relations = new guru.interlis.model.query.TypeRelations(graph);
            assertEquals(RelationDecision.ALLOWED, relations.canAssign(new TypeRef(graph.snapshotId(),enumeration.id()),
                    new TypeRef(graph.snapshotId(),domain(graph,"Base").id()), AssignmentContext.FUNCTION_ARGUMENT).known().orElseThrow().decision());
            assertTrue(enumeration.ordered()); assertEquals(Set.of("red"), enumeration.finalSubtrees());
            assertEquals(((EnumerationType) domain(graph,"Base")).values().getFirst().range(), enumeration.values().getFirst().range());
            ok(version, "TOPIC T = " + defs + " CLASS A = value: Last; MANDATORY CONSTRAINT value == #green; END A; END T;");
            bad(version, "TOPIC T = " + defs + " CLASS A = value: Last; MANDATORY CONSTRAINT value == #red; END A; END T;");
        }
    }

    @Test void enumerationExtensionRejectsClosedListsCyclesAndRepeatedLeaves() {
        for (String version : List.of("2.3", "2.4")) {
            for (String defs : List.of("Base = (a,b: FINAL); More EXTENDS Base = (c);",
                    "Base = (a(x,y: FINAL),b); More EXTENDS Base = (a(z));",
                    "Base = (a,b) CIRCULAR; More EXTENDS Base = (c);",
                    "Base = (a,b); More EXTENDS Base = (a);", "Base EXTENDS Base = (a,b);",
                    "Base = (a,b); More EXTENDS Base = (missing.child(x));", "Base = (a,b); More EXTENDS Base = (a.new);"))
                bad(version, "TOPIC T = DOMAIN " + defs + " END T;");
            ok(version, "TOPIC T = DOMAIN Base = (a,b: FINAL); More EXTENDS Base = (a(x,y)); END T;");
            ok(version, "TOPIC T = DOMAIN Base = (a(x,y),b); More EXTENDS Base = (a(FINAL)); END T;");
        }
    }

    @Test void inlineAttributeEnumerationExtensionUsesTheSameEffectiveTree() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = CLASS A = value: (a,b) ORDERED; END A; CLASS B EXTENDS A = "
                    + "value (EXTENDED): (a(x,y)); MANDATORY CONSTRAINT value == #b; END B; END T;";
            var graph = ok(version, body).schema().semanticGraph();
            assertTrue(graph.types().stream().filter(EnumerationType.class::isInstance).map(EnumerationType.class::cast)
                    .anyMatch(t -> t.values().stream().map(EnumerationValue::path).toList().equals(List.of("a","a.x","a.y","b"))));
            bad(version, body.replace("(a,b) ORDERED", "(a,b: FINAL) ORDERED").replace("(a(x,y))", "(c)"));
        }
    }

    @Test void oidCarriersAndExtensionRulesRemainTypedInSchema() {
        for (String version : List.of("2.3", "2.4")) {
            var graph = ok(version, existence("DOMAIN Id = OID TEXT*36;", "Id", "Id")).schema().semanticGraph();
            var oid = assertInstanceOf(OidType.class, domain(graph,"Id"));
            var carrier = assertInstanceOf(TextType.class, graph.type(oid.valueType().orElseThrow()).orElseThrow());
            assertEquals(36, carrier.maxLength().orElseThrow());
            ok(version, existence("", "OID 0..999", "OID 0..10"));
            ok(version, existence("", "OID TEXT*30", "OID TEXT*10"));
            ok(version, existence("", "OID ANY", "OID TEXT*10"));
            ok(version, existence("", "INTERLIS.UUIDOID", "INTERLIS.UUIDOID"));
            bad(version, existence("", "INTERLIS.UUIDOID", "UUIDOID"));
            ok(version, existence("DOMAIN UUIDOID = OID TEXT*36;", "UUIDOID", "UUIDOID"));
            bad(version, "TOPIC T = DOMAIN Id EXTENDS INTERLIS.ANYOID = TEXT*10; END T;");
            bad(version, "TOPIC T = DOMAIN Id EXTENDS Missing = OID TEXT*10; END T;");
            bad(version, "TOPIC T = DOMAIN Base = OID ANY; Id EXTENDS Missing.Base = OID TEXT*10; END T;");
            ok(version, existence("", "INTERLIS.I32OID", "INTERLIS.ANYOID"));
            ok(version, "TOPIC T = DOMAIN Id EXTENDS INTERLIS.ANYOID = OID TEXT*10; END T;");
            bad(version, "TOPIC T = DOMAIN Id EXTENDS INTERLIS.I32OID = OID 0..10; END T;");
            bad(version, existence("", "OID 0..10", "OID TEXT*10"));
            bad(version, "TOPIC T = DOMAIN Id = OID TEXT*36; Narrow EXTENDS Id = OID TEXT*10; END T;");
            ok(version, "TOPIC T = DOMAIN Id (ABSTRACT) = OID ANY; Concrete EXTENDS Id = OID TEXT*10; END T;");
        }
    }

    @Test void collectionExistencePreservesWholeValuesOrderAndElementIdentity() {
        for (String version : List.of("2.3", "2.4")) {
            String defs = "STRUCTURE S = code: TEXT; END S; STRUCTURE Child EXTENDS S = number: 0..10; END Child; STRUCTURE Other = code: TEXT; END Other;";
            for (String kind : List.of("BAG", "LIST")) {
                var graph = ok(version, existence(defs, kind + " {0..10} OF Child", kind + " {1..2} OF S")).schema().semanticGraph();
                var constraint = graph.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast)
                        .map(ConstraintSymbol::definition).filter(ExistenceConstraint.class::isInstance).map(ExistenceConstraint.class::cast).findFirst().orElseThrow();
                var type = assertInstanceOf(CollectionType.class, graph.type(graph.expression(constraint.source()).orElseThrow().typeId()).orElseThrow());
                assertEquals(java.math.BigInteger.ZERO, type.cardinality().lower());
                assertInstanceOf(StructureValueType.class, graph.type(type.elementType()).orElseThrow());
                bad(version, existence(defs, kind + " OF Other", kind + " OF S"));
            }
            bad(version, existence(defs,"BAG OF S","LIST OF S"));
            bad(version, existence(defs,"BAG OF S","S"));
            bad(version, existence(defs,"BAG OF S","BAG OF S").replace("value REQUIRED", "value->code REQUIRED"));
        }
        ok("2.4", existence("", "LIST {0..9} OF 0..999", "LIST {1..2} OF 0..10"));
        bad("2.4", existence("", "LIST OF TEXT", "LIST OF 0..10"));
    }

    @Test void formatLayoutBindsStructureAttributesAndDomainsWithRenameRanges() {
        for (String version : List.of("2.3", "2.4")) {
            String defs = "STRUCTURE S = major: 0..99; minor: 0..9; END S; DOMAIN Code = FORMAT BASED ON S (major/2 \"-\" minor); "
                    + "Small = FORMAT Code \"01-0\" .. \"20-9\";";
            String body = existence(defs,"Code","Small");
            var result = ok(version,body); var graph = result.schema().semanticGraph();
            var code = assertInstanceOf(FormattedType.class, domain(graph,"Code"));
            var small = assertInstanceOf(FormattedType.class, domain(graph,"Small"));
            assertEquals(3,code.parts().size()); assertEquals(code.parts(), small.parts());
            assertTrue(code.structure().isPresent()); assertTrue(small.baseDomain().isPresent());
            assertEquals(java.math.BigInteger.TWO,code.parts().getFirst().width().orElseThrow());
            var source = CompilerTestSupport.source("memory:/values.ili", text(version,body));
            int offset = source.text().indexOf("major/2");
            var query = LanguageQueries.create();
            var handle = query.symbolAt(result.analysis(),source.range(offset,offset),TOKEN).known().orElseThrow();
            assertEquals(code.parts().getFirst().attribute().orElseThrow(),handle.symbol());
            var edits = query.planRename(result.analysis(),new RenameRequest(handle,"primary",result.analysis().sourceRevisions()),TOKEN);
            assertTrue(edits.complete());
            assertTrue(edits.edits().stream().flatMap(e -> e.edits().stream()).anyMatch(e -> e.range().equals(source.range(offset,offset+5))));
            bad(version,body.replace("major/2", "missing/2"));
            bad(version,body.replace("major: 0..99", "major: TEXT"));
            bad(version,body.replace("major/2", "major/0"));
            bad(version,body.replace("\"20-9\"", "\"100-9\""));
            bad(version,body.replace("\"01-0\"", "\"21-0\""));
        }
    }

    @Test void nestedAndInheritedFormatsPreserveBoundPrefixes() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = STRUCTURE S = number: 0..99; END S; DOMAIN Code = FORMAT BASED ON S (number/2); "
                    + "STRUCTURE Container = nested: S; extra: 0..9; END Container; "
                    + "DOMAIN Pair = FORMAT BASED ON Container (nested/Code \"-\" extra); "
                    + "STRUCTURE Extended EXTENDS S = suffix: 0..9; END Extended; "
                    + "DOMAIN ExtendedCode EXTENDS Code = FORMAT BASED ON Extended (INHERITANCE \"-\" suffix); END T;";
            var graph = ok(version,body).schema().semanticGraph();
            var pair = (FormattedType) domain(graph,"Pair"); assertTrue(pair.parts().getFirst().format().isPresent());
            var extension = (FormattedType) domain(graph,"ExtendedCode");
            assertEquals(((FormattedType)domain(graph,"Code")).parts().getFirst(), extension.parts().getFirst());
            assertEquals(3,extension.parts().size());
            bad(version,body.replace("nested: S", "nested: BAG OF S"));
            bad(version,body.replace("nested/Code", "nested/Missing"));
            bad(version,body.replace("INHERITANCE", ""));
        }
    }

    @Test void predefinedFormatsAndInheritedBoundsUseExplicitIdentity() {
        for (String version : List.of("2.3", "2.4")) {
            ok(version,existence("", "FORMAT INTERLIS.XMLDate \"2010-01-01\" .. \"2110-12-31\"", "INTERLIS.XMLDate"));
            bad(version,existence("", "INTERLIS.XMLDate", "INTERLIS.XMLTime"));
            String body = "TOPIC T = STRUCTURE S = number: 0..99; END S; DOMAIN Code = FORMAT BASED ON S (number) \"10\" .. \"30\"; "
                    + "Narrow EXTENDS Code = \"12\" .. \"20\"; END T;";
            ok(version,body); bad(version,body.replace("\"12\"", "\"09\""));
            String chain = "TOPIC T = STRUCTURE S = number: 0..99; END S; DOMAIN Code = FORMAT BASED ON S (number) \"10\" .. \"30\"; "
                    + "STRUCTURE More EXTENDS S = extra: 0..9; END More; DOMAIN Pair EXTENDS Code = FORMAT BASED ON More (INHERITANCE \"-\" extra); "
                    + "Narrow EXTENDS Pair = \"12-1\" .. \"20-1\"; END T;";
            ok(version,chain); bad(version,chain.replace("\"12-1\"", "\"09-1\""));
        }
        ok("2.4",existence("", "DATE", "INTERLIS.XMLDate"));
    }
}
