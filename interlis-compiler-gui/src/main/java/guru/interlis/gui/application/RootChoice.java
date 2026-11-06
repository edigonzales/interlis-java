package guru.interlis.gui.application;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.source.SourceId;

import java.util.Objects;
import java.util.Optional;

/** An explicit root with the source identity retained independently of its display label. */
public sealed interface RootChoice permits RootChoice.Local, RootChoice.Repository {
    ModelSelector.ByName selector();

    record Local(String name, Optional<String> version, SourceId source) implements RootChoice {
        public Local {
            Objects.requireNonNull(name);
            Objects.requireNonNull(version);
            Objects.requireNonNull(source);
        }

        @Override
        public ModelSelector.ByName selector() {
            return new ModelSelector.ByName(name, version, Optional.of(source));
        }
    }

    record Repository(String name, String version, SourceId source, RepositoryBase repository) implements RootChoice {
        public Repository {
            Objects.requireNonNull(name);
            Objects.requireNonNull(version);
            Objects.requireNonNull(source);
            Objects.requireNonNull(repository);
        }

        @Override
        public ModelSelector.ByName selector() {
            return new ModelSelector.ByName(name, Optional.of(version), Optional.of(source));
        }
    }
}
