package guru.interlis.cli;

import java.util.Objects;

/** The pinned ili2c checking profile uses zero for success, one for rejection. */
public final class LegacyExitCodePolicy {
    /** Host cancellation/internal failures also fail, without claiming a legacy signal exit. */
    public int exitCode(CliTermination termination, LegacyCliProfile profile) {
        Objects.requireNonNull(termination, "termination");
        Objects.requireNonNull(profile, "profile");
        return termination == CliTermination.SUCCESS ? 0 : 1;
    }
}
