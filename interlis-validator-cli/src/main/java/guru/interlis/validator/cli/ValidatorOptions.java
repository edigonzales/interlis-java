package guru.interlis.validator.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record ValidatorOptions(Path input, List<String> repositories, List<String> models, Set<String> flags) {
    private static final Set<String> FLAGS = Set.of("--help", "--version", "--offline", "--verbose");
    private static final Set<String> VALUES = Set.of("--repository", "--model");

    static ValidatorOptions parse(List<String> args) {
        var flags = new LinkedHashSet<String>();
        var repositories = new ArrayList<String>();
        var models = new ArrayList<String>();
        Path input = null;
        boolean positional = false;
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (!positional && arg.equals("--")) {
                positional = true;
            } else if (!positional && FLAGS.contains(arg)) {
                if (!flags.add(arg)) throw new IllegalArgumentException("Duplicate option " + arg);
            } else if (!positional && VALUES.contains(arg)) {
                if (++i == args.size() || args.get(i).isBlank() || args.get(i).startsWith("-")) {
                    throw new IllegalArgumentException("Missing value for " + arg);
                }
                (arg.equals("--repository") ? repositories : models).add(args.get(i));
            } else if (!positional && arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option " + arg);
            } else {
                if (input != null) throw new IllegalArgumentException("Expected one primary XTF file");
                if (arg.isBlank()) throw new IllegalArgumentException("Expected a nonempty file path");
                input = Path.of(arg);
            }
        }
        if (flags.contains("--help") || flags.contains("--version")) {
            if (args.size() != 1) throw new IllegalArgumentException("Use --help or --version on its own");
        } else if (input == null) {
            throw new IllegalArgumentException("Expected a primary XTF file");
        }
        return new ValidatorOptions(input, List.copyOf(repositories), List.copyOf(models), Set.copyOf(flags));
    }

    boolean has(String option) {
        return flags.contains(option);
    }

    static String usage() {
        return """
                Usage: interlis-validator [--repository LOCATION] [--model NAME] [--offline] [--verbose] [--] FILE
                       interlis-validator --help
                       interlis-validator --version
                """;
    }
}
