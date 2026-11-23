package guru.interlis.explorer;

import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only queries for one opened Explorer store. Results are bounded previews. */
public final class ExplorerQueries {
    private static final int ROW_LIMIT = 1_000;
    private static final int CELL_LIMIT = 2_048;
    private static final int TIMEOUT_SECONDS = 60;
    private static final List<String> SQL_KEYWORDS = List.of(
            "SELECT", "FROM", "WHERE", "AS", "JOIN", "ON", "AND", "OR", "NOT", "GROUP BY", "HAVING",
            "ORDER BY", "LIMIT", "WITH", "DISTINCT", "UNION", "IS NULL", "ASC", "DESC");

    private final DuckDbStore store;

    public ExplorerQueries(DuckDbStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public enum Mode { IQL, SQL }

    /** Offsets are UTF-16 indices into the source passed for completion. */
    public record Suggestion(String text, String description, int start, int end) {}

    /** Column labels and string previews in result order. All lists are immutable. */
    public record Result(List<String> columns, List<List<String>> rows, boolean truncated) {
        public Result {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
        }
    }

    /** Cancellation handle for one call to execute. cancel may be called from another thread. */
    public static final class Cancellation {
        private final AtomicReference<PreparedStatement> statement = new AtomicReference<>();
        private volatile boolean requested;

        public Cancellation() {}

        public void cancel() {
            requested = true;
            var running = statement.get();
            if (running != null) {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    // Closing a completed statement may race with cancel.
                }
            }
        }

        public boolean requested() {
            return requested;
        }

        private void check() {
            if (requested) {
                throw new CancellationException();
            }
        }

        private void attach(PreparedStatement running) {
            statement.set(running);
            check();
        }
    }

    /** Executes one IQL or native SQL query. SQL is parsed as a single query before it can run. */
    public Result execute(Mode mode, String text, Cancellation cancellation) throws SQLException {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(text);
        cancellation.check();
        try (var connection = store.duplicateForQuery()) {
            if (mode == Mode.SQL) {
                requireSingleQuery(connection, text, cancellation);
                try (var query = connection.prepareStatement(text)) {
                    return preview(query, cancellation);
                }
            }
            var compiled = Iql.compile(store.queryPlan(), text, ROW_LIMIT);
            if (!compiled.checks().isEmpty() && !store.spatialAvailable()) {
                var check = compiled.checks().getFirst();
                throw new Iql.QueryException("DuckDB Spatial is unavailable. "
                        + store.spatialDiagnostic().orElse("Reopen the dataset to retry loading the extension."),
                        check.start(), check.end());
            }
            for (var check : compiled.checks()) {
                try (var validation = connection.prepareStatement(check.sql())) {
                    validation.setQueryTimeout(TIMEOUT_SECONDS);
                    cancellation.attach(validation);
                    try (var rows = validation.executeQuery()) {
                        if (rows.next()) {
                            var message = rows.getString(1);
                            if (message != null) {
                                throw new Iql.QueryException(message, check.start(), check.end());
                            }
                        }
                    } finally {
                        cancellation.statement.compareAndSet(validation, null);
                    }
                }
            }
            try (var query = connection.prepareStatement(compiled.sql())) {
                for (int index = 0; index < compiled.parameters().size(); index++) {
                    query.setObject(index + 1, compiled.parameters().get(index));
                }
                return preview(query, cancellation);
            }
        } finally {
            cancellation.statement.set(null);
        }
    }

    private static void requireSingleQuery(Connection connection, String sql, Cancellation cancellation)
            throws SQLException {
        try (var classify = connection.prepareStatement("SELECT json_serialize_sql(?)")) {
            classify.setQueryTimeout(TIMEOUT_SECONDS);
            classify.setString(1, sql);
            cancellation.attach(classify);
            try (var rows = classify.executeQuery()) {
                rows.next();
                var parsed = JsonParser.parseString(rows.getString(1)).getAsJsonObject();
                if (parsed.get("error").getAsBoolean()) {
                    throw new SQLException(parsed.has("error_message")
                            ? parsed.get("error_message").getAsString() : "Expected one read-only SQL query.");
                }
                if (parsed.getAsJsonArray("statements").size() != 1) {
                    throw new SQLException("Enter exactly one read-only SQL query.");
                }
            } finally {
                cancellation.statement.compareAndSet(classify, null);
            }
        }
    }

    private static Result preview(PreparedStatement query, Cancellation cancellation) throws SQLException {
        query.setQueryTimeout(TIMEOUT_SECONDS);
        cancellation.attach(query);
        try (var rows = query.executeQuery()) {
            var metadata = rows.getMetaData();
            var columns = new ArrayList<String>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                columns.add(metadata.getColumnLabel(index));
            }
            var data = new ArrayList<List<String>>();
            while (data.size() < ROW_LIMIT) {
                cancellation.check();
                if (!rows.next()) {
                    return new Result(columns, data, false);
                }
                var line = new ArrayList<String>();
                for (int index = 1; index <= columns.size(); index++) {
                    var type = metadata.getColumnTypeName(index);
                    if ("GEOMETRY".equalsIgnoreCase(type) || metadata.getColumnType(index) == Types.BLOB) {
                        line.add(rows.getObject(index) == null ? "NULL" : type);
                    } else {
                        var value = rows.getString(index);
                        line.add(value == null ? "NULL" : abbreviated(value));
                    }
                }
                data.add(line);
            }
            cancellation.check();
            return new Result(columns, data, rows.next());
        } finally {
            cancellation.statement.compareAndSet(query, null);
        }
    }

    private static String abbreviated(String value) {
        if (value.length() <= CELL_LIMIT) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(CELL_LIMIT - 1)) ? CELL_LIMIT - 1 : CELL_LIMIT;
        return value.substring(0, end) + "…";
    }

    public List<Suggestion> completeIql(String text, int caret) {
        return Iql.complete(store.queryPlan(), text, caret).stream()
                .map(proposal -> new Suggestion(proposal.text(), proposal.description(),
                        proposal.start(), proposal.end())).toList();
    }

    /** Immutable catalog snapshot built once off the Swing event thread. */
    public record SqlCatalog(Map<String, List<String>> tables, List<String> functions) {
        public SqlCatalog {
            var copy = new LinkedHashMap<String, List<String>>();
            tables.forEach((name, columns) -> copy.put(name, List.copyOf(columns)));
            tables = Map.copyOf(copy);
            functions = List.copyOf(functions);
        }

        public List<Suggestion> complete(String text, int caret) {
            int start = identifierStart(text, caret);
            var prefix = text.substring(start, caret).toLowerCase(Locale.ROOT);
            var candidates = new LinkedHashMap<String, Suggestion>();
            if (start > 0 && text.charAt(start - 1) == '.') {
                int qualifierEnd = start - 1;
                var qualifier = text.substring(identifierStart(text, qualifierEnd), qualifierEnd);
                var tablesInScope = new ArrayList<String>();
                tables.keySet().stream().filter(name -> name.equalsIgnoreCase(qualifier)).findFirst()
                        .ifPresent(tablesInScope::add);
                if (tablesInScope.isEmpty()) {
                    var aliases = java.util.regex.Pattern.compile(
                            "(?i)\\b(?:FROM|JOIN)\\s+([\\w]+)\\s+(?:AS\\s+)?([\\w]+)").matcher(text);
                    while (aliases.find()) {
                        if (aliases.group(2).equalsIgnoreCase(qualifier)) {
                            var table = aliases.group(1);
                            tables.keySet().stream().filter(name -> name.equalsIgnoreCase(table)).findFirst()
                                    .ifPresent(tablesInScope::add);
                        }
                    }
                }
                tablesInScope.forEach(table -> tables.get(table).forEach(column ->
                        add(candidates, column, table, prefix, start, caret)));
            } else {
                tables.keySet().stream().sorted().forEach(table ->
                        add(candidates, table, "Table", prefix, start, caret));
                tables.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(table ->
                        table.getValue().forEach(column -> add(candidates, column, table.getKey(), prefix, start, caret)));
                SQL_KEYWORDS.forEach(keyword -> add(candidates, keyword, "SQL", prefix, start, caret));
                functions.forEach(name -> add(candidates, name, "Function", prefix, start, caret));
            }
            return candidates.values().stream().limit(100).toList();
        }

        private static int identifierStart(String text, int end) {
            int start = end;
            while (start > 0 && (Character.isLetterOrDigit(text.charAt(start - 1))
                    || text.charAt(start - 1) == '_')) {
                start--;
            }
            return start;
        }

        private static void add(Map<String, Suggestion> choices, String text, String description,
                                String prefix, int start, int caret) {
            var lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith(prefix)) {
                choices.putIfAbsent(lower, new Suggestion(text, description, start, caret));
            }
        }
    }

    public SqlCatalog sqlCatalog() throws SQLException {
        var tables = new LinkedHashMap<String, List<String>>();
        var functions = new TreeSet<String>();
        try (var connection = store.duplicateForQuery();
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("""
                    SELECT table_name, column_name FROM duckdb_columns()
                    WHERE schema_name = 'main' AND NOT internal
                    ORDER BY table_name, column_index
                    """)) {
                while (rows.next()) {
                    tables.computeIfAbsent(rows.getString(1), name -> new ArrayList<>()).add(rows.getString(2));
                }
            }
            try (var rows = statement.executeQuery("SELECT DISTINCT function_name FROM duckdb_functions()")) {
                while (rows.next()) {
                    functions.add(rows.getString(1));
                }
            }
        }
        return new SqlCatalog(tables, List.copyOf(functions));
    }
}
