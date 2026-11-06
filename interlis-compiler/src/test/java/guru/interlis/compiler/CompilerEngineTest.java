package guru.interlis.compiler;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.Phase;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AnalysisCoverage;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.CoverageStatus;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.NumericType;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.query.QueryResult;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerEngineTest {
    private static final String BASE = """
            INTERLIS 2.4;
            MODEL Base AT "https://example.interlis.ch" VERSION "1.0" =
              DOMAIN
                Count = 0..999;
                Label = TEXT*40;
              TOPIC Common = OID AS INTERLIS.UUIDOID;
                CLASS BaseBuilding =
                  height : Count;
                  label : Label;
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
                  ownerName : TEXT*40;
                  MANDATORY CONSTRAINT height > 0;
                END Building;
                ASSOCIATION OwnerLink =
                  building -- {0..1} Building;
                  ownerRole (EXTERNAL) -- {1} Base.Common.BaseBuilding;
                END OwnerLink;
              END Data;
            END Survey.
            """;

    @Test
    void validImportedDomainAndConstraintCompiles() {
        SourceText base = CompilerTestSupport.source("memory:/base.ili", BASE);
        SourceText survey = CompilerTestSupport.source("memory:/survey.ili", SURVEY);
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        assertTrue(baseSyntax.diagnostics().isEmpty(), () -> diagnostics(baseSyntax.diagnostics()));
        assertTrue(surveySyntax.diagnostics().isEmpty(), () -> diagnostics(surveySyntax.diagnostics()));
        CompilationRequest request = CompilerTestSupport.request(surveySyntax, baseSyntax,
                CompilerOptions.defaults());
        CompilationOutcome outcome = CompilerEngines.create().compile(request, CancellationToken.none());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class, outcome, () -> describe(outcome));
        var phases = List.of(Phase.LEXING, Phase.PARSING, Phase.DECLARATION_INDEXING, Phase.BINDING,
                Phase.TYPE_CHECKING, Phase.SEMANTIC_CHECKING);
        var expectedCoverage = List.of(survey.id(), base.id()).stream().flatMap(source ->
                phases.stream().map(phase -> new AnalysisCoverage.CoverageEntry(phase, Optional.of(source),
                        CoverageStatus.COMPLETE, Optional.empty(), Optional.empty()))).toList();
        assertEquals(expectedCoverage, succeeded.analysis().coverage().entries());

        CompiledSchema schema = succeeded.schema();
        assertEquals(1, schema.rootModels().size());
        assertEquals(2, schema.models().size());
        List<SymbolRef> classRefs = schema.queries().findQualifiedName(
                QualifiedName.of("Survey", "Data", "Building"));
        assertEquals(1, classRefs.size());
        SymbolRef buildingRef = classRefs.getFirst();
        ClassSymbol building = assertInstanceOf(ClassSymbol.class,
                schema.queries().symbol(buildingRef).known().orElseThrow());
        assertEquals("Building", building.name().value());
        List<EffectiveMember> members = schema.queries().effectiveMembers(buildingRef)
                .known().orElseThrow();
        List<String> names = members.stream()
                .map(m -> schema.queries().symbol(new SymbolRef(schema.snapshotId(),
                        m.declaredSymbol())).known().orElseThrow().name().value())
                .toList();
        assertEquals(List.of("height", "label", "ownerName"), names);
        assertEquals(1, members.stream().filter(EffectiveMember::overridden).count());

        SymbolRef height = new SymbolRef(schema.snapshotId(), members.getFirst().declaredSymbol());
        EffectiveType effective = schema.queries().effectiveType(height).known().orElseThrow();
        assertInstanceOf(NumericType.class, schema.queries().type(
                new guru.interlis.model.TypeRef(schema.snapshotId(), effective.effectiveType()))
                .known().orElseThrow());

        SymbolRef constraintRef = new SymbolRef(schema.snapshotId(),
                building.constraints().getFirst());
        ConstraintSymbol constraint = assertInstanceOf(ConstraintSymbol.class,
                schema.queries().symbol(constraintRef).known().orElseThrow());
        assertInstanceOf(MandatoryConstraint.class, constraint.definition());
    }

    @Test
    void unresolvedDomainRejectsClosure() {
        String broken = SURVEY.replace("height (EXTENDED) : Base.Count;", "height (EXTENDED) : Base.Missing;");
        SourceText base = CompilerTestSupport.source("memory:/base.ili", BASE);
        SourceText survey = CompilerTestSupport.source("memory:/survey.ili", broken);
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        CompilationRequest request = CompilerTestSupport.request(surveySyntax, baseSyntax,
                CompilerOptions.defaults());
        CompilationOutcome outcome = CompilerEngines.create().compile(request, CancellationToken.none());
        CompilationOutcome.Rejected rejected = assertInstanceOf(
                CompilationOutcome.Rejected.class, outcome, () -> describe(outcome));
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
        assertTrue(rejected.analysis().diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-SEM-0001")));
        var coverage = rejected.analysis().coverage();
        assertEquals(CoverageStatus.BLOCKED_BY_ERROR, coverage.entry(Phase.BINDING, Optional.of(survey.id())).status());
        assertEquals(CoverageStatus.COMPLETE, coverage.entry(Phase.BINDING, Optional.of(base.id())).status());
    }

    @Test
    void boundNonBooleanConstraintBlocksTypeChecking() {
        SourceText source = CompilerTestSupport.source("memory:/constraint.ili", """
                INTERLIS 2.4;
                MODEL F AT "x" VERSION "1" =
                  TOPIC T = CLASS C =
                    value : TEXT;
                    MANDATORY CONSTRAINT value;
                  END C; END T;
                END F.
                """);
        var request = CompilerTestSupport.request(List.of("F"), Map.of(source.id(), source),
                List.of(), CompilerOptions.defaults());
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        var analysis = rejected.analysis();
        assertTrue(analysis.requestDiagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SEM-0005")));
        assertEquals(CoverageStatus.COMPLETE, analysis.coverage().entry(Phase.BINDING, Optional.of(source.id())).status());
        assertEquals(CoverageStatus.BLOCKED_BY_ERROR,
                analysis.coverage().entry(Phase.TYPE_CHECKING, Optional.of(source.id())).status());
    }

    @Test
    void rejectedTypeDescriptorsBlockTypeCheckingEvenWithGenericRuleDiagnostics() {
        SourceText source = CompilerTestSupport.source("memory:/invalid-type.ili",
                "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = DOMAIN Empty = TEXT*0; END F.");
        var request = CompilerTestSupport.request(List.of("F"), Map.of(source.id(), source),
                List.of(), CompilerOptions.defaults());
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        var analysis = rejected.analysis();
        assertTrue(analysis.requestDiagnostics().stream().anyMatch(d -> d.phase() == Phase.SEMANTIC_CHECKING
                && d.arguments().toString().contains("TEXT_LENGTH")));
        assertEquals(CoverageStatus.BLOCKED_BY_ERROR,
                analysis.coverage().entry(Phase.TYPE_CHECKING, Optional.of(source.id())).status());
        assertEquals(CoverageStatus.COMPLETE,
                analysis.coverage().entry(Phase.BINDING, Optional.of(source.id())).status());
    }

    @Test
    void unsupportedExpressionHasAFeatureEntryWithTheOriginalSourceAndDiagnostic() {
        SourceText source = CompilerTestSupport.source("memory:/unsupported-expression.ili", """
                INTERLIS 2.4;
                MODEL F AT "x" VERSION "1" =
                  TOPIC T = CLASS C =
                    value : TEXT;
                    MANDATORY CONSTRAINT ALL OF value;
                  END C; END T;
                END F.
                """);
        var request = CompilerTestSupport.request(List.of("F"), Map.of(source.id(), source),
                List.of(), CompilerOptions.defaults());
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.INCOMPLETE_SUPPORT), rejected.rejection().reasons());
        var analysis = rejected.analysis();
        assertEquals(CoverageStatus.NOT_IMPLEMENTED,
                analysis.coverage().entry(Phase.SEMANTIC_CHECKING, Optional.of(source.id())).status());
        var entry = analysis.coverage().entries().stream()
                .filter(e -> e.featureId().equals(Optional.of("ALL_OF_PATH"))).findFirst().orElseThrow();
        assertEquals(Optional.of(source.id()), entry.source());
        assertEquals(CoverageStatus.NOT_IMPLEMENTED, entry.status());
        assertTrue(analysis.requestDiagnostics().stream().anyMatch(d -> Optional.of(d.id()).equals(entry.diagnosticId())
                && d.code().value().equals("ILI-IMPL-0001")));
    }

    @Test
    void unrelatedBrokenModelDoesNotBlock() {
        SourceText base = CompilerTestSupport.source("memory:/base.ili", BASE);
        SourceText survey = CompilerTestSupport.source("memory:/survey.ili", SURVEY);
        SourceText broken = CompilerTestSupport.source("memory:/broken.ili", """
                INTERLIS 2.4;
                MODEL Broken AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
    DEPENDS ON Base.Common;
                    CLASS BrokenClass =
                      missingType :
                    END BrokenClass;
                  END Data;
                END Broken.
                """);
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        CompilationRequest baseRequest = CompilerTestSupport.request(surveySyntax, baseSyntax,
                CompilerOptions.defaults());
        Map<SourceId, SourceText> sources = new LinkedHashMap<>(baseRequest.sources().sources());
        sources.put(broken.id(), broken);
        CompilationRequest request = new CompilationRequest(baseRequest.roots(),
                SourceSet.of(sources, baseRequest.sources().imports(), List.of(),
                        baseRequest.sources().fingerprint()), baseRequest.options());
        CompilationOutcome outcome = CompilerEngines.create().compile(request, CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, () -> describe(outcome));
    }

    @Test
    void trailingContentRejectsCompilation() {
        SourceText source = CompilerTestSupport.source("memory:/trailing.ili", """
                INTERLIS 2.4;
                MODEL Trailing AT "https://example.interlis.ch" VERSION "1.0" =
                END Trailing.
                GARBAGE
                """);
        SyntaxSnapshot syntax = CompilerTestSupport.parse(source);
        assertTrue(syntax.diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-SYN-0004")));
        CompilationRequest request = CompilerTestSupport.request(List.of("Trailing"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
        CompilationOutcome outcome = CompilerEngines.create().compile(request, CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Rejected.class, outcome, () -> describe(outcome));
    }

    @Test
    void projectionSchemaIsComplete() {
        SourceText source = CompilerTestSupport.source("memory:/view.ili", """
                INTERLIS 2.4;
                MODEL ViewModel AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
                    CLASS Building =
                      name : TEXT*20;
                    END Building;
                    VIEW V
                      PROJECTION OF Building;
                      =
                      ATTRIBUTE
                        ALL OF Building;
                    END V;
                  END Data;
                END ViewModel.
                """);
        CompilationRequest request = CompilerTestSupport.request(List.of("ViewModel"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
        CompilationOutcome outcome = CompilerEngines.create().compile(request, CancellationToken.none());
        assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, () -> describe(outcome));
    }

    private static String diagnostics(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(d -> d.code() + " " + d.arguments()).toList().toString();
    }

    private static String describe(CompilationOutcome outcome) {
        return switch (outcome) {
            case CompilationOutcome.Rejected rejected -> "rejected: " + rejected.rejection()
                    + " diagnostics=" + diagnostics(rejected.analysis().diagnostics());
            case CompilationOutcome.Cancelled cancelled -> "cancelled: " + cancelled.reason();
            case CompilationOutcome.Failed failed -> "failed: " + failed.failureId();
            case CompilationOutcome.Succeeded ignored -> "succeeded";
        };
    }
}
