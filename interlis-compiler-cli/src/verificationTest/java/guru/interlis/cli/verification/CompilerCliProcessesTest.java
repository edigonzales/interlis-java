package guru.interlis.cli.verification;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompilerCliProcessesTest {
    @Test void wrongExitPollutedStdoutAndCrashFailTheStandardProcessProfile() {
        for (var result : List.of(new HostChecks.Result(List.of(), 1, "", "", 1),
                new HostChecks.Result(List.of(), 0, "polluted", "", 1),
                new HostChecks.Result(List.of(), 0, "", "Exception in thread main", 1)))
            assertThrows(IllegalArgumentException.class, () -> CompilerCliProcesses.verify("valid", result, 0, false));
    }
    @Test void usageErrorsMustActuallyFailAndKeepStdoutEmpty() {
        assertDoesNotThrow(() -> CompilerCliProcesses.verify("usage", new HostChecks.Result(List.of(), 2, "", "usage", 1), 2, false));
        assertThrows(IllegalArgumentException.class, () -> CompilerCliProcesses.verify("usage", new HostChecks.Result(List.of(), 0, "", "", 1), 2, false));
    }
}
