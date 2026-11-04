package build;

import build.interlis.JenesisBridge;
import build.interlis.JvmBuildInputs;
import build.interlis.CompilerContractRun;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.util.List;

/** Project entry point. Uses the current checkout, Java25 and the pinned engine. */
public final class InterlisJvmBuild {
    public record BuildRequest(String profile, String module, String tests, Path root, java.util.Map<String,String> options) {
        public BuildRequest(String profile,String module,String tests,Path root){this(profile,module,tests,root,java.util.Map.of());}
        public BuildRequest { options=java.util.Map.copyOf(options); }
    }

    public static void main(String[] args) throws Exception {
        BuildRequest request = parse(args);
        JvmBuildInputs.requireProject(request.root());
        Path target=request.root().resolve("target/jenesis-jvm");Files.createDirectories(target);
        try(var channel=FileChannel.open(target.resolve("project.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lock=channel.tryLock()) {
            if(lock==null)throw new IllegalStateException("Another project build is active");
            if(request.profile().equals("contracts")) {
                if(request.module()!=null||request.tests()!=null)throw new IllegalArgumentException("contracts uses the full current binding selection");
                CompilerContractRun.run(request.root());
            } else if(request.profile().equals("parity")) {
                var existing=JenesisBridge.readExistingOutcome(request.root());
                CompilerContractRun.host(request.root(),JenesisBridge.runtimeClasspath("interlis-verification",existing),"guru.interlis.verification.JvmBuildParity",
                        List.of(request.options().get("--baseline"),target.resolve("reports/parity.json").toString()),target.resolve("reports/parity-process"));
                System.out.println("Parity completed: "+target.resolve("reports/parity.json"));
            } else if(request.profile().equals("editor-check")) {
                build.interlis.EditorCompatibilityRun.run(request);
            } else if(request.profile().equals("sdk-check")) {
                if(request.module()!=null||request.tests()!=null)throw new IllegalArgumentException("sdk-check uses complete runtime closures");
                build.interlis.ExistingJvmDistribution.run(request.root());
            } else if(request.profile().equals("build")) {
                var outcome = JenesisBridge.run(request);
                System.out.println("JVM build completed; scope=" + (request.module() == null ? "all" : request.module())
                        + ", tests=" + (request.tests() == null ? "normal" : request.tests())
                        + ", inventory=" + outcome.inventory());
            } else throw new IllegalArgumentException("Profile not yet available: " + request.profile());
            JvmBuildInputs.requireProject(request.root());
        }
    }

    static BuildRequest parse(String[] args) {
        String profile = "build", module = null, tests = null;
        var options=new java.util.HashMap<String,String>();
        int i = 0;
        if (args.length > 0 && !args[0].startsWith("--")) profile = args[i++];
        if (!List.of("build", "contracts", "sdk-check", "parity", "editor-check").contains(profile))
            throw new IllegalArgumentException("Unknown profile: " + profile);
        while (i < args.length) {
            String option = args[i++];
            if (i == args.length) throw new IllegalArgumentException("Missing value for " + option);
            String value = args[i++];
            switch (option) {
                case "--module" -> {
                    if (module != null || !JvmBuildInputs.MODULES.contains(value))
                        throw new IllegalArgumentException("Unknown or duplicate module: " + value);
                    module = value;
                }
                case "--tests" -> {
                    if (tests != null || value.isBlank() || value.contains("/") || value.contains(","))
                        throw new IllegalArgumentException("Expected one explicit class regex with optional #method");
                    tests = value;
                }
                case "--baseline", "--extension-dir", "--reference-bundle" -> {
                    if(options.put(option,value)!=null||!Path.of(value).isAbsolute()||!Files.exists(Path.of(value)))throw new IllegalArgumentException("Expected existing absolute path for "+option);
                }
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        if (tests != null && module == null) throw new IllegalArgumentException("--tests requires --module");
        var expected=profile.equals("parity")?java.util.Set.of("--baseline"):profile.equals("editor-check")?java.util.Set.of("--extension-dir","--reference-bundle"):java.util.Set.of();
        if(!options.keySet().equals(expected))throw new IllegalArgumentException("Expected options for "+profile+": "+expected);
        if(!profile.equals("build")&&(module!=null||tests!=null))throw new IllegalArgumentException("Module/test restrictions are only available for build");
        return new BuildRequest(profile, module, tests, Path.of("").toAbsolutePath().normalize(),options);
    }
}
