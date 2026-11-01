import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Standalone JVM repository host example; accepts an optional HTTP(S) source URL. */
public final class RepositoryNetworkExample {
    public static void main(String[] args) {
        var policy = new FetchPolicy(false, 16 * 1024 * 1024, Duration.ofSeconds(15),
                Duration.ofSeconds(40), Optional.empty());
        var proxy = new RepositoryProxyConfiguration.SystemDefault();
        var access = HostRepositories.create(Path.of("."), RepositoryCacheConfiguration.disabled(), event -> {}, proxy);
        System.out.println("connect=" + policy.connectTimeout() + " read=" + policy.readTimeout()
                + " request=" + policy.requestTimeout().map(Duration::toString).orElse("off"));
        if (args.length > 0) {
            var loaded = access.loader().load(new SourceLocator(args[0]), policy, CancellationToken.none());
            System.out.println(loaded.text());
        }
    }
}
