package guru.interlis.compiler.internal.antlr;

import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.DetectionState;
import guru.interlis.syntax.LanguageDetection;

import java.util.Objects;
import java.util.Optional;

/**
 * Small header scanner for {@code INTERLIS 2.3;} / {@code INTERLIS 2.4;}.
 * It skips comments and whitespace but is not a second full grammar.
 */
public final class VersionHeaderScanner {
    private final SourceText source;

    public VersionHeaderScanner(SourceText source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public LanguageDetection detect() {
        String text = source.text();
        int index = 0;
        while (true) {
            index = skipTrivia(text, index);
            if (index >= text.length()) {
                return LanguageDetection.absent();
            }
            if (text.startsWith("INTERLIS", index) && boundary(text, index + 8)) {
                int start = index;
                int cursor = skipWhitespace(text, index + 8);
                int versionStart = cursor;
                while (cursor < text.length()) {
                    char c = text.charAt(cursor);
                    if (Character.isDigit(c) || c == '.') {
                        cursor++;
                    } else {
                        break;
                    }
                }
                if (versionStart == cursor) {
                    return new LanguageDetection(Optional.empty(),
                            Optional.of(source.range(start, Math.min(source.length(), start + 8))),
                            DetectionState.UNSUPPORTED, Optional.empty());
                }
                String raw = text.substring(versionStart, cursor);
                cursor = skipWhitespace(text, cursor);
                if (cursor >= text.length() || text.charAt(cursor) != ';') {
                    SourceRange range = source.range(start, Math.min(source.length(), cursor));
                    return new LanguageDetection(Optional.empty(), Optional.of(range),
                            DetectionState.UNSUPPORTED, Optional.of(raw));
                }
                SourceRange range = source.range(start, cursor + 1);
                Optional<LanguageVersion> version = LanguageVersion.fromLiteral(raw);
                return new LanguageDetection(version, Optional.of(range),
                        version.isPresent() ? DetectionState.DETECTED : DetectionState.UNSUPPORTED,
                        Optional.of(raw));
            }
            // A different first keyword is not a valid INTERLIS header.
            return LanguageDetection.absent();
        }
    }

    private static boolean boundary(String text, int index) {
        return index >= text.length() || !Character.isLetterOrDigit(text.charAt(index));
    }

    private static int skipTrivia(String text, int start) {
        int index = start;
        boolean progress = true;
        while (progress) {
            progress = false;
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
                progress = true;
            }
            if (text.startsWith("!!", index)) {
                int end = index + 2;
                while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
                    end++;
                }
                index = end;
                progress = true;
            } else if (text.startsWith("/*", index)) {
                int end = text.indexOf("*/", index + 2);
                index = end < 0 ? text.length() : end + 2;
                progress = true;
            }
        }
        return index;
    }

    private static int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }
}
