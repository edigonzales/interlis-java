package guru.interlis.cli.evidence;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Recorded SDK processes are portable evidence, independent of the reading host OS. */
class RuntimeReportPathsTest {
    @Test void nativeWindowsAndUnixPathsKeepExactlyTheSameCommandContract() {
        for (String root : List.of("C:\\fixture", "/fixture", "\\\\server\\share\\fixture")) {
            var report = report(root);
            assertDoesNotThrow(() -> RuntimeReports.sdk(report));
            report.addProperty("cli", "interlis-cli/build/install/interlis-cli/bin/other.exe");
            assertThrows(IllegalArgumentException.class, () -> RuntimeReports.sdk(report));
        }
        for (String relative : List.of("fixture", "C:fixture")) {
            assertThrows(IllegalArgumentException.class, () -> RuntimeReports.sdk(report(relative)));
        }
    }

    private static JsonObject report(String root) {
        boolean windows = root.contains("\\") || root.startsWith("C:");
        String slash = windows ? "\\" : "/", temporary = windows ? "D:\\temporary\\model.ili" : "/temporary/model.ili";
        String model = root + slash + "testdata" + slash + "models" + slash;
        var arguments = List.of(List.of("version"), List.of("version", "--json"), List.of("capabilities"),
                List.of("check", model + "base24.ili", model + "survey24.ili"), List.of("check", model + "broken24.ili"),
                List.of("format", "--check", temporary), List.of("format", "--indent", "4", "--line-ending", "lf", temporary),
                List.of("format", "--write", "--indent", "4", "--line-ending", "lf", temporary),
                List.of("check", "--no-such-option", model + "base24.ili"));
        var exits = List.of(0,0,0,0,1,1,0,0,2);
        var report = new JsonObject(); report.addProperty("schemaVersion", "1.0");
        report.addProperty("cli", "interlis-cli/build/install/interlis-cli/bin/interlis-cli" + (windows ? ".bat" : ""));
        var commands = new JsonArray(); report.add("commands", commands);
        for (int i=0; i<arguments.size(); i++) {
            var row = new JsonObject(); row.add("arguments", new Gson().toJsonTree(arguments.get(i)));
            row.addProperty("exitCode", exits.get(i)); row.addProperty("stdoutSha256", "0".repeat(64));
            row.addProperty("stdoutLines", 0); row.addProperty("stderrLines", 0); commands.add(row);
        }
        return report;
    }
}
