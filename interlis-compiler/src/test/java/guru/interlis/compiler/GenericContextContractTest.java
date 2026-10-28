package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** K3: identity-bound contexts and legal per-topic deferred coordinate choices. */
class GenericContextContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();
    private static final String DOMAINS = "DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC; A = COORD 0..100,0..100; B = COORD 200..300,200..300; ";
    private static String model(String body) { return "INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = " + body + " END M."; }
    private static String topic(String deferred) { return "TOPIC T = " + deferred + " CLASS C = p: G; END C; END T;"; }
    private static CompilationRequest request(Map<SourceId, SourceText> sources) {
        var bindings = new ArrayList<ImportBinding>();
        var parsed = sources.values().stream().map(CompilerTestSupport::parse).toList();
        for (var syntax : parsed) for (var model : syntax.root().models()) for (var imported : model.imports())
            for (var targetSource : parsed) for (var target : targetSource.root().models()) if (target.name().equals(imported.modelName()))
                bindings.add(new ImportBinding(new ImportSite(imported.range(), model.name(), imported.modelName()),
                        new ImportResolution.Resolved(new ModelLocation(targetSource.source().id(), targetSource.source().revision(),
                                target.name(), Optional.of(target.modelVersion()), target.range().startOffset()))));
        return CompilerTestSupport.request(List.of("M"), sources, bindings, CompilerOptions.defaults());
    }
    private static CompilationRequest request(String text) {
        var source = CompilerTestSupport.source("memory:/context.ili", text); return request(Map.of(source.id(), source));
    }
    private static CompilationOutcome.Succeeded ok(String text) {
        var result = CompilerEngines.create().compile(request(text), TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
    }
    private static void bad(String text, String rule) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request(text), TOKEN));
        assertTrue(result.analysis().diagnostics().toString().contains(rule), result.analysis().diagnostics()::toString);
        assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
    }
    private static Symbol named(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(s -> s.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static TopicSymbol topic(SemanticGraph graph, String name) { return (TopicSymbol) named(graph, name); }

    @Test void contextMappingsAndDeferredStateRetainOrderedIdentitiesAndReferences() {
        var result = ok(model(DOMAINS + "CONTEXT default = G = A OR B; " + topic("DEFERRED GENERICS G;")));
        var graph = result.schema().semanticGraph(); var context = (ContextSymbol) named(graph, "default");
        var mapping = context.mappings().getFirst();
        assertEquals(named(graph, "G").id(), mapping.genericDomain());
        assertEquals(List.of(named(graph, "A").id(), named(graph, "B").id()), mapping.alternatives());
        var topic = topic(graph, "T"); var binding = topic.genericBindings().getFirst();
        assertEquals(TopicSymbol.BindingState.DEFERRED, binding.state());
        assertEquals(mapping.alternatives(), binding.alternatives()); assertEquals(List.of(context.id()), binding.contexts());
        assertEquals(List.of(mapping.genericDomain()), topic.deferredGenerics());
        var source = result.analysis().syntaxSnapshots().values().iterator().next().source();
        for (String needle : List.of("G = A", "A OR B", "B; TOPIC", "G; CLASS")) {
            int offset = source.text().indexOf(needle); assertTrue(offset >= 0);
            assertTrue(LanguageQueries.create().symbolAt(result.analysis(), source.range(offset, offset), TOKEN).known().isPresent());
        }
        assertTrue(graph.types().stream().noneMatch(t -> t instanceof ErrorType || t instanceof PendingType));
    }

    @Test void concreteAndAbstractBindingsAreDifferentFromInvalidDeferral() {
        var concrete = ok(model(DOMAINS + "CONTEXT default = G = A; " + topic(""))).schema().semanticGraph();
        assertEquals(TopicSymbol.BindingState.CONCRETE, topic(concrete, "T").genericBindings().getFirst().state());
        var abs = ok(model(DOMAINS + topic("").replace("TOPIC T =", "TOPIC T (ABSTRACT) ="))).schema().semanticGraph();
        assertEquals(TopicSymbol.BindingState.ABSTRACT, topic(abs, "T").genericBindings().getFirst().state());
        bad(model(DOMAINS + topic("")), "GENERIC_CONTEXT_MISSING");
        bad(model(DOMAINS + topic("DEFERRED GENERICS G;")), "GENERIC_CONTEXT_MISSING");
        bad(model(DOMAINS + "CONTEXT default = G = A OR B; " + topic("")), "GENERIC_DEFERRED_REQUIRED");
        bad(model(DOMAINS + "CONTEXT default = G = A OR B; " + topic("DEFERRED GENERICS G;").replace("p: G", "p: A")), "GENERIC_DEFERRED_UNUSED");
        bad(model(DOMAINS + topic("DEFERRED GENERICS A;")), "GENERIC_DEFERRED_UNKNOWN");
        bad(model(DOMAINS + topic("DEFERRED GENERICS Missing;")), "ILI-SEM-0001");
    }

    @Test void multipleMappingsAndContextsNeverLeakAcrossTopicsOrModelNames() {
        String body = DOMAINS + "H (GENERIC) = COORD NUMERIC, NUMERIC; CONTEXT first = G = A; H = B; second = G = A; " + topic("");
        var graph = ok(model(body)).schema().semanticGraph();
        assertEquals(2, ((ContextSymbol) named(graph, "first")).mappings().size());
        assertEquals(List.of(named(graph, "second").id()), topic(graph, "T").genericBindings().getFirst().contexts());
        bad(model(DOMAINS + "CONTEXT default = G = A OR B; " + topic("DEFERRED GENERICS G;")
                + "TOPIC U = CLASS D = p: G; END D; END U;"), "GENERIC_DEFERRED_REQUIRED");
        String unrelated = "MODEL Other AT \"x\" VERSION \"1\" = " + DOMAINS + " CONTEXT default = G = A; END Other.";
        bad(model(DOMAINS + topic("")) + unrelated, "GENERIC_CONTEXT_MISSING");
        bad(model(DOMAINS + "CONTEXT default = G = A; G = B; " + topic("")), "CONTEXT_DUPLICATE");
    }

    @Test void alternativesMustBeConcreteCoordinatesWithCompatibleBounds() {
        bad(model(DOMAINS + "CONTEXT default = A = B; " + topic("")), "CONTEXT_GENERIC");
        bad(model(DOMAINS + "CONTEXT default = G = G; " + topic("")), "CONTEXT_CONCRETE");
        bad(model(DOMAINS + "X = TEXT; CONTEXT default = G = X; " + topic("")), "CONTEXT_CONCRETE");
        bad(model(DOMAINS + "X = COORD 0..100; CONTEXT default = G = X; " + topic("")), "GENERIC_DIMENSIONS");
        bad(model(DOMAINS.replace("COORD NUMERIC, NUMERIC", "COORD 0..100, NUMERIC") + "CONTEXT default = G = B; " + topic("")), "GENERIC_RANGE");
        bad(model(DOMAINS + "X (ABSTRACT) = COORD NUMERIC, NUMERIC; CONTEXT default = G = X; " + topic("")), "CONTEXT_CONCRETE");
        bad(model("DOMAIN G (GENERIC) = TEXT;"), "GENERIC_TYPE");
        bad(model(DOMAINS + "CONTEXT default = Missing.G = A; " + topic("")), "ILI-SEM-0001");
        bad(model(DOMAINS + "TOPIC T = CONTEXT default = G = A; END T;"), "CONTEXT_SCOPE");
        bad(model(DOMAINS + "CONTEXT default = G = A; default = G = A;"), "CONTEXT_NAME_UNIQUE");
    }

    @Test void importedContextsRefineAlternativesAndTopicInheritanceKeepsTheEffectiveChoice() {
        String prefix = "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = " + DOMAINS
                + "CONTEXT default = G = A OR B; " + topic("DEFERRED GENERICS G;") + " END Base. ";
        String derived = "MODEL M AT \"x\" VERSION \"1\" = IMPORTS Base; DOMAIN Small EXTENDS Base.A = COORD 10..20,10..20; "
                + "CONTEXT local = Base.G = Small; TOPIC U EXTENDS Base.T = END U; END M.";
        var graph = ok(prefix + derived).schema().semanticGraph();
        var binding = topic(graph, "U").genericBindings().getFirst();
        assertEquals(TopicSymbol.BindingState.CONCRETE, binding.state());
        assertEquals(List.of(named(graph, "Small").id()), binding.alternatives());
        bad(prefix + derived.replace("EXTENDS Base.A", "EXTENDS Base.G"), "GENERIC_CONTEXT_EXTENDS");
        bad(prefix + derived.replace("IMPORTS Base;", ""), "ILI-SEM-0001");
        bad(prefix + derived.replace("10..20,10..20", "0..200,0..200"), "CONTEXT_ALTERNATIVE_EXTENSION");
    }

    @Test void genericUsesIncludeAliasesCollectionsAndVisibleStructureExtensions() {
        var graph = ok(model(DOMAINS + "CONTEXT default = G = A OR B; "
                + "STRUCTURE S = END S; STRUCTURE D EXTENDS S = p: POLYLINE WITH (STRAIGHTS) VERTEX G; END D; "
                + "TOPIC T = DEFERRED GENERICS G; CLASS C = parts: BAG OF S; END C; END T;")).schema().semanticGraph();
        assertEquals(named(graph, "G").id(), topic(graph, "T").genericBindings().getFirst().genericDomain());
        bad(model(DOMAINS + "CONTEXT default = G = A OR B; STRUCTURE S = p: G; END S; "
                + "TOPIC T = CLASS C = parts: LIST OF S; END C; END T;"), "GENERIC_DEFERRED_REQUIRED");
    }

    @Test void importsAreOrderIndependentAndFreshAnalysisMatchesReuse() {
        for (boolean reverse : List.of(false, true)) {
            var base = CompilerTestSupport.source("memory:/" + (reverse ? "z" : "a") + ".ili",
                    "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = " + DOMAINS + "CONTEXT default = G = A; END Base.");
            var consumer = CompilerTestSupport.source("memory:/" + (reverse ? "a" : "z") + ".ili", model("IMPORTS Base; TOPIC T = CLASS C = p: Base.G; END C; END T;"));
            var req = request(Map.of(base.id(), base, consumer.id(), consumer)); var engine = CompilerEngines.create();
            var result = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(req, TOKEN));
            var fresh = engine.analyze(req, TOKEN); var reuse = engine.analyze(req, new AnalysisReuse(Optional.of(result.analysis()), result.analysis().syntaxSnapshots()), TOKEN);
            assertEquals(fresh.diagnostics(), reuse.diagnostics());
            assertEquals(topic(fresh.semanticGraph(), "T").genericBindings(), topic(reuse.semanticGraph(), "T").genericBindings());
            assertEquals(TopicSymbol.BindingState.CONCRETE, topic(result.schema().semanticGraph(), "T").genericBindings().getFirst().state());
            var edited = new SourceText(base.id(), new SourceRevision(2), base.text().replace("G = A;", "G = B;"));
            var changed = request(Map.of(edited.id(), edited, consumer.id(), consumer));
            var changedFresh = engine.analyze(changed, TOKEN);
            var changedReuse = engine.analyze(changed, new AnalysisReuse(Optional.of(result.analysis()), result.analysis().syntaxSnapshots()), TOKEN);
            assertEquals(changedFresh.diagnostics(), changedReuse.diagnostics());
            var changedBinding = topic(changedReuse.semanticGraph(), "T").genericBindings().getFirst();
            assertEquals(List.of(named(changedReuse.semanticGraph(), "B").id()), changedBinding.alternatives());
            assertEquals(topic(changedFresh.semanticGraph(), "T").genericBindings(), topic(changedReuse.semanticGraph(), "T").genericBindings());
        }
    }

    @Test void version23RejectsGenericContextsAndDeferredSyntax() {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request(model(DOMAINS + "CONTEXT default = G = A; " + topic("DEFERRED GENERICS G;")).replace("INTERLIS 2.4", "INTERLIS 2.3")), TOKEN));
        assertTrue(result.analysis().diagnostics().stream().anyMatch(d -> d.code().value().startsWith("ILI-SYN")));
    }

    @Test void transitiveContextsConvergeOrReportConflictingImportedAlternatives() {
        String base = "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = " + DOMAINS + "CONTEXT original = G = A OR B; END Base. ";
        String left = "MODEL Left AT \"x\" VERSION \"1\" = IMPORTS Base; CONTEXT left = Base.G = Base.A; END Left. ";
        String right = "MODEL Right AT \"x\" VERSION \"1\" = IMPORTS Base; CONTEXT right = Base.G = Base.B; END Right. ";
        String consumer = "MODEL M AT \"x\" VERSION \"1\" = IMPORTS Base, Left; TOPIC T = CLASS C = p: Base.G; END C; END T; END M.";
        var graph = ok(base + left + consumer).schema().semanticGraph();
        assertEquals(List.of(named(graph, "A").id()), topic(graph, "T").genericBindings().getFirst().alternatives());
        bad(base + left + right + consumer.replace("Base, Left;", "Base, Left, Right;"), "CONTEXT_CONFLICT");
        ok(base + left + right.replace("Base.B", "Base.A") + consumer.replace("Base, Left;", "Right, Left, Base;"));
    }

    @Test void translatedContextsPreserveMappingTargetsAndDeferredDomains() {
        String original = "INTERLIS 2.4; MODEL Original AT \"x\" VERSION \"1\" = " + DOMAINS
                + "CONTEXT original = G = A; " + topic("") + " END Original. ";
        String translation = "MODEL M AT \"x\" VERSION \"1\" TRANSLATION OF Original [\"1\"] = "
                + DOMAINS.replace("G (", "H (").replace("A =", "X =").replace("B =", "Y =")
                + "CONTEXT translated = H = X; TOPIC U = CLASS D = p: H; END D; END U; END M.";
        ok(original + translation);
        bad(original + translation.replace("H = X;", "H = Y;"), "TRANSLATION_REFERENCE");
    }
    @Test void mixedGenericAndConcreteCoordinateAxesKeepTheirSourceOrder() {
        String source = model("DOMAIN G (GENERIC) = COORD NUMERIC,0..100; A = COORD 200..300,0..100; CONTEXT c = G = A; " + topic(""));
        var graph = ok(source).schema().semanticGraph();
        var domain = (DomainSymbol) named(graph, "G");
        var coordinate = (CoordinateType) graph.type(domain.typeId()).orElseThrow();
        assertTrue(coordinate.axes().getFirst().minimum().isEmpty());
        assertEquals("100", coordinate.axes().get(1).maximum().orElseThrow().exactValue().toPlainString());
        bad(source.replace("200..300,0..100", "0..100,200..300"), "GENERIC_RANGE");
        ok(source.replace("NUMERIC,0..100", "0..100,NUMERIC").replace("200..300,0..100", "0..100,200..300"));
    }
}
