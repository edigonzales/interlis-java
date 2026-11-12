package guru.interlis.validation;

import java.util.Objects;

/** A host claim of completeness for one aspect of an explicit extent. */
public record ExtentDeclaration(ExtentSelector selector, ExtentAspect aspect, String evidenceDescription) {
    public ExtentDeclaration {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(aspect, "aspect");
        if (Objects.requireNonNull(evidenceDescription, "evidenceDescription").isBlank()) {
            throw new IllegalArgumentException("An extent declaration requires evidence");
        }
    }
}
