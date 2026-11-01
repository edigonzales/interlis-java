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
    /**
     * Loads with a null-free cache request. Existing functional loaders may ignore
     * this optimization. Cache-capable hosts preserve limits, cancellation and
     * offline safety. No cache decision belongs to the compiler core.
     */
    default LoadedSource load(SourceLocator locator, FetchPolicy policy, RepositoryCacheRequest request, CancellationToken token) {
        java.util.Objects.requireNonNull(request, "request");
        return load(locator, policy, token);
    }
    /**
     * Loads only previously cached material, never a network or live source.
     * The default rejects the operation without invoking load, preserving the
     * offline safety of existing functional implementations. Cache-capable hosts
     * override this method; limits, decoding and cancellation still apply.
     *
     * @throws IllegalArgumentException if policy is not offline
     * @throws SourceLoadException with OFFLINE when no verified entry is available
     */
    default LoadedSource loadCached(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        java.util.Objects.requireNonNull(locator); java.util.Objects.requireNonNull(policy); java.util.Objects.requireNonNull(token);
        token.throwIfCancellationRequested();
        if(!policy.offline())throw new IllegalArgumentException("cached loading requires offline policy");
        throw new SourceLoadException(SourceLoadException.Reason.OFFLINE,"verified content unavailable offline",null);
    }
}
