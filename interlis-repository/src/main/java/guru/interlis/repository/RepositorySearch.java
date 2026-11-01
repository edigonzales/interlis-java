package guru.interlis.repository;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.*;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceId;
import java.net.URI;
import java.util.*;
import static guru.interlis.repository.RepositorySearchStep.Kind.*;

/** Run-local, lazy repository visitor. Traversal and version reduction follow ili2c 5.6.9. */
final class RepositorySearch {
    private final SourceLoader loader;
    private final RepositoryConfig config;
    private final CancellationToken token;
    private final long limit;
    private final int maxDiagnostics;
    private int warningCount;
    private long work;
    private boolean indexFailures;
    private final Map<URI,Optional<RepositoryIndex>> indexes=new LinkedHashMap<>();
    private final Map<URI,RepositorySiteReader.Site> sites=new LinkedHashMap<>();
    private final Map<SourceId,SourceLoadException.Reason> unavailable=new LinkedHashMap<>();
    private final List<RepositorySearchStep> steps=new ArrayList<>();
    private final Map<URI,Integer> ranks=new LinkedHashMap<>();
    RepositorySearch(SourceLoader loader,RepositoryConfig config,CancellationToken token,long limit) {
        this(loader,config,token,limit,1000);
    }
    RepositorySearch(SourceLoader loader,RepositoryConfig config,CancellationToken token,long limit,int maxDiagnostics) {
        this.loader=loader;this.config=config;this.token=token;this.limit=limit;this.maxDiagnostics=maxDiagnostics;
    }
    List<RepositorySearchStep> steps() { return List.copyOf(steps); }
    Map<SourceId,SourceLoadException.Reason> unavailable() { return Collections.unmodifiableMap(new LinkedHashMap<>(unavailable)); }
    boolean hasIndexFailures() { return indexFailures; }
    boolean hasOtherVersion(ModelSelector.ByName query) {
        return indexes.values().stream().flatMap(Optional::stream).flatMap(i->i.models().stream()).anyMatch(m->m.name().equals(query.modelName()) && query.modelVersion().filter(v -> !v.equals(m.version())).isPresent()
                && query.sourceHint().filter(id -> !id.value().equals(m.location().toString())).isEmpty());
    }
    private void tick() { token.throwIfCancellationRequested(); if(++work>limit)throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT); }
    private void note(String model,URI base,RepositorySearchStep.Kind kind,String detail) {
        tick();var step=new RepositorySearchStep(model,base,kind,detail);
        if(step.warning() && ++warningCount>maxDiagnostics)throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
        steps.add(step);
    }
    private URI base(URI uri) { String s=SourceUris.canonicalFile(uri.toString().trim());return URI.create(s.endsWith("/")?s:s+"/"); }
    Optional<ModelCandidate> find(ModelSelector.ByName query,Optional<LanguageVersion> language) {
        var visited=new HashSet<URI>();var parents=new ArrayDeque<URI>();var children=new ArrayDeque<URI>();
        var roots=config.sources().stream().map(s->base(s.base().uri())).toList();
        for(var root:roots) {
            if(!visited.add(root))continue;
            var found=visit(query,language,root);if(found.isPresent())return found;
        }
        for(var root:roots) { var site=site(root,query.modelName());parents.addAll(site.parents());children.addAll(site.subsidiaries()); }
        var found=parents(query,language,visited,parents);if(found.isPresent())return found;
        while(!children.isEmpty()) {
            tick();var uri=base(children.removeFirst());if(!visited.add(uri))continue;
            found=visit(query,language,uri);if(found.isPresent())return found;
            var site=site(uri,query.modelName());children.addAll(site.subsidiaries());parents.addAll(site.parents());
            found=parents(query,language,visited,parents);if(found.isPresent())return found;
        }
        return Optional.empty();
    }
    private Optional<ModelCandidate> parents(ModelSelector.ByName query,Optional<LanguageVersion> language,Set<URI> visited,Deque<URI> parents) {
        while(!parents.isEmpty()) {
            tick();var uri=base(parents.removeFirst());if(!visited.add(uri))continue;
            var found=visit(query,language,uri);if(found.isPresent())return found;
            parents.addAll(site(uri,query.modelName()).parents());
        }
        return Optional.empty();
    }
    private Optional<ModelCandidate> visit(ModelSelector.ByName query,Optional<LanguageVersion> language,URI uri) {
        note(query.modelName(),uri,LOOKUP,language.map(l -> l==LanguageVersion.ILI23 ? "INTERLIS 2.3" : "INTERLIS 2.4").orElse("INTERLIS 2.4/2.3")+query.modelVersion().map(v -> " version="+v).orElse("")+query.sourceHint().map(id -> " source="+RepositorySearchStep.safeUri(id.value())).orElse(""));
        var index=index(uri,query.modelName());if(index.isEmpty())return Optional.empty();
        var languages=language.map(l->List.of(l==LanguageVersion.ILI23?"ili2_3":"ili2_4")).orElse(List.of("ili2_4","ili2_3"));
        for(String schema:languages) {
            var matching=new ArrayList<RepositoryModelMetadata>();
            for(var entry:index.orElseThrow().models()) {
                tick();
                if(entry.name().equals(query.modelName()) && entry.schemaLanguage().equals(schema) && !entry.browseOnly()
                        && query.modelVersion().filter(v->!v.equals(entry.version())).isEmpty()
                        && query.sourceHint().filter(id->!id.value().equals(entry.location().toString())).isEmpty())matching.add(entry);
            }
            if(matching.isEmpty())continue;
            // Explicit identity requests must remain able to select historical versions.
            RepositoryModelMetadata chosen;
            if(query.modelVersion().isPresent()) {
                chosen=matching.getFirst();
                if(matching.stream().distinct().count()>1) {
                    // Preserve genuine ambiguity for explicit identity requests.
                    throw new AmbiguousSelection(query.modelName(),matching);
                }
            } else chosen=latest(query.modelName(),uri,matching);
            if(chosen==null)continue;
            note(query.modelName(),uri,SELECTED,chosen.version()+" "+RepositorySearchStep.safeUri(chosen.location().toString()));
            int rank=ranks.computeIfAbsent(uri,k->ranks.size());
            return Optional.of(new ModelCandidate(chosen,new RepositoryBase(uri),uri.resolve("ilimodels.xml"),rank));
        }
        return Optional.empty();
    }
    private RepositoryModelMetadata latest(String model,URI uri,List<RepositoryModelMetadata> values) {
        var remaining=new ArrayList<>(values); RepositoryModelMetadata first=null;
        for(var it=remaining.iterator();it.hasNext();) {
            tick();var entry=it.next();if(entry.precursorVersion().isPresent())continue;
            if(first==null)first=entry;else note(model,uri,DUPLICATE_ROOT,entry.version());
            it.remove();
        }
        if(first==null) { note(model,uri,NO_ROOT,"");return null; }
        var current=first;
        while(!remaining.isEmpty()) {
            RepositoryModelMetadata next=null;
            for(var it=remaining.iterator();it.hasNext();) {
                tick();var entry=it.next();
                if(!entry.precursorVersion().orElse("").equals(current.version()))continue;
                if(next==null)next=entry;else note(model,uri,DUPLICATE_SUCCESSOR,entry.version());
                it.remove();
            }
            if(next==null)break;current=next;
        }
        if(!remaining.isEmpty())note(model,uri,BROKEN_CHAIN,current.version()+"; "+String.join(", ",remaining.stream().map(RepositoryModelMetadata::version).toList()));
        return current;
    }
    private Optional<RepositoryIndex> index(URI uri,String model) {
        return indexes.computeIfAbsent(uri,key->{
            var locator=config.sources().stream().filter(s->base(s.base().uri()).equals(uri)).map(RepositorySource::indexLocator).findFirst().orElse(new SourceLocator(SourceUris.canonicalFile(uri.resolve("ilimodels.xml").toString())));
            try { var loaded=loader.load(locator,config.fetchPolicy(),token);return Optional.of(new RepositoryIndexReader().read(new IndexPayload(loaded.text()),new RepositoryBase(uri))); }
            catch(SourceLoadException failure) { unavailable.put(new SourceId(locator.location()),failure.reason());note(model,uri,REPOSITORY_UNAVAILABLE,failure.reason().name()); }
            catch(RepositoryIndexException failure) { indexFailures=true;note(model,uri,REPOSITORY_UNAVAILABLE,"INVALID_INDEX"); }
            return Optional.empty();
        });
    }
    private RepositorySiteReader.Site site(URI uri,String model) {
        return sites.computeIfAbsent(uri,key->{
            var locator=new SourceLocator(SourceUris.canonicalFile(uri.resolve("ilisite.xml").toString()));
            try { var loaded=loader.load(locator,config.fetchPolicy(),token);return new RepositorySiteReader().read(new IndexPayload(loaded.text()),new RepositoryBase(uri)); }
            catch(SourceLoadException failure) {
                unavailable.put(new SourceId(locator.location()),failure.reason());
                if(failure.reason()!=SourceLoadException.Reason.NOT_FOUND)note(model,uri,SITE_UNAVAILABLE,failure.reason().name());
            } catch(RepositoryIndexException failure) { indexFailures=true;note(model,uri,SITE_UNAVAILABLE,"INVALID_INDEX"); }
            return new RepositorySiteReader.Site(List.of(),List.of());
        });
    }
    static final class AmbiguousSelection extends RuntimeException {
        final String model;final List<RepositoryModelMetadata> candidates;
        AmbiguousSelection(String model,List<RepositoryModelMetadata> candidates) { this.model=model;this.candidates=List.copyOf(candidates); }
    }
}
