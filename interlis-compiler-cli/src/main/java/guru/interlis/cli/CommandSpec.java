package guru.interlis.cli;

import java.util.*;

/** Immutable command grammar. Option order is also the deterministic help order. */
public record CommandSpec(String command, List<Option> options, boolean acceptsFiles) {
    /** Copies the ordered definitions and rejects duplicate option names. */
    public CommandSpec {
        Objects.requireNonNull(command, "command");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        if (command.isBlank() || options.stream().map(Option::name).distinct().count() != options.size()) {
            throw new IllegalArgumentException("invalid command or duplicate option");
        }
    }

    /** A flag has zero values. Repeated occurrences retain input order. */
    public record Option(String name, int valueCount, boolean repeatable) {
        public Option {
            Objects.requireNonNull(name, "name");
            if (!name.startsWith("-") || name.equals("--") || valueCount < 0) {
                throw new IllegalArgumentException("invalid option definition");
            }
        }
    }

    /** Returns the explicit new CLI grammar; unknown commands are API errors. */
    public static CommandSpec standard(String command) {
        var options = new ArrayList<Option>();
        boolean files = switch (command) {
            case "help", "capabilities" -> false;
            case "version" -> { options.add(new Option("--json", 0, false)); yield false; }
            case "format" -> {
                for (String flag : List.of("--check", "--write")) options.add(new Option(flag, 0, false));
                for (String value : List.of("--indent", "--line-ending")) options.add(new Option(value, 1, false));
                yield true;
            }
            case "check", "compile", "analyze", "diagram" -> {
                options.add(new Option("--json", 0, false));
                options.add(new Option("--root", 1, true));
                if (command.equals("diagram")) options.add(new Option("--format", 1, false));
                if (command.equals("check") || command.equals("compile")) {
                    options.add(new Option("--repository", 1, true));
                    options.add(new Option("--model-pin", 4, true));
                    options.add(new Option("--source-pin", 2, true));
                    for (String name : List.of("--read-lock", "--write-lock", "--cache", "--config", "--warnings"))
                        options.add(new Option(name, 1, false));
                    options.add(new Option("--offline", 0, false));
                    options.add(new Option("--explain-config", 0, false));
                }
                yield true;
            }
            default -> throw new IllegalArgumentException("unknown command: " + command);
        };
        return new CommandSpec(command, options, files);
    }
}
