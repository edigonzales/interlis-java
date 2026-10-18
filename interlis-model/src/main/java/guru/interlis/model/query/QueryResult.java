package guru.interlis.model.query;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.NotAnalyzedReason;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.UnresolvedReason;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Typed query result. {@code Known} may describe a legal deferred type;
 * {@code Unresolved} means the query was attempted but had no unique binding;
 * {@code NotAnalyzed} means analysis coverage is missing.
 */
public sealed interface QueryResult<T> permits QueryResult.Known,
        QueryResult.Unresolved, QueryResult.Invalid, QueryResult.NotAnalyzed {

    record Known<T>(T value) implements QueryResult<T> {
        /** Creates a validated instance. */
        public Known {
            Objects.requireNonNull(value, "value");
        }
    }

    record Unresolved<T>(UnresolvedReason reason, List<SymbolRef> candidates)
            implements QueryResult<T> {
        /** Creates a validated instance. */
        public Unresolved {
            Objects.requireNonNull(reason, "reason");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    record Invalid<T>(List<DiagnosticId> diagnosticIds) implements QueryResult<T> {
        /** Creates a validated instance. */
        public Invalid {
            diagnosticIds = List.copyOf(Objects.requireNonNull(diagnosticIds, "diagnosticIds"));
            if (diagnosticIds.isEmpty()) {
                throw new IllegalArgumentException("Invalid needs at least one diagnostic id");
            }
        }
    }

    record NotAnalyzed<T>(NotAnalyzedReason reason) implements QueryResult<T> {
        /** Creates a validated instance. */
        public NotAnalyzed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Returns the {@code known} value.
     *
     * @return the value
     */
    default Optional<T> known() {
        return this instanceof Known<T> known ? Optional.of(known.value()) : Optional.empty();
    }

    default <R> QueryResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return switch (this) {
            case Known<T> known -> new Known<>(mapper.apply(known.value()));
            case Unresolved<T> unresolved ->
                    new Unresolved<>(unresolved.reason(), unresolved.candidates());
            case Invalid<T> invalid -> new Invalid<>(invalid.diagnosticIds());
            case NotAnalyzed<T> notAnalyzed -> new NotAnalyzed<>(notAnalyzed.reason());
        };
    }
}
