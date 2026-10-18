package guru.interlis.lsp;

import java.util.List;

/** Parameters of the custom read-only interlis/diagram request. */
public record DiagramParams(String uri, String snapshotId, List<String> symbolIds) {
    public DiagramParams {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("uri is required");
        }
        symbolIds = symbolIds == null ? List.of() : List.copyOf(symbolIds);
    }
}
