package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;

/** Host adapter contract; implementations may perform IO. */
public interface SourceLoader {
    /**
     * Loads one host source under the given fetch policy.
     *
     * @throws SourceLoadException for IO, policy, size or decoding failures
     * @param locator host source location
     * @param policy timeout/size/encoding policy
     * @param token cancellation signal
     * @return loaded immutable source
     * @throws guru.interlis.execution.OperationCancelledException if cancelled
     */
    LoadedSource load(SourceLocator locator, FetchPolicy policy, CancellationToken token);
}
