package guru.interlis.cli.verification;

import com.google.gson.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import static guru.interlis.cli.verification.HostChecks.*;

/** The small historical reference profiles, separate from the external conformance runner. */
public final class ReferenceProcesses {
    static final String ARCHIVE="0d6fc5dc739a490ef6b027072d58281bb0731c932f03d95662f83d20dc07c8ee";
    static final String JAR="37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e";
    private ReferenceProcesses() { }
    static Path prepare(Path root) throws Exception {
        Path cache=cache(root,"ILI2C_CACHE","");Path archive=cache.resolve("ili2c-5.6.9.zip");
        download(URI.create("https://downloads.interlis.ch/ili2c/ili2c-5.6.9.zip"),archive,ARCHIVE);
        Path jar=root.resolve("build/reference/ili2c.jar");Files.createDirectories(jar.getParent());
        try(var zip=new ZipFile(archive.toFile());var in=zip.getInputStream(Objects.requireNonNull(zip.getEntry("ili2c.jar"),"Missing ili2c.jar"))) {Files.copy(in,jar,StandardCopyOption.REPLACE_EXISTING);}
        pin(jar,JAR);return jar;
    }
    static Result reference(Path root,Path jar,List<String> args) throws Exception {
        var command=new ArrayList<>(List.of(java().toString(),"-jar",jar.toString()));command.addAll(args);return run(root,command,Duration.ofSeconds(45));
    }
    static boolean processError(Result result) {
        return !Set.of(0,1).contains(result.exitCode()) || Pattern.compile("java\\.[\\w.]+(?:Exception|Error)|(?m)^\\s+at ch\\.interlis\\.|Cannot invoke .* because .* is null").matcher(result.stderr()).find();
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();String mode=args[1];Path jar=prepare(root);
        var version=reference(root,jar,List.of("--version"));require(version.exitCode()==0 && (version.stdout()+version.stderr()).contains("Version 5.6.9-"),"Wrong reference version");
        if(mode.equals("profile")) profile(root,jar);
        else if(mode.equals("originals")) originals(root,jar);
        else shared(root,jar,args[2],mode);
        pin(jar,JAR);
    }
    static void profile(Path root,Path jar) throws Exception {
        Path output=root.resolve("build/reports/reference/reference-results.json");Files.deleteIfExists(output);
        var original=read(root.resolve("docs/evidence/reference/reference-results.json"));List<Map<String,Object>> results=new ArrayList<>();
        Properties fixturePins=new Properties();try(var reader=Files.newBufferedReader(root.resolve("gradle/reference-fixtures.properties"))){fixturePins.load(reader);}
        var cases=rows(original,"results");require(cases.size()==5 && cases.stream().map(r->s(r,"id")).collect(java.util.stream.Collectors.toSet()).equals(Set.of("base-survey-24","legacy-23","broken-24","trailing-24","missing-import")),"Changed reference profile");
        for(var entry:cases) {
            var files=strings(entry.get("files"));for(String file:files)pin(root.resolve(file),Objects.requireNonNull(fixturePins.getProperty(file),"Missing reference fixture pin"));
            var command=new ArrayList<>(strings(entry.get("extraArgs")));command.addAll(files);var result=reference(root,jar,command);
            require(result.exitCode()==entry.get("expectedExit").getAsInt() && !processError(result),"Reference profile failed: "+s(entry,"id")+": "+result);
            Map<String,Object> row=new LinkedHashMap<>();for(String key:List.of("id","files","extraArgs","expectedExit"))row.put(key,entry.get(key));
            row.put("fixtureSha256",files.stream().collect(java.util.stream.Collectors.toMap(f->f,fixturePins::getProperty)));row.put("actualExit",result.exitCode());row.put("status","PASS");row.put("stdout",result.stdout());row.put("stderr",result.stderr());row.put("command",result.command());results.add(row);
        }
        write(output,Map.of("tool","ili2c","version","5.6.9","sourceSha256",ARCHIVE,"referenceJarSha256",JAR,"results",results));
        System.out.println("REFERENCE_PASS "+results.size()+" cases");
    }
    static void originals(Path root,Path jar) throws Exception {
        Path output=root.resolve("build/reports/stabilization/original-view-reference-comparison.json");Files.deleteIfExists(output);
        var artifacts=artifacts(root);var properties=new Properties();Path fixture=root.resolve("testdata/conformance/b1-b3-acceptance");
        try(var reader=Files.newBufferedReader(fixture.resolve("sources.properties"))) {properties.load(reader);}
        require(properties.size()==19,"Changed original-view selection");List<Map<String,Object>> cases=new ArrayList<>();
        for(String id:new TreeSet<>(properties.stringPropertyNames())) {
            String[] fields=properties.getProperty(id).split("\\|");Path source=fixture.resolve(fields[0]).normalize();require(source.getParent().equals(fixture),"Unsafe original fixture");pin(source,fields[1]);
            var current=cli(root,List.of("check","--json",source.toString()));checkPayload(current,false);
            var ref=reference(root,jar,List.of("-o0",source.toString()));require(ref.exitCode()==0 && !processError(ref),"Reference rejected original view: "+id);
            cases.add(Map.of("testId",id,"sourceSha256",fields[1],"upstreamPath",fields[2],"current",current,"ili2c",ref));pin(source,fields[1]);
        }
        require(artifacts.equals(artifacts(root)),"Original comparison artifacts changed");write(output,Map.of("total",19,"currentExpected",19,"referenceExpected",19,"processErrors",0,"referenceJarSha256",JAR,"ownArtifacts",artifacts,"cases",cases));
        System.out.println("ORIGINAL_VIEWS_PASS 19/19 both processes");
    }
    static void shared(Path root,Path jar,String node,String kind) throws Exception {
        require(Set.of("views","units").contains(kind),"Unknown comparison profile");Path output=root.resolve("build/reports/stabilization/"+kind+"-reference-comparison.json");Files.deleteIfExists(output);
        var export=run(root,List.of(node,root.resolve("tools/reference/export-comparison-cases.cjs").toString(),kind),Duration.ofSeconds(20));require(export.exitCode()==0 && export.stderr().isEmpty(),"Shared case export failed");
        List<JsonObject> input=JsonParser.parseString(export.stdout()).getAsJsonArray().asList().stream().map(JsonElement::getAsJsonObject).toList();
        require(input.size()==(kind.equals("views")?134:117),"Changed shared comparison selection");
        Path directory=root.resolve("build/reports/stabilization/"+kind+"-cases");Files.createDirectories(directory);List<Map<String,Object>> results=new ArrayList<>();int number=0,agreements=0,errors=0;
        List<JsonObject> cases=new ArrayList<>(input);
        if(kind.equals("units"))try(var paths=Files.list(root.resolve("testdata/conformance/k6-units"))) {
            for(Path path:paths.filter(p->p.toString().endsWith(".ili")).sorted().toList()) {
                JsonObject row=new JsonObject();row.addProperty("expected","rejected");JsonArray sources=new JsonArray();JsonObject source=new JsonObject();source.addProperty("sourceId",path.getFileName().toString());source.addProperty("text",Files.readString(path));sources.add(source);row.add("sources",sources);cases.add(row);
            }
        }
        for(var row:cases) {
            Path folder=directory.resolve(String.format("%03d",number++));Files.createDirectories(folder);List<String> paths=new ArrayList<>();Map<String,String> hashes=new LinkedHashMap<>();
            for(var source:rows(row,"sources")) {
                Path file=folder.resolve(Path.of(URI.create(s(source,"sourceId")).getPath()).getFileName());require(!paths.contains(file.toString()),"Duplicate shared source filename");Files.writeString(file,s(source,"text"));paths.add(file.toString());hashes.put(file.toString(),hash(file));
            }
            var ownArgs=new ArrayList<>(List.of("check","--json"));ownArgs.addAll(paths);var current=cli(root,ownArgs);checkPayload(current,!s(row,"expected").equals("succeeded"));
            var refArgs=new ArrayList<>(List.of("-o0"));refArgs.addAll(paths);var ref=reference(root,jar,refArgs);boolean error=processError(ref);if(error)errors++;if(!error&&ref.exitCode()==current.exitCode())agreements++;
            for(var entry:hashes.entrySet())pin(Path.of(entry.getKey()),entry.getValue());
            results.add(Map.of("id",number-1,"expected",s(row,"expected"),"sourceHashes",hashes,"current",current,"ili2c",ref,"referenceProcessError",error));
        }
        write(output,Map.of("total",cases.size(),"ownExpected",cases.size(),"referenceAgreement",agreements,"referenceProcessErrors",errors,"referenceJarSha256",JAR,"cases",results,
                "scope","Own expectations verified; reference observations are not normative approvals."));
        require(!kind.equals("units") || errors==0,"Reference process errors in unit profile");
        System.out.println(kind.toUpperCase()+"_REFERENCE "+cases.size()+" own expectations; "+agreements+" agreements; "+errors+" reference process errors");
    }
}
