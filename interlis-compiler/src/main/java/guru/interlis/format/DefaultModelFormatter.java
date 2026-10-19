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
    private static final Set<String> OPENING_DECLARATIONS = Set.of(
            "MODEL", "TOPIC", "CLASS", "STRUCTURE", "ASSOCIATION", "VIEW", "FUNCTION",
            "GRAPHIC", "SIGN");

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
            int indent = 0;
            List<String> formattedLines = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                List<SyntaxToken> tokens = new ArrayList<>(tokensByLine.getOrDefault(i, List.of()));
                tokens.sort(Comparator.comparingInt(t -> t.range().startOffset()));
                List<TriviaSpan> comments = new ArrayList<>(
                        commentsByLine.getOrDefault(i, List.of()));
                comments.sort(Comparator.comparingInt(t -> t.range().startOffset()));
                if (tokens.isEmpty() && comments.isEmpty()) {
                    formattedLines.add("");
                    continue;
                }
                if (!tokens.isEmpty()
                        && "END".equalsIgnoreCase(tokens.getFirst().text())) {
                    indent = Math.max(0, indent - 1);
                }
                StringBuilder builder = new StringBuilder(" ".repeat(indent * options.indentWidth()));
                if (!tokens.isEmpty()) {
                    builder.append(renderTokens(tokens));
                }
                if (!comments.isEmpty()) {
                    if (!tokens.isEmpty()) {
                        builder.append(' ');
                    }
                    builder.append(renderComments(comments));
                }
                formattedLines.add(builder.toString());
                if (!tokens.isEmpty() && isOpeningLine(tokens)) {
                    indent++;
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
                String ending = lineEnding != null ? lineEnding
                        : last ? "" : (lines.get(i).ending().isEmpty() ? "\n" : lines.get(i).ending());
                result.append(ending);
            }
            String text = result.toString();
            if (options.finalNewline()) {
                String eol = lineEnding != null ? lineEnding : "\n";
                while (text.endsWith(eol)) {
                    text = text.substring(0, text.length() - eol.length());
                }
                text = text + eol;
            } else {
                while (text.endsWith("\n") || text.endsWith("\r")) {
                    text = text.substring(0, text.length() - 1);
                }
            }
            return new FormatOutcome.Formatted(text, List.of());
        } catch (OperationCancelledException cancelled) {
            return new FormatOutcome.Cancelled(cancelled.reason());
        }
    }

    private static String renderTokens(List<SyntaxToken> tokens) {
        StringBuilder builder = new StringBuilder();
        String previous = null;
        for (SyntaxToken token : tokens) {
            String text = token.text();
            if (previous != null && needsSpace(previous, text)) {
                builder.append(' ');
            }
            builder.append(text);
            previous = text;
        }
        return builder.toString();
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

    private static String renderComments(List<TriviaSpan> comments) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < comments.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(comments.get(i).text().strip());
        }
        return builder.toString();
    }

    private static boolean isOpeningLine(List<SyntaxToken> tokens) {
        String first = tokens.getFirst().text();
        if (!OPENING_DECLARATIONS.contains(first.toUpperCase(java.util.Locale.ROOT))) {
            return false;
        }
        return tokens.stream().anyMatch(token -> token.text().equals("="));
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
