package guru.interlis.gui.infrastructure;

import guru.interlis.compiler.WarningPolicy;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.gui.application.ConfiguredRepository;
import guru.interlis.gui.application.SessionOptions;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositoryProxyConfiguration;
import guru.interlis.repository.RepositorySource;
import guru.interlis.repository.SourceLocator;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Versioned properties, loaded and saved off the EDT under an explicitly supplied directory. */
public final class GuiSettingsStore {
    private static final String VERSION = "1";
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_REPOSITORIES = 1000;
    private final Path directory;
    private final Path file;

    public record Loaded(GuiSettings settings, String warning, boolean writable) {
    }

    public GuiSettingsStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        file = this.directory.resolve("settings.properties");
    }

    public Loaded load() {
        byte[] content;
        try (var input = Files.newInputStream(file)) {
            content = input.readNBytes(MAX_BYTES + 1);
        } catch (NoSuchFileException absent) {
            return new Loaded(GuiSettings.defaults(), "", true);
        } catch (IOException unavailable) {
            return new Loaded(GuiSettings.defaults(), "Settings could not be read. Using defaults.", false);
        }
        try {
            if (content.length > MAX_BYTES) {
                throw new IllegalArgumentException("Settings file is too large");
            }
            var properties = new Properties();
            String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(content)).toString();
            properties.load(new StringReader(text));
            return new Loaded(decode(properties), "", true);
        } catch (IllegalArgumentException | java.time.DateTimeException | IOException invalid) {
            try {
                Files.copy(file, directory.resolve("settings.invalid-" + UUID.randomUUID() + ".properties"));
                return new Loaded(GuiSettings.defaults(), "Invalid settings were backed up. Using defaults.", true);
            } catch (IOException backupFailed) {
                return new Loaded(GuiSettings.defaults(), "Invalid settings could not be backed up. Changes will not be saved.", false);
            }
        }
    }

    /** Replaces the complete file only after a new document has been written successfully. */
    public void save(GuiSettings settings) throws IOException {
        var properties = encode(settings);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".settings-", ".tmp");
        try {
            try (var output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(output, "INTERLIS Compiler GUI preferences");
            }
            if (Files.size(temporary) > MAX_BYTES) {
                throw new IOException("Settings exceed their size limit");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static GuiSettings decode(Properties p) {
        if (!VERSION.equals(p.getProperty("schemaVersion", VERSION))) {
            throw new IllegalArgumentException("Unknown settings version");
        }
        var defaults = GuiSettings.defaults();
        var options = defaults.session();
        var fetch = options.repositories().fetchPolicy();
        var repositories = new ArrayList<ConfiguredRepository>();
        int count = integer(p, "repositories.count", 0);
        if (count < 0 || count > MAX_REPOSITORIES) {
            throw new IllegalArgumentException("Invalid repository count");
        }
        for (int i = 0; i < count; i++) {
            String prefix = "repository." + i + ".";
            var source = repository(p.getProperty(prefix + "base", ""));
            if (repositories.stream().anyMatch(row -> row.source().base().equals(source.base()))) {
                throw new IllegalArgumentException("Duplicate repository base");
            }
            repositories.add(new ConfiguredRepository(source, flag(p, prefix + "enabled", true)));
        }
        var enabled = repositories.stream().filter(ConfiguredRepository::enabled).map(ConfiguredRepository::source).toList();
        var policy = new FetchPolicy(flag(p, "network.offline", fetch.offline()), fetch.maxBytes(),
                duration(p, "network.connect", fetch.connectTimeout()), duration(p, "network.read", fetch.readTimeout()),
                optional(p, "network.request").map(Duration::parse));
        var proxy = switch (p.getProperty("proxy.mode", "SYSTEM")) {
            case "SYSTEM" -> new RepositoryProxyConfiguration.SystemDefault();
            case "DIRECT" -> new RepositoryProxyConfiguration.Direct();
            case "HTTP" -> new RepositoryProxyConfiguration.HttpProxy(p.getProperty("proxy.host", ""),
                    integer(p, "proxy.port", 0));
            default -> throw new IllegalArgumentException("Unknown proxy mode");
        };
        var cache = new RepositoryCacheConfiguration(optional(p, "cache.directory").map(GuiSettingsStore::absolutePath));
        var session = new SessionOptions(new RepositoryConfig(enabled, policy), cache, proxy,
                WarningPolicy.valueOf(p.getProperty("warnings", options.warnings().name())),
                DiagnosticLanguage.valueOf(p.getProperty("diagnostics.language", options.diagnosticLanguage().name())));
        Optional<GuiSettings.Bounds> bounds = Optional.empty();
        if (p.containsKey("window.width") && p.containsKey("window.height")) {
            bounds = Optional.of(new GuiSettings.Bounds(integer(p, "window.x", 0), integer(p, "window.y", 0),
                    integer(p, "window.width", 1280), integer(p, "window.height", 860)));
        }
        var dividers = new LinkedHashMap<String, Integer>();
        p.stringPropertyNames().stream().filter(key -> key.matches("divider\\.[a-z-]+")).forEach(key -> {
            int value = integer(p, key, -1);
            if (value >= 0) {
                dividers.put(key.substring("divider.".length()), value);
            }
        });
        return new GuiSettings(GuiSettings.Theme.valueOf(p.getProperty("theme", defaults.theme().name())), session,
                repositories, bounds, dividers, optional(p, "file.lastDirectory").map(GuiSettingsStore::absolutePath));
    }

    private static Properties encode(GuiSettings settings) {
        var p = new Properties();
        p.setProperty("schemaVersion", VERSION);
        p.setProperty("theme", settings.theme().name());
        var options = settings.session();
        p.setProperty("diagnostics.language", options.diagnosticLanguage().name());
        p.setProperty("warnings", options.warnings().name());
        var policy = options.repositories().fetchPolicy();
        p.setProperty("network.offline", Boolean.toString(policy.offline()));
        p.setProperty("network.connect", policy.connectTimeout().toString());
        p.setProperty("network.read", policy.readTimeout().toString());
        policy.requestTimeout().ifPresent(value -> p.setProperty("network.request", value.toString()));
        options.cache().directory().ifPresent(path -> p.setProperty("cache.directory", path.toString()));
        switch (options.proxy()) {
            case RepositoryProxyConfiguration.SystemDefault ignored -> p.setProperty("proxy.mode", "SYSTEM");
            case RepositoryProxyConfiguration.Direct ignored -> p.setProperty("proxy.mode", "DIRECT");
            case RepositoryProxyConfiguration.HttpProxy(var host, var port) -> {
                p.setProperty("proxy.mode", "HTTP");
                p.setProperty("proxy.host", host);
                p.setProperty("proxy.port", Integer.toString(port));
            }
        }
        if (settings.repositories().size() > MAX_REPOSITORIES) {
            throw new IllegalArgumentException("Too many repositories");
        }
        p.setProperty("repositories.count", Integer.toString(settings.repositories().size()));
        for (int i = 0; i < settings.repositories().size(); i++) {
            var configured = settings.repositories().get(i);
            String prefix = "repository." + i + ".";
            var source = repository(configured.source().base().uri().toString());
            if (!source.equals(configured.source())) {
                throw new IllegalArgumentException("Repository index does not match the configured base");
            }
            p.setProperty(prefix + "base", source.base().uri().toString());
            p.setProperty(prefix + "enabled", Boolean.toString(configured.enabled()));
        }
        settings.window().ifPresent(window -> {
            p.setProperty("window.x", Integer.toString(window.x()));
            p.setProperty("window.y", Integer.toString(window.y()));
            p.setProperty("window.width", Integer.toString(window.width()));
            p.setProperty("window.height", Integer.toString(window.height()));
        });
        settings.dividers().forEach((name, value) -> p.setProperty("divider." + name, Integer.toString(value)));
        settings.lastDirectory().ifPresent(path -> p.setProperty("file.lastDirectory", path.toString()));
        return p;
    }

    private static RepositorySource repository(String value) {
        URI uri = URI.create(value);
        if (!uri.isAbsolute() || uri.isOpaque() || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getPath() == null || !uri.getPath().endsWith("/")) {
            throw new IllegalArgumentException("Invalid repository base");
        }
        switch (uri.getScheme()) {
            case "file" -> absolutePath(Path.of(uri).toString());
            case "http", "https" -> {
                if (uri.getHost() == null) {
                    throw new IllegalArgumentException("Invalid repository host");
                }
            }
            default -> throw new IllegalArgumentException("Invalid repository scheme");
        }
        return new RepositorySource(new RepositoryBase(uri), new SourceLocator(uri.resolve("ilimodels.xml").toString()));
    }

    private static Path absolutePath(String value) {
        Path path = Path.of(value);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Expected an absolute settings path");
        }
        return path.normalize();
    }

    private static Optional<String> optional(Properties p, String key) {
        return Optional.ofNullable(p.getProperty(key)).filter(value -> !value.isBlank());
    }

    private static int integer(Properties p, String key, int fallback) {
        return optional(p, key).map(Integer::parseInt).orElse(fallback);
    }

    private static Duration duration(Properties p, String key, Duration fallback) {
        return optional(p, key).map(Duration::parse).orElse(fallback);
    }

    private static boolean flag(Properties p, String key, boolean fallback) {
        return switch (p.getProperty(key, Boolean.toString(fallback))) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean setting");
        };
    }
}
