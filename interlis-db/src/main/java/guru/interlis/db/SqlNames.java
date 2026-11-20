package guru.interlis.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Allocates names in declaration order for the default ili2db 5.5.2 mapping. */
final class SqlNames {
    private static final int NAME_LENGTH = 60;
    private static final int MODEL_LENGTH = 14;
    private static final int TOPIC_LENGTH = 17;
    private static final int REVERSE_LENGTH = NAME_LENGTH - 6;
    private static final int REVERSE_PARENT_LENGTH = (REVERSE_LENGTH - 5) / 2 - 2;
    private static final Set<String> KEYWORDS = readKeywords();

    private final Set<String> tables = new HashSet<>();
    private final Map<String, Set<String>> columns = new HashMap<>();

    String table(String model, String topic, String className) {
        String preferred = preferred(className);
        if (tables.add(preferred)) {
            return preferred;
        }
        String prefix = (topic == null ? shorten(model, NAME_LENGTH / 2 - 2)
                : shorten(model, MODEL_LENGTH) + shorten(topic, TOPIC_LENGTH)) + "_";
        String qualified = (prefix + shorten(className, NAME_LENGTH - prefix.length())).toLowerCase(Locale.ROOT);
        if (tables.add(qualified)) {
            return qualified;
        }
        for (int suffix = 1; ; suffix++) {
            String candidate = qualified + suffix;
            // Legacy table lookups truncate the suffix beyond 60 characters, repeating a tried name.
            if (candidate.length() > NAME_LENGTH) {
                throw new DbException("No ili2db 5.5.2 table name available for "
                        + model + "." + topic + "." + className);
            }
            if (tables.add(candidate)) {
                return candidate;
            }
        }
    }

    String column(String table, String attribute) {
        return allocateColumn(table, preferred(attribute));
    }

    String reverseColumn(String child, String parent, String attribute) {
        String prefix = shorten(parent, REVERSE_PARENT_LENGTH) + "_";
        return allocateColumn(child, preferred(prefix + shorten(attribute, REVERSE_LENGTH - prefix.length())));
    }

    private String allocateColumn(String table, String base) {
        Set<String> used = columns.computeIfAbsent(table, ignored -> new HashSet<>());
        String candidate = base;
        for (int suffix = 1; !used.add(candidate); suffix++) {
            // Legacy column lookups use the full suffix. The backend checks its physical name limit.
            candidate = base + suffix;
        }
        return candidate;
    }

    private static String preferred(String name) {
        String candidate = shorten(name, NAME_LENGTH);
        while (KEYWORDS.contains(candidate.toUpperCase(Locale.ROOT))) {
            candidate = "a" + candidate;
        }
        return candidate.substring(0, Math.min(candidate.length(), NAME_LENGTH)).toLowerCase(Locale.ROOT);
    }

    private static String shorten(String name, int length) {
        int excess = name.length() - length;
        if (excess <= 0) {
            return name;
        }
        // The reference removes interior vowels from right to left before cutting the middle.
        var shortened = new StringBuilder(name.length());
        for (int index = name.length() - 1; index >= 0; index--) {
            char letter = name.charAt(index);
            if (excess > 0 && index >= 3 && index < name.length() - 3 && "AEIOUaeiou".indexOf(letter) >= 0) {
                excess--;
            } else {
                shortened.append(letter);
            }
        }
        shortened.reverse();
        int middle = (shortened.length() - excess) / 2;
        return shortened.delete(middle, middle + excess).toString();
    }

    private static Set<String> readKeywords() {
        var input = SqlNames.class.getResourceAsStream("ili2db-5.5.2-sql-keywords.txt");
        if (input == null) {
            throw new IllegalStateException("Missing ili2db 5.5.2 keyword vocabulary");
        }
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read ili2db 5.5.2 keyword vocabulary", failure);
        }
    }
}
