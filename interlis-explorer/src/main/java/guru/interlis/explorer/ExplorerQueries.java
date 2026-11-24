package guru.interlis.explorer;

import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/** Read-only queries for one opened Explorer store. Results are bounded previews. */
public final class ExplorerQueries {
    private static final int ROW_LIMIT = 1_000;
    private static final int CELL_LIMIT = 2_048;
    private static final int TIMEOUT_SECONDS = 60;
    private static final long CANCEL_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
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

    /** Cancellation handle for one call to execute. cancel requests cancellation without waiting for JDBC. */
    public static final class Cancellation {
        private final AtomicReference<PreparedStatement> statement = new AtomicReference<>();
        private final AtomicBoolean requested = new AtomicBoolean();

        public Cancellation() {}

        public void cancel() {
            if (requested.compareAndSet(false, true)) {
                var running = statement.get();
                if (running != null) {
                    Thread.startVirtualThread(() -> interrupt(running));
                }
            }
        }

        private void interrupt(PreparedStatement running) {
            // DuckDB ignores cancel until execute acquires the statement lock.
            while (statement.get() == running) {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    return; // Closing a completed statement may race with cancel.
                }
                LockSupport.parkNanos(CANCEL_RETRY_NANOS);
            }
        }

        public boolean requested() {
            return requested.get();
        }

        private void check() {
            if (requested()) {
                throw new CancellationException();
            }
        }
    }

    /** Executes one IQL or native SQL query and materializes at most 1,001 preview rows in DuckDB. */
    public Result execute(Mode mode, String text, Cancellation cancellation) throws SQLException {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(text);
        cancellation.check();
        try (var connection = store.duplicateForQuery()) {
            if (mode == Mode.SQL) {
                var select = singleSelect(connection, text, cancellation);
                List<String> labels;
                // The wrapper disambiguates duplicate labels. Take labels from the original SELECT.
                try (var original = connection.prepareStatement(text)) {
                    labels = columnLabels(original.getMetaData());
                }
                cancellation.check();
                var boundedSql = "SELECT * FROM (" + select + ") LIMIT " + (ROW_LIMIT + 1);
                try (var query = connection.prepareStatement(boundedSql)) {
                    return preview(query, cancellation, labels);
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
                cancellation.check();
                try (var validation = connection.prepareStatement(check.sql())) {
                    bind(validation, check.parameters());
                    var message = runStatement(validation, cancellation, () -> {
                        try (var rows = validation.executeQuery()) {
                            return rows.next() ? rows.getString(1) : null;
                        }
                    });
                    if (message != null) {
                        throw new Iql.QueryException(message, check.start(), check.end());
                    }
                }
            }
            cancellation.check();
            try (var query = connection.prepareStatement(compiled.sql())) {
                bind(query, compiled.parameters());
                return preview(query, cancellation, columnLabels(query.getMetaData()));
            }
        }
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    private static String singleSelect(Connection connection, String sql, Cancellation cancellation)
            throws SQLException {
        try (var classify = connection.prepareStatement("""
                WITH parsed AS (SELECT json_serialize_sql(?) AS tree)
                SELECT tree, CASE WHEN NOT (tree->>'error')::BOOLEAN
                        AND json_array_length(tree->'statements') = 1
                    THEN json_deserialize_sql(tree) END
                FROM parsed
                """)) {
            classify.setString(1, sql);
            return runStatement(classify, cancellation, () -> {
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
                    return rows.getString(2);
                }
            });
        }
    }

    private static List<String> columnLabels(ResultSetMetaData metadata) throws SQLException {
        var columns = new ArrayList<String>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            columns.add(metadata.getColumnLabel(index));
        }
        return columns;
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }

    private static <T> T runStatement(PreparedStatement statement, Cancellation cancellation,
                                      SqlOperation<T> operation) throws SQLException {
        statement.setQueryTimeout(TIMEOUT_SECONDS);
        cancellation.statement.set(statement);
        try {
            cancellation.check();
            var result = operation.run();
            cancellation.check();
            return result;
        } catch (SQLException failure) {
            cancellation.check();
            throw failure;
        } finally {
            cancellation.statement.compareAndSet(statement, null);
        }
    }

    private static Result preview(PreparedStatement query, Cancellation cancellation, List<String> columns)
            throws SQLException {
        return runStatement(query, cancellation, () -> {
            try (var rows = query.executeQuery()) {
                var metadata = rows.getMetaData();
                var data = new ArrayList<List<String>>();
                while (data.size() < ROW_LIMIT) {
                    cancellation.check();
                    if (!rows.next()) {
                        return new Result(columns, data, false);
                    }
                    var line = new ArrayList<String>();
                    for (int index = 1; index <= columns.size(); index++) {
                        cancellation.check();
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
            }
        });
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
