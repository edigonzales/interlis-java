package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static guru.interlis.compiler.CompilerTestSupport.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2R3CaseSensitivityCounterProbeTest {
    private static guru.interlis.compiler.CompilationRequest input(String version, String body) {
        var source = new SourceText(new SourceId("memory:/a2-r3-case.ili"), new SourceRevision(17),
                "INTERLIS " + version + "; MODEL F AT \"https://example.org\" VERSION \"1\" = "
                        + "TOPIC T = " + body + " END T; END F.");
        return request(List.of("F"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
    }

    @Test void exactCaseControlsIdentityBindingAndPublicNavigation() {
        for (String version : List.of("2.3", "2.4")) {
            var requested = input(version,
                    "CLASS A = END A; CLASS a = END a; "
                            + "CLASS Upper EXTENDS A = END Upper; CLASS Lower EXTENDS a = END Lower;");
            var outcome = CompilerEngines.create().compile(requested, CancellationToken.none());
            var succeeded = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome);
            var graph = succeeded.schema().semanticGraph();
            var upperTarget = graph.symbols().stream().filter(ClassSymbol.class::isInstance)
                    .map(ClassSymbol.class::cast).filter(symbol -> symbol.name().value().equals("A"))
                    .findFirst().orElseThrow();
            var lowerTarget = graph.symbols().stream().filter(ClassSymbol.class::isInstance)
                    .map(ClassSymbol.class::cast).filter(symbol -> symbol.name().value().equals("a"))
                    .findFirst().orElseThrow();
            assertNotEquals(upperTarget.id(), lowerTarget.id());
            var source = succeeded.analysis().syntaxSnapshots().values().iterator().next().source();
            assertEquals("A", source.slice(upperTarget.nameRange().orElseThrow()));
            assertEquals("a", source.slice(lowerTarget.nameRange().orElseThrow()));

            for (var target : List.of(upperTarget, lowerTarget)) {
                String needle = "EXTENDS " + target.name().value();
                int start = source.text().indexOf(needle) + "EXTENDS ".length();
                var cursor = source.range(start, start);
                assertEquals(target.id(), LanguageQueries.create().symbolAt(
                        succeeded.analysis(), cursor, CancellationToken.none()).known().orElseThrow().symbol());
                assertEquals(target.id(), LanguageQueries.create().definition(
                        succeeded.analysis(), cursor, CancellationToken.none()).known().orElseThrow().symbol().symbol());
            }

            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                    CompilerEngines.create().compile(input(version,
                            "CLASS missingcase = END missingcase; CLASS Wrong EXTENDS MissingCase = END Wrong;"),
                            CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejected.rejection().reasons());
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(diagnostic ->
                    diagnostic.code().value().equals("ILI-SEM-0001")
                            && diagnostic.arguments().toString().contains("MissingCase")));
            var badSource = rejected.analysis().syntaxSnapshots().values().iterator().next().source();
            assertTrue(rejected.analysis().semanticGraph().references().stream()
                    .filter(reference -> badSource.slice(reference.range()).equals("MissingCase"))
                    .anyMatch(reference -> reference.segments().stream().anyMatch(segment -> segment.target().isEmpty())));
            assertFalse(rejected.analysis().diagnostics().isEmpty());
        }
    }
}
