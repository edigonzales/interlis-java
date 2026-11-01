package guru.interlis.cli;

import com.google.gson.*;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryNetworkConfigurationTest {
    @TempDir Path directory;
    @Test void defaultsAreExplainedForEveryRepositoryCommand() {
        for (String command : List.of("check", "compile", "analyze", "diagram")) {
            var run = run(command, "--explain-config"); var settings = settings(run);
            assertSetting(settings, "connect-timeout", "15", "PROFILE_DEFAULT");
            assertSetting(settings, "read-timeout", "40", "PROFILE_DEFAULT");
            assertSetting(settings, "request-timeout", "off", "PROFILE_DEFAULT");
            assertSetting(settings, "proxy-mode", "system", "PROFILE_DEFAULT");
            assertSetting(settings, "no-proxy", "false", "PROFILE_DEFAULT");
        }
    }
    @Test void commandLineOverridesFileWhileHostRetainsConfiguredPort() throws Exception {
        config("connect-timeout=7\nread-timeout=9\nrequest-timeout=25\nproxy=old.example\nproxyPort=3128\n");
        var settings = settings(run("check", "--config", "network.properties", "--proxy", "new.example", "--read-timeout", "8", "--request-timeout", "off", "--explain-config"));
        assertSetting(settings, "connect-timeout", "7", "CONFIGURATION_FILE");
        assertSetting(settings, "read-timeout", "8", "CLI");
        assertSetting(settings, "request-timeout", "off", "CLI");
        assertSetting(settings, "proxy", "new.example", "CLI");
        assertSetting(settings, "proxyPort", "3128", "CONFIGURATION_FILE");
        assertSetting(settings, "proxy-mode", "http", "CLI");
        var defaultPort = settings(run("check", "--proxy", "localhost", "--explain-config"));
        assertSetting(defaultPort, "proxyPort", "80", "PROFILE_DEFAULT");
        var overriddenPort = settings(run("check", "--config", "network.properties", "--proxyPort", "8080", "--explain-config"));
        assertSetting(overriddenPort, "proxy", "old.example", "CONFIGURATION_FILE");
        assertSetting(overriddenPort, "proxyPort", "8080", "CLI");
    }
    @Test void directAndManualChoicesOverrideLowerLevelChoice() throws Exception {
        config("proxy=old.example\nproxyPort=3128\n");
        var settings = settings(run("check", "--config", "network.properties", "--no-proxy", "--explain-config"));
        assertSetting(settings, "proxy-mode", "direct", "CLI");
        assertFalse(settings.has("--proxy")); assertFalse(settings.has("--proxyPort"));
        config("no-proxy=true\n");
        settings = settings(run("check", "--config", "network.properties", "--proxy", "localhost", "--explain-config"));
        assertSetting(settings, "proxy-mode", "http", "CLI");
        assertSetting(settings, "no-proxy", "false", "PROFILE_DEFAULT");
        config("no-proxy=false\n");
        settings = settings(run("check", "--config", "network.properties", "--explain-config"));
        assertSetting(settings, "proxy-mode", "system", "PROFILE_DEFAULT");
        assertSetting(settings, "no-proxy", "false", "CONFIGURATION_FILE");
    }
    @Test void invalidCliValuesFailBeforeSourceLoadingEvenWhenExplaining() {
        for (String option : List.of("--connect-timeout", "--read-timeout", "--request-timeout")) {
            for (String value : List.of("0", "-1", "0.5", "NaN", "9223372036854775807", "offf"))
                usage(run("check", option, value, "--explain-config"));
        }
        usage(run("check", "--connect-timeout", "off", "missing.ili"));
        usage(run("check", "--read-timeout", "off", "missing.ili"));
        for (String host : List.of("http://localhost", "user:marked-secret@localhost", "localhost/path", "localhost?marked-secret", "localhost:80", "localhost\n")) {
            var run = run("check", "--proxy", host, "--explain-config"); usage(run);
            assertFalse(run.out.contains("marked-secret")); assertFalse(run.err.contains("marked-secret"));
        }
        for (String port : List.of("0", "65536", "-1", "abc", "999999999999999999999"))
            usage(run("check", "--proxy", "localhost", "--proxyPort", port, "--explain-config"));
        usage(run("check", "--proxyPort", "80", "--explain-config"));
        usage(run("check", "--no-proxy", "--proxy", "localhost", "--explain-config"));
        usage(run("check", "--no-proxy", "--proxyPort", "80", "--explain-config"));
    }
    @Test void invalidPropertiesAndSameLevelConflictsCannotBeHiddenByCli() throws Exception {
        for (String contents : List.of("read-timeout=0", "connect-timeout=off", "request-timeout=-1", "proxyPort=80", "no-proxy=maybe",
                "no-proxy=true\nproxy=localhost", "no-proxy=true\nproxyPort=80")) {
            config(contents + "\n"); usage(run("check", "--config", "network.properties", "--explain-config"));
        }
        config("no-proxy=true\nproxy=localhost\n");
        usage(run("check", "--config", "network.properties", "--proxy", "other.example", "--explain-config"));
    }
    @Test void networkOptionsDoNotChangeLocalSourceCompilation() throws Exception {
        Files.writeString(directory.resolve("local.ili"), "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = END F.");
        assertEquals(CliTermination.SUCCESS, run("check", "--proxy", "unreachable.invalid", "--connect-timeout", "1", "local.ili").result.termination());
    }
    private void config(String text) throws IOException { Files.writeString(directory.resolve("network.properties"), text); }
    private static void usage(Run run) { assertEquals(CliTermination.USAGE_ERROR, run.result.termination(), run.err); assertFalse(run.err.contains("source load failed")); }
    private static JsonObject settings(Run run) {
        assertEquals(CliTermination.SUCCESS, run.result.termination(), run.err);
        return JsonParser.parseString(run.out).getAsJsonObject().getAsJsonObject("settings");
    }
    private static void assertSetting(JsonObject settings, String key, String value, String origin) {
        var setting = settings.getAsJsonObject("--" + key); assertNotNull(setting, key);
        assertEquals(value, setting.getAsJsonArray("values").get(0).getAsString(), key);
        assertEquals(origin, setting.get("origin").getAsString(), key);
    }
    private Run run(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        var env = new CliEnvironment(directory, InputStream.nullInputStream(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
        var result = new DefaultCompilerCli().run(List.of(args), env, CancellationToken.none());
        return new Run(result, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    private record Run(CliExecutionResult result, String out, String err) { }
}
