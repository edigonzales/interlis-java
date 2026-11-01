package guru.interlis.cli;

import java.util.Objects;

/** Adds only fixed SGR sequences around text sanitized by the caller. */
public final class AnsiStyler {
    /** Static restrained palette; no terminal control commands beyond SGR. */
    public enum Role {
        ERROR("31"), WARNING("33"), INFO("36"), HINT("34"), LOCATION("36"), CODE("90");
        private final String sgr;
        Role(String sgr) { this.sgr = sgr; }
    }
    /** Null-free text must already be safeText-escaped; every enabled span closes with reset. */
    public String style(Role role, String alreadySanitizedText, boolean enabled) {
        Objects.requireNonNull(role); Objects.requireNonNull(alreadySanitizedText);
        return enabled ? "\u001b[" + role.sgr + "m" + alreadySanitizedText + "\u001b[0m" : alreadySanitizedText;
    }
}
