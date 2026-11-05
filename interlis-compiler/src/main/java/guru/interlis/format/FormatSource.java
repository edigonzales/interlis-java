package guru.interlis.format;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.syntax.SyntaxToken;
import guru.interlis.syntax.TokenKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Request-local source index. Each token owns its original UTF-16 span and its preceding gap. */
final class FormatSource {
    record Atom(SyntaxToken token, String gap, List<String> endings, boolean protectedGap) {
        int start() { return token.range().startOffset(); }
        int end() { return token.range().endOffset(); }
        String text() { return token.text(); }
        boolean comment() { return token.channel() != 0; }
        boolean lineComment() { return comment() && text().startsWith("!!"); }
        boolean blockComment() { return comment() && text().startsWith("/*"); }
        boolean eof() { return token.kind() == TokenKind.EOF; }
    }

    private final List<Atom> atoms;
    private final String defaultEnding;
    private final String requestedEnding;
    private final List<FormattingNotice> notices = new ArrayList<>();

    FormatSource(SyntaxSnapshot syntax, FormatOptions options, CancellationToken cancellation) {
        String text = syntax.source().text();
        List<SourceRange> attributes = syntax.metadata().values().stream()
                .flatMap(group -> group.attributes().stream()).map(attribute -> attribute.range())
                .sorted(Comparator.comparingInt(SourceRange::startOffset)).toList();
        requestedEnding = switch (options.lineEnding()) {
            case LF -> "\n";
            case CRLF -> "\r\n";
            case PRESERVE -> null;
        };

        var parsed = new ArrayList<Atom>();
        var counts = new java.util.LinkedHashMap<String, Integer>();
        int end = 0;
        int attribute = 0;
        for (SyntaxToken token : syntax.tokens()) {
            cancellation.throwIfCancellationRequested();
            int start = token.range().startOffset();
            String gap = text.substring(end, start);
            List<String> endings = lineEndings(gap);
            while (attribute < attributes.size() && attributes.get(attribute).endOffset() <= end) attribute++;
            boolean protectedGap = attribute < attributes.size()
                    && attributes.get(attribute).startOffset() < end
                    && start < attributes.get(attribute).endOffset();
            if (!protectedGap) count(counts, endings);
            var atom = new Atom(token, gap, endings, protectedGap);
            parsed.add(atom);
            List<String> internalEndings = lineEndings(token.text());
            if (atom.blockComment()) count(counts, internalEndings);
            if (!atom.comment() && differs(internalEndings)) notice(token.range());
            end = token.range().endOffset();
        }
        for (SourceRange range : attributes) {
            cancellation.throwIfCancellationRequested();
            if (differs(lineEndings(text.substring(range.startOffset(), range.endOffset())))) notice(range);
        }
        notices.sort(Comparator.comparingInt(n -> n.range().orElseThrow().startOffset()));
        atoms = List.copyOf(parsed);
        defaultEnding = counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse("\n");
    }

    List<Atom> atoms() { return atoms; }
    List<FormattingNotice> notices() { return List.copyOf(notices); }

    String ending(Atom atom, int index) {
        if (requestedEnding != null) return requestedEnding;
        if (index < atom.endings().size()) return atom.endings().get(index);
        return defaultEnding;
    }

    private boolean differs(List<String> endings) {
        return requestedEnding != null && endings.stream().anyMatch(e -> !e.equals(requestedEnding));
    }

    private void notice(SourceRange range) {
        notices.add(new FormattingNotice("VALUE_LINE_ENDING_PRESERVED",
                "Original line endings inside a value were preserved to retain its meaning.", Optional.of(range)));
    }

    private static void count(java.util.Map<String, Integer> counts, List<String> endings) {
        for (String ending : endings) counts.merge(ending, 1, Integer::sum);
    }

    static List<String> lineEndings(String text) {
        var endings = new ArrayList<String>();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    endings.add("\r\n");
                    i++;
                } else {
                    endings.add("\r");
                }
            } else if (text.charAt(i) == '\n') {
                endings.add("\n");
            }
        }
        return List.copyOf(endings);
    }
}
