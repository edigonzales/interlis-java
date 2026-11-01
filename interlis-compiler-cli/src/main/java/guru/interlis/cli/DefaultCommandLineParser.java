package guru.interlis.cli;

import java.util.*;

/** Explicit grammar for the compiler command line; parsing performs no host IO. */
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
                String inlineColor = null;
                if (isInlineColorOption(arg, definitions)) { inlineColor = arg.substring(8); arg = "--color"; }
                var option = definitions.get(arg);
                if (option == null) return new CliParseOutcome.InvalidCommandLine("unsupported option: " + arg);
                if (!option.repeatable() && options.containsKey(arg))
                    return new CliParseOutcome.InvalidCommandLine("duplicate option: " + arg);
                if (inlineColor == null && index + option.valueCount() >= args.size())
                    return new CliParseOutcome.InvalidCommandLine(arg + " needs " + option.valueCount() + " value(s)");
                var values = new ArrayList<String>();
                if (inlineColor != null) values.add(inlineColor);
                for (int count = 0; inlineColor == null && count < option.valueCount(); count++) {
                    String value = args.get(++index);
                    if (value.equals("--") || definitions.containsKey(value) || isInlineColorOption(value, definitions))
                        return new CliParseOutcome.InvalidCommandLine(arg + " has a missing value");
                    values.add(value);
                }
                if (arg.equals("--color") && option.valueCount() == 1 && !Set.of("auto", "always", "never").contains(values.getFirst()))
                    return new CliParseOutcome.InvalidCommandLine("--color must be auto, always or never");
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
    private static boolean isInlineColorOption(String argument, Map<String, CommandSpec.Option> definitions) {
        var color = definitions.get("--color");
        return color != null && color.valueCount() == 1 && argument.startsWith("--color=");
    }
}
