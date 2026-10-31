package guru.interlis.cli.verification;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Same explicit product/test/profile source binding as tools/performance/profile.cjs. */
final class ProfileFingerprint {
    private ProfileFingerprint() { }
    static String commit(Path root) throws Exception {
        var result=run(root,List.of("git","rev-parse","HEAD"),Duration.ofSeconds(10));
        require(result.exitCode()==0&&result.stdout().trim().matches("[a-f0-9]{40}"),"Missing source commit");
        return result.stdout().trim();
    }
    static boolean dirty(Path root) throws Exception {
        var result=run(root,List.of("git","status","--porcelain"),Duration.ofSeconds(10));
        require(result.exitCode()==0,"Cannot inspect source state");return !result.stdout().isBlank();
    }
    static String current(Path root) throws Exception {
        var result=run(root,List.of("git","ls-files","--cached","--others","--exclude-standard","-z"),Duration.ofSeconds(10));
        require(result.exitCode()==0,"Cannot enumerate verification sources");
        var files=new TreeSet<String>();for(var file:result.stdout().split("\\x00"))if(included(file))files.add(file);
        require(files.size()>500,"Incomplete verification source binding");var text=new StringBuilder();
        for(var file:files)text.append(file).append('\0').append(hash(root.resolve(file))).append('\n');
        return hash(text.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static boolean included(String file) {
        return file.startsWith("interlis-")||file.startsWith("tools/")||file.startsWith("gradle/")
                ||file.startsWith(".github/workflows/")||file.startsWith("docs/spec/")
                ||file.startsWith("docs/evidence/p3/full-corpus/")
                ||Set.of("build.gradle","settings.gradle","gradle.properties","gradlew","gradlew.bat").contains(file);
    }
}
