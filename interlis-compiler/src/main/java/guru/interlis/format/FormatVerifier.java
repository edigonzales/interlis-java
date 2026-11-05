package guru.interlis.format;

import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.SourceMetadata;
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.syntax.SyntaxToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Checks the real reparse against the original UTF-16 token and annotation spans. */
final class FormatVerifier {
    private FormatVerifier() {
    }

    static boolean isComplete(SyntaxSnapshot syntax) {
        var coverage = syntax.coverage();
        return coverage.parseComplete() && coverage.lexicallyComplete()
                && coverage.consumedEntireInput() && coverage.recoveredRanges().isEmpty()
                && !coverage.budgetExceeded() && !coverage.cancelled()
                && syntax.diagnostics().stream().noneMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    static void verify(SyntaxSnapshot before, SyntaxSnapshot after, FormatOptions options,
                       CancellationToken cancellation) {
        require(isComplete(after) && before.language().version().equals(after.language().version())
                && before.coverage().unsupportedFeatures().equals(after.coverage().unsupportedFeatures()),
                "the output did not reparse with the original syntax coverage");
        var beforeSource = new FormatSource(before, options, cancellation);
        var afterSource = new FormatSource(after, options, cancellation);
        List<SyntaxToken> original = before.tokens();
        List<SyntaxToken> formatted = after.tokens();
        require(original.size() == formatted.size(), "the token count changed");
        Map<SourceRange, Integer> beforeOrdinals = new HashMap<>();
        Map<SourceRange, Integer> afterOrdinals = new HashMap<>();
        for (int ordinal = 0; ordinal < original.size(); ordinal++) {
            cancellation.throwIfCancellationRequested();
            var left = original.get(ordinal);
            var right = formatted.get(ordinal);
            String expected = left.text();
            if (left.channel() != 0 && expected.startsWith("/*")) {
                expected = normalizeCommentEndings(expected, options.lineEnding());
            }
            var oldAtom = beforeSource.atoms().get(ordinal);
            var newAtom = afterSource.atoms().get(ordinal);
            if (oldAtom.blockComment() && !FormatSource.lineEndings(left.text()).isEmpty()
                    && atMargin(oldAtom, ordinal) && atMargin(newAtom, ordinal)) {
                int delta = CommentLayout.column(newAtom) - CommentLayout.column(oldAtom);
                if (delta != 0) {
                    require(CommentLayout.spaceMargin(oldAtom) && CommentLayout.spaceMargin(newAtom),
                            "comment margin uses non-space indentation at ordinal " + ordinal);
                    expected = CommentLayout.shift(expected, delta).orElseThrow(() ->
                            new IllegalStateException("Formatter invariant: comment margin cannot shift uniformly"));
                }
            }
            require(left.kind() == right.kind() && left.channel() == right.channel()
                    && expected.equals(right.text()), "token or comment changed at ordinal " + ordinal);
            require(!oldAtom.comment() || ordinal == 0
                    || oldAtom.endings().isEmpty() == newAtom.endings().isEmpty(),
                    "comment changed between trailing and standalone at ordinal " + ordinal);
            beforeOrdinals.put(left.range(), ordinal);
            afterOrdinals.put(right.range(), ordinal);
        }
        var annotations = new HashMap<Integer, SourceMetadata>();
        after.metadata().forEach((range, metadata) -> annotations.put(afterOrdinals.get(range), metadata));
        require(before.metadata().size() == annotations.size(), "annotation count changed");
        for (var entry : before.metadata().entrySet()) {
            cancellation.throwIfCancellationRequested();
            Integer ordinal = beforeOrdinals.get(entry.getKey());
            var left = entry.getValue();
            var right = annotations.get(ordinal);
            require(ordinal != null && right != null
                    && left.attributes().size() == right.attributes().size()
                    && left.documentation().size() == right.documentation().size(),
                    "annotation binding changed");
            for (int i = 0; i < left.attributes().size(); i++) {
                var a = left.attributes().get(i);
                var b = right.attributes().get(i);
                require(a.key().equals(b.key()) && a.text().equals(b.text())
                        && originalText(before, a.range()).equals(originalText(after, b.range())),
                        "metadata value changed at ordinal " + ordinal);
            }
            for (int i = 0; i < left.documentation().size(); i++) {
                var a = left.documentation().get(i);
                var b = right.documentation().get(i);
                require(Objects.equals(beforeOrdinals.get(a.range()), afterOrdinals.get(b.range())),
                        "documentation binding changed at ordinal " + ordinal);
            }
        }
        cancellation.throwIfCancellationRequested();
    }

    private static boolean atMargin(FormatSource.Atom atom, int ordinal) {
        return ordinal == 0 || !atom.endings().isEmpty();
    }

    static String normalizeCommentEndings(String text, FormatOptions.LineEnding ending) {
        if (ending == FormatOptions.LineEnding.PRESERVE) return text;
        String lf = text.replace("\r\n", "\n").replace('\r', '\n');
        return ending == FormatOptions.LineEnding.LF ? lf : lf.replace("\n", "\r\n");
    }

    private static String originalText(SyntaxSnapshot syntax, SourceRange range) {
        return syntax.source().text().substring(range.startOffset(), range.endOffset());
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Formatter invariant: " + reason);
    }
}
