package guru.interlis.compiler.query;

import guru.interlis.model.SnapshotId;
import guru.interlis.source.*;

import java.util.*;

/**
 * Pure, single-source editor operation against an exact captured revision. caretOffset is an
 * absolute UTF-16 offset in the resulting text; a negative value leaves it unchanged.
 */
public record EditorEditPlan(
        String id,
        String title,
        SnapshotId snapshotId,
        SourceId source,
        SourceRevision revision,
        List<TextEdit> edits,
        int caretOffset) {
    public EditorEditPlan {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(source);
        Objects.requireNonNull(revision);
        edits = List.copyOf(edits);
        for (var e : edits)
            if (!e.range().source().equals(source) || !e.range().revision().equals(revision))
                throw new IllegalArgumentException("foreign editor edit");
    }
}
