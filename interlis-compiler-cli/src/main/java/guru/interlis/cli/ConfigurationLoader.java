package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;

/** Explicit UTF-8 properties configuration: CLI overrides file, then profile defaults. */
public final class ConfigurationLoader {
    /** Setting provenance, without retaining configuration text or credentials. */
    public enum Origin { CLI, CONFIGURATION_FILE, PROFILE_DEFAULT }

    /** Immutable effective command and per-option provenance. */
    public record ResolvedConfiguration(ParsedCommand command, Map<String, Origin> origins) {
        public ResolvedConfiguration {
            Objects.requireNonNull(command, "command");
            origins = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(origins, "origins")));
        }
    }

    /**
     * Loads only an explicitly named file, relative to the injected working
     * directory. The injected home determines the default cache; no configuration files are implicit.
     * Duplicate/unknown keys and invalid values are usage errors. File IO and
     * decoding errors retain the repository loader's typed host failure.
     */
    public ResolvedConfiguration load(ParsedCommand command, CliEnvironment environment,
                                      CancellationToken cancellation) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(cancellation, "cancellation");
        if(command.has("--cache") && command.has("--no-cache"))
            throw new CliUsageException("--cache and --no-cache are mutually exclusive");
        RepositoryNetworkOptions.rejectProxyConflict(command.options());
        var options = new LinkedHashMap<String, List<List<String>>>();
        var origins = new TreeMap<String, Origin>();
        options.put("--warnings", List.of(List.of("allow")));
        origins.put("--warnings", Origin.PROFILE_DEFAULT);
        origins.put("--offline", Origin.PROFILE_DEFAULT);
        command.value("--config").ifPresent(file -> {
            var defaults = FetchPolicy.defaults();
            var loaded = new FileSourceLoader(environment.workingDirectory()).load(new SourceLocator(file),
                    new FetchPolicy(false, 1_048_576, defaults.connectTimeout(), defaults.readTimeout(), defaults.requestTimeout()), cancellation);
            var properties = new Properties() {
                @Override public synchronized Object put(Object key, Object value) {
                    if (containsKey(key)) throw new CliUsageException("duplicate configuration key: " + key);
                    return super.put(key, value);
                }
            };
            try { properties.load(new StringReader(loaded.text())); }
            catch (IOException impossible) { throw new IllegalStateException(impossible); }
            catch (IllegalArgumentException invalid) { throw new CliUsageException("invalid configuration encoding"); }
            for (String key : new TreeSet<>(properties.stringPropertyNames())) {
                if (!Set.of("root", "repository", "cache", "read-lock", "write-lock", "offline", "no-cache", "warnings", "connect-timeout", "read-timeout", "request-timeout", "proxy", "proxyPort", "no-proxy").contains(key))
                    throw new CliUsageException("unsupported configuration key: " + key);
                String value = properties.getProperty(key);
                if (key.equals("offline") || key.equals("no-cache") || key.equals("no-proxy")) {
                    if (!value.equals("true") && !value.equals("false"))
                        throw new CliUsageException(key + " must be true or false");
                    if (value.equals("true")) options.put("--" + key, List.of(List.of()));
                } else {
                    if (value.isBlank()) throw new CliUsageException("empty configuration value: " + key);
                    options.put("--" + key, List.of(List.of(value)));
                }
                origins.put("--" + key, Origin.CONFIGURATION_FILE);
            }
        });
        RepositoryNetworkOptions.rejectProxyConflict(options);
        if (command.has("--no-proxy")) {
            for (String name : List.of("--proxy", "--proxyPort")) { options.remove(name); origins.remove(name); }
        }
        if (command.has("--proxy")) { options.remove("--no-proxy"); origins.remove("--no-proxy"); }
        if(command.has("--cache")) { options.remove("--no-cache"); origins.remove("--no-cache"); }
        if(command.has("--no-cache")) { options.remove("--cache"); origins.remove("--cache"); }
        command.options().forEach((name, values) -> {
            if (!name.equals("--config") && !name.equals("--explain-config")) {
                options.put(name, values);
                origins.put(name, Origin.CLI);
            }
        });
        if(options.containsKey("--cache") && options.containsKey("--no-cache"))
            throw new CliUsageException("cache and no-cache are mutually exclusive");
        if(!options.containsKey("--no-cache")) {
            if(!options.containsKey("--cache")) {
                options.put("--cache",List.of(List.of(RepositoryCacheConfiguration.defaults(environment.homeDirectory()).directory().orElseThrow().toString())));
                origins.put("--cache",Origin.PROFILE_DEFAULT);
            } else {
                String value=options.get("--cache").getFirst().getFirst();
                try {
                    Path path=value.startsWith("file:")?Path.of(java.net.URI.create(value)):Path.of(value);
                    options.put("--cache",List.of(List.of(environment.workingDirectory().resolve(path).toAbsolutePath().normalize().toString())));
                } catch(IllegalArgumentException invalid) { throw new CliUsageException("invalid cache directory"); }
            }
        }
        RepositoryNetworkOptions.normalize(options, origins);
        return new ResolvedConfiguration(new ParsedCommand(command.command(), options, command.files()), origins);
    }

    /** Deterministic explanatory JSON; URI credentials and queries are redacted. */
    public String explain(ResolvedConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        var output = new StringBuilder("{\"profile\":\"cli-standard/v1\",\"settings\":{");
        boolean first = true;
        for (var entry : configuration.origins().entrySet()) {
            if (!first) output.append(',');
            first = false;
            output.append('"').append(JsonDiagnosticWriter.escape(entry.getKey())).append("\":{\"origin\":\"")
                    .append(entry.getValue()).append("\",\"values\":[");
            var occurrences = configuration.command().occurrences(entry.getKey());
            var values = occurrences.stream().flatMap(Collection::stream).toList();
            if (entry.getKey().equals("--offline") || entry.getKey().equals("--no-cache") || entry.getKey().equals("--no-proxy")) values = List.of(Boolean.toString(configuration.command().has(entry.getKey())));
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) output.append(',');
                output.append('"').append(JsonDiagnosticWriter.escape(safeValue(values.get(index)))).append('"');
            }
            output.append("]}");
        }
        return output.append("}}").toString();
    }

    private static String safeValue(String value) {
        if (value.length() >= 3 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':'
                && (value.charAt(2) == '\\' || value.charAt(2) == '/')) return value;
        try {
            var uri = java.net.URI.create(value);
            if (uri.isOpaque()) return uri.getScheme() + ":REDACTED";
            String authority = uri.getRawAuthority();
            if (authority != null && authority.contains("@")) authority = authority.substring(authority.lastIndexOf('@') + 1);
            if (uri.getRawUserInfo() != null || !Objects.equals(authority, uri.getRawAuthority()) || uri.getRawQuery() != null)
                return new java.net.URI(uri.getScheme(), authority, uri.getPath(),
                        uri.getRawQuery() == null ? null : "REDACTED", uri.getFragment()).toASCIIString();
        } catch (IllegalArgumentException | java.net.URISyntaxException invalid) {
            // Malformed URI values still must not echo authority or query data.
            if (value.matches("[A-Za-z][A-Za-z0-9+.-]*:.*") || value.contains("?") || value.contains("@"))
                return "REDACTED_URI";
        }
        return value;
    }
}
