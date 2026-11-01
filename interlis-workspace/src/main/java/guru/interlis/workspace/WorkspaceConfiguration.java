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
 * @param resolutionDiagnostics immutable global source-resolution diagnostics; included in every capture and checked by the compiler gate
 */
public record WorkspaceConfiguration(CompilerOptions compilerOptions,
                                     ResolutionPolicy resolutionPolicy,
                                     ResolverCatalog resolverCatalog,
                                     CacheLimits cacheLimits, java.util.List<guru.interlis.diagnostics.Diagnostic> resolutionDiagnostics) {
    /** Compatibility constructor without host-provided resolution diagnostics. */
    public WorkspaceConfiguration(CompilerOptions options, ResolutionPolicy policy, ResolverCatalog catalog, CacheLimits limits) {
        this(options, policy, catalog, limits, java.util.List.of());
    }
    /** Creates a validated instance. */
    public WorkspaceConfiguration {
        resolutionDiagnostics = java.util.List.copyOf(Objects.requireNonNull(resolutionDiagnostics, "resolutionDiagnostics"));
        if (resolutionDiagnostics.stream().anyMatch(d -> d.primaryRange().isPresent() || d.phase() != guru.interlis.diagnostics.Phase.SOURCE_RESOLUTION))
            throw new IllegalArgumentException("configuration diagnostics must be global; source diagnostics belong to a SourceSet");
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
        return compilerOptions.fingerprint() + "|" + resolutionPolicy.fingerprint() + "|"
                + resolverCatalog.fingerprint() + "|" + diagnosticFingerprint();
    }

    private String diagnosticFingerprint() {
        var value = new StringBuilder("workspace-resolution-diagnostics-v1");
        field(value,Integer.toString(resolutionDiagnostics.size()));
        for (var diagnostic : resolutionDiagnostics) {
            field(value,diagnostic.id().value());
            field(value,guru.interlis.diagnostics.DiagnosticIds.create(diagnostic.code(),diagnostic.phase(),
                    diagnostic.primaryRange(),diagnostic.messageKey(),diagnostic.arguments()).value());
            field(value,diagnostic.severity().name());
            field(value,diagnostic.causeId().map(guru.interlis.diagnostics.DiagnosticId::value).orElse(""));
            field(value,Integer.toString(diagnostic.dependsOn().size()));
            diagnostic.dependsOn().forEach(id -> field(value,id.value()));
            field(value,Integer.toString(diagnostic.relatedLocations().size()));
            diagnostic.relatedLocations().forEach(location -> field(value,guru.interlis.diagnostics.DiagnosticIds.create(
                    diagnostic.code(),diagnostic.phase(),java.util.Optional.of(location.range()),location.messageKey(),location.arguments()).value()));
            field(value,diagnostic.symbolId().map(guru.interlis.model.SymbolId::value).orElse(""));
            field(value,diagnostic.failureId().map(guru.interlis.execution.FailureId::value).orElse(""));
            field(value,Integer.toString(diagnostic.tags().size()));
            diagnostic.tags().forEach(tag -> field(value,tag.name()));
        }
        return guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digestUtf16(value.toString()));
    }
    private static void field(StringBuilder target,String value) { target.append(value.length()).append(':').append(value); }

    /**
     * Returns the {@code fullFingerprint} value.
     *
     * @return the value
     */
    public String fullFingerprint() {
        return semanticFingerprint() + "|" + cacheLimits;
    }
}
