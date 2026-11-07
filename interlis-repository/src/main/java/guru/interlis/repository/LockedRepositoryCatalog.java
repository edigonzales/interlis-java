package guru.interlis.repository;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceId;
import java.net.URI;
import java.util.*;

/** Replays explicit v2 identities against digest-verified frozen metadata, without a new repository search. */
final class LockedRepositoryCatalog {
    private final List<ModelCandidate> candidates=new ArrayList<>();
    LockedRepositoryCatalog(ResolutionLock lock,RepositoryConfig config,SourceLoader loader,CancellationToken token) {
        int priority=0;
        for(var input:lock.indexes().keySet().stream().sorted(Comparator.comparing(SourceId::value)).toList()) {
            token.throwIfCancellationRequested();
            var loaded=loader.load(new SourceLocator(input.value()),config.fetchPolicy(),RepositoryCacheRequest.metadata(),token);
            URI location=URI.create(input.value());
            var base=config.sources().stream().filter(s->s.indexLocator().location().equals(input.value())).map(RepositorySource::base)
                    .findFirst().orElse(new RepositoryBase(location.resolve(".")));
            if (location.getPath().endsWith("/ilisite.xml")
                    && config.sources().stream().noneMatch(s -> s.indexLocator().location().equals(input.value()))) {
                new RepositorySiteReader().read(new IndexPayload(loaded.text()),base);
            } else {
                var index=new RepositoryIndexReader().read(new IndexPayload(loaded.text()),base);
                for(var metadata:index.models())candidates.add(new ModelCandidate(metadata,base,location,priority));
                priority++;
            }
        }
    }
    List<ModelCandidate> select(ModelSelector.ByName query,Optional<LanguageVersion> language,ResolutionLock lock) {
        var pins=lock.models().getOrDefault(query.modelName(),List.of());
        return candidates.stream().filter(c -> pins.stream().anyMatch(p -> p.source().value().equals(c.metadata().location().toString())
                        && p.modelVersion().equals(c.metadata().version())))
                .filter(c -> c.metadata().name().equals(query.modelName()))
                .filter(c -> query.modelVersion().filter(v -> !v.equals(c.metadata().version())).isEmpty())
                .filter(c -> query.sourceHint().filter(id -> !id.value().equals(c.metadata().location().toString())).isEmpty())
                .filter(c -> language.map(l -> c.metadata().schemaLanguage().equals(l==LanguageVersion.ILI23?"ili2_3":"ili2_4"))
                        .orElse(Set.of("ili2_3","ili2_4").contains(c.metadata().schemaLanguage())))
                .toList();
    }
}
