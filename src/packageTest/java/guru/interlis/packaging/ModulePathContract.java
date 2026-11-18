package guru.interlis.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs the public validation example against the built SDK artifacts. */
public final class ModulePathContract {
    @TempDir Path output;

    @Test
    void validationExampleUsesBuiltLibraries() throws Exception {
        var root = Path.of(System.getProperty("repo.root"));
        var path = System.getProperty("sdk.example.modulepath");
        var sources = root.resolve("examples/sdk/validation");
        var named = Files.createDirectory(output.resolve("named"));
        var unnamed = Files.createDirectory(output.resolve("classpath"));
        var source = sources.resolve("ValidationExample.java").toString();
        process(root, "javac", "--release", "25", "--module-path", path, "-d", named.toString(),
                sources.resolve("module-info.java").toString(), source);
        process(root, "java", "--module-path", path + File.pathSeparator + named, "--module",
                "interlis.validation.example/example.validation.ValidationExample");
        process(root, "javac", "--release", "25", "-cp", path, "-d", unnamed.toString(), source);
        process(root, "java", "-cp", path + File.pathSeparator + unnamed,
                "example.validation.ValidationExample", "classpath");
    }

    private static void process(Path root, String executable, String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.addAll(List.of(arguments));
        DistributionChecks.run(root, command).requireExit(0);
    }
}
