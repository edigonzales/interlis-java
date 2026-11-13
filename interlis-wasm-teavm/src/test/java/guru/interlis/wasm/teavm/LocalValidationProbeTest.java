package guru.interlis.wasm.teavm;

import guru.interlis.bridge.Json;
import guru.interlis.bridge.JsonValue;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalValidationProbeTest {
    @Test
    void validatesTheBrowserExamplesOnTheJvm() throws Exception {
        var model = Files.readString(Path.of("test", "LocalValidation.ili"));
        check(model, "20.00", true, """
                {"result":"PASSED_CHECKED_SCOPE","violations":0,"diagnostics":[]}
                """);
        check(model, "200.00", true, """
                {"result":"FAILED_CHECKED_SCOPE","violations":1,"diagnostics":[{"code":"VALUE_OUT_OF_RANGE","path":["amount"]}]}
                """);
        check(model, "5.00", true, """
                {"result":"FAILED_CHECKED_SCOPE","violations":1,"diagnostics":[{"code":"MANDATORY_CONSTRAINT_FALSE","path":[]}]}
                """);
        check(model, "20.00", false, """
                {"result":"FAILED_CHECKED_SCOPE","violations":1,"diagnostics":[{"code":"REQUIRED_VALUE_MISSING","path":["label"]}]}
                """);
        check(model.replace(">= 10.00", ">= 25.00"), "20.00", true, """
                {"result":"FAILED_CHECKED_SCOPE","violations":1,"diagnostics":[{"code":"MANDATORY_CONSTRAINT_FALSE","path":[]}]}
                """);
        var invalid = (JsonValue.JsonObject) Json.parse(LocalValidationProbeEntryPoint.validateExample(
                model.replace("CLASS Item", "CLASS = Item"), "20.00", "label", true));
        assertEquals(new JsonValue.JsonString("COMPILATION_REJECTED"), invalid.members().get("result"));
    }

    private static void check(String model, String amount, boolean includeLabel, String expected) {
        assertEquals(Json.parse(expected), Json.parse(
                LocalValidationProbeEntryPoint.validateExample(model, amount, "label", includeLabel)));
    }
}
