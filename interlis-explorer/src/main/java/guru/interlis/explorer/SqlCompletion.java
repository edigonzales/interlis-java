package guru.interlis.explorer;

import guru.interlis.explorer.ExplorerQueries.SqlCatalog;
import guru.interlis.explorer.ExplorerQueries.SqlCatalog.Table;
import guru.interlis.explorer.ExplorerQueries.Suggestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lexical completion for incomplete SQL. DuckDB still parses and binds executable queries. */
final class SqlCompletion {
    private static final int MAX_COMPLETION_CHARS = 64_000;
    private static final int MAX_INFERRED_COLUMNS = 1_000;
    private static final int MAX_SUGGESTIONS = 100;
    private static final List<String> KEYWORDS = List.of(
            "SELECT", "FROM", "WHERE", "AS", "JOIN", "ON", "AND", "OR", "NOT", "GROUP BY", "HAVING",
            "ORDER BY", "LIMIT", "WITH", "DISTINCT", "UNION", "IS NULL", "ASC", "DESC");
    private static final Set<String> FROM_END = Set.of(
            "where", "group", "having", "qualify", "window", "order", "limit", "offset", "fetch",
            "union", "intersect", "except", "sample", "returning", "select");
    private static final Set<String> NO_ALIAS = Set.of(
            "from", "join", "on", "using", "left", "right", "inner", "outer", "full", "cross",
            "natural", "asof", "anti", "semi", "lateral", "positional", "pivot", "unpivot",
            "as", "and", "or", "not", "null", "true", "false", "end", "then", "else", "when");
    private static final Set<String> QUOTED_WORDS = Set.of(
            "all", "analyse", "analyze", "any", "asc", "between", "both", "case", "cast", "check", "collate",
            "column", "constraint", "create", "current_catalog", "current_date", "current_schema",
            "current_time", "current_timestamp", "default", "deferrable", "desc", "distinct", "do", "else",
            "end", "false", "for", "foreign", "grant", "in", "initially", "leading", "notnull", "only", "placing",
            "primary", "references", "some", "symmetric", "table", "then", "to", "trailing", "unique", "variadic",
            "when", "with");

    private enum Kind { WORD, QUOTED, LITERAL, COMMENT, NUMBER, DOT, COMMA, STAR, OPEN, CLOSE, SEMICOLON, OTHER }

    private record Token(Kind kind, String text, int start, int end, boolean closed) {
        boolean name() {
            return kind == Kind.WORD || kind == Kind.QUOTED;
        }

        boolean word(String keyword) {
            return kind == Kind.WORD && same(text, keyword);
        }

        String value() {
            return kind == Kind.QUOTED ? unquote(text, closed) : text;
        }
    }

    private record Edit(int index, int start, int end, String prefix, boolean quoted) {}
    private record Path(List<String> names, int end) {}

    private record Source(List<String> names, List<String> columns) {
        String name() {
            return names.getLast();
        }

        boolean matches(List<String> qualifier) {
            int offset = names.size() - qualifier.size();
            return offset >= 0 && equalNames(names.subList(offset, names.size()), qualifier)
                    || names.size() == 3 && qualifier.size() == 2 && same(names.get(1), "main")
                    && same(names.getFirst(), qualifier.getFirst()) && same(name(), qualifier.getLast());
        }
    }

    private final SqlCatalog catalog;
    private final List<Token> tokens;
    private final int[] closes;

    private SqlCompletion(SqlCatalog catalog, List<Token> tokens) {
        this.catalog = catalog;
        this.tokens = tokens;
        closes = new int[tokens.size()];
        Arrays.fill(closes, -1);
        var opens = new ArrayList<Integer>();
        for (int index = 0; index < tokens.size(); index++) {
            if (at(index, Kind.OPEN)) {
                opens.add(index);
            } else if (at(index, Kind.CLOSE) && !opens.isEmpty()) {
                closes[opens.removeLast()] = index;
            }
        }
    }

    static List<Suggestion> complete(SqlCatalog catalog, String text, int caret) {
        if (caret < 0 || caret > text.length() || text.length() > MAX_COMPLETION_CHARS) {
            return List.of();
        }
        var all = lex(text);
        for (var token : all) {
            if ((token.kind() == Kind.COMMENT || token.kind() == Kind.LITERAL || token.kind() == Kind.NUMBER)
                    && token.start() < caret && (caret < token.end() || !token.closed() && caret == token.end())) {
                return List.of();
            }
        }
        var tokens = all.stream().filter(token -> token.kind() != Kind.COMMENT).toList();
        return new SqlCompletion(catalog, tokens).complete(edit(tokens, caret));
    }

    private List<Suggestion> complete(Edit edit) {
        int start = 0;
        int end = tokens.size();
        for (int index = 0; index < tokens.size(); index++) {
            if (at(index, Kind.SEMICOLON)) {
                if (index < edit.index()) {
                    start = index + 1;
                } else {
                    end = index;
                    break;
                }
            }
        }
        var ctes = new LinkedHashMap<String, Source>();
        var sources = new ArrayList<Source>();
        int queryStart = start;
        int queryEnd = end;
        for (int index = start; index < edit.index(); index++) {
            if (at(index, Kind.OPEN) && queryAt(index + 1)
                    && (closes[index] < 0 || edit.index() <= closes[index])) {
                int main = readCtes(queryStart, queryEnd, ctes, edit.index());
                if (main >= 0) {
                    sources.addAll(bindings(main, queryEnd, ctes));
                }
                queryStart = index + 1;
                queryEnd = closes[index] < 0 ? queryEnd : closes[index];
            }
        }
        int main = readCtes(queryStart, queryEnd, ctes, edit.index());
        if (main >= 0) {
            sources.addAll(bindings(main, queryEnd, ctes));
        }

        var qualifier = new ArrayList<String>();
        int pathStart = edit.index();
        while (pathStart >= queryStart + 2 && at(pathStart - 1, Kind.DOT) && nameAt(pathStart - 2)) {
            qualifier.addFirst(tokens.get(pathStart - 2).value());
            pathStart -= 2;
        }
        var suggestions = new LinkedHashMap<String, Suggestion>();
        if (relationPosition(queryStart, pathStart)) {
            addRelations(suggestions, edit, qualifier, ctes);
        } else if (!qualifier.isEmpty()) {
            var source = sources.reversed().stream().filter(candidate -> candidate.matches(qualifier)).findFirst();
            if (source.isEmpty() && sources.isEmpty()) {
                source = resolve(qualifier, ctes);
            }
            source.ifPresent(match -> addColumns(suggestions, edit, match));
        } else {
            sources.reversed().forEach(source -> {
                addColumns(suggestions, edit, source);
                add(suggestions, edit, source.name(), "Source", true);
            });
            if (sources.isEmpty()) {
                addRelations(suggestions, edit, List.of(), ctes);
                catalog.sources().forEach(table -> addColumns(suggestions, edit, source(table)));
            }
            if (!edit.quoted()) {
                KEYWORDS.forEach(keyword -> add(suggestions, edit, keyword, "SQL", false));
                catalog.functions().forEach(name -> add(suggestions, edit, name, "Function", true));
            }
        }
        return List.copyOf(suggestions.values());
    }

    private boolean relationPosition(int start, int pathStart) {
        if (word(pathStart - 1, "from") || word(pathStart - 1, "join")) {
            return true;
        }
        if (!at(pathStart - 1, Kind.COMMA)) {
            return false;
        }
        boolean from = false;
        for (int index = start; index < pathStart - 1; index = next(index, pathStart)) {
            if (word(index, "from") || word(index, "join")) {
                from = true;
            } else if (oneOf(index, FROM_END) || word(index, "on") || word(index, "using")) {
                from = false;
            }
        }
        return from;
    }

    private void addRelations(Map<String, Suggestion> choices, Edit edit, List<String> qualifier,
                              Map<String, Source> ctes) {
        if (qualifier.isEmpty()) {
            ctes.values().forEach(cte -> add(choices, edit, cte.name(), "CTE", true));
            for (var table : catalog.sources()) {
                add(choices, edit, table.name(), "Table", true);
                add(choices, edit, table.schema(), "Schema", true);
                add(choices, edit, table.catalog(), "Catalog", true);
            }
        } else if (qualifier.size() == 1) {
            for (var table : catalog.sources()) {
                if (same(table.schema(), qualifier.getFirst())) {
                    add(choices, edit, table.name(), "Table", true);
                }
                if (same(table.catalog(), qualifier.getFirst())) {
                    add(choices, edit, table.schema(), "Schema", true);
                    if (same(table.schema(), "main")) {
                        add(choices, edit, table.name(), "Table", true);
                    }
                }
            }
        } else if (qualifier.size() == 2) {
            for (var table : catalog.sources()) {
                if (same(table.catalog(), qualifier.getFirst()) && same(table.schema(), qualifier.getLast())) {
                    add(choices, edit, table.name(), "Table", true);
                }
            }
        }
    }

    private List<Source> bindings(int start, int end, Map<String, Source> ctes) {
        var sources = new ArrayList<Source>();
        boolean from = false;
        for (int index = start; index < end; index = next(index, end)) {
            if (oneOf(index, FROM_END) || word(index, "on") || word(index, "using")) {
                from = false;
            }
            if (word(index, "from") || word(index, "join") || from && at(index, Kind.COMMA)) {
                from = true;
                binding(index + 1, end, ctes).ifPresent(sources::add);
            }
        }
        return sources;
    }

    private Optional<Source> binding(int start, int end, Map<String, Source> ctes) {
        if (word(start, "lateral")) {
            start++;
        }
        var path = path(start, end);
        if (path.names().isEmpty() || at(path.end(), Kind.OPEN)) {
            return Optional.empty();
        }
        int after = path.end();
        var source = resolve(path.names(), ctes).orElseGet(() -> new Source(path.names(), List.of()));
        if (word(after, "as")) {
            after++;
        }
        if (after < end && aliasAt(after)) {
            source = new Source(List.of(tokens.get(after).value()), source.columns());
        }
        return Optional.of(source);
    }

    private Optional<Source> resolve(List<String> names, Map<String, Source> ctes) {
        if (names.size() == 1) {
            var cte = ctes.get(normalize(names.getFirst()));
            if (cte != null) {
                return Optional.of(cte);
            }
        }
        return catalog.sources().stream().filter(table -> tableMatches(table, names))
                .min(Comparator.comparing(table -> !same(table.schema(), "main")))
                .map(SqlCompletion::source);
    }

    private static boolean tableMatches(Table table, List<String> names) {
        return switch (names.size()) {
            case 1 -> same(table.name(), names.getFirst());
            case 2 -> same(table.name(), names.getLast()) && (same(table.schema(), names.getFirst())
                    || same(table.schema(), "main") && same(table.catalog(), names.getFirst()));
            case 3 -> equalNames(source(table).names(), names);
            default -> false;
        };
    }

    private static Source source(Table table) {
        return new Source(List.of(table.catalog(), table.schema(), table.name()), table.columns());
    }

    /** Reads sibling CTEs in declaration order and returns the main query start. */
    private int readCtes(int start, int end, Map<String, Source> ctes, int caretIndex) {
        if (!word(start, "with")) {
            return start;
        }
        int index = word(start + 1, "recursive") ? start + 2 : start + 1;
        while (index < end && nameAt(index)) {
            String name = tokens.get(index++).value();
            var explicit = namesInParentheses(index, end);
            if (!explicit.isEmpty()) {
                index = closes[index] + 1;
            }
            if (!word(index, "as")) {
                break;
            }
            index++;
            if (word(index, "not")) {
                index++;
            }
            if (word(index, "materialized")) {
                index++;
            }
            if (!at(index, Kind.OPEN) || closes[index] < 0 || closes[index] >= end) {
                break;
            }
            int close = closes[index];
            if (index < caretIndex && caretIndex <= close) {
                break;
            }
            var columns = explicit.isEmpty() ? projection(index + 1, close, ctes) : explicit;
            ctes.put(normalize(name), new Source(List.of(name), columns));
            index = close + 1;
            if (!at(index, Kind.COMMA)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private List<String> namesInParentheses(int open, int end) {
        if (open >= end || !at(open, Kind.OPEN) || closes[open] < 0 || closes[open] >= end) {
            return List.of();
        }
        var names = new ArrayList<String>();
        for (int index = open + 1; index < closes[open]; index += 2) {
            if (!nameAt(index)) {
                return List.of();
            }
            names.add(tokens.get(index).value());
            if (index + 1 < closes[open] && (!at(index + 1, Kind.COMMA) || index + 2 == closes[open])) {
                return List.of();
            }
        }
        return names;
    }

    private List<String> projection(int select, int end, Map<String, Source> ctes) {
        if (!word(select, "select")) {
            return List.of();
        }
        int item = select + 1;
        if (word(item, "distinct") || word(item, "all")) {
            item++;
            if (word(item, "on") && at(item + 1, Kind.OPEN) && closes[item + 1] >= 0) {
                item = closes[item + 1] + 1;
            }
        }
        var sources = bindings(item, end, ctes);
        var columns = new ArrayList<String>();
        for (int index = item; index < end; index = next(index, end)) {
            if (word(index, "from") || oneOf(index, FROM_END)) {
                addProjection(columns, item, index, sources);
                return columns;
            }
            if (at(index, Kind.COMMA)) {
                addProjection(columns, item, index, sources);
                item = index + 1;
            }
        }
        addProjection(columns, item, end, sources);
        return columns;
    }

    private void addProjection(List<String> columns, int start, int end, List<Source> sources) {
        if (start >= end || columns.size() == MAX_INFERRED_COLUMNS) {
            return;
        }
        if (end == start + 1 && at(start, Kind.STAR)) {
            sources.forEach(source -> addInferredColumns(columns, source));
            return;
        }
        var path = path(start, end);
        if (path.end() == end && !path.names().isEmpty()) {
            columns.add(path.names().getLast());
        } else if (path.end() == end - 2 && at(end - 2, Kind.DOT) && at(end - 1, Kind.STAR)) {
            sources.stream().filter(source -> source.matches(path.names())).findFirst()
                    .ifPresent(source -> addInferredColumns(columns, source));
        } else if (aliasAt(end - 1) && (word(end - 2, "as") || implicitProjectionAlias(start, end - 1))) {
            columns.add(tokens.get(end - 1).value());
        }
    }

    private static void addInferredColumns(List<String> columns, Source source) {
        int count = Math.min(MAX_INFERRED_COLUMNS - columns.size(), source.columns().size());
        columns.addAll(source.columns().subList(0, count));
    }

    private boolean implicitProjectionAlias(int start, int alias) {
        if (start >= alias || tokens.get(alias - 1).end() == tokens.get(alias).start()) {
            return false;
        }
        if (alias == start + 1 && (at(start, Kind.NUMBER) || at(start, Kind.LITERAL))) {
            return true;
        }
        int after = path(start, alias).end();
        return after > start && (after == alias || at(after, Kind.OPEN) && closes[after] == alias - 1);
    }

    private Path path(int start, int end) {
        var names = new ArrayList<String>();
        int index = start;
        if (index < end && nameAt(index)) {
            names.add(tokens.get(index++).value());
            while (index + 1 < end && at(index, Kind.DOT) && nameAt(index + 1)) {
                names.add(tokens.get(index + 1).value());
                index += 2;
            }
        }
        return new Path(names, index);
    }

    private boolean queryAt(int index) {
        return word(index, "select") || word(index, "from") || word(index, "with") || word(index, "values");
    }

    private boolean aliasAt(int index) {
        return nameAt(index) && !oneOf(index, FROM_END) && !oneOf(index, NO_ALIAS);
    }

    private boolean at(int index, Kind kind) {
        return index >= 0 && index < tokens.size() && tokens.get(index).kind() == kind;
    }

    private boolean nameAt(int index) {
        return at(index, Kind.WORD) || at(index, Kind.QUOTED);
    }

    private boolean word(int index, String value) {
        return at(index, Kind.WORD) && tokens.get(index).word(value);
    }

    private boolean oneOf(int index, Set<String> values) {
        return at(index, Kind.WORD) && values.contains(normalize(tokens.get(index).text()));
    }

    private int next(int index, int end) {
        if (at(index, Kind.OPEN)) {
            return closes[index] < 0 ? end : closes[index] + 1;
        }
        return index + 1;
    }

    private static boolean equalNames(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (!same(left.get(index), right.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String name) {
        var lower = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); index++) {
            char letter = name.charAt(index);
            lower.append(letter >= 'A' && letter <= 'Z' ? (char) (letter + ('a' - 'A')) : letter);
        }
        return lower.toString();
    }

    private static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static void addColumns(Map<String, Suggestion> choices, Edit edit, Source source) {
        source.columns().forEach(name -> add(choices, edit, name, source.name(), true));
    }

    private static void add(Map<String, Suggestion> choices, Edit edit, String name, String description,
                            boolean identifier) {
        String key = normalize(name);
        if (choices.size() < MAX_SUGGESTIONS && !key.isEmpty() && key.startsWith(edit.prefix())) {
            String text = identifier && (edit.quoted() || quoteRequired(name)) ? quote(name) : name;
            choices.putIfAbsent(key, new Suggestion(text, description, edit.start(), edit.end()));
        }
    }

    private static boolean quoteRequired(String name) {
        String lower = normalize(name);
        return !wordStart(name.charAt(0)) || !name.chars().allMatch(SqlCompletion::wordPart)
                || FROM_END.contains(lower) || NO_ALIAS.contains(lower) || QUOTED_WORDS.contains(lower);
    }

    private static String quote(String name) {
        return '"' + name.replace("\"", "\"\"") + '"';
    }

    private static String unquote(String text, boolean closed) {
        return text.substring(1, text.length() - (closed ? 1 : 0)).replace("\"\"", "\"");
    }

    private static Edit edit(List<Token> tokens, int caret) {
        int index = 0;
        for (; index < tokens.size(); index++) {
            var token = tokens.get(index);
            if (token.start() > caret) {
                break;
            }
            if (token.name() && caret <= token.end()) {
                boolean quoted = token.kind() == Kind.QUOTED;
                boolean closed = quoted && token.closed() && caret == token.end();
                var prefix = caret == token.start() ? "" : token.text().substring(0, caret - token.start());
                if (quoted && !prefix.isEmpty()) {
                    prefix = unquote(prefix, closed);
                }
                return new Edit(index, token.start(), token.end(), normalize(prefix), quoted);
            }
            if (token.start() == caret) {
                break;
            }
        }
        return new Edit(index, caret, caret, "", false);
    }

    private static List<Token> lex(String text) {
        var tokens = new ArrayList<Token>();
        int cursor = 0;
        while (cursor < text.length()) {
            int start = cursor++;
            char value = text.charAt(start);
            if (Character.isWhitespace(value)) {
                continue;
            }
            Kind kind;
            boolean closed = true;
            if (text.startsWith("--", start)) {
                kind = Kind.COMMENT;
                while (cursor < text.length() && text.charAt(cursor) != '\n' && text.charAt(cursor) != '\r') {
                    cursor++;
                }
                closed = false;
            } else if (text.startsWith("/*", start)) {
                kind = Kind.COMMENT;
                cursor = blockCommentEnd(text, start + 2);
                closed = cursor >= 0;
                cursor = closed ? cursor : text.length();
            } else if (value == '"' || value == '\'' || (value == 'E' || value == 'e')
                    && cursor < text.length() && text.charAt(cursor) == '\'') {
                boolean escaped = value == 'E' || value == 'e';
                char quote = escaped ? '\'' : value;
                kind = quote == '"' ? Kind.QUOTED : Kind.LITERAL;
                cursor = quoteEnd(text, cursor + (escaped ? 1 : 0), quote, escaped);
                closed = cursor >= 0;
                cursor = closed ? cursor : text.length();
            } else if (value == '$' && dollarDelimiter(text, start) > start) {
                kind = Kind.LITERAL;
                int delimiterEnd = dollarDelimiter(text, start);
                int delimiterStart = text.indexOf(text.substring(start, delimiterEnd), delimiterEnd);
                closed = delimiterStart >= 0;
                cursor = closed ? delimiterStart + delimiterEnd - start : text.length();
            } else if (wordStart(value) || Character.isDigit(value)) {
                kind = Character.isDigit(value) ? Kind.NUMBER : Kind.WORD;
                while (cursor < text.length() && wordPart(text.charAt(cursor))) {
                    cursor++;
                }
            } else {
                kind = switch (value) {
                    case '.' -> Kind.DOT;
                    case ',' -> Kind.COMMA;
                    case '*' -> Kind.STAR;
                    case '(' -> Kind.OPEN;
                    case ')' -> Kind.CLOSE;
                    case ';' -> Kind.SEMICOLON;
                    default -> Kind.OTHER;
                };
            }
            tokens.add(new Token(kind, text.substring(start, cursor), start, cursor, closed));
        }
        return tokens;
    }

    private static int quoteEnd(String text, int cursor, char quote, boolean escaped) {
        while (cursor < text.length()) {
            char current = text.charAt(cursor++);
            if (current == '\\' && escaped && cursor < text.length()) {
                cursor++;
            } else if (current == quote) {
                if (cursor == text.length() || text.charAt(cursor) != quote) {
                    return cursor;
                }
                cursor++;
            }
        }
        return -1;
    }

    private static int blockCommentEnd(String text, int cursor) {
        int depth = 1;
        while (cursor < text.length()) {
            if (text.startsWith("/*", cursor)) {
                depth++;
                cursor += 2;
            } else if (text.startsWith("*/", cursor)) {
                cursor += 2;
                if (--depth == 0) {
                    return cursor;
                }
            } else {
                cursor++;
            }
        }
        return -1;
    }

    private static int dollarDelimiter(String text, int start) {
        int end = start + 1;
        while (end < text.length() && text.charAt(end) != '$' && wordPart(text.charAt(end))) {
            end++;
        }
        if (end < text.length() && text.charAt(end) == '$'
                && (end == start + 1 || wordStart(text.charAt(start + 1)))) {
            return end + 1;
        }
        return -1;
    }

    private static boolean wordStart(int value) {
        return value == '_' || Character.isLetter(value);
    }

    private static boolean wordPart(int value) {
        return wordStart(value) || Character.isDigit(value) || value == '$';
    }
}
