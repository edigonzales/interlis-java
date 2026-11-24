package guru.interlis.explorer;

import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final DuckDbStore store;

    public ExplorerQueries(DuckDbStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public enum Mode { IQL, SQL }

    /** Replacement for the half-open UTF-16 range in the source passed for completion. */
    public record Suggestion(String text, String description, int start, int end) {}

    /**
     * A geometry selected with IQL's explicit native geometry projection in this dataset.
     * The source position and path identify the exact stored occurrence. Unknown CRS and
     * unavailable native types are empty, with the latter carrying a projection error.
     */
    public record GeometryCell(long sourcePosition, String valuePath, Optional<String> crs,
                               Optional<String> geometryType, Optional<String> projectionError) {
        public GeometryCell {
            Objects.requireNonNull(valuePath);
            Objects.requireNonNull(crs);
            Objects.requireNonNull(geometryType);
            Objects.requireNonNull(projectionError);
        }

        private String preview() {
            return geometryType.map(type -> type + " · " + crs.orElse("CRS unknown"))
                    .orElse("Geometry unavailable");
        }
    }

    /**
     * Column labels and string previews in result order. All collections are immutable.
     * A null result still has the string preview "NULL". Each row's set in
     * {@code nullColumnsByRow} identifies its missing cells. A present geometry whose
     * native projection failed has a preview and a diagnostic in {@code geometriesByRow}.
     * Its coordinate key is the zero-based visible column, as for null cells.
     */
    public record Result(List<String> columns, List<List<String>> rows, boolean truncated,
                         List<Set<Integer>> nullColumnsByRow, List<Map<Integer, GeometryCell>> geometriesByRow) {
        public Result {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
            nullColumnsByRow = nullColumnsByRow.stream().map(Set::copyOf).toList();
            if (nullColumnsByRow.size() != rows.size()) {
                throw new IllegalArgumentException("Null column information must cover every result row");
            }
            geometriesByRow = geometriesByRow.stream().map(Map::copyOf).toList();
            if (geometriesByRow.size() != rows.size()) {
                throw new IllegalArgumentException("Geometry information must cover every result row");
            }
        }

        /** Creates a result from string previews and null cells without geometry metadata. */
        public Result(List<String> columns, List<List<String>> rows, boolean truncated, List<Set<Integer>> nullColumnsByRow) {
            this(columns, rows, truncated, nullColumnsByRow, Collections.nCopies(rows.size(), Map.of()));
        }

        /** Creates a result from string previews without any null-cell information. */
        public Result(List<String> columns, List<List<String>> rows, boolean truncated) {
            this(columns, rows, truncated, Collections.nCopies(rows.size(), Set.of()));
        }

        /** Reports whether the cell at the zero-based result indices contains a null. */
        public boolean isNull(int row, int column) {
            return nullColumnsByRow.get(row).contains(column);
        }

        /** Returns the geometry occurrence, if present, at the zero-based result indices. */
        public Optional<GeometryCell> geometry(int row, int column) {
            return Optional.ofNullable(geometriesByRow.get(row).get(column));
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
                    return preview(query, cancellation, labels, List.of());
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
            try (var query = connection.prepareStatement(compiled.previewSql())) {
                bind(query, compiled.parameters());
                var metadata = query.getMetaData();
                int visibleColumns = metadata.getColumnCount() - Iql.GEOMETRY_METADATA_COLUMNS * compiled.geometryColumns().size();
                return preview(query, cancellation, columnLabels(metadata, visibleColumns), compiled.geometryColumns());
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
        return columnLabels(metadata, metadata.getColumnCount());
    }

    private static List<String> columnLabels(ResultSetMetaData metadata, int count) throws SQLException {
        var columns = new ArrayList<String>();
        for (int index = 1; index <= count; index++) {
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

    private static Result preview(PreparedStatement query, Cancellation cancellation, List<String> columns,
                                  List<Integer> geometryColumns) throws SQLException {
        var companionColumns = new int[columns.size()];
        for (int index = 0; index < geometryColumns.size(); index++) {
            companionColumns[geometryColumns.get(index)] = columns.size() + 1 + index * Iql.GEOMETRY_METADATA_COLUMNS;
        }
        return runStatement(query, cancellation, () -> {
            try (var rows = query.executeQuery()) {
                var metadata = rows.getMetaData();
                var data = new ArrayList<List<String>>();
                var nullColumnsByRow = new ArrayList<Set<Integer>>();
                var geometriesByRow = new ArrayList<Map<Integer, GeometryCell>>();
                while (data.size() < ROW_LIMIT) {
                    cancellation.check();
                    if (!rows.next()) {
                        return new Result(columns, data, false, nullColumnsByRow, geometriesByRow);
                    }
                    var line = new ArrayList<String>();
                    var nullColumns = new HashSet<Integer>();
                    Map<Integer, GeometryCell> geometries = geometryColumns.isEmpty() ? Map.of() : new HashMap<>();
                    for (int index = 1; index <= columns.size(); index++) {
                        cancellation.check();
                        int column = index - 1;
                        String value;
                        if (companionColumns[column] != 0) {
                            var geometry = readGeometry(rows, index, companionColumns[column]);
                            if (geometry != null) {
                                geometries.put(column, geometry);
                            }
                            value = geometry == null ? null : geometry.preview();
                        } else {
                            var type = metadata.getColumnTypeName(index);
                            value = "GEOMETRY".equalsIgnoreCase(type) || metadata.getColumnType(index) == Types.BLOB
                                    ? (rows.getObject(index) == null ? null : type) : rows.getString(index);
                        }
                        if (value == null) {
                            nullColumns.add(column);
                        }
                        line.add(value == null ? "NULL" : abbreviated(value));
                    }
                    data.add(line);
                    nullColumnsByRow.add(nullColumns);
                    geometriesByRow.add(geometries);
                }
                cancellation.check();
                return new Result(columns, data, rows.next(), nullColumnsByRow, geometriesByRow);
            }
        });
    }

    private static GeometryCell readGeometry(ResultSet rows, int column, int companion) throws SQLException {
        long source = rows.getLong(companion);
        if (rows.wasNull()) {
            return null;
        }
        String path = rows.getString(companion + 1);
        var crs = Optional.ofNullable(rows.getString(companion + 2));
        var error = Optional.ofNullable(rows.getString(companion + 3));
        var type = error.isPresent() ? Optional.<String>empty() : Optional.of(geometryType(rows, column));
        return new GeometryCell(source, path, crs, type, error);
    }

    private static String geometryType(ResultSet rows, int column) throws SQLException {
        // The pinned DuckDB JDBC driver exposes GEOMETRY as a WKB blob. A preview
        // only needs its byte order and type, even without the Spatial extension.
        var wkb = rows.getBlob(column);
        if (wkb == null) {
            return "GEOMETRY";
        }
        byte[] header = wkb.getBytes(1, 5);
        if (header.length != 5 || (header[0] != 0 && header[0] != 1)) {
            return "GEOMETRY";
        }
        int type = ByteBuffer.wrap(header, 1, 4)
                .order(header[0] == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).getInt();
        return switch (type % 1000) {
            case 1 -> "POINT";
            case 2 -> "LINESTRING";
            case 3 -> "POLYGON";
            case 4 -> "MULTIPOINT";
            case 5 -> "MULTILINESTRING";
            case 6 -> "MULTIPOLYGON";
            case 7 -> "GEOMETRYCOLLECTION";
            default -> "GEOMETRY";
        };
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
    public record SqlCatalog(Map<String, List<String>> tables, List<String> functions, List<Table> sources) {
        /** A relation with its declared spelling and columns in catalog order. */
        public record Table(String catalog, String schema, String name, List<String> columns) {
            public Table {
                Objects.requireNonNull(catalog);
                Objects.requireNonNull(schema);
                Objects.requireNonNull(name);
                columns = List.copyOf(columns);
            }
        }

        public SqlCatalog(Map<String, List<String>> tables, List<String> functions) {
            this(tables, functions, tables.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> new Table("", "", entry.getKey(), entry.getValue())).toList());
        }

        public SqlCatalog {
            var copy = new LinkedHashMap<String, List<String>>();
            tables.forEach((name, columns) -> copy.put(name, List.copyOf(columns)));
            tables = Map.copyOf(copy);
            functions = List.copyOf(functions);
            sources = List.copyOf(sources);
        }

        /** Suggests full token replacements, using half-open UTF-16 offsets into text. */
        public List<Suggestion> complete(String text, int caret) {
            return SqlCompletion.complete(this, text, caret);
        }
    }

    public SqlCatalog sqlCatalog() throws SQLException {
        var columns = new LinkedHashMap<List<String>, List<String>>();
        var functions = new TreeSet<String>();
        try (var connection = store.duplicateForQuery();
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("""
                    SELECT database_name, schema_name, table_name, column_name FROM duckdb_columns()
                    WHERE NOT internal
                    ORDER BY database_name, schema_name, table_name, column_index
                    """)) {
                while (rows.next()) {
                    var table = List.of(rows.getString(1), rows.getString(2), rows.getString(3));
                    columns.computeIfAbsent(table, name -> new ArrayList<>()).add(rows.getString(4));
                }
            }
            try (var rows = statement.executeQuery("SELECT DISTINCT function_name FROM duckdb_functions()")) {
                while (rows.next()) {
                    functions.add(rows.getString(1));
                }
            }
        }
        var tables = new LinkedHashMap<String, List<String>>();
        var sources = new ArrayList<SqlCatalog.Table>();
        columns.forEach((name, names) -> {
            var table = new SqlCatalog.Table(name.get(0), name.get(1), name.get(2), names);
            sources.add(table);
            if (table.schema().equals("main")) {
                tables.putIfAbsent(table.name(), table.columns());
            }
        });
        return new SqlCatalog(tables, List.copyOf(functions), sources);
    }
}
