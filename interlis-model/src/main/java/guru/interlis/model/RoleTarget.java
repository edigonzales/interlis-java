package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * One alternative of a role target. Base and restrictions are bound identities.
 * Restrictions are an ordered disjunction of subclasses of the base; an empty
 * list allows the entire base hierarchy. Values and lists are non-null and immutable.
 */
public record RoleTarget(SymbolId base, List<SymbolId> restrictions) {
    public RoleTarget {
        Objects.requireNonNull(base, "base");
        restrictions = List.copyOf(restrictions);
    }

    /** Allowed hierarchy roots in declaration order, without expanding subclasses. */
    public List<SymbolId> allowedRoots() {
        return restrictions.isEmpty() ? List.of(base) : restrictions;
    }
}
