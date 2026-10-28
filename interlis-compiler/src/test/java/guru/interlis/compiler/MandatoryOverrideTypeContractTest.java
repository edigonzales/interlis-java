package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MandatoryOverrideTypeContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.";
    }
    private static CompilationOutcome compile(String version, String body) {
        var source = CompilerTestSupport.source("memory:/mandatory.ili", model(version, body));
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults()), TOKEN);
    }
    private static CompilationOutcome.Succeeded ok(String version, String body) {
        var result = compile(version, body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> body + "\n" + (result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics() : result));
    }
    private static void bad(String version, String body, String reason) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, compile(version, body), body);
        assertTrue(result.analysis().diagnostics().toString().contains(reason), () -> result.analysis().diagnostics().toString());
        assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
    }
    private static AttributeSymbol attribute(SemanticGraph graph, String owner, String name) {
        return graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(a -> a.name().value().equals(name) && graph.symbol(a.owner().orElseThrow()).orElseThrow().name().value().equals(owner)).findFirst().orElseThrow();
    }
    private static String referenceBody() {
        return "TOPIC A = OID AS INTERLIS.UUIDOID; CLASS Target = flag: BOOLEAN; END Target; END A; "
                + "TOPIC Base = DEPENDS ON A; STRUCTURE S = ref: REFERENCE TO (EXTERNAL) F.A.Target; END S; END Base; "
                + "TOPIC T = DEPENDS ON A; STRUCTURE Child EXTENDS F.Base.S = ref (EXTENDED): MANDATORY; "
                + "MANDATORY CONSTRAINT ref->flag; END Child; END T;";
    }
    @Test void referenceContinuationUsesInheritedTypeBeforeConstraintBinding() {
        for (String version : List.of("2.3", "2.4")) {
            var result = ok(version, referenceBody()); var graph = result.schema().semanticGraph();
            var base = attribute(graph, "S", "ref"); var child = attribute(graph, "Child", "ref");
            assertEquals(base.declaredType(), child.declaredType());
            assertEquals(base.id(), child.overrides().orElseThrow());
            assertTrue(child.mandatory());
            var reference = assertInstanceOf(ReferenceType.class, graph.type(child.declaredType()).orElseThrow());
            assertTrue(reference.external());
            var path = graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast).findFirst().orElseThrow();
            assertInstanceOf(BooleanType.class, graph.type(path.typeId()).orElseThrow());
            assertEquals(child.id(), ((PathStep.AttributeStep)path.path().steps().getFirst()).target());
            assertEquals(attribute(graph, "Target", "flag").id(), ((PathStep.AttributeStep)path.path().steps().getLast()).target());
            var source = CompilerTestSupport.source("memory:/mandatory.ili", model(version, referenceBody()));
            int offset = source.text().indexOf("ref->flag");
            assertEquals(child.id(), LanguageQueries.create().symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().orElseThrow().symbol());
            bad(version, referenceBody().replace("ref->flag;", "ref->missing;"), "unresolved");
            bad(version, referenceBody().replace("ref->flag;", "ref->flag > 0;"), "ILI-SEM-0005");
        }
    }
    @Test void multiLevelOverridesKeepScalarAndDomainTypesInFunctionsAndOperators() {
        for (String version : List.of("2.3", "2.4")) for (String kind : List.of("CLASS", "STRUCTURE")) {
            String body = "DOMAIN Label = TEXT*20; FUNCTION truth(v: BOOLEAN): BOOLEAN; TOPIC T = " + kind + " Base = flag: BOOLEAN; label: F.Label; number: 0..100; END Base; "
                    + kind + " Middle EXTENDS Base = flag (EXTENDED): MANDATORY; label (EXTENDED): MANDATORY; number (EXTENDED): MANDATORY; END Middle; "
                    + kind + " Child EXTENDS Middle = flag (EXTENDED): MANDATORY; label (EXTENDED): MANDATORY; number (EXTENDED): MANDATORY; "
                    + "MANDATORY CONSTRAINT truth(flag) AND INTERLIS.len(label) > 0 AND number > 2; END Child; END T;";
            var graph = ok(version, body).schema().semanticGraph();
            for (String name : List.of("flag", "label", "number")) {
                assertEquals(attribute(graph, "Base", name).declaredType(), attribute(graph, "Child", name).declaredType());
                assertEquals(attribute(graph, "Middle", name).id(), attribute(graph, "Child", name).overrides().orElseThrow());
            }
            bad(version, body.replace("truth(flag)", "truth(number)"), "ILI-SEM-0005");
        }
    }
    @Test void inheritedStructureAndListIndicesWorkForAllConstraintBinders() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = STRUCTURE Item = code: 0..100; END Item; "
                    + "CLASS Required = code: 0..100; END Required; "
                    + "CLASS Base = detail: Item; items: LIST {1..3} OF Item; END Base; "
                    + "CLASS Child EXTENDS Base = detail (EXTENDED): MANDATORY; items (EXTENDED): MANDATORY; "
                    + "MANDATORY CONSTRAINT items[FIRST]->code > 0; UNIQUE detail->code; "
                    + "UNIQUE (LOCAL) items: code; EXISTENCE CONSTRAINT detail->code REQUIRED IN Required: code; END Child; END T;";
            var graph = ok(version, body).schema().semanticGraph();
            assertEquals(attribute(graph, "Base", "items").declaredType(), attribute(graph, "Child", "items").declaredType());
            assertTrue(graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast)
                    .flatMap(p -> p.path().steps().stream()).anyMatch(PathStep.IndexStep.class::isInstance));
            bad(version, body.replace("items[FIRST]", "items[4]"), "ILI-SEM-0005");
            bad(version, body.replace("UNIQUE detail->code", "UNIQUE detail->missing"), "unresolved");
        }
    }
    @Test void extendedClassesAndExternalConstraintsSeeTheSameTypes() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC Base = CLASS C = flag: BOOLEAN; END C; END Base; "
                    + "TOPIC T EXTENDS Base = CLASS C (EXTENDED) = flag (EXTENDED): MANDATORY; "
                    + "MANDATORY CONSTRAINT flag; END C; CONSTRAINTS OF C = MANDATORY CONSTRAINT flag; END; END T;";
            ok(version, body);
            bad(version, body.replace("CONSTRAINT flag;", "CONSTRAINT flag > 0;"), "ILI-SEM-0005");
        }
    }
    @Test void associationAttributesRetainTheirInheritedType() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = END A; CLASS B = END B; ASSOCIATION Link = a -- A; b -- B; flag: BOOLEAN; END Link; "
                    + "ASSOCIATION Child EXTENDS Link = flag (EXTENDED): MANDATORY; MANDATORY CONSTRAINT flag; END Child; END T;";
            var graph = ok(version, body).schema().semanticGraph();
            assertEquals(attribute(graph, "Link", "flag").declaredType(), attribute(graph, "Child", "flag").declaredType());
            bad(version, body.replace("CONSTRAINT flag;", "CONSTRAINT flag > 0;"), "ILI-SEM-0005");
        }
    }
    @Test void anExplicitTypeMustNotBeMistakenForMandatoryOnly() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = CLASS Base = value: BOOLEAN; END Base; CLASS Child EXTENDS Base = value (EXTENDED): NUMERIC; END Child; END T;";
            bad(version, body, "ATTRIBUTE_OVERRIDE_TYPE");
            bad(version, body.replace(": NUMERIC", ": MANDATORY NUMERIC"), "ATTRIBUTE_OVERRIDE_TYPE");
        }
    }
    @Test void classExtensionOfStructureRetainsInheritedMembersAndTypes() {
        for (String version : List.of("2.3", "2.4")) {
            var graph = ok(version, "TOPIC T = STRUCTURE S = flag: BOOLEAN; END S; CLASS Child EXTENDS S = flag (EXTENDED): MANDATORY; MANDATORY CONSTRAINT flag; END Child; END T;").schema().semanticGraph();
            var child = graph.symbols().stream().filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast).findFirst().orElseThrow();
            assertInstanceOf(StructureSymbol.class, graph.symbol(child.baseType().orElseThrow()).orElseThrow());
            assertEquals(attribute(graph, "S", "flag").declaredType(), attribute(graph, "Child", "flag").declaredType());
        }
    }
    @Test void aMissingFunctionTypeIsNotAnInheritedAttribute() {
        for (String version : List.of("2.3", "2.4")) {
            bad(version, "FUNCTION f(v: MANDATORY): BOOLEAN;", "INHERITED_TYPE_CONTEXT");
            bad(version, "FUNCTION f(v: BOOLEAN): MANDATORY;", "INHERITED_TYPE_CONTEXT");
        }
    }
    @Test void predefinedBaseNameRetainsItsKeywordSegment() {
        var source = CompilerTestSupport.source("memory:/refsystem.ili", model("2.3", "TOPIC T = CLASS C EXTENDS INTERLIS.REFSYSTEM = END C; END T;"));
        var topic = (guru.interlis.syntax.TopicSyntax) CompilerTestSupport.parse(source).root().models().getFirst().members().getFirst();
        var declaration = (guru.interlis.syntax.ClassDeclarationSyntax) topic.members().getFirst();
        assertEquals("INTERLIS.REFSYSTEM", declaration.baseType().orElseThrow().text());
    }
    @Test void missingAndCyclicInheritedTypesAreLanguageErrors() {
        for (String version : List.of("2.3", "2.4")) {
            bad(version, "TOPIC T = CLASS C = value: MANDATORY; END C; END T;", "ATTRIBUTE_INHERITED_TYPE");
            bad(version, "TOPIC T = CLASS Base = value: BOOLEAN; END Base; CLASS C EXTENDS Base = value: MANDATORY; END C; END T;", "OVERRIDE");
            bad(version, "TOPIC T = CLASS C = value (EXTENDED): MANDATORY; END C; END T;", "ATTRIBUTE_INHERITED_TYPE");
            bad(version, "TOPIC T = STRUCTURE S = other: BOOLEAN; END S; STRUCTURE C EXTENDS S = value (EXTENDED): MANDATORY; END C; END T;", "ATTRIBUTE_INHERITED_TYPE");
            bad(version, "TOPIC T = STRUCTURE S EXTENDS C = value (EXTENDED): MANDATORY; END S; STRUCTURE C EXTENDS S = value (EXTENDED): MANDATORY; END C; END T;", "cycle");
        }
    }
    @Test void importedInheritedPathsDoNotDependOnSourceFileSorting() {
        for (String version : List.of("2.3", "2.4")) for (boolean baseFirst : List.of(true, false)) {
            var base = CompilerTestSupport.source("memory:/" + (baseFirst ? "a" : "z") + ".ili", model(version,
                    "TOPIC A = OID AS INTERLIS.UUIDOID; CLASS Target = flag: BOOLEAN; END Target; END A; "
                    + "TOPIC T = DEPENDS ON A; STRUCTURE S = ref: REFERENCE TO (EXTERNAL) F.A.Target; END S; END T;"));
            var source = CompilerTestSupport.source("memory:/" + (baseFirst ? "z" : "a") + ".ili", model(version,
                    "IMPORTS F; TOPIC T = DEPENDS ON F.A; STRUCTURE Child EXTENDS F.T.S = ref (EXTENDED): MANDATORY; MANDATORY CONSTRAINT ref->flag; END Child; END T;")
                    .replace("MODEL F AT", "MODEL M AT").replace("END F.", "END M."));
            var binding = CompilerTestSupport.binding(CompilerTestSupport.parse(source), "F", base, CompilerTestSupport.parse(base));
            var result = CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source, base.id(), base), List.of(binding), CompilerOptions.defaults()), TOKEN);
            assertInstanceOf(CompilationOutcome.Succeeded.class, result, () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
        }
    }
}
