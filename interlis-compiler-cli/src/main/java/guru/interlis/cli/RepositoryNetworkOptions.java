package guru.interlis.cli;

import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.RepositoryProxyConfiguration;
import java.time.Duration;
import java.util.*;

/** Shared CLI conversion; repository networking never introduces compiler semantics. */
final class RepositoryNetworkOptions {
    private RepositoryNetworkOptions() { }

    static void rejectProxyConflict(Map<String, List<List<String>>> options) {
        if (options.containsKey("--no-proxy") && (options.containsKey("--proxy") || options.containsKey("--proxyPort")))
            throw new CliUsageException("no-proxy and proxy/proxyPort are mutually exclusive at the same configuration level");
    }

    static void normalize(Map<String, List<List<String>>> options, Map<String, ConfigurationLoader.Origin> origins) {
        var defaults = FetchPolicy.defaults();
        for (var entry : Map.of("--connect-timeout", Long.toString(defaults.connectTimeout().toSeconds()),
                "--read-timeout", Long.toString(defaults.readTimeout().toSeconds()),
                "--request-timeout", defaults.requestTimeout().map(d -> Long.toString(d.toSeconds())).orElse("off")).entrySet()) {
            if (!options.containsKey(entry.getKey())) {
                options.put(entry.getKey(), List.of(List.of(entry.getValue())));
                origins.put(entry.getKey(), ConfigurationLoader.Origin.PROFILE_DEFAULT);
            }
            String value = value(options, entry.getKey());
            if (!entry.getKey().equals("--request-timeout") || !value.equals("off")) duration(value, entry.getKey());
        }
        if (options.containsKey("--proxyPort") && !options.containsKey("--proxy"))
            throw new CliUsageException("proxyPort requires a proxy host");
        if (options.containsKey("--proxy")) {
            if (!options.containsKey("--proxyPort")) {
                options.put("--proxyPort", List.of(List.of("80")));
                origins.put("--proxyPort", ConfigurationLoader.Origin.PROFILE_DEFAULT);
            }
            proxy(new ParsedCommand("check", options, List.of())); // validate before source or network IO
        }
        origins.putIfAbsent("--no-proxy", ConfigurationLoader.Origin.PROFILE_DEFAULT);
        String mode = options.containsKey("--no-proxy") ? "direct" : options.containsKey("--proxy") ? "http" : "system";
        options.put("--proxy-mode", List.of(List.of(mode))); // explanatory effective setting, not a CLI argument
        origins.put("--proxy-mode", mode.equals("direct") ? origins.get("--no-proxy")
                : mode.equals("http") ? origins.get("--proxy") : ConfigurationLoader.Origin.PROFILE_DEFAULT);
    }

    static FetchPolicy fetchPolicy(ParsedCommand command) {
        var defaults = FetchPolicy.defaults();
        String total = command.value("--request-timeout").orElseThrow();
        return new FetchPolicy(command.has("--offline"), defaults.maxBytes(),
                duration(command.value("--connect-timeout").orElseThrow(), "connect-timeout"),
                duration(command.value("--read-timeout").orElseThrow(), "read-timeout"),
                total.equals("off") ? Optional.empty() : Optional.of(duration(total, "request-timeout")));
    }

    static RepositoryProxyConfiguration proxy(ParsedCommand command) {
        if (command.has("--no-proxy")) return new RepositoryProxyConfiguration.Direct();
        if (!command.has("--proxy")) return new RepositoryProxyConfiguration.SystemDefault();
        String port = command.value("--proxyPort").orElse("80");
        try {
            if (!port.matches("[0-9]+")) throw new IllegalArgumentException();
            return new RepositoryProxyConfiguration.HttpProxy(command.value("--proxy").orElseThrow(), Integer.parseInt(port));
        } catch (IllegalArgumentException invalid) {
            throw new CliUsageException("invalid proxy host or port (expected bare host and port 1..65535)");
        }
    }

    private static Duration duration(String value, String option) {
        try {
            if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
            var duration = Duration.ofSeconds(Long.parseLong(value));
            if (duration.isZero()) throw new IllegalArgumentException();
            duration.toNanos();
            return duration;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw new CliUsageException(option + " requires positive whole seconds within the nanosecond range");
        }
    }

    private static String value(Map<String, List<List<String>>> options, String key) {
        return options.get(key).getFirst().getFirst();
    }
}
