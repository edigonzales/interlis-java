package guru.interlis.xtf23;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One XTF 2.3 header declaration connecting an XML-ID prefix to its OID domain.
 * Both components retain their exact source spelling.
 */
public record Xtf23OidSpace(String alias, String domain) {
    static final String NC_NAME = "[A-Za-z_][A-Za-z0-9_.-]*";
    private static final Pattern ALIAS = Pattern.compile(NC_NAME);
    private static final Pattern QUALIFIED_DOMAIN =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");

    /** Creates a declaration with a nonnull XML prefix and qualified INTERLIS domain name. */
    public Xtf23OidSpace {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(domain, "domain");
        if (alias.length() > 1024 || domain.length() > 1024 || !ALIAS.matcher(alias).matches()
                || !QUALIFIED_DOMAIN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Invalid OIDSPACE alias or qualified domain name");
        }
    }
}
