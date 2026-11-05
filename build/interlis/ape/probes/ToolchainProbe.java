import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Separate Java25 capability probe; deliberately imports no INTERLIS classes. */
public class ToolchainProbe {
    sealed interface Value permits Text {}
    record Text(String value) implements Value {}
    public static void main(String[] args) throws Exception {
        Value value = new Text("Grüezi 🌍");
        String text = switch (value) { case Text(var content) -> content; };
        String resource;
        try (var input = ToolchainProbe.class.getResourceAsStream("/probe.txt")) {
            if (input == null) throw new AssertionError("Missing resource");
            resource = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!resource.equals("Ressource ä 🌍\n")) throw new AssertionError("Resource bytes");
        Path output = Path.of(args[0]).resolve("probe-ä 🌍.txt");
        Files.writeString(output, text, StandardCharsets.UTF_8);
        if (!Files.readString(output).equals(text)) throw new AssertionError("File bytes");
        var observed = new AtomicReference<String>();
        Thread child = new Thread(() -> observed.set(text)); child.start(); child.join();
        if (!text.equals(observed.get())) throw new AssertionError("Thread result");
        try { throw new IllegalArgumentException("controlled"); }
        catch (IllegalArgumentException expected) {
            if (!expected.getMessage().equals("controlled")) throw new AssertionError("Exception");
        }
        System.out.println("PROBE_PASS " + text + " resource/file/thread/exception");
        System.out.println("host=" + System.getProperty("os.name") + "/" + System.getProperty("os.arch"));
    }
}
