package guru.interlis.workspace.resolution;

import guru.interlis.compiler.ImportSite;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Host-facing request for a missing model; it never triggers IO itself.
 *
 * @param modelName the {@code modelName} component
 * @param modelVersion model version
 * @param possibleLocators the {@code possibleLocators} component
 * @param reason the {@code reason} component
 * @param importSites the {@code importSites} component
 */
public record PendingSourceRequest(String modelName, Optional<String> modelVersion,
                                   List<String> possibleLocators, String reason,
                                   List<ImportSite> importSites) {
    /** Creates a validated instance. */
    public PendingSourceRequest {
        Objects.requireNonNull(modelName, "modelName");
        modelVersion = Objects.requireNonNull(modelVersion, "modelVersion");
        possibleLocators = List.copyOf(Objects.requireNonNull(possibleLocators, "possibleLocators"));
        Objects.requireNonNull(reason, "reason");
        importSites = List.copyOf(Objects.requireNonNull(importSites, "importSites"));
    }
}
