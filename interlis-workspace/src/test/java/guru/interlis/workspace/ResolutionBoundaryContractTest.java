package guru.interlis.workspace;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.source.*;
import guru.interlis.workspace.resolution.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResolutionBoundaryContractTest {
    @Test void missingNamedRootHasANegativeDependencyAndPendingFetch() {
        try (var workspace = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            var closure = workspace.capture(new AnalysisRequest(workspace.currentRevision(),
                    List.of(new ModelSelector.ByName("Absent", Optional.of("v1"), Optional.empty())))).resolution();
            assertFalse(closure.complete());
            assertEquals(1, closure.pendingFetches().size());
            assertEquals("Absent", closure.pendingFetches().getFirst().modelName());
            assertEquals(Optional.of("v1"), closure.pendingFetches().getFirst().modelVersion());
            assertEquals(1, closure.negativeDependencies().size());
        }
    }

    @Test void exactVersionSourcePriorityAndRootSelectionUseOnePurePolicy() {
        var low = new SourceId("memory:/low.ili");
        var high = new SourceId("memory:/high.ili");
        var chosen = new ModelSelector.ByName("Dep", Optional.of("1"), Optional.of(low));
        var policy = new ResolutionPolicy(true, Map.of("Dep", chosen), Map.of(low, 2, high, 0));
        var config = new WorkspaceConfiguration(CompilerOptions.defaults(), policy, ResolverCatalog.empty(), CacheLimits.defaults());
        try (var w = Workspaces.create(CompilerEngines.create(), config)) {
            w.update(new SourceUpdate(low, new SourceExpectation.Absent(), new SourceChange.Replace(
                    "INTERLIS 2.4; MODEL Dep AT \"https://example.org\" VERSION \"1\"=DOMAIN D=TEXT*7;END Dep.")));
            w.update(new SourceUpdate(high, new SourceExpectation.Absent(), new SourceChange.Replace(
                    "INTERLIS 2.4; MODEL Dep AT \"https://example.org\" VERSION \"99\"=DOMAIN D=TEXT*99;END Dep.")));
            var roots = List.<ModelSelector>of(new ModelSelector.ByName("Dep", Optional.empty(), Optional.empty()));
            var capture = w.capture(new AnalysisRequest(w.currentRevision(), roots));
            assertTrue(capture.resolution().complete());
            assertEquals(roots, capture.request().roots());
            var binding = assertInstanceOf(RootResolution.Resolved.class,
                    capture.request().sources().rootResolutions().get(roots.getFirst()));
            assertEquals(chosen.sourceHint().orElseThrow(), binding.target().source());
            assertEquals(chosen.modelVersion(), binding.target().modelVersion());
            var out = w.compile(new WorkspaceCompileRequest(w.currentRevision(), roots), CancellationToken.none());
            var ok = assertInstanceOf(CompilationOutcome.Succeeded.class, out);
            assertTrue(ok.schema().semanticGraph().symbols().stream().filter(x -> x instanceof guru.interlis.model.ModelSymbol)
                    .map(x -> (guru.interlis.model.ModelSymbol) x).filter(x -> x.name().value().equals("Dep"))
                    .allMatch(x -> x.modelVersion().equals("1")));
            assertNotEquals(policy.fingerprint(), new ResolutionPolicy(true, Map.of("Dep", chosen), Map.of(low, 0, high, 2)).fingerprint());
            assertEquals(policy.fingerprint(), new ResolutionPolicy(true, new TreeMap<>(policy.modelSelections()),
                    new LinkedHashMap<>(policy.sourcePriorities())).fingerprint());
            var impossible = new ModelSelector.ByName("Dep", Optional.of("99"), Optional.empty());
            var conflicting = w.capture(new AnalysisRequest(w.currentRevision(), List.of(impossible)));
            assertFalse(conflicting.resolution().complete());
            assertInstanceOf(RootResolution.Missing.class, conflicting.request().sources().rootResolutions().get(impossible));
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                    w.compile(new WorkspaceCompileRequest(w.currentRevision(), List.of(impossible)), CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.MISSING_INPUT), rejected.rejection().reasons());
            assertEquals(List.of(impossible), rejected.rejection().requestedRoots());
        }
    }

    @Test void compilerUsesOnlyTheResolversBestRankedCandidates() {
        var first = new SourceId("memory:/one.ili");
        var second = new SourceId("memory:/two.ili");
        var lower = new SourceId("memory:/three.ili");
        var options = CompilerOptions.defaults();
        var config = new WorkspaceConfiguration(options, new ResolutionPolicy(true, Map.of(),
                Map.of(first, 0, second, 0, lower, 1)), ResolverCatalog.empty(), CacheLimits.defaults());
        var named = new ModelSelector.ByName("Root", Optional.empty(), Optional.empty());
        try (var workspace = Workspaces.create(CompilerEngines.create(), config)) {
            for (var id : List.of(first, second, lower)) {
                workspace.update(new SourceUpdate(id, new SourceExpectation.Absent(), new SourceChange.Replace(
                        "INTERLIS 2.4; MODEL Root AT \"x\" VERSION \"1\" = END Root.")));
            }
            var outcome = assertInstanceOf(CompilationOutcome.Rejected.class, workspace.compile(
                    new WorkspaceCompileRequest(workspace.currentRevision(), List.of(named)), CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.AMBIGUOUS_INPUT), outcome.rejection().reasons());
            var diagnosis = outcome.analysis().requestDiagnostics().stream()
                    .filter(d -> d.code().value().equals("ILI-RES-0002")).findFirst().orElseThrow();
            var candidates = (guru.interlis.diagnostics.DiagnosticArgument.IdentifierListArgument)
                    diagnosis.arguments().stream().filter(a -> a.name().equals("candidates")).findFirst().orElseThrow();
            assertEquals(List.of(first.value(), second.value()), candidates.values());
        }
    }

    @Test void atomicConfigurationAndSourcesBothRemainUnchangedAfterAnyRejectedMember() {
        try (var w = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            var config = w.configuration(); var revision = w.currentRevision();
            var newConfig = new WorkspaceConfiguration(config.compilerOptions(), new ResolutionPolicy(false),
                    ResolverCatalog.empty(), config.cacheLimits());
            var id = new SourceId("memory:/source.ili");
            var good = new SourceUpdate(id, new SourceExpectation.Absent(), new SourceChange.Replace("INTERLIS 2.4;"));
            var bad = new SourceUpdate(new SourceId("memory:/other.ili"), new SourceExpectation.At(new SourceRevision(9)),
                    new SourceChange.Replace("invalid"));
            assertThrows(UpdateRejectedException.class, () -> w.applyBatch(List.of(good, bad), revision, newConfig));
            assertEquals(revision, w.currentRevision()); assertEquals(config, w.configuration()); assertTrue(w.source(id).isEmpty());
        }
    }

    @Test void selectionChangesAlterCaptureIdentityWithoutSourceOrOverlayEdits() {
        var config = WorkspaceConfiguration.defaults();
        try (var w = Workspaces.create(CompilerEngines.create(), config)) {
            for (int n : List.of(1, 2)) w.update(new SourceUpdate(new SourceId("memory:/dep"+n), new SourceExpectation.Absent(),
                    new SourceChange.Replace("INTERLIS 2.4; MODEL Dep AT \"https://example.org\" VERSION \""+n+"\"=DOMAIN D=TEXT;END Dep.")));
            var roots = List.<ModelSelector>of(new ModelSelector.ByName("Dep", Optional.empty(), Optional.empty()));
            InputFingerprint previous = null;
            for (int n : List.of(1, 2)) {
                var policy = new ResolutionPolicy(true, Map.of("Dep", new ModelSelector.ByName("Dep", Optional.of(Integer.toString(n)), Optional.empty())), Map.of());
                w.configure(new WorkspaceConfiguration(config.compilerOptions(), policy, config.resolverCatalog(), config.cacheLimits()), w.currentRevision());
                var capture = w.capture(new AnalysisRequest(w.currentRevision(), roots));
                if (previous != null) assertNotEquals(previous, capture.fingerprint());
                previous = capture.fingerprint();
            }
        }
    }

    @Test void analysisWithOtherRootSelectionCannotBeAcceptedIntoACapture() {
        try (var w = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults())) {
            for (int n : List.of(1, 2)) w.update(new SourceUpdate(new SourceId("memory:/dep"+n), new SourceExpectation.Absent(),
                    new SourceChange.Replace("INTERLIS 2.4; MODEL Dep AT \"https://example.org\" VERSION \""+n+"\"=END Dep.")));
            var capture=w.capture(new AnalysisRequest(w.currentRevision(),List.of(new ModelSelector.ByName("Dep",Optional.of("1"),Optional.empty()))));
            var foreign=new CompilationRequest(List.of(new ModelSelector.ByName("Dep",Optional.of("2"),Optional.empty())),capture.request().sources(),capture.request().options());
            var analysis=CompilerEngines.create().analyze(foreign,CancellationToken.none());
            assertThrows(IllegalArgumentException.class,()->w.acceptAnalysis(capture,analysis));
            var own=CompilerEngines.create().analyze(capture.request(),CancellationToken.none());
            assertEquals(AnalysisAcceptance.ACCEPTED,w.acceptAnalysis(capture,own));
        }
    }

    @Test void captureRejectsAnalysisThatClaimsDifferentGrammarProvenance() {
        var engine = CompilerEngines.create();
        var token = CancellationToken.none();
        try (var workspace = Workspaces.create(engine, WorkspaceConfiguration.defaults())) {
            var id = new SourceId("memory:/provenance.ili");
            WorkspaceTestSupport.add(workspace, id.value(),
                    "INTERLIS 2.4; MODEL Root AT \"x\" VERSION \"1\" = END Root.");
            var capture = workspace.capture(new AnalysisRequest(workspace.currentRevision(),
                    List.of(new ModelSelector.BySource(id))), token);
            var own = engine.analyze(capture.request(), capture.reuse(), token);
            var current = own.provenance();
            var foreign = new AnalysisProvenance(current.compilerVersion(), "other grammar", current.semanticProfile(),
                    current.resolverFingerprint(), current.optionsFingerprint(), current.sourceFingerprints());
            var supplied = new AnalysisSnapshot(own.snapshotId(), own.semanticGraph(), own.sourceRevisions(),
                    own.syntaxSnapshots(), own.diagnostics(), own.requestDiagnostics(), own.coverage(),
                    own.fingerprint(), foreign);
            assertThrows(IllegalArgumentException.class, () -> workspace.acceptAnalysis(capture, supplied));
            assertEquals(0, workspace.metrics().acceptedAnalyses());
            assertEquals(AnalysisAcceptance.ACCEPTED, workspace.acceptAnalysis(capture, own));
        }
    }

    @Test void selectedOriginalIdentityIsPreservedAcrossOverlayAndSourceTranslations() {
        for (boolean external : List.of(true, false)) {
            var d=CompilerOptions.defaults();
            var overlay=new SemanticOverlays.SemanticOverlay.TranslationOverlay(new SemanticOverlays.OverlaySource("project",1),
                    SemanticOverlays.Target.named("F"),SemanticOverlays.Target.named("Original"));
            var options=new CompilerOptions(d.semanticProfile(),d.allowedLanguages(),d.warningPolicy(),d.budget(),
                    external?new SemanticOverlays(List.of(overlay)):SemanticOverlays.empty());
            var selected=new SourceId("memory:/selected-original.ili");
            var policy=new ResolutionPolicy(true,Map.of("Original",new ModelSelector.ByName("Original",Optional.of("1"),Optional.of(selected))),Map.of());
            try(var w=Workspaces.create(CompilerEngines.create(),new WorkspaceConfiguration(options,policy,ResolverCatalog.empty(),CacheLimits.defaults()))) {
                w.update(new SourceUpdate(selected,new SourceExpectation.Absent(),new SourceChange.Replace("INTERLIS 2.4;MODEL Original AT \"https://example.org\" VERSION \"1\"=DOMAIN D=TEXT;END Original.")));
                w.update(new SourceUpdate(new SourceId("memory:/other-original.ili"),new SourceExpectation.Absent(),new SourceChange.Replace("INTERLIS 2.4;MODEL Original AT \"https://example.org\" VERSION \"1\"=DOMAIN D=TEXT*7;END Original.")));
                var root=new SourceId("memory:/translation.ili");
                w.update(new SourceUpdate(root,new SourceExpectation.Absent(),new SourceChange.Replace("INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\" "+(external?"":"TRANSLATION OF Original[\"1\"] ")+"=DOMAIN D=TEXT;END F.")));
                var result=w.compile(new WorkspaceCompileRequest(w.currentRevision(),List.of(new ModelSelector.BySource(root))),CancellationToken.none());
                var ok=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():result.toString());
                var graph=ok.schema().semanticGraph();
                var original=graph.symbols().stream().filter(x->x instanceof guru.interlis.model.ModelSymbol&&x.name().value().equals("Original")).findFirst().orElseThrow();
                var translated=(guru.interlis.model.ModelSymbol)graph.symbols().stream().filter(x->x instanceof guru.interlis.model.ModelSymbol&&x.name().value().equals("F")).findFirst().orElseThrow();
                assertEquals(selected,original.header().nameRange().orElseThrow().source());
                assertEquals(original.id(),translated.translation().orElseThrow().sourceTarget().orElseThrow());
            }
        }
    }

    @Test void resolutionBudgetControlsHeaderAndCandidateScans() {
        var options = CompilerOptions.defaults();
        options = new CompilerOptions(options.semanticProfile(), options.allowedLanguages(), options.warningPolicy(),
                options.budget().withMaxSemanticWorkUnits(1), options.overlays());
        var config = new WorkspaceConfiguration(options, ResolutionPolicy.defaults(),
                ResolverCatalog.empty(), CacheLimits.defaults());
        try (var workspace = Workspaces.create(CompilerEngines.create(), config)) {
            var id = new SourceId("memory:/root.ili");
            workspace.update(new SourceUpdate(id, new SourceExpectation.Absent(), new SourceChange.Replace(
                    "INTERLIS 2.4; MODEL Root AT \"https://example.org\" VERSION \"1\" = IMPORTS Missing; END Root.")));
            var failure = assertThrows(OperationCancelledException.class, () -> workspace.capture(
                    new AnalysisRequest(workspace.currentRevision(), List.of(new ModelSelector.BySource(id)))));
            assertEquals(CancellationReason.RESOURCE_LIMIT, failure.reason());
        }
    }
}
