package guru.interlis.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static guru.interlis.verification.HostChecks.*;
import static guru.interlis.verification.TestReportInventory.*;

/** Narrow process boundary for the build adapter; projects real fresh selected producer reports. */
public final class ReportProjectionMain {
    private ReportProjectionMain() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=5)throw new IllegalArgumentException("Expected root, native reports, projected reports, receipt, manifest");
        Path root=Path.of(args[0]),raw=Path.of(args[1]),target=Path.of(args[2]),receiptFile=Path.of(args[3]),manifestFile=Path.of(args[4]);
        var input=new Properties();try(var r=Files.newBufferedReader(receiptFile)){input.load(r);}
        var data=new JsonObject();var reports=new JsonObject();var artifacts=new JsonObject();
        for(String key:input.stringPropertyNames()) {
            if(key.startsWith("report."))reports.addProperty(key.substring(7),input.getProperty(key));
            else if(key.startsWith("artifact."))artifacts.addProperty(key.substring(9),input.getProperty(key));
            else data.addProperty(key,input.getProperty(key));
        }
        data.add("reportHashes",reports);data.add("artifactHashes",artifacts);
        require("interlis-jvm-inputs-v1".equals(input.getProperty("inputPolicy")),"Unknown input snapshot policy");
        var sources=new TreeMap<String,String>();
        for(String key:input.stringPropertyNames())if(key.startsWith("source."))sources.put(key.substring(7),input.getProperty(key));
        require(!sources.isEmpty(),"Missing source/input snapshot");
        StringBuilder sourceBinding=new StringBuilder("interlis-jvm-inputs-v1\n");
        for(var source:sources.entrySet()) {
            Path relative=Path.of(source.getKey()),file=root.resolve(relative).normalize();
            require(!relative.isAbsolute()&&file.startsWith(root)&&file.toRealPath().startsWith(root.toRealPath()),"Foreign source input path");
            pin(file,source.getValue());sourceBinding.append(source.getKey()).append('\0').append(source.getValue()).append('\n');
        }
        require(hash(sourceBinding.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(s(data,"inputFingerprint")),"Incomplete/changed source input snapshot");
        require(!artifacts.isEmpty(),"Missing executed artifact bindings");
        for(var artifact:artifacts.entrySet()) {
            Path file=root.resolve(artifact.getKey()).normalize();
            require(file.startsWith(root.resolve("target/jenesis-jvm/engine"))&&Files.isRegularFile(file)
                    &&file.toRealPath().startsWith(root.resolve("target/jenesis-jvm/engine").toRealPath()),"Foreign executed artifact path");
            pin(file,artifact.getValue().getAsString());
        }
        write(receiptFile.resolveSibling("receipt.json"),data);
        String module=s(data,"module"),runId=s(data,"runId");
        require(s(data,"execution").equals("EXECUTED")&&data.get("exitCode").getAsInt()==0,"Producer not freshly successful");
        Map<String,String> hashes=new TreeMap<>();data.getAsJsonObject("reportHashes").entrySet().forEach(e->hashes.put(e.getKey(),e.getValue().getAsString()));
        Map<String,String> properties=Map.of("contract.runId",runId,"interlis.build.inputs",s(data,"inputFingerprint"));
        var receipt=new ExecutionReceipt(module,"jenesis",runId,Instant.parse(s(data,"started")),0,"EXECUTED",hashes,Map.of(),properties);
        var reader=new TestReportInventory();var actual=reader.readJenesis(raw,receipt);reader.requireSuccessful(actual);
        Set<String> expected=new TreeSet<>();
        for(var entry:CompilerContractCatalog.load(root).document().getAsJsonObject("bindings").entrySet()) {
            var binding=entry.getValue().getAsJsonObject();if(!s(binding,"source").startsWith(module+"/"))continue;
            for(String invocation:strings(binding.get("invocations")))require(expected.add(s(binding,"suite")+"\0"+invocation),"Duplicate expected invocation");
        }
        Set<String> observed=new TreeSet<>();for(TestCase testcase:actual.cases())require(observed.add(testcase.identity().className()+"\0"+testcase.displayName()),"Duplicate observed invocation");
        require(!expected.isEmpty()&&observed.equals(expected),"Lost/extra fresh producer invocations");
        var projection=reader.projectPerSuite(actual,target);reader.verifyRawBindings(projection);
        var manifest=new JsonObject();manifest.addProperty("runId",runId);manifest.addProperty("started",receipt.started().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        manifest.add("gitCommit",data.get("gitCommit"));manifest.add("treeSha",data.get("treeSha"));
        var derived=new JsonObject();projection.projectedHashes().forEach((file,hash)->derived.addProperty(file.getFileName().toString(),hash));manifest.add("hashes",derived);
        manifest.addProperty("receiptSha256",hash(receiptFile));manifest.add("rawHashes",data.get("reportHashes"));manifest.add("artifactHashes",data.get("artifactHashes"));
        manifest.addProperty("adapterHash",projection.adapterHash());manifest.addProperty("selectionSha256",hash(root.resolve("testdata/verification/compiler-contracts.json")));
        write(manifestFile,manifest);System.out.println(actual.cases().size()+" real producer invocations projected and bound");
    }
}
