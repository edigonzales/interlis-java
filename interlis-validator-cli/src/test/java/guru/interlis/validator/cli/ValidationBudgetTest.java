package guru.interlis.validator.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationBudgetTest {
    @TempDir Path directory;

    @Test
    void rejectsInvalidBudgetAssignmentsBeforeReadingTheTransfer() {
        assertFailure(run("--budget", "object-work"), 2, "options", "Expected NAME=N");
        assertFailure(run("--budget", "object-work="), 2, "options", "Expected NAME=N");
        assertFailure(run("--budget", "unknown=1"), 2, "options", "Unknown budget unknown");
        for (var value : List.of("object-work=0",
                "object-work=-1", "area-components=1.5", "area-components=9223372036854775808")) {
            assertFailure(run("--budget", value), 2, "options", "requires a positive integer");
        }
        assertFailure(run("--budget", "object-work=1", "--budget", "object-work=2"),
                2, "options", "Duplicate budget object-work");
    }

    @Test
    void appliesObjectAndAreaWorkLimitsIndependentlyForOneRun() throws IOException {
        writeParcel();
        assertFailure(run("--budget", "object-work=1"), 3, "transfer", "Dataset value traversal budget exceeded");
        assertFailure(run("--budget", "area-components=1"), 3, "validation",
                "Geometry coverage exceeds its prepared component budget");

        var defaults = run();
        assertEquals(0, defaults.exit(), defaults.report().toString());
        assertTrue(defaults.report().get("completed").getAsBoolean());
        assertEquals(1, defaults.report().get("objects").getAsInt());
        assertEquals(defaults, run("--budget", "object-work=1000", "--budget", "area-components=1000"));
    }

    @Test
    void reappliesTheXmlEventBudgetForAnXtf23ObjectAfterExhaustion() throws IOException {
        writeParcel();
        var limited = run("--budget", "xtf23-object-events=1");
        assertFailure(limited, 3, "transfer", "XML event budget of 1");
        assertEquals("LIMIT_EXCEEDED",
                limited.report().getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString());

        var recovered = run("--budget", "xtf23-object-events=1000");
        assertEquals(0, recovered.exit(), recovered.report().toString());
        assertTrue(recovered.report().get("completed").getAsBoolean());
        assertEquals(1, recovered.report().get("objects").getAsInt());
        assertEquals(run(), recovered);
    }

    @Test
    void reappliesDatasetConstraintWorkBudgetAfterExhaustion() throws IOException {
        writeParcel();
        assertFailure(run("--budget", "constraint-work=1"), 3, "validation", "Dataset constraint work limit exceeded");

        var recovered = run("--budget", "constraint-work=1000");
        assertEquals(0, recovered.exit(), recovered.report().toString());
        assertTrue(recovered.report().get("completed").getAsBoolean());
        assertEquals(1, recovered.report().get("objects").getAsInt());
        assertEquals(run(), recovered);
    }

    private void writeParcel() throws IOException {
        Files.writeString(directory.resolve("Parcels.ili"), """
                INTERLIS 2.3;
                MODEL Parcels AT "https://example.org/parcels" VERSION "1" =
                  DOMAIN Position = COORD 0.000 .. 10.000, 0.000 .. 10.000;
                  TOPIC Data =
                    CLASS Parcel =
                      shape : MANDATORY AREA WITH (STRAIGHTS) VERTEX Position WITHOUT OVERLAPS > 0.001;
                    END Parcel;
                  END Data;
                END Parcels.
                """);
        Files.writeString(directory.resolve("parcels.xtf"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="Parcels" VERSION="1" URI="https://example.org/parcels"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION><Parcels.Data BID="basket"><Parcels.Data.Parcel TID="one">
                    <shape><SURFACE><BOUNDARY><POLYLINE>
                      <COORD><C1>1</C1><C2>1</C2></COORD>
                      <COORD><C1>8</C1><C2>1</C2></COORD>
                      <COORD><C1>1</C1><C2>8</C2></COORD>
                      <COORD><C1>1</C1><C2>1</C2></COORD>
                    </POLYLINE></BOUNDARY></SURFACE></shape>
                  </Parcels.Data.Parcel></Parcels.Data></DATASECTION>
                </TRANSFER>
                """);
    }

    private Result run(String... overrides) {
        var args = new ArrayList<>(List.of("--offline", "--json", "parcels.xtf"));
        args.addAll(List.of(overrides));
        var output = new StringWriter();
        var error = new StringWriter();
        int exit = ValidatorCommand.run(args, directory, new PrintWriter(output), new PrintWriter(error),
                CancellationToken.none());
        return new Result(exit, JsonParser.parseString(output.toString()).getAsJsonObject());
    }

    private static void assertFailure(Result result, int code, String phase, String message) {
        assertEquals(code, result.exit(), result.report().toString());
        assertEquals(code, result.report().get("exitCode").getAsInt());
        assertFalse(result.report().get("completed").getAsBoolean());
        assertEquals(phase, result.report().get("phase").getAsString());
        var diagnostic = result.report().getAsJsonArray("diagnostics").get(0).getAsJsonObject();
        assertTrue(diagnostic.get("message").getAsString().contains(message), diagnostic.toString());
    }

    private record Result(int exit, JsonObject report) { }
}
