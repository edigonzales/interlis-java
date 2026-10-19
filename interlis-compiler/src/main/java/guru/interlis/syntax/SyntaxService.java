package guru.interlis.syntax;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;

/** Parse service without workspace, IO or global listeners. */
public interface SyntaxService {
    SyntaxSnapshot parse(SourceText source, ParseOptions options,
                         CancellationToken cancellation);

    /**
     * Parses a complete expression, object path or attribute declaration inside
     * an existing source revision. The returned ranges stay in the original
     * source coordinates; trailing content is reported and cannot be silently
     * accepted.
     */
    FragmentSnapshot parseFragment(SourceText source, SourceRange fragment,
                                   FragmentKind kind, LanguageVersion languageVersion,
                                   CancellationToken cancellation);
}
