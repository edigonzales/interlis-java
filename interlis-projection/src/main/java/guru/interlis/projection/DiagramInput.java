package guru.interlis.projection;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.model.CompiledSchema;

import java.util.Objects;

/** Explicit checked or partial input. */
public sealed interface DiagramInput permits DiagramInput.FromAnalysis,
        DiagramInput.FromSchema {
    record FromAnalysis(AnalysisSnapshot snapshot) implements DiagramInput {
        /** Creates a validated instance. */
        public FromAnalysis {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record FromSchema(CompiledSchema schema) implements DiagramInput {
        /** Creates a validated instance. */
        public FromSchema {
            Objects.requireNonNull(schema, "schema");
        }
    }
}
