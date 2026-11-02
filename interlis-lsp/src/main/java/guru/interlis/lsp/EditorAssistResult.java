package guru.interlis.lsp;

import org.eclipse.lsp4j.*;

import java.util.*;

/** Java-planned edits and the resulting native caret; null response means no assistance. */
public record EditorAssistResult(
        int protocolVersion,
        String serverInstanceId,
        String snapshotId,
        String uri,
        int documentVersion,
        List<TextEdit> edits,
        Position caret) {
    public EditorAssistResult {
        edits = List.copyOf(edits);
    }
}
