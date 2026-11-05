package guru.interlis.format;

import guru.interlis.compiler.internal.antlr.FormatStructure;
import guru.interlis.execution.CancellationToken;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** Applies width decisions to a bounded ordinal document using the parser's group boundaries. */
final class ConstraintLayout {
    private final List<FormatSource.Atom> atoms;
    private final int[] lines;
    private final int[] levels;
    private final boolean[] space;
    private final boolean[] trailing;
    private final boolean[] mixed;
    private final int[] widths;
    private final int[] flatColumns;
    private final int[] hardLines;
    private final boolean[] planned;
    private final FormatOptions options;
    private final CancellationToken cancellation;

    ConstraintLayout(List<FormatSource.Atom> atoms, int[] lines, int[] levels, boolean[] space,
                     boolean[] trailing, boolean[] mixed, FormatOptions options,
                     CancellationToken cancellation) {
        this.atoms = atoms;
        this.lines = lines;
        this.levels = levels;
        this.space = space;
        this.trailing = trailing;
        this.mixed = mixed;
        this.options = options;
        this.cancellation = cancellation;
        int size = atoms.size();
        widths = new int[size];
        flatColumns = new int[size + 1];
        hardLines = new int[size + 1];
        planned = new boolean[size];
        for (int i = 0; i < size; i++) {
            cancellation.throwIfCancellationRequested();
            String text = atoms.get(i).text();
            widths[i] = text.codePointCount(0, text.length());
            flatColumns[i + 1] = flatColumns[i] + widths[i] + (space[i] ? 1 : 0);
            boolean multiline = text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
            hardLines[i + 1] = hardLines[i] + (multiline || hardBefore(i) ? 1 : 0);
        }
    }

    void apply(FormatStructure structure) {
        var starting = new HashMap<Integer, List<FormatStructure.Group>>();
        for (var group : structure.expressionGroups()) {
            starting.computeIfAbsent(group.first(), ignored -> new ArrayList<>()).add(group);
        }
        starting.values().forEach(groups ->
                groups.sort(Comparator.comparingInt(FormatStructure.Group::last).reversed()));

        FormatStructure.Group previous = null;
        boolean previousMultiline = false;
        for (var constraint : structure.constraints()) {
            cancellation.throwIfCancellationRequested();
            int first = constraint.first();
            int last = constraint.last();
            applyConstraint(constraint, starting);
            boolean multiline = false;
            for (int i = first + 1; i <= last; i++) {
                if (lines[i] > 0) {
                    multiline = true;
                    break;
                }
            }
            int anchor = leading(first);
            int previousToken = anchor - 1;
            while (previousToken >= 0 && atoms.get(previousToken).comment()) previousToken--;
            if (previousToken >= 0 && atoms.get(previousToken).text().equals(";")) {
                boolean consecutive = previous != null && previous.last() == previousToken;
                if (!consecutive || previousMultiline || multiline || anchor < first) {
                    lines[anchor] = Math.max(lines[anchor], 2);
                }
            }
            previous = constraint;
            previousMultiline = multiline;
        }
    }

    private void applyConstraint(FormatStructure.Group constraint,
                                 HashMap<Integer, List<FormatStructure.Group>> starting) {
        int first = constraint.first();
        int last = constraint.last();
        int level = levels[first];
        int column = level * options.indentWidth();
        for (int i = first + 1; i <= last; i++) lines[i] = hardBefore(i) ? 1 : 0;
        if (!fits(constraint, column)) open(constraint, level, first);
        for (int i = first; i <= last; i++) {
            cancellation.throwIfCancellationRequested();
            if (i > first) {
                if (lines[i] > 0) {
                    if (planned[i]) level = levels[i];
                    else levels[i] = level;
                    column = level * options.indentWidth();
                } else if (space[i]) {
                    column++;
                }
            }
            var groups = starting.get(i);
            if (groups != null) {
                for (var group : groups) {
                    if (group.last() <= last && !fits(group, column)) open(group, level, first);
                }
            }
            String text = atoms.get(i).text();
            int ending = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));
            column = ending < 0 ? column + widths[i] : text.codePointCount(ending + 1, text.length());
        }
    }

    private boolean fits(FormatStructure.Group group, int column) {
        int first = group.first();
        int last = group.last();
        // A hard line immediately before this group affects its starting column only.
        int hard = hardLines[last + 1] - hardLines[first + 1];
        String text = atoms.get(first).text();
        if (hard != 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) return false;
        int width = flatColumns[last + 1] - flatColumns[first] - (space[first] ? 1 : 0);
        for (int i = last + 1; i < atoms.size() && width <= options.maxLineWidth(); i++) {
            if (space[i] || lines[i] > 0 || atoms.get(i).eof()) break;
            String suffix = atoms.get(i).text();
            if (!suffix.equals(")") && !suffix.equals(";") && !suffix.equals(",")) break;
            width += widths[i];
        }
        return column + width <= options.maxLineWidth();
    }

    private void open(FormatStructure.Group group, int level, int first) {
        for (var item : group.breaks()) {
            int index = Math.max(first + 1, leading(item.ordinal()));
            if (index <= group.first() || trailing[index]
                    || mixed[index - 1] && !trailing[index - 1]) continue;
            lines[index] = 1;
            planned[index] = true;
            levels[index] = level + item.extraIndent();
        }
    }

    private int leading(int index) {
        while (index > 0 && atoms.get(index - 1).comment() && !trailing[index - 1]) index--;
        return index;
    }

    private boolean hardBefore(int i) {
        if (i == 0) return false;
        var before = atoms.get(i - 1);
        var current = atoms.get(i);
        return before.lineComment() || !current.endings().isEmpty() && (before.comment() || current.comment());
    }
}
