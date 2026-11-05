package guru.interlis.gui.application;

import guru.interlis.compiler.WarningPolicy;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositoryProxyConfiguration;

import java.util.List;
import java.util.Objects;

/** Immutable host and compiler options. The repository list is in priority order. */
public record SessionOptions(RepositoryConfig repositories, RepositoryCacheConfiguration cache,
                             RepositoryProxyConfiguration proxy, WarningPolicy warnings,
                             DiagnosticLanguage diagnosticLanguage) {
    public SessionOptions {
        Objects.requireNonNull(repositories);
        Objects.requireNonNull(cache);
        Objects.requireNonNull(proxy);
        Objects.requireNonNull(warnings);
        Objects.requireNonNull(diagnosticLanguage);
    }

    public static SessionOptions defaults() {
        return new SessionOptions(new RepositoryConfig(List.of(), FetchPolicy.defaults()),
                RepositoryCacheConfiguration.disabled(), new RepositoryProxyConfiguration.SystemDefault(),
                WarningPolicy.ALLOW_WARNINGS, DiagnosticLanguage.EN);
    }

    /** Presentation language does not invalidate a completed compiler run. */
    public boolean sameCompileOptions(SessionOptions other) {
        return repositories.equals(other.repositories) && cache.equals(other.cache)
                && proxy.equals(other.proxy) && warnings == other.warnings;
    }

    public SessionOptions withDiagnosticLanguage(DiagnosticLanguage language) {
        return new SessionOptions(repositories, cache, proxy, warnings, language);
    }

    public SessionOptions withWarnings(WarningPolicy policy) {
        return new SessionOptions(repositories, cache, proxy, policy, diagnosticLanguage);
    }
}
