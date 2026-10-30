package guru.interlis.cli;

import java.util.*;

/** Explicit grammar shared by the standard and ili2c compatibility entry points. */
public final class DefaultCommandLineParser implements CommandLineParser {
    @Override public CliParseOutcome parse(List<String> arguments, CommandSpec specification) {
        List<String> args = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        Objects.requireNonNull(specification, "specification");
        if (args.isEmpty() || !args.getFirst().equals(specification.command()))
            return new CliParseOutcome.InvalidCommandLine("missing or wrong command");
        var definitions = new LinkedHashMap<String, CommandSpec.Option>();
        specification.options().forEach(option -> definitions.put(option.name(), option));
        var options = new LinkedHashMap<String, List<List<String>>>();
        var files = new ArrayList<String>();
        boolean fileMode = false;
        for (int index = 1; index < args.size(); index++) {
            String arg = args.get(index);
            if (!fileMode && arg.equals("--")) { fileMode = true; continue; }
            if (!fileMode && arg.startsWith("-")) {
                var option = definitions.get(arg);
                if (option == null) return new CliParseOutcome.InvalidCommandLine("unsupported option: " + arg);
                if (!option.repeatable() && options.containsKey(arg))
                    return new CliParseOutcome.InvalidCommandLine("duplicate option: " + arg);
                if (index + option.valueCount() >= args.size())
                    return new CliParseOutcome.InvalidCommandLine(arg + " needs " + option.valueCount() + " value(s)");
                var values = new ArrayList<String>();
                for (int count = 0; count < option.valueCount(); count++) {
                    String value = args.get(++index);
                    if (value.equals("--") || definitions.containsKey(value))
                        return new CliParseOutcome.InvalidCommandLine(arg + " has a missing value");
                    values.add(value);
                }
                options.computeIfAbsent(arg, ignored -> new ArrayList<>()).add(values);
            } else {
                if (!specification.acceptsFiles())
                    return new CliParseOutcome.InvalidCommandLine(specification.command() + " accepts no files");
                files.add(arg);
            }
        }
        var parsed = new ParsedCommand(specification.command(), options, files);
        return switch (specification.command()) {
            case "help" -> new CliParseOutcome.HelpRequested(parsed);
            case "version" -> new CliParseOutcome.VersionRequested(parsed);
            default -> parsed;
        };
    }
}
