package guru.interlis.workspace;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.RejectionReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceTest {
    private static final String BASE = """
            INTERLIS 2.4;
            MODEL Base AT "https://example.interlis.ch" VERSION "1.0" =
              DOMAIN
                Count = 0..999;
              TOPIC Common =
                CLASS BaseBuilding =
                  height : Count;
                END BaseBuilding;
              END Common;
            END Base.
            """;
    private static final String SURVEY = """
            INTERLIS 2.4;
            MODEL Survey AT "https://example.interlis.ch" VERSION "1.0" =
              IMPORTS Base;
              TOPIC Data =
    DEPENDS ON Base.Common;
                CLASS Building EXTENDS Base.Common.BaseBuilding =
                  height (EXTENDED) : Base.Count;
                  label : TEXT*20;
                  MANDATORY CONSTRAINT height > 0;
                END Building;
              END Data;
            END Survey.
            """;

    @Test
    void compileFixAndIncrementalEquality() {
        Workspace workspace = WorkspaceTestSupport.workspace();
        SourceId baseId = new SourceId("memory:/base.ili");
        SourceId surveyId = new SourceId("memory:/survey.ili");
        WorkspaceTestSupport.add(workspace, baseId.value(), BASE);
        WorkspaceTestSupport.add(workspace, surveyId.value(), SURVEY);
        WorkspaceCompileRequest request = new WorkspaceCompileRequest(
                workspace.currentRevision(),
                List.of(new ModelSelector.ByName("Survey", Optional.empty(), Optional.empty())));
        CompilationOutcome first = workspace.compile(request, CancellationToken.none());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class, first, () -> describe(first));
        List<String> firstMembers = memberNames(succeeded.schema());

        String broken = SURVEY.replace("height (EXTENDED) : Base.Count;", "height (EXTENDED) : Base.Missing;");
        WorkspaceTestSupport.replace(workspace, surveyId, broken);
        CompilationOutcome rejected = workspace.compile(new WorkspaceCompileRequest(
                workspace.currentRevision(), request.roots()), CancellationToken.none());
        CompilationOutcome.Rejected rejection = assertInstanceOf(
                CompilationOutcome.Rejected.class, rejected, () -> describe(rejected));
        assertTrue(rejection.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));

        WorkspaceTestSupport.replace(workspace, surveyId, SURVEY);
        CompilationOutcome second = workspace.compile(new WorkspaceCompileRequest(
                workspace.currentRevision(), request.roots()), CancellationToken.none());
        CompilationOutcome.Succeeded succeededAgain = assertInstanceOf(
                CompilationOutcome.Succeeded.class, second);
        assertEquals(firstMembers, memberNames(succeededAgain.schema()));
        assertTrue(workspace.metrics().acceptedAnalyses() >= 2);
    }

    @Test
    void missingImportAppearsLaterWithoutEdit() {
        Workspace workspace = WorkspaceTestSupport.workspace();
        SourceId surveyId = new SourceId("memory:/survey.ili");
        WorkspaceTestSupport.add(workspace, surveyId.value(), SURVEY);
        CompilationOutcome missing = workspace.compile(new WorkspaceCompileRequest(
                workspace.currentRevision(),
                List.of(new ModelSelector.ByName("Survey", Optional.empty(), Optional.empty()))),
                CancellationToken.none());
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class, missing);
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.MISSING_INPUT));

        WorkspaceTestSupport.add(workspace, "memory:/base.ili", BASE);
        CompilationOutcome resolved = workspace.compile(new WorkspaceCompileRequest(
                workspace.currentRevision(),
                List.of(new ModelSelector.ByName("Survey", Optional.empty(), Optional.empty()))),
                CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Succeeded.class, resolved, () -> describe(resolved));
    }

    @Test
    void staleDetachedAnalysisIsNotCommitted() {
        Workspace workspace = WorkspaceTestSupport.workspace();
        SourceId baseId = new SourceId("memory:/base.ili");
        SourceId surveyId = new SourceId("memory:/survey.ili");
        WorkspaceTestSupport.add(workspace, baseId.value(), BASE);
        WorkspaceTestSupport.add(workspace, surveyId.value(), SURVEY);
        AnalysisRequest request = new AnalysisRequest(workspace.currentRevision(),
                List.of(new ModelSelector.ByName("Survey", Optional.empty(), Optional.empty())));
        WorkspaceCapture capture = workspace.capture(request);
        AnalysisSnapshot analysis = guru.interlis.compiler.CompilerEngines.create()
                .analyze(capture.request(), capture.reuse(), CancellationToken.none());
        WorkspaceTestSupport.replace(workspace, surveyId, SURVEY + "\n!! changed\n");
        assertEquals(AnalysisAcceptance.STALE_NO_COMMIT,
                workspace.acceptAnalysis(capture, analysis));
        assertNotEquals(capture.workspaceRevision(), workspace.currentRevision());
    }

    @Test
    void ambiguousImportIsReported() {
        Workspace workspace = WorkspaceTestSupport.workspace();
        WorkspaceTestSupport.add(workspace, "memory:/base1.ili", BASE);
        WorkspaceTestSupport.add(workspace, "memory:/base2.ili", BASE);
        WorkspaceTestSupport.add(workspace, "memory:/survey.ili", SURVEY);
        CompilationOutcome outcome = workspace.compile(new WorkspaceCompileRequest(
                workspace.currentRevision(),
                List.of(new ModelSelector.ByName("Survey", Optional.empty(), Optional.empty()))),
                CancellationToken.none());
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class, outcome);
        assertTrue(rejected.rejection().reasons().contains(
                guru.interlis.compiler.RejectionReason.AMBIGUOUS_INPUT));
    }

    @Test
    void batchConflictLeavesWorkspaceUnchanged() {
        Workspace workspace = WorkspaceTestSupport.workspace();
        SourceId a = new SourceId("memory:/a.ili");
        SourceId b = new SourceId("memory:/b.ili");
        WorkspaceTestSupport.add(workspace, a.value(), BASE);
        WorkspaceRevision before = workspace.currentRevision();
        try {
            workspace.applyBatch(List.of(
                    new SourceUpdate(b, new SourceExpectation.Absent(),
                            new SourceChange.Replace("INTERLIS 2.4; MODEL B AT \"x\" VERSION \"1\" = END B.")),
                    new SourceUpdate(a, new SourceExpectation.Absent(),
                            new SourceChange.Replace(BASE))), before);
        } catch (UpdateRejectedException expected) {
            assertEquals(UpdateRejectionCode.SOURCE_ALREADY_PRESENT, expected.code());
        }
        assertEquals(before, workspace.currentRevision());
        assertTrue(workspace.source(a).isPresent());
        assertFalse(workspace.source(b).isPresent());
    }

    private static String describe(CompilationOutcome outcome) {
        return switch (outcome) {
            case CompilationOutcome.Rejected rejected -> "rejected " + rejected.rejection()
                    + " diagnostics=" + rejected.analysis().diagnostics();
            case CompilationOutcome.Cancelled cancelled -> "cancelled " + cancelled.reason();
            case CompilationOutcome.Failed failed -> "failed " + failed.failureId() + " " + failed.error();
            case CompilationOutcome.Succeeded ignored -> "succeeded";
        };
    }

    @Test
    void failedBatchPreservesDeletedSourceRevision() {
        Workspace workspace = WorkspaceTestSupport.workspace();
        SourceId id = new SourceId("memory:/deleted.ili");
        WorkspaceTestSupport.add(workspace, id.value(), BASE);
        WorkspaceTestSupport.replace(workspace, id, BASE + "\n");
        var deletedRevision = workspace.source(id).orElseThrow().revision();
        workspace.remove(id, deletedRevision);
        WorkspaceRevision before = workspace.currentRevision();
        org.junit.jupiter.api.Assertions.assertThrows(UpdateRejectedException.class,
                () -> workspace.applyBatch(List.of(
                        new SourceUpdate(id, new SourceExpectation.Absent(),
                                new SourceChange.Replace(BASE)),
                        new SourceUpdate(new SourceId("memory:/missing.ili"),
                                new SourceExpectation.At(deletedRevision),
                                new SourceChange.Replace(BASE))), before));
        assertEquals(before, workspace.currentRevision());
        assertTrue(workspace.source(id).isEmpty());
        WorkspaceTestSupport.add(workspace, id.value(), BASE);
        assertEquals(deletedRevision.value() + 1,
                workspace.source(id).orElseThrow().revision().value());
    }

    @Test
    void syntaxIsSharedWithResolverAndBoundedAfterAcceptance() {
        Workspace workspace = WorkspaceTestSupport.workspace();
        SourceId id = new SourceId("memory:/base.ili");
        WorkspaceTestSupport.add(workspace, id.value(), BASE);
        var roots = List.<ModelSelector>of(new ModelSelector.ByName("Base", Optional.empty(), Optional.empty()));
        var request = new AnalysisRequest(workspace.currentRevision(), roots);
        var capture = workspace.capture(request);
        var analysis = guru.interlis.compiler.CompilerEngines.create().analyze(capture.request(), capture.reuse(), CancellationToken.none());
        org.junit.jupiter.api.Assertions.assertSame(capture.reuse().previousSyntax().get(id),
                analysis.syntaxSnapshot(id).orElseThrow());
        workspace.acceptAnalysis(capture, analysis);
        assertEquals(1, workspace.metrics().parses());
        assertEquals(0, workspace.metrics().semanticAnalyses()); // The host ran this capture.
        workspace.analyze(request, CancellationToken.none());
        assertEquals(1, workspace.metrics().semanticAnalyses());
        assertEquals(1, workspace.metrics().parses());
        assertEquals(1, workspace.metrics().syntaxReuses());

        var defaults = WorkspaceConfiguration.defaults();
        workspace.configure(new WorkspaceConfiguration(defaults.compilerOptions(),
                defaults.resolutionPolicy(), defaults.resolverCatalog(), new CacheLimits(0, 0, 0, 0)),
                workspace.currentRevision());
        assertEquals(0, workspace.metrics().cachedSyntaxEntries());
        assertEquals(0, workspace.metrics().cachedAnalysisEntries());
        workspace.analyze(request, CancellationToken.none());
        assertEquals(2, workspace.metrics().parses());
        assertEquals(0, workspace.metrics().cachedSyntaxEntries());
        assertTrue(workspace.metrics().evictions() > 0);
    }

    private static List<String> memberNames(CompiledSchema schema) {
        SymbolRef ref = schema.queries().findQualifiedName(
                QualifiedName.of("Survey", "Data", "Building")).getFirst();
        ClassSymbol clazz = assertInstanceOf(ClassSymbol.class,
                schema.queries().symbol(ref).known().orElseThrow());
        return schema.queries().effectiveMembers(ref).known().orElseThrow().stream()
                .map(EffectiveMember::declaredSymbol)
                .map(id -> new SymbolRef(schema.snapshotId(), id))
                .map(symbol -> schema.queries().symbol(symbol).known().orElseThrow().name().value())
                .toList();
    }
    @Test void translatedViewIncludesOriginalImportsAndSurvivesEdits() {
        for (String version : List.of("2.3", "2.4")) {
            Workspace workspace = WorkspaceTestSupport.workspace();
            String body = "IMPORTS Shared; TOPIC T = DEPENDS ON Shared.T; VIEW V EXTENDS Shared.T.V = END V; END T;";
            String shared = "INTERLIS " + version + "; MODEL Shared AT \"https://example.org\" VERSION \"1\" = TOPIC T = CLASS A = x : TEXT; END A; VIEW V PROJECTION OF A; = ALL OF A; END V; END T; END Shared.";
            WorkspaceTestSupport.add(workspace, "memory:/a-translation.ili", "INTERLIS " + version + "; MODEL M AT \"https://example.org\" VERSION \"1\" TRANSLATION OF Base [\"1\"] = " + body + " END M.");
            WorkspaceTestSupport.add(workspace, "memory:/z-base.ili", "INTERLIS " + version + "; MODEL Base AT \"https://example.org\" VERSION \"1\" = " + body + " END Base.");
            var id = WorkspaceTestSupport.add(workspace, "memory:/shared.ili", shared).id();
            var roots = List.<ModelSelector>of(new ModelSelector.ByName("M", Optional.empty(), Optional.empty()));
            for (String edit : List.of(shared, shared.replace("ALL OF A", "ALL OF Missing"), shared)) {
                WorkspaceTestSupport.replace(workspace, id, edit);
                var result = workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(), roots), CancellationToken.none());
                if (edit.equals(shared)) assertInstanceOf(CompilationOutcome.Succeeded.class, result, () -> describe(result));
                else assertInstanceOf(CompilationOutcome.Rejected.class, result);
            }
        }
    }

    @Test void missingTranslationOriginalCanBeAddedWithoutEditingTheTranslation() {
        for (String version : List.of("2.3", "2.4")) {
            Workspace workspace = WorkspaceTestSupport.workspace();
            WorkspaceTestSupport.add(workspace, "memory:/translation.ili", "INTERLIS " + version + "; MODEL M AT \"https://example.org\" VERSION \"1\" TRANSLATION OF Base [\"1\"] = END M.");
            var roots = List.<ModelSelector>of(new ModelSelector.ByName("M", Optional.empty(), Optional.empty()));
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(), roots), CancellationToken.none()));
            assertTrue(rejected.rejection().reasons().contains(RejectionReason.MISSING_INPUT));
            WorkspaceTestSupport.add(workspace, "memory:/base.ili", "INTERLIS " + version + "; MODEL Base AT \"https://example.org\" VERSION \"1\" = END Base.");
            var result = workspace.compile(new WorkspaceCompileRequest(workspace.currentRevision(), roots), CancellationToken.none());
            assertInstanceOf(CompilationOutcome.Succeeded.class, result, () -> describe(result));
        }
    }

}
