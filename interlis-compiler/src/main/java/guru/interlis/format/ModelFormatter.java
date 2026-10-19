package guru.interlis.format;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceText;

/** Deterministic source formatter without file or network IO. */
public interface ModelFormatter {
    /**
     * Formats one complete source revision.
     *
     * @param source source text
     * @param options formatting preferences
     * @param cancellation cancellation signal
     * @return outcome; never a partial successful document
     */
    FormatOutcome format(SourceText source, FormatOptions options, CancellationToken cancellation);
}
