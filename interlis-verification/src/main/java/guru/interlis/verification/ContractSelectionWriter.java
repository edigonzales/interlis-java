package guru.interlis.verification;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static guru.interlis.verification.HostChecks.*;

/** Writes the exact producer test plan from the canonical compiler contract catalog. */
public final class ContractSelectionWriter {
    private ContractSelectionWriter() {}

    public static void main(String[] args) throws Exception {
        if(args.length==3&&args[0].equals("norms")) {
            Path root=Path.of(args[1]),output=Path.of(args[2]);
            var documents=HostChecks.read(root.resolve("testdata/reference/norms/documents.json")).getAsJsonObject("documents");
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
        var document=CompilerContractCatalog.load(root).document();write(out.resolve("selection.json"),document);
        var modules=new TreeMap<String,Set<String>>();
        for(var entry:document.getAsJsonObject("bindings").entrySet()) {
            var b=entry.getValue().getAsJsonObject();String module=s(b,"module");
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
