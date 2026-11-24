package guru.interlis.explorer;

import guru.interlis.transfer.TransferPlan;

import java.util.List;
import java.util.Objects;

/** Read-only, model-bound queries for one Native Store transfer plan. */
public final class Iql {
    private static final int MAX_QUERY_LENGTH = 32_768;

    private Iql() {}

    /** DuckDB statement with its ordered, immutable JDBC parameters and preflight checks. */
    public record Compiled(String sql, List<Object> parameters, List<Check> checks) {
        public Compiled {
            parameters = List.copyOf(parameters);
            checks = List.copyOf(checks);
        }
    }

    /** A scalar SQL check with immutable JDBC parameters. Its single text result is null on success. */
    public record Check(String sql, List<Object> parameters, int start, int end) {
        public Check {
            parameters = List.copyOf(parameters);
        }

        public Check(String sql, int start, int end) {
            this(sql, List.of(), start, end);
        }
    }

    /** Suggested text, description and replacement range in UTF-16 editor offsets. */
    public record Completion(String text, String description, int start, int end) {}

    /** Query diagnostic with a half-open UTF-16 source range. */
    public static final class QueryException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final int start;
        private final int end;

        public QueryException(String message, int start, int end) {
            super(message);
            this.start = start;
            this.end = end;
        }

        public int start() { return start; }
        public int end() { return end; }
    }

    /** Compiles the supplied text for this plan. The JDBC result is capped one row past the preview limit. */
    public static Compiled compile(TransferPlan plan, String text, int previewLimit) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(text);
        if (previewLimit < 1) throw new IllegalArgumentException("Preview limit must be positive");
        if (text.length() > MAX_QUERY_LENGTH) throw new QueryException("Query text is too long", MAX_QUERY_LENGTH, text.length());
        return new IqlBinder(new IqlModel(plan)).compile(IqlSyntax.parse(text), previewLimit);
    }

    /** Computes immutable model-aware suggestions for an incomplete query without executing it. */
    public static List<Completion> complete(TransferPlan plan, String text, int caret) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(text);
        if (caret < 0 || caret > text.length()) throw new IllegalArgumentException("Caret is outside query text");
        if (text.length() > MAX_QUERY_LENGTH) return List.of();
        return new IqlCompletion(new IqlModel(plan), text, caret).suggestions();
    }
}
