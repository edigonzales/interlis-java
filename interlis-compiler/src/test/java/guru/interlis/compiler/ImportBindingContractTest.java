package guru.interlis.compiler;

import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.TextType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportBindingContractTest {
    private static final String BASE = """
            INTERLIS 2.4;
            MODEL Base AT "https://example.interlis.ch" VERSION "1.0" =
              DOMAIN Count = 0..999;
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
                END Building;
              END Data;
            END Survey.
            """;

    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void resolvedImportMustNameTheActualRequestedModel(String version) {
        SourceText root = CompilerTestSupport.source("memory:/root.ili", """
                INTERLIS %s;
                MODEL Root AT "https://example.org" VERSION "1" =
                  IMPORTS UNQUALIFIED Wanted;
                  STRUCTURE Holder = value : D; END Holder;
                END Root.
                """.formatted(version));
        SourceText targets = CompilerTestSupport.source("memory:/targets.ili", """
                INTERLIS %s;
                MODEL Wanted AT "https://example.org" VERSION "1" =
                  DOMAIN D = TEXT*30;
                END Wanted.
                MODEL Other AT "https://example.org" VERSION "1" =
                  DOMAIN D = 0..10;
                END Other.
                """.formatted(version));
        var syntax = CompilerTestSupport.parse(targets);
        var correct = CompilerTestSupport.binding(CompilerTestSupport.parse(root), "Wanted", targets, syntax);
        var other = syntax.root().models().getLast();
        var wrong = new ImportBinding(correct.site(), new ImportResolution.Resolved(new ModelLocation(
                targets.id(), targets.revision(), other.name(), Optional.of(other.modelVersion()),
                other.range().startOffset())));
        var misleading = new ImportBinding(correct.site(), new ImportResolution.Resolved(new ModelLocation(
                targets.id(), targets.revision(), "Wanted", Optional.of(other.modelVersion()),
                other.range().startOffset())));
        var sources = Map.of(root.id(), root, targets.id(), targets);
        var initial = CompilerTestSupport.request(List.of("Root"), sources, List.of(correct), CompilerOptions.defaults());
        var changed = new CompilationRequest(initial.roots(), SourceSet.of(sources, List.of(wrong), List.of(),
                new ResolutionFingerprint("test-resolver/wrong-target")), initial.options());
        var engine = CompilerEngines.create();
        var token = CancellationToken.none();
        var initialOutcome = engine.compile(initial, token);
        var good = assertInstanceOf(CompilationOutcome.Succeeded.class, initialOutcome, () -> initialOutcome instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().diagnostics().toString() : initialOutcome.toString());
        var graph = good.schema().semanticGraph();
        var domain = graph.symbols().stream().filter(DomainSymbol.class::isInstance)
                .map(DomainSymbol.class::cast).filter(d -> d.name().value().equals("D"))
                .findFirst().orElseThrow();
        var type = assertInstanceOf(TextType.class, graph.type(domain.typeId()).orElseThrow());
        assertEquals(30, type.maxLength().orElseThrow());

        var reuse = new AnalysisReuse(Optional.of(good.analysis()), good.analysis().syntaxSnapshots());
        var wrongMetadata = new CompilationRequest(initial.roots(), SourceSet.of(sources, List.of(misleading),
                List.of(), new ResolutionFingerprint("test-resolver/wrong-metadata")), initial.options());
        for (var outcome : List.of(engine.compile(changed, token), engine.compile(changed, reuse, token),
                engine.compile(wrongMetadata, token))) {
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, outcome);
            assertTrue(rejected.rejection().reasons().contains(RejectionReason.MISSING_INPUT));
            var identity = rejected.analysis().diagnostics().stream()
                    .filter(d -> d.code().value().equals("ILI-RES-0004")).findFirst().orElseThrow();
            assertEquals(correct.site().nameRange(), identity.primaryRange().orElseThrow());
            assertTrue(identity.arguments().contains(new DiagnosticArgument.IdentifierArgument("expected", "Wanted")));
            assertTrue(identity.arguments().stream().anyMatch(argument ->
                    argument instanceof DiagnosticArgument.IdentifierListArgument actual
                            && actual.name().equals("actual")
                            && actual.values().getFirst().contains("@1:Other")));

        }
        assertEquals(type, graph.type(domain.typeId()).orElseThrow());
    }

    @Test
    void staleTargetRevisionIsRejected() {
        SourceText base = CompilerTestSupport.source("memory:/stale-base.ili", BASE);
        SourceText survey = CompilerTestSupport.source("memory:/stale-survey.ili", SURVEY);
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        ImportBinding correct = CompilerTestSupport.binding(surveySyntax, "Base", base, baseSyntax);
        ImportResolution.Resolved resolved = (ImportResolution.Resolved) correct.resolution();
        ImportBinding stale = new ImportBinding(correct.site(),
                new ImportResolution.Resolved(new ModelLocation(resolved.target().source(),
                        new SourceRevision(resolved.target().revision().value() + 1),
                        resolved.target().modelName(), resolved.target().modelVersion(),
                        resolved.target().declarationOffset())));
        Map<SourceId, SourceText> sources = new LinkedHashMap<>();
        sources.put(base.id(), base);
        sources.put(survey.id(), survey);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Survey", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(sources, List.of(stale), List.of(),
                        new ResolutionFingerprint("test-resolver/stale")),
                CompilerOptions.defaults());
        CompilationOutcome outcome = CompilerEngines.create().compile(request,
                CancellationToken.none());
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class, outcome);
        assertTrue(rejected.analysis().diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-RES-0004")
                        || d.code().value().equals("ILI-RES-0001")));
    }
}
