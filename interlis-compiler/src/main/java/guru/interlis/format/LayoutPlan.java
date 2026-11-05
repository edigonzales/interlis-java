package guru.interlis.format;

import guru.interlis.compiler.internal.antlr.FormatStructure;
import guru.interlis.execution.CancellationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Ordered layout document: atom ordinals are fixed and only their preceding gaps can change. */
final class LayoutPlan {
    private final FormatSource source;
    private final List<FormatSource.Atom> atoms;
    private final FormatOptions options;
    private final int[] lines;
    private final int[] levels;
    private final boolean[] space;
    private final boolean[] trailing;
    private final boolean[] mixed;
    private final List<FormattingNotice> notices;

    LayoutPlan(FormatSource source, FormatStructure structure, FormatOptions options,
               CancellationToken cancellation) {
        this.source = source;
        this.atoms = source.atoms();
        this.options = options;
        int size = atoms.size();
        lines = new int[size];
        levels = new int[size];
        space = new boolean[size];
        trailing = new boolean[size];
        mixed = new boolean[size];
        notices = new ArrayList<>(source.notices());

        int[] events = new int[size];
        for (var indent : structure.indents()) {
            events[indent.first()]++;
            events[indent.endExclusive()]--;
        }
        boolean[] tight = new boolean[size];
        for (int ordinal : structure.tight()) tight[ordinal] = true;
        int depth = 0;
        for (int i = 0; i < size; i++) {
            cancellation.throwIfCancellationRequested();
            var atom = atoms.get(i);
            depth += events[i];
            levels[i] = depth;
            if (i == 0) continue;
            var previous = atoms.get(i - 1);
            lines[i] = Math.min(2, atom.endings().size());
            space[i] = atom.comment() || previous.comment()
                    || !tight[i] && needsSpace(previous.text(), atom.text());
            if (atom.comment()) {
                trailing[i] = lines[i] == 0;
                if (trailing[i]) levels[i] = levels[i - 1];
                mixed[i] = atom.blockComment() && i + 1 < size && !atoms.get(i + 1).eof()
                        && atoms.get(i + 1).endings().isEmpty();
            }
        }
        for (var header : structure.headers()) {
            for (int i = header.first() + 1; i <= header.last(); i++) {
                cancellation.throwIfCancellationRequested();
                if (lines[i] > 0) levels[i]++;
            }
        }
        for (var boundary : structure.boundaries()) {
            cancellation.throwIfCancellationRequested();
            int ordinal = boundary.ordinal();
            int anchor = ordinal;
            while (anchor > 0 && atoms.get(anchor - 1).comment() && !trailing[anchor - 1]) anchor--;
            while (anchor < size && trailing[anchor]) anchor++;
            if (anchor == 0 || anchor == size) continue;
            if (boundary.exact()) {
                lines[anchor] = boundary.lines();
            } else {
                lines[anchor] = Math.max(lines[anchor], boundary.lines());
            }
            if (anchor < ordinal && !atoms.get(ordinal).text().equals("END")) {
                for (int i = anchor; i < ordinal; i++) {
                    levels[i] = levels[ordinal];
                    if (i > anchor && !mixed[i - 1]) lines[i] = 1;
                }
                if (!mixed[ordinal - 1]) lines[ordinal] = 1;
            }
        }
        // Exact block edges take precedence over sibling separators, including attached trivia.
        for (var boundary : structure.boundaries()) {
            if (!boundary.exact()) continue;
            int anchor = boundary.ordinal();
            while (anchor < size && trailing[anchor]) anchor++;
            if (anchor >= size || anchor == 0) continue;
            if (atoms.get(anchor).text().equals("END")) {
                lines[anchor] = boundary.lines();
            } else if (!mixed[anchor - 1]) {
                lines[anchor] = boundary.lines();
            }
        }
        for (int i = 1; i < size; i++) {
            if (trailing[i] || mixed[i - 1]) lines[i] = 0;
            if (atoms.get(i - 1).lineComment()) lines[i] = Math.max(1, lines[i]);
        }
        if (!structure.constraints().isEmpty()) {
            new ConstraintLayout(atoms, lines, levels, space, trailing, mixed, options, cancellation)
                    .apply(structure);
        }
    }

    FormatOutcome.Formatted print(CancellationToken cancellation) {
        var output = new StringBuilder();
        for (int i = 0; i < atoms.size(); i++) {
            cancellation.throwIfCancellationRequested();
            var atom = atoms.get(i);
            if (atom.eof()) {
                if (options.finalNewline()) output.append(source.ending(atom, 0));
                break;
            }
            int column = levels[i] * options.indentWidth();
            String indent = " ".repeat(column);
            String text = atom.text();
            if (atom.blockComment()) {
                if ((i == 0 || lines[i] > 0) && !mixed[i] && FormatSource.lineEndings(text).size() > 0) {
                    var shifted = CommentLayout.spaceMargin(atom)
                            ? CommentLayout.shift(text, column - CommentLayout.column(atom)) : Optional.<String>empty();
                    if (shifted.isPresent()) {
                        text = shifted.get();
                    } else {
                        indent = atom.gap().substring(atom.gap().length() - CommentLayout.column(atom));
                        notices.add(new FormattingNotice("COMMENT_MARGIN_PRESERVED",
                                "The original comment margin was preserved because it cannot be shifted uniformly.",
                                Optional.of(atom.token().range())));
                    }
                }
                text = FormatVerifier.normalizeCommentEndings(text, options.lineEnding());
            }
            if (atom.protectedGap()) {
                output.append(atom.gap());
            } else if (i > 0 && lines[i] > 0) {
                String previousEnding = "";
                for (int line = 0; line < lines[i]; line++) {
                    String ending = source.ending(atom, line);
                    // A bare CR and a subsequent LF need a separator to remain two EOLs.
                    if (previousEnding.equals("\r") && ending.equals("\n")) output.append(' ');
                    output.append(ending);
                    previousEnding = ending;
                }
                output.append(indent);
            } else if (i > 0 && space[i]) {
                output.append(' ');
            }
            output.append(text);
        }
        notices.sort(java.util.Comparator.comparingInt(n -> n.range().orElseThrow().startOffset()));
        return new FormatOutcome.Formatted(output.toString(), notices);
    }

    private static boolean needsSpace(String left, String right) {
        return switch (right) {
            case ",", ";", ")", "]", ".", "[" -> false;
            default -> switch (left) {
                case "(", "[", ".", "#", "\\" -> false;
                default -> !right.equals("%");
            };
        };
    }
}
