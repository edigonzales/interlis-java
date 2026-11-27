package guru.interlis.validator.cli;

import guru.interlis.validation.ValidationOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record ValidatorOptions(List<Path> primary, List<Path> reference, List<String> repositories,
                        List<String> models, Set<String> flags, Storage storage, Optional<Path> tempDir,
                        int maxDiagnostics, Map<Budget, Long> budgets) {
    enum Storage { MEMORY, MVSTORE }

    enum Budget {
        OBJECT_WORK("object-work"),
        AREA_COMPONENTS("area-components"),
        CONSTRAINT_WORK("constraint-work"),
        XTF23_OBJECT_EVENTS("xtf23-object-events");

        private final String name;

        Budget(String name) {
            this.name = name;
        }

        static Budget named(String name) {
            for (var budget : values()) {
                if (budget.name.equals(name)) return budget;
            }
            throw new IllegalArgumentException("Unknown budget " + name);
        }
    }

    private static final Set<String> FLAGS = Set.of("--help", "--version", "--offline", "--verbose", "--partial", "--json");
    private static final Set<String> VALUES = Set.of("--repository", "--model", "--refdata", "--storage",
            "--temp-dir", "--max-diagnostics", "--budget");
    private static final Set<String> REPEATABLE = Set.of("--repository", "--model", "--refdata", "--budget");

    static ValidatorOptions parse(List<String> args) {
        var flags = new LinkedHashSet<String>();
        var values = new LinkedHashSet<String>();
        var repositories = new ArrayList<String>();
        var models = new ArrayList<String>();
        var primary = new ArrayList<Path>();
        var reference = new ArrayList<Path>();
        var budgets = new EnumMap<Budget, Long>(Budget.class);
        Storage storage = Storage.MEMORY;
        Optional<Path> tempDir = Optional.empty();
        int maxDiagnostics = ValidationOptions.defaults().maxDiagnosticExamples();
        boolean positional = false;
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (!positional && arg.equals("--")) {
                positional = true;
            } else if (!positional && FLAGS.contains(arg)) {
                if (!flags.add(arg)) throw new IllegalArgumentException("Duplicate option " + arg);
            } else if (!positional && VALUES.contains(arg)) {
                if (!REPEATABLE.contains(arg) && !values.add(arg)) {
                    throw new IllegalArgumentException("Duplicate option " + arg);
                }
                if (++i == args.size() || args.get(i).isBlank() || args.get(i).startsWith("-")) {
                    throw new IllegalArgumentException("Missing value for " + arg);
                }
                var value = args.get(i);
                switch (arg) {
                    case "--repository" -> repositories.add(value);
                    case "--model" -> models.add(value);
                    case "--refdata" -> reference.add(Path.of(value));
                    case "--temp-dir" -> tempDir = Optional.of(Path.of(value));
                    case "--storage" -> storage = switch (value) {
                        case "memory" -> Storage.MEMORY;
                        case "mvstore" -> Storage.MVSTORE;
                        default -> throw new IllegalArgumentException("Expected memory or mvstore for --storage");
                    };
                    case "--max-diagnostics" -> maxDiagnostics = diagnosticLimit(value);
                    case "--budget" -> addBudget(budgets, value);
                    default -> throw new IllegalStateException("Unexpected option " + arg);
                }
            } else if (!positional && arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option " + arg);
            } else {
                if (arg.isBlank()) throw new IllegalArgumentException("Expected a nonempty file path");
                primary.add(Path.of(arg));
            }
        }
        if (flags.contains("--help")) {
            if (args.size() != 1) throw new IllegalArgumentException("Use --help on its own");
        } else if (flags.contains("--version")) {
            if (args.size() != (flags.contains("--json") ? 2 : 1)) {
                throw new IllegalArgumentException("Use --version with only the optional --json");
            }
        } else if (primary.isEmpty()) {
            throw new IllegalArgumentException("Expected at least one primary XTF file");
        }
        if (tempDir.isPresent() && storage != Storage.MVSTORE) {
            throw new IllegalArgumentException("--temp-dir requires --storage mvstore");
        }
        return new ValidatorOptions(List.copyOf(primary), List.copyOf(reference), List.copyOf(repositories),
                List.copyOf(models), Set.copyOf(flags), storage, tempDir, maxDiagnostics, Map.copyOf(budgets));
    }

    private static void addBudget(Map<Budget, Long> budgets, String value) {
        int separator = value.indexOf('=');
        if (separator < 1 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Expected NAME=N for --budget");
        }
        String name = value.substring(0, separator);
        var budget = Budget.named(name);
        long limit;
        try {
            limit = Long.parseLong(value.substring(separator + 1));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Budget " + name + " requires a positive integer");
        }
        if (limit < 1) throw new IllegalArgumentException("Budget " + name + " requires a positive integer");
        if (budgets.putIfAbsent(budget, limit) != null) {
            throw new IllegalArgumentException("Duplicate budget " + name);
        }
    }

    private static int diagnosticLimit(String value) {
        try {
            int limit = Integer.parseInt(value);
            if (limit > 0) return limit;
        } catch (NumberFormatException invalid) {
            // Report the same option error for invalid syntax and overflow.
        }
        throw new IllegalArgumentException("--max-diagnostics requires a positive integer");
    }

    boolean has(String option) {
        return flags.contains(option);
    }

    static String usage() {
        return """
                Usage: interlis-validator [--repository LOCATION] [--model NAME] [--refdata FILE]
                       [--offline] [--partial] [--storage memory|mvstore] [--temp-dir DIRECTORY]
                       [--budget NAME=N] [--max-diagnostics N] [--json] [--verbose] [--] FILE [FILE ...]
                       interlis-validator --help
                       interlis-validator --version [--json]

                Budget names: object-work, area-components, constraint-work, xtf23-object-events
                """;
    }
}
