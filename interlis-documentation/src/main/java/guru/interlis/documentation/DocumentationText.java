package guru.interlis.documentation;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class DocumentationText {
    static String provenance(DocumentationDocument d) {
        String label =
                switch (d.options().language()) {
                    case DE -> "Quellen";
                    case IT -> "Fonti";
                    case EN, FR -> "Sources";
                };
        return "Snapshot: "
                + d.snapshotId()
                + "\n"
                + label
                + ":\n"
                + new TreeMap<>(d.sourceRevisions())
                        .entrySet().stream()
                                .map(e -> e.getKey() + " @" + e.getValue())
                                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private DocumentationText() {}

    static String safe(String text) {
        var b = new StringBuilder();
        text.codePoints()
                .forEach(
                        c -> {
                            if ((c < 32 && c != 9 && c != 10 && c != 13)
                                    || c == 0xfffe
                                    || c == 0xffff
                                    || c == 127
                                    || (c >= 0x202a && c <= 0x202e)
                                    || (c >= 0x2066 && c <= 0x2069))
                                b.append(String.format(Locale.ROOT, "\\u%04X", c));
                            else if (c >= 0xd800 && c <= 0xdfff) b.append('\ufffd');
                            else b.appendCodePoint(c);
                        });
        return b.toString();
    }

    static String xml(String text) {
        return safe(text)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    static String markdown(String text) {
        return xml(text)
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("`", "\\`")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("\r", "")
                .replace("\n", "<br>");
    }

    static String anchor(String id) {
        return "s-" + HexFormat.of().formatHex(id.getBytes(StandardCharsets.UTF_8));
    }

    static List<String> headers(DocumentationOptions o) {
        return List.of("name", "kind", "type", "cardinality", "origin", "description").stream()
                .map(k -> DocumentationLabels.get(o, k))
                .toList();
    }

    static List<String> cells(DocumentationDocument.Member m) {
        return List.of(
                m.name(),
                m.kind(),
                m.type(),
                m.cardinality(),
                m.origin(),
                m.description()
                        + (m.references().isEmpty()
                                ? ""
                                : " → " + String.join(", ", m.references())));
    }

    static void checkSize(int length) {
        if (length > 8_000_000)
            throw new IllegalArgumentException("documentation output exceeds 8 MB");
    }
}
