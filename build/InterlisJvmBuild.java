package build;

import build.interlis.JenesisBridge;
import build.interlis.JvmBuildInputs;
import build.interlis.CompilerContractRun;
import java.nio.file.Path;
import java.nio.file.Files;
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
        Path target=JvmBuildInputs.requireOutputRoot(request.root(),"target/jenesis-jvm");Files.createDirectories(target);
        if(request.profile().equals("wasm")) {
            build.interlis.WasmBuildRun.run(request);
            JvmBuildInputs.requireProject(request.root());
            return;
        }
        if(request.profile().equals("editor-bundle")) {
            build.interlis.EditorBundleRun.execute(request);
            JvmBuildInputs.requireProject(request.root());
            return;
        }
        try(var lease=build.interlis.JvmBuildLock.acquire(request.root())) {
            if(request.profile().equals("wasm-export")) {
                var outcome=JenesisBridge.run(new BuildRequest("artifacts",null,null,request.root()));
                var snapshot=build.interlis.ConsumerSnapshot.create(request.root(),outcome);
                System.out.println("Consumer snapshot: "+snapshot.manifest()+" SHA-256="+snapshot.manifestSha256());
            } else if(request.profile().equals("host")) {
                build.interlis.HostVerificationProfiles.run(request,lease);
            } else if(request.profile().equals("clean")) {
                build.interlis.BuildOutputs.clean(request.root(),request.options().get("--scope"));
            } else if(request.profile().equals("contracts")) {
                if(request.module()!=null||request.tests()!=null)throw new IllegalArgumentException("contracts uses the full current binding selection");
                CompilerContractRun.run(request.root());
            } else if(request.profile().equals("editor-check")) {
                build.interlis.EditorCompatibilityRun.run(request);
            } else if(List.of("gui-run", "gui-runtime", "gui-package", "gui-check").contains(request.profile())) {
                build.interlis.GuiRuntime.run(request);
            } else if(request.profile().equals("runtime-check")) {
                build.interlis.CompilerRuntimeCheck.run(request.root());
            } else if(request.profile().equals("module-check")) {
                build.interlis.ModulePathContract.run(request.root());
            } else if(request.profile().equals("sdk-package")) {
                build.interlis.ExistingJvmDistribution.generate(request.root());
            } else if(request.profile().equals("release-check")) {
                build.interlis.HostVerificationProfiles.release(request.root(),lease);
            } else if(request.profile().equals("sdk-check")) {
                if(request.module()!=null||request.tests()!=null)throw new IllegalArgumentException("sdk-check uses complete runtime closures");
                build.interlis.ExistingJvmDistribution.run(request.root());
            } else if(request.profile().equals("build")||request.profile().equals("artifacts")) {
                var outcome = JenesisBridge.run(request);
                if(request.profile().equals("build")&&request.module()==null)build.interlis.HostVerificationProfiles.verifyPythonFree(request.root(),outcome);
                System.out.println("JVM build completed; scope=" + (request.module() == null ? "all" : request.module())
                        + ", tests=" + (request.profile().equals("artifacts")?"NOT_RUN":request.tests() == null ? "normal" : request.tests())
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
        if (!List.of("build", "artifacts", "wasm-export", "wasm", "host", "clean", "contracts", "sdk-check", "sdk-package", "runtime-check", "module-check", "release-check", "editor-check", "editor-bundle", "gui-run", "gui-runtime", "gui-package", "gui-check").contains(profile))
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
                    if (tests != null || value.isBlank() || value.contains("/"))
                        throw new IllegalArgumentException("Expected one explicit class regex with optional #method");
                    tests = value;
                }
                case "--extension-dir", "--reference-bundle", "--inputs" -> {
                    if(options.put(option,value)!=null||!Path.of(value).isAbsolute()||!Files.exists(Path.of(value)))throw new IllegalArgumentException("Expected existing absolute path for "+option);
                }
                case "--application" -> {
                    if(!value.equals("compiler")||options.put(option,value)!=null)throw new IllegalArgumentException("Expected application compiler");
                }
                case "--scope" -> {
                    if(!List.of("jvm","consumer","wasm").contains(value)||options.put(option,value)!=null)
                        throw new IllegalArgumentException("Expected exactly one clean scope: jvm|consumer|wasm");
                }
                case "--profile" -> {
                    var profiles=profile.equals("gui-check")?java.util.Set.of("shell","compile","diagnostics","dependencies","repositories","lifecycle","packaged"):profile.equals("release-check")?java.util.Set.of("compiler"):profile.equals("wasm")?build.interlis.WasmBuildRun.PROFILES:build.interlis.HostVerificationProfiles.PROFILES;
                    if(!profiles.contains(value)||options.put(option,value)!=null)
                        throw new IllegalArgumentException("Unknown or duplicate host profile: "+value);
                }
                case "--inputs-sha256" -> {
                    if(!value.matches("[0-9a-f]{64}")||options.put(option,value)!=null)throw new IllegalArgumentException("Expected producer manifest SHA-256");
                }
                case "--request-id" -> {
                    if(!java.util.UUID.fromString(value).toString().equals(value)||options.put(option,value)!=null)throw new IllegalArgumentException("Expected unique canonical request UUID");
                }
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        if (tests != null && module == null) throw new IllegalArgumentException("--tests requires --module");
        var expected=profile.equals("runtime-check")?java.util.Set.of("--application"):profile.equals("editor-check")?java.util.Set.of("--extension-dir","--reference-bundle"):profile.equals("clean")?java.util.Set.of("--scope"):List.of("host","release-check","gui-check").contains(profile)?java.util.Set.of("--profile"):profile.equals("wasm")?options.containsKey("--inputs-sha256")?java.util.Set.of("--profile","--inputs","--inputs-sha256"):java.util.Set.of("--profile","--inputs"):profile.equals("editor-bundle")&&options.containsKey("--request-id")?java.util.Set.of("--request-id"):java.util.Set.of();
        if(!options.keySet().equals(expected))throw new IllegalArgumentException("Expected options for "+profile+": "+expected);
        if(!List.of("build","artifacts").contains(profile)&&(module!=null||tests!=null))throw new IllegalArgumentException("Module/test restrictions are only available for build/artifacts");
        if(profile.equals("artifacts")&&tests!=null)throw new IllegalArgumentException("artifacts never selects tests");
        return new BuildRequest(profile, module, tests, Path.of("").toAbsolutePath().normalize(),options);
    }
}
