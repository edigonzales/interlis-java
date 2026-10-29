package guru.interlis.cli.verification;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static guru.interlis.cli.verification.HostChecks.*;

/** Existing subprocess benchmark: one warmup, three measured checks/formats per pinned source. */
public final class JvmBenchmark {
    private JvmBenchmark() { }
    static void stable(Result baseline, Result actual) {
        require(actual.exitCode()==0 && baseline.stdout().equals(actual.stdout()) && baseline.stderr().equals(actual.stderr()),"Failed/unstable benchmark subprocess: "+actual);
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),output=root.resolve("build/reports/performance/performance-jvm.json");Files.deleteIfExists(output);
        var manifest=GeoCorpus.verify(root);var artifacts=artifacts(root);List<Map<String,Object>> models=new ArrayList<>();
        boolean mac=System.getProperty("os.name").startsWith("Mac");
        require(mac || System.getProperty("os.name").equals("Linux"),"RSS benchmark requires macOS or Linux /usr/bin/time");
        for(var entry:rows(manifest,"models")) {
            Path source=GeoCorpus.cacheDirectory(root).resolve(s(entry,"file").replace("/","__"));
            var check=cli(root,List.of("check",source.toString()));var format=cli(root,List.of("format",source.toString()));
            require(check.exitCode()==0 && format.exitCode()==0,"Benchmark warmup failed: "+s(entry,"file")+"\n"+check.stderr()+format.stderr());
            List<Double> checks=new ArrayList<>(),formats=new ArrayList<>();long peak=0;
            for(int i=0;i<3;i++) for(String operation:List.of("check","format")) {
                Path rss=Files.createTempFile("interlis-rss-",".txt");
                try {
                    var command=new ArrayList<>(List.of("/usr/bin/time",mac?"-l":"-v","-o",rss.toString(),cli(root).toString(),operation,source.toString()));
                    var measured=run(root,command,Duration.ofSeconds(60));stable(operation.equals("check")?check:format,measured);
                    (operation.equals("check")?checks:formats).add(measured.durationMs());
                    var matcher=Pattern.compile(mac?"(?m)^\\s*(\\d+)\\s+maximum resident set size":"(?m)^\\s*Maximum resident set size \\(kbytes\\):\\s*(\\d+)").matcher(Files.readString(rss));
                    require(matcher.find(),"Missing RSS measurement");peak=Math.max(peak,Long.parseLong(matcher.group(1))*(mac?1:1024));
                } finally {Files.deleteIfExists(rss);}
            }
            pin(source,s(entry,"sha256"));require(checks.size()==3 && formats.size()==3 && peak>0,"Incomplete benchmark measurements");
            Map<String,Object> row=new LinkedHashMap<>();for(String key:List.of("model","file","language","bytes","sha256"))row.put(key,entry.get(key));
            row.putAll(Map.of("checkMs",checks,"checkMedianMs",median(checks),"formatMs",formats,"formatMedianMs",median(formats),"outputStableAcrossRuns",true,"maxRssBytes",peak));models.add(row);
        }
        require(artifacts.equals(artifacts(root)),"Benchmark artifacts changed");
        write(output,Map.of("schemaVersion","1.0","runtime","jvm","tool",root.relativize(cli(root)).toString(),"iterationsPerModel",3,"warmupPerModel",1,
                "corpus","docs/evidence/p3/geo-corpus.json","corpusIndexSha256",manifest.getAsJsonObject("index").get("sha256"),"models",models,
                "machine",Map.of("platform",System.getProperty("os.name"),"machine",System.getProperty("os.arch"),"cpus",Runtime.getRuntime().availableProcessors(),"java",System.getProperty("java.runtime.version")),
                "notes","Wall-clock subprocess measurements; warmup excluded. RSS in bytes per child via /usr/bin/time. No production claim."));
        System.out.println("JVM_BENCHMARK_PASS "+models.size()+" models");
    }
    static double median(List<Double> samples) {require(samples.size()==3 && samples.stream().allMatch(v->Double.isFinite(v)&&v>0),"Missing benchmark sample");return samples.stream().sorted().toList().get(1);}
}
