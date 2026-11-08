package build.interlis;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit consumer check. Reference staging must already have occurred outside the JVM producer. */
public final class EditorCompatibilityRun {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2 || Arrays.stream(arguments).map(Path::of)
                .anyMatch(path -> !path.isAbsolute() || !Files.isDirectory(path))) {
            throw new IllegalArgumentException("Expected absolute extension and reference bundle directories");
        }
        Path root = Path.of("").toAbsolutePath().normalize();
        Path output = JvmBuildInputs.requireOutputRoot(root, "target/jenesis-jvm/reports");
        Files.createDirectories(output);
        String node=System.getenv("INTERLIS_NODE");if(node==null||!Path.of(node).isAbsolute())throw new IllegalArgumentException("Set INTERLIS_NODE to the documented Node executable");
        var args=List.of(node,root.resolve("tools/verification/editor-check.cjs").toString(),root.toString(),arguments[0],arguments[1],Path.of(System.getProperty("java.home"),"bin/java").toString());
        Files.writeString(output.resolve("editor-command.json"),JsonOutput.write(args));
        var process=new ProcessBuilder(args).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("editor-check.log").toFile()).start();
        try {
            if(!process.waitFor(120,TimeUnit.SECONDS))throw new IllegalStateException("Editor check timed out");
            Files.writeString(output.resolve("editor-check.exit"),Integer.toString(process.exitValue()));
            if(process.exitValue()!=0)throw new IllegalStateException("Editor check failed: "+output.resolve("editor-check.log"));
            System.out.println(Files.readString(output.resolve("editor-check.log")));
        }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
}
