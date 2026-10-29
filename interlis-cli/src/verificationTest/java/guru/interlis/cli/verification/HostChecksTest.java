package guru.interlis.cli.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.cli.verification.HostChecks.*;

class HostChecksTest {
    @TempDir Path directory;
    @Test void wrongSdkVersionOrCliPayloadNeverPasses() {
        assertThrows(Exception.class,()->SdkProcesses.version(JsonParser.parseString("{\"apiVersion\":\"wrong\",\"protocol\":\"interlis.compiler/1.0\"}").getAsJsonObject()));
        var ok=new Result(List.of(),0,"{\"status\":\"ok\",\"diagnostics\":[]}","",1);checkPayload(ok,false);
        for(var bad:List.of(new Result(List.of(),3,ok.stdout(),"",1),new Result(List.of(),0,ok.stdout(),"internal error",1),
                new Result(List.of(),1,"{\"status\":\"incomplete\",\"diagnostics\":[]}","",1)))assertThrows(Exception.class,()->checkPayload(bad,false));
    }
    @Test void brokenArtifactAndBenchmarkMeasurementFail() throws Exception {
        Path file=Files.writeString(directory.resolve("artifact.jar"),"wrong bytes");assertThrows(Exception.class,()->pin(file,"0".repeat(64)));
        assertThrows(Exception.class,()->JvmBenchmark.median(List.of()));assertThrows(Exception.class,()->JvmBenchmark.median(List.of(1.0,0.0,2.0)));
        var baseline=new Result(List.of(),0,"original","",1);
        assertThrows(Exception.class,()->JvmBenchmark.stable(baseline,new Result(List.of(),0,"changed","",1)));
        assertThrows(Exception.class,()->JvmBenchmark.stable(baseline,new Result(List.of(),1,"original","",1)));
    }
    @Test void corpusMetadataMustExistBeUnambiguousAndKeepItsVersion() throws Exception {
        String xml="<TRANSFER><IliRepository20.RepositoryIndex.ModelMetadata><Name>M</Name><File>M.ili</File><Version>1</Version><SchemaLanguage>ili2_4</SchemaLanguage></IliRepository20.RepositoryIndex.ModelMetadata></TRANSFER>";
        Path file=Files.writeString(directory.resolve("index.xml"),xml);
        var manifest=JsonParser.parseString("{\"index\":{},\"models\":[{\"model\":\"M\",\"file\":\"M.ili\",\"version\":\"1\",\"language\":\"2.4\"}]}").getAsJsonObject();
        manifest.getAsJsonObject("index").addProperty("sha256",hash(file));GeoCorpus.validateIndex(file,manifest);
        rows(manifest,"models").getFirst().addProperty("version","wrong");assertThrows(Exception.class,()->GeoCorpus.validateIndex(file,manifest));
        rows(manifest,"models").getFirst().addProperty("version","1");Files.writeString(file,xml.replace("<Name>M</Name>","<Name>Other</Name>"));manifest.getAsJsonObject("index").addProperty("sha256",hash(file));assertThrows(Exception.class,()->GeoCorpus.validateIndex(file,manifest));
        Files.writeString(file,xml.replace("</TRANSFER>",xml.substring("<TRANSFER>".length())));manifest.getAsJsonObject("index").addProperty("sha256",hash(file));assertThrows(Exception.class,()->GeoCorpus.validateIndex(file,manifest));
    }
    @Test void realSubprocessTimeoutAndNonzeroExitRemainDistinct() throws Exception {
        var command=List.of(java().toString(),"-cp",System.getProperty("java.class.path"),HostChecksTest.class.getName(),"fail");
        assertEquals(37,run(directory,command,Duration.ofSeconds(10)).exitCode());
        var timeout=List.of(java().toString(),"-cp",System.getProperty("java.class.path"),HostChecksTest.class.getName(),"sleep");
        assertThrows(java.io.IOException.class,()->run(directory,timeout,Duration.ofMillis(100)));
    }
    public static void main(String[] args) throws Exception {if(args[0].equals("sleep"))Thread.sleep(60000);else System.exit(37);}
}
