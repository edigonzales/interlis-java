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
     * Fragment analysis is a P2 capability. Implementations must throw
     * {@link UnsupportedOperationException} rather than publish an empty
     * successful fragment while the feature is not implemented.
     */
    FragmentSnapshot parseFragment(SourceText source, SourceRange fragment,
                                   FragmentKind kind, LanguageVersion languageVersion,
                                   CancellationToken cancellation);
}
