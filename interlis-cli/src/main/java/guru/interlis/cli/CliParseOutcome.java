package guru.interlis.cli;

import java.util.Objects;

/** Validated commands, informational requests and usage errors remain distinct. */
public sealed interface CliParseOutcome permits ParsedCommand, CliParseOutcome.HelpRequested,
        CliParseOutcome.VersionRequested, CliParseOutcome.InvalidCommandLine {
    record HelpRequested(ParsedCommand command) implements CliParseOutcome {
        public HelpRequested { Objects.requireNonNull(command, "command"); }
    }
    record VersionRequested(ParsedCommand command) implements CliParseOutcome {
        public VersionRequested { Objects.requireNonNull(command, "command"); }
    }
    record InvalidCommandLine(String message) implements CliParseOutcome {
        public InvalidCommandLine { Objects.requireNonNull(message, "message"); }
    }
}
