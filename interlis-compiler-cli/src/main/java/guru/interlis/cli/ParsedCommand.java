package guru.interlis.cli;

import java.util.*;

/** Deeply immutable option occurrences and file arguments, each in input order. */
public record ParsedCommand(String command, Map<String, List<List<String>>> options,
                            List<String> files) implements CliParseOutcome {
    public ParsedCommand {
        Objects.requireNonNull(command, "command");
        var copy = new LinkedHashMap<String, List<List<String>>>();
        Objects.requireNonNull(options, "options").forEach((name, occurrences) ->
                copy.put(Objects.requireNonNull(name), occurrences.stream().map(List::copyOf).toList()));
        options = Collections.unmodifiableMap(copy);
        files = List.copyOf(Objects.requireNonNull(files, "files"));
    }

    public boolean has(String option) { return options.containsKey(option); }
    public List<List<String>> occurrences(String option) { return options.getOrDefault(option, List.of()); }
    public Optional<String> value(String option) {
        var values = occurrences(option);
        return values.isEmpty() || values.getFirst().isEmpty() ? Optional.empty()
                : Optional.of(values.getFirst().getFirst());
    }
    public List<String> values(String option) {
        return occurrences(option).stream().filter(values -> !values.isEmpty()).map(List::getFirst).toList();
    }
}
