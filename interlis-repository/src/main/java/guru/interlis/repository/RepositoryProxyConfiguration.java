package guru.interlis.repository;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;

/** Immutable JVM host proxy choice. Does not mutate process configuration or contain credentials. */
public sealed interface RepositoryProxyConfiguration {
    /** Captures the current JVM selector when a loader is constructed, without changing it. */
    record SystemDefault() implements RepositoryProxyConfiguration { }
    /** Connects directly regardless of JVM proxy settings. */
    record Direct() implements RepositoryProxyConfiguration { }
    /** One HTTP proxy for HTTP and HTTPS CONNECT; port is in 1..65535, host is a bare hostname or IP. */
    record HttpProxy(String host, int port) implements RepositoryProxyConfiguration {
        /** Validates without DNS or other IO. Bracketed IPv6 literals are accepted. */
        public HttpProxy {
            Objects.requireNonNull(host, "host");
            if (port < 1 || port > 65535) throw new IllegalArgumentException("proxy port must be in 1..65535");
            try {
                URI uri = URI.create("http://" + host + ":" + port);
                if (host.isBlank() || !host.equals(host.strip()) || uri.getHost() == null
                        || uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty()
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || !uri.getHost().equalsIgnoreCase(host))
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("proxy host must be a bare hostname or IP address");
            }
        }
    }

    /** Resolves this choice once for a loader. A missing JVM selector means direct access. */
    default ProxySelector selector() {
        return switch (this) {
            case SystemDefault ignored -> Objects.requireNonNullElse(ProxySelector.getDefault(), HttpClient.Builder.NO_PROXY);
            case Direct ignored -> HttpClient.Builder.NO_PROXY;
            case HttpProxy proxy -> ProxySelector.of(InetSocketAddress.createUnresolved(proxy.host(), proxy.port()));
        };
    }
}
