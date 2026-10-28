package guru.interlis.model;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, null-free LIST/coordinate selection. Positions are exact, one-based
 * numbers; zero can occur in syntax but must be rejected by semantic binding.
 * FIRST/LAST carry no position. No runtime collection length is implied.
 */
public record PathIndex(Kind kind, Optional<BigInteger> position) {
    public enum Kind { FIRST, LAST, POSITION }

    public PathIndex {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        if ((kind == Kind.POSITION) != position.isPresent()
                || position.filter(p -> p.signum() < 0).isPresent())
            throw new IllegalArgumentException("position required only for a numeric index");
    }
}
