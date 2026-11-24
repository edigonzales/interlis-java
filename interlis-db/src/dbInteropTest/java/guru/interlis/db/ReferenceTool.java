package guru.interlis.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

import static guru.interlis.db.DbTestSupport.logText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReferenceTool {
    private final Path work;
    private final Path jar;
    private final Path home;
    private final String timeZone;
    private int runs;

    ReferenceTool(Path work, boolean postgis) throws IOException {
        this(work, postgis, null);
    }

    ReferenceTool(Path work, boolean postgis, String timeZone) throws IOException {
        this.work = work;
        this.timeZone = timeZone;
        String version = System.getProperty("ili2db.reference.version");
        String directory = System.getProperty("ili2db.reference.dir");
        assertNotNull(version, "Gradle must provide ili2db.reference.version");
        assertNotNull(directory, "Gradle must provide ili2db.reference.dir");
        String name = (postgis ? "ili2pg" : "ili2gpkg") + "-" + version;
        Path archive = Path.of(directory).resolve(name + ".zip");
        Path dist = Files.createDirectory(work.resolve("reference"));
        home = Files.createDirectory(work.resolve("reference-home"));
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path file = dist.resolve(entry.getName()).normalize();
                if (!file.startsWith(dist)) throw new IOException("Invalid reference ZIP entry");
                if (entry.isDirectory()) Files.createDirectories(file);
                else {
                    Files.createDirectories(file.getParent());
                    Files.copy(zip, file);
                }
            }
        }
        jar = dist.resolve(name + ".jar");
    }

    void run(TestDatabase db, String modelDir, String... args) throws Exception {
        run(db, modelDir, null, args);
    }

    void expectFailure(TestDatabase db, String modelDir, String error, String... args) throws Exception {
        run(db, modelDir, error, args);
    }

    private void run(TestDatabase db, String modelDir, String expectedError, String[] args) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        var command = new ArrayList<>(List.of(java.toString(), "--enable-native-access=ALL-UNNAMED",
                "-Duser.home=" + home, "-Djava.io.tmpdir=" + work));
        if (timeZone != null) command.add("-Duser.timezone=" + timeZone);
        command.addAll(List.of("-jar", jar.toString(), "--modeldir", modelDir));
        command.addAll(db.referenceOptions());
        command.addAll(List.of(args));
        Path log = work.resolve("reference-" + ++runs + ".log");
        Process process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), () -> "Reference timed out: " + args[0]);
            if (expectedError == null) {
                assertEquals(0, process.exitValue(), () -> logText(log, db.password));
            } else {
                String output = logText(log, db.password);
                assertNotEquals(0, process.exitValue(), output);
                assertTrue(output.contains(expectedError), output);
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }
}
