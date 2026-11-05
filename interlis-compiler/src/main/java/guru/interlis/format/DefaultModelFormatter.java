package guru.interlis.format;

import guru.interlis.compiler.internal.antlr.AntlrSyntaxService;
import guru.interlis.compiler.internal.antlr.FormatStructure;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;

import java.util.Objects;

/** Stateless formatter for the supported INTERLIS source profiles. */
final class DefaultModelFormatter implements ModelFormatter {
    @Override
    public FormatOutcome format(SourceText source, FormatOptions options, CancellationToken cancellation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        try {
            var syntaxService = new AntlrSyntaxService();
            var parseOptions = ParseOptions.defaults(ParseMode.STRICT);
            var structure = new FormatStructure();
            var snapshot = syntaxService.parseForFormatting(source, parseOptions, cancellation, structure);
            if (!FormatVerifier.isComplete(snapshot)) {
                return new FormatOutcome.Rejected(snapshot.diagnostics());
            }
            var original = new FormatSource(snapshot, options, cancellation);
            var formatted = new LayoutPlan(original, structure, options, cancellation).print(cancellation);
            var output = new SourceText(source.id(), source.revision(), formatted.text());
            FormatVerifier.verify(snapshot, syntaxService.parse(output, parseOptions, cancellation),
                    options, cancellation);
            return formatted;
        } catch (OperationCancelledException cancelled) {
            return new FormatOutcome.Cancelled(cancelled.reason());
        }
    }
}
