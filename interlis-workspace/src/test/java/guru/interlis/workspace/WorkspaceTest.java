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
}
