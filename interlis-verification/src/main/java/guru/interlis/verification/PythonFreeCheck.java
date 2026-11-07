package guru.interlis.verification;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static guru.interlis.verification.HostChecks.*;

/** Repository canary; runtime absence is independently proved by the confined phase run. */
public final class PythonFreeCheck {
    private static final String COMMAND_PATH = "(?:[^\\s\"'`;|&]+/)?";
    private static final String EXECUTABLE = COMMAND_PATH+"(?:py(?:thon|py)[0-9.]*|pip[0-9.]*|uvx?)(?:\\.exe)?";
    private static final String ENV_PREFIX = "(?:"+COMMAND_PATH+"env\\s+(?:(?:-(?:u|C)\\s+[^\\s]+|-[^\\s]+|[A-Za-z_][A-Za-z_0-9]*=[^\\s]+)\\s+)*)?";
    private static final String XARGS_PREFIX = "(?:"+COMMAND_PATH+"xargs\\s+(?:-[^\\s]+\\s+(?:[0-9]+\\s+|\\{\\}\\s+)?)*)?";
    // Shell/YAML command starts, including absolute paths and env options/assignments.
    private static final Pattern SHELL_COMMAND = Pattern.compile("(?im)(?:^|[;|&`]|\\$\\()\\s*(?:(?:run|command)\\s*:\\s*)?(?:(?:exec|command)\\s+)?"+XARGS_PREFIX+ENV_PREFIX+EXECUTABLE+"(?=\\s|$)");
    private static final Pattern COMMAND=Pattern.compile("(?im)(?:^#![^\\r\\n]*\\b|[\"'`](?:(?:/|\\./|\\.\\./|[A-Za-z]:[\\\\/])[^\"'`\\r\\n]*)?)(?:py(?:thon|py)[0-9.]*|pip[0-9.]*|uvx?)(?=[\"'`\\s]|$)|^\\s*(?:(?:run|command)\\s*:\\s*|exec\\s+)?(?:py(?:thon|py)[0-9.]*|pip[0-9.]*|uvx?)\\s");
    private PythonFreeCheck() { }
    static void validate(String path,String text) {
        // The standalone reference harness is outside the JVM build and delivery.
        if (path.matches("tools/iox-reference-harness/[^/]+\\.py")) return;
        require(!path.toLowerCase(Locale.ROOT).matches(".*\\.py[wc]?$"),"Forbidden source remains: "+path);
        if(path.startsWith("docs/"))return; // Documentation can quote example commands.
        require(!Path.of(path).getFileName().toString().matches("(?i)(pyproject\\.toml|Pipfile(?:\\.lock)?|\\.python-version|requirements.*\\.txt)"),"Forbidden dependency manifest: "+path);
        // This one harness only makes deliberately failing interpreter probes, then
        // enforces an OS execution denial for the entire descendant process tree.
        if(path.equals("tools/verification/without-python.cjs"))return;
        boolean active=active(path);
        if(active)require(!COMMAND.matcher(text).find() && !SHELL_COMMAND.matcher(text).find(),"Active Python/pip command: "+path);
    }
    private static boolean active(String path) { return path.matches(".*\\.(gradle|sh|bat|cmd|ps1|cjs|mjs|js|java|ya?ml)$") || path.matches("(?:.*/)?package(?:-lock)?\\.json") || path.equals("gradlew");
    }
    /** Vendor capabilities are inventoried separately only after every original byte is verified. */
    static Set<String> pinnedVendor(Path root)throws Exception {
        Path pin=root.resolve("build/JENESIS-UPSTREAM.json");if(!Files.exists(pin))return Set.of();
        var document=read(pin);require(s(document,"version").equals("0.18.1")&&s(document,"commit").equals("177014053cce311924f6724d4e4a7fe5689ed9b6"),"Unexpected vendor pin");
        var files=document.getAsJsonObject("files");Set<String> expected=new HashSet<>();
        for(var entry:files.entrySet()) {
            String relative=entry.getKey();Path path=root.resolve("build/jenesis").resolve(relative).normalize();
            require(!Path.of(relative).isAbsolute()&&!relative.contains("..")&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&hash(path).equals(entry.getValue().getAsString()),"Changed/unsafe pinned vendor source: "+relative);
            expected.add("build/jenesis/"+relative);
        }
        Set<String> actual=new HashSet<>();try(var walk=Files.walk(root.resolve("build/jenesis"))){for(Path path:walk.filter(p->!Files.isDirectory(p)).toList())actual.add(root.relativize(path).toString().replace('\\','/'));}
        require(actual.equals(expected),"Added/missing vendor input");return Set.copyOf(expected);
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();Path report=VerificationLayout.forInvocation(root).rootReport("python-free/source-check.json");Files.deleteIfExists(report);
        var result=run(root,List.of("git","ls-files","-z","--cached","--others","--exclude-standard"),Duration.ofSeconds(30));require(result.exitCode()==0,"Cannot enumerate repository inputs");
        Set<String> vendor=pinnedVendor(root);
        int checked=0;Set<String> files=new HashSet<>(List.of(result.stdout().split("\u0000")));
        for(String file:files)if(!file.isEmpty()) {
            if(vendor.contains(file))continue;
            Path path=root.resolve(file);
            validate(file,Files.isRegularFile(path)&&active(file)?Files.readString(path):"");checked++;
        }
        write(report,Map.of("status","passed","files",checked,"pinnedVendorFiles",vendor.size(),"vendorPinSha256",vendor.isEmpty()?"none":hash(root.resolve("build/JENESIS-UPSTREAM.json")),"scope","Own tracked/non-ignored inputs scanned; byte-verified unchanged upstream engine capabilities inventoried separately; standalone IOX reference harness Python sources permitted; historical docs excluded. Runtime proof is separate."));
        System.out.println("PYTHON_FREE_SOURCE_PASS "+checked+" inputs");
    }
}
