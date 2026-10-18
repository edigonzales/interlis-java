package guru.interlis.syntax;

/** Public factory for the compiler's own syntax implementation. */
public final class SyntaxServices {
    private SyntaxServices() {
    }

    /**
     * Executes {@code create}.
     *
     * @return the result
     */
    public static SyntaxService create() {
        return new guru.interlis.compiler.internal.antlr.AntlrSyntaxService();
    }
}
