package guru.interlis.model;

import guru.interlis.diagnostics.DiagnosticId;

import java.util.List;
import java.util.Objects;

/**
 * Analysis error type; never a productive language type.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param diagnosticIds the {@code diagnosticIds} component
 */
public record ErrorType(TypeId id, Origin origin, List<DiagnosticId> diagnosticIds)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public ErrorType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        diagnosticIds = List.copyOf(Objects.requireNonNull(diagnosticIds, "diagnosticIds"));
        if (diagnosticIds.isEmpty()) {
            throw new IllegalArgumentException("ErrorType needs a diagnostic cause");
        }
    }

    @Override
    public TypeKind kind() {
        return TypeKind.ERROR;
    }
}
