package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/** Typed provenance, including synthetic and predefined origins. */
public sealed interface Origin permits Origin.DeclaredOrigin, Origin.DerivedOrigin,
        Origin.PredefinedOrigin, Origin.SyntheticOrigin, Origin.OverlayOrigin {

    /** External immutable configuration provenance; the range may belong to a
     * configuration source outside the model SourceSet. */
    record OverlayOrigin(String overlayId,int priority,Optional<SourceRange> range) implements Origin {
        public OverlayOrigin {Objects.requireNonNull(overlayId,"overlayId");if(overlayId.isBlank())throw new IllegalArgumentException("blank overlay id");Objects.requireNonNull(range,"range");}
    }

    record DeclaredOrigin(SourceRange range) implements Origin {
        /** Creates a validated instance. */
        public DeclaredOrigin {
            Objects.requireNonNull(range, "range");
        }
    }

    record DerivedOrigin(SymbolId declaredAt, DerivationKind kind) implements Origin {
        /** Creates a validated instance. */
        public DerivedOrigin {
            Objects.requireNonNull(declaredAt, "declaredAt");
            Objects.requireNonNull(kind, "kind");
        }
    }

    record PredefinedOrigin(LanguageVersion language, String key) implements Origin {
        /** Creates a validated instance. */
        public PredefinedOrigin {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(key, "key");
        }
    }

    record SyntheticOrigin(SyntheticReason reason, Optional<SourceRange> range) implements Origin {
        /** Creates a validated instance. */
        public SyntheticOrigin {
            Objects.requireNonNull(reason, "reason");
            range = Objects.requireNonNull(range, "range");
        }
    }
}
