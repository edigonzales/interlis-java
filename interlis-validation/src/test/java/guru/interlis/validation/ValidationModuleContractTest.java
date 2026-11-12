package guru.interlis.validation;

import guru.interlis.model.CompiledSchema;
import guru.interlis.transfer.TransferPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ValidationModuleContractTest {
    @TempDir Path work;

    @Test
    void externalNamedConsumerLoadsValidatorWithOnlyModelAndTransfer() throws Exception {
        String modulePath = String.join(File.pathSeparator, List.of(
                location(CompiledSchema.class), location(TransferPlan.class), location(ValidationPlan.class)));
        Path descriptor = Files.writeString(work.resolve("module-info.java"),
                "module consumer { requires guru.interlis.validation; }");
        Path source = Files.writeString(work.resolve("Main.java"), """
                package consumer;
                import guru.interlis.model.CompiledSchema;
                import guru.interlis.transfer.TransferPlan;
                import guru.interlis.validation.ValidationPlan;
                import guru.interlis.validation.ValidationPlanner;

                public class Main {
                    public static void main(String[] args) throws Exception {
                        if (!Main.class.getModule().getName().equals("consumer")
                                || !ValidationPlan.class.getModule().getName().equals("guru.interlis.validation")) {
                            throw new AssertionError("Unexpected consumer or validation module");
                        }
                        ValidationPlanner.class.getMethod("plan", CompiledSchema.class, TransferPlan.class);
                        for (String module : new String[]{"guru.interlis.compiler", "guru.interlis.xtf23", "guru.interlis.xtf24"}) {
                            if (ModuleLayer.boot().findModule(module).isPresent()) {
                                throw new AssertionError("Unexpected product dependency: " + module);
                            }
                        }
                    }
                }
                """);
        Path classes = work.resolve("classes");
        run("javac", "--module-path", modulePath, "-d", classes.toString(), descriptor.toString(), source.toString());
        run("java", "--module-path", modulePath + File.pathSeparator + classes, "-m", "consumer/consumer.Main");
    }

    private static String location(Class<?> type) throws Exception {
        var module = type.getModule();
        return Path.of(module.getLayer().configuration().findModule(module.getName()).orElseThrow()
                .reference().location().orElseThrow()).toString();
    }

    private void run(String executable, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        command.add(Path.of(System.getProperty("java.home"), "bin", executable + suffix).toString());
        command.addAll(List.of(arguments));
        Path output = work.resolve(executable + ".log");
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), () -> executable + " timed out");
            assertEquals(0, process.exitValue(), () -> {
                try {
                    return Files.readString(output);
                } catch (java.io.IOException failure) {
                    return failure.toString();
                }
            });
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
        }
    }
}
