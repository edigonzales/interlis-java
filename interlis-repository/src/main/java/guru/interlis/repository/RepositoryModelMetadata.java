package guru.interlis.repository;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * One model entry from an INTERLIS repository index.
 *
 * @param name model name
 * @param schemaLanguage declared schema language
 * @param location resolved model file URI
 * @param version model version
 * @param precursorVersion optional predecessor version
 * @param md5 optional published content digest
 */
public record RepositoryModelMetadata(String name, String schemaLanguage, URI location,
                                      String version, Optional<String> precursorVersion,
                                      Optional<String> md5) {
    /** Creates a validated instance. */
    public RepositoryModelMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(schemaLanguage, "schemaLanguage");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(version, "version");
        precursorVersion = Objects.requireNonNull(precursorVersion, "precursorVersion");
        md5 = Objects.requireNonNull(md5, "md5");
    }
}
