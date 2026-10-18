package guru.interlis.compiler;

import java.util.Objects;

/**
 * Verified or explicitly unresolved import binding.
 *
 * @param site the {@code site} component
 * @param resolution the {@code resolution} component
 */
public record ImportBinding(ImportSite site, ImportResolution resolution) {
    /** Creates a validated instance. */
    public ImportBinding {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(resolution, "resolution");
    }
}
