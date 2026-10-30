package guru.interlis.cli;

import guru.interlis.model.VersionManifest;
import java.util.Objects;

/** Reports the actual replacement identity and the separate reference profile. */
public final class ToolVersionRenderer {
    public String render(VersionManifest manifest, LegacyCliProfile profile) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(profile, "profile");
        return "INTERLIS Compiler (interlis-java), Version " + manifest.toolVersion() + "\n"
                + "Compatibility profile: " + profile.id() + "\n"
                + "Reference: ili2c " + profile.referenceVersion() + "\n";
    }
}
