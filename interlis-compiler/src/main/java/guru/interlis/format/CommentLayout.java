package guru.interlis.format;

import java.util.Optional;

/** Moves only a common space margin, leaving empty lines and the body of the comment intact. */
final class CommentLayout {
    private CommentLayout() {
    }

    static Optional<String> shift(String text, int delta) {
        if (delta == 0) return Optional.of(text);
        var shifted = new StringBuilder(text.length());
        int start = 0;
        while (start < text.length()) {
            int end = start;
            while (end < text.length() && text.charAt(end) != '\r' && text.charAt(end) != '\n') end++;
            if (start > 0 && end > start) {
                if (delta > 0) {
                    shifted.append(" ".repeat(delta));
                } else {
                    int remove = -delta;
                    if (end - start < remove) return Optional.empty();
                    for (int i = 0; i < remove; i++) {
                        if (text.charAt(start + i) != ' ') return Optional.empty();
                    }
                    start += remove;
                }
            }
            shifted.append(text, start, end);
            if (end == text.length()) break;
            if (text.charAt(end) == '\r' && end + 1 < text.length() && text.charAt(end + 1) == '\n') {
                shifted.append("\r\n");
                start = end + 2;
            } else {
                shifted.append(text.charAt(end));
                start = end + 1;
            }
        }
        return Optional.of(shifted.toString());
    }

    static boolean spaceMargin(FormatSource.Atom atom) {
        return atom.gap().substring(afterLastEnding(atom.gap())).chars().allMatch(c -> c == ' ');
    }

    static int column(FormatSource.Atom atom) {
        return atom.gap().length() - afterLastEnding(atom.gap());
    }

    private static int afterLastEnding(String gap) {
        return Math.max(gap.lastIndexOf('\r'), gap.lastIndexOf('\n')) + 1;
    }
}
