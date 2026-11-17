package guru.interlis.validator.cli;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        System.exit(ValidatorCommand.run(List.of(args), Path.of("").toAbsolutePath(),
                new PrintWriter(System.out, false, StandardCharsets.UTF_8), new PrintWriter(System.err), Thread.currentThread()::isInterrupted));
    }
}
