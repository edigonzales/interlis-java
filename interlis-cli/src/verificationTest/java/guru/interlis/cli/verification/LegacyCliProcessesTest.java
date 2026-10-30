package guru.interlis.cli.verification;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCliProcessesTest {
    private static HostChecks.Result result(int exit, String out, String err) {
        return new HostChecks.Result(List.of("test"), exit, out, err, 1);
    }
    @Test void wrongReferenceExitPollutedChannelsAndCrashesCannotEarnCompatibility() {
        var expected = new LegacyCliProcesses.Expectation("valid", List.of("model.ili"), 0, 0, true);
        for (var bad : List.of(result(1, "", ""), result(0, "polluted", ""), result(0, "", "Exception in thread main")))
            assertThrows(IllegalArgumentException.class, () -> LegacyCliProcesses.compare(expected, result(0, "", ""), bad));
        assertThrows(IllegalArgumentException.class, () -> LegacyCliProcesses.compare(expected, result(1, "", ""), result(0, "", "")));
        assertThrows(IllegalArgumentException.class, () -> LegacyCliProcesses.compare(expected, null, result(0, "", "")));
    }
    @Test void documentedUnsupportedOperationMustStillBeAnActualFailure() {
        var expected = new LegacyCliProcesses.Expectation("unsupported", List.of("-oXSD"), 0, 1, false);
        assertDoesNotThrow(() -> LegacyCliProcesses.compare(expected, null, result(1, "", "unsupported option")));
        assertThrows(IllegalArgumentException.class, () -> LegacyCliProcesses.compare(expected, null, result(0, "", "")));
    }
}
