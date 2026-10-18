package guru.interlis.workspace;

import guru.interlis.source.SourceRevision;

import java.util.Objects;

/** Optimistic precondition for a source update. */
public sealed interface SourceExpectation permits SourceExpectation.Absent,
        SourceExpectation.At {
    record Absent() implements SourceExpectation {
    }

    record At(SourceRevision revision) implements SourceExpectation {
        /** Creates a validated instance. */
        public At {
            Objects.requireNonNull(revision, "revision");
        }
    }
}
