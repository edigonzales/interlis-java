package guru.interlis.compiler;

import guru.interlis.diagnostics.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** CLI2: selection is a compiler contract, independent of presentation and reuse. */
class RequestDiagnosticsContractTest {
    static final CancellationToken TOKEN = CancellationToken.none();
    static String model(String name, String body) {
        return "MODEL " + name + " AT \"x\" VERSION \"1\" = " + body + " END " + name + ".";
    }
    static String broken(String name) {
        return model(name, "TOPIC T = CLASS C = value: Missing; END C; END T;");
    }
    static SourceText source(String name, String models) {
        return CompilerTestSupport.source("memory:/" + name, "INTERLIS 2.4; " + models);
    }
    static CompilationRequest input(String root, List<SourceText> sources, WarningPolicy warnings) {
        var data = new LinkedHashMap<SourceId, SourceText>(); sources.forEach(s -> data.put(s.id(), s));
        var d = CompilerOptions.defaults();
        return CompilerTestSupport.request(List.of(root), data, List.of(), new CompilerOptions(
                d.semanticProfile(), d.allowedLanguages(), warnings, d.budget(), d.overlays()));
    }
    static AnalysisSnapshot analysis(CompilationOutcome out) {
        return switch (out) {
            case CompilationOutcome.Succeeded s -> s.analysis();
            case CompilationOutcome.Rejected r -> r.analysis();
            default -> throw new AssertionError(out);
        };
    }

    @Test void unrelatedSemanticErrorsStayCanonicalButAreNotRequestDiagnostics() {
        for (var sources : List.of(List.of(source("shared", model("Good", "") + broken("Bad"))),
                List.of(source("good", model("Good", "")), source("bad", broken("Bad"))))) {
            var out = CompilerEngines.create().compile(input("Good", sources, WarningPolicy.ALLOW_WARNINGS), TOKEN);
            var a = assertInstanceOf(CompilationOutcome.Succeeded.class, out).analysis();
            assertFalse(a.diagnostics().isEmpty()); assertTrue(a.requestDiagnostics().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> a.requestDiagnostics().clear());
            var bad = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create()
                    .compile(input("Bad", sources, WarningPolicy.ALLOW_WARNINGS), TOKEN));
            assertFalse(bad.analysis().requestDiagnostics().isEmpty());
            assertEquals(bad.analysis().requestDiagnostics().stream().map(Diagnostic::id).sorted(Comparator.comparing(DiagnosticId::value)).toList(), bad.rejection().diagnosticIds());
            for (var diagnostic : bad.analysis().requestDiagnostics())
                assertSame(diagnostic, bad.analysis().diagnostics().stream().filter(d -> d.id().equals(diagnostic.id())).findFirst().orElseThrow());
        }
    }

    @Test void foreignParserWarningsCannotRejectTheSelectedModel() {
        var shared = source("warnings", model("Good", "") + model("Other", "TOPIC T (ABSTRACT, ABSTRACT) = END T;"));
        var out = assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create()
                .compile(input("Good", List.of(shared), WarningPolicy.REJECT_WARNINGS), TOKEN));
        assertTrue(out.analysis().diagnostics().stream().anyMatch(d -> d.severity() == Severity.WARNING));
        assertTrue(out.analysis().requestDiagnostics().isEmpty());
        var relevant = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create()
                .compile(input("Other", List.of(shared), WarningPolicy.REJECT_WARNINGS), TOKEN));
        assertTrue(relevant.rejection().reasons().contains(RejectionReason.WARNINGS_REJECTED));
        assertTrue(relevant.analysis().requestDiagnostics().stream().anyMatch(d -> d.severity() == Severity.WARNING));
        assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create()
                .compile(input("Other", List.of(shared), WarningPolicy.ALLOW_WARNINGS), TOKEN));
    }

    @Test void requiredFileSyntaxErrorsStayVisibleEvenOutsideTheSelectedModelRange() {
        var good = source("good", model("Good", ""));
        var bad = source("bad", broken("Bad") + " GARBAGE");
        var separate = assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create()
                .compile(input("Good", List.of(good, bad), WarningPolicy.ALLOW_WARNINGS), TOKEN));
        assertFalse(separate.analysis().diagnostics().isEmpty()); assertTrue(separate.analysis().requestDiagnostics().isEmpty());
        var together = source("shared", model("Good", "") + broken("Bad") + " GARBAGE");
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create()
                .compile(input("Good", List.of(together), WarningPolicy.ALLOW_WARNINGS), TOKEN));
        assertTrue(rejected.analysis().requestDiagnostics().stream().anyMatch(d -> d.phase() == Phase.PARSING));
    }

    @Test void importsAndMissingOrAmbiguousRootsRemainVisible() {
        var root = source("root", model("Root", "IMPORTS Base;")); var base = source("base", broken("Base"));
        var original = input("Root", List.of(root, base), WarningPolicy.ALLOW_WARNINGS);
        var bound = CompilerTestSupport.request(List.of("Root"), original.sources().sources(),
                List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(root), "Base", base, CompilerTestSupport.parse(base))), original.options());
        var imported = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(bound, TOKEN));
        assertTrue(imported.analysis().requestDiagnostics().stream().anyMatch(d -> d.primaryRange().map(r -> r.source().equals(base.id())).orElse(false)));
        for (var request : List.of(input("Missing", List.of(root), WarningPolicy.ALLOW_WARNINGS),
                input("Root", List.of(root, source("other", model("Root", ""))), WarningPolicy.ALLOW_WARNINGS), original)) {
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request, TOKEN));
            assertTrue(rejected.analysis().requestDiagnostics().stream().anyMatch(d -> d.phase() == Phase.SOURCE_RESOLUTION), rejected.analysis().diagnostics().toString());
        }
    }

    @Test void unlocatedInputFailuresAreConservativeGlobals() {
        var source = source("good", model("Good", "")); var input = input("Good", List.of(source), WarningPolicy.ALLOW_WARNINGS);
        var global = new Diagnostic(new DiagnosticId("external-input"), new DiagnosticCode("ILI-RES-0003"), Severity.ERROR,
                Phase.SOURCE_RESOLUTION, "diagnostic.resolution.source", List.of(new DiagnosticArgument.TextArgument("source", "memory:/missing"),
                new DiagnosticArgument.TextArgument("reason", "not available")), Optional.empty(), List.of(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(), List.of());
        var request = new CompilationRequest(input.roots(), SourceSet.of(input.sources().sources(), List.of(), List.of(global), ResolutionFingerprint.none()), input.options());
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request, TOKEN));
        assertTrue(rejected.analysis().requestDiagnostics().stream().anyMatch(d -> d.code().equals(global.code()) && d.arguments().equals(global.arguments()) && d.primaryRange().isEmpty()));
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.ENVIRONMENT_FAILURE));
    }

    @Test void rootAndRevisionSwitchesWithReuseMatchFreshAndLeavePublishedSelectionsUntouched() {
        var engine = CompilerEngines.create(); AnalysisSnapshot previous = null; AnalysisSnapshot first = null;
        for (int i = 0; i < 4; i++) {
            var old = source("shared", model("Good", "") + broken("Bad"));
            var source = new SourceText(old.id(), new SourceRevision(i + 1), "!! 🦊\r\n".repeat(i) + old.text());
            var request = input(i % 2 == 0 ? "Good" : "Bad", List.of(source), WarningPolicy.REJECT_WARNINGS);
            var fresh = analysis(CompilerEngines.create().compile(request, TOKEN));
            var reuse = analysis(engine.compile(request, previous == null ? AnalysisReuse.none() : new AnalysisReuse(Optional.of(previous), previous.syntaxSnapshots()), TOKEN));
            assertEquals(fresh.diagnostics(), reuse.diagnostics()); assertEquals(fresh.requestDiagnostics(), reuse.requestDiagnostics());
            assertEquals(request.fingerprint(), reuse.fingerprint());
            for (var d : reuse.requestDiagnostics()) assertEquals(source.revision(), d.primaryRange().orElseThrow().revision());
            if (first == null) first = reuse; assertTrue(first.requestDiagnostics().isEmpty()); previous = reuse;
        }
    }

    @Test void translationAndOverlayOriginalErrorsBelongToTheRequest() {
        var original = source("original", broken("Original"));
        var translated = source("translated", model("F", "TOPIC T=CLASS C=value:Missing;END C;END T;")
                .replace("VERSION \"1\" =", "VERSION \"1\" TRANSLATION OF Original[\"1\"] ="));
        var request = input("F", List.of(original, translated), WarningPolicy.ALLOW_WARNINGS);
        var binding = new TranslationBinding(TranslationSelectionContractTest.location(translated), TranslationSelectionContractTest.location(original));
        var explicit = new CompilationRequest(request.roots(), SourceSet.of(request.sources().sources(), List.of(), List.of(), ResolutionFingerprint.none(), List.of(binding)), request.options());
        var a = analysis(CompilerEngines.create().compile(explicit, TOKEN));
        assertTrue(a.requestDiagnostics().stream().anyMatch(d -> d.primaryRange().map(r -> r.source().equals(original.id())).orElse(false)));
        var plain = source("plain", model("F", "TOPIC T=CLASS C=value:Missing;END C;END T;"));
        var overlays = new SemanticOverlays(List.of(new SemanticOverlays.SemanticOverlay.TranslationOverlay(
                new SemanticOverlays.OverlaySource("translation", 1), SemanticOverlays.Target.named("F"), SemanticOverlays.Target.named("Original"))));
        var d = CompilerOptions.defaults();
        var overlayInput = input("F", List.of(original, plain), WarningPolicy.ALLOW_WARNINGS);
        var overlay = new CompilationRequest(overlayInput.roots(), overlayInput.sources(), new CompilerOptions(d.semanticProfile(), d.allowedLanguages(), d.warningPolicy(), d.budget(), overlays));
        var out = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(overlay, TOKEN));
        assertTrue(out.analysis().requestDiagnostics().stream().anyMatch(x -> x.primaryRange().map(r -> r.source().equals(original.id())).orElse(false)));
    }

    @Test void requestedSourcesKeepSyntaxErrorsWhenNoModelCanBeRecovered() {
        var damaged = source("no-model", "GARBAGE\r\n");
        for (var selector : List.of(new ModelSelector.BySource(damaged.id()),
                new ModelSelector.ByName("Lost", Optional.empty(), Optional.of(damaged.id())))) {
            var request = new CompilationRequest(List.of(selector), SourceSet.of(Map.of(damaged.id(), damaged)), CompilerOptions.defaults());
            var out = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request, TOKEN));
            assertTrue(out.analysis().requestDiagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SYN-0004")));
            assertFalse(out.rejection().diagnosticIds().isEmpty());
        }
        // An unassociated file cannot acquire a model identity from its basename.
        var named = input("Lost", List.of(damaged), WarningPolicy.ALLOW_WARNINGS);
        var out = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(named, TOKEN));
        assertTrue(out.analysis().requestDiagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-RES-0001")));
        assertFalse(out.analysis().requestDiagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SYN-0004")));
    }

    @Test void aResolvedImportKeepsItsRequiredSyntaxSourceEvenWhenThePromisedModelIsLost() {
        var root = source("root", model("Root", "IMPORTS Dep;")); var damaged = source("Dep.ili", "GARBAGE\r\n");
        var syntax = CompilerTestSupport.parse(root); var site = syntax.root().models().getFirst().imports().getFirst();
        var binding = new ImportBinding(new ImportSite(site.range(), "Root", "Dep"), new ImportResolution.Resolved(
                new ModelLocation(damaged.id(), damaged.revision(), "Dep", Optional.of("1"), 14)));
        var request = CompilerTestSupport.request(List.of("Root"), Map.of(root.id(), root, damaged.id(), damaged), List.of(binding), CompilerOptions.defaults());
        var out = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(request, TOKEN));
        assertTrue(out.analysis().requestDiagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SYN-0004")
                && d.primaryRange().orElseThrow().source().equals(damaged.id())));
        assertTrue(out.analysis().requestDiagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-RES-0004")));
        var unassociated = new CompilationRequest(request.roots(), SourceSet.of(request.sources().sources()), request.options());
        var missing = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(unassociated, TOKEN));
        assertTrue(missing.analysis().requestDiagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-RES-0001")));
        assertFalse(missing.analysis().requestDiagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SYN-0004")));
    }

    @Test void namespaceConflictsCarryTheActualRevisionBoundDeclaration() {
        for (int shift : List.of(0, 2)) {
            var source = new SourceText(new SourceId("memory:/🦊.ili"), new SourceRevision(shift + 7),
                    "!! 🦊\r\n".repeat(shift) + "INTERLIS 2.4;\r\n" + model("F", "TOPIC Office =\r\n CLASS Office = END Office; END Office;"));
            var a = analysis(CompilerEngines.create().compile(input("F", List.of(source), WarningPolicy.ALLOW_WARNINGS), TOKEN));
            var d = a.requestDiagnostics().stream().filter(x -> x.code().value().equals("ILI-SEM-0010")).findFirst().orElseThrow();
            assertTrue(d.arguments().toString().contains("SCOPE_OUTER_TYPE_NAME"));
            var related = d.relatedLocations().getFirst().range(); assertEquals(source.id(), related.source()); assertEquals(source.revision(), related.revision());
            assertEquals(source.text().indexOf("Office"), related.startOffset()); assertEquals("Office", source.text().substring(related.startOffset(), related.endOffset()));
            assertTrue(d.primaryRange().orElseThrow().startOffset() > related.startOffset());
        }
    }
}
