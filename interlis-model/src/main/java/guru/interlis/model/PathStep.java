package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.Objects;

/** Closed typed object-path step. */
public sealed interface PathStep permits PathStep.ThisStep, PathStep.ParentStep,
        PathStep.AttributeStep, PathStep.RoleStep {

    /**
     * Returns the {@code range} value.
     *
     * @return the value
     */
    SourceRange range();

    record ThisStep(SourceRange range) implements PathStep {
        /** Creates a validated instance. */
        public ThisStep {
            Objects.requireNonNull(range, "range");
        }
    }

    record ParentStep(SourceRange range) implements PathStep {
        /** Creates a validated instance. */
        public ParentStep {
            Objects.requireNonNull(range, "range");
        }
    }

    record AttributeStep(String name, SymbolId target, SourceRange range) implements PathStep {
        /** Creates a validated instance. */
        public AttributeStep {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(range, "range");
        }
    }

    record RoleStep(String name, SymbolId target, SourceRange range) implements PathStep {
        /** Creates a validated instance. */
        public RoleStep {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(range, "range");
        }
    }
}
