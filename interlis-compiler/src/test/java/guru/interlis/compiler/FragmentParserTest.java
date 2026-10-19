package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.BinaryExpressionSyntax;
import guru.interlis.syntax.FragmentKind;
import guru.interlis.syntax.FragmentSnapshot;
import guru.interlis.syntax.PathExpressionSyntax;
import guru.interlis.syntax.SyntaxService;
import guru.interlis.syntax.SyntaxServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FragmentParserTest {
    private static SourceText source(String text) {
        return new SourceText(new SourceId("memory:/fragment.ili"), new SourceRevision(1), text);
    }

    private static FragmentSnapshot fragment(String text, FragmentKind kind, int start, int end) {
        SourceText source = source(text);
        return SyntaxServices.create().parseFragment(source, source.range(start, end), kind,
                LanguageVersion.ILI24, CancellationToken.none());
    }

    @Test
    void parsesExpressionWithOriginalOffsets() {
        FragmentSnapshot snapshot = fragment("xx height + 1 yy", FragmentKind.EXPRESSION, 3, 13);
        assertInstanceOf(BinaryExpressionSyntax.class, snapshot.root());
        assertTrue(snapshot.consumedEntireFragment());
        assertTrue(snapshot.diagnostics().isEmpty(), () -> snapshot.diagnostics().toString());
        assertEquals(3, snapshot.root().range().startOffset());
        assertEquals(13, snapshot.root().range().endOffset());
    }

    @Test
    void parsesObjectPath() {
        FragmentSnapshot snapshot = fragment("this -> height", FragmentKind.OBJECT_PATH, 0, 14);
        assertInstanceOf(PathExpressionSyntax.class, snapshot.root());
        assertTrue(snapshot.consumedEntireFragment());
        assertTrue(snapshot.diagnostics().isEmpty(), () -> snapshot.diagnostics().toString());
    }

    @Test
    void parsesAttributeDeclaration() {
        FragmentSnapshot snapshot = fragment("height : MANDATORY TEXT*20;",
                FragmentKind.ATTRIBUTE_DECLARATION, 0, 27);
        assertInstanceOf(AttributeDeclarationSyntax.class, snapshot.root());
        assertTrue(snapshot.consumedEntireFragment());
        assertTrue(snapshot.diagnostics().isEmpty(), () -> snapshot.diagnostics().toString());
    }

    @Test
    void reportsTrailingContent() {
        FragmentSnapshot snapshot = fragment("height + 1 garbage", FragmentKind.EXPRESSION, 0, 18);
        assertTrue(!snapshot.consumedEntireFragment());
        assertTrue(snapshot.diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-SYN-0004")));
    }
}
