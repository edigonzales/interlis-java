package guru.interlis.gui.application;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.RejectionReason;
import guru.interlis.compiler.WarningPolicy;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositorySource;
import guru.interlis.repository.SourceLocator;
import guru.interlis.source.SourceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CompileServiceTest {
    @TempDir
    Path directory;
    private final CompileService service = new CompileService();
    private final RunContextFactory contexts = RunContextFactory.defaults();

    @Test
    void allInputsAndExplicitRootsCheckTheCorrectModelsAndRetainTheRunSources() throws Exception {
        Path shared = write("shared models.ili", "INTERLIS 2.4; " + model("First", "") + model("Second", ""));
        Path unrelated = write("bad.ili", "INTERLIS 2.4; " + model("Unrelated", "DOMAIN D = UnknownType;"));
        var inspected = service.inspect(shared, directory, CancellationToken.none()).orElseThrow();
        assertEquals(List.of("First", "Second"), inspected.models().stream().map(RootChoice.Local::name).toList());
        var defaults = SessionOptions.defaults();

        var allValid = compile(List.of(shared), List.of(), defaults);
        var good = assertInstanceOf(CompilationOutcome.Succeeded.class, compiled(allValid));
        assertEquals(2, good.schema().rootModels().size());
        assertTrue(allValid.preparedClosure().orElseThrow().complete());

        var all = compile(List.of(shared, unrelated), List.of(), defaults);
        assertTrue(rejection(all).reasons().contains(RejectionReason.SOURCE_ERRORS));
        assertTrue(all.diagnostics().stream().anyMatch(d -> d.severity() == Severity.ERROR && (d.phase() == Phase.TYPE_CHECKING || d.phase() == Phase.BINDING)));
        var explicit = compile(List.of(shared, unrelated), List.of(inspected.models().getLast()), defaults);
        var chosen = assertInstanceOf(CompilationOutcome.Succeeded.class, compiled(explicit));
        assertEquals(1, chosen.schema().rootModels().size());
        assertTrue(explicit.diagnostics().isEmpty());
        assertEquals(2, explicit.sources().size());

        SourceId id = inspected.source().orElseThrow();
        var recorded = explicit.sources().get(id);
        Files.writeString(shared, "changed after the run");
        assertTrue(recorded.source().text().contains("MODEL Second"));
        assertEquals(recorded.source().revision(), chosen.analysis().sourceRevisions().get(id));
        assertTrue(recorded.digest().isPresent());
        assertThrows(UnsupportedOperationException.class, explicit.sources()::clear);
    }

    @Test
    void badFileHeaderAndMissingImportStayVisibleInTheirTypedOutcomes() throws Exception {
        Path invalid = write("header.ili", "THIS IS NOT AN INTERLIS HEADER");
        var inspected = service.inspect(invalid, directory, CancellationToken.none()).orElseThrow();
        assertTrue(inspected.models().isEmpty());
        var header = compile(List.of(invalid), List.of(), SessionOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class, compiled(header));
        assertTrue(header.diagnostics().stream().anyMatch(d -> d.primaryRange().map(range ->
                range.source().equals(inspected.source().orElseThrow())).orElse(false)));

        Path missing = write("import.ili", "INTERLIS 2.4; " + model("Root", "IMPORTS Absent;"));
        var unresolved = compile(List.of(missing), List.of(), SessionOptions.defaults());
        assertTrue(rejection(unresolved).reasons().contains(RejectionReason.MISSING_INPUT));
        assertFalse(unresolved.diagnostics().isEmpty());
        assertFalse(unresolved.preparedClosure().orElseThrow().complete());
    }

    @Test
    void warningPolicyAndHostFailuresStayDistinct() throws Exception {
        Path warning = write("warnings.ili", "INTERLIS 2.4; " + model("Warn", "TOPIC T (ABSTRACT, ABSTRACT) = END T;"));
        var defaults = SessionOptions.defaults();
        var allowed = compile(List.of(warning), List.of(), defaults);
        assertInstanceOf(CompilationOutcome.Succeeded.class, compiled(allowed));
        assertTrue(allowed.diagnostics().stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.WARNING));
        assertTrue(rejection(compile(List.of(warning), List.of(), defaults.withWarnings(WarningPolicy.REJECT_WARNINGS)))
                .reasons().contains(RejectionReason.WARNINGS_REJECTED));
        var unreadable = compile(List.of(warning, directory.resolve("does not exist.ili")), List.of(), defaults);
        var failed = assertInstanceOf(CompileResult.InputFailed.class, unreadable.outcome());
        assertEquals(1, failed.problems().size());
        assertEquals(1, unreadable.sources().size());

        var cancelled = service.compile(request(List.of(warning), List.of(), defaults),
                CancellationToken.requested(CancellationReason.USER_REQUESTED));
        assertEquals(new CompileResult.Cancelled(CancellationReason.USER_REQUESTED), cancelled.outcome());
    }

    @Test
    void existingHostResolverLoadsRepositoryClosureWithOriginalProvenance() throws Exception {
        Path repository = Files.createDirectory(directory.resolve("repo"));
        Files.writeString(repository.resolve("ilimodels.xml"), """
                <TRANSFER><ModelMetadata><Name>Dependency</Name><SchemaLanguage>ili2_4</SchemaLanguage>
                <File>Dependency.ili</File><Version>1</Version></ModelMetadata></TRANSFER>
                """);
        Path dependency = repository.resolve("Dependency.ili");
        Files.writeString(dependency, "INTERLIS 2.4; " + model("Dependency", "DOMAIN Kind = TEXT*20;"));
        Path root = write("root.ili", "INTERLIS 2.4; " + model("Root", "IMPORTS Dependency;"));
        var source = new RepositorySource(new RepositoryBase(repository.toUri()),
                new SourceLocator(repository.resolve("ilimodels.xml").toUri().toString()));
        var defaults = SessionOptions.defaults();
        var options = new SessionOptions(new RepositoryConfig(List.of(source), FetchPolicy.defaults()),
                defaults.cache(), defaults.proxy(), defaults.warnings(), defaults.diagnosticLanguage());

        var result = compile(List.of(root), List.of(), options);
        assertInstanceOf(CompilationOutcome.Succeeded.class, compiled(result));
        var captured = result.sources().get(new SourceId(dependency.toUri().toString()));
        assertEquals(CompileResult.Origin.REPOSITORY, captured.origin());
        assertTrue(captured.digest().isPresent());
        assertFalse(result.repositorySteps().isEmpty());
        assertTrue(result.repositoryLock().isPresent());

        var selected = new RootChoice.Repository("Dependency", "1", captured.source().id(), source.base());
        assertInstanceOf(CompilationOutcome.Succeeded.class, compiled(compile(List.of(), List.of(selected), options)));
        Files.delete(dependency);
        var missing = compile(List.of(root), List.of(), options);
        assertInstanceOf(CompileResult.RepositoryRejected.class, missing.outcome());
        assertFalse(missing.repositorySteps().isEmpty());
    }

    private CompileResult compile(List<Path> files, List<RootChoice> roots, SessionOptions options) {
        return service.compile(request(files, roots, options), CancellationToken.none());
    }

    private CompileRequest request(List<Path> files, List<RootChoice> roots, SessionOptions options) {
        return new CompileRequest(contexts.createRoot(), 1, new SessionInputs(files, roots), options, directory);
    }

    private static CompilationOutcome compiled(CompileResult result) {
        return assertInstanceOf(CompileResult.Compiled.class, result.outcome()).result();
    }

    private static guru.interlis.compiler.RejectionSummary rejection(CompileResult result) {
        return assertInstanceOf(CompilationOutcome.Rejected.class, compiled(result)).rejection();
    }

    private Path write(String name, String text) throws Exception {
        return Files.writeString(directory.resolve(name), text);
    }

    private static String model(String name, String body) {
        return " MODEL " + name + " AT \"https://example.invalid\" VERSION \"1\" = " + body + " END " + name + ". ";
    }
}
