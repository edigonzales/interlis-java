package guru.interlis.compiler;

import guru.interlis.diagnostics.Phase;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.Modifier;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.DomainDeclarationSyntax;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.syntax.TopicSyntax;
import guru.interlis.syntax.UnitDeclarationSyntax;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeclarationSectionContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();

    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void declarationsRequireTheCorrectSection(String version) {
        for (String body : List.of("Code = TEXT;", "metre;", "UNIT Code = TEXT;")) {
            SourceText input = model(version, body);
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, compile(input));
            assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d ->
                    d.phase() == Phase.PARSING && d.primaryRange().orElseThrow().source().equals(input.id())));
        }
        for (String body : List.of("DOMAIN Code = TEXT;", "UNIT metre;", "UNIT metre; DOMAIN Code = TEXT;")) {
            var outcome = compile(model(version, body));
            assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, outcome::toString);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void sectionsFlattenInSourceOrderAndKeepAnnotations(String version) {
        SourceText source = model(version, """
                DOMAIN
                /** First domain */
                Kind (FINAL) = (one);
                Code = TEXT;
                UNIT
                metre;
                Pair = (metre * metre);
                DOMAIN
                TOPIC T =
                  UNIT
                  second;
                  minute = 60 [second];
                  DOMAIN
                  Local = TEXT;
                  Flag = BOOLEAN;
                END T;
                """);
        var outcome = compile(source);
        var success = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : outcome.toString());
        var members = success.analysis().syntaxSnapshots().get(source.id()).root().models().getFirst().members();
        assertEquals(5, members.size());
        var kind = assertInstanceOf(DomainDeclarationSyntax.class, members.get(0));
        assertEquals("Kind", source.slice(kind.nameRange().orElseThrow()));
        assertEquals("Kind (FINAL) = (one);", source.slice(kind.range()));
        assertEquals("Code", assertInstanceOf(DomainDeclarationSyntax.class, members.get(1)).name());
        assertEquals("metre", assertInstanceOf(UnitDeclarationSyntax.class, members.get(2)).name());
        assertEquals("Pair", assertInstanceOf(UnitDeclarationSyntax.class, members.get(3)).name());
        var topic = assertInstanceOf(TopicSyntax.class, members.get(4));
        assertEquals(4, topic.members().size());
        assertEquals("second", assertInstanceOf(UnitDeclarationSyntax.class, topic.members().get(0)).name());
        assertEquals("minute", assertInstanceOf(UnitDeclarationSyntax.class, topic.members().get(1)).name());
        assertEquals("Local", assertInstanceOf(DomainDeclarationSyntax.class, topic.members().get(2)).name());
        assertEquals("Flag", assertInstanceOf(DomainDeclarationSyntax.class, topic.members().get(3)).name());
        var symbol = success.schema().semanticGraph().symbols().stream()
                .filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast)
                .filter(d -> d.name().value().equals("Kind")).findFirst().orElseThrow();
        assertTrue(symbol.header().has(Modifier.FINAL));
        assertEquals("First domain", symbol.header().documentation().text());
    }

    @Test
    void damagedSectionReportsAnErrorAndEditorRetainsFollowingClass() {
        var source = model("2.4", "DOMAIN Broken = ; CLASS Next = END Next;");
        var service = SyntaxServices.create();
        for (var mode : List.of(ParseMode.EDITOR, ParseMode.STRICT)) {
            var syntax = service.parse(source, ParseOptions.defaults(mode), TOKEN);
            assertTrue(syntax.diagnostics().stream().anyMatch(d -> d.phase() == Phase.PARSING));
            if (mode == ParseMode.EDITOR) {
                assertTrue(syntax.root().models().getFirst().members().stream()
                        .anyMatch(member -> member instanceof ClassDeclarationSyntax c && c.name().equals("Next")));
            }
        }
    }

    private static SourceText model(String version, String body) {
        return CompilerTestSupport.source("memory:/sections.ili",
                "INTERLIS " + version + "; MODEL M AT \"https://example.org\" VERSION \"1\" =\n" + body + "\nEND M.");
    }

    private static CompilationOutcome compile(SourceText source) {
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("M"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults()), TOKEN);
    }
}
