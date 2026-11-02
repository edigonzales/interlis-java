package guru.interlis.compiler.query;

import guru.interlis.syntax.*;

import java.util.*;

/**
 * Shared adapter over the compiler's actual lossless syntax; never a text/regex declaration
 * scanner.
 */
final class EditorSyntax {
    private EditorSyntax() {}

    record Block(
            SyntaxNode node,
            String name,
            Optional<String> endName,
            boolean model,
            boolean viewTopic) {}

    static Optional<Block> block(SyntaxNode n) {
        return switch (n) {
            case ModelSyntax m -> Optional.of(new Block(n, m.name(), m.endName(), true, false));
            case TopicSyntax t ->
                    Optional.of(
                            new Block(
                                    n,
                                    t.name(),
                                    t.endName(),
                                    false,
                                    t.modifiers().contains(guru.interlis.model.Modifier.VIEW)));
            case ClassDeclarationSyntax c ->
                    Optional.of(new Block(n, c.name(), c.endName(), false, false));
            case StructureDeclarationSyntax s ->
                    Optional.of(new Block(n, s.name(), Optional.empty(), false, false));
            case AssociationDeclarationSyntax a ->
                    Optional.of(new Block(n, a.name(), a.endName(), false, false));
            case ViewDeclarationSyntax v ->
                    Optional.of(new Block(n, v.name(), v.endName(), false, false));
            case GraphicDeclarationSyntax g ->
                    Optional.of(new Block(n, g.name(), g.endName(), false, false));
            default -> Optional.empty();
        };
    }

    static Block withActualEnd(SyntaxSnapshot syntax, Block b) {
        var ts =
                tokens(syntax).stream()
                        .filter(
                                t ->
                                        t.range().startOffset() >= b.node.range().startOffset()
                                                && t.range().endOffset()
                                                        <= b.node.range().endOffset())
                        .toList();
        int childEnd =
                b.node.children().stream()
                        .mapToInt(n -> n.range().endOffset())
                        .max()
                        .orElse(b.node.range().startOffset());
        for (int i = ts.size() - 2; i >= 0; i--)
            if (ts.get(i).text().equals("END")
                    && ts.get(i).range().startOffset() >= childEnd
                    && ts.get(i + 1).kind() == TokenKind.IDENTIFIER)
                return new Block(
                        b.node, b.name, Optional.of(ts.get(i + 1).text()), b.model, b.viewTopic);
        return b;
    }

    static List<SyntaxNode> nodes(SyntaxSnapshot s) {
        var out = new ArrayList<SyntaxNode>();
        collect(s.root(), out);
        return out;
    }

    private static void collect(SyntaxNode n, List<SyntaxNode> out) {
        out.add(n);
        for (var c : n.children()) collect(c, out);
    }

    static List<SyntaxToken> tokens(SyntaxSnapshot s) {
        return s.tokens().stream()
                .filter(
                        t ->
                                t.origin() == TokenOrigin.SOURCE
                                        && t.kind() != TokenKind.EOF
                                        && t.channel() == 0
                                        && s.trivia().stream()
                                                .noneMatch(v -> v.range().equals(t.range())))
                .toList();
    }

    static boolean inert(SyntaxSnapshot s, int offset) {
        return s.trivia().stream()
                        .anyMatch(
                                t ->
                                        t.kind() != TriviaKind.WHITESPACE_GAP
                                                && t.range().startOffset() <= offset
                                                && offset <= t.range().endOffset())
                || s.tokens().stream()
                        .anyMatch(
                                t ->
                                        t.kind() == TokenKind.STRING
                                                && t.range().startOffset() < offset
                                                && offset < t.range().endOffset());
    }

    static Optional<Block> enclosing(SyntaxSnapshot s, int offset) {
        return nodes(s).stream()
                .flatMap(n -> block(n).stream())
                .map(b -> withActualEnd(s, b))
                .filter(
                        b ->
                                b.node.range().startOffset() <= offset
                                        && offset <= b.node.range().endOffset())
                .min(Comparator.comparingInt(b -> b.node.width()));
    }
}
