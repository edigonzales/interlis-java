package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.TopicSyntax;
import guru.interlis.syntax.TokenOrigin;
import guru.interlis.syntax.TriviaKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static guru.interlis.compiler.CompilerTestSupport.request;
import static org.junit.jupiter.api.Assertions.*;

class NestedBlockCommentContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void openOuterCommentAfterClosedInnerCommentNeverProducesCompiledSchema(String version) throws Exception {
        String original = Files.readString(Path.of(System.getProperty("testdata.dir"),
                "g2/a2-unterminated-nested-comment.ili"));
        var invalid = new SourceText(new SourceId("memory:/a2-unterminated-nested-comment.ili"),
                new SourceRevision(7), original.replace("INTERLIS 2.3;", "INTERLIS " + version + ";"));
        var outcome = CompilerEngines.create().compile(
                request(List.of("F"), Map.of(invalid.id(), invalid), List.of(), CompilerOptions.defaults()),
                CancellationToken.none());
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, outcome);
        assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void balancedCommentDelimitersPreserveTriviaAndFollowingDeclarations(String version) {
        for (String newline : List.of("\n", "\r\n")) {
            for (String comment : List.of("/**/", "/* ä😀" + newline + "!! text */",
                    "/* outer /* middle /* inner 😀 */ middle */ outer */",
                    "/* / // ** ***/", "/*/**/*/")) {
                var valid = source(version, "!! prefix 😀" + newline + comment + newline
                        + "TOPIC T = CLASS C = END C; END T;");
                int start = valid.text().indexOf(comment);
                for (var mode : ParseMode.values()) {
                    var syntax = SyntaxServices.create().parse(valid, ParseOptions.defaults(mode), CancellationToken.none());
                    assertTrue(syntax.diagnostics().isEmpty(), () -> version + " " + mode + " " + syntax.diagnostics());
                    assertTrue(syntax.coverage().consumedEntireInput());
                    assertTrue(syntax.coverage().lexicallyComplete());
                    assertTrue(syntax.coverage().parseComplete());
                    assertEquals(valid.text(), syntax.reconstruct());
                    var trivia = syntax.trivia().stream().filter(t -> t.text().equals(comment)).findFirst().orElseThrow();
                    assertEquals(TriviaKind.COMMENT, trivia.kind());
                    assertEquals(valid.range(start, start + comment.length()), trivia.range());
                    assertEquals(comment, valid.slice(trivia.range()));
                    var token = syntax.tokens().stream().filter(t -> t.text().equals(comment)).findFirst().orElseThrow();
                    assertEquals(TokenOrigin.SOURCE, token.origin());
                    assertEquals(trivia.range(), token.range());
                    var topic = assertInstanceOf(TopicSyntax.class, syntax.root().models().getFirst().members().getFirst());
                    var declaration = assertInstanceOf(ClassDeclarationSyntax.class, topic.members().getFirst());
                    assertEquals("C", declaration.name());
                    assertEquals("CLASS C = END C;", valid.slice(declaration.range()));
                }
                var outcome = CompilerEngines.create().compile(
                        request(List.of("F"), Map.of(valid.id(), valid), List.of(), CompilerOptions.defaults()),
                        CancellationToken.none());
                var success = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome);
                assertEquals(1, success.schema().rootModels().size());
                assertTrue(success.schema().semanticGraph().symbols().stream()
                        .anyMatch(s -> s.header().name().value().equals("C")));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void unfinishedCommentsHaveOriginalSyntaxErrorsInBothParseModes(String version) {
        for (String newline : List.of("\n", "\r\n")) {
            for (String comment : List.of("/* outer", "/* outer /* inner",
                    "/* outer /* inner */")) {
                var invalid = source(version, "!! prefix 😀" + newline + comment + newline
                        + "TOPIC T = CLASS C = END C; END T;");
                int start = invalid.text().indexOf("/*");
                for (var mode : ParseMode.values()) {
                    var syntax = SyntaxServices.create().parse(invalid, ParseOptions.defaults(mode), CancellationToken.none());
                    assertEquals(invalid.text(), syntax.reconstruct());
                    assertFalse(syntax.coverage().parseComplete());
                    // The incomplete comment is not a hidden token. The existing
                    // parser diagnostic identifies its original opening slash.
                    var diagnostic = syntax.diagnostics().stream()
                            .filter(d -> d.code().value().equals("ILI-SYN-0002")
                                    && d.primaryRange().orElseThrow().startOffset() == start)
                            .findFirst().orElseThrow(() -> new AssertionError(syntax.diagnostics()));
                    assertEquals(Phase.PARSING, diagnostic.phase());
                    assertEquals(Severity.ERROR, diagnostic.severity());
                    assertEquals(invalid.range(start, start + 1), diagnostic.primaryRange().orElseThrow());
                    assertTrue(syntax.trivia().stream().noneMatch(t -> t.range().startOffset() == start
                            && t.kind() == TriviaKind.COMMENT));
                    // Editor recovery can retain the enclosing model without
                    // claiming that the unfinished comment made the source valid.
                    assertEquals("F", syntax.root().models().getFirst().name());
                }
                var outcome = CompilerEngines.create().compile(
                        request(List.of("F"), Map.of(invalid.id(), invalid), List.of(), CompilerOptions.defaults()),
                        CancellationToken.none());
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, outcome);
                assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
                assertTrue(rejected.analysis().diagnostics().stream()
                        .anyMatch(d -> d.code().value().equals("ILI-SYN-0002") && d.phase() == Phase.PARSING));
            }
        }
    }

    private SourceText source(String version, String body) {
        return new SourceText(new SourceId("memory:/lexical-recovery.ili"), new SourceRevision(1),
                "INTERLIS " + version + "; MODEL F AT \"https://example.org\" VERSION \"1\" = "
                        + body + " END F.");
    }

}
