package guru.interlis.compiler;

import guru.interlis.compiler.query.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** K2 / C1: runtime parameter identities and class-valued functions, including consumers. */
class ParameterClassFunctionContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL M AT \"x\" VERSION \"1\" = " + body + " END M.";
    }
    private static CompilationRequest request(String text) {
        var source = CompilerTestSupport.source("memory:/parameter.ili", text);
        var syntax = CompilerTestSupport.parse(source);
        var bindings = new ArrayList<ImportBinding>();
        for (var model : syntax.root().models()) for (var imported : model.imports()) {
            syntax.root().models().stream().filter(m -> m.name().equals(imported.modelName())).findFirst().ifPresent(target ->
                    bindings.add(new ImportBinding(new ImportSite(imported.range(), model.name(), imported.modelName()),
                            new ImportResolution.Resolved(new ModelLocation(source.id(), source.revision(), target.name(),
                                    Optional.of(target.modelVersion()), target.range().startOffset())))));
        }
        return CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source), bindings, CompilerOptions.defaults());
    }
    private static CompilationOutcome compile(String text) { return CompilerEngines.create().compile(request(text), TOKEN); }
    private static CompilationOutcome.Succeeded ok(String text) {
        var outcome = compile(text);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : outcome.toString());
    }
    private static void bad(String text, String reason) {
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, compile(text), text);
        assertTrue(rejected.analysis().diagnostics().toString().contains(reason), rejected.analysis().diagnostics()::toString);
        assertFalse(rejected.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
    }
    private static Symbol named(SemanticGraph graph, String name, SymbolKind kind) {
        return graph.symbols().stream().filter(s -> s.name().value().equals(name) && s.kind() == kind).findFirst().orElseThrow();
    }

    @Test void parameterNamespacesBoundTypesAndLanguageReferencesAgree() {
        for (String version : List.of("2.3", "2.4")) {
            var result = ok(model(version, "DOMAIN shared = TEXT*2; PARAMETER shared: MANDATORY BOOLEAN; other: BOOLEAN; "
                    + "TOPIC T = CLASS C = flag: BOOLEAN; MANDATORY CONSTRAINT flag == PARAMETER shared; END C; END T;"));
            var graph = result.schema().semanticGraph();
            var parameter = (ParameterSymbol) named(graph, "shared", SymbolKind.PARAMETER);
            assertTrue(parameter.mandatory());
            assertInstanceOf(BooleanType.class, graph.type(parameter.typeId()).orElseThrow());
            assertEquals(parameter.typeId(), graph.declaredType(parameter.id()).orElseThrow());
            assertEquals(parameter.typeId(), graph.effectiveType(parameter.id()).orElseThrow().effectiveType());
            var expression = graph.expressions().stream().filter(SymbolValueExpression.class::isInstance).map(SymbolValueExpression.class::cast).findFirst().orElseThrow();
            assertEquals(parameter.id(), expression.target()); assertEquals(parameter.typeId(), expression.typeId());
            var source = result.analysis().syntaxSnapshots().values().iterator().next().source();
            int offset = source.text().lastIndexOf("PARAMETER shared") + "PARAMETER ".length();
            var queries = LanguageQueries.create();
            assertEquals(parameter.id(), queries.symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().orElseThrow().symbol());
            var other = named(graph, "other", SymbolKind.PARAMETER);
            var collision = queries.planRename(result.analysis(), new RenameRequest(new SymbolRef(graph.snapshotId(), other.id()), "shared", Map.of(source.id(), source.revision())), TOKEN);
            assertFalse(collision.complete());
            var domain = named(graph, "shared", SymbolKind.DOMAIN);
            var rename = queries.planRename(result.analysis(), new RenameRequest(new SymbolRef(graph.snapshotId(), domain.id()), "other", Map.of(source.id(), source.revision())), TOKEN);
            assertTrue(rename.complete(), rename.toString());
        }
    }

    @Test void runtimeParametersHaveVersionedContractAndScopeRules() {
        String body = "PARAMETER p: BOOLEAN; TOPIC T = CLASS C = MANDATORY CONSTRAINT PARAMETER p; END C; END T;";
        bad(model("2.3", body).replace("CONTRACTED ", ""), "PARAMETER_CONTRACTED");
        ok(model("2.4", body).replace("CONTRACTED ", ""));
        for (String version : List.of("2.3", "2.4")) {
            bad(model(version, body.replace("p: BOOLEAN", "p: TEXT")), "ILI-SEM-0005");
            bad(model(version, body.replace("PARAMETER p;", "PARAMETER Missing.p;")), "ILI-SEM-0001");
            bad(model(version, body.replace("PARAMETER p: BOOLEAN;", "DOMAIN p = BOOLEAN;")), "ILI-SEM-0001");
            bad(model(version, "PARAMETER p: BOOLEAN; p: TEXT;"), "PARAMETER_NAME_UNIQUE");
            bad(model(version, "DOMAIN Coord = COORD 0..100,0..100; Shape = AREA WITH (STRAIGHTS) VERTEX Coord WITHOUT OVERLAPS > 0.01; PARAMETER p: Shape;"), "PARAMETER_AREA");
        }
    }

    @Test void classBuiltinsPreserveBoundClassAndStructureResults() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = STRUCTURE S = value: TEXT; END S; CLASS A = END A; CLASS C EXTENDS A = "
                    + "s: S; cls(TRANSIENT): CLASS RESTRICTION (A) := INTERLIS.myClass(THIS); "
                    + "str(TRANSIENT): STRUCTURE RESTRICTION (S) := INTERLIS.myClass(s); "
                    + "MANDATORY CONSTRAINT INTERLIS.isOfClass(THIS, >A) AND INTERLIS.isSubClass(INTERLIS.myClass(THIS), >A) AND INTERLIS.isOfClass(s, >S); END C; END T;";
            var graph = ok(model(version, body)).schema().semanticGraph();
            var cls = (AttributeSymbol) named(graph, "cls", SymbolKind.ATTRIBUTE);
            var call = assertInstanceOf(FunctionCallExpression.class, graph.expression(cls.derivations().getFirst()).orElseThrow());
            var actual = assertInstanceOf(ClassValueType.class, graph.type(call.typeId()).orElseThrow());
            assertFalse(actual.structureValue()); assertEquals(List.of(named(graph, "C", SymbolKind.CLASS).id()), actual.restrictions());
            assertEquals(RelationDecision.ALLOWED, new TypeRelations(graph).canAssign(new TypeRef(graph.snapshotId(), call.typeId()),
                    new TypeRef(graph.snapshotId(), cls.declaredType()), AssignmentContext.FUNCTION_ARGUMENT).known().orElseThrow().decision());
            var str = (AttributeSymbol) named(graph, "str", SymbolKind.ATTRIBUTE);
            var structureResult = graph.expression(str.derivations().getFirst()).orElseThrow();
            assertTrue(((ClassValueType)graph.type(structureResult.typeId()).orElseThrow()).structureValue());
        }
    }

    @Test void classFunctionsRejectWrongKindsArityAndObjectSets() {
        for (String version : List.of("2.3", "2.4")) for (String expression : List.of(
                "INTERLIS.isOfClass(THIS, 123)", "INTERLIS.isOfClass(>A, >A)", "INTERLIS.isOfClass(ALL, >A)",
                "INTERLIS.isOfClass(THIS)", "INTERLIS.isSubClass(THIS, >A)", "INTERLIS.isSubClass(>A, >A, >A)",
                "DEFINED(INTERLIS.myClass(123))", "DEFINED(INTERLIS.myClass(ALL))")) {
            bad(model(version, "TOPIC T = CLASS A = MANDATORY CONSTRAINT " + expression + "; END A; END T;"), "ILI-SEM-0005");
        }
    }

    @Test void classValuesUseIdentitiesAndRespectRestrictions() {
        for (String version : List.of("2.3", "2.4")) {
            String prefix = "TOPIC T = STRUCTURE S = END S; CLASS A = END A; CLASS B EXTENDS A = END B; CLASS Other = END Other; ";
            ok(model(version, prefix + "CLASS C = a(TRANSIENT): CLASS RESTRICTION (A) := >B; END C; END T;"));
            bad(model(version, prefix + "CLASS C = a(TRANSIENT): CLASS RESTRICTION (A) := >Other; END C; END T;"), "ILI-SEM-0005");
            bad(model(version, prefix + "CLASS C = a(TRANSIENT): CLASS := >S; END C; END T;"), "ILI-SEM-0005");
            bad(model(version, prefix + "CLASS C = a: CLASS RESTRICTION (S); END C; END T;"), "ILI-SEM-0001");
            bad(model(version, prefix + "CLASS C = a(TRANSIENT): CLASS := >Missing.A; END C; END T;"), "ILI-SEM-0001");
            bad(model(version, prefix + "CLASS C = a: CLASS RESTRICTION (A); END C; CLASS D EXTENDS C = a(EXTENDED): CLASS; END D; END T;"), "ATTRIBUTE_OVERRIDE_TYPE");
        }
    }

    @Test void myClassRoleAndAttributeOfKeepTheOriginalTargets() {
        for (String version : List.of("2.3", "2.4")) {
            var graph = ok(model(version, "TOPIC T = OID AS INTERLIS.STANDARDOID; CLASS A = END A; CLASS B = END B; ASSOCIATION Link = a -- A; b -- B; "
                    + "objClass(TRANSIENT): CLASS := INTERLIS.myClass(a); attrPath: ATTRIBUTE OF objClass; END Link; END T;")).schema().semanticGraph();
            var path = (AttributeSymbol) named(graph, "attrPath", SymbolKind.ATTRIBUTE);
            var descriptor = assertInstanceOf(AttributePathType.class, graph.type(path.declaredType()).orElseThrow());
            var owner = assertInstanceOf(PathExpression.class, graph.expression(((AttributePathType.ClassAttribute)descriptor.owner().orElseThrow()).path()).orElseThrow());
            assertEquals(named(graph, "objClass", SymbolKind.ATTRIBUTE).id(), ((PathStep.AttributeStep)owner.path().steps().getFirst()).target());
            bad(model(version, "TOPIC T = CLASS C = objClass: TEXT; attrPath: ATTRIBUTE OF objClass; END C; END T;"), "ATTRIBUTE_PATH_TYPE");
        }
    }

    @Test void translationsCompareParameterTypesMandatoryFlagsAndTargets() {
        for (String version : List.of("2.3", "2.4")) {
            String source = "INTERLIS " + version + "; CONTRACTED MODEL Original AT \"x\" VERSION \"1\" = DOMAIN Flag = BOOLEAN; PARAMETER p: MANDATORY Flag; q: Flag; TOPIC T = CLASS C = MANDATORY CONSTRAINT PARAMETER p; END C; END T; END Original. ";
            String target = "CONTRACTED MODEL M AT \"x\" VERSION \"1\" TRANSLATION OF Original [\"1\"] = DOMAIN Vlag = BOOLEAN; PARAMETER x: MANDATORY Vlag; y: Vlag; TOPIC U = CLASS D = MANDATORY CONSTRAINT PARAMETER x; END D; END U; END M.";
            ok(source + target);
            bad(source + target.replace("x: MANDATORY Vlag", "x: Vlag"), "TRANSLATION");
            bad(source + target.replace("PARAMETER x;", "PARAMETER y;"), "TRANSLATION_REFERENCE");
            bad(source + target.replace("y: Vlag", "y: TEXT"), "TRANSLATION");
        }
    }

    @Test void importedParametersAreIndependentOfSourceOrderAndReusedAnalysis() {
        for (String version : List.of("2.3", "2.4")) for (boolean first : List.of(true, false)) {
            var imported = CompilerTestSupport.source("memory:/" + (first ? "a" : "z") + ".ili", "INTERLIS " + version + "; CONTRACTED MODEL Imported AT \"x\" VERSION \"1\" = DOMAIN Flag = BOOLEAN; PARAMETER p: Flag; END Imported.");
            var source = CompilerTestSupport.source("memory:/" + (first ? "z" : "a") + ".ili", model(version, "IMPORTS Imported; PARAMETER p: TEXT; TOPIC T = CLASS C = MANDATORY CONSTRAINT PARAMETER Imported.p; END C; END T;"));
            var binding = CompilerTestSupport.binding(CompilerTestSupport.parse(source), "Imported", imported, CompilerTestSupport.parse(imported));
            var req = CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source, imported.id(), imported), List.of(binding), CompilerOptions.defaults());
            var engine = CompilerEngines.create();
            var result = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(req, TOKEN));
            var fresh = engine.analyze(req, TOKEN);
            var reused = engine.analyze(req, new AnalysisReuse(Optional.of(result.analysis()), result.analysis().syntaxSnapshots()), TOKEN);
            assertEquals(fresh.diagnostics(), reused.diagnostics());
            var graph = result.schema().semanticGraph();
            var value = graph.expressions().stream().filter(SymbolValueExpression.class::isInstance).map(SymbolValueExpression.class::cast).findFirst().orElseThrow();
            assertEquals("Imported", graph.symbol(graph.symbol(value.target()).orElseThrow().owner().orElseThrow()).orElseThrow().name().value());
            assertInstanceOf(DomainReferenceType.class, graph.type(value.typeId()).orElseThrow());
        }
    }
    @Test void parameterImportsRespectQualifiedUnqualifiedAndAmbiguousNames() {
        for (String version : List.of("2.3", "2.4")) {
            String prefix = "INTERLIS " + version + "; CONTRACTED MODEL A AT \"x\" VERSION \"1\" = PARAMETER p: BOOLEAN; END A. "
                    + "CONTRACTED MODEL B AT \"x\" VERSION \"1\" = PARAMETER p: TEXT; END B. ";
            String consumer = "CONTRACTED MODEL M AT \"x\" VERSION \"1\" = IMPORTS UNQUALIFIED A, B; TOPIC T = CLASS C = MANDATORY CONSTRAINT PARAMETER p; END C; END T; END M.";
            ok(prefix + consumer);
            bad(prefix + consumer.replace("UNQUALIFIED A", "A"), "ILI-SEM-0001");
            bad(prefix + consumer.replace("A, B", "A, UNQUALIFIED B"), "ILI-SEM-0002");
            bad(prefix + consumer.replace("PARAMETER p;", "PARAMETER B.p;"), "ILI-SEM-0005");
            bad(prefix + consumer.replace("IMPORTS UNQUALIFIED A, B;", "IMPORTS B;").replace("PARAMETER p;", "PARAMETER A.p;"), "ILI-SEM-0001");
        }
    }

    @Test void attributePathArgumentsAndConstantsKeepTheirBindingsInCompiledSchema() {
        for (String version : List.of("2.3", "2.4")) {
            var result = ok(model(version, "FUNCTION f(objects: OBJECTS OF ANYCLASS; path: ATTRIBUTE OF @objects): BOOLEAN; "
                    + "TOPIC T = CLASS C = label: TEXT*10; path: ATTRIBUTE; copy(TRANSIENT): ATTRIBUTE RESTRICTION (TEXT*20) := >>label; "
                    + "MANDATORY CONSTRAINT path == >>label; END C; END T;"));
            var graph = result.schema().semanticGraph();
            var function = (FunctionSymbol) named(graph, "f", SymbolKind.FUNCTION);
            var type = (AttributePathType) graph.type(function.parameters().get(1).typeId()).orElseThrow();
            assertEquals(new AttributePathType.Argument(function.id(), 0), type.owner().orElseThrow());
            var constant = graph.expressions().stream().filter(SymbolValueExpression.class::isInstance).map(SymbolValueExpression.class::cast).findFirst().orElseThrow();
            assertEquals(named(graph, "label", SymbolKind.ATTRIBUTE).id(), constant.target());
            assertInstanceOf(AttributePathType.class, graph.type(constant.typeId()).orElseThrow());
            bad(model(version, "FUNCTION f(objects: NUMERIC; path: ATTRIBUTE OF @objects): BOOLEAN;"), "ATTRIBUTE_ARGUMENT_TYPE");
            bad(model(version, "FUNCTION f(objects: OBJECTS OF ANYCLASS; path: ATTRIBUTE OF @missing): BOOLEAN;"), "ATTRIBUTE_ARGUMENT");
            bad(model(version, "FUNCTION f(a: ATTRIBUTE OF @b; b: ATTRIBUTE OF @a): BOOLEAN;"), "FUNCTION_ARGUMENT_CYCLE");
            bad(model(version, "TOPIC T = CLASS C = label: TEXT*20; copy(TRANSIENT): ATTRIBUTE RESTRICTION (TEXT*10) := >>label; END C; END T;"), "ILI-SEM-0005");
            bad(model(version, "TOPIC T = CLASS C = label: TEXT; MANDATORY CONSTRAINT DEFINED(>>Missing->label); END C; END T;"), "ILI-SEM-0001");
        }
    }

}
