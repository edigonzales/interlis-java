package guru.interlis.projection;

import java.util.List;
import java.util.Objects;

/** Produced document or explicit rejection. */
public sealed interface DiagramProjectionOutcome permits DiagramProjectionOutcome.Produced,
        DiagramProjectionOutcome.Rejected {
    record Produced(DiagramDocument document, List<ProjectionNotice> notices)
            implements DiagramProjectionOutcome {
        /** Creates a validated instance. */
        public Produced {
            Objects.requireNonNull(document, "document");
            notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
        }
    }

    record Rejected(List<ProjectionNotice> reasons) implements DiagramProjectionOutcome {
        /** Creates a validated instance. */
        public Rejected {
            reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
            if (reasons.isEmpty()) {
                throw new IllegalArgumentException("projection rejection needs reasons");
            }
        }
    }
}
