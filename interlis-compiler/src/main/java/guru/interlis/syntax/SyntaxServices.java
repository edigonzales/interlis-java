package guru.interlis.syntax;

import guru.interlis.compiler.internal.antlr.AntlrSyntaxService;

/** Public factory for the compiler's own syntax implementation. */
public final class SyntaxServices {
    private SyntaxServices() {
    }

    /** Starts a fresh budget for preparing syntax across multiple sources. */
    public static SyntaxBatch batch(ParseOptions options, guru.interlis.execution.CancellationToken cancellation) {
        return new SyntaxBatch(options, cancellation);
    }

    /**
     * Executes {@code create}.
     *
     * @return the result
     */
    public static SyntaxService create() {
        return parser();
    }

    static AntlrSyntaxService parser() {
        return new AntlrSyntaxService(SyntaxSnapshot::fromParser);
    }
}
