package guru.interlis.cli;

import java.util.List;

/** Pure argument parsing: no IO, source loading, stream mutation or analysis. */
@FunctionalInterface
public interface CommandLineParser {
    /** All arguments are null-free; the first argument is the specification's command. */
    CliParseOutcome parse(List<String> arguments, CommandSpec specification);
}
