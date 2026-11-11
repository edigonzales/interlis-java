package guru.interlis.xtf24;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;

final class Xtf24IdentityScope {
    // eCH-0031 §4.3.1: the local part may start with a digit.
    private static final Pattern XML_ID =
            Pattern.compile("(?:[A-Za-z_][A-Za-z0-9_.-]*:)?[A-Za-z0-9_][A-Za-z0-9_.-]*");

    private final Xtf24Limits limits;
    private final Set<String> seen = new HashSet<>();

    Xtf24IdentityScope(Xtf24Limits limits) {
        this.limits = limits;
    }

    void add(String id) {
        requireIdentifier(id, limits.maxIdentifierCodeUnits());
        if (seen.contains(id)) {
            throw Xtf24Wire.failure(DUPLICATE_ID, "Duplicate basket or object identity");
        }
        if (seen.size() >= limits.maxIdentities()) {
            throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XTF 2.4 identity limit exceeded");
        }
        seen.add(id);
    }

    static void requireIdentifier(String id, int limit) {
        if (id.length() > limit) {
            throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XTF 2.4 identifier is too long");
        }
        if (!XML_ID.matcher(id).matches()) {
            throw Xtf24Wire.failure(INVALID_WIRE, "Expected an INTERLIS XML-ID");
        }
    }

    void clear() {
        seen.clear();
    }
}
