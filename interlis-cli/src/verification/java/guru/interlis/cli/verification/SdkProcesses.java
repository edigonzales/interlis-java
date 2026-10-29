package guru.interlis.cli.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Exact installed-process profile retained from the SDK checker. */
public final class SdkProcesses {
    private SdkProcesses() { }
    static Result expect(Path root, List<String> arguments, int exit) throws Exception {
        var result=cli(root,arguments);require(result.exitCode()==exit,"Wrong SDK exit: "+result);return result;
    }
    public static void main(String[] args) throws Exception { check(Path.of(args[0]).toAbsolutePath()); }
    static void check(Path root) throws Exception {
        Path output=root.resolve("build/reports/sdk/sdk-process-comparison.json");Files.deleteIfExists(output);
        var artifacts=artifacts(root); List<Result> results=new ArrayList<>();
        var version=expect(root,List.of("version"),0);
        for(String field:List.of("api:","diagnostics:","protocol:","format:","diagram:"))require(version.stdout().contains(field),"Missing SDK version field: "+field);
        results.add(version);var versionJson=expect(root,List.of("version","--json"),0);results.add(versionJson);
        var manifest=JsonParser.parseString(versionJson.stdout()).getAsJsonObject();version(manifest);
        write(root.resolve("build/reports/sdk/version-manifest.json"),manifest);
        var capabilities=expect(root,List.of("capabilities"),0);version(JsonParser.parseString(capabilities.stdout()).getAsJsonObject());results.add(capabilities);
        results.add(expect(root,List.of("check",root.resolve("testdata/models/base24.ili").toString(),root.resolve("testdata/models/survey24.ili").toString()),0));
        var negative=expect(root,List.of("check",root.resolve("testdata/models/broken24.ili").toString()),1);require(negative.stderr().contains("ILI-"),"Missing SDK diagnostic");results.add(negative);
        Path temporary=Files.createTempDirectory("interlis-sdk-");Path model=temporary.resolve("model.ili");
        try {
            Files.writeString(model,"INTERLIS 2.4;\nMODEL M AT \"x\" VERSION \"1\" =\nTOPIC T =\nCLASS C =\nvalue : TEXT*10;\nEND C;\nEND T;\nEND M.\n");
            results.add(expect(root,List.of("format","--check",model.toString()),1));
            var formatted=expect(root,List.of("format","--indent","4","--line-ending","lf",model.toString()),0);results.add(formatted);
            results.add(expect(root,List.of("format","--write","--indent","4","--line-ending","lf",model.toString()),0));
            require(Files.readString(model).contains("    TOPIC T =") && Files.readString(model).equals(formatted.stdout()),"SDK write differs from formatter output");
        } finally {Files.deleteIfExists(model);Files.deleteIfExists(temporary);}
        results.add(expect(root,List.of("check","--no-such-option",root.resolve("testdata/models/base24.ili").toString()),2));
        require(artifacts.equals(artifacts(root)),"SDK artifacts changed during processes");
        List<Map<String,Object>> commands=new ArrayList<>();
        for(var result:results) commands.add(Map.of("arguments",result.command().subList(1,result.command().size()),"exitCode",result.exitCode(),
                "stdoutSha256",hash(result.stdout().getBytes(java.nio.charset.StandardCharsets.UTF_8)),"stdoutLines",result.stdout().lines().count(),"stderrLines",result.stderr().lines().count()));
        write(output,Map.of("schemaVersion","1.0","status","passed","cli",root.relativize(cli(root)).toString(),"commands",commands,"artifacts",artifacts));
        System.out.println("SDK_PROCESS_PASS "+results.size()+" commands");
    }
    static void version(JsonObject manifest) {
        require(s(manifest,"apiVersion").equals("0.2") && s(manifest,"protocol").equals("interlis.compiler/1.0"),"Wrong SDK version/protocol");
    }
}
