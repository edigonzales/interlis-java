package guru.interlis.lsp;

import com.google.gson.*;
import guru.interlis.repository.*;
import guru.interlis.source.SourceId;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Immutable host policy, decoded atomically alongside compiler configuration. No IO during decoding. */
record RepositorySettings(RepositoryResolveOptions options, RepositoryCacheConfiguration cache, String diagnosticLanguage) {
    static final Set<String> FIELDS=Set.of("repositories","offline","cacheDirectory","modelPins","sourcePins","resolutionLock","diagnosticLanguage");
    static RepositorySettings defaults() {
        return new RepositorySettings(RepositoryResolveOptions.defaults(new RepositoryConfig(List.of(),FetchPolicy.defaults())),
                RepositoryCacheConfiguration.disabled(),"auto");
    }
    static JsonObject root(Object value) {
        var json=new Gson().toJsonTree(value);
        if(!json.isJsonObject()) throw new IllegalArgumentException();
        var root=json.getAsJsonObject();
        if(root.has("interlis")) { if(!root.get("interlis").isJsonObject()) throw new IllegalArgumentException(); root=root.getAsJsonObject("interlis"); }
        return root;
    }
    static RepositorySettings read(Object value,RepositorySettings previous) {
        var root=root(value);
        var repositories=previous.options.repositories().sources();
        if(root.has("repositories")) {
            var list=new ArrayList<RepositorySource>();
            for(var entry:array(root.get("repositories"))) {
                URI uri=URI.create(string(entry));
                if(!Set.of("https","http","file").contains(uri.getScheme()) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                    throw new IllegalArgumentException();
                String location=uri.toString(); if(!location.endsWith("/")) location+="/";
                var base=new RepositoryBase(URI.create(location));
                list.add(new RepositorySource(base,new SourceLocator(base.uri().resolve("ilimodels.xml").toString())));
                if(list.size()>16) throw new IllegalArgumentException();
            }
            repositories=List.copyOf(list);
        }
        boolean offline=previous.options.repositories().fetchPolicy().offline();
        if(root.has("offline")) { var v=root.get("offline"); if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(); offline=v.getAsBoolean(); }
        var cache=previous.cache;
        if(root.has("cacheDirectory")) {
            String v=string(root.get("cacheDirectory"));
            if(v.isEmpty()) cache=RepositoryCacheConfiguration.disabled();
            else { Path path=Path.of(v); if(!path.isAbsolute()) throw new IllegalArgumentException(); cache=RepositoryCacheConfiguration.at(path); }
        }
        String language=root.has("diagnosticLanguage")?string(root.get("diagnosticLanguage")):previous.diagnosticLanguage;
        if(!Set.of("auto","de","en").contains(language)) throw new IllegalArgumentException();
        var pins=previous.options.modelPins();
        if(root.has("modelPins")) {
            var list=new LinkedHashMap<String,ModelPin>();
            for(var entry:array(root.get("modelPins"))) {
                if(!entry.isJsonObject()) throw new IllegalArgumentException();var p=entry.getAsJsonObject();
                if(!p.keySet().equals(Set.of("modelName","modelVersion","sourceId","sha256"))) throw new IllegalArgumentException();
                var pin=new ModelPin(string(p.get("modelName")),string(p.get("modelVersion")),new SourceId(string(p.get("sourceId"))),new ContentDigest("sha256",string(p.get("sha256"))));
                if(list.putIfAbsent(pin.modelName(),pin)!=null) throw new IllegalArgumentException();
            }
            pins=Map.copyOf(list);
        }
        var sourcePins=previous.options.sourcePins();
        if(root.has("sourcePins")) {
            var list=new LinkedHashMap<SourceId,ContentDigest>();
            for(var entry:array(root.get("sourcePins"))) {
                if(!entry.isJsonObject()) throw new IllegalArgumentException();var p=entry.getAsJsonObject();
                if(!p.keySet().equals(Set.of("sourceId","sha256"))) throw new IllegalArgumentException();
                if(list.putIfAbsent(new SourceId(string(p.get("sourceId"))),new ContentDigest("sha256",string(p.get("sha256"))))!=null) throw new IllegalArgumentException();
            }
            sourcePins=Map.copyOf(list);
        }
        var lock=previous.options.lock();
        if(root.has("resolutionLock")) { String v=string(root.get("resolutionLock"));lock=v.isEmpty()?Optional.empty():Optional.of(new ResolutionLockCodec().decode(v)); }
        return new RepositorySettings(new RepositoryResolveOptions(new RepositoryConfig(repositories,new FetchPolicy(offline,16_777_216,Duration.ofSeconds(15),Duration.ofSeconds(40),Optional.empty())),pins,sourcePins,lock),cache,language);
    }
    private static String string(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();return value.getAsString();
    }
    private static JsonArray array(JsonElement value) { if(value==null || !value.isJsonArray()) throw new IllegalArgumentException();return value.getAsJsonArray(); }
}
