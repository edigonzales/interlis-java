package guru.interlis.documentation;

import guru.interlis.model.SymbolId;

import java.util.*;

/** Immutable presentation choices. Identifiers and source comments are never translated. */
public record DocumentationOptions(
        List<SymbolId> roots,
        Language language,
        Inheritance inheritance,
        Map<String, String> headers,
        int maxEntries) {
    public enum Language {
        DE,
        FR,
        IT,
        EN
    }

    public enum Inheritance {
        SEPARATE,
        INLINE,
        INLINE_ABSTRACT_ONLY
    }

    public DocumentationOptions {
        roots = List.copyOf(roots);
        Objects.requireNonNull(language);
        Objects.requireNonNull(inheritance);
        headers = Map.copyOf(headers);
        if (roots.size() > 100 || maxEntries < 1 || maxEntries > 20_000)
            throw new IllegalArgumentException("documentation resource limit");
        for (var e : headers.entrySet())
            if (!Set.of(
                                    "catalog",
                                    "name",
                                    "kind",
                                    "type",
                                    "cardinality",
                                    "origin",
                                    "description",
                                    "constraints",
                                    "inherited",
                                    "base")
                            .contains(e.getKey())
                    || e.getValue().length() > 200)
                throw new IllegalArgumentException("unknown or oversized documentation header");
    }

    public static DocumentationOptions defaults() {
        return new DocumentationOptions(
                List.of(), Language.EN, Inheritance.SEPARATE, Map.of(), 10_000);
    }
}
