package guru.interlis.cli.verification;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static guru.interlis.cli.verification.HostChecks.*;

/** Repository canary; runtime absence is independently proved by the confined phase run. */
public final class PythonFreeCheck {
    private static final Pattern COMMAND=Pattern.compile("(?im)(?:^#![^\\r\\n]*\\b|[\"'`](?:(?:/|\\./|\\.\\./|[A-Za-z]:[\\\\/])[^\"'`\\r\\n]*)?)(?:py(?:thon|py)[0-9.]*|pip[0-9.]*|uvx?)(?=[\"'`\\s]|$)|^\\s*(?:(?:run|command)\\s*:\\s*|exec\\s+)?(?:py(?:thon|py)[0-9.]*|pip[0-9.]*|uvx?)\\s");
    private PythonFreeCheck() { }
    static void validate(String path,String text) {
        require(!path.toLowerCase(Locale.ROOT).matches(".*\\.py[wc]?$"),"Forbidden source remains: "+path);
        if(path.startsWith("docs/"))return; // Historical evidence and quotations are preserved.
        require(!Path.of(path).getFileName().toString().matches("(?i)(pyproject\\.toml|Pipfile(?:\\.lock)?|\\.python-version|requirements.*\\.txt)"),"Forbidden dependency manifest: "+path);
        // This one harness only makes deliberately failing interpreter probes, then
        // enforces an OS execution denial for the entire descendant process tree.
        if(path.equals("tools/verification/without-python.cjs"))return;
        boolean active=active(path);
        if(active)require(!COMMAND.matcher(text).find(),"Active Python/pip command: "+path);
    }
    private static boolean active(String path) { return path.matches(".*\\.(gradle|sh|bat|cmd|ps1|cjs|mjs|js|java|ya?ml)$") || path.matches("(?:.*/)?package(?:-lock)?\\.json") || path.equals("gradlew");
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();Path report=root.resolve("build/reports/python-free/source-check.json");Files.deleteIfExists(report);
        var result=run(root,List.of("git","ls-files","-z","--cached","--others","--exclude-standard"),Duration.ofSeconds(30));require(result.exitCode()==0,"Cannot enumerate repository inputs");
        int checked=0;Set<String> files=new HashSet<>(List.of(result.stdout().split("\u0000")));
        for(String file:files)if(!file.isEmpty()) {
            Path path=root.resolve(file);
            validate(file,Files.isRegularFile(path)&&active(file)?Files.readString(path):"");checked++;
        }
        write(report,Map.of("status","passed","files",checked,"scope","Tracked and non-ignored worktree inputs; historical docs excluded from command scanning. Runtime proof is separate."));
        System.out.println("PYTHON_FREE_SOURCE_PASS "+checked+" inputs");
    }
}
