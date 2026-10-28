package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static guru.interlis.compiler.CompilerTestSupport.request;
import static org.junit.jupiter.api.Assertions.*;

class LexicalRecoveryContractTest {
    private SourceText source(String version, String body) {
        return new SourceText(new SourceId("memory:/lexical-recovery.ili"), new SourceRevision(1),
                "INTERLIS " + version + "; MODEL F AT \"https://example.org\" VERSION \"1\" = "
                        + body + " END F.");
    }

    @Test void lineAndBlockCommentsFollowTheirDelimitersWithoutConsumingFollowingSource() {
        for (String version : List.of("2.3", "2.4")) {
            for (String lineEnding : List.of("\n", "\r\n")) {
                String comment = "!! äöü 😀";
                var source = source(version, comment + lineEnding + "TOPIC T = CLASS C = END C; END T;");
                for (var mode : ParseMode.values()) {
                    var snapshot = SyntaxServices.create().parse(source, ParseOptions.defaults(mode), CancellationToken.none());
                    assertTrue(snapshot.diagnostics().isEmpty(), () -> version + " " + mode + " " + snapshot.diagnostics());
                    assertTrue(snapshot.reconstruct().equals(source.text()));
                    assertTrue(snapshot.trivia().stream().anyMatch(t -> t.text().equals(comment)
                            && source.slice(t.range()).equals(comment)));
                }
                var result = CompilerEngines.create().compile(
                        request(List.of("F"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults()),
                        CancellationToken.none());
                assertInstanceOf(CompilationOutcome.Succeeded.class, result);
            }
            for (String notAComment : List.of("! only one", "! ! separated")) {
                var source = source(version, notAComment + "\nTOPIC T = CLASS C = END C; END T;");
                assertFalse(SyntaxServices.create().parse(source, ParseOptions.defaults(ParseMode.STRICT),
                        CancellationToken.none()).diagnostics().isEmpty(), version + ":" + notAComment);
                var result = CompilerEngines.create().compile(
                        request(List.of("F"), Map.of(source.id(), source), List.of(), CompilerOptions.defaults()),
                        CancellationToken.none());
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, result);
                assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
            }
            String block = "/* first\r\nsecond ä😀 */";
            var blockSource = source(version, block + "\nTOPIC T = CLASS C = END C; END T;");
            for (var mode : ParseMode.values()) {
                var snapshot = SyntaxServices.create().parse(blockSource, ParseOptions.defaults(mode), CancellationToken.none());
                assertTrue(snapshot.diagnostics().isEmpty(), () -> version + " " + mode + " " + snapshot.diagnostics());
                assertEquals(blockSource.text(), snapshot.reconstruct());
                assertTrue(snapshot.trivia().stream().anyMatch(t -> t.text().equals(block)
                        && blockSource.slice(t.range()).equals(block)));
            }
        }
    }
    @Test void nestedBlockCommentsArePreservedAndSimpleUnterminatedCommentsAreRejected() {
        for (String version : List.of("2.3", "2.4")) {
            String comment = "/* outer ä !! text\r\n /* inner 😀 */ end */";
            var valid = source(version, comment + " TOPIC T = CLASS C = END C; END T;");
            for (var mode : ParseMode.values()) {
                var snapshot = SyntaxServices.create().parse(valid, ParseOptions.defaults(mode), CancellationToken.none());
                assertTrue(snapshot.diagnostics().isEmpty(), () -> version + " " + mode + " " + snapshot.diagnostics());
                assertEquals(valid.text(), snapshot.reconstruct());
                assertTrue(snapshot.trivia().stream().anyMatch(t -> t.text().equals(comment)
                        && valid.slice(t.range()).equals(comment)));
            }
            var request = request(List.of("F"), Map.of(valid.id(), valid), List.of(), CompilerOptions.defaults());
            assertInstanceOf(CompilationOutcome.Succeeded.class, CompilerEngines.create().compile(request, CancellationToken.none()));
            for (String unfinished : List.of("/* outer")) {
                var invalid = source(version, unfinished);
                for (var mode : ParseMode.values()) {
                    var snapshot = SyntaxServices.create().parse(invalid, ParseOptions.defaults(mode), CancellationToken.none());
                    assertFalse(snapshot.diagnostics().isEmpty(), () -> version + " " + mode + " " + unfinished);
                }
                var bad = request(List.of("F"), Map.of(invalid.id(), invalid), List.of(), CompilerOptions.defaults());
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, CompilerEngines.create().compile(bad, CancellationToken.none()));
                assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
            }
        }
    }
}
