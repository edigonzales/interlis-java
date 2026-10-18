package guru.interlis.workspace;

import guru.interlis.compiler.CompilerOptions;
import guru.interlis.workspace.resolution.ResolverCatalog;
import guru.interlis.workspace.resolution.ResolutionPolicy;

import java.util.Objects;

/**
 * Complete workspace configuration.
 *
 * @param compilerOptions the {@code compilerOptions} component
 * @param resolutionPolicy the {@code resolutionPolicy} component
 * @param resolverCatalog the {@code resolverCatalog} component
 * @param cacheLimits the {@code cacheLimits} component
 */
public record WorkspaceConfiguration(CompilerOptions compilerOptions,
                                     ResolutionPolicy resolutionPolicy,
                                     ResolverCatalog resolverCatalog,
                                     CacheLimits cacheLimits) {
    /** Creates a validated instance. */
    public WorkspaceConfiguration {
        Objects.requireNonNull(compilerOptions, "compilerOptions");
        Objects.requireNonNull(resolutionPolicy, "resolutionPolicy");
        Objects.requireNonNull(resolverCatalog, "resolverCatalog");
        Objects.requireNonNull(cacheLimits, "cacheLimits");
    }

    /**
     * Returns the {@code defaults} value.
     *
     * @return the value
     */
    public static WorkspaceConfiguration defaults() {
        return new WorkspaceConfiguration(CompilerOptions.defaults(),
                ResolutionPolicy.defaults(), ResolverCatalog.empty(), CacheLimits.defaults());
    }

    /**
     * Returns the {@code semanticFingerprint} value.
     *
     * @return the value
     */
    public String semanticFingerprint() {
        return compilerOptions.fingerprint() + "|" + resolutionPolicy + "|"
                + resolverCatalog.fingerprint();
    }

    /**
     * Returns the {@code fullFingerprint} value.
     *
     * @return the value
     */
    public String fullFingerprint() {
        return semanticFingerprint() + "|" + cacheLimits;
    }
}
