package guru.interlis.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import static guru.interlis.verification.HostChecks.*;
import static guru.interlis.verification.TestReportInventory.*;

/** Internal logical JVM parity gate. Input receipts bind original reports and actual artifact bytes. */
public final class JvmBuildParity {
    public record Dependency(String id,String classifier,String type,String sha256,String path,String origin) {}
    public record Artifact(String path,String sha256,boolean sources) {}
    public record ModuleInventory(String module,Map<String,List<Dependency>> dependencies,List<Artifact> artifacts,
            String reports,ExecutionReceipt receipt) {}
    public record BuildInventory(String builder,String commit,String tree,String jdk,boolean dirty,List<ModuleInventory> modules) {}
    public record ParityResult(String status,int originalTests,int addedTests,List<String> variations) {}
    private final List<String> variations=new ArrayList<>();
    private static final Set<String> MODULES=Set.of("interlis-model","interlis-compiler","interlis-workspace","interlis-repository","interlis-projection","interlis-documentation","interlis-wasm-bridge","interlis-compiler-cli","interlis-lsp","interlis-verification");
    private static final Set<String> ADAPTERS=Set.of("PythonFreeCheck","CompilerCliProcesses","HostChecks","ProfileFingerprint","SdkArchiveCheck","SdkProcesses","VerificationLayout");
    private static final Set<String> NEW_ADAPTERS=Set.of("ContractSelectionWriter","ReportProjectionMain","TestReportInventory","JvmBuildParity");
    private static final Set<String> SCOPES=Set.of("mainCompile","mainRuntime","testCompile","testRuntime");
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected parity input JSON and output JSON");
        var input=read(Path.of(args[0]));var parser=new GsonBuilder().registerTypeAdapter(Instant.class,(JsonDeserializer<Instant>)(j,t,c)->Instant.parse(j.getAsString())).create();
        var checker=new JvmBuildParity();
        try {
            var result=checker.compare(parser.fromJson(input.get("baseline"),BuildInventory.class),parser.fromJson(input.get("candidate"),BuildInventory.class));write(Path.of(args[1]),result);System.out.println("JVM_PARITY_PASS original="+result.originalTests()+" added="+result.addedTests());
        }catch(Exception failure){write(Path.of(args[1]),Map.of("status","failed","cause",failure.toString()));throw failure;}
    }
    public ParityResult compare(BuildInventory baseline,BuildInventory candidate)throws Exception {
        requireScopeAndProvenance(baseline);requireScopeAndProvenance(candidate);require(baseline.builder().equals("gradle")&&candidate.builder().equals("jenesis"),"Wrong parity direction");
        require(baseline.jdk().equals(candidate.jdk()),"Changed JDK profile");int originals=0,added=0;
        Map<String,ModuleInventory> modules=new HashMap<>();candidate.modules().forEach(m->modules.put(m.module(),m));
        for(var before:baseline.modules()) {
            var after=modules.get(before.module());compareDependencies(before,after);compareArtifacts(before,after);
            var reader=new TestReportInventory();var old=reader.readGradle(Path.of(before.reports()),before.receipt());var current=reader.readJenesis(Path.of(after.reports()),after.receipt());
            compareExecutedTests(old,current);originals+=old.cases().size();added+=current.cases().size()-old.cases().size();
        }
        return new ParityResult("passed",originals,added,List.copyOf(variations));
    }
    public void requireScopeAndProvenance(BuildInventory inventory)throws Exception {
        require(Set.of("gradle","jenesis").contains(inventory.builder())&&inventory.commit().matches("[a-f0-9]{40}")&&inventory.tree().matches("[a-f0-9]{40}")&&!inventory.jdk().isBlank(),"Missing build provenance");
        Map<String,ModuleInventory> own=new HashMap<>();inventory.modules().forEach(m->own.put(m.module(),m));
        Set<String> modules=new HashSet<>();for(var module:inventory.modules()) {
            require(modules.add(module.module())&&module.receipt().module().equals(module.module())&&module.receipt().builder().equals(inventory.builder()),"Duplicate/foreign module receipt");
            require(module.dependencies().keySet().equals(SCOPES),"Incomplete scope inventory");
            require(module.artifacts().size()==2&&module.artifacts().stream().filter(Artifact::sources).count()==1,"Missing/duplicate main/source artifact");
            for(var artifact:module.artifacts())require(hash(Path.of(artifact.path())).equals(artifact.sha256()),"Changed parity artifact: "+artifact.path());
            for(var scope:module.dependencies().values()){Set<String> ids=new HashSet<>();for(var dependency:scope){require(ids.add(dependency.id())&&(dependency.type().equals("jar")||(inventory.builder().equals("gradle")&&dependency.id().startsWith("guru.interlis:")&&dependency.type().equals("java-classes-directory")&&Files.isDirectory(Path.of(dependency.path()))))&&!dependency.origin().isBlank(),"Duplicate/invalid dependency");if(dependency.sha256()!=null)require(hash(Path.of(dependency.path())).equals(dependency.sha256()),"Changed dependency bytes: "+dependency.id());
                if(dependency.id().startsWith("guru.interlis:"))verifyOwnDependency(dependency,own);}}
        }require(modules.equals(MODULES),"Parity scope differs from ten JVM modules");
    }
    private static void verifyOwnDependency(Dependency dependency,Map<String,ModuleInventory> modules)throws Exception {
        String[] id=dependency.id().split(":");require(id.length==3&&id[2].equals("0.2.0-SNAPSHOT")&&modules.containsKey(id[1]),"Foreign own coordinates: "+dependency.id());
        var artifact=modules.get(id[1]).artifacts().stream().filter(a->!a.sources()).findFirst().orElseThrow();Path file=Path.of(dependency.path());
        if(dependency.type().equals("jar")){require(hash(file).equals(artifact.sha256()),"Foreign/stale own dependency bytes: "+dependency.id());return;}
        var expected=new TreeMap<String,String>();for(var entry:entries(Path.of(artifact.path())).entrySet())if(entry.getKey().endsWith(".class"))expected.put(entry.getKey(),hash(entry.getValue()));
        var actual=new TreeMap<String,String>();try(var walk=Files.walk(file)){for(Path path:walk.filter(p->p.toString().endsWith(".class")).toList())actual.put(file.relativize(path).toString().replace('\\','/'),hash(path));}
        require(actual.equals(expected),"Foreign/stale own class directory: "+dependency.id());
    }
    public void compareDependencies(ModuleInventory before,ModuleInventory after) {
        require(before.module().equals(after.module()),"Foreign module");
        for(String scope:SCOPES) {
            var old=byId(before.dependencies().get(scope));var current=byId(after.dependencies().get(scope));
            for(var entry:old.entrySet()){var actual=current.get(entry.getKey());require(actual!=null,"Lost dependency: "+before.module()+"/"+scope+"/"+entry.getKey());require(Objects.equals(entry.getValue().classifier(),actual.classifier())&&(entry.getValue().type().equals(actual.type())||(entry.getValue().id().startsWith("guru.interlis:")&&entry.getValue().type().equals("java-classes-directory")&&actual.type().equals("jar")))&&(entry.getValue().sha256()==null||entry.getValue().sha256().equals(actual.sha256())),"Changed dependency: "+entry.getKey());}
            for(String id:current.keySet())if(!old.containsKey(id)) {
                boolean compiler=(scope.equals("mainCompile")||scope.equals("testCompile"))&&id.equals("org.antlr:antlr4-runtime:4.13.2")&&before.dependencies().get("mainRuntime").stream().anyMatch(d->d.id().equals(id)&&Objects.equals(d.sha256(),current.get(id).sha256()));
                boolean junitCompile=scope.equals("testCompile")&&id.matches("org.junit.jupiter:junit-jupiter-engine:5.11.4|org.junit.platform:junit-platform-(engine|launcher):1.11.4");
                boolean tooling=(scope.equals("testCompile")||scope.equals("testRuntime"))&&id.matches("org.junit.platform:junit-platform-(console|reporting):1.11.4|org.apiguardian:apiguardian-api:1.1.2");
                require(compiler||junitCompile||tooling,"Unallowed dependency addition: "+before.module()+"/"+scope+"/"+id);variations.add(before.module()+"/"+scope+"/"+id+": "+(compiler?"declared dependency compile exposure":junitCompile?"same pinned JUnit runtime visible at test compile":"isolated engine test tooling"));
            }
        }
    }
    private static Map<String,Dependency> byId(List<Dependency> list){var map=new HashMap<String,Dependency>();for(var dependency:list)require(map.put(dependency.id(),dependency)==null,"Duplicate dependency");return map;}
    public void compareExecutedTests(TestInventory before,TestInventory after) {
        var reader=new TestReportInventory();reader.requireSuccessful(before);reader.requireSuccessful(after);
        var old=new HashMap<TestIdentity,TestCase>();before.cases().forEach(c->old.put(c.identity(),c));var current=new HashMap<TestIdentity,TestCase>();after.cases().forEach(c->current.put(c.identity(),c));
        for(var id:old.keySet())require(current.containsKey(id)&&old.get(id).outcome().equals(current.get(id).outcome()),"Lost/changed original invocation: "+id);
        for(var id:current.keySet())if(!old.containsKey(id))require(id.module().equals("interlis-verification")&&Set.of("guru.interlis.verification.TestReportInventoryTest","guru.interlis.verification.VerificationLayoutTest","guru.interlis.verification.JvmBuildParityTest").contains(id.className()),"Unexpected new product test: "+id);
    }
    public void compareArtifacts(ModuleInventory before,ModuleInventory after)throws Exception {
        for(var oldArtifact:before.artifacts()) {
            var current=after.artifacts().stream().filter(a->a.sources()==oldArtifact.sources()).findFirst().orElseThrow();
            Map<String,byte[]> old=entries(Path.of(oldArtifact.path())),now=entries(Path.of(current.path()));String module=before.module();
            normalizeCompilerSources(module,oldArtifact.sources(),old,now);
            for(String name:old.keySet()) {
                require(now.containsKey(name),"Lost archive entry: "+module+"/"+name);
                if(Arrays.equals(old.get(name),now.get(name)))continue;
                if(name.equals("META-INF/MANIFEST.MF")){compareManifest(old.get(name),now.get(name),module,oldArtifact.sources());continue;}
                String adapter=adapterName(name);require(module.equals("interlis-verification")&&ADAPTERS.contains(adapter),"Changed archive entry: "+module+"/"+name);variations.add(module+"/"+name+": explicit layout/input adapter source change");
            }
            Set<String> metadata=Set.of("META-INF/maven/guru.interlis/"+module+"/pom.xml","META-INF/maven/guru.interlis/"+module+"/pom.properties","META-INF/sbom/guru.interlis/"+module+".cdx.json");
            for(String name:now.keySet())if(!old.containsKey(name)) {
                boolean adapter=module.equals("interlis-verification")&&NEW_ADAPTERS.contains(adapterName(name));
                boolean unicode=oldArtifact.sources()&&module.equals("interlis-compiler")&&name.equals("META-INF/licenses/UNICODE-LICENSE.txt");
                require((!oldArtifact.sources()&&metadata.contains(name))||adapter||unicode,"Unallowed archive entry: "+module+"/"+name);variations.add(module+"/"+name+": "+(adapter?"new internal verification adapter":unicode?"existing compiler license also in source archive":"native builder coordinates/SBOM"));
            }
        }
    }
    private static String adapterName(String name){if(!name.startsWith("guru/interlis/verification/")||name.substring(27).contains("/"))return "";String base=name.substring(name.lastIndexOf('/')+1);return base.split("[.$]",2)[0];}
    private void normalizeCompilerSources(String module,boolean sources,Map<String,byte[]> old,Map<String,byte[]> now)throws Exception {
        if(!module.equals("interlis-compiler")||!sources)return;
        for(String type:List.of("InterlisLexer","InterlisParser","InterlisParserBaseVisitor","InterlisParserVisitor")) {
            byte[] value=old.remove(type+".java");require(value!=null,"Missing original generated Java");String file="guru/interlis/compiler/internal/antlr/"+type+".java";
            // Only ANTLR's first generated-from comment carries builder-specific source paths.
            require(normalizeGeneratorHeader(value).equals(normalizeGeneratorHeader(now.get(file))),"Changed generated Java: "+type);old.put(file,now.get(file));variations.add(module+"/"+file+": generated path/header only");
        }
        for(String name:List.of("InterlisLexer.interp","InterlisLexer.tokens","InterlisParser.interp","InterlisParser.tokens")){require(old.remove(name)!=null,"Missing original ANTLR auxiliary metadata");variations.add(module+"/"+name+": Gradle includes generator auxiliary files; no maintained source removed");}
    }
    private static String normalizeGeneratorHeader(byte[] bytes){require(bytes!=null,"Missing generated source");return new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replaceFirst("^// Generated from [^\\r\\n]+ by ANTLR 4\\.13\\.2","// Generated by ANTLR 4.13.2");}
    private void compareManifest(byte[] before,byte[] after,String module,boolean sources)throws Exception {
        var old=new Manifest(new java.io.ByteArrayInputStream(before));var now=new Manifest(new java.io.ByteArrayInputStream(after));
        require(old.getEntries().equals(now.getEntries()),"Changed manifest entry sections");
        var expected=new java.util.jar.Attributes(old.getMainAttributes());
        Map<String,String> additions=new TreeMap<>(Map.of("Created-By","Jenesis","Implementation-Title",module,"Implementation-Version","0.2.0-SNAPSHOT"));
        if(!sources){additions.put("Sbom-Format","CycloneDX");additions.put("Sbom-Location","META-INF/sbom/guru.interlis/"+module+".cdx.json");}
        for(var entry:additions.entrySet()){String value=now.getMainAttributes().getValue(entry.getKey());require(entry.getValue().equals(value),"Unexpected builder manifest value: "+entry.getKey()+"="+value);expected.putValue(entry.getKey(),value);}
        require(expected.equals(now.getMainAttributes()),"Unexpected manifest attributes: "+module);variations.add(module+": exact native manifest additions "+additions.keySet());
    }
    private static Map<String,byte[]> entries(Path path)throws Exception {var entries=new TreeMap<String,byte[]>();try(var zip=new ZipFile(path.toFile())){for(var entry:zip.stream().filter(e->!e.isDirectory()).toList()){require(entries.put(entry.getName(),zip.getInputStream(entry).readAllBytes())==null,"Duplicate ZIP entry");}}return entries;}
}
