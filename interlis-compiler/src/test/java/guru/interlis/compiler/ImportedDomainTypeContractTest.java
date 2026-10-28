package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportedDomainTypeContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version, String name, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL " + name + " AT \"https://example.org\" VERSION \"1\" = " + body + " END " + name + ".";
    }
    private static CompilationRequest request(String version, String definitions, String consumer, boolean baseFirst) {
        return request(version, definitions, consumer, baseFirst, 1);
    }
    private static CompilationRequest request(String version, String definitions, String consumer, boolean baseFirst, long revision) {
        var base = new SourceText(new SourceId("memory:/" + (baseFirst ? "a" : "z") + "-base.ili"), new SourceRevision(revision), model(version, "F", definitions));
        var source = CompilerTestSupport.source("memory:/" + (baseFirst ? "z" : "a") + "-importer.ili", model(version, "M", "IMPORTS F; " + consumer));
        var binding = CompilerTestSupport.binding(CompilerTestSupport.parse(source), "F", base, CompilerTestSupport.parse(base));
        return CompilerTestSupport.request(List.of("M"), Map.of(base.id(), base, source.id(), source), List.of(binding), CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String version, String definitions, String consumer, boolean baseFirst) {
        var result = CompilerEngines.create().compile(request(version, definitions, consumer, baseFirst), TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result, () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
    }
    private static void bad(String version, String definitions, String consumer, boolean baseFirst, String reason) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request(version, definitions, consumer, baseFirst), TOKEN));
        assertTrue(result.analysis().diagnostics().toString().contains(reason), () -> result.analysis().diagnostics().toString());
        assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
    }
    private static AttributeSymbol attribute(SemanticGraph graph, String owner, String name) {
        return graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(a -> a.name().value().equals(name) && graph.symbol(a.owner().orElseThrow()).orElseThrow().name().value().equals(owner)).findFirst().orElseThrow();
    }
    private static final String BASE = "DOMAIN Flag = BOOLEAN; TOPIC T = STRUCTURE S = flag: F.Flag; END S; END T;";
    private static final String CHILD = "TOPIC T = STRUCTURE Child EXTENDS F.T.S = flag (EXTENDED): MANDATORY; MANDATORY CONSTRAINT flag == #true; END Child; END T;";

    @Test void mandatoryAliasHasTheSamePublicTypeBeforeAndAfterFileReordering() {
        for (String version : List.of("2.3", "2.4")) for (boolean order : List.of(true, false)) {
            var result = ok(version, BASE, CHILD, order); var graph = result.schema().semanticGraph();
            var child = attribute(graph, "Child", "flag"); var base = attribute(graph, "S", "flag");
            assertEquals(base.declaredType(), child.declaredType());
            var alias = assertInstanceOf(DomainReferenceType.class, graph.type(child.declaredType()).orElseThrow());
            var domain = assertInstanceOf(DomainSymbol.class, graph.symbol(alias.targetDomain()).orElseThrow());
            assertEquals("Flag", domain.name().value());
            assertInstanceOf(BooleanType.class, graph.type(domain.typeId()).orElseThrow());
            var effective = graph.effectiveType(child.id()).orElseThrow();
            assertEquals(List.of(domain.id()), effective.aliasPath()); assertEquals(domain.typeId(), effective.effectiveType());
            assertEquals(RelationDecision.ALLOWED, new TypeRelations(graph).canAssign(new TypeRef(graph.snapshotId(), child.declaredType()),
                    new TypeRef(graph.snapshotId(), domain.typeId()), AssignmentContext.FUNCTION_ARGUMENT).known().orElseThrow().decision());
            assertTrue(graph.expressions().stream().filter(PathExpression.class::isInstance).allMatch(p -> p.typeId().equals(child.declaredType())));
            bad(version, BASE.replace("Flag = BOOLEAN", "Flag = TEXT*20"), CHILD, order, "ILI-SEM-0005");
        }
    }
    @Test void functionsAndScalarOperatorsUseImportedTypesAndKeepReferencesNavigable() {
        String defs = "DOMAIN Flag = BOOLEAN; Number = 0..100; Label = TEXT*20;";
        String use = "FUNCTION truth(v: F.Flag): F.Flag; TOPIC T = CLASS C = flag: F.Flag; n: F.Number; label: F.Label; MANDATORY CONSTRAINT truth(flag) AND n > 0 AND INTERLIS.len(label) > 0; END C; END T;";
        for (String version : List.of("2.3", "2.4")) for (boolean order : List.of(true, false)) {
            var result = ok(version, defs, use, order);
            var source = result.analysis().syntaxSnapshots().values().stream().map(s -> s.source()).filter(s -> s.text().contains("MODEL M ")).findFirst().orElseThrow();
            int offset = source.text().indexOf("label: F.Label") + "label: F.".length();
            var ref = LanguageQueries.create().symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().orElseThrow();
            var label = attribute(result.schema().semanticGraph(), "C", "label");
            assertEquals(((DomainReferenceType)result.schema().semanticGraph().type(label.declaredType()).orElseThrow()).targetDomain(), ref.symbol());
            bad(version, defs, use.replace("truth(flag)", "truth(n)"), order, "ILI-SEM-0005");
            String imported = use.replace("FUNCTION truth(v: F.Flag): F.Flag; ", "").replace("truth(flag)", "F.truth(flag)");
            String withFunction = defs + " FUNCTION truth(v: F.Flag): F.Flag;";
            ok(version, withFunction, imported, order);
            bad(version, withFunction, imported.replace("F.truth(flag)", "F.truth(n)"), order, "ILI-SEM-0005");
            bad(version, defs, use.replace("n > 0", "label > 0"), order, "ILI-SEM-0005");
        }
    }
    @Test void importedStructuresAndIndicesShareTheConstraintPathBinder() {
        String defs = "DOMAIN Number = 0..100; STRUCTURE Item = code: F.Number; END Item;";
        String use = "TOPIC T = CLASS Required = code: F.Number; END Required; CLASS C = detail: F.Item; items: LIST {1..3} OF F.Item; MANDATORY CONSTRAINT items[1]->code > 0; UNIQUE detail->code; UNIQUE (LOCAL) items: code; EXISTENCE CONSTRAINT detail->code REQUIRED IN Required: code; END C; END T;";
        for (String version : List.of("2.3", "2.4")) for (boolean order : List.of(true, false)) {
            var graph = ok(version, defs, use, order).schema().semanticGraph();
            assertInstanceOf(StructureValueType.class, graph.type(attribute(graph, "C", "detail").declaredType()).orElseThrow());
            bad(version, defs, use.replace("items[1]", "items[4]"), order, "ILI-SEM-0005");
            bad(version, defs.replace("Number = 0..100", "Number = TEXT*20"), use, order, "ILI-SEM-0005");
        }
    }
    @Test void importedEnumerationBaseIsMergedBeforeExpressions() {
        for (String version : List.of("2.3", "2.4")) for (boolean order : List.of(true, false)) {
            String defs = "DOMAIN Color = (red, green) ORDERED;";
            String use = "DOMAIN More EXTENDS F.Color = (blue); TOPIC T = CLASS C = color: More; MANDATORY CONSTRAINT color == #red; END C; END T;";
            var graph = ok(version, defs, use, order).schema().semanticGraph();
            var effective = graph.effectiveType(attribute(graph, "C", "color").id()).orElseThrow();
            var type = assertInstanceOf(EnumerationType.class, graph.type(effective.effectiveType()).orElseThrow());
            assertEquals(List.of("red", "green", "blue"), type.values().stream().map(EnumerationValue::path).toList());
            assertTrue(type.ordered());
            bad(version, defs, use.replace("#red", "#missing"), order, "ILI-SEM-0005");
        }
    }
    @Test void formattedAliasesResolveTheirStructureComponentsBeforeComparison() {
        String defs = "DOMAIN Number = 0..99; STRUCTURE Parts = n: F.Number; END Parts; DOMAIN Code = FORMAT BASED ON Parts (n/2) \"00\" .. \"99\";";
        String use = "TOPIC T = CLASS C = code: F.Code; MANDATORY CONSTRAINT code == \"05\"; END C; END T;";
        for (String version : List.of("2.3", "2.4")) for (boolean order : List.of(true, false)) {
            ok(version, defs, use, order);
            bad(version, defs, use.replace("\"05\"", "\"5\""), order, "ILI-SEM-0005");
        }
    }
    @Test void wrongQualificationsNeverBindAnAlreadyBuiltSameNamedDomain() {
        for (String version : List.of("2.3", "2.4")) for (boolean order : List.of(true, false)) {
            bad(version, "DOMAIN Flag = BOOLEAN;", "TOPIC T = CLASS C = flag: Wrong.Flag; END C; END T;", order, "unresolved");
            bad(version, "DOMAIN Flag = BOOLEAN;", "TOPIC T = CLASS C = flag: Flag; END C; END T;", order, "unresolved");
            ok(version, "DOMAIN Flag = BOOLEAN;", "TOPIC T = DOMAIN Flag = TEXT*10; CLASS C = flag: Flag; MANDATORY CONSTRAINT INTERLIS.len(flag) > 0; END C; END T;", order);
        }
    }
    @Test void singleElementEnumsAndComposedUnitsKeepTheirDeclaredKind() {
        for (String version : List.of("2.3", "2.4")) for (boolean order : List.of(true, false)) {
            String defs = "UNIT BaseUnit (ABSTRACT); Wrapped (ABSTRACT) = (BaseUnit); DOMAIN One = (one);";
            var graph = ok(version, defs, "TOPIC T = CLASS C = value: F.One; MANDATORY CONSTRAINT value == #one; END C; END T;", order).analysis().semanticGraph();
            assertTrue(graph.symbols().stream().anyMatch(s -> s instanceof UnitSymbol && s.name().value().equals("Wrapped")));
            assertTrue(graph.symbols().stream().anyMatch(s -> s instanceof DomainSymbol && s.name().value().equals("One")));
        }
    }
    @Test void domainDependencyCyclesAreRejectedWithoutInternalFailures() {
        for (String version : List.of("2.3", "2.4")) for (boolean order : List.of(true, false)) {
            bad(version, "DOMAIN A EXTENDS B = (a); B EXTENDS A = (b);", "TOPIC T = CLASS C = value: F.A; END C; END T;", order, "DOMAIN_TYPE_CYCLE");
            bad(version, "DOMAIN A = FORMAT B \"00\" .. \"99\"; B = FORMAT A \"00\" .. \"99\";", "TOPIC T = CLASS C = value: F.A; END C; END T;", order, "DOMAIN_TYPE_CYCLE");
        }
    }
    @Test void earlyImportedTypesKeepTheirDomainConstraints() {
        String defs = "DOMAIN Number = 0..100 CONSTRAINTS Positive: THIS > 0; FUNCTION identity(v: F.Number): F.Number;";
        String use = "TOPIC T = CLASS C = n: F.Number; MANDATORY CONSTRAINT F.identity(n) > 0; END C; END T;";
        for (boolean order : List.of(true, false)) {
            var graph = ok("2.4", defs, use, order).schema().semanticGraph();
            assertTrue(graph.symbols().stream().filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast)
                    .anyMatch(c -> c.name().value().equals("Positive") && c.definition() instanceof DomainConstraint));
            bad("2.4", defs.replace("THIS > 0", "INTERLIS.len(THIS) > 0"), use, order, "ILI-SEM-0005");
        }
    }
    @Test void editedImportedDomainMatchesFreshAnalysisAndPreservesPreviousSnapshot() {
        var engine = CompilerEngines.create();
        var first = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(request("2.4", BASE, CHILD, false), TOKEN));
        var original = first.schema().semanticGraph(); var previous = first.analysis();
        long revision = 1;
        for (String type : List.of("TEXT*20", "BOOLEAN", "0..100", "BOOLEAN")) {
            var next = request("2.4", BASE.replace("Flag = BOOLEAN", "Flag = " + type), CHILD, false, ++revision);
            var reused = engine.analyze(next, new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), TOKEN);
            var fresh = engine.analyze(next, TOKEN);
            assertEquals(fresh.diagnostics().stream().map(d -> List.of(d.code(), d.arguments(), d.primaryRange())).toList(),
                    reused.diagnostics().stream().map(d -> List.of(d.code(), d.arguments(), d.primaryRange())).toList());
            assertEquals(type.equals("BOOLEAN"), reused.diagnostics().isEmpty());
            previous = reused;
        }
        assertInstanceOf(BooleanType.class, original.type(original.effectiveType(attribute(original, "Child", "flag").id()).orElseThrow().effectiveType()).orElseThrow());
    }
}
