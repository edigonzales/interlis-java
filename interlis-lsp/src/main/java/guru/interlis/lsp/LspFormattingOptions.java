package guru.interlis.lsp;

import guru.interlis.format.FormatOptions;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

/** Decodes only the options of the current textDocument/formatting request. */
final class LspFormattingOptions {
    private static final String MAX_WIDTH = "interlis.maxLineWidth";

    private LspFormattingOptions() {
    }

    static FormatOptions decode(FormattingOptions options) {
        int tabSize = integer(options, "tabSize", 1, 16);
        var spaces = options.get("insertSpaces");
        if (spaces == null || !spaces.isThird() || !Boolean.TRUE.equals(spaces.getThird())) {
            throw LspFailures.error(ResponseErrorCode.InvalidParams, "INTERLIS formatting requires insertSpaces=true");
        }
        int width = options.containsKey(MAX_WIDTH)
                ? integer(options, MAX_WIDTH, 40, 240) : FormatOptions.DEFAULT_MAX_LINE_WIDTH;
        return new FormatOptions(tabSize, FormatOptions.LineEnding.PRESERVE, true, width);
    }

    private static int integer(FormattingOptions options, String key, int min, int max) {
        var value = options == null ? null : options.get(key);
        if (value != null && value.isSecond()) {
            try {
                // LSP4J retains JSON numbers as Number. Parsing its lexeme rejects fractions
                // and exponents, while the Either tag rules out JSON strings and booleans.
                int number = Integer.parseInt(value.getSecond().toString());
                if (number >= min && number <= max) return number;
            } catch (NumberFormatException invalid) {
                // Fall through to the same InvalidParams response.
            }
        }
        throw LspFailures.error(ResponseErrorCode.InvalidParams,
                key + " must be an integer between " + min + " and " + max);
    }
}
