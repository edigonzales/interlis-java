package guru.interlis.format;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxService;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.syntax.SyntaxToken;
import guru.interlis.syntax.TokenKind;
import guru.interlis.syntax.TriviaKind;
import guru.interlis.syntax.TriviaSpan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Line-preserving token formatter. */
final class DefaultModelFormatter implements ModelFormatter {

    @Override
    public FormatOutcome format(guru.interlis.source.SourceText source, FormatOptions options,
                                CancellationToken cancellation) {
        java.util.Objects.requireNonNull(source, "source");
        java.util.Objects.requireNonNull(options, "options");
        java.util.Objects.requireNonNull(cancellation, "cancellation");
        try {
            cancellation.throwIfCancellationRequested();
            SyntaxSnapshot snapshot = SyntaxServices.create().parse(source,
                    ParseOptions.defaults(ParseMode.STRICT), cancellation);
            if (!snapshot.coverage().parseComplete()
                    || !snapshot.coverage().lexicallyComplete()
                    || !snapshot.coverage().consumedEntireInput()) {
                return new FormatOutcome.Rejected(snapshot.diagnostics());
            }
            List<Line> lines = splitLines(source.text());
            Map<Integer, List<TriviaSpan>> commentsByLine = new LinkedHashMap<>();
            Set<Integer> commentStarts = new java.util.HashSet<>();
            for (TriviaSpan trivia : snapshot.trivia()) {
                if (trivia.kind() == TriviaKind.COMMENT
                        || trivia.kind() == TriviaKind.DOCUMENTATION_COMMENT) {
                    int line = lineOf(lines, trivia.range().startOffset());
                    commentsByLine.computeIfAbsent(line, ignored -> new ArrayList<>()).add(trivia);
                    commentStarts.add(trivia.range().startOffset());
                }
            }
            Map<Integer, List<SyntaxToken>> tokensByLine = new LinkedHashMap<>();
            for (SyntaxToken token : snapshot.tokens()) {
                if (token.kind() == TokenKind.EOF
                        || commentStarts.contains(token.range().startOffset())) {
                    continue;
                }
                int line = lineOf(lines, token.range().startOffset());
                tokensByLine.computeIfAbsent(line, ignored -> new ArrayList<>()).add(token);
            }
            // A multiline token/comment owns its entire source span. Preserve intersecting
            // lines verbatim rather than emitting the span and its continuation lines twice.
            Set<Integer> protectedLines = new java.util.HashSet<>();
            Set<Integer> valueLineEndings = new java.util.HashSet<>();
            List<guru.interlis.format.FormattingNotice> notices = new ArrayList<>();
            for (var token : snapshot.tokens()) {
                protectLines(lines, token.range(), protectedLines);
                if (!commentStarts.contains(token.range().startOffset())) {
                    protectValueEndings(lines, token.range(), valueLineEndings, options, notices);
                }
            }
            for (var trivia : snapshot.trivia()) {
                if (trivia.kind() == TriviaKind.COMMENT
                        || trivia.kind() == TriviaKind.DOCUMENTATION_COMMENT) {
                    protectLines(lines, trivia.range(), protectedLines);
                }
            }
            for (var metadata : snapshot.metadata().values()) {
                for (var attribute : metadata.attributes()) {
                    protectLines(lines, attribute.range(), protectedLines);
                    protectValueEndings(lines, attribute.range(), valueLineEndings, options, notices);
                }
            }
            var indentation = indentationEvents(snapshot, cancellation);
            var events = indentation.entrySet().iterator();
            Map.Entry<Integer, Integer> next = events.hasNext() ? events.next() : null;
            int indent = 0;
            List<String> formattedLines = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                List<SyntaxToken> tokens = new ArrayList<>(tokensByLine.getOrDefault(i, List.of()));
                tokens.sort(Comparator.comparingInt(t -> t.range().startOffset()));
                List<TriviaSpan> comments = new ArrayList<>(
                        commentsByLine.getOrDefault(i, List.of()));
                comments.sort(Comparator.comparingInt(t -> t.range().startOffset()));
                if (tokens.isEmpty() && comments.isEmpty() && !protectedLines.contains(i)) {
                    formattedLines.add("");
                    continue;
                }
                int contentOffset = tokens.isEmpty() ? line.start() : tokens.getFirst().range().startOffset();
                if (!comments.isEmpty()) contentOffset = Math.min(contentOffset, comments.getFirst().range().startOffset());
                while (next != null && next.getKey() <= contentOffset) {
                    indent += next.getValue();
                    next = events.hasNext() ? events.next() : null;
                }
                cancellation.throwIfCancellationRequested();
                if (protectedLines.contains(i)) {
                    formattedLines.add(source.text().substring(line.start(), line.end()));
                } else {
                    List<Part> parts = new ArrayList<>();
                    for (SyntaxToken token : tokens) {
                        parts.add(new Part(token.range().startOffset(), token.text(), false));
                    }
                    for (TriviaSpan comment : comments) {
                        parts.add(new Part(comment.range().startOffset(), comment.text(), true));
                    }
                    parts.sort(Comparator.comparingInt(Part::offset));
                    StringBuilder builder = new StringBuilder(" ".repeat(indent * options.indentWidth()));
                    Part previous = null;
                    for (Part part : parts) {
                        if (previous != null && (previous.comment() || part.comment()
                                || needsSpace(previous.text(), part.text()))) {
                            builder.append(' ');
                        }
                        builder.append(part.text());
                        previous = part;
                    }
                    formattedLines.add(builder.toString());
                }
            }
            String lineEnding = switch (options.lineEnding()) {
                case LF -> "\n";
                case CRLF -> "\r\n";
                case PRESERVE -> null;
            };
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < formattedLines.size(); i++) {
                result.append(formattedLines.get(i));
                boolean last = i == formattedLines.size() - 1;
                String ending = valueLineEndings.contains(i) ? lines.get(i).ending()
                        : lineEnding != null ? lineEnding
                        : last ? "" : (lines.get(i).ending().isEmpty() ? "\n" : lines.get(i).ending());
                result.append(ending);
            }
            String text = result.toString();
            String finalEnding = lineEnding != null ? lineEnding : lines.stream()
                    .map(Line::ending).filter(ending -> !ending.isEmpty()).findFirst().orElse("\n");
            while (text.endsWith("\n") || text.endsWith("\r")) {
                text = text.substring(0, text.length() - 1);
            }
            if (options.finalNewline()) {
                text += finalEnding;
            }
            return new FormatOutcome.Formatted(text, notices);
        } catch (OperationCancelledException cancelled) {
            return new FormatOutcome.Cancelled(cancelled.reason());
        }
    }

    private static boolean needsSpace(String previous, String current) {
        if (previous.isEmpty() || current.isEmpty()) {
            return false;
        }
        if (current.equals(",") || current.equals(";") || current.equals(")")
                || current.equals("]") || current.equals(".") || current.equals(":")
                || current.equals("*") || previous.equals("*")) {
            return false;
        }
        if (previous.equals("(") || previous.equals("[") || previous.equals(".")) {
            return false;
        }
        return true;
    }

    private record Part(int offset, String text, boolean comment) { }

    private static void protectValueEndings(List<Line> lines, guru.interlis.source.SourceRange range,
                                            Set<Integer> protectedEndings, FormatOptions options,
                                            List<guru.interlis.format.FormattingNotice> notices) {
        int first = lineOf(lines, range.startOffset());
        int last = lineOf(lines, Math.max(range.startOffset(), range.endOffset() - 1));
        String requested = switch (options.lineEnding()) {
            case LF -> "\n";
            case CRLF -> "\r\n";
            case PRESERVE -> null;
        };
        boolean different = false;
        for (int line = first; line < last; line++) {
            protectedEndings.add(line);
            if (requested != null && !requested.equals(lines.get(line).ending())) different = true;
        }
        if (different) notices.add(new guru.interlis.format.FormattingNotice("VALUE_LINE_ENDING_PRESERVED",
                "Original line endings inside a value were preserved to retain its meaning.", Optional.of(range)));
    }

    private static void protectLines(List<Line> lines, guru.interlis.source.SourceRange range,
                                     Set<Integer> protectedLines) {
        int first = lineOf(lines, range.startOffset());
        int last = lineOf(lines, Math.max(range.startOffset(), range.endOffset() - 1));
        if (last > first) {
            for (int line = first; line <= last; line++) {
                protectedLines.add(line);
            }
        }
    }

    private static java.util.SortedMap<Integer, Integer> indentationEvents(
            SyntaxSnapshot syntax, CancellationToken cancellation) {
        var events = new java.util.TreeMap<Integer, Integer>();
        var pending = new java.util.ArrayDeque<guru.interlis.syntax.SyntaxNode>();
        pending.add(syntax.root());
        List<SyntaxToken> tokens = syntax.tokens();
        while (!pending.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            var node = pending.removeFirst();
            pending.addAll(node.children());
            boolean block = switch (node.kind()) {
                case MODEL, TOPIC, CLASS, STRUCTURE, ASSOCIATION, VIEW -> true;
                default -> false;
            };
            if (!block) continue;
            int body = -1;
            int end = -1;
            int low = 0;
            int high = tokens.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (tokens.get(middle).range().startOffset() < node.range().startOffset()) low = middle + 1;
                else high = middle;
            }
            for (int i = low; i < tokens.size(); i++) {
                cancellation.throwIfCancellationRequested();
                SyntaxToken token = tokens.get(i);
                if (token.range().endOffset() > node.range().endOffset()) break;
                if (body < 0 && "=".equals(token.text())) body = token.range().endOffset();
                if ("END".equals(token.text())) end = token.range().startOffset();
            }
            if (body >= 0 && end >= body) {
                events.merge(body, 1, Integer::sum);
                events.merge(end, -1, Integer::sum);
            }
        }
        return events;
    }

    private static int lineOf(List<Line> lines, int offset) {
        int low = 0;
        int high = lines.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Line line = lines.get(middle);
            if (offset < line.start()) {
                high = middle - 1;
            } else if (offset > line.end()) {
                low = middle + 1;
            } else {
                return middle;
            }
        }
        return Math.max(0, Math.min(lines.size() - 1, low));
    }

    private static List<Line> splitLines(String text) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                int end = i;
                String ending = "\n";
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    ending = "\r\n";
                    i++;
                } else if (c == '\r') {
                    ending = "\r";
                }
                lines.add(new Line(start, end, ending));
                start = i + 1;
            }
        }
        lines.add(new Line(start, text.length(), ""));
        return lines;
    }

    private record Line(int start, int end, String ending) {
    }
}
