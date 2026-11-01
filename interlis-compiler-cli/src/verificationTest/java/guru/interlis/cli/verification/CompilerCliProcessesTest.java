package guru.interlis.cli.verification;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private static Map<String,HostChecks.Result> colors() {
        var rows=new LinkedHashMap<String,HostChecks.Result>();
        String plain="x:1:1: ERROR [ILI-SEM-0001] Missing\n  1 | Missing\n    | ^~~~~~~";
        String ansi=plain.replace("ERROR","\u001b[31mERROR\u001b[0m");
        for(String name:CompilerCliProcesses.REQUIRED)if(name.startsWith("color-")) {
            boolean artifact=name.startsWith("color-json-")||name.startsWith("color-diagram-")||name.equals("color-format-always")||name.equals("color-format-never");
            int exit=name.equals("color-invalid")||name.equals("color-duplicate")?2:artifact&&!name.startsWith("color-json-")||name.equals("color-analyze")?0:1;
            String out=artifact?(name.startsWith("color-json-")?"{\"diagnostics\":[{}]}":"artifact"):"";
            String err=artifact?"":name.equals("color-no-color")?"kann an dieser Stelle nicht aufgelöst werden":
                    List.of("color-always","color-always-override","color-compile","color-analyze","color-format-error").contains(name)?ansi:plain;
            rows.put(name,new HostChecks.Result(List.of(),exit,out,err,1));
        }
        return rows;
    }
    @Test void strictColorCheckerRejectsAnsiLeaksMissingResetsAndFalseExits() {
        assertDoesNotThrow(()->CompilerCliProcesses.verifyColorCases(colors()));
        for(String mutation:List.of("json","reset","cursor","exit","artifact","missing")) {
            var rows=colors();String key=mutation.equals("json")?"color-json-always":mutation.equals("artifact")?"color-format-always":"color-always";
            var r=rows.get(key);
            if(mutation.equals("missing"))rows.remove(key);
            else rows.put(key,new HostChecks.Result(r.command(),mutation.equals("exit")?0:r.exitCode(),
                    mutation.equals("json")||mutation.equals("artifact")?r.stdout()+"\u001b[31m":r.stdout(),
                    mutation.equals("reset")?r.stderr().replace("\u001b[0m",""):mutation.equals("cursor")?r.stderr()+"\u001b[2J":r.stderr(),1));
            assertThrows(IllegalArgumentException.class,()->CompilerCliProcesses.verifyColorCases(rows),mutation);
        }
        assertThrows(IllegalArgumentException.class,()->CompilerCliProcesses.stripSgr("\u001b]8;;bad\u0007"));
    }
}
