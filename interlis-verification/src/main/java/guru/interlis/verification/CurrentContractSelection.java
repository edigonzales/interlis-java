package guru.interlis.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static guru.interlis.verification.HostChecks.*;

/** The current read-only original/overlay selection, shared by contracts and build coordination. */
public final class CurrentContractSelection {
    private CurrentContractSelection() {}
    public static JsonObject read(Path root) throws Exception {
        var overlay=HostChecks.read(root.resolve("testdata/verification/current-bindings.json"));
        Path original=root.resolve("testdata/g2/completion-links.json");pin(original,s(overlay.getAsJsonObject("origin"),"sha256"));
        var result=HostChecks.read(original);var bindings=result.getAsJsonObject("bindings");
        for(String key:List.of("overrides","extras"))for(var entry:overlay.getAsJsonObject(key).entrySet()) {
            require(key.equals("overrides")==bindings.has(entry.getKey()),"Unknown override/duplicate extra");bindings.add(entry.getKey(),entry.getValue());
        }
        for(var entry:bindings.entrySet()) {
            var binding=entry.getValue().getAsJsonObject();var parameters=overlay.getAsJsonObject("parameterInvocations");
            if(parameters.has(entry.getKey()))binding.add("invocations",parameters.get(entry.getKey()));
            else {var invocations=new JsonArray();invocations.add(s(binding,"method")+"()");binding.add("invocations",invocations);}
        }
        for(var row:rows(result,"requirements"))if(row.has("processEvidence")) {
            var evidence=row.getAsJsonArray("processEvidence");for(int i=0;i<evidence.size();i++) {
                String source=s(evidence.get(i).getAsJsonObject(),"source");
                if(overlay.getAsJsonObject("processOverrides").has(source))evidence.set(i,overlay.getAsJsonObject("processOverrides").get(source));
            }
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        if(args.length==3&&args[0].equals("norms")) {
            Path root=Path.of(args[1]),output=Path.of(args[2]);
            var documents=HostChecks.read(root.resolve("docs/evidence/stabilization/g2-rule-matrix.json")).getAsJsonObject("documents");
            Path cache=HostChecks.cache(root,"INTERLIS_NORM_CACHE","norms");Files.createDirectories(output);
            for(var entry:documents.entrySet()) {
                var document=entry.getValue().getAsJsonObject();String name="ili"+entry.getKey().replace(".","")+".pdf";
                Path cached=cache.resolve(name);download(java.net.URI.create(s(document,"url")),cached,s(document,"sha256"));
                Files.copy(cached,output.resolve(name));
            }
            return;
        }
        if(args.length!=2)throw new IllegalArgumentException("Expected root and new plan directory");
        Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        var document=read(root);write(out.resolve("selection.json"),document);
        var modules=new TreeMap<String,Set<String>>();
        for(var entry:document.getAsJsonObject("bindings").entrySet()) {
            var b=entry.getValue().getAsJsonObject();String module=s(b,"source").split("/")[0];
            String invocation=strings(b.get("invocations")).getFirst();
            String signature=invocation.substring(0,invocation.indexOf(')')+1);
            String parameters=signature.substring(signature.indexOf('(')+1,signature.length()-1);
            if(parameters.equals("String"))signature=signature.replace("(String)","(java.lang.String)");
            else require(parameters.isEmpty()||Set.of("int","long","boolean","double","float","char","byte","short").contains(parameters),"Unsupported parameter selector metadata: "+signature);
            modules.computeIfAbsent(module,k->new TreeSet<>()).add(s(b,"suite")+"#"+signature);
        }
        require(!modules.isEmpty(),"Empty contract selection");var properties=new Properties();
        modules.forEach((m,tests)->properties.setProperty(m,String.join(",",tests)));
        try(var writer=Files.newBufferedWriter(out.resolve("selection.properties"))){properties.store(writer,"Current exact producer selection");}
    }
}
