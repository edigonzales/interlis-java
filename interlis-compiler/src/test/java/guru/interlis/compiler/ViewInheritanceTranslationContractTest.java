package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** View inheritance and translations use the same bound identities as ordinary paths. */
class ViewInheritanceTranslationContractTest {
    private static final String PREFIX = "TOPIC T = CLASS A = x : TEXT; END A; CLASS B = x : TEXT; END B; ";
    private static ViewSymbol view(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast)
                .filter(v -> v.name().value().equals(name)).findFirst().orElseThrow();
    }
    @Test void mandatoryCannotBeRemovedInAViewOverride() {
        for (String v : List.of("2.3", "2.4")) ViewBindingContractTest.rule(v, PREFIX
                + "VIEW V PROJECTION OF A; = x : MANDATORY TEXT := A->x; END V; "
                + "VIEW W EXTENDS V = x (EXTENDED) : TEXT := A->x; END W; END T;", "ATTRIBUTE_OVERRIDE_MANDATORY");
    }
    @Test void mandatoryWithoutTypeCanExtendAnAllOfMember() {
        for (String v : List.of("2.3", "2.4")) {
            var graph = ViewBindingContractTest.success(v, PREFIX
                    + "VIEW V PROJECTION OF A; = ALL OF A; END V; "
                    + "VIEW W EXTENDS V = x (EXTENDED) : MANDATORY; END W; END T;").schema().semanticGraph();
            var base = (AttributeSymbol)graph.symbol(view(graph, "V").members().getFirst()).orElseThrow();
            var derived = (AttributeSymbol)graph.symbol(view(graph, "W").members().getFirst()).orElseThrow();
            assertTrue(derived.mandatory()); assertEquals(base.declaredType(), derived.declaredType());
            assertEquals(Optional.of(base.id()), derived.overrides());
        }
    }
    @Test void effectiveMembersKeepTheirFullInheritancePathAndOverrideOrigin() {
        for (String v : List.of("2.3", "2.4")) {
            var graph = ViewBindingContractTest.success(v, PREFIX
                    + "VIEW V PROJECTION OF A; = x : TEXT := A->x; y : TEXT := A->x; END V; "
                    + "VIEW W EXTENDS V = x (EXTENDED) : TEXT := A->x; END W; "
                    + "VIEW Z EXTENDS W = END Z; END T;").schema().semanticGraph();
            var base = view(graph, "V"); var middle = view(graph, "W"); var end = view(graph, "Z");
            var effective = graph.effectiveMembers(end.id());
            assertEquals(List.of(middle.id(), end.id()), effective.getFirst().inheritancePath());
            assertEquals(List.of(base.id(), middle.id(), end.id()), effective.get(1).inheritancePath());
            assertEquals(EffectiveMemberOrigin.OVERRIDDEN, graph.effectiveMembers(middle.id()).getFirst().origin());
            assertEquals(EffectiveMemberOrigin.INHERITED, effective.getFirst().origin());
            assertTrue(effective.getFirst().overridden());
        }
    }
    @Test void unionOverridesRetainInheritedDerivationAlternatives() {
        for (String v : List.of("2.3", "2.4")) ViewBindingContractTest.success(v, PREFIX
                + "VIEW V UNION OF a~A,b~B; = x : MANDATORY TEXT := a->x,b->x; END V; "
                + "VIEW W EXTENDS V = x (EXTENDED) : MANDATORY TEXT := a->x; END W; END T;");
    }
    @Test void overridesKeepTransientModeAndDomainIdentity() {
        for (String v : List.of("2.3", "2.4")) {
            ViewBindingContractTest.rule(v, PREFIX
                    + "VIEW V PROJECTION OF A; = x (TRANSIENT) : TEXT := A->x; END V; "
                    + "VIEW W EXTENDS V = x (EXTENDED) : TEXT := A->x; END W; END T;", "ATTRIBUTE_OVERRIDE_TRANSIENT");
            ViewBindingContractTest.rule(v, "DOMAIN First = TEXT; Second = TEXT; " + PREFIX
                    + "VIEW V PROJECTION OF A; = x : First := A->x; END V; "
                    + "VIEW W EXTENDS V = x (EXTENDED) : Second := A->x; END W; END T;", "ATTRIBUTE_OVERRIDE_TYPE");
        }
    }
    @Test void translationCannotReplaceAnImportedAttributeDomainWithAnEqualTypedDomain() {
        for (String v : List.of("2.3", "2.4")) {
            String common = "MODEL Shared AT \"https://example.org\" VERSION \"1\" = DOMAIN First = TEXT; Second = TEXT; END Shared.";
            String body = "IMPORTS Shared; " + PREFIX + "VIEW V PROJECTION OF A; = x : Shared.First := A->x; END V; END T;";
            var positive = translated(v, common, body, body);
            assertInstanceOf(CompilationOutcome.Succeeded.class, positive, positive.toString());
            var negative = assertInstanceOf(CompilationOutcome.Rejected.class, translated(v, common, body, body.replace("Shared.First", "Shared.Second")));
            assertTrue(negative.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains("TRANSLATION_REFERENCE")), negative.analysis().diagnostics().toString());
        }
    }
    private CompilationOutcome translated(String version, String common, String sourceBody, String targetBody) {
        var source = CompilerTestSupport.source("memory:/translation.ili", "INTERLIS " + version + "; " + common
                + " MODEL Base AT \"https://example.org\" VERSION \"1\" = " + sourceBody + " END Base. "
                + " MODEL M AT \"https://example.org\" VERSION \"1\" TRANSLATION OF Base [\"1\"] = " + targetBody + " END M.");
        var syntax = CompilerTestSupport.parse(source);
        List<ImportBinding> bindings = new ArrayList<>();
        for (var model : syntax.root().models()) for (var imported : model.imports()) {
            var target = syntax.root().models().stream().filter(m -> m.name().equals(imported.modelName())).findFirst().orElseThrow();
            bindings.add(new ImportBinding(new ImportSite(imported.range(), model.name(), imported.modelName()),
                    new ImportResolution.Resolved(new ModelLocation(source.id(), source.revision(), target.name(),
                            Optional.of(target.modelVersion()), target.range().startOffset()))));
        }
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"), Map.of(source.id(), source), bindings, CompilerOptions.defaults()), CancellationToken.none());
    }
    @Test void aggregationTranslationMapsTheSyntheticAggregatesMember() {
        for (String v : List.of("2.3", "2.4")) {
            String body = PREFIX + "VIEW V AGGREGATION OF a~A ALL; = count := INTERLIS.elementCount(AGGREGATES); END V; VIEW W EXTENDS V = END W; END T;";
            var result = translated(v, "", body, body);
            assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                    result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
        }
    }
    @Test void translationMayKeepAnUntranslatedImportedBaseView() {
        for (String v : List.of("2.3", "2.4")) {
            String common = "MODEL Shared AT \"https://example.org\" VERSION \"1\" = " + PREFIX + "VIEW V PROJECTION OF A; = ALL OF A; END V; END T; END Shared.";
            String body = "IMPORTS Shared; TOPIC T = DEPENDS ON Shared.T; VIEW W EXTENDS Shared.T.V = END W; END T;";
            var result = translated(v, common, body, body);
            assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                    result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
        }
    }
    @Test void importedAllOfTypesAndPathsAreStableAcrossFileOrderAndEdits() {
        for (String v : List.of("2.3", "2.4")) for (boolean baseFirst : List.of(true, false)) {
            var engine = CompilerEngines.create(); AnalysisSnapshot previous = null;
            for (int revision = 1; revision <= 3; revision++) {
                int length = revision == 2 ? 10 : 20;
                var base = new guru.interlis.source.SourceText(new guru.interlis.source.SourceId("memory:/" + (baseFirst ? "a" : "z") + "-base.ili"),
                        new guru.interlis.source.SourceRevision(revision), "INTERLIS " + v + "; MODEL Base AT \"https://example.org\" VERSION \"1\" = TOPIC T = CLASS A = x : TEXT*" + length + "; END A; VIEW V PROJECTION OF A; = ALL OF A; END V; END T; END Base.");
                var use = CompilerTestSupport.source("memory:/" + (baseFirst ? "z" : "a") + "-use.ili", "INTERLIS " + v + "; MODEL M AT \"https://example.org\" VERSION \"1\" = IMPORTS Base; TOPIC T = DEPENDS ON Base.T; VIEW W EXTENDS Base.T.V = x (EXTENDED) : MANDATORY; MANDATORY CONSTRAINT DEFINED(x); END W; END T; END M.");
                var request = CompilerTestSupport.request(List.of("M"), Map.of(base.id(), base, use.id(), use),
                        List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(use), "Base", base, CompilerTestSupport.parse(base))), CompilerOptions.defaults());
                var reused = engine.analyze(request, previous == null ? AnalysisReuse.none() : new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), CancellationToken.none());
                var fresh = engine.analyze(request, CancellationToken.none());
                assertEquals(fresh.diagnostics(), reused.diagnostics()); assertTrue(reused.diagnostics().isEmpty(), reused.diagnostics().toString());
                var graph = reused.semanticGraph(); var w = view(graph, "W");
                var attribute = (AttributeSymbol)graph.symbol(w.members().getFirst()).orElseThrow();
                assertEquals(length, ((TextType)graph.type(attribute.declaredType()).orElseThrow()).maxLength().orElseThrow());
                assertEquals(OriginalViewReuseTest.semantic(graph.effectiveMembers(w.id()),graph), OriginalViewReuseTest.semantic(fresh.semanticGraph().effectiveMembers(view(fresh.semanticGraph(), "W").id()),fresh.semanticGraph()));
                var original = (AttributeSymbol)graph.symbol(attribute.overrides().orElseThrow()).orElseThrow();
                assertInstanceOf(Origin.DerivedOrigin.class, original.header().origin());
                var compiled = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(request, CancellationToken.none()));
                var checked=compiled.schema().semanticGraph();
                var checkedW=view(checked,"W");var checkedAttribute=(AttributeSymbol)checked.symbol(checkedW.members().getFirst()).orElseThrow();
                var checkedOriginal=(AttributeSymbol)checked.symbol(checkedAttribute.overrides().orElseThrow()).orElseThrow();
                assertTrue(checked.expression(checkedOriginal.derivations().getFirst()).isPresent());
                assertEquals(OriginalViewReuseTest.semantic(original,graph),OriginalViewReuseTest.semantic(checkedOriginal,checked));
                previous = reused;
            }
        }
    }
    @Test void aTranslationIncludesErrorsInItsOriginalModelInTheGate() {
        for (String v : List.of("2.3", "2.4")) {
            String valid = PREFIX + "VIEW V PROJECTION OF A; = x : TEXT := A->x; END V; END T;";
            var result = assertInstanceOf(CompilationOutcome.Rejected.class, translated(v, "", valid.replace("A->x", "A->missing"), valid));
            assertTrue(result.analysis().diagnostics().stream().anyMatch(d -> d.arguments().toString().contains("missing")));
            assertTrue(result.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
        }
    }

}
