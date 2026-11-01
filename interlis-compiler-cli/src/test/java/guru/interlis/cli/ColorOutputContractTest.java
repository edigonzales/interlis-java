package guru.interlis.cli;

import guru.interlis.compiler.CompilerServices;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ColorOutputContractTest {
    @TempDir Path directory;

    @Test void bothSpellingsAndTerminatorForEveryDiagnosticCommand() {
        var parser = new DefaultCommandLineParser();
        for (var command : List.of("check", "compile", "analyze", "diagram", "format")) {
            var spec = CommandSpec.standard(command);
            for (var mode : List.of("auto", "always", "never")) {
                var a = assertInstanceOf(ParsedCommand.class, parser.parse(List.of(command, "--color="+mode, "--diagnostic-language", "de", "--", "--color=literal.ili"), spec));
                var b = assertInstanceOf(ParsedCommand.class, parser.parse(List.of(command, "--color", mode, "--diagnostic-language", "de", "--", "--color=literal.ili"), spec));
                assertEquals(a, b); assertEquals(Optional.of(mode), a.value("--color"));
                assertEquals(List.of("--color=literal.ili"), a.files());
            }
        }
    }

    @Test void invalidOptionsFailBeforeSourceOrConfigurationIo() {
        for (var command : List.of("check", "compile", "analyze", "diagram", "format")) {
            for (var tail : List.of(List.of("--color"), List.of("--color="), List.of("--color=ALWAYS"),
                    List.of("--color", "bad"), List.of("--color=auto", "--color", "never"),
                    List.of("--color", "auto", "--color=always"), List.of("--color", "--json"))) {
                var args = new ArrayList<>(List.of(command)); args.addAll(tail);
                var run = run(args, Map.of(), true);
                assertEquals(CliTermination.USAGE_ERROR, run.result.termination(), args.toString());
                assertFalse(run.err.contains("loading")); assertTrue(run.out.isEmpty());
            }
        }
        for (var command : List.of("help", "version", "capabilities"))
            assertEquals(2, run(List.of(command, "--color=always"), Map.of(), true).result.exitCode());
        assertTrue(run(List.of("help"), Map.of(), false).out.contains("--color=auto|always|never"));
        assertInstanceOf(CliParseOutcome.InvalidCommandLine.class, new DefaultCommandLineParser().parse(
                List.of("check", "--diagnostic-language=de"), CommandSpec.standard("check")));
        assertEquals(2, run(List.of("check", "--config", "absent.properties", "--color=bad"), Map.of(), true).result.exitCode());
    }

    @Test void exhaustivePolicyTable() {
        var policy = new ColorPolicy();
        for (var mode : ColorMode.values()) for (boolean terminal : List.of(false,true))
            for (boolean machine : List.of(false,true)) for (var noColor : List.of("absent", "", "1"))
                for (var term : List.of("absent", "xterm", "dumb", "DuMb")) {
                    var env = new HashMap<String,String>();
                    if (!noColor.equals("absent")) env.put("NO_COLOR",noColor);
                    if (!term.equals("absent")) env.put("TERM",term);
                    boolean expected = !machine && (mode == ColorMode.ALWAYS || mode == ColorMode.AUTO
                            && terminal && !noColor.equals("1") && !term.equalsIgnoreCase("dumb"));
                    assertEquals(expected, policy.useAnsi(mode,new TerminalCapabilities(terminal),env,machine),
                            mode+" "+terminal+" "+machine+" "+env);
                }
    }

    @Test void fixedPaletteAndResetOnly() {
        var styler = new AnsiStyler();
        var codes = List.of("31","33","36","34","36","90"); int i=0;
        for (var role : AnsiStyler.Role.values()) {
            assertEquals("\u001b["+codes.get(i++)+"mFehler 🦊\u001b[0m",styler.style(role,"Fehler 🦊",true));
            assertEquals("Fehler 🦊",styler.style(role,"Fehler 🦊",false));
        }
    }

    @Test void capabilitySurvivesEffectiveEnvironmentAndJsonIsIdentical() throws Exception {
        Files.writeString(directory.resolve("bad.ili"), "INTERLIS 2.4; MODEL Bad AT \"x\" VERSION \"1\" = TOPIC T=CLASS C=value:Missing;END C;END T;END Bad.");
        var plain = run(List.of("check","--color=never","bad.ili"), Map.of(),true);
        assertEquals(1,plain.result.exitCode()); assertFalse(plain.err.contains("\u001b"));
        for (var args : List.of(List.of("check","bad.ili"), List.of("check","--color=auto","bad.ili"),List.of("check","--color=always","bad.ili"))) {
            var colored = run(args,Map.of(),true); assertEquals(plain.result.diagnostics(),colored.result.diagnostics());
            assertTrue(colored.err.contains("\u001b[31mERROR\u001b[0m")); assertEquals(plain.err,stripSgr(colored.err));
        }
        for (var env : List.of(Map.of("NO_COLOR","1"),Map.of("TERM","dumb"))) {
            assertEquals(plain.err,run(List.of("check","bad.ili"),env,true).err);
            assertTrue(run(List.of("check","--color=always","bad.ili"),env,false).err.contains("\u001b[31m"));
        }
        assertEquals(plain.err,run(List.of("check","bad.ili"),Map.of(),false).err);
        var json = run(List.of("check","--json","bad.ili"),Map.of(),true);
        for (var mode : List.of("auto","always","never")) {
            var other = run(List.of("check","--json","--color="+mode,"bad.ili"),Map.of(),true);
            assertEquals(json.out,other.out); assertTrue(other.err.isEmpty()); assertFalse(other.out.contains("\u001b"));
        }
        var out = new PrintStream(new ByteArrayOutputStream());
        assertFalse(new CliEnvironment(directory,InputStream.nullInputStream(),out,out,Map.of()).terminalCapabilities().stderrInteractive());
        assertFalse(new CliEnvironment(directory,InputStream.nullInputStream(),out,out,Map.of(),CompilerServices.defaults()).terminalCapabilities().stderrInteractive());
    }

    static String stripSgr(String text) {
        String stripped = text.replaceAll("\\x1b\\[(?:0|31|33|34|36|90)m", "");
        assertFalse(stripped.contains("\u001b"), "Unexpected terminal control sequence"); return stripped;
    }
    private Run run(List<String> args,Map<String,String> env,boolean interactive) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        var result = new DefaultCompilerCli().run(args,new CliEnvironment(directory,InputStream.nullInputStream(),
                new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8),env,
                CompilerServices.defaults(),new TerminalCapabilities(interactive)),CancellationToken.none());
        return new Run(result,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private record Run(CliExecutionResult result,String out,String err) { }
}
