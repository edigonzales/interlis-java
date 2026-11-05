package guru.interlis.gui.infrastructure;

import guru.interlis.compiler.WarningPolicy;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.execution.CancellationToken;
import guru.interlis.gui.application.ConfiguredRepository;
import guru.interlis.gui.application.RepositoryCatalogService;
import guru.interlis.gui.application.SessionOptions;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.RepositoryCacheConfiguration;
import guru.interlis.repository.RepositoryConfig;
import guru.interlis.repository.RepositoryProxyConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GuiSettingsStoreTest {
    @TempDir
    Path directory;

    @Test
    void roundTripRetainsConfigurationOrderAndOnlyPreferences() throws Exception {
        var adapter = new RepositoryCatalogService();
        var first = adapter.repository("https://example.invalid/sub", directory, CancellationToken.none());
        var local = Files.createDirectory(directory.resolve("Repository ä"));
        var second = adapter.repository(local.toString(), directory, CancellationToken.none());
        var config = new RepositoryConfig(List.of(second),
                new FetchPolicy(true, FetchPolicy.defaults().maxBytes(), Duration.ofSeconds(9),
                        Duration.ofSeconds(20), Optional.of(Duration.ofSeconds(42))));
        var session = new SessionOptions(config, RepositoryCacheConfiguration.at(directory.resolve("cache")),
                new RepositoryProxyConfiguration.HttpProxy("proxy.example.invalid", 8080),
                WarningPolicy.REJECT_WARNINGS, DiagnosticLanguage.DE);
        var expected = new GuiSettings(GuiSettings.Theme.DARK, session,
                List.of(new ConfiguredRepository(first, false), new ConfiguredRepository(second, true)),
                Optional.of(new GuiSettings.Bounds(12, 18, 1100, 780)), Map.of("repository-main", 250), Optional.of(local));
        var root = directory.resolve("settings");
        var store = new GuiSettingsStore(root);
        assertEquals(GuiSettings.defaults(), store.load().settings());
        store.save(expected);
        assertEquals(expected, store.load().settings());
        var text = Files.readString(root.resolve("settings.properties"));
        assertTrue(text.contains("schemaVersion=1"));
        assertFalse(text.contains("Compiler result"));
        assertFalse(text.contains("SourceSnapshot"));
        assertFalse(text.contains("secret"));
        try (var files = Files.list(root)) {
            assertEquals(List.of("settings.properties"), files.map(file -> file.getFileName().toString()).toList());
        }
    }

    @Test
    void damagedUnsupportedAndPartialPreferencesUseRecoverableDefaults() throws Exception {
        Path file = directory.resolve("settings.properties");
        var store = new GuiSettingsStore(directory);
        Files.writeString(file, "schemaVersion=1\ntheme=DARK\nunknown.property=ignored\n");
        assertEquals(GuiSettings.Theme.DARK, store.load().settings().theme());
        assertEquals(SessionOptions.defaults(), store.load().settings().session());
        for (String content : List.of("schemaVersion=93\n", "network.read=not-a-duration\n",
                "repositories.count=1\nrepository.0.base=https://user:secret@example.invalid/repo/\n")) {
            Files.writeString(file, content);
            var recovered = store.load();
            assertEquals(GuiSettings.defaults(), recovered.settings());
            assertTrue(recovered.writable());
            assertTrue(recovered.warning().contains("backed up"));
            assertEquals(content, Files.readString(file));
            try (var files = Files.list(directory)) {
                assertTrue(files.filter(path -> path.getFileName().toString().startsWith("settings.invalid-"))
                        .anyMatch(path -> {
                            try {
                                return content.equals(Files.readString(path));
                            } catch (IOException failure) {
                                throw new java.io.UncheckedIOException(failure);
                            }
                        }));
            }
        }
    }

    @Test
    void failedWritesLeaveThePreviousSettingsAndDoNotPersistUnsafeLocations() throws Exception {
        var store = new GuiSettingsStore(directory);
        store.save(GuiSettings.defaults());
        Path good = directory.resolve("settings.properties");
        byte[] original = Files.readAllBytes(good);
        var unsafeBase = new guru.interlis.repository.RepositoryBase(java.net.URI.create("https://name:secret@example.invalid/"));
        var unsafe = new guru.interlis.repository.RepositorySource(unsafeBase,
                new guru.interlis.repository.SourceLocator("https://name:secret@example.invalid/ilimodels.xml"));
        var value = new GuiSettings(GuiSettings.Theme.LIGHT, SessionOptions.defaults(),
                List.of(new ConfiguredRepository(unsafe, false)), Optional.empty(), Map.of(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> store.save(value));
        assertArrayEquals(original, Files.readAllBytes(good));
        assertThrows(IOException.class, () -> new GuiSettingsStore(good.resolve("cannot-create")).save(GuiSettings.defaults()));
        assertArrayEquals(original, Files.readAllBytes(good));
    }
}
