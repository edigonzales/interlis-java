package guru.interlis.syntax;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;

import java.util.List;
import java.util.Objects;

/**
 * Fragment parse result retaining original source coordinates.
 *
 * @param source source identifier
 * @param kind kind discriminator
 * @param fragment the {@code fragment} component
 * @param root the {@code root} component
 * @param consumedEntireFragment the {@code consumedEntireFragment} component
 * @param diagnostics diagnostics
 * @param languageVersion the {@code languageVersion} component
 */
public record FragmentSnapshot(SourceText source, FragmentKind kind, SourceRange fragment,
                               SyntaxNode root, boolean consumedEntireFragment,
                               List<Diagnostic> diagnostics, LanguageVersion languageVersion) {
    /** Creates a validated instance. */
    public FragmentSnapshot {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(fragment, "fragment");
        Objects.requireNonNull(root, "root");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        Objects.requireNonNull(languageVersion, "languageVersion");
    }
}
